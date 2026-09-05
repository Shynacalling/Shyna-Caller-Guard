package com.example.callruleblocker.call

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

enum class AppCallType { VOICE, VIDEO }
enum class AppCallStatus {
    CREATED, INVITING, RINGING, ACCEPTED, CONNECTING, CONNECTED,
    RECONNECTING, ENDED, MISSED, DECLINED, CANCELLED, FAILED,
    BUSY, NO_ANSWER, UNAVAILABLE,
    REJECTED // Keep for backward compatibility with old records
}

data class AppCall(
    val id: String = "",
    val callerUid: String = "",
    val callerName: String = "",
    val callerPhoto: String? = null,
    val receiverUid: String = "",
    val receiverName: String = "",
    val receiverPhoto: String? = null,
    val type: AppCallType = AppCallType.VOICE,
    val status: AppCallStatus = AppCallStatus.RINGING,
    val roomName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val answeredAt: Long? = null,
    val connectedAt: Long? = null,
    val endedAt: Long? = null,
    val duration: Long = 0,
    val endReason: String? = null,
    val participantIds: List<String> = emptyList(),
    val isGroup: Boolean = false,
    val scheduledCallId: String? = null,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

object CallSignalingManager {
    private const val TAG = "ShynaCall"
    private val db = FirebaseFirestore.getInstance()
    private var callListener: ListenerRegistration? = null
    private val meetingStartsInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Starts a Shyna internet call. The backend is the preferred path because it can
     * send FCM notifications. If the backend create endpoint is temporarily unavailable,
     * Firestore signaling is used as a safe in-app fallback so the call buttons never
     * become no-op controls while both users have Shyna open.
     */
    fun startCall(
        context: Context,
        receiverUid: String,
        type: AppCallType,
        onCallCreated: (AppCall) -> Unit,
        onError: (Exception) -> Unit,
        isGroup: Boolean = false,
        participantIds: List<String>? = null
    ) {
        if (receiverUid.isBlank() && !isGroup) {
            onError(IllegalArgumentException("Receiver is missing"))
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isGroup && receiverUid.isNotBlank()) {
                    val receiverDoc = runCatching { db.collection("users").document(receiverUid).get().await() }.getOrNull()
                    if (receiverDoc == null || !receiverDoc.exists()) {
                        withContext(Dispatchers.Main) {
                            onError(IllegalStateException("This user account no longer exists or has been deleted."))
                        }
                        return@launch
                    }
                }

                val manager = LiveKitCallManager(context)
                // Start backend creation and Firestore lookup in parallel if possible
                val result = manager.createCall(receiverUid, type, isGroup, participantIds)
                var call: AppCall? = null

                if (result != null && result.has("callId")) {
                    val callId = result.get("callId").asString
                    // Pre-fetch call data or use local construction if backend is slow
                    call = db.collection("app_calls").document(callId).get().await()
                        .toObject(AppCall::class.java)
                }

                if (call == null) {
                    Log.w(TAG, "Backend call creation unavailable; using Firestore signaling fallback")
                    call = createCallFallback(context, receiverUid, type, isGroup, participantIds)
                }

                val finalCall = call ?: throw IllegalStateException("Unable to create call")
                
                // Report event ASYNCHRONOUSLY to not block the UI navigation
                CoroutineScope(Dispatchers.IO).launch {
                    CallStateController.reportCallEvent(
                        MainCallType.SHYNA_LINK,
                        GlobalCallState.CONNECTING,
                        finalCall.id,
                        receiverUid
                    )
                }
                
                withContext(Dispatchers.Main) { onCallCreated(finalCall) }
            } catch (e: Exception) {
                Log.e(TAG, "START_CALL_FAILED", e)
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    private suspend fun createCallFallback(
        context: Context,
        receiverUid: String,
        type: AppCallType,
        isGroup: Boolean,
        requestedParticipantIds: List<String>?
    ): AppCall {
        val authUser = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Please login to start a Shyna call")
        val callerUid = authUser.uid
        val callerDoc = runCatching {
            db.collection("users").document(callerUid).get().await()
        }.getOrNull()
        val receiverDoc = if (!isGroup && receiverUid.isNotBlank()) {
            runCatching { db.collection("users").document(receiverUid).get().await() }.getOrNull()
        } else null

        val participants = (requestedParticipantIds.orEmpty() + callerUid +
            if (!isGroup && receiverUid.isNotBlank()) listOf(receiverUid) else emptyList())
            .filter { it.isNotBlank() }
            .distinct()

        if (participants.size < 2) {
            throw IllegalStateException("Select at least one other Shyna user")
        }

        val callId = "call_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val fallback = AppCall(
            id = callId,
            callerUid = callerUid,
            callerName = callerDoc?.getString("name") ?: authUser.displayName ?: "Shyna User",
            callerPhoto = callerDoc?.getString("photoUrl"),
            receiverUid = if (isGroup) "GROUP" else receiverUid,
            receiverName = if (isGroup) "Group Call" else receiverDoc?.getString("name") ?: "Shyna User",
            receiverPhoto = receiverDoc?.getString("photoUrl"),
            type = type,
            status = AppCallStatus.RINGING,
            roomName = "room_$callId",
            participantIds = participants,
            isGroup = isGroup
        )

        db.collection("app_calls").document(callId).set(fallback).await()
        // Best effort. Foreground users are still reached through the Firestore listener.
        try {
            managerNotify(context, fallback)
        } catch (e: Exception) {
            Log.w(TAG, "Fallback notification failed: ${e.message}")
        }
        return fallback
    }

    private suspend fun managerNotify(context: Context, call: AppCall) {
        if (call.isGroup) return
        LiveKitCallManager(context).notifyReceiver(call)
    }

    fun startMeeting(
        context: Context,
        hostUid: String,
        hostName: String,
        hostPhoto: String?,
        meetingId: String,
        title: String,
        type: AppCallType,
        onCreated: (AppCall) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val cleanMeetingId = meetingId.filter { it.isLetterOrDigit() }
        if (cleanMeetingId.isBlank() || hostUid.isBlank()) {
            onError(IllegalArgumentException("Invalid meeting or host"))
            return
        }

        val callId = "MEETING_$cleanMeetingId"
        val roomName = "room_$callId"
        val call = AppCall(
            id = callId,
            callerUid = hostUid,
            callerName = hostName,
            callerPhoto = hostPhoto,
            receiverUid = "MEETING_ROOM",
            receiverName = title,
            type = type,
            status = AppCallStatus.CONNECTED,
            roomName = roomName,
            participantIds = listOf(hostUid),
            isGroup = true
        )

        if (!meetingStartsInFlight.add(callId)) {
            onError(IllegalStateException("Meeting is already starting"))
            return
        }

        db.collection("app_calls").document(callId)
            .set(call)
            .addOnSuccessListener {
                meetingStartsInFlight.remove(callId)
                Log.d(TAG, "MEETING_ROOM_READY: $callId room=$roomName")
                onCreated(call)
            }
            .addOnFailureListener { e ->
                meetingStartsInFlight.remove(callId)
                Log.e(TAG, "MEETING_ROOM_FAILED: ${e.message}", e)
                onError(e)
            }
    }

    /** Adds the current user to an already-created meeting room and returns its AppCall. */
    fun joinMeeting(
        context: Context,
        userUid: String,
        meetingId: String,
        passcode: String = "",
        onJoined: (AppCall) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val cleanMeetingId = meetingId.filter { it.isLetterOrDigit() }
        if (userUid.isBlank() || cleanMeetingId.isBlank()) {
            onError(IllegalArgumentException("Invalid meeting ID"))
            return
        }
        val callId = "MEETING_$cleanMeetingId"
        val callRef = db.collection("app_calls").document(callId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Prefer server-side meeting validation (ID/passcode/status). If an older
                // deployment does not expose the endpoint yet, preserve compatibility with
                // the existing Firestore meeting flow.
                val joinedByBackend = LiveKitCallManager(context).joinMeetingOnServer(cleanMeetingId, passcode)
                if (!joinedByBackend) {
                    callRef.update(
                        mapOf(
                            "participantIds" to FieldValue.arrayUnion(userUid),
                            "lastUpdatedAt" to System.currentTimeMillis()
                        )
                    ).await()
                }
                val call = callRef.get().await().toObject(AppCall::class.java)
                    ?: throw IllegalStateException("Meeting room is not active")
                if (call.status in setOf(AppCallStatus.ENDED, AppCallStatus.CANCELLED, AppCallStatus.FAILED)) {
                    throw IllegalStateException("Meeting has ended")
                }
                withContext(Dispatchers.Main) { onJoined(call) }
            } catch (e: Exception) {
                Log.e(TAG, "JOIN_MEETING_FAILED meeting=$cleanMeetingId", e)
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    fun leaveGroupCall(userUid: String, callId: String) {
        if (userUid.isBlank() || callId.isBlank()) return
        db.collection("app_calls").document(callId)
            .update(
                mapOf(
                    "participantIds" to FieldValue.arrayRemove(userUid),
                    "lastUpdatedAt" to System.currentTimeMillis()
                )
            )
            .addOnFailureListener { e -> Log.e(TAG, "GROUP_LEAVE_FAILED call=$callId", e) }
    }

    fun leaveMeeting(userUid: String, meetingId: String) {
        val cleanMeetingId = meetingId.filter { it.isLetterOrDigit() }
        if (userUid.isBlank() || cleanMeetingId.isBlank()) return
        db.collection("app_calls").document("MEETING_$cleanMeetingId")
            .update(
                mapOf(
                    "participantIds" to FieldValue.arrayRemove(userUid),
                    "lastUpdatedAt" to System.currentTimeMillis()
                )
            )
    }

    fun endMeeting(meetingId: String, reason: String = "host_ended") {
        val cleanMeetingId = meetingId.filter { it.isLetterOrDigit() }
        if (cleanMeetingId.isBlank()) return
        updateCallStatus("MEETING_$cleanMeetingId", AppCallStatus.ENDED, reason)
        db.collection("meetings")
            .whereEqualTo("meetingId", cleanMeetingId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.firstOrNull()?.reference?.update(
                    mapOf(
                        "status" to "ENDED",
                        "endTime" to System.currentTimeMillis(),
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
            }
    }

    fun listenForIncomingCalls(userUid: String, onIncomingCall: (AppCall) -> Unit) {
        callListener?.remove()
        Log.d(TAG, "LISTENING_FOR_CALLS_START: uid=$userUid")
        // All current Shyna calls store participantIds. Listening by membership supports
        // direct as well as group calls and avoids a second, duplicate listener. Status is
        // filtered client-side so this query does not require a composite status index.
        callListener = db.collection("app_calls")
            .whereArrayContains("participantIds", userUid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "SIGNALING_LISTEN_FAILED", error)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { dc ->
                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val call = dc.document.toObject(AppCall::class.java)
                        val now = System.currentTimeMillis()
                        val diff = kotlin.math.abs(now - call.timestamp)
                        Log.d(TAG, "FIRESTORE_SIGNAL_RECEIVED: id=${call.id} status=${call.status} skew=${now - call.timestamp}ms")

                        if (call.status == AppCallStatus.RINGING && call.callerUid != userUid && diff < 120000) {
                            // Let the UI/service owner mark the call INCOMING after it has
                            // passed duplicate/busy checks. Marking it here caused MainActivity
                            // to classify the brand-new call as "already handling" and skip UI.
                            onIncomingCall(call)
                        }
                    }
                }
            }
    }

    fun updateCallStatus(callId: String, status: AppCallStatus, reason: String? = null) {
        Log.d(TAG, "UPDATING_CALL_STATUS: id=$callId target=$status reason=$reason")
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>(
            "status" to status.name,
            "lastUpdatedAt" to now
        )
        if (status == AppCallStatus.ACCEPTED) {
            updates["answeredAt"] = now
        }
        if (status == AppCallStatus.CONNECTED) {
            updates["connectedAt"] = now
        }
        if (status in setOf(
                AppCallStatus.ENDED, AppCallStatus.MISSED, AppCallStatus.DECLINED,
                AppCallStatus.CANCELLED, AppCallStatus.FAILED, AppCallStatus.BUSY,
                AppCallStatus.NO_ANSWER, AppCallStatus.UNAVAILABLE, AppCallStatus.REJECTED
            )) {
            updates["endedAt"] = now
        }
        reason?.let { updates["endReason"] = it }

        db.collection("app_calls").document(callId)
            .update(updates)
            .addOnSuccessListener { Log.d(TAG, "STATUS_UPDATED_SUCCESS: $callId -> $status") }
            .addOnFailureListener { e -> Log.e(TAG, "STATUS_UPDATE_FAILED: id=$callId target=$status error=${e.message}") }
    }

    fun updateCallType(callId: String, type: AppCallType) {
        db.collection("app_calls").document(callId)
            .update(mapOf("type" to type.name, "lastUpdatedAt" to System.currentTimeMillis()))
            .addOnFailureListener { e -> Log.e(TAG, "TYPE_UPDATE_FAILED: id=$callId error=${e.message}") }
    }

    fun listenToCall(callId: String, onUpdate: (AppCall) -> Unit): ListenerRegistration {
        return db.collection("app_calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "CALL_LISTENER_FAILED id=$callId", error)
                    return@addSnapshotListener
                }
                snapshot?.toObject(AppCall::class.java)?.let { onUpdate(it) }
            }
    }

    fun cleanup() {
        callListener?.remove()
        callListener = null
    }

    fun saveCallHistory(call: AppCall, currentUid: String) {
        if (call.id.startsWith("MEETING_") || call.receiverUid == "MEETING_ROOM") return
        val historyEntry = hashMapOf(
            "callId" to call.id,
            "callerUid" to call.callerUid,
            "callerName" to call.callerName,
            "callerPhoto" to call.callerPhoto,
            "receiverUid" to call.receiverUid,
            "receiverName" to call.receiverName,
            "receiverPhoto" to call.receiverPhoto,
            "type" to call.type.name,
            "status" to call.status.name,
            "duration" to call.duration,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "direction" to if (call.callerUid == currentUid) "outgoing" else "incoming"
        )

        db.collection("users").document(currentUid)
            .collection("call_history").document(call.id)
            .set(historyEntry, SetOptions.merge())
            .addOnFailureListener { e -> Log.e(TAG, "CALL_HISTORY_SAVE_FAILED", e) }
    }

    fun saveCallMessageToChat(call: AppCall) {
        if (call.isGroup || call.id.startsWith("MEETING_") || call.receiverUid == "MEETING_ROOM") return
        val chatId = if (call.callerUid < call.receiverUid) "${call.callerUid}_${call.receiverUid}" else "${call.receiverUid}_${call.callerUid}"

        val msg = mapOf(
            "text" to if (call.type == AppCallType.VIDEO) "Video call" else "Audio call",
            "senderId" to call.callerUid,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "type" to "CALL",
            "callId" to call.id,
            "callType" to call.type.name,
            "callStatus" to call.status.name,
            "callDuration" to call.duration
        )

        db.collection("chats").document(chatId).collection("messages").add(msg)

        val statusLabel = when (call.status) {
            AppCallStatus.MISSED, AppCallStatus.NO_ANSWER -> "Missed ${call.type.name.lowercase()} call"
            AppCallStatus.DECLINED -> "Declined ${call.type.name.lowercase()} call"
            else -> "${if (call.type == AppCallType.VIDEO) "📹" else "📞"} ${call.type.name.lowercase()} call"
        }
        db.collection("chats").document(chatId)
            .set(mapOf("lastMessage" to statusLabel, "timestamp" to com.google.firebase.Timestamp.now()), SetOptions.merge())
    }
}
