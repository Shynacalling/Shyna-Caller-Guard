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
import android.media.projection.MediaProjectionManager
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.callruleblocker.call.*
import com.example.callruleblocker.ui.ShynaDesign
import com.example.callruleblocker.ui.WhiteboardScreen
import com.example.callruleblocker.ui.ShynaTheme
import com.example.callruleblocker.ui.ThemeMode
import com.example.callruleblocker.ui.VideoRenderer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.AudioTrack
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.Track
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
    var showWhiteboard by remember { mutableStateOf(false) }
    
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

    // Handle Auto-Accept from Notification or Screen
    LaunchedEffect(autoAccept, isIncoming, call?.status) {
        if (isIncoming && autoAccept) {
            hasAcceptedIncoming = true
            if (call?.status == AppCallStatus.RINGING) {
                callManager.callAction(callId, "accept")
            }
            joinCall()
        } else if (isIncoming && (call?.status == AppCallStatus.ACCEPTED || call?.status == AppCallStatus.CONNECTED)) {
            hasAcceptedIncoming = true
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
                            onShowParticipants = { showParticipants = true },
                            onShowWhiteboard = { showWhiteboard = true }
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
            ZoomParticipantsBottomSheet(
                room = room!!,
                onDismiss = { showParticipants = false }
            )
        }

        if (showWhiteboard) {
            WhiteboardScreen(
                meetingId = callId,
                onClose = { showWhiteboard = false }
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
fun VideoCallUI(
    call: AppCall,
    isIncoming: Boolean,
    room: Room?,
    scope: CoroutineScope,
    duration: Long,
    networkType: String,
    remoteTracks: List<VideoTrack>,
    localTrack: VideoTrack?,
    isMuted: Boolean,
    isCameraOff: Boolean,
    isSpeakerOn: Boolean,
    isScreenSharing: Boolean,
    screenShareLauncher: ActivityResultLauncher<Intent>,
    onMuteToggle: () -> Unit,
    onCameraToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onScreenShareToggle: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    onShowParticipants: () -> Unit,
    onShowWhiteboard: () -> Unit
) {
    val peerName = if (isIncoming) call.callerName else call.receiverName
    val mContext = LocalContext.current
    val isMeetingCall = call.isGroup || call.id.startsWith("MEETING_") || call.receiverUid == "MEETING_ROOM"
    val meetingTitle = if (call.isGroup || call.id.startsWith("MEETING_")) "${FirebaseAuth.getInstance().currentUser?.displayName ?: "Shashi"}'s Meeting" else peerName

    var showChatDialog by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    val chatMessages = remember { mutableStateListOf<Pair<String, String>>("Host" to "Welcome to Shyna Meeting!") }
    var chatInput by remember { mutableStateOf("") }

    val screenShareTracks = remember(remoteTracks) {
        remoteTracks.filter { 
            it.name.contains("screen", true) || it.name.contains("share", true)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Video View / Grid / Screen Share
        if (screenShareTracks.isNotEmpty() && room != null) {
            VideoRenderer(screenShareTracks.first(), room, modifier = Modifier.fillMaxSize())
            
            // Top floating camera window during screen share (Zoom style)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 90.dp, end = 16.dp)
                    .size(120.dp, 170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray)
                    .border(2.dp, Color.White.copy(0.4f), RoundedCornerShape(12.dp))
                    .zIndex(20f)
            ) {
                localTrack?.let { VideoRenderer(it, room, modifier = Modifier.fillMaxSize()) }
                Text(
                    text = "Speaker",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        } else if (room != null && remoteTracks.isNotEmpty()) {
            VideoGrid(remoteTracks, room, modifier = Modifier.fillMaxSize())
        } else if (isMeetingCall && room?.state == Room.State.CONNECTED) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, modifier = Modifier.size(110.dp), color = Color.DarkGray) {
                        val photo = FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() ?: call.callerPhoto
                        if (!photo.isNullOrBlank()) AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.Person, null, modifier = Modifier.padding(24.dp), tint = Color.LightGray)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(if (isCameraOff) "Camera is off" else "Waiting for participants to join...", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    val cleanMeetingId = call.id.removePrefix("MEETING_")
                    Text("Meeting ID: $cleanMeetingId", color = ShynaDesign.colors.BrandGreen, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen)
                    Spacer(Modifier.height(16.dp))
                    Text("Connecting to Shyna Meeting...", color = Color.White)
                }
            }
        }

        // Local Video PiP Preview if remote is primary
        if (room != null && localTrack != null && remoteTracks.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 90.dp, end = 16.dp)
                    .size(110.dp, 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray)
                    .border(2.dp, Color.White.copy(0.4f), RoundedCornerShape(12.dp))
                    .zIndex(5f)
            ) {
                VideoRenderer(localTrack, room, modifier = Modifier.fillMaxSize())
                if (isCameraOff) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VideocamOff, null, tint = Color.White)
                    }
                }
                Text(
                    text = "You",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Name Tag Overlay on Video (Zoom style bottom-left badge)
        Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 110.dp).zIndex(6f)) {
            Surface(
                color = Color.Black.copy(0.6f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = FirebaseAuth.getInstance().currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "iam shashi",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // --- ZOOM TOP BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.8f), Color.Transparent)))
                .padding(top = 36.dp, start = 12.dp, end = 12.dp, bottom = 20.dp)
                .zIndex(10f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top-left Minimize arrow
            IconButton(onClick = onEndCall) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(4.dp))
            // Meeting Title with dropdown arrow
            Row(
                modifier = Modifier.weight(1f).clickable { showSecurityDialog = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = meetingTitle,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            // Top-right Zoom Action Icons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                // 1. Security Shield with Check
                IconButton(onClick = { showSecurityDialog = true }, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Security, contentDescription = "Security", tint = Color.White, modifier = Modifier.size(20.dp))
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFF25D366), CircleShape).align(Alignment.BottomEnd))
                    }
                }
                // 2. Whiteboard / Pen
                IconButton(onClick = onShowWhiteboard, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Whiteboard", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                // 3. Sparkles / Effects
                IconButton(onClick = { Toast.makeText(mContext, "Video touch-up active", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Effects", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                // 4. Audio Routing Speaker
                IconButton(onClick = onSpeakerToggle, modifier = Modifier.size(36.dp)) {
                    Icon(if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute, contentDescription = "Audio", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                // 5. Camera Switch
                IconButton(onClick = onSwitchCamera, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // --- ZOOM BOTTOM BAR ---
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF121B22))
                .zIndex(10f),
            color = Color(0xFF121B22)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Mute / Unmute
                ZoomBottomButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    isActive = isMuted,
                    onClick = onMuteToggle
                )
                // 2. Stop Video / Start Video
                ZoomBottomButton(
                    icon = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                    label = if (isCameraOff) "Start video" else "Stop video",
                    isActive = isCameraOff,
                    onClick = onCameraToggle
                )
                // 3. Chat
                ZoomBottomButton(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = "Chat",
                    isActive = false,
                    onClick = { showChatDialog = true }
                )
                // 4. Participants (with count badge)
                ZoomBottomButtonWithBadge(
                    icon = Icons.Default.Groups,
                    label = "Participants",
                    badgeCount = (room?.remoteParticipants?.size ?: 0) + 1,
                    onClick = onShowParticipants
                )
                // 5. More (...)
                ZoomBottomButton(
                    icon = Icons.Default.MoreHoriz,
                    label = "More",
                    isActive = false,
                    onClick = { showMoreDialog = true }
                )
                // 6. End (Red Button)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onEndCall).padding(4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE53935),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "End", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("End", color = Color(0xFFE53935), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- ZOOM CHAT BOTTOM SHEET ---
        if (showChatDialog) {
            ZoomChatBottomSheet(
                chatMessages = chatMessages,
                onDismiss = { showChatDialog = false },
                onSendMessage = { msgText ->
                    chatMessages.add("You" to msgText)
                }
            )
        }

        // --- ZOOM MORE OPTIONS BOTTOM SHEET ---
        if (showMoreDialog) {
            ZoomMoreBottomSheet(
                onDismiss = { showMoreDialog = false },
                onStartShare = {
                    val mediaProjectionManager = mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                },
                onShowCaptions = { Toast.makeText(mContext, "Live Captions enabled", Toast.LENGTH_SHORT).show() },
                onMeetingInfo = { showSecurityDialog = true },
                onHostTools = { Toast.makeText(mContext, "Host Tools & Security", Toast.LENGTH_SHORT).show(); showSecurityDialog = true },
                onSettings = { Toast.makeText(mContext, "Meeting Settings", Toast.LENGTH_SHORT).show() },
                onRaiseHand = { Toast.makeText(mContext, "Hand raised", Toast.LENGTH_SHORT).show() },
                onReaction = { emoji: String -> Toast.makeText(mContext, "Reaction: $emoji", Toast.LENGTH_SHORT).show() },
                onDisconnectAudio = {
                    val audioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.mode = AudioManager.MODE_NORMAL
                    Toast.makeText(mContext, "Audio disconnected", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // --- SECURITY BOTTOM SHEET ---
        if (showSecurityDialog) {
            ZoomSecurityBottomSheet(
                callId = call.id,
                isHost = true,
                onDismiss = { showSecurityDialog = false }
            )
        }
    }
}

@Composable
fun ZoomBottomButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color(0xFFE53935) else Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) Color(0xFFE53935) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ZoomBottomButtonWithBadge(
    icon: ImageVector,
    label: String,
    badgeCount: Int,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Surface(
                color = ShynaDesign.colors.BrandGreen,
                shape = CircleShape,
                modifier = Modifier.offset(x = 8.dp, y = (-4).dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
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
fun ZoomChatBottomSheet(
    chatMessages: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.6f))
            .clickable(onClick = onDismiss)
            .zIndex(60f),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFF121B22)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color.Gray, CircleShape)
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("In-Meeting Chat", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF2A3942))
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chatMessages.size) { idx ->
                        val msg = chatMessages[idx]
                        val isMe = msg.first == "You"
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                text = msg.first,
                                color = if (isMe) ShynaDesign.colors.BrandGreen else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isMe) Color(0xFF005C4B) else Color(0xFF2A3942),
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = msg.second,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Send message to everyone", color = Color.Gray, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ShynaDesign.colors.BrandGreen,
                            unfocusedBorderColor = Color(0xFF2A3942),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1F2C34),
                            unfocusedContainerColor = Color(0xFF1F2C34)
                        ),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onSendMessage(textInput)
                                textInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(ShynaDesign.colors.BrandGreen, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomParticipantsBottomSheet(
    room: Room,
    onDismiss: () -> Unit
) {
    val participants = remember(room.remoteParticipants.size) {
        listOf(room.localParticipant) + room.remoteParticipants.values.toList()
    }
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val nameCache = remember { mutableStateMapOf<String, String>() }
    val currentAuthUser = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(participants) {
        participants.forEach { p ->
            val uid = p.identity?.value ?: return@forEach
            if (!nameCache.containsKey(uid)) {
                if (uid == currentAuthUser?.uid) {
                    nameCache[uid] = currentAuthUser.displayName ?: "Shashi"
                } else {
                    runCatching {
                        val doc = db.collection("users").document(uid).get().await()
                        val name = doc.getString("name") ?: doc.getString("displayName")
                        if (!name.isNullOrBlank()) {
                            nameCache[uid] = name
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.6f))
            .clickable(onClick = onDismiss)
            .zIndex(60f),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFF121B22)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color.Gray, CircleShape)
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Participants (${participants.size})", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                room.remoteParticipants.values.forEach { remoteP ->
                                    remoteP.audioTrackPublications.forEach { (it.second as? AudioTrack)?.enabled = false }
                                }
                                Toast.makeText(context, "Muted all participants", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A3942)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.MicOff, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mute All", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF2A3942))
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(participants.size) { index ->
                        val p = participants[index]
                        val isLocal = p is LocalParticipant
                        val uid = p.identity?.value ?: ""
                        val resolvedName = nameCache[uid] ?: p.name?.takeIf { it.isNotBlank() && !it.startsWith("room_") && !it.startsWith("call_") } ?: if (isLocal) (currentAuthUser?.displayName ?: "Shashi") else "Shyna Member"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1F2C34), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = ShynaDesign.colors.BrandGreen.copy(0.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = resolvedName.take(1).uppercase(),
                                        color = ShynaDesign.colors.BrandGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isLocal) "$resolvedName (You)" else resolvedName,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    if (isLocal) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = ShynaDesign.colors.BrandGreen,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "HOST",
                                                color = Color.Black,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (p.isMicrophoneEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                            contentDescription = null,
                                            tint = if (p.isMicrophoneEnabled) ShynaDesign.colors.BrandGreen else Color.Red,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (p.isMicrophoneEnabled) "Audio on" else "Muted",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (p.isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                            contentDescription = null,
                                            tint = if (p.isCameraEnabled) ShynaDesign.colors.BrandGreen else Color.Red,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (p.isCameraEnabled) "Video on" else "Camera off",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s) 
    else String.format(java.util.Locale.US, "%02d:%02d", m, s)
}

@Composable
fun ZoomMoreBottomSheet(
    onDismiss: () -> Unit,
    onStartShare: () -> Unit,
    onShowCaptions: () -> Unit,
    onMeetingInfo: () -> Unit,
    onHostTools: () -> Unit,
    onSettings: () -> Unit,
    onRaiseHand: () -> Unit,
    onReaction: (String) -> Unit,
    onDisconnectAudio: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.5f))
            .clickable(onClick = onDismiss)
            .zIndex(50f),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFF121B22)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color.Gray, CircleShape)
                )
                Spacer(Modifier.height(16.dp))

                // Top Reactions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onRaiseHand,
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF2A3942),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PanTool, null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Raise hand", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    listOf("🎉", "👍", "❤️").forEach { emoji ->
                        Surface(
                            onClick = { onReaction(emoji) },
                            shape = CircleShape,
                            color = Color(0xFF2A3942),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }
                    IconButton(onClick = { onReaction("👏") }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.MoreHoriz, null, tint = Color.White)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Grid Rows
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        MoreActionItem(icon = Icons.AutoMirrored.Filled.ScreenShare, label = "Start share", onClick = { onDismiss(); onStartShare() })
                        MoreActionItem(icon = Icons.Default.ClosedCaption, label = "Show captions", onClick = { onDismiss(); onShowCaptions() })
                        MoreActionItem(icon = Icons.Default.Apps, label = "Apps", onClick = { onDismiss() })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        MoreActionItem(icon = Icons.Default.HeadsetOff, label = "Disconnect audio", onClick = { onDismiss(); onDisconnectAudio() })
                        MoreActionItem(icon = Icons.Default.Info, label = "Meeting info", onClick = { onDismiss(); onMeetingInfo() })
                        MoreActionItem(icon = Icons.Default.Security, label = "Host tools", onClick = { onDismiss(); onHostTools() })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Box(modifier = Modifier.width(100.dp)) {
                            MoreActionItem(icon = Icons.Default.Settings, label = "Settings", onClick = { onDismiss(); onSettings() })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MoreActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(80.dp)
            .padding(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF2A3942),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun ZoomSecurityBottomSheet(
    callId: String,
    isHost: Boolean,
    onDismiss: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    var lockMeeting by remember { mutableStateOf(false) }
    var waitingRoom by remember { mutableStateOf(false) }
    var hideProfiles by remember { mutableStateOf(false) }
    var allowShareScreen by remember { mutableStateOf(true) }
    var allowChat by remember { mutableStateOf(true) }
    var allowRename by remember { mutableStateOf(true) }
    var allowUnmute by remember { mutableStateOf(true) }
    var allowStartVideo by remember { mutableStateOf(true) }
    var allowWhiteboards by remember { mutableStateOf(true) }
    var allowNotes by remember { mutableStateOf(true) }
    var allowTimers by remember { mutableStateOf(true) }
    var allowRecordingRequest by remember { mutableStateOf(true) }

    LaunchedEffect(callId) {
        runCatching {
            val doc = db.collection("app_calls").document(callId).get().await()
            lockMeeting = doc.getBoolean("locked") ?: false
            waitingRoom = doc.getBoolean("waitingRoomEnabled") ?: false
            hideProfiles = doc.getBoolean("hideProfilePictures") ?: false
            allowShareScreen = doc.getBoolean("allowShareScreen") ?: true
            allowChat = doc.getBoolean("allowChat") ?: true
            allowRename = doc.getBoolean("allowRename") ?: true
            allowUnmute = doc.getBoolean("allowUnmute") ?: true
            allowStartVideo = doc.getBoolean("allowStartVideo") ?: true
            allowWhiteboards = doc.getBoolean("allowWhiteboards") ?: true
            allowNotes = doc.getBoolean("allowNotes") ?: true
            allowTimers = doc.getBoolean("allowTimers") ?: true
            allowRecordingRequest = doc.getBoolean("allowRecordingRequest") ?: true
        }
    }

    val updateSetting: (String, Boolean) -> Unit = { key, value ->
        db.collection("app_calls").document(callId).update(key, value)
            .addOnSuccessListener { Toast.makeText(context, "Security updated", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { Toast.makeText(context, "Failed to update security", Toast.LENGTH_SHORT).show() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.6f))
            .clickable(onClick = onDismiss)
            .zIndex(70f),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFF121B22)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color.Gray, CircleShape)
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Security Controls", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF2A3942))
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SecurityToggleRow("Lock Meeting", lockMeeting) {
                            lockMeeting = it
                            updateSetting("locked", it)
                        }
                    }
                    item {
                        SecurityToggleRow("Enable Waiting Room", waitingRoom) {
                            waitingRoom = it
                            updateSetting("waitingRoomEnabled", it)
                        }
                    }
                    item {
                        SecurityToggleRow("Hide Profile Pictures", hideProfiles) {
                            hideProfiles = it
                            updateSetting("hideProfilePictures", it)
                        }
                    }

                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Allow All Participants to:", color = ShynaDesign.colors.BrandGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                    }

                    item { SecurityCheckboxRow("Share Screen", allowShareScreen) { allowShareScreen = it; updateSetting("allowShareScreen", it) } }
                    item { SecurityCheckboxRow("Chat", allowChat) { allowChat = it; updateSetting("allowChat", it) } }
                    item { SecurityCheckboxRow("Rename Themselves", allowRename) { allowRename = it; updateSetting("allowRename", it) } }
                    item { SecurityCheckboxRow("Unmute Themselves", allowUnmute) { allowUnmute = it; updateSetting("allowUnmute", it) } }
                    item { SecurityCheckboxRow("Start Video", allowStartVideo) { allowStartVideo = it; updateSetting("allowStartVideo", it) } }
                    item { SecurityCheckboxRow("Share Whiteboards", allowWhiteboards) { allowWhiteboards = it; updateSetting("allowWhiteboards", it) } }
                    item { SecurityCheckboxRow("Share Notes", allowNotes) { allowNotes = it; updateSetting("allowNotes", it) } }
                    item { SecurityCheckboxRow("Set Meeting Timers", allowTimers) { allowTimers = it; updateSetting("allowTimers", it) } }
                    item { SecurityCheckboxRow("Request Host to Start Cloud Recording", allowRecordingRequest) { allowRecordingRequest = it; updateSetting("allowRecordingRequest", it) } }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            onClick = {
                                lockMeeting = true
                                updateSetting("locked", true)
                                Toast.makeText(context, "Participant activities suspended", Toast.LENGTH_LONG).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE53935).copy(0.2f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Suspend Participant Activities",
                                color = Color(0xFFE53935),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ShynaDesign.colors.BrandGreen)
        )
    }
}

@Composable
fun SecurityCheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = ShynaDesign.colors.BrandGreen, uncheckedColor = Color.Gray)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}
