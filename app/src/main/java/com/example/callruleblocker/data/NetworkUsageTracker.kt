package com.example.callruleblocker.data

import android.content.Context

object NetworkUsageTracker {
    private const val PREFS_NAME = "shyna_network_usage"

    fun track(context: Context, type: String, sent: Long = 0, received: Long = 0) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isWifi = NetworkDetector.isWifi(context)
        val suffix = if (isWifi) "_wifi" else "_mobile"
        
        prefs.edit().apply {
            putLong("${type}_sent", prefs.getLong("${type}_sent", 0) + sent)
            putLong("${type}_received", prefs.getLong("${type}_received", 0) + received)
            
            // Detailed tracking
            putLong("${type}${suffix}_sent", prefs.getLong("${type}${suffix}_sent", 0) + sent)
            putLong("${type}${suffix}_received", prefs.getLong("${type}${suffix}_received", 0) + received)
        }.apply()
    }
    
    fun getDetailedUsage(context: Context, type: String, isWifi: Boolean): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val suffix = if (isWifi) "_wifi" else "_mobile"
        val sent = prefs.getLong("${type}${suffix}_sent", 0L)
        val received = prefs.getLong("${type}${suffix}_received", 0L)
        return "Sent: ${formatSize(sent)} · Received: ${formatSize(received)}"
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
    
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
