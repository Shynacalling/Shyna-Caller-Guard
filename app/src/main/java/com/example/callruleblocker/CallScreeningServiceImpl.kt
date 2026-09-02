package com.example.callruleblocker

import android.telecom.Call
import android.telecom.CallScreeningService
import android.os.Build
import android.util.Log
import android.content.Context
import com.example.callruleblocker.data.BlockedCallStore
import com.example.callruleblocker.data.RuleRepository
import com.example.callruleblocker.sim.SimSlotResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Professional Call Screening Service for microsecond-fast blocking.
 * This service is called by the Android system BEFORE the phone rings.
 */
class CallScreeningServiceImpl : CallScreeningService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ruleRepository by lazy { RuleRepository(applicationContext) }
    private val blockedCallStore by lazy { BlockedCallStore(applicationContext) }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        if (number == null) {
            respondToCall(callDetails, CallScreeningService.CallResponse.Builder().build())
            return
        }

        serviceScope.launch {
            val advancedPrefs = applicationContext.getSharedPreferences("advanced_feature_control_v5", Context.MODE_PRIVATE)
            val fastBlockingEnabled = advancedPrefs.getBoolean("feature_fast_blocking", true)
            val noRingCutEnabled = advancedPrefs.getBoolean("feature_no_ring_cut", true)

            // OPTIMIZATION: Immediate pre-check for specific blocked numbers regardless of SIM
            // to ensure "Zero-Ring" blocking for known spammers.
            if (fastBlockingEnabled) {
                val specificBlocked = ruleRepository.blockedSpecificNumbers()
                val simplifiedNumber = number.filter { it.isDigit() }.takeLast(10)
                
                if (specificBlocked.contains(simplifiedNumber)) {
                    Log.d("ShynaCall", "[SCREENING] FAST-BLOCKING (Pre-check): $number")
                    val response = CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipCallLog(true)
                        .setSkipNotification(true)
                        .apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && noRingCutEnabled) {
                                setSilenceCall(true)
                            }
                        }
                        .build()
                    respondToCall(callDetails, response)
                    blockedCallStore.record(number, 0) // Record on default slot for fast path
                    return@launch
                }
            }

            val decision = runCatching {
                withTimeoutOrNull(400) { // Slightly tighter timeout for faster response
                    @Suppress("MissingPermission")
                    val simSlot = SimSlotResolver.resolveSlot(applicationContext, callDetails.accountHandle)
                    ruleRepository.decide(number, simSlot)
                }
            }.getOrNull() ?: "ALLOW"

            val responseBuilder = CallResponse.Builder()
            if (decision == "BLOCK") {
                Log.d("ShynaCall", "[SCREENING] BLOCKING: $number")
                
                @Suppress("MissingPermission")
                val simSlot = SimSlotResolver.resolveSlot(applicationContext, callDetails.accountHandle)
                blockedCallStore.record(number, simSlot)

                responseBuilder.setDisallowCall(true)
                responseBuilder.setRejectCall(true)
                responseBuilder.setSkipCallLog(true)
                responseBuilder.setSkipNotification(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    responseBuilder.setSilenceCall(true)
                }
            } else {
                Log.d("ShynaCall", "[SCREENING] ALLOWING: $number")
            }

            respondToCall(callDetails, responseBuilder.build())
        }
    }
}
