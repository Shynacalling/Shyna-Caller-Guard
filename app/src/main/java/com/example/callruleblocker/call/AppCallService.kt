package com.example.callruleblocker.call

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.callruleblocker.AppCallActivity
import com.example.callruleblocker.CallActionReceiver
import com.example.callruleblocker.R

class AppCallService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    companion object {
        private const val TAG = "ShynaCall"
        private const val NOTIFICATION_ID = 888
        private const val CHANNEL_ACTIVE = "shyna_active_call"
        private const val CHANNEL_RINGING = "shyna_ringing_call"
        
        const val ACTION_START_RINGING = "com.example.callruleblocker.action.START_RINGING"
        const val ACTION_START_ACTIVE = "com.example.callruleblocker.action.START_ACTIVE"
        const val ACTION_UPDATE_STATE = "com.example.callruleblocker.action.UPDATE_STATE"
        const val ACTION_STOP = "com.example.callruleblocker.action.STOP"

        var currentRingingCallId: String? = null
            private set

        fun startRinging(context: Context, callId: String, callerName: String, isVideo: Boolean) {
            if (currentRingingCallId == callId) return
            currentRingingCallId = callId
            val intent = Intent(context, AppCallService::class.java).apply {
                action = ACTION_START_RINGING
                putExtra("callId", callId)
                putExtra("peerName", callerName)
                putExtra("isVideo", isVideo)
            }
            startServiceInternal(context, intent)
        }

        fun startActive(context: Context, callId: String, peerName: String, isVideo: Boolean) {
            val intent = Intent(context, AppCallService::class.java).apply {
                action = ACTION_START_ACTIVE
                putExtra("callId", callId)
                putExtra("peerName", peerName)
                putExtra("isVideo", isVideo)
            }
            startServiceInternal(context, intent)
        }
        
        fun updateState(context: Context, isVideo: Boolean, isScreenSharing: Boolean) {
            val intent = Intent(context, AppCallService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra("isVideo", isVideo)
                putExtra("isScreenSharing", isScreenSharing)
            }
            startServiceInternal(context, intent)
        }

        fun stop(context: Context) {
            currentRingingCallId = null
            // Do not start a background service just to stop it. On Android O+ that can
            // throw IllegalStateException when invoked from FCM/background lifecycle events.
            context.stopService(Intent(context, AppCallService::class.java))
        }

        private fun startServiceInternal(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var isVideoActive = false
    private var isScreenSharingActive = false
    private var lastPeerName = "Unknown"
    private var lastCallId = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val callId = intent?.getStringExtra("callId") ?: lastCallId
        val peerName = intent?.getStringExtra("peerName") ?: lastPeerName
        val isVideo = intent?.getBooleanExtra("isVideo", isVideoActive) ?: isVideoActive
        val isScreenSharing = intent?.getBooleanExtra("isScreenSharing", isScreenSharingActive) ?: isScreenSharingActive

        Log.d(TAG, "APP_CALL_SERVICE_ACTION: $action id=$callId isVideo=$isVideo share=$isScreenSharing")

        when (action) {
            ACTION_START_RINGING -> {
                lastCallId = callId
                lastPeerName = peerName
                isVideoActive = isVideo
                handleRinging(callId, peerName, isVideo)
            }
            ACTION_START_ACTIVE -> {
                lastCallId = callId
                lastPeerName = peerName
                isVideoActive = isVideo
                handleActive(callId, peerName, isVideo, isScreenSharing)
            }
            ACTION_UPDATE_STATE -> {
                isVideoActive = isVideo
                isScreenSharingActive = isScreenSharing
                handleActive(lastCallId, lastPeerName, isVideo, isScreenSharing)
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun handleRinging(callId: String, callerName: String, isVideo: Boolean) {
        Log.d(TAG, "HANDLING_RINGING id=$callId")
        createChannels()
        startRingingMedia()

        val fullScreenIntent = Intent(this, AppCallActivity::class.java).apply {
            putExtra("callId", callId)
            putExtra("isIncoming", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(this, callId.hashCode(), fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "com.example.callruleblocker.action.ACCEPT_APP_CALL"
            putExtra("callId", callId)
        }
        val acceptPending = PendingIntent.getBroadcast(this, callId.hashCode() + 1, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "com.example.callruleblocker.action.DECLINE_APP_CALL"
            putExtra("callId", callId)
        }
        val declinePending = PendingIntent.getBroadcast(this, callId.hashCode() + 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_RINGING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming ${if(isVideo) "Video" else "Voice"} Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePending)
            .setOngoing(true)
            .setSilent(true) // We play sound manually for better control
            .build()

        startForegroundInternal(notification, isVideo)
    }

    private fun handleActive(callId: String, peerName: String, isVideo: Boolean, isScreenSharing: Boolean = false) {
        Log.d(TAG, "HANDLING_ACTIVE id=$callId isVideo=$isVideo share=$isScreenSharing")
        stopRingingMedia()
        createChannels()

        val intent = Intent(this, AppCallActivity::class.java).apply {
            putExtra("callId", callId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(this, callId.hashCode() + 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ACTIVE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (isScreenSharing) "Sharing Screen" else if (isVideo) "Active Video Call" else "Active Voice Call")
            .setContentText(peerName)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForegroundInternal(notification, isVideo, isScreenSharing)
    }

    private fun startForegroundInternal(notification: Notification, isVideo: Boolean, isScreenSharing: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (isVideo) foregroundType = foregroundType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (isScreenSharing) foregroundType = foregroundType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            
            startForeground(NOTIFICATION_ID, notification, foregroundType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRingingMedia() {
        try {
            if (mediaPlayer == null) {
                var ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                if (ringtoneUri == null) {
                    ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
                
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AppCallService, ringtoneUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d(TAG, "Ringtone started: $ringtoneUri")
            }
            
            if (vibrator == null) {
                vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val pattern = longArrayOf(0, 1000, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
                Log.d(TAG, "Vibration started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ringing media error: ${e.message}")
            // Fallback: try to play a simple beep or just vibrate
        }
    }

    private fun stopRingingMedia() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            
            val activeChannel = NotificationChannel(CHANNEL_ACTIVE, "Active Call", NotificationManager.IMPORTANCE_LOW)
            nm?.createNotificationChannel(activeChannel)
            
            val ringingChannel = NotificationChannel(CHANNEL_RINGING, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming app calls"
                setSound(null, null) // Silent because we play manually
                enableVibration(false) // Manual vibration
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm?.createNotificationChannel(ringingChannel)
        }
    }

    override fun onDestroy() {
        stopRingingMedia()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
