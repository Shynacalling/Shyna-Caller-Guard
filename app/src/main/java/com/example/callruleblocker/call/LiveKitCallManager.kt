package com.example.callruleblocker.call

import android.content.Context
import android.util.Log
import com.example.callruleblocker.data.LiveKitConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class LiveKitCallManager(private val context: Context) {
    private var room: Room? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    private fun getTokenServerUrl(): String {
        val prefs = context.getSharedPreferences("smart_communication_v2", Context.MODE_PRIVATE)
        return prefs.getString("server_url", LiveKitConfig.TOKEN_SERVER_URL) ?: LiveKitConfig.TOKEN_SERVER_URL
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun fetchToken(roomName: String, userId: String, callId: String): String? = withContext(Dispatchers.IO) {
        Log.d("ShynaCall", "TOKEN_REQUEST_STARTED room=$roomName user=$userId call=$callId")
        
        if (!isNetworkAvailable()) {
            Log.e("ShynaCall", "TOKEN_REQUEST_FAILED reason=no_internet")
            return@withContext null
        }

        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
        val idToken = try {
            user.getIdToken(false).await().token
        } catch (e: Exception) {
            Log.e("ShynaCall", "ID_TOKEN_FETCH_FAILED: ${e.message}")
            null
        } ?: return@withContext null

        val baseUrl = getTokenServerUrl().trimEnd('/')
        val url = "$baseUrl/token"
        
        val json = JsonObject().apply {
            addProperty("roomName", roomName)
            addProperty("participantName", userId)
            addProperty("callId", callId)
        }

        // Retry loop to handle Render server cold start
        var attempts = 0
        val maxAttempts = 3

        while (attempts < maxAttempts) {
            attempts++
            try {
                Log.d("ShynaCall", "TOKEN_REQUEST_ATTEMPT $attempts of $maxAttempts to $url")
                val body = gson.toJson(json).toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $idToken")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                response.use { res ->
                    if (res.code == 401) {
                        Log.e("ShynaCall", "TOKEN_REQUEST_FAILED reason=UNAUTHORIZED (401). Check Render Server Variables.")
                        return@withContext null
                    }
                    if (!res.isSuccessful) {
                        Log.e("ShynaCall", "TOKEN_REQUEST_FAILED code=${res.code} url=$url")
                        if (attempts < maxAttempts) {
                            delay(2000)
                            return@use
                        }
                        return@withContext null
                    }

                    val responseData = res.body?.string().orEmpty()
                    if (responseData.isBlank()) {
                        Log.e("ShynaCall", "TOKEN_REQUEST_FAILED reason=blank_body")
                        if (attempts < maxAttempts) {
                            delay(2000)
                            return@use
                        }
                        return@withContext null
                    }

                    val result = gson.fromJson(responseData, JsonObject::class.java)
                    val token = result.get("token")?.asString
                    if (!token.isNullOrBlank()) {
                        Log.d("ShynaCall", "TOKEN_REQUEST_SUCCESS on attempt $attempts")
                        return@withContext token
                    }
                }
            } catch (e: Exception) {
                Log.w("ShynaCall", "TOKEN_REQUEST_EXCEPTION on attempt $attempts: ${e.message}")
                if (attempts < maxAttempts) {
                    delay(2000)
                }
            }
        }
        Log.e("ShynaCall", "TOKEN_REQUEST_ALL_RETRIES_FAILED")
        null
    }

    suspend fun joinRoom(roomName: String, userId: String, callId: String): Room {
        Log.d("ShynaCall", "JOIN_FLOW_STARTED ROOM_ID=$roomName")
        Log.d("ShynaCall", "RTC_ENGINE_INIT_START")
        
        val token = fetchToken(roomName, userId, callId) 
        if (token == null) {
            Log.e("ShynaCall", "ROOM_CONNECT_FAILED reason=token_fetch_failed")
            throw IllegalStateException("Failed to fetch token")
        }
        
        Log.d("ShynaCall", "ROOM_CONNECT_START url=${LiveKitConfig.URL}")
        try {
            val r = LiveKit.create(
                appContext = context.applicationContext,
                options = io.livekit.android.RoomOptions(
                    adaptiveStream = true,
                    dynacast = true
                )
            )
            Log.d("ShynaCall", "RTC_ENGINE_INIT_SUCCESS. Attempting connection to ${LiveKitConfig.URL}")
            
            r.connect(LiveKitConfig.URL, token)
            Log.d("ShynaCall", "ROOM_CONNECT_SUCCESS sid=${r.sid} state=${r.state}")
            room = r
            return r
        } catch (e: Exception) {
            Log.e("ShynaCall", "ROOM_CONNECT_FAILED error=${e.message}", e)
            val msg = e.message?.lowercase() ?: ""
            if (msg.contains("401") || msg.contains("unauthorized")) {
                Log.e("ShynaCall", "CRITICAL: LiveKit server rejected token (401). Please verify LIVEKIT_API_KEY and LIVEKIT_API_SECRET on Render dashboard for project kn60m55l.")
            } else if (msg.contains("region") || msg.contains("fetch")) {
                Log.e("ShynaCall", "NETWORK_ERROR: Could not reach LiveKit Cloud. Check project ID kn60m55l and internet connection.")
            }
            throw e
        }
    }

    fun leaveRoom() {
        room?.disconnect()
        room = null
    }

    suspend fun notifyReceiver(call: AppCall): Boolean = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext false
        val idToken = try {
            user.getIdToken(false).await().token ?: return@withContext false
        } catch (e: Exception) {
            return@withContext false
        }

        val baseUrl = getTokenServerUrl().trimEnd('/')
        val url = "$baseUrl/notify-call"

        val json = JsonObject().apply {
            addProperty("callId", call.id)
            addProperty("receiverUid", call.receiverUid)
            addProperty("callerUid", call.callerUid)
            addProperty("callerName", call.callerName)
            addProperty("callType", call.type.name)
        }

        val body = gson.toJson(json).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $idToken")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                Log.d("ShynaCall", "FCM_NOTIFY_SERVER: success=$success code=${response.code}")
                success
            }
        } catch (e: Exception) {
            Log.e("ShynaCall", "FCM_NOTIFY_FAILED", e)
            false
        }
    }

    suspend fun joinMeetingOnServer(meetingId: String, passcode: String): Boolean = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext false
        val idToken = try { user.getIdToken(false).await().token ?: return@withContext false } catch (_: Exception) { return@withContext false }
        val json = JsonObject().apply {
            addProperty("meetingId", meetingId)
            addProperty("passcode", passcode)
        }
        val baseUrl = getTokenServerUrl().trimEnd('/')
        val request = Request.Builder()
            .url("$baseUrl/api/meetings/join")
            .addHeader("Authorization", "Bearer $idToken")
            .post(gson.toJson(json).toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val ok = response.isSuccessful
                if (!ok) Log.w("ShynaCall", "MEETING_JOIN_BACKEND_FAILED code=${response.code}")
                ok
            }
        } catch (e: Exception) {
            Log.w("ShynaCall", "MEETING_JOIN_BACKEND_UNAVAILABLE: ${e.message}")
            false
        }
    }

    suspend fun createCall(receiverUid: String, type: AppCallType, isGroup: Boolean = false, participantIds: List<String>? = null): JsonObject? = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
        val idToken = user.getIdToken(false).await().token ?: return@withContext null

        val json = JsonObject().apply {
            addProperty("receiverUid", receiverUid)
            addProperty("type", type.name)
            addProperty("isGroup", isGroup)
            if (participantIds != null) {
                val array = com.google.gson.JsonArray()
                participantIds.forEach { array.add(it) }
                add("participantIds", array)
            }
        }

        val baseUrl = getTokenServerUrl().trimEnd('/')
        val url = "$baseUrl/api/calls/create"

        val body = gson.toJson(json).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $idToken")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ShynaCall", "CREATE_CALL_BACKEND_FAILED code=${response.code} url=$url")
                    return@withContext null
                }
                val data = response.body?.string() ?: return@withContext null
                gson.fromJson(data, JsonObject::class.java)
            }
        } catch (e: Exception) {
            Log.e("ShynaCall", "CREATE_CALL_BACKEND_EXCEPTION: ${e.message}", e)
            null
        }
    }

    suspend fun callAction(callId: String, action: String): Boolean = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext false
        val idToken = try { user.getIdToken(false).await().token } catch (_: Exception) { null }

        var backendSuccess = false
        if (!idToken.isNullOrBlank()) {
            val baseUrl = getTokenServerUrl().trimEnd('/')
            val url = "$baseUrl/api/calls/$callId/$action"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $idToken")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            backendSuccess = try {
                client.newCall(request).execute().use { response ->
                    val ok = response.isSuccessful
                    if (!ok) Log.w("ShynaCall", "CALL_ACTION_BACKEND_FAILED action=$action code=${response.code}")
                    ok
                }
            } catch (e: Exception) {
                Log.w("ShynaCall", "CALL_ACTION_BACKEND_EXCEPTION action=$action error=${e.message}")
                false
            }
        }

        if (backendSuccess) return@withContext true

        // Resilient signaling fallback. This keeps accept/reject/end controls functional
        // if the HTTP lifecycle endpoint is unavailable while Firestore is reachable.
        val now = System.currentTimeMillis()
        val status = when (action.lowercase()) {
            "accept" -> AppCallStatus.ACCEPTED
            "decline" -> AppCallStatus.DECLINED
            "cancel" -> AppCallStatus.CANCELLED
            "end" -> AppCallStatus.ENDED
            "missed" -> AppCallStatus.MISSED
            "no-answer", "no_answer" -> AppCallStatus.NO_ANSWER
            "busy" -> AppCallStatus.BUSY
            else -> null
        } ?: return@withContext false

        val updates = mutableMapOf<String, Any>(
            "status" to status.name,
            "lastUpdatedAt" to now
        )
        if (status == AppCallStatus.ACCEPTED) {
            updates["answeredAt"] = now
        }
        if (status in setOf(
                AppCallStatus.DECLINED, AppCallStatus.CANCELLED, AppCallStatus.ENDED,
                AppCallStatus.MISSED, AppCallStatus.NO_ANSWER, AppCallStatus.BUSY
            )) {
            updates["endedAt"] = now
        }

        return@withContext try {
            FirebaseFirestore.getInstance().collection("app_calls").document(callId).update(updates).await()
            Log.d("ShynaCall", "CALL_ACTION_FIRESTORE_FALLBACK_SUCCESS action=$action id=$callId")
            true
        } catch (e: Exception) {
            Log.e("ShynaCall", "CALL_ACTION_FIRESTORE_FALLBACK_FAILED action=$action id=$callId", e)
            false
        }
    }
}
