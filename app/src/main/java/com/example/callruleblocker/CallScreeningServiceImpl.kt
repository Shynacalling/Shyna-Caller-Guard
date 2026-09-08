package com.example.callruleblocker

import android.Manifest
import android.telecom.Call
import android.telecom.CallScreeningService
import android.os.Build
import android.util.Log
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.callruleblocker.data.BlockedCallStore
import com.example.callruleblocker.data.Rule
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

        if (findContactId(number, applicationContext) != null) {
            respondToCall(callDetails, CallResponse.Builder().build())
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

            @Suppress("MissingPermission")
            val simSlot = SimSlotResolver.resolveSlot(applicationContext, callDetails.accountHandle)

            val decision = runCatching {
                withTimeoutOrNull(400) { // Slightly tighter timeout for faster response
                    ruleRepository.decide(number, simSlot)
                }
            }.getOrNull() ?: "ALLOW"

            val simplifiedNumber = number.filter { it.isDigit() }.takeLast(10)
            val unknownPrefs = applicationContext.getSharedPreferences("sim_unknown_block_prefs",
                MODE_PRIVATE
            )
            val unknownBlockActive = unknownPrefs.getBoolean("unknown_block_active", false)

            var finalDecision = decision
            if (finalDecision == "ALLOW" && unknownBlockActive) {
                val isBlockedOnThisSim = ruleRepository.isBlockedOnSim(simSlot, simplifiedNumber)
                if (!isBlockedOnThisSim) {
                    val countsPrefs = applicationContext.getSharedPreferences("unknown_call_counts_sim_$simSlot",
                        MODE_PRIVATE
                    )
                    val count = countsPrefs.getInt(simplifiedNumber, 0) + 1
                    countsPrefs.edit().putInt(simplifiedNumber, count).apply()

                    if (count >= 2) {
                        ruleRepository.addRule(Rule(simSlotIndex = simSlot, matchType = "SPECIFIC_NUMBER", matchValue = simplifiedNumber, action = "BLOCK"))
                        finalDecision = "BLOCK"
                        Log.d("ShynaCall", "[AUTO-BLOCK] Unknown number $number permanently blocked on SIM $simSlot after 2 calls")
                    }
                }
            }

            val responseBuilder = CallResponse.Builder()
            if (finalDecision == "BLOCK") {
                Log.d("ShynaCall", "[SCREENING] BLOCKING & SKIPPING LOG: $number")
                
                @Suppress("MissingPermission")
                val simSlot = SimSlotResolver.resolveSlot(applicationContext, callDetails.accountHandle)
                blockedCallStore.record(number, simSlot)

                responseBuilder.setDisallowCall(true)
                responseBuilder.setRejectCall(true)
                responseBuilder.setSkipCallLog(true)
                responseBuilder.setSkipNotification(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && noRingCutEnabled) {
                    responseBuilder.setSilenceCall(true)
                }
            } else {
                Log.d("ShynaCall", "[SCREENING] ALLOWING: $number")
            }

            respondToCall(callDetails, responseBuilder.build())
        }
    }

    private fun findContactId(number: String, context: Context): Long? = runCatching {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon().appendPath(number).build()
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.CONTACT_ID),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    }.getOrNull()
}
