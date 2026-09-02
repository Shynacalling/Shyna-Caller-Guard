package com.example.callruleblocker

import android.content.Intent
import android.app.KeyguardManager
import android.os.Build
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import java.util.concurrent.ConcurrentHashMap
import com.example.callruleblocker.call.CallControlCenter
import com.example.callruleblocker.call.CallHolder
import com.example.callruleblocker.data.RuleRepository
import com.example.callruleblocker.data.BlockedCallStore
import com.example.callruleblocker.sim.SimSlotResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class InCallServiceImpl : InCallService() {
    private val callCallbacks = ConcurrentHashMap<Call, Call.Callback>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ruleRepository by lazy { RuleRepository(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        CallControlCenter.attach(this)
    }

    override fun onDestroy() {
        callCallbacks.forEach { (call, callback) -> runCatching { call.unregisterCallback(callback) } }
        callCallbacks.clear()
        OngoingCallNotification.cancel(this)
        serviceScope.cancel()
        CallControlCenter.detach(this)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("super.onCallAudioStateChanged(audioState)"))
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallControlCenter.updateAudioState(audioState)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallControlCenter.add(call)
        val notificationCallback = object : Call.Callback() {
            override fun onStateChanged(changedCall: Call, state: Int) {
                serviceScope.launch {
                    when (state) {
                        Call.STATE_RINGING -> OngoingCallNotification.showIncoming(this@InCallServiceImpl, changedCall)
                        Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING ->
                            OngoingCallNotification.show(this@InCallServiceImpl, changedCall)
                        Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                            val replacement = CallControlCenter.allCalls.value.firstOrNull {
                                it !== changedCall && (it.state != Call.STATE_DISCONNECTED) && (it.state != Call.STATE_DISCONNECTING)
                            }
                            if (replacement != null) {
                                CallHolder.set(replacement)
                                OngoingCallNotification.show(this@InCallServiceImpl, replacement)
                            } else {
                                if (CallHolder.currentCall.value === changedCall) CallHolder.set(null)
                                OngoingCallNotification.cancel(this@InCallServiceImpl)
                                
                                saveCallHistory(changedCall)

                                // AUTO-OPEN APP ON DISCONNECT: Brings the app to foreground when the talk ends, 
                                // ensuring the user sees the dialer/recents exactly like Samsung/Pixel.
                                val intent = Intent(this@InCallServiceImpl, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                startActivity(intent)
                            }
                        }
                        else -> {}
                    }
                }
            }
            override fun onDetailsChanged(changedCall: Call, details: Call.Details) {
                serviceScope.launch {
                    if (changedCall.state == Call.STATE_RINGING) {
                        OngoingCallNotification.showIncoming(this@InCallServiceImpl, changedCall)
                    } else {
                        OngoingCallNotification.show(this@InCallServiceImpl, changedCall)
                    }
                }
            }
        }
        call.registerCallback(notificationCallback)
        callCallbacks[call] = notificationCallback
        
        // DO NOT show notification here for incoming calls. 
        // We wait for the rule decision below to prevent "First Ring" leak.
        if (call.state != Call.STATE_RINGING) {
            serviceScope.launch {
                OngoingCallNotification.show(this@InCallServiceImpl, call)
            }
        }

        val isIncoming = (call.state == Call.STATE_RINGING) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && call.details.callDirection == Call.Details.DIRECTION_INCOMING)
        if (!isIncoming) {
            CallHolder.set(call)
            launchCallScreen()
            return
        }

        val number = call.details.handle?.schemeSpecificPart
        if (number == null) {
            CallHolder.set(call)
            serviceScope.launch { OngoingCallNotification.showIncoming(this@InCallServiceImpl, call) }
            if (shouldOpenFullScreenForIncoming()) launchCallScreen()
            return
        }

        serviceScope.launch {
            // FAST PATH: Check specific blocked numbers list immediately
            // This ensures zero-delay cutting if CallScreeningService was bypassed by OS.
            val specificBlocked = ruleRepository.blockedSpecificNumbers()
            val simplifiedNumber = number.filter { it.isDigit() }.takeLast(10)
            
            if (specificBlocked.contains(simplifiedNumber)) {
                Log.d("ShynaCall", "[INCALL] FAST-REJECTING (Pre-check): $number")
                BlockedCallStore(applicationContext).record(number, 0)
                OngoingCallNotification.cancel(this@InCallServiceImpl)
                call.reject(false, null)
                return@launch
            }

            var simSlot = 0
            val decision = runCatching {
                withTimeoutOrNull(500) {
                    @Suppress("MissingPermission")
                    simSlot = SimSlotResolver.resolveSlot(applicationContext, call.details.accountHandle)
                    ruleRepository.decide(number, simSlot)
                }
            }.getOrNull() ?: "ALLOW"

            if (call.state == Call.STATE_DISCONNECTED || call.state == Call.STATE_DISCONNECTING) return@launch
            if (decision == "BLOCK") {
                Log.d("ShynaCall", "[INCALL] BLOCKING: $number")
                BlockedCallStore(applicationContext).record(number, simSlot)
                OngoingCallNotification.cancel(this@InCallServiceImpl)
                call.reject(false, null)
            } else {
                CallHolder.set(call)
                OngoingCallNotification.showIncoming(this@InCallServiceImpl, call)
                if (shouldOpenFullScreenForIncoming()) launchCallScreen()
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        callCallbacks.remove(call)?.let { callback -> runCatching { call.unregisterCallback(callback) } }
        CallControlCenter.remove(call)
        val replacement = CallControlCenter.allCalls.value.firstOrNull {
            it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING
        }
        if (replacement != null) {
            CallHolder.set(replacement)
            serviceScope.launch { OngoingCallNotification.show(this@InCallServiceImpl, replacement) }
        } else {
            if (CallHolder.currentCall.value == call) CallHolder.set(null)
            OngoingCallNotification.cancel(this)
        }
    }

    private fun shouldOpenFullScreenForIncoming(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        return powerManager?.isInteractive != true || keyguardManager?.isKeyguardLocked == true
    }

    private fun launchCallScreen() {
        startActivity(Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
    }

    private fun saveCallHistory(call: Call) {
        val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val details = call.details
        
        val number = details.handle?.schemeSpecificPart ?: "Unknown"
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            details.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            false // Simplified fallback
        }
        
        val connectTime = details.connectTimeMillis
        val durationSeconds = if (connectTime > 0) (System.currentTimeMillis() - connectTime) / 1000 else 0
        
        val callData = hashMapOf(
            "otherUserId" to number, // In a real app, you'd resolve number to UID
            "type" to if (VideoProfile.isVideo(details.videoState)) "video" else "audio",
            "direction" to if (isIncoming) "incoming" else "outgoing",
            "status" to "completed",
            "duration" to durationSeconds,
            "timestamp" to Timestamp.now()
        )

        db.collection("users")
            .document(senderId)
            .collection("callHistory")
            .add(callData)
            .addOnFailureListener { it.printStackTrace() }
    }
}
