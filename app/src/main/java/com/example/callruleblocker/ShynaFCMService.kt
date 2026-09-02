package com.example.callruleblocker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.RemoteMessage
import com.example.callruleblocker.call.CallStateController
import com.example.callruleblocker.call.GlobalCallState
import com.example.callruleblocker.call.CallSignalingManager
import com.example.callruleblocker.call.AppCallStatus
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.app.Notification
import androidx.core.content.ContextCompat

class ShynaFCMService : FirebaseMessagingService() {
    private companion object {
        const val TAG = "ShynaCall"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM_TOKEN_RECEIVED: $token")
        
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "FCM_TOKEN_UPDATED_ON_REFRESH") }
                .addOnFailureListener { e -> Log.e(TAG, "FCM_TOKEN_UPDATE_FAILED", e) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "FCM_MESSAGE_RECEIVED")
        Log.d(TAG, "FCM_DATA=${message.data}")

        val data = message.data
        val eventType = data["type"] ?: "INCOMING_CALL"
        val callId = data["callId"] ?: return

        // Call lifecycle FCMs are synchronization signals, NOT new incoming calls.
        // Firestore remains the source of truth for the call screen state.
        if (eventType != "INCOMING_CALL") {
            Log.d(TAG, "FCM_CALL_LIFECYCLE type=$eventType callId=$callId")
            if (eventType in setOf(
                    "CALL_DECLINED", "CALL_CANCELLED", "CALL_ENDED", "CALL_MISSED",
                    "CALL_NO_ANSWER", "CALL_BUSY", "CALL_FAILED", "CALL_REJECTED"
                ) && com.example.callruleblocker.call.AppCallService.currentRingingCallId == callId
            ) {
                com.example.callruleblocker.call.AppCallService.stop(this)
            }
            return
        }

        val callerName = data["callerName"] ?: "Shyna User"
        val callType = data["callType"] ?: "VOICE"
        val callerUid = data["callerUid"] ?: ""

        Log.d(TAG, "FCM_CALL_ID=$callId FCM_CALLER_UID=$callerUid FCM_CALLER_NAME=$callerName FCM_CALL_TYPE=$callType")

        val receiverUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.w(TAG, "FCM_RECEIVED_BUT_NO_USER_LOGGED_IN")
            return
        }

        // BLOCK CHECK STARTED
        Log.d(TAG, "BLOCK_CHECK_STARTED for $callerUid")
        if (callerUid.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(receiverUid).collection("blockedUsers").document(callerUid)
                .get()
                .addOnSuccessListener { d ->
                    val isBlocked = d.exists()
                    Log.d(TAG, "BLOCK_CHECK_RESULT=${if(isBlocked) "BLOCKED" else "ALLOWED"}")
                    
                    if (isBlocked) {
                        Log.d(TAG, "Caller is blocked. Rejecting call.")
                        CallSignalingManager.updateCallStatus(callId, AppCallStatus.DECLINED, "caller_blocked")
                        com.example.callruleblocker.call.AppCallService.stop(this@ShynaFCMService)
                    } else {
                        handleIncomingFcm(callId, callerName, callType)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "BLOCK_CHECK_FAILED", e)
                    // Safe fallback: Allow call if block check fails to avoid missing important calls
                    handleIncomingFcm(callId, callerName, callType)
                }
        } else {
            handleIncomingFcm(callId, callerName, callType)
        }
    }

    private fun handleIncomingFcm(callId: String, callerName: String, callType: String) {
        // IDEMPOTENT FCM CHECK
        val activeSession = CallStateController.activeSession.value
        val currentState = CallStateController.globalState.value
        
        val isSameCall = activeSession?.callId == callId || com.example.callruleblocker.call.AppCallService.currentRingingCallId == callId
        // If we are already handling THIS call (ringing or connected), don't show another notification.
        if (isSameCall && currentState != GlobalCallState.IDLE) {
            Log.d(TAG, "FCM: Call $callId already active in state $currentState. Ignoring duplicate FCM.")
            return
        }

        // If we are busy with a DIFFERENT call, auto-reject this one.
        val isTrulyBusy = currentState != GlobalCallState.IDLE && currentState != GlobalCallState.ENDED && !isSameCall
        if (isTrulyBusy) {
            Log.d(TAG, "FCM: User Truly Busy with ${activeSession?.callId}. Auto-rejecting $callId.")
            CallSignalingManager.updateCallStatus(callId, AppCallStatus.DECLINED, "user_busy_fcm")
            return
        }

        // START RINGING IMMEDIATELY
        Log.d(TAG, "STARTING_RINGING_SERVICE callId=$callId")
        val isVideo = callType.uppercase() == "VIDEO"
        com.example.callruleblocker.call.AppCallService.startRinging(this, callId, callerName, isVideo)
    }

}
