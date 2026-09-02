package com.example.callruleblocker.data

import android.content.Context
import android.content.SharedPreferences

class StatusLocalStore private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("shyna_status_store", Context.MODE_PRIVATE)

    fun getSeenStatusIds(): Set<String> {
        return prefs.getStringSet("seen_status_ids", emptySet()) ?: emptySet()
    }

    fun markStatusSeenLocally(statusId: String) {
        if (statusId.isBlank()) return
        val current = getSeenStatusIds().toMutableSet()
        if (current.add(statusId)) {
            prefs.edit().putStringSet("seen_status_ids", current).apply()
        }
    }

    fun isStatusSeenLocally(statusId: String): Boolean {
        return getSeenStatusIds().contains(statusId)
    }

    fun getMutedUserIds(): Set<String> {
        return prefs.getStringSet("muted_user_ids", emptySet()) ?: emptySet()
    }

    fun setMuteUser(userId: String, mute: Boolean) {
        if (userId.isBlank()) return
        val current = getMutedUserIds().toMutableSet()
        val changed = if (mute) current.add(userId) else current.remove(userId)
        if (changed) {
            prefs.edit().putStringSet("muted_user_ids", current).apply()
        }
    }

    fun isUserMuted(userId: String): Boolean {
        return getMutedUserIds().contains(userId)
    }

    fun isNotificationEnabledForUser(userId: String): Boolean {
        return prefs.getBoolean("notif_enabled_$userId", false)
    }

    fun setNotificationEnabledForUser(userId: String, enabled: Boolean) {
        prefs.edit().putBoolean("notif_enabled_$userId", enabled).apply()
    }

    fun getPrivacyMode(): String {
        return prefs.getString("status_privacy_mode", "MY_CONTACTS") ?: "MY_CONTACTS"
    }

    fun setPrivacyMode(mode: String) {
        prefs.edit().putString("status_privacy_mode", mode).apply()
    }

    fun getExcludedUids(): Set<String> {
        return prefs.getStringSet("status_excluded_uids", emptySet()) ?: emptySet()
    }

    fun setExcludedUids(uids: Set<String>) {
        prefs.edit().putStringSet("status_excluded_uids", uids).apply()
    }

    fun getAllowedUids(): Set<String> {
        return prefs.getStringSet("status_allowed_uids", emptySet()) ?: emptySet()
    }

    fun setAllowedUids(uids: Set<String>) {
        prefs.edit().putStringSet("status_allowed_uids", uids).apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: StatusLocalStore? = null

        fun getInstance(context: Context): StatusLocalStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StatusLocalStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
