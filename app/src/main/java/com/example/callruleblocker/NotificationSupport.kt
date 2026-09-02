package com.example.callruleblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationSupport {
    const val CHANNEL_CALLS = "calls"
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_RECORDING = "recording"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(
                CHANNEL_CALLS,
                "Calls and missed calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming, missed-call and call status alerts"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Call-back reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Scheduled reminders to call contacts back"
            },
            NotificationChannel(
                CHANNEL_RECORDING,
                "Call recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Recording status and saved recording alerts"
                setSound(null, null)
            }
        )
        manager.createNotificationChannels(channels)
    }
}
