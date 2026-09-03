package com.example.callruleblocker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.Ringtone
import android.media.ToneGenerator
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.callruleblocker.call.*
import com.example.callruleblocker.ui.ShynaDesign
import com.example.callruleblocker.ui.ShynaTheme
import com.example.callruleblocker.ui.ThemeMode
import com.example.callruleblocker.ui.VideoRenderer
import com.google.firebase.auth.FirebaseAuth
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppCallActivity : ComponentActivity() {
    private var currentCallId: String? = null
    private val autoAcceptState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupScreenBehavior()
        val callId = intent.getStringExtra("callId") ?: return finish()
        val isIncoming = intent.getBooleanExtra("isIncoming", false)
        autoAcceptState.value = intent.getBooleanExtra("autoAccept", false)
        currentCallId = callId
        
        // Stop ringing service if it was running
        if (isIncoming) {
            com.example.callruleblocker.call.AppCallService.stop(this)
        }

        setContent {
            ShynaTheme(mode = ThemeMode.DARK) {
                AppCallScreen(callId, isIncoming, autoAcceptState, onExit = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val callId = intent.getStringExtra("callId") ?: return
        val isIncoming = intent.getBooleanExtra("isIncoming", false)
        if (isIncoming) {
            com.example.callruleblocker.call.AppCallService.stop(this)
        }
        if (callId != currentCallId) {
            currentCallId = callId
            autoAcceptState.value = intent.getBooleanExtra("autoAccept", false)
            val isIncoming = intent.getBooleanExtra("isIncoming", false)
            setContent {
                ShynaTheme(mode = ThemeMode.DARK) {
                    AppCallScreen(callId, isIncoming, autoAcceptState, onExit = { finish() })
                }
            }
        } else {
            autoAcceptState.value = intent.getBooleanExtra("autoAccept", false)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
        }
    }

    private fun setupScreenBehavior() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            km.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
fun AppCallScreen(callId: String, isIncoming: Boolean, autoAcceptState: State<Boolean>, onExit: () -> Unit) {
    val autoAccept by autoAcceptState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? ComponentActivity
    val isMeeting = activity?.intent?.getBooleanExtra("isMeeting", false) == true || callId.startsWith("MEETING_")
    val initialMute = remember(callId) { activity?.intent?.getBooleanExtra("initialMute", false) == true }
    val initialVideoOff = remember(callId) { activity?.intent?.getBooleanExtra("initialVideoOff", false) == true }
    val autoShareRequested = remember(callId) { activity?.intent?.getBooleanExtra("autoShareScreen", false) == true }
    var autoShareLaunched by remember(callId) { mutableStateOf(false) }
    var call by remember { mutableStateOf<AppCall?>(null) }
    var room by remember { mutableStateOf<Room?>(null) }
    val remoteVideoTracks = remember { mutableStateMapOf<Participant.Sid, VideoTrack>() }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var connectionState by remember { mutableStateOf(io.livekit.android.room.Room.State.DISCONNECTED) }
    var isMuted by remember(callId) { mutableStateOf(initialMute) }
    var isCameraOff by remember(callId) { mutableStateOf(initialVideoOff) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var callDuration by remember { mutableLongStateOf(0L) }
    var connectedAt by remember { mutableStateOf<Long?>(null) }
    var networkType by remember { mutableStateOf("Unknown") }
    var isScreenSharing by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }
    // Group calls use one shared call document. Keep each recipient's local accept
    // decision separate so one participant accepting does not remove the Accept/Decline
    // UI for every other invited participant.
    var hasAcceptedIncoming by remember(callId) { mutableStateOf(!isIncoming) }
    var showParticipants by remember { mutableStateOf(false) }
    
    val callManager = remember { LiveKitCallManager(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val wakeLock = remember { 
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        pm.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Shyna:ProximityLock") 
    }

    var joinCallLambda by remember { mutableStateOf<(() -> Unit)?>(null) }
    var routeAudioLambda by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    val routeAudio: (Boolean) -> Unit = { speaker ->
        Log.d("ShynaCall", "Routing audio: speaker=$speaker")
        try {
            audioManager.isMicrophoneMute = false
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val btDevice = devices.firstOrNull { 
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                val speakerDevice = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                val earpieceDevice = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                
                if (!speaker && btDevice != null) {
                    audioManager.setCommunicationDevice(btDevice)
                } else if (speaker && speakerDevice != null) {
                    audioManager.setCommunicationDevice(speakerDevice)
                } else if (!speaker && earpieceDevice != null) {
                    audioManager.setCommunicationDevice(earpieceDevice)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = speaker
            }
        } catch (e: Exception) { Log.e("ShynaCall", "[AUDIO] Route Error: ${e.message}") }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val camNeeded = call?.type == AppCallType.VIDEO || isMeeting
        val camGranted = if (camNeeded) permissions[Manifest.permission.CAMERA] == true else true
        if (micGranted && camGranted) joinCallLambda?.invoke()
        else { Toast.makeText(context, "Microphone & Camera permissions are required", Toast.LENGTH_LONG).show(); onExit() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val joinCall: () -> Unit = {
        if (!isJoining && room == null) {
            isJoining = true
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (call?.type == AppCallType.VIDEO || isMeeting) {
                permissions.add(Manifest.permission.CAMERA)
            }
            
            if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                // Use the lifecycleScope of the activity/lifecycleOwner instead of rememberCoroutineScope.
                // This ensures that even if composition is disposed (e.g. call cut), 
                // the signaling/joining work can continue or cleanup safely without scope errors.
                lifecycleOwner.lifecycleScope.launch {
                    try {
                        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                        if (currentUid == null || call == null) { 
                            withContext(Dispatchers.Main) { onExit() }
                            return@launch 
                        }
                        
                        Log.d("ShynaCall", "Joining room: ${call!!.roomName}")
                        val r = callManager.joinRoom(call!!.roomName, currentUid, callId)
                        room = r
                        CallSignalingManager.updateCallStatus(callId, AppCallStatus.CONNECTED)
                        
                        // Sync any existing remote participants' video tracks immediately on join
                        r.remoteParticipants.values.forEach { participant ->
                            participant.videoTrackPublications.mapNotNull { it.second as? VideoTrack }.forEach { track ->
                                remoteVideoTracks[participant.sid] = track
                            }
                        }

                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        routeAudio(isSpeakerOn)
                        
                        launch { 
                            delay(200)
                            if (room?.state == Room.State.CONNECTED) {
                                r.localParticipant.setMicrophoneEnabled(!isMuted)
                            }
                        }
                        if (call?.type == AppCallType.VIDEO || isMeeting) {
                            launch { 
                                delay(200)
                                r.localParticipant.setCameraEnabled(!isCameraOff)
                            }
                        }
                        
                        localVideoTrack = r.localParticipant.videoTrackPublications.firstOrNull()?.second as? VideoTrack
                        
                        // Ensure local camera is active if needed
                        if ((call?.type == AppCallType.VIDEO || isMeeting) && !isCameraOff) {
                            r.localParticipant.setCameraEnabled(true)
                        }

                        r.events.collect { event ->
                            connectionState = r.state
                            when (event) {
                                is RoomEvent.TrackPublished -> {
                                    if (event.participant == r.localParticipant && event.publication.track is VideoTrack) {
                                        localVideoTrack = event.publication.track as VideoTrack
                                    }
                                }
                                is RoomEvent.TrackSubscribed -> {
                                    if (event.track is VideoTrack) {
                                        remoteVideoTracks[event.participant.sid] = event.track as VideoTrack
                                    } else {
                                        routeAudioLambda?.invoke(isSpeakerOn)
                                    }
                                }
                                is RoomEvent.TrackUnsubscribed -> {
                                    if (event.track is VideoTrack) {
                                        remoteVideoTracks.remove(event.participant.sid)
                                    }
                                }
                                is RoomEvent.ParticipantDisconnected -> {
                                    remoteVideoTracks.remove(event.participant.sid)
                                }
                                is RoomEvent.Disconnected -> {
                                    if (r.state == Room.State.DISCONNECTED && call?.status == AppCallStatus.CONNECTED) {
                                        withContext(Dispatchers.Main) { onExit() }
                                    }
                                }
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ShynaCall", "Join failed: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Unable to connect call: ${e.message ?: "network/error"}", Toast.LENGTH_LONG).show()
                            onExit()
                        }
                    } finally { isJoining = false }
                }
            } else { 
                permissionLauncher.launch(permissions.toTypedArray())
                isJoining = false 
            }
        }
    }
    
    SideEffect { joinCallLambda = joinCall; routeAudioLambda = routeAudio }

    // Handle Auto-Accept from Notification
    LaunchedEffect(autoAccept, call?.status) {
        if (autoAccept && isIncoming && call?.status == AppCallStatus.RINGING) {
            hasAcceptedIncoming = true
            callManager.callAction(callId, "accept")
            joinCall()
        }
    }

    DisposableEffect(call?.status, isMeeting) {
        var ringtone: Ringtone? = null
        var toneGen: ToneGenerator? = null
        
        if (!isMeeting) {
            if (isIncoming && call?.status == AppCallStatus.RINGING) {
                try {
                    val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
                    ringtone?.play()
                } catch (e: Exception) { Log.e("ShynaCall", "Ringtone Error: ${e.message}") }
            } else if (!isIncoming && call?.status == AppCallStatus.RINGING) {
                try {
                    toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
                    toneGen?.startTone(ToneGenerator.TONE_SUP_RINGTONE)
                } catch (e: Exception) { Log.e("ShynaCall", "Ringback Tone Error: ${e.message}") }
            }
        }
        onDispose { 
            ringtone?.stop() 
            try { toneGen?.stopTone(); toneGen?.release() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(call?.connectedAt) {
        if (call?.connectedAt != null) {
            connectedAt = call!!.connectedAt
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState == io.livekit.android.room.Room.State.CONNECTED && connectedAt == null) {
            connectedAt = System.currentTimeMillis()
        }
    }

    LaunchedEffect(connectedAt) {
        if (connectedAt != null) {
            while (true) { callDuration = (System.currentTimeMillis() - connectedAt!!) / 1000; delay(500) }
        }
    }

    LaunchedEffect(call?.status) {
        if (call?.status == AppCallStatus.RINGING) {
            delay(45000)
            if (call?.status == AppCallStatus.RINGING) {
                if (isIncoming) callManager.callAction(callId, "missed")
                else callManager.callAction(callId, "no-answer")
                onExit()
            }
        }
    }

    LaunchedEffect(call?.status) {
        if (call?.status == AppCallStatus.CONNECTED) {
            val peer = if(isIncoming) call?.callerName ?: "Caller" else call?.receiverName ?: "Receiver"
            CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.ACTIVE, callId)
            AppCallService.startActive(context, callId, peer, call?.type == AppCallType.VIDEO)
        }
    }

    LaunchedEffect(isSpeakerOn) { routeAudio(isSpeakerOn) }

    LaunchedEffect(call?.type, room, isCameraOff) {
        if (room != null && call?.type == AppCallType.VIDEO) {
            room?.localParticipant?.setCameraEnabled(!isCameraOff)
        }
    }

    DisposableEffect(callId) {
        val registration = CallSignalingManager.listenToCall(callId) { updatedCall ->
            call = updatedCall
            when (updatedCall.status) {
                AppCallStatus.ENDED, AppCallStatus.DECLINED, AppCallStatus.MISSED, 
                AppCallStatus.FAILED, AppCallStatus.CANCELLED, AppCallStatus.BUSY, 
                AppCallStatus.NO_ANSWER, AppCallStatus.UNAVAILABLE, AppCallStatus.REJECTED -> {
                    CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.ENDED, callId)
                    onExit()
                }
                else -> {}
            }
        }
        onDispose { registration.remove() }
    }

    val endCurrentCall: () -> Unit = {
        val activeCall = call
        val currentRoom = room
        // Use GlobalScope or a lifecycle-aware scope that doesn't cause the "rememberCoroutineScope left the composition" error
        // since onExit() finishes the activity and disposes this composition immediately.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isMeeting && activeCall != null) {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    val meetingId = callId.removePrefix("MEETING_")
                    if (uid.isNotBlank() && uid == activeCall.callerUid) {
                        CallSignalingManager.endMeeting(meetingId)
                    } else if (uid.isNotBlank()) {
                        CallSignalingManager.leaveMeeting(uid, meetingId)
                    }
                } else {
                    callManager.callAction(callId, "end")
                }
                currentRoom?.disconnect()
            } catch (e: Exception) {
                Log.e("ShynaCall", "End call error: ${e.message}")
            }
            withContext(Dispatchers.Main) { onExit() }
        }
    }

    val globalState by CallStateController.globalState.collectAsState()
    val isPipMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        (context as? ComponentActivity)?.isInPictureInPictureMode == true
    } else false

    Box(Modifier.fillMaxSize().background(ShynaDesign.premiumGradient())) {
        if (connectionState == Room.State.RECONNECTING) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).zIndex(10f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen)
                    Spacer(Modifier.height(16.dp))
                    Text("Reconnecting...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isPipMode) {
             if (call?.type == AppCallType.VIDEO && remoteVideoTracks.isNotEmpty()) {
                 VideoGrid(remoteVideoTracks.values.toList(), room!!, modifier = Modifier.fillMaxSize())
             } else {
                 Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                     Text("Call Active", color = Color.White)
                 }
             }
        } else if (globalState == GlobalCallState.INTERRUPTED) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Call Interrupted", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("A higher priority call is active.", color = Color.White.copy(0.7f), fontSize = 16.sp)
                }
            }
        } else if (call == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen) }
        } else {
            when (call!!.status) {
                AppCallStatus.RINGING -> {
                    if (isIncoming) {
                        IncomingCallUI(call!!,
                            onAccept = {
                                hasAcceptedIncoming = true
                                scope.launch { callManager.callAction(callId, "accept"); joinCall() }
                            },
                            onReject = {
                                scope.launch {
                                    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                                    if (call!!.isGroup && currentUid.isNotBlank()) {
                                        CallSignalingManager.leaveGroupCall(currentUid, callId)
                                    } else {
                                        callManager.callAction(callId, "decline")
                                    }
                                    onExit()
                                }
                            }
                        )
                    } else {
                        OutgoingCallUI(call!!, onCancel = { scope.launch { callManager.callAction(callId, "cancel"); onExit() } })
                    }
                }
                AppCallStatus.ACCEPTED, AppCallStatus.CONNECTED -> {
                    val waitingForGroupAcceptance = isIncoming && call!!.isGroup && !hasAcceptedIncoming && room == null
                    if (waitingForGroupAcceptance) {
                        IncomingCallUI(
                            call = call!!,
                            onAccept = {
                                hasAcceptedIncoming = true
                                scope.launch { joinCall() }
                            },
                            onReject = {
                                val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                                if (currentUid.isNotBlank()) CallSignalingManager.leaveGroupCall(currentUid, callId)
                                onExit()
                            }
                        )
                        LaunchedEffect(callId, waitingForGroupAcceptance) {
                            delay(45000)
                            if (!hasAcceptedIncoming && room == null) {
                                val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                                if (currentUid.isNotBlank()) CallSignalingManager.leaveGroupCall(currentUid, callId)
                                onExit()
                            }
                        }
                    } else {
                    if (room == null && (!isIncoming || hasAcceptedIncoming)) { LaunchedEffect(callId, hasAcceptedIncoming) { joinCall() } }
                    val screenShareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        if (result.resultCode == android.app.Activity.RESULT_OK) {
                            val data = result.data ?: return@rememberLauncherForActivityResult
                            scope.launch { 
                                room?.localParticipant?.setScreenShareEnabled(true,
                                    ScreenCaptureParams(mediaProjectionPermissionResultData = data)
                                )
                                isScreenSharing = true
                                AppCallService.updateState(context, isVideo = !isCameraOff, isScreenSharing = true)
                            }
                        }
                    }
                    LaunchedEffect(autoShareRequested, room) {
                        if (autoShareRequested && !autoShareLaunched && room != null) {
                            autoShareLaunched = true
                            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                            screenShareLauncher.launch(projectionManager.createScreenCaptureIntent())
                        }
                    }
                    if (call!!.type == AppCallType.VIDEO || isMeeting) {
                        VideoCallUI(
                            call = call!!, isIncoming = isIncoming, room = room, scope = scope, duration = callDuration, networkType = networkType, remoteTracks = remoteVideoTracks.values.toList(), localTrack = localVideoTrack,
                            isMuted = isMuted, isCameraOff = isCameraOff, isSpeakerOn = isSpeakerOn, isScreenSharing = isScreenSharing, screenShareLauncher = screenShareLauncher,
                            onMuteToggle = { isMuted = !isMuted; scope.launch { room?.localParticipant?.setMicrophoneEnabled(!isMuted) } },
                            onCameraToggle = { 
                                isCameraOff = !isCameraOff
                                scope.launch { 
                                    room?.localParticipant?.setCameraEnabled(!isCameraOff)
                                    AppCallService.updateState(context, isVideo = !isCameraOff, isScreenSharing = isScreenSharing)
                                }
                            },
                            onSpeakerToggle = { isSpeakerOn = !isSpeakerOn },
                            onScreenShareToggle = { 
                                isScreenSharing = it
                                AppCallService.updateState(context, isVideo = !isCameraOff, isScreenSharing = it)
                            },
                            onSwitchCamera = { (room?.localParticipant?.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)?.track as? io.livekit.android.room.track.LocalVideoTrack)?.switchCamera(); isFrontCamera = !isFrontCamera },
                            onEndCall = endCurrentCall,
                            onShowParticipants = { showParticipants = true }
                        )
                    } else {
                        VoiceCallUI(
                            call = call!!, isIncoming = isIncoming, room = room, scope = scope, duration = callDuration, networkType = networkType,
                            isMuted = isMuted, isSpeakerOn = isSpeakerOn, isScreenSharing = isScreenSharing, screenShareLauncher = screenShareLauncher,
                            onMuteToggle = { isMuted = !isMuted; scope.launch { room?.localParticipant?.setMicrophoneEnabled(!isMuted) } },
                            onSpeakerToggle = { isSpeakerOn = !isSpeakerOn },
                            onScreenShareToggle = { 
                                isScreenSharing = it
                                AppCallService.updateState(context, isVideo = !isCameraOff, isScreenSharing = it)
                            },
                            onUpgradeToVideo = {
                                isCameraOff = false
                                CallSignalingManager.updateCallType(callId, AppCallType.VIDEO)
                                scope.launch { 
                                    room?.localParticipant?.setCameraEnabled(true)
                                    AppCallService.updateState(context, isVideo = true, isScreenSharing = isScreenSharing)
                                }
                            },
                            onEndCall = endCurrentCall,
                            onShowParticipants = { showParticipants = true }
                        )
                    }
                    }
                }
                else -> onExit()
            }
        }

        if (showParticipants && room != null) {
            ParticipantListDialog(
                room = room!!,
                onDismiss = { showParticipants = false }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CallStateController.reportCallEvent(MainCallType.SHYNA_LINK, GlobalCallState.ENDED, callId)
            AppCallService.stop(context)
            callManager.leaveRoom()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice()
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            call?.let { c ->
                if (currentUid != null) {
                    val finalCall = c.copy(duration = callDuration)
                    CallSignalingManager.saveCallHistory(finalCall, currentUid)
                }
            }
        }
    }
}

