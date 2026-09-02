package com.example.callruleblocker.data

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

object SessionManager {
    private const val PREFS_NAME = "session_prefs"
    private const val KEY_SESSION_ID = "active_session_id"

    fun getLocalSessionId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SESSION_ID, null)
    }

    fun startNewSession(context: Context): String {
        val newSessionId = UUID.randomUUID().toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SESSION_ID, newSessionId)
            .apply()
        return newSessionId
    }

    fun clearLocalSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_SESSION_ID)
            .apply()
    }

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    fun updateFirestoreSession(context: Context, uid: String, onComplete: () -> Unit = {}) {
        val db = FirebaseFirestore.getInstance()
        val sessionId = getLocalSessionId(context) ?: startNewSession(context)
        val deviceId = getDeviceId(context)
        
        val update = hashMapOf<String, Any>(
            "activeSessionId" to sessionId,
            "deviceId" to deviceId,
            "lastLoginAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("users").document(uid).update(update)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onComplete() }
    }
}
