package com.example.callruleblocker.sim

import android.content.Context
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.delay

/**
 * Maps an incoming/outgoing call to a physical SIM slot index (0 = SIM 1,
 * 1 = SIM 2).
 *
 * Now that this app is the DEFAULT PHONE APP, Call.Details.getAccountHandle()
 * is reliably populated by the system (confirmed: plain call-screening apps
 * on this device always got a null handle; default-dialer apps get the real
 * one). So handle-based detection is now the PRIMARY path — fast and exact.
 * The call-state poll from the earlier call-screening approach is kept only
 * as a short fallback for the rare case a handle is missing.
 */
object SimSlotResolver {

    @RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    suspend fun resolveSlot(context: Context, handle: PhoneAccountHandle?): Int {
        val subManager = context.getSystemService(SubscriptionManager::class.java)
        val activeSubs = try {
            subManager?.activeSubscriptionInfoList ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }

        Log.d(
            "CallRuleBlocker",
            "resolveSlot: handle.id=${handle?.id} handle.component=${handle?.componentName} " +
                "activeSubs=${activeSubs.map { "sub${it.subscriptionId}->slot${it.simSlotIndex}" }}"
        )

        if (activeSubs.isEmpty()) return 0
        if (activeSubs.size == 1) {
            // Only one SIM active — no ambiguity regardless of OEM quirks.
            return activeSubs[0].simSlotIndex
        }

        // Signal 1 (PRIMARY, now that we're the default dialer): framework API.
        if (handle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            val subId = try {
                telephonyManager?.getSubscriptionId(handle) ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
            } catch (_: Exception) {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }
            Log.d("CallRuleBlocker", "resolveSlot: TelephonyManager.getSubscriptionId -> $subId")
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                activeSubs.firstOrNull { it.subscriptionId == subId }
                    ?.let { return it.simSlotIndex }
            }
        }

        // Signal 2: PhoneAccount extras.
        if (handle != null) {
            try {
                val telecomManager = context.getSystemService(TelecomManager::class.java)
                val account = telecomManager?.getPhoneAccount(handle)
                val subIndex = account?.extras?.getInt("android.telephony.extra.SUBSCRIPTION_INDEX", -1) ?: -1
                Log.d("CallRuleBlocker", "resolveSlot: PhoneAccount extras subIndex -> $subIndex")
                if (subIndex != -1) {
                    activeSubs.firstOrNull { it.subscriptionId == subIndex }
                        ?.let { return it.simSlotIndex }
                }
            } catch (_: Exception) {
                // ignore — move to next fallback
            }
        }

        // Signal 3: PhoneAccountHandle.id as a raw subscriptionId string.
        handle?.id?.toIntOrNull()?.let { subId ->
            activeSubs.firstOrNull { it.subscriptionId == subId }?.let { return it.simSlotIndex }
        }

        // Signal 4: ICC id match.
        handle?.id?.let { rawId ->
            activeSubs.firstOrNull { sub ->
                sub.iccId?.takeIf { it.isNotEmpty() }?.let { rawId.contains(it, ignoreCase = true) } == true
            }?.let { return it.simSlotIndex }
        }

        // Fallback only: call-state poll, shortened since handle should
        // normally have resolved it above now that we're the default dialer.
        // Kept short (≤200ms) so a blocked call never hangs before being cut.
        Log.d("CallRuleBlocker", "resolveSlot: handle-based signals failed, polling call state as fallback")
        val baseTm = context.getSystemService(TelephonyManager::class.java)
        if (baseTm != null) {
            repeat(4) {
                for (sub in activeSubs) {
                    val state = try {
                        @Suppress("DEPRECATION")
                        baseTm.createForSubscriptionId(sub.subscriptionId)?.callState
                    } catch (_: Exception) {
                        null
                    }
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        return sub.simSlotIndex
                    }
                }
                delay(50)
            }
        }

        // Last resort.
        Log.d("CallRuleBlocker", "resolveSlot: all signals failed, defaulting to first active slot")
        return activeSubs.firstOrNull()?.simSlotIndex ?: 0
    }
}