@Composable
fun IncomingCallUI(call: AppCall, onAccept: () -> Unit, onReject: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.15f, animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "scale")
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B141B))) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, modifier = Modifier.size(180.dp).scale(scale).border(BorderStroke(2.dp, Color.White.copy(0.2f)), CircleShape), color = Color.DarkGray) {
                if (!call.callerPhoto.isNullOrBlank()) AsyncImage(model = call.callerPhoto, contentDescription = null, contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, modifier = Modifier.padding(50.dp), tint = Color.LightGray)
            }
            Spacer(Modifier.height(40.dp))
            Text(call.callerName, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Incoming Shyna ${call.type.name.lowercase()} call...", color = Color.Gray, fontSize = 16.sp)
        }
        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(onClick = onReject, containerColor = Color(0xFFE53935), shape = CircleShape, modifier = Modifier.size(72.dp)) { Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.height(8.dp)); Text("Decline", color = Color.White, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(onClick = onAccept, containerColor = Color(0xFF25D366), shape = CircleShape, modifier = Modifier.size(72.dp)) { Icon(if (call.type == AppCallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.height(8.dp)); Text("Accept", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun OutgoingCallUI(call: AppCall, onCancel: () -> Unit) {
    val statusLabel = when (call.status) {
        AppCallStatus.CREATED, AppCallStatus.INVITING -> "Calling..."
        AppCallStatus.RINGING -> "Ringing..."
        AppCallStatus.CONNECTING -> "Connecting..."
        AppCallStatus.CONNECTED -> "Connected"
        else -> "Calling..."
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B141B))) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, modifier = Modifier.size(180.dp), border = BorderStroke(2.dp, Color.White.copy(0.2f)), color = Color.DarkGray) {
                val photo = call.receiverPhoto
                if (!photo.isNullOrBlank()) AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, modifier = Modifier.padding(50.dp), tint = Color.LightGray)
            }
            Spacer(Modifier.height(40.dp))
            Text(call.receiverName, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(statusLabel, color = Color.Gray, fontSize = 16.sp)
        }
        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingActionButton(onClick = { onCancel() }, modifier = Modifier.size(72.dp), containerColor = Color(0xFFE53935), shape = CircleShape) { Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.height(8.dp)); Text("Cancel", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun VoiceCallUI(call: AppCall, isIncoming: Boolean, room: Room?, scope: CoroutineScope, duration: Long, networkType: String, isMuted: Boolean, isSpeakerOn: Boolean, isScreenSharing: Boolean, screenShareLauncher: ActivityResultLauncher<Intent>, onMuteToggle: () -> Unit, onSpeakerToggle: () -> Unit, onScreenShareToggle: (Boolean) -> Unit, onUpgradeToVideo: () -> Unit, onEndCall: () -> Unit, onShowParticipants: () -> Unit) {
    val peerName = if (isIncoming) call.callerName else call.receiverName
    val peerPhoto = if (isIncoming) call.callerPhoto else call.receiverPhoto
    val statusText = if (call.status == AppCallStatus.CONNECTED) formatDuration(duration) else "Connecting..."
    val mContext = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B141B))) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (call.isGroup) call.receiverName else peerName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp)); Text(text = "End-to-end encrypted", color = Color.Gray, fontSize = 12.sp)
                }
            }
            if (call.isGroup) {
                IconButton(onClick = onShowParticipants) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, null, tint = Color.White)
                        val count = (room?.remoteParticipants?.size ?: 0) + 1
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                            color = ShynaDesign.colors.BrandGreen,
                            shape = CircleShape
                        ) {
                            Text(count.toString(), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp), color = Color.White)
                        }
                    }
                }
            } else {
                Spacer(Modifier.size(26.dp))
            }
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(shape = CircleShape, modifier = Modifier.size(220.dp), color = Color.DarkGray) {
                if (!peerPhoto.isNullOrBlank()) AsyncImage(model = peerPhoto, contentDescription = null, contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, modifier = Modifier.padding(60.dp), tint = Color.LightGray)
            }
        }
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF1F2C34)) {
            Column(modifier = Modifier.padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val stateName = if(room?.state == io.livekit.android.room.Room.State.CONNECTED) "LIVE" else room?.state?.name ?: "IDLE"
                Text(text = if(room?.state == io.livekit.android.room.Room.State.CONNECTED) statusText else "Connecting... ($stateName)", color = if(stateName == "LIVE") ShynaDesign.colors.BrandGreen else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CallActionButton(icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute, label = "Speaker", isActive = isSpeakerOn, onClick = onSpeakerToggle)
                        CallActionButton(icon = Icons.Default.Videocam, label = "Video", isActive = false, onClick = onUpgradeToVideo)
                        CallActionButton(icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, label = "Mute", isActive = isMuted, onClick = onMuteToggle)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CallActionButton(icon = Icons.Default.FileUpload, label = "Share", isActive = isScreenSharing, onClick = { if (isScreenSharing) { scope.launch { room?.localParticipant?.setScreenShareEnabled(false); onScreenShareToggle(false) } } else { val mediaProjectionManager = mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager; screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent()) } })
                        CallActionButton(icon = Icons.Default.CallEnd, label = "End", isActive = false, isEndButton = true, onClick = onEndCall)
                    }
                }
            }
        }
    }
}

