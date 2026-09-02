package com.example.callruleblocker.call

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile

data class SimChoice(
    val index: Int,
    val label: String,
    val handle: PhoneAccountHandle
)

object SimCallManager {
    const val PREFS = "dialer_preferences"
    const val KEY_DEFAULT_SIM = "default_sim_mode"
    const val MODE_ASK = "ASK"
    const val MODE_SIM_PREFIX = "SIM_"

    @SuppressLint("MissingPermission")
    fun getChoices(context: Context): List<SimChoice> {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        return runCatching {
            telecom.callCapablePhoneAccounts.mapIndexed { index, handle ->
                val account = telecom.getPhoneAccount(handle)
                val rawLabel = account?.label?.toString()?.trim().orEmpty()
                SimChoice(
                    index = index,
                    label = rawLabel.ifBlank { "SIM ${index + 1}" },
                    handle = handle
                )
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    fun placeCall(context: Context, number: String, simIndex: Int? = null) {
        val telecom = context.getSystemService(TelecomManager::class.java)
        val extras = Bundle()

        val targetIndex = if (simIndex != null) {
            simIndex
        } else {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val mode = prefs.getString(KEY_DEFAULT_SIM, MODE_ASK) ?: MODE_ASK
            if (mode.startsWith(MODE_SIM_PREFIX)) {
                mode.removePrefix(MODE_SIM_PREFIX).toIntOrNull()
            } else {
                null
            }
        }

        if (targetIndex != null) {
            // Hot path: resolve handle for target SIM
            runCatching { telecom?.callCapablePhoneAccounts?.getOrNull(targetIndex) }.getOrNull()?.let { handle ->
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
        }

        val cleanNumber = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        runCatching {
            telecom?.placeCall(Uri.parse("tel:$cleanNumber"), extras)
        }.onFailure { e ->
            android.widget.Toast.makeText(context, "Call failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    fun placeVideoCall(context: Context, number: String, simIndex: Int? = null) {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        val extras = Bundle().apply {
            putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
        }

        val targetIndex = if (simIndex != null) {
            simIndex
        } else {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val mode = prefs.getString(KEY_DEFAULT_SIM, MODE_ASK) ?: MODE_ASK
            if (mode.startsWith(MODE_SIM_PREFIX)) {
                mode.removePrefix(MODE_SIM_PREFIX).toIntOrNull()
            } else {
                null
            }
        }

        if (targetIndex != null) {
            getChoices(context).getOrNull(targetIndex)?.let { choice ->
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, choice.handle)
            }
        }

        val cleanNumber = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        runCatching {
            telecom.placeCall(Uri.parse("tel:$cleanNumber"), extras)
        }.onFailure { e ->
            android.widget.Toast.makeText(context, "Video call failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

}
