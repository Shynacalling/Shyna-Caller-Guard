package com.example.callruleblocker

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.callruleblocker.call.CallControlCenter
import com.example.callruleblocker.call.CallHolder
import com.example.callruleblocker.call.CallUiVisibility
import com.example.callruleblocker.sim.SimSlotResolver

object OngoingCallNotification {
    const val NOTIFICATION_ID = 9204
    const val ACTION_ANSWER_CALL = "com.example.callruleblocker.action.ANSWER_CALL"
    const val ACTION_DECLINE_CALL = "com.example.callruleblocker.action.DECLINE_CALL"
    const val ACTION_END_CALL = "com.example.callruleblocker.action.END_CALL"

    private fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openCallPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            9204,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    suspend fun showIncoming(context: Context, call: Call) {
        if (call.state != Call.STATE_RINGING) {
            cancel(context)
            return
        }

        // If Shyna's dedicated call UI is already the foreground screen, the
        // caller/answer/reject controls are already visible there. Posting a
        // heads-up notification in that exact case would only duplicate the UI.
        // The moment the user leaves CallActivity, its lifecycle re-enables this
        // notification so another app still gets the expected compact call banner.
        if (CallUiVisibility.isCallScreenForeground) {
            cancel(context)
            return
        }
        NotificationSupport.createChannels(context)
        if (!notificationsAllowed(context)) return

        CallHolder.set(call)
        val openIntent = openCallPendingIntent(context)
        val answerIntent = actionPendingIntent(context, ACTION_ANSWER_CALL, 9206)
        val declineIntent = actionPendingIntent(context, ACTION_DECLINE_CALL, 9207)
        
        val rawNumber = call.details.handle?.schemeSpecificPart.orEmpty()
        val contactName = lookupContactName(context, rawNumber)
        val simSlot = runCatching { 
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                SimSlotResolver.resolveSlot(context, call.details.accountHandle)
            } else 0
        }.getOrDefault(0)
        val simLabel = if (simSlot == 1) "SIM 2" else "SIM 1"
        
        val title = contactName ?: if (VideoProfile.isVideo(call.details.videoState)) "Incoming video call" else "Incoming call"
        val text = if (contactName != null) "$rawNumber • $simLabel" else "$rawNumber • $simLabel"

        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        val useFullScreen = powerManager?.isInteractive != true || keyguardManager?.isKeyguardLocked == true

        val builder = NotificationCompat.Builder(context, NotificationSupport.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setVibrate(longArrayOf(0, 500, 350, 500))
            .addAction(android.R.drawable.sym_action_call, "Answer", answerIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declineIntent)

        if (useFullScreen) builder.setFullScreenIntent(openIntent, true)

        // Contact/SIM lookup above can take long enough for CallActivity to come
        // to the foreground. Re-check here to close that race and guarantee that
        // a late heads-up notification never overlays the already-visible call UI.
        if (CallUiVisibility.isCallScreenForeground) {
            cancel(context)
            return
        }

        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, builder.build())
    }

    private fun lookupContactName(context: Context, number: String): String? {
        if (number.isBlank() || ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    suspend fun show(context: Context, call: Call) {
        if (call.state == Call.STATE_RINGING) {
            showIncoming(context, call)
            return
        }
        if (call.state == Call.STATE_DISCONNECTED || call.state == Call.STATE_DISCONNECTING) {
            cancel(context)
            return
        }

        NotificationSupport.createChannels(context)
        if (!notificationsAllowed(context)) return

        val openPendingIntent = openCallPendingIntent(context)
        val endPendingIntent = actionPendingIntent(context, ACTION_END_CALL, 9205)
        val number = call.details.handle?.schemeSpecificPart.orEmpty().ifBlank { "Ongoing call" }
        val isVideo = VideoProfile.isVideo(call.details.videoState)
        val title = if (isVideo) "Video call in progress" else "Call in progress"

        val notification = NotificationCompat.Builder(context, NotificationSupport.CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle(title)
            .setContentText(number)
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Increased to MAX for better heads-up/chip visibility
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(call.state == Call.STATE_ACTIVE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End call", endPendingIntent)
            .setFullScreenIntent(openPendingIntent, false) // Standard trick to trigger OEM call chips/popups
            .build().apply { flags = flags or Notification.FLAG_ONGOING_EVENT }

        context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