@Composable
fun CallActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isActive: Boolean, isEndButton: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(60.dp), shape = CircleShape, color = if (isEndButton) Color(0xFFE53935) else if (isActive) Color.White else Color.White.copy(0.1f), onClick = onClick) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = if (isEndButton) Color.White else if (isActive) Color.Black else Color.White, modifier = Modifier.size(28.dp)) }
        }
        Spacer(Modifier.height(8.dp)); Text(text = label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun VideoCallUI(call: AppCall, isIncoming: Boolean, room: Room?, scope: CoroutineScope, duration: Long, networkType: String, remoteTracks: List<VideoTrack>, localTrack: VideoTrack?, isMuted: Boolean, isCameraOff: Boolean, isSpeakerOn: Boolean, isScreenSharing: Boolean, screenShareLauncher: ActivityResultLauncher<Intent>, onMuteToggle: () -> Unit, onCameraToggle: () -> Unit, onSpeakerToggle: () -> Unit, onScreenShareToggle: (Boolean) -> Unit, onSwitchCamera: () -> Unit, onEndCall: () -> Unit, onShowParticipants: () -> Unit) {
    val peerName = if (isIncoming) call.callerName else call.receiverName
    val statusText = if (call.status == AppCallStatus.CONNECTED) formatDuration(duration) else "Connecting..."
    val mContext = LocalContext.current
    val isMeetingCall = call.isGroup || call.id.startsWith("MEETING_") || call.receiverUid == "MEETING_ROOM"

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (room != null && remoteTracks.isNotEmpty()) {
            VideoGrid(remoteTracks, room, modifier = Modifier.fillMaxSize())
        } else if (isMeetingCall && room?.state == Room.State.CONNECTED) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, modifier = Modifier.size(110.dp), color = Color.DarkGray) {
                        val photo = call.callerPhoto
                        if (!photo.isNullOrBlank()) AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Person, null, modifier = Modifier.padding(24.dp), tint = Color.LightGray)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Waiting for others to join...", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    val cleanMeetingId = call.id.removePrefix("MEETING_")
                    Text("Meeting ID: $cleanMeetingId", color = ShynaDesign.colors.BrandGreen, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen)
                    Spacer(Modifier.height(16.dp))
                    Text("Connecting...", color = Color.White)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(0.6f), Color.Transparent))).padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (call.isGroup) call.receiverName else peerName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val stateName = if(room?.state == Room.State.CONNECTED) "LIVE" else room?.state?.name ?: "IDLE"
                Text(if(room?.state == Room.State.CONNECTED) statusText else "Connecting... ($stateName)", color = if(stateName == "LIVE") ShynaDesign.colors.BrandGreen else Color.White.copy(0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            if (call.isGroup) {
                IconButton(onClick = onShowParticipants) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, null, tint = Color.White)
                        val count = (room?.remoteParticipants?.size ?: 0) + 1
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                            color = ShynaDesign.colors.BrandGreen,
                            shape = CircleShape
                        ) {
                            Text(count.toString(), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp), color = Color.White)
                        }
                    }
                }
            }
            IconButton(onClick = onSwitchCamera) { Icon(Icons.Default.SwitchCamera, null, tint = Color.White) }
        }
        if (room != null && localTrack != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 16.dp).size(110.dp, 160.dp).clip(RoundedCornerShape(12.dp)).background(Color.DarkGray).border(2.dp, Color.White.copy(0.4f), RoundedCornerShape(12.dp)).zIndex(5f)) {
                VideoRenderer(localTrack, room, modifier = Modifier.fillMaxSize())
                if (isCameraOff) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VideocamOff, null, tint = Color.White)
                    }
                }
            }
        }
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF1F2C34).copy(alpha = 0.9f)) {
            Row(modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                CallActionButtonSmall(icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute, isActive = isSpeakerOn, onClick = onSpeakerToggle)
                CallActionButtonSmall(icon = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, isActive = isCameraOff, onClick = onCameraToggle)
                CallActionButtonSmall(icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, isActive = isMuted, onClick = onMuteToggle)
                CallActionButtonSmall(icon = if (isScreenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare, isActive = isScreenSharing, onClick = { if (isScreenSharing) { scope.launch { room?.localParticipant?.setScreenShareEnabled(false); onScreenShareToggle(false) } } else { val mediaProjectionManager = mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager; screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent()) } })
                FloatingActionButton(onClick = onEndCall, containerColor = Color(0xFFE53935), shape = CircleShape, modifier = Modifier.size(60.dp)) { Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

@Composable
fun CallActionButtonSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = if (isActive) Color.White else Color.White.copy(0.15f), onClick = onClick) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = if (isActive) Color.Black else Color.White, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
fun VideoGrid(tracks: List<VideoTrack>, room: Room, modifier: Modifier = Modifier) {
    if (tracks.size == 1) {
        VideoRenderer(tracks[0], room, modifier = modifier)
    } else {
        val columns = if (tracks.size <= 4) 2 else 3
        val rows = (tracks.size + columns - 1) / columns
        
        Column(modifier) {
            for (r in 0 until rows) {
                Row(Modifier.weight(1f)) {
                    for (c in 0 until columns) {
                        val index = r * columns + c
                        if (index < tracks.size) {
                            Box(Modifier.weight(1f).fillMaxHeight().border(1.dp, Color.Black)) {
                                VideoRenderer(tracks[index], room, modifier = Modifier.fillMaxSize())
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ParticipantListDialog(room: Room, onDismiss: () -> Unit) {
    val participants = remember(room.remoteParticipants.size) {
        listOf(room.localParticipant) + room.remoteParticipants.values.toList()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Participants (${participants.size})", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(participants.size) { index ->
                    val p = participants[index]
                    val isLocal = p is LocalParticipant
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = Color.DarkGray) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.padding(8.dp), tint = Color.LightGray)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (isLocal) "${p.identity?.value ?: "Me"} (You)" else p.identity?.value ?: "Participant",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (p.isMicrophoneEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = null,
                                    tint = if (p.isMicrophoneEnabled) ShynaDesign.colors.BrandGreen else Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (p.isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = null,
                                    tint = if (p.isCameraEnabled) ShynaDesign.colors.BrandGreen else Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        if (p == room.localParticipant) {
                            Text("HOST", color = ShynaDesign.colors.BrandGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = ShynaDesign.colors.BrandGreen) }
        },
        containerColor = Color(0xFF1F2C34)
    )
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s) 
    else String.format(java.util.Locale.US, "%02d:%02d", m, s)
}
