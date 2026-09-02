package com.example.callruleblocker

import android.Manifest
import android.app.KeyguardManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.telephony.SubscriptionManager
import android.media.AudioManager
import android.util.Rational
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.callruleblocker.ui.CallScreeningOverlay
import com.example.callruleblocker.ui.LocalAppearance
import com.example.callruleblocker.ui.PersonalizationManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.callruleblocker.call.CallControlCenter
import com.example.callruleblocker.call.CallHolder
import com.example.callruleblocker.call.CallUiVisibility
import com.example.callruleblocker.call.CallRecorder
import com.example.callruleblocker.call.SimCallManager
import com.example.callruleblocker.sim.SimSlotResolver
import com.example.callruleblocker.ui.CompactSimBadge

import com.example.callruleblocker.ui.theme.CallRuleBlockerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

class CallActivity : ComponentActivity() {

    private var proximityWakeLock: PowerManager.WakeLock? = null

    internal fun updateProximitySensor(active: Boolean) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (active) {
            if (proximityWakeLock == null && (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK))) {
                proximityWakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Shyna:ProximityLock")
            }
            if (proximityWakeLock?.isHeld == false) proximityWakeLock?.acquire(10 * 60 * 1000L /* 10 minutes */)
        } else {
            if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)

        setContent {
            val context = LocalContext.current
            var appearanceSettings by remember { mutableStateOf(PersonalizationManager.getSettings(context)) }
            
            CompositionLocalProvider(LocalAppearance provides appearanceSettings) {
                CallRuleBlockerTheme {
                    val call by CallHolder.currentCall.collectAsState()
                    val audioState by CallControlCenter.audioState.collectAsState()
                    val calls by CallControlCenter.allCalls.collectAsState()
                    val currentCall = call
                    if (currentCall == null) {
                        LaunchedEffect(Unit) { finish() }
                    } else {
                        AdvancedCallScreen(
                            call = currentCall,
                            audioState = audioState,
                            calls = calls,
                        ) { finish() }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CallUiVisibility.onResumed()

        // A ringing call is already fully represented by this screen, so remove
        // any stale heads-up banner left from the transition into CallActivity.
        if (CallHolder.currentCall.value?.state == Call.STATE_RINGING) {
            OngoingCallNotification.cancel(this)
        }
    }

    override fun onPause() {
        // Mark background before rebuilding a ringing notification. This makes
        // Home/Recents/another app immediately eligible for the compact banner.
        CallUiVisibility.onPaused()
        val ringingCall = CallHolder.currentCall.value?.takeIf { it.state == Call.STATE_RINGING }
        if (ringingCall != null) {
            lifecycleScope.launch {
                OngoingCallNotification.showIncoming(this@CallActivity, ringingCall)
            }
        }
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val prefs = getSharedPreferences("call_settings", Context.MODE_PRIVATE)
            val call = CallHolder.currentCall.value
            if (prefs.getBoolean("volume_answer", false) && call?.state == Call.STATE_RINGING) {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val call = CallHolder.currentCall.value
        if (call != null && VideoProfile.isVideo(call.details.videoState)) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onDestroy() {
        updateProximitySensor(active = false)
        if (isFinishing && CallHolder.currentCall.value?.state in listOf(Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING)) {
            runCatching {
                CallHolder.currentCall.value?.videoCall?.apply {
                    setCamera(null)
                    setPreviewSurface(null)
                    setDisplaySurface(null)
                }
            }
        }
        super.onDestroy()
    }
}

private data class CallerData(val name: String?, val photoBytes: ByteArray?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CallerData
        if (name != other.name) return false
        if (photoBytes != null) {
            if (other.photoBytes == null) return false
            if (!photoBytes.contentEquals(other.photoBytes)) return false
        } else if (other.photoBytes != null) return false
        return true
    }
    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (photoBytes?.contentHashCode() ?: 0)
        return result
    }
}

@Composable
private fun AdvancedCallScreen(
    call: Call,
    audioState: CallAudioState?,
    calls: List<Call>,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val recorder = remember { CallRecorder(context) }
    val recordingScope = rememberCoroutineScope()
    var amplitudeMonitorJob by remember { mutableStateOf<Job?>(null) }
    val settings = remember { context.getSharedPreferences("call_settings", Context.MODE_PRIVATE) }
    var callState by remember { mutableIntStateOf(call.state) }
    var callRevision by remember { mutableIntStateOf(0) }
    var showCalculator by remember { mutableStateOf(false) }
    var showDtmfKeypad by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }
    var lastDtmfDigit by remember { mutableStateOf<Char?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    var showRecordingBlockedDialog by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }


    var showAddCallChooser by remember { mutableStateOf(false) }
    var showParticipants by remember { mutableStateOf(false) }
    var videoRefreshCounter by remember { mutableIntStateOf(0) }
    var videoState by remember(callRevision) { mutableIntStateOf(call.details.videoState) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var isCameraOn by remember { mutableStateOf(true) }
    var isScreenOff by remember { mutableStateOf(false) }
    var showVideoUpgradeDialog by remember { mutableStateOf(false) }
    var pendingVideoProfile by remember { mutableStateOf<VideoProfile?>(null) }
    var isScreening by remember { mutableStateOf(false) }
    val isVideoMode = VideoProfile.isVideo(videoState)
    val appearance = LocalAppearance.current

    DisposableEffect(isVideoMode) {
        val activity = context as? CallActivity
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (isVideoMode) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler(enabled = isVideoMode) {
        val activity = context as? CallActivity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build()
            runCatching { activity.enterPictureInPictureMode(params) }
                .onFailure { activity.moveTaskToBack(true) }
        } else {
            activity?.moveTaskToBack(true)
        }
    }

    val rotation = LocalConfiguration.current.orientation
    LaunchedEffect(callRevision, isVideoMode, rotation) {
        val videoCall = call.videoCall ?: return@LaunchedEffect
        if (isVideoMode) {
            val deviceOrientation = when (rotation) {
                android.content.res.Configuration.ORIENTATION_LANDSCAPE -> 90
                else -> 0
            }
            videoCall.setDeviceOrientation(deviceOrientation)
        }
    }

    LaunchedEffect(callRevision, isFrontCamera, isCameraOn, isVideoMode) {
        val videoCall = call.videoCall ?: return@LaunchedEffect
        if (!isCameraOn || !isVideoMode) {
            videoCall.setCamera(null)
            return@LaunchedEffect
        }
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = cameraManager.cameraIdList
        var targetId: String? = null
        for (id in ids) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (isFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                targetId = id
                break
            } else if (!isFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) {
                targetId = id
                break
            }
        }
        if (targetId != null) videoCall.setCamera(targetId)
    }

    LaunchedEffect(callState, audioState, videoState) {
        val activity = context as? CallActivity ?: return@LaunchedEffect
        val isEarpiece = audioState?.route == CallAudioState.ROUTE_EARPIECE
        val shouldDim = callState == Call.STATE_ACTIVE && isEarpiece && !isVideoMode
        activity.updateProximitySensor(shouldDim)
    }

    DisposableEffect(call, callRevision) {
        val videoCall = call.videoCall
        val callback = object : InCallService.VideoCall.Callback() {
            override fun onSessionModifyRequestReceived(videoProfile: VideoProfile) {
                if (VideoProfile.isVideo(videoProfile.videoState)) {
                    pendingVideoProfile = videoProfile
                    showVideoUpgradeDialog = true
                }
            }
            override fun onSessionModifyResponseReceived(status: Int, requestedProfile: VideoProfile, responseProfile: VideoProfile) {
                videoState = responseProfile.videoState
            }
            override fun onCallSessionEvent(event: Int) {}
            override fun onPeerDimensionsChanged(width: Int, height: Int) {
                videoRefreshCounter++
            }
            override fun onVideoQualityChanged(videoQuality: Int) {}
            override fun onCallDataUsageChanged(dataUsage: Long) {}
            override fun onCameraCapabilitiesChanged(cameraCapabilities: VideoProfile.CameraCapabilities) {}
        }
        videoCall?.registerCallback(callback)
        onDispose { videoCall?.unregisterCallback(callback) }
    }

    val phoneContactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val number = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        if (!number.isNullOrBlank()) SimCallManager.placeCall(context, number, null)
        else Toast.makeText(context, "No callable number found", Toast.LENGTH_SHORT).show()
    }
    val callNumber = call.details.handle?.schemeSpecificPart.orEmpty()
    var callerData by remember(callNumber) { mutableStateOf(CallerData(null, null)) }
    val incomingCall = remember(call) { call.state == Call.STATE_RINGING }
    var resolvedSimSlot by remember(call) { mutableIntStateOf(0) }
    var resolvedSimLabel by remember(call) { mutableStateOf("SIM 1") }

    LaunchedEffect(call, callRevision) {
        val slot = runCatching {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                SimSlotResolver.resolveSlot(context, call.details.accountHandle)
            } else 0
        }.getOrDefault(0).coerceIn(0, 1)
        resolvedSimSlot = slot
        resolvedSimLabel = buildSimDisplayLabel(context, slot)
    }

    val showName = settings.getBoolean("caller_name", true)
    val showNumber = settings.getBoolean("caller_number", true)
    val showSim = settings.getBoolean("caller_sim", true)
    val advancedPrefs = remember { context.getSharedPreferences("advanced_feature_control_v5", Context.MODE_PRIVATE) }
    val videoCallerIdEnabled = advancedPrefs.getBoolean("feature_video_full", true)
    
    val backgroundMode = settings.getString("call_background", "AURORA") ?: "AURORA"
    val customBackgroundUri = settings.getString("call_background_uri", null)
    var customBackgroundBytes by remember(customBackgroundUri) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(callNumber) { callerData = lookupCallerData(context, callNumber) }
    LaunchedEffect(customBackgroundUri) {
        customBackgroundBytes = if (backgroundMode == "CUSTOM" && !customBackgroundUri.isNullOrBlank()) {
            loadImageBytes(context, Uri.parse(customBackgroundUri))
        } else null
    }

    val isRinging = callState == Call.STATE_RINGING
    val isMuted = audioState?.isMuted == true
    val route = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
    val isSpeakerOn = route == CallAudioState.ROUTE_SPEAKER
    val isBluetoothOn = route == CallAudioState.ROUTE_BLUETOOTH

    // Sync speaker state to recorder for quality hints
    LaunchedEffect(isSpeakerOn) {
        recorder.isSpeakerOn = isSpeakerOn
    }

    var recordingStartPending by remember(call) { mutableStateOf(false) }

    fun beginRecording(showMessage: Boolean) {
        if (!hasRecordPermission(context)) {
            Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
            return
        }
        if (recorder.isRecording() || recordingStartPending) return
        recordingStartPending = true

        recordingScope.launch {
            delay(250)
            if (callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) {
                recordingStartPending = false
                return@launch
            }
            recorder.isSpeakerOn = CallControlCenter.audioState.value?.route == CallAudioState.ROUTE_SPEAKER
            recorder.start(callNumber)
                .onSuccess {
                    recordingStartPending = false
                    isRecording = true
                    amplitudeMonitorJob?.cancel()
                    amplitudeMonitorJob = recordingScope.launch {
                        while (recorder.isRecording()) {
                            recorder.currentAmplitude()
                            delay(250)
                        }
                    }
                    if (showMessage) Toast.makeText(
                        context,
                        "Recording started. Audio route was not changed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure { error ->
                    recordingStartPending = false
                    isRecording = false
                    Toast.makeText(context, error.message ?: "Recording could not start", Toast.LENGTH_LONG).show()
                }
        }
    }

    fun finishRecording(showMessage: Boolean) {
        if (!recorder.isRecording()) return
        amplitudeMonitorJob?.cancel()
        amplitudeMonitorJob = null
        recorder.currentAmplitude()
        recorder.stop()
            .onSuccess { file ->
                isRecording = false
                if (showMessage) Toast.makeText(context, "Saved: ${file.name}", Toast.LENGTH_SHORT).show()
            }
            .onFailure { error ->
                isRecording = false
                if (error is com.example.callruleblocker.call.CallAudioBlockedException) {
                    showRecordingBlockedDialog = true
                } else {
                    Toast.makeText(
                        context,
                        error.message ?: "No audible recording was saved",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    DisposableEffect(call) {
        val callback = object : Call.Callback() {
            override fun onStateChanged(changedCall: Call, state: Int) {
                callState = state
                callRevision++
                if (state == Call.STATE_DISCONNECTING || state == Call.STATE_DISCONNECTED) {
                    // DISCONNECTING is already a terminal Telecom transition. Some OEM/carrier
                    // stacks can remain in it for a noticeable time before DISCONNECTED/onCallRemoved.
                    // Stop call-only work immediately and do not leave a stale running-call UI.
                    recordingStartPending = false
                    if (recorder.isRecording()) finishRecording(showMessage = false)
                    val replacement = CallControlCenter.allCalls.value.firstOrNull {
                        it !== changedCall && it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING
                    }
                    if (replacement != null) {
                        CallHolder.set(replacement)
                    } else {
                        CallHolder.set(null)
                        onFinish()
                    }
                }
            }

            override fun onChildrenChanged(changedCall: Call, children: MutableList<Call>) {
                callRevision++
            }

            override fun onConferenceableCallsChanged(changedCall: Call, conferenceableCalls: MutableList<Call>) {
                callRevision++
            }

            override fun onDetailsChanged(changedCall: Call, details: Call.Details) {
                callRevision++
            }
        }
        call.registerCallback(callback)
        callState = call.state
        onDispose {
            call.unregisterCallback(callback)
            recordingStartPending = false
            if (recorder.isRecording()) finishRecording(showMessage = false)
        }
    }

    LaunchedEffect(callState, callRevision) {
        if (callState == Call.STATE_ACTIVE || callState == Call.STATE_HOLDING) {
            val connectTime = call.details.connectTimeMillis
            while (true) {
                val now = System.currentTimeMillis()
                elapsed = if (connectTime > 0) (now - connectTime) / 1000 else 0
                kotlinx.coroutines.delay(1000)
            }
        } else {
            elapsed = 0
        }
    }

    LaunchedEffect(callState, callerData.name) {
        if (callState == Call.STATE_ACTIVE && shouldAutoRecord(context, callNumber, incomingCall, callerData.name != null)) {
            if (!recorder.isRecording()) beginRecording(showMessage = false)
        }
    }

    val isConference = call.details.hasProperty(Call.Details.PROPERTY_CONFERENCE) || call.children.isNotEmpty()
    val canHold = !isConference
    val isHolding = callState == Call.STATE_HOLDING
    val canMerge = call.conferenceableCalls.isNotEmpty() || calls.any { it.conferenceableCalls.isNotEmpty() }
    val conferenceParticipants = remember(call, calls, callRevision) {
        val children = call.children
        if (children.isNotEmpty()) children else calls.filter { it !== call && it.state != Call.STATE_DISCONNECTED }
    }

    fun endCurrentSession() {
        runCatching {
            call.videoCall?.apply {
                setCamera(null)
                setPreviewSurface(null)
                setDisplaySurface(null)
            }
        }
        isCameraOn = false
        CallControlCenter.end(call)
    }

    DisposableEffect(call) {
        onDispose {
            runCatching {
                call.videoCall?.apply {
                    setCamera(null)
                    setPreviewSurface(null)
                    setDisplaySurface(null)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(callBackground(backgroundMode))) {
        if (videoCallerIdEnabled && isRinging) {
            val videoUri = settings.getString("call_background_video_uri", null)
            if (!videoUri.isNullOrBlank()) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoURI(Uri.parse(videoUri))
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            }
        }
        
        if (customBackgroundBytes != null && (!videoCallerIdEnabled || !isRinging)) {
            val bitmap = remember(customBackgroundBytes) {
                customBackgroundBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
            }
            if (bitmap != null) {
                Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .then(if (isVideoMode) Modifier else Modifier.padding(horizontal = 22.dp, vertical = 26.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val otherCalls = remember(calls, call) { calls.filter { it !== call && it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING } }
            if (otherCalls.isNotEmpty() && !isVideoMode) {
                val heldCall = otherCalls.first()
                MultiCallBanner(heldCall, onSwap = { swapTarget -> 
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    CallControlCenter.swap(call, swapTarget) 
                })
                Spacer(Modifier.height(16.dp))
            }

            if (!isVideoMode) {
                Text(stateLabel(callState), color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.titleMedium)
            }
            if (showSim && !isVideoMode) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompactSimBadge(index = resolvedSimSlot + 1)
                    Spacer(Modifier.width(8.dp))
                    Text(resolvedSimLabel, color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (!isVideoMode) {
                Spacer(Modifier.height(18.dp))
                CallerAvatar(callerData.photoBytes)
                Spacer(Modifier.height(18.dp))
                if (showName) {
                    Text(
                        callerData.name ?: if (!showNumber) "Unknown caller" else callNumber.ifBlank { "Unknown caller" },
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
                
                // --- PREMIUM SPAM RISK BADGE ---
                if (callerData.name == null && callNumber.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE53E36).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE53E36).copy(alpha = 0.4f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Report, null, tint = Color(0xFFE53E36), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("High Risk - Reported by 400+ users", color = Color(0xFFE53E36), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (showNumber && callNumber.isNotBlank() && (callerData.name != null || !showName)) {
                    Spacer(Modifier.height(4.dp))
                    Text(callNumber, color = Color.White.copy(alpha = .76f), style = MaterialTheme.typography.titleMedium)
                }
                if (callState == Call.STATE_ACTIVE) {
                    Spacer(Modifier.height(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatElapsed(elapsed), color = Color.White.copy(alpha = .7f))
                            if (isRecording) {
                                Spacer(Modifier.width(8.dp))
                                RecordingIndicator(recorder.expectedQuality())
                            }
                        }
                        
                        // --- UNIVERSAL IVR DIGIT DISPLAY ---
                        AnimatedVisibility(
                            visible = dtmfDigits.isNotEmpty(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dtmfDigits,
                                    color = Color(0xFFD0BCFF),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 4.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(onClick = { dtmfDigits = "" }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Clear, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            AnimatedContent(
                targetState = isVideoMode,
                transitionSpec = {
                    fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "video-transition"
            ) { targetIsVideo ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (targetIsVideo) {
                        VideoLayout(
                            videoCall = call.videoCall,
                            isCameraOn = isCameraOn,
                            isScreenOff = isScreenOff,
                            refreshCounter = videoRefreshCounter,
                            onSwitchCamera = { isFrontCamera = !isFrontCamera },
                            onToggleCamera = { isCameraOn = !isCameraOn },
                            onToggleScreen = { isScreenOff = !isScreenOff },
                            isMuted = isMuted,
                            onToggleMute = { CallControlCenter.setMuted(!isMuted) },
                            onEndCall = { endCurrentSession() },
                            speakerOn = isSpeakerOn,
                            callerLabel = callerData.name ?: callNumber.ifBlank { "Video call" },
                            elapsedLabel = if (callState == Call.STATE_ACTIVE) formatElapsed(elapsed) else stateLabel(callState),
                            onBack = {
                                val activity = context as? CallActivity
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                    val params = PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build()
                                    runCatching { activity.enterPictureInPictureMode(params) }.onFailure { activity.moveTaskToBack(true) }
                                } else activity?.moveTaskToBack(true)
                            },
                            onToggleSpeaker = {
                                val target = if (isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
                                CallControlCenter.setRoute(target)
                            }
                        )
                    } else {
                        // Audio-call avatar is already rendered above with caller information.
                        // Keep this branch empty to prevent the same contact photo appearing twice.
                        Spacer(Modifier.fillMaxSize())
                    }
                }
            }

            if (!isVideoMode) Spacer(Modifier.weight(1f))

            if (isRinging) {
                val otherActive = otherCalls.firstOrNull { it.state == Call.STATE_ACTIVE || it.state == Call.STATE_HOLDING }
                ThemeableCallLayout(
                    themeId = appearance.callScreenThemeId,
                    callerName = callerData.name ?: if (!showNumber) "Unknown caller" else callNumber.ifBlank { "Unknown caller" },
                    callNumber = callNumber,
                    simLabel = resolvedSimLabel,
                    isSpam = callerData.name == null && callNumber.isNotBlank(),
                    onAnswer = {
                        OngoingCallNotification.cancel(context)
                        if (otherActive != null) {
                            CallControlCenter.answerAndHold(call, otherActive)
                        } else {
                            val state = if (VideoProfile.isVideo(call.details.videoState)) VideoProfile.STATE_BIDIRECTIONAL else VideoProfile.STATE_AUDIO_ONLY
                            call.answer(state)
                        }
                    },
                    onDecline = {
                        OngoingCallNotification.cancel(context)
                        call.reject(false, null)
                    },
                    onScreen = { isScreening = true },
                    onSilent = {
                        runCatching {
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
                            Toast.makeText(context, "Call Silenced", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMessage = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$callNumber")).apply {
                                putExtra("sms_body", "In a meeting, will call you back.")
                            }
                            context.startActivity(intent)
                            call.reject(false, null)
                        }
                    }
                )
            } else if (!isVideoMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xD9191721),
                    shape = RoundedCornerShape(34.dp),
                    tonalElevation = 8.dp
                ) {
                    AnimatedContent(
                        targetState = showDtmfKeypad,
                        transitionSpec = {
                            if (targetState) {
                                slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
                            } else {
                                slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut()
                            }
                        },
                        label = "ivr-keypad-transition"
                    ) { isKeypadVisible ->
                        if (isKeypadVisible) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                                val rows = listOf(listOf('1','2','3'), listOf('4','5','6'), listOf('7','8','9'), listOf('*','0','#'))
                                rows.forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        row.forEach { digit ->
                                            IvrKey(digit) {
                                                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                                dtmfDigits += digit
                                                lastDtmfDigit = digit
                                                call.playDtmfTone(digit)
                                                call.stopDtmfTone()
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    TextButton(onClick = { showDtmfKeypad = false }) {
                                        Icon(Icons.Default.KeyboardHide, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Hide Keypad", fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { EndCallButton { endCurrentSession() } }
                            }
                        } else {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 22.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    FeatureButton(Icons.Filled.Add, if (canMerge) "Merge calls" else "Add call") {
                                        if (canMerge) {
                                            if (!CallControlCenter.merge(call)) Toast.makeText(context, "Calls are not ready to merge", Toast.LENGTH_SHORT).show()
                                        } else showAddCallChooser = true
                                    }
                                    FeatureButton(Icons.Filled.Videocam, "Video call", enabled = true) {
                                        if (isVideoMode) {
                                            isFrontCamera = !isFrontCamera
                                        } else {
                                            val videoCall = call.videoCall
                                            if (videoCall == null) {
                                                Toast.makeText(context, "Carrier did not expose video upgrade for this call", Toast.LENGTH_LONG).show()
                                            } else {
                                                runCatching { videoCall.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_BIDIRECTIONAL)) }
                                                    .onSuccess { Toast.makeText(context, "Video call request sent", Toast.LENGTH_SHORT).show() }
                                                    .onFailure { Toast.makeText(context, "Video upgrade is not supported by this carrier call", Toast.LENGTH_LONG).show() }
                                            }
                                        }
                                    }
                                    FeatureButton(Icons.Filled.Bluetooth, "Bluetooth", selected = isBluetoothOn) {
                                        val target = if (isBluetoothOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_BLUETOOTH
                                        if (!CallControlCenter.setRoute(target)) {
                                            Toast.makeText(context, "Connect a Bluetooth call device first", Toast.LENGTH_SHORT).show()
                                            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(18.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    FeatureButton(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", selected = isSpeakerOn) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!CallControlCenter.setRoute(if (isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)) Toast.makeText(context, "Speaker route unavailable", Toast.LENGTH_SHORT).show()
                                    }
                                    FeatureButton(Icons.Filled.MicOff, "Mute", selected = isMuted) { 
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        CallControlCenter.setMuted(!isMuted) 
                                    }
                                    FeatureButton(Icons.Filled.Calculate, "Calculator", selected = showCalculator) { 
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showCalculator = !showCalculator 
                                    }
                                }
                                Spacer(Modifier.height(18.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    FeatureButton(Icons.Filled.Pause, if (isHolding) "Resume" else "Hold", selected = isHolding, enabled = canHold) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isHolding) call.unhold() else call.hold()
                                    }
                                    FeatureButton(Icons.Filled.FiberManualRecord, if (isRecording) "Stop record" else "Record", selected = isRecording) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isRecording) finishRecording(showMessage = true)
                                        else beginRecording(showMessage = true)
                                    }
                                    FeatureButton(
                                        if (isConference) Icons.Filled.People else Icons.Filled.Dialpad,
                                        if (isConference) "Participants" else "Keypad",
                                        selected = !isConference && showDtmfKeypad
                                    ) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isConference) showParticipants = true
                                        else showDtmfKeypad = true
                                    }
                                }
                                Spacer(Modifier.height(18.dp))
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { EndCallButton { endCurrentSession() } }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showCalculator) {
        CalculatorDialog(onDismiss = { showCalculator = false })
    }


    if (showAddCallChooser) {
        AlertDialog(
            onDismissRequest = { showAddCallChooser = false },
            title = { Text("Add another call") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = {
                            showAddCallChooser = false
                            context.startActivity(Intent(context, DialerActivity::class.java).apply {
                                putExtra("conference_add_call", true)
                            })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Dialpad, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Open dialer")
                    }
                    FilledTonalButton(
                        onClick = {
                            showAddCallChooser = false
                            phoneContactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Contacts, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Choose from contacts")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddCallChooser = false }) { Text("Cancel") } }
        )
    }

    if (showParticipants) {
        ConferenceParticipantsDialog(
            conferenceCall = call,
            participants = conferenceParticipants,
            onAddMore = { showParticipants = false; showAddCallChooser = true },
            onDismiss = { showParticipants = false },
            onEndConference = { showParticipants = false; call.disconnect() }
        )
    }





    if (showRecordingBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showRecordingBlockedDialog = false },
            title = { Text("Call Recording Blocked") },
            text = {
                Text("Some Android devices often restrict third-party call recording for privacy. To record both sides, please try turning on the speakerphone during the call.")
            },
            confirmButton = {
                TextButton(onClick = { showRecordingBlockedDialog = false }) { Text("OK") }
            }
        )
    }

    if (showVideoUpgradeDialog && pendingVideoProfile != null) {
        VideoUpgradeRequestDialog(
            callerName = callerData.name ?: callNumber,
            onAccept = {
                pendingVideoProfile?.let { profile ->
                    call.videoCall?.sendSessionModifyResponse(VideoProfile(profile.videoState))
                }
                pendingVideoProfile = null
                showVideoUpgradeDialog = false
            },
            onDecline = {
                call.videoCall?.sendSessionModifyResponse(VideoProfile(VideoProfile.STATE_AUDIO_ONLY))
                pendingVideoProfile = null
                showVideoUpgradeDialog = false
            }
        )
    }

    if (isScreening) {
        CallScreeningOverlay(
            callerName = callerData.name ?: callNumber,
            onAnswer = {
                isScreening = false
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
            },
            onDecline = {
                isScreening = false
                call.reject(false, null)
            }
        )
    }
}

@Composable
private fun VideoUpgradeRequestDialog(
    callerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Video Call Request") },
        text = { Text("$callerName wants to switch to a video call. Do you want to accept?") },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF36C567))
            ) {
                Icon(Icons.Default.Videocam, null)
                Spacer(Modifier.width(8.dp))
                Text("Accept")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDecline) {
                Text("Decline")
            }
        }
    )
}

@Composable
private fun ConferenceParticipantsDialog(
    conferenceCall: Call,
    participants: List<Call>,
    onAddMore: () -> Unit,
    onDismiss: () -> Unit,
    onEndConference: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conference call") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${participants.size.coerceAtLeast(2)} participants · carrier limit applies",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                participants.forEachIndexed { index, participant ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Person, null)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(participant.details.handle?.schemeSpecificPart ?: "Participant ${index + 1}")
                                Text(stateLabel(participant.state), style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { participant.disconnect() }) {
                                Icon(Icons.Filled.CallEnd, "Disconnect participant", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                FilledTonalButton(onClick = onAddMore, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PersonAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add more participant")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEndConference) { Text("End conference", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private suspend fun loadImageBytes(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
}

private fun callBackground(mode: String): Brush = when (mode) {
    "DARK", "BG02" -> Brush.verticalGradient(listOf(Color.Black, Color(0xFF111116), Color.Black))
    "MINIMAL", "BG03" -> Brush.verticalGradient(listOf(Color(0xFF10151C), Color(0xFF18232D), Color(0xFF10151C)))
    "BG04" -> Brush.verticalGradient(listOf(Color(0xFF16051F), Color(0xFF5A1A8A), Color(0xFF21082E)))
    "BG05" -> Brush.verticalGradient(listOf(Color(0xFF061522), Color(0xFF0B4260), Color(0xFF061522)))
    "BG06" -> Brush.verticalGradient(listOf(Color(0xFF041B15), Color(0xFF087A59), Color(0xFF052019)))
    "BG07" -> Brush.verticalGradient(listOf(Color(0xFF27101E), Color(0xFF9A3C68), Color(0xFF2B1021)))
    "BG08" -> Brush.verticalGradient(listOf(Color(0xFF2A0C08), Color(0xFFE06A2B), Color(0xFF49150C)))
    "BG09" -> Brush.verticalGradient(listOf(Color(0xFF090B2A), Color(0xFF2932A3), Color(0xFF10133B)))
    "BG10" -> Brush.verticalGradient(listOf(Color(0xFF101114), Color(0xFF4A4D55), Color(0xFF17181C)))
    "BG11" -> Brush.verticalGradient(listOf(Color(0xFF04191B), Color(0xFF0D7C81), Color(0xFF062326)))
    "BG12" -> Brush.verticalGradient(listOf(Color(0xFF240408), Color(0xFF8A1024), Color(0xFF31070C)))
    "BG13" -> Brush.verticalGradient(listOf(Color(0xFF2A1704), Color(0xFFC7801C), Color(0xFF3C2208)))
    "BG14" -> Brush.verticalGradient(listOf(Color(0xFF1D1329), Color(0xFF8C6EB4), Color(0xFF2A1A3D)))
    "BG15" -> Brush.verticalGradient(listOf(Color(0xFF03151F), Color(0xFF05718E), Color(0xFF062431)))
    "BG16" -> Brush.verticalGradient(listOf(Color(0xFF07140B), Color(0xFF245D31), Color(0xFF0A1C10)))
    "BG17" -> Brush.verticalGradient(listOf(Color(0xFF23091D), Color(0xFF8A255F), Color(0xFF320D29)))
    "BG18" -> Brush.verticalGradient(listOf(Color(0xFF041128), Color(0xFF135EDB), Color(0xFF071C3B)))
    "BG19" -> Brush.verticalGradient(listOf(Color(0xFF241109), Color(0xFF9B5428), Color(0xFF35190C)))
    "BG20" -> Brush.verticalGradient(listOf(Color(0xFF111214), Color(0xFF303238), Color(0xFF16171A)))
    else -> Brush.verticalGradient(listOf(Color(0xFF14081E), Color(0xFF331059), Color(0xFF14081E)))
}

@Composable
private fun CallerAvatar(photoBytes: ByteArray?) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
        Surface(modifier = Modifier.size(180.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.08f)) {}
        Surface(modifier = Modifier.size(150.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.10f)) {}
        Surface(modifier = Modifier.size(120.dp), shape = CircleShape, color = Color(0xFFF1F1F5)) {
            if (photoBytes != null) {
                val bitmap = remember(photoBytes) { BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)?.asImageBitmap() }
                if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize().clip(CircleShape).padding(3.dp), contentScale = ContentScale.Fit, alignment = Alignment.Center)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color(0xFF693CA3), modifier = Modifier.size(64.dp)) }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color(0xFF693CA3), modifier = Modifier.size(64.dp)) }
            }
        }
    }
}

@Composable
private fun RealSwipeAnswerBar(onDecline: () -> Unit, onAnswer: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val maxPx = with(density) { 120.dp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(offset, spring(dampingRatio = .72f), label = "swipeOffset")
    Box(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .scale(if (offset != 0f) 1.02f else 1f)
            .clip(RoundedCornerShape(38.dp))
            .background(Color.White.copy(alpha = .09f)),
        contentAlignment = Alignment.Center
    ) {
        Text("Decline", color = Color(0xFFFF7770), modifier = Modifier.align(Alignment.CenterStart).clickable { onDecline() }.padding(start = 22.dp, top = 20.dp, bottom = 20.dp))
        Text("Answer", color = Color(0xFF65E28A), modifier = Modifier.align(Alignment.CenterEnd).clickable { onAnswer() }.padding(end = 22.dp, top = 20.dp, bottom = 20.dp))
        Surface(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .size(66.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            val threshold = maxPx * 0.45f // Standard responsive threshold (45%) for easy swiping
                            when {
                                offset > threshold -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAnswer() }
                                offset < -threshold -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDecline() }
                            }
                            offset = 0f
                        },
                        onDragCancel = { offset = 0f },
                        onDrag = { change, drag ->
                            change.consume()
                            offset = (offset + drag.x).coerceIn(-maxPx, maxPx)
                        }
                    )
                },
            shape = CircleShape,
            color = when { animatedOffset > 20f -> Color(0xFF36C567); animatedOffset < -20f -> Color(0xFFE74A43); else -> Color.White },
            shadowElevation = 10.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (animatedOffset < -20f) Icons.Default.CallEnd else Icons.Default.Call, null, tint = if (abs(animatedOffset) > 20f) Color.White else Color.Black, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun CalculatorDialog(onDismiss: () -> Unit) {
    var display by rememberSaveable { mutableStateOf("0") }
    var expression by rememberSaveable { mutableStateOf("") }
    var storedValue by rememberSaveable { mutableStateOf<Double?>(null) }
    var pendingOperation by rememberSaveable { mutableStateOf<String?>(null) }
    var replaceDisplay by rememberSaveable { mutableStateOf(true) }
    val haptics = LocalHapticFeedback.current

    fun cleanNumber(value: Double): String {
        if (!value.isFinite()) return "Error"
        val longValue = value.toLong()
        return if (value == longValue.toDouble()) longValue.toString()
        else java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }

    fun currentValue(): Double = display.toDoubleOrNull() ?: 0.0

    fun applyPending(right: Double): Double {
        val left = storedValue ?: right
        return when (pendingOperation) {
            "+" -> left + right
            "-" -> left - right
            "×" -> left * right
            "÷" -> if (right == 0.0) Double.NaN else left / right
            else -> right
        }
    }

    fun inputDigit(text: String) {
        if (display == "Error" || replaceDisplay) {
            display = if (text == ".") "0." else text
            replaceDisplay = false
            return
        }
        if (text == "." && display.contains('.')) return
        if (display.length < 16) display += text
    }

    fun chooseOperation(op: String) {
        val right = currentValue()
        if (storedValue != null && pendingOperation != null && !replaceDisplay) {
            val result = applyPending(right)
            display = cleanNumber(result)
            storedValue = if (result.isFinite()) result else null
        } else {
            storedValue = right
        }
        pendingOperation = op
        expression = "${cleanNumber(storedValue!!)} $op"
        replaceDisplay = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF141218),
            shadowElevation = 24.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Calculate, null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Calculator", color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.6f)) }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = expression,
                        color = Color(0xFFD0BCFF).copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = display,
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))

                val buttons = listOf(
                    listOf("AC", "⌫", "%", "÷"),
                    listOf("7", "8", "9", "×"),
                    listOf("4", "5", "6", "-"),
                    listOf("1", "2", "3", "+"),
                    listOf("±", "0", ".", "=")
                )

                buttons.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { key ->
                            val isOp = key in setOf("÷", "×", "-", "+", "=")
                            val isAction = key in setOf("AC", "⌫", "%", "±")

                            val containerColor = when {
                                key == "=" -> Color(0xFFD0BCFF)
                                isOp -> Color(0xFF4F378B)
                                isAction -> Color.White.copy(alpha = 0.08f)
                                else -> Color.White.copy(alpha = 0.04f)
                            }
                            val contentColor = when {
                                key == "=" -> Color(0xFF381E72)
                                isOp -> Color(0xFFEADDFF)
                                isAction -> Color(0xFFD0BCFF)
                                else -> Color.White
                            }

                            Surface(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    when (key) {
                                        "AC" -> { display = "0"; expression = ""; storedValue = null; pendingOperation = null; replaceDisplay = true }
                                        "±" -> if (display != "Error") display = cleanNumber(-currentValue())
                                        "%" -> if (display != "Error") { display = cleanNumber(currentValue() / 100.0); replaceDisplay = true }
                                        "⌫" -> if (!replaceDisplay && display != "Error") {
                                            display = if (display.length <= 1 || (display.length == 2 && display.startsWith("-"))) "0" else display.dropLast(1)
                                        }
                                        "+", "-", "×", "÷" -> chooseOperation(key)
                                        "=" -> {
                                            if (pendingOperation != null && storedValue != null) {
                                                val right = currentValue()
                                                val result = applyPending(right)
                                                expression = "${cleanNumber(storedValue!!)} $pendingOperation ${cleanNumber(right)} ="
                                                display = cleanNumber(result)
                                                storedValue = null
                                                pendingOperation = null
                                                replaceDisplay = true
                                            }
                                        }
                                        else -> inputDigit(key)
                                    }
                                },
                                modifier = Modifier.weight(1f).aspectRatio(1.2f),
                                shape = RoundedCornerShape(20.dp),
                                color = containerColor,
                                contentColor = contentColor
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    when (key) {
                                        "⌫" -> Icon(Icons.AutoMirrored.Filled.Backspace, null, modifier = Modifier.size(22.dp))
                                        "+" -> Icon(Icons.Default.Add, null, modifier = Modifier.size(24.dp))
                                        "-" -> Icon(Icons.Default.Remove, null, modifier = Modifier.size(24.dp))
                                        "×" -> Icon(Icons.Default.Close, null, modifier = Modifier.size(22.dp))
                                        else -> Text(text = key, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}


@Composable
private fun MultiCallBanner(heldCall: Call, onSwap: (Call) -> Unit) {
    val number = heldCall.details.handle?.schemeSpecificPart.orEmpty()
    val context = LocalContext.current
    var name by remember(number) { mutableStateOf<String?>(null) }
    LaunchedEffect(number) {
        if (number.isNotBlank() && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            runCatching {
                context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) name = it.getString(0)
                }
            }
        }
    }
    
    Surface(
        onClick = { onSwap(heldCall) },
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Pause, null, tint = Color(0xFFFFCC65), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "On hold: ${name ?: number}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "SWAP",
                color = Color(0xFFD0BCFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun RecordingIndicator(quality: CallRecorder.ExpectedQuality) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording-pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse-alpha"
    )
    
    val tint = Color.Red
    val text = if (quality == CallRecorder.ExpectedQuality.GOOD_BOTH_SIDES) "RECORDING" else "RECORDING (MIC)"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(Color.Red, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Surface(color = tint.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
            Text(
                text = text, 
                color = tint, 
                fontSize = 11.sp, 
                fontWeight = FontWeight.ExtraBold, 
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

private enum class VideoFilterPreset(val label: String, val tint: Color) {
    NONE("Natural", Color.Transparent),
    WARM("Warm", Color(0x33FFB56B)),
    COOL("Cool", Color(0x332E7DFF)),
    SUNSET("Sunset", Color(0x44FF6F61)),
    POOL("Swimming pool", Color(0x3338D6D1)),
    GOLDEN("Golden", Color(0x33FFD54F)),
    ROSE("Rose", Color(0x33F06292)),
    CINEMA("Cinema", Color(0x332B1B4B)),
    MONO("Mono", Color(0x33000000)),
    CUSTOM("Custom image", Color.Transparent)
}

@Composable
private fun VideoLayout(
    videoCall: InCallService.VideoCall?,
    isCameraOn: Boolean,
    isScreenOff: Boolean,
    refreshCounter: Int,
    onSwitchCamera: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleScreen: () -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit,
    speakerOn: Boolean,
    callerLabel: String,
    elapsedLabel: String,
    onBack: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var previewOffsetX: Float by rememberSaveable { mutableFloatStateOf(0f) }
    var previewOffsetY: Float by rememberSaveable { mutableFloatStateOf(0f) }
    var localIsMain by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val filterPrefs = remember { context.getSharedPreferences("video_filter", Context.MODE_PRIVATE) }
    var selectedFilter by rememberSaveable {
        mutableStateOf(runCatching { VideoFilterPreset.valueOf(filterPrefs.getString("preset", "NONE") ?: "NONE") }.getOrDefault(VideoFilterPreset.NONE))
    }
    var showFilterDialog by remember { mutableStateOf(false) }
    var customFilterUri by rememberSaveable { mutableStateOf(filterPrefs.getString("custom_uri", null)) }
    val customFilterLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            customFilterUri = uri.toString()
            selectedFilter = VideoFilterPreset.CUSTOM
            filterPrefs.edit().putString("custom_uri", uri.toString()).putString("preset", VideoFilterPreset.CUSTOM.name).apply()
        }
    }
    val customFilterBitmap = remember(customFilterUri) {
        customFilterUri?.let { value ->
            runCatching { context.contentResolver.openInputStream(Uri.parse(value))?.use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            }
    ) {
        val previewWidth = 112.dp
        val previewHeight = 168.dp
        val edgePadding = 14.dp
        val maxX = with(density) { (maxWidth - previewWidth - edgePadding * 2).toPx().coerceAtLeast(0f) }
        val maxY = with(density) { (maxHeight - previewHeight - 190.dp).toPx().coerceAtLeast(0f) }

        // Main video always fills the complete S24 Ultra call area. Tap it to swap with the floating view.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(localIsMain, isCameraOn, isScreenOff) {
                    detectTapGestures(onTap = { localIsMain = !localIsMain })
                }
        ) {
            if (localIsMain) {
                if (isCameraOn) {
                    VideoSurface(videoCall = videoCall, isRemote = false, refreshCounter = refreshCounter, modifier = Modifier.fillMaxSize())
                } else {
                    CameraOffPlaceholder(Modifier.fillMaxSize(), "Camera off")
                }
            } else {
                if (!isScreenOff) {
                    VideoSurface(videoCall = videoCall, isRemote = true, refreshCounter = refreshCounter, modifier = Modifier.fillMaxSize())
                } else {
                    CameraOffPlaceholder(Modifier.fillMaxSize(), "Remote video hidden")
                }
            }
        }

        if (selectedFilter == VideoFilterPreset.CUSTOM && customFilterBitmap != null) {
            Image(
                bitmap = customFilterBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.18f }
            )
        } else if (selectedFilter.tint != Color.Transparent) {
            Box(Modifier.fillMaxSize().background(selectedFilter.tint))
        }

        // Subtle readable overlays; tap anywhere to show/hide controls.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.36f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.62f)
                            )
                        )
                    )
            )
        }

        // Floating secondary video. Tap to exchange local and remote views; drag to reposition.
        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (with(density) { edgePadding.toPx() } + previewOffsetX).roundToInt(),
                        (with(density) { 76.dp.toPx() } + previewOffsetY).roundToInt()
                    )
                }
                .size(previewWidth, previewHeight)
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(maxX, maxY, localIsMain) {
                    detectDragGestures(
                        onDrag = { _, dragAmount ->
                            previewOffsetX = (previewOffsetX + dragAmount.x).coerceIn(0f, maxX)
                            previewOffsetY = (previewOffsetY + dragAmount.y).coerceIn(0f, maxY)
                        }
                    )
                }
                .clickable { localIsMain = !localIsMain },
            color = Color.Black,
            shadowElevation = 12.dp,
            tonalElevation = 2.dp
        ) {
            if (localIsMain) {
                if (!isScreenOff) VideoSurface(videoCall = videoCall, isRemote = true, refreshCounter = refreshCounter, modifier = Modifier.fillMaxSize())
                else CameraOffPlaceholder(Modifier.fillMaxSize(), "Remote off")
            } else {
                if (isCameraOn) VideoSurface(videoCall = videoCall, isRemote = false, refreshCounter = refreshCounter, modifier = Modifier.fillMaxSize())
                else CameraOffPlaceholder(Modifier.fillMaxSize(), "Camera off")
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PremiumCircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(callerLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(elapsedLabel, color = Color.White.copy(alpha = .76f), fontSize = 12.sp)
                }
                PremiumCircleButton(Icons.Filled.AutoAwesome, "Video filters") { showFilterDialog = true }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(220)) { it / 3 } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(160)) { it / 3 } + fadeOut(tween(120))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                color = Color(0xCC141219),
                shape = RoundedCornerShape(26.dp),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Three-column standard video-call controls.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        VideoControlButton(
                            icon = if (speakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            label = "Speaker",
                            selected = speakerOn,
                            onClick = onToggleSpeaker
                        )
                        VideoControlButton(
                            icon = if (isCameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                            label = "Camera",
                            selected = isCameraOn,
                            onClick = onToggleCamera
                        )
                        VideoControlButton(
                            icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = "Mute",
                            selected = isMuted,
                            onClick = onToggleMute
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VideoControlButton(
                            icon = Icons.Filled.FlipCameraAndroid,
                            label = "Switch",
                            onClick = onSwitchCamera
                        )
                        VideoControlButton(
                            icon = if (isScreenOff) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            label = "Remote",
                            selected = isScreenOff,
                            onClick = onToggleScreen
                        )
                        VideoEndCallButton(onClick = onEndCall)
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Video filter") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                    items(VideoFilterPreset.entries) { preset ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (preset == VideoFilterPreset.CUSTOM) {
                                    customFilterLauncher.launch(arrayOf("image/*"))
                                } else {
                                    selectedFilter = preset
                                    filterPrefs.edit().putString("preset", preset.name).apply()
                                }
                                showFilterDialog = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selectedFilter == preset) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(if (preset.tint == Color.Transparent) Color(0xFF76717C) else preset.tint.copy(alpha = 1f)))
                                Spacer(Modifier.width(12.dp))
                                Text(preset.label, modifier = Modifier.weight(1f))
                                if (selectedFilter == preset) Icon(Icons.Filled.Check, null)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilterDialog = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun CameraOffPlaceholder(modifier: Modifier = Modifier, text: String) {
    Box(modifier.background(Color(0xFF111116)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.VideocamOff, null, tint = Color.White.copy(alpha = .72f), modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(text, color = Color.White.copy(alpha = .78f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun PremiumCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .94f else 1f, spring(dampingRatio = .72f, stiffness = 650f), label = "premiumCircle")
    Surface(
        modifier = Modifier.size(44.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = CircleShape, color = Color.Black.copy(alpha = .42f)
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(23.dp)) } }
}

@Composable
private fun VideoControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = .72f, stiffness = 620f), label = "videoControlScale")
    Column(
        modifier = Modifier
            .width(82.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.16f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (selected) Color.Black else Color.White, modifier = Modifier.size(25.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun VideoEndCallButton(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = .68f, stiffness = 720f), label = "videoEndScale")
    Column(
        modifier = Modifier
            .width(82.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(modifier = Modifier.size(58.dp), shape = CircleShape, color = Color(0xFFE9433F)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CallEnd, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text("End", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun VideoSurface(modifier: Modifier = Modifier, videoCall: InCallService.VideoCall?, isRemote: Boolean, refreshCounter: Int = 0) {
    var activeSurface by remember(videoCall, isRemote) { mutableStateOf<Surface?>(null) }

    fun attach(surface: Surface?) {
        runCatching {
            if (isRemote) videoCall?.setDisplaySurface(surface)
            else videoCall?.setPreviewSurface(surface)
        }
    }

    LaunchedEffect(videoCall, activeSurface, refreshCounter, isRemote) {
        attach(activeSurface)
    }

    DisposableEffect(videoCall, isRemote) {
        onDispose {
            attach(null)
            activeSurface?.release()
            activeSurface = null
        }
    }

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        applyVideoCenterCrop(this@apply)
                        val newSurface = Surface(st)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            runCatching { newSurface.setFrameRate(30f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) }
                        }
                        attach(null)
                        activeSurface?.release()
                        activeSurface = newSurface
                        attach(newSurface)
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        applyVideoCenterCrop(this@apply)
                        attach(activeSurface)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        attach(null)
                        activeSurface?.release()
                        activeSurface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
        update = { view ->
            applyVideoCenterCrop(view)
            if (view.isAvailable && activeSurface == null) {
                view.surfaceTexture?.let { st ->
                    val newSurface = Surface(st)
                    activeSurface = newSurface
                    attach(newSurface)
                }
            } else attach(activeSurface)
        },
        modifier = modifier
    )
}

private fun applyVideoCenterCrop(view: TextureView) {
    val width = view.width.toFloat()
    val height = view.height.toFloat()
    if (width <= 0f || height <= 0f) return
    val sourceAspect = 9f / 16f
    val viewAspect = width / height
    val matrix = Matrix()
    if (sourceAspect > viewAspect) {
        matrix.setScale(sourceAspect / viewAspect, 1f, width / 2f, height / 2f)
    } else {
        matrix.setScale(1f, viewAspect / sourceAspect, width / 2f, height / 2f)
    }
    view.setTransform(matrix)
}

@Composable
private fun FeatureButton(icon: ImageVector, label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "buttonScale")
    
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
        FilledIconButton(
            onClick = { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick() 
            },
            enabled = enabled, 
            interactionSource = interaction, 
            modifier = Modifier.size(56.dp).scale(scale),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (selected) Color.White else Color.White.copy(alpha = 0.10f),
                contentColor = if (selected) Color(0xFF24222B) else Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.05f), 
                disabledContentColor = Color.White.copy(alpha = 0.28f)
            )
        ) { Icon(icon, label, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label, 
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f), 
            style = MaterialTheme.typography.labelMedium, 
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IvrKey(digit: Char, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.6f, stiffness = 800f), label = "ivr-key-scale")
    
    Surface(
        onClick = onClick,
        modifier = Modifier.size(68.dp).scale(scale),
        shape = CircleShape,
        color = if (pressed) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
        contentColor = Color.White,
        interactionSource = interaction
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit.toString(), fontSize = 30.sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
private fun EndCallButton(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .92f else 1f, spring(dampingRatio = .68f, stiffness = 700f), label = "callScale")
    FilledIconButton(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        interactionSource = interaction, modifier = Modifier.size(76.dp).scale(scale),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53E36))
    ) { Icon(Icons.Filled.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(34.dp)) }
}

private suspend fun lookupCallerData(context: Context, number: String): CallerData = withContext(Dispatchers.IO) {
    if (number.isBlank() || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return@withContext CallerData(null, null)
    runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use CallerData(null, null)
            val name = cursor.getString(0)
            val photoUri = cursor.getString(1)?.let(Uri::parse)
            val bytes = photoUri?.let { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } }
            CallerData(name, bytes)
        } ?: CallerData(null, null)
    }.getOrDefault(CallerData(null, null))
}

private fun buildSimDisplayLabel(context: Context, slotIndex: Int): String {
    val fallback = "SIM ${slotIndex + 1}"
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return fallback
    val manager = context.getSystemService(SubscriptionManager::class.java) ?: return fallback
    val info = runCatching {
        manager.activeSubscriptionInfoList.orEmpty().firstOrNull { it.simSlotIndex == slotIndex }
    }.getOrNull() ?: return fallback
    val number = runCatching { info.number.orEmpty().trim() }.getOrDefault("")
    val carrier = info.carrierName?.toString()?.trim().orEmpty()
    return when {
        number.isNotBlank() -> "$fallback • $number"
        carrier.isNotBlank() -> "$fallback • $carrier"
        else -> fallback
    }
}

private fun stateLabel(state: Int): String = when (state) {
    Call.STATE_RINGING -> "Incoming call"
    Call.STATE_DIALING -> "Calling…"
    Call.STATE_CONNECTING -> "Connecting…"
    Call.STATE_ACTIVE -> "In call"
    Call.STATE_HOLDING -> "On hold"
    Call.STATE_DISCONNECTED -> "Call ended"
    else -> "Connecting…"
}

@Composable
private fun ThemeableCallLayout(
    themeId: String,
    callerName: String,
    callNumber: String,
    simLabel: String,
    isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
    onSilent: () -> Unit,
    onMessage: () -> Unit
) {
    when (themeId) {
        "Apple" -> IPhoneCallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
        "Google", "Motorola", "Nokia", "Sony", "ASUS", "Lenovo" -> MaterialYouCallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
        "Xiaomi", "Redmi", "POCO" -> MIUICallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
        "Nothing" -> NothingCallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
        "OPPO", "vivo", "realme", "OnePlus" -> ColorOSCallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
        "HONOR", "Huawei" -> EMUICallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
        "TECNO", "Infinix", "nubia", "ZTE", "Meizu" -> HiOSCallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
        "Button" -> ButtonCallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onMessage)
        "Gesture" -> GestureCallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
        "Circle" -> CircleCallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
        "Premium" -> PremiumCallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
        "Minimal" -> MinimalCallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
        else -> ClassicCallTheme(callerName, callNumber, simLabel, isSpam, onAnswer, onDecline, onScreen, onSilent, onMessage)
    }
}

@Composable
private fun IPhoneCallTheme(
    @Suppress("UNUSED_PARAMETER") callerName: String,
    @Suppress("UNUSED_PARAMETER") callNumber: String,
    @Suppress("UNUSED_PARAMETER") simLabel: String,
    @Suppress("UNUSED_PARAMETER") isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
    onSilent: () -> Unit,
    onMessage: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(bottom = 60.dp), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(30.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onSilent, modifier = Modifier.size(50.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) { Icon(Icons.Outlined.NotificationsOff, null, tint = Color.White) }
                    Text("Remind Me", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onScreen, modifier = Modifier.size(50.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) { Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White) }
                    Text("AI Screen", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onMessage, modifier = Modifier.size(50.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) { Icon(Icons.AutoMirrored.Outlined.Message, null, tint = Color.White) }
                    Text("Message", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Surface(modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 30.dp), shape = RoundedCornerShape(40.dp), color = Color.White.copy(alpha = 0.1f)) {
                RealSwipeAnswerBar(onDecline = onDecline, onAnswer = onAnswer)
            }
        }
    }
}

@Composable
private fun MaterialYouCallTheme(
    @Suppress("UNUSED_PARAMETER") callerName: String,
    @Suppress("UNUSED_PARAMETER") callNumber: String,
    @Suppress("UNUSED_PARAMETER") simLabel: String,
    @Suppress("UNUSED_PARAMETER") isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onScreen: () -> Unit,
    onSilent: () -> Unit,
    onMessage: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(bottom = 70.dp), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onSilent) { Icon(Icons.AutoMirrored.Outlined.VolumeOff, null, tint = Color.White.copy(alpha = 0.6f)) }
                IconButton(onClick = onMessage) { Icon(Icons.AutoMirrored.Outlined.Message, null, tint = Color.White.copy(alpha = 0.6f)) }
            }
            Surface(modifier = Modifier.size(100.dp), shape = CircleShape, color = Color(0xFF24C98A), shadowElevation = 8.dp) {
                val haptic = LocalHapticFeedback.current
                var dragOffsetY by remember { mutableFloatStateOf(0f) }
                Box(Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { dragOffsetY = 0f },
                        onDragCancel = { dragOffsetY = 0f },
                        onDrag = { change, dragAmount -> 
                            change.consume()
                            dragOffsetY += dragAmount.y
                            if (dragOffsetY < -140f) { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragOffsetY = 0f
                                onAnswer() 
                            }
                            if (dragOffsetY > 140f) { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragOffsetY = 0f
                                onDecline() 
                            }
                        }
                    )
                }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Call, null, tint = Color.Black, modifier = Modifier.size(40.dp))
                }
            }
            Text("Swipe up to answer", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun MIUICallTheme(
    @Suppress("UNUSED_PARAMETER") callerName: String,
    @Suppress("UNUSED_PARAMETER") callNumber: String,
    @Suppress("UNUSED_PARAMETER") simLabel: String,
    @Suppress("UNUSED_PARAMETER") isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
    onSilent: () -> Unit,
    onMessage: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(bottom = 60.dp), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(25.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onSilent) { Icon(Icons.Outlined.NotificationsOff, null, tint = Color.White.copy(alpha = 0.6f)) }
                IconButton(onClick = onMessage) { Icon(Icons.AutoMirrored.Outlined.Message, null, tint = Color.White.copy(alpha = 0.6f)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                FloatingActionButton(onClick = onDecline, containerColor = Color(0xFFE53E36), shape = CircleShape, modifier = Modifier.size(72.dp)) { Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = onScreen) { Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White.copy(alpha = 0.5f)) }
                FloatingActionButton(onClick = onAnswer, containerColor = Color(0xFF24C98A), shape = CircleShape, modifier = Modifier.size(72.dp)) { Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
        }
    }
}

@Composable
private fun NothingCallTheme(
    @Suppress("UNUSED_PARAMETER") callerName: String,
    @Suppress("UNUSED_PARAMETER") callNumber: String,
    @Suppress("UNUSED_PARAMETER") simLabel: String,
    @Suppress("UNUSED_PARAMETER") isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onScreen: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onSilent: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onMessage: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(30.dp)) {
            Text("NOTHING OS", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 50.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedIconButton(onClick = onDecline, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White), modifier = Modifier.size(64.dp)) { Icon(Icons.Default.Close, null, tint = Color.White) }
                OutlinedIconButton(onClick = onAnswer, border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red), modifier = Modifier.size(64.dp)) { Icon(Icons.Default.Check, null, tint = Color.Red) }
            }
        }
    }
}

@Composable
private fun ColorOSCallTheme(
    @Suppress("UNUSED_PARAMETER") callerName: String,
    @Suppress("UNUSED_PARAMETER") callNumber: String,
    @Suppress("UNUSED_PARAMETER") simLabel: String,
    @Suppress("UNUSED_PARAMETER") isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
    onSilent: () -> Unit,
    onMessage: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(bottom = 60.dp), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MediumActionButton(Icons.Outlined.NotificationsOff, "Silent", Color.White.copy(alpha = 0.1f), onSilent)
                MediumActionButton(Icons.Outlined.AutoAwesome, "Screen", Color.White.copy(alpha = 0.1f), onScreen)
                MediumActionButton(Icons.AutoMirrored.Outlined.Message, "Message", Color.White.copy(alpha = 0.1f), onMessage)
            }
            Surface(modifier = Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 20.dp), shape = RoundedCornerShape(32.dp), color = Color.White.copy(alpha = 0.05f)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        LargeActionButton(Icons.Default.CallEnd, "Reject", Color(0xFFE53E36), onDecline)
                        LargeActionButton(Icons.Default.Call, "Accept", Color(0xFF24C98A), onAnswer)
                    }
                }
            }
        }
    }
}

@Composable
private fun EMUICallTheme(
    callerName: String,
    callNumber: String,
    simLabel: String,
    isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onScreen: () -> Unit,
    onSilent: () -> Unit,
    onMessage: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MediumActionButton(Icons.Outlined.NotificationsOff, "Silent", Color.White.copy(alpha = 0.1f), onSilent)
                MediumActionButton(Icons.AutoMirrored.Outlined.Message, "Message", Color.White.copy(alpha = 0.1f), onMessage)
            }
            Surface(modifier = Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 20.dp), shape = RoundedCornerShape(45.dp), color = Color(0xFF2979FF)) {
                RealSwipeAnswerBar(onDecline = onDecline, onAnswer = onAnswer)
            }
        }
    }
}

@Composable
private fun HiOSCallTheme(callerName: String, callNumber: String, simLabel: String, isSpam: Boolean, onAnswer: () -> Unit, onDecline: () -> Unit, onScreen: () -> Unit, onSilent: () -> Unit, onMessage: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(30.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 50.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onSilent) { Icon(Icons.Outlined.NotificationsOff, null, tint = Color.White.copy(alpha = 0.5f)) }
                IconButton(onClick = onScreen) { Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White.copy(alpha = 0.5f)) }
                IconButton(onClick = onMessage) { Icon(Icons.AutoMirrored.Outlined.Message, null, tint = Color.White.copy(alpha = 0.5f)) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(1f, 1.2f, infiniteRepeatable(tween(1000), RepeatMode.Reverse))
                Surface(modifier = Modifier.size(80.dp).graphicsLayer { scaleX = scale; scaleY = scale }, shape = CircleShape, color = Color(0xFF00C853)) {
                    IconButton(onClick = onAnswer) { Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                }
                Spacer(Modifier.height(40.dp))
                IconButton(onClick = onDecline, modifier = Modifier.size(56.dp).background(Color(0xFFE53E36), CircleShape)) { Icon(Icons.Default.CallEnd, null, tint = Color.White) }
            }
        }
    }
}
@Composable
private fun ClassicCallTheme(
    callerName: String,
    callNumber: String,
    simLabel: String,
    isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
    onSilent: () -> Unit,
    onMessage: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSilent) { Icon(Icons.Outlined.NotificationsOff, null, tint = Color.White.copy(alpha = 0.6f)) }
            OutlinedButton(
                onClick = onScreen,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Screen Call", fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onMessage) { Icon(Icons.AutoMirrored.Outlined.Message, null, tint = Color.White.copy(alpha = 0.6f)) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 55.dp)
        ) {
            RealSwipeAnswerBar(onDecline = onDecline, onAnswer = onAnswer)
        }
    }
}

@Composable
private fun ButtonCallTheme(
    callerName: String,
    callNumber: String,
    simLabel: String,
    isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
    onMessage: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LargeActionButton(Icons.Default.Call, "Answer", Color(0xFF24C98A), onAnswer)
            LargeActionButton(Icons.Default.CallEnd, "Decline", Color(0xFFE53E36), onDecline)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MediumActionButton(Icons.Outlined.AutoAwesome, "Screen", Color.White.copy(alpha = 0.2f), onScreen)
            MediumActionButton(Icons.AutoMirrored.Filled.Message, "Message", Color.White.copy(alpha = 0.2f), onMessage)
        }
    }
}

@Composable
private fun LargeActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = color,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(84.dp)
        ) { Icon(icon, null, Modifier.size(36.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MediumActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = color, contentColor = Color.White),
            modifier = Modifier.size(56.dp)
        ) { Icon(icon, null) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun GestureCallTheme(
    callerName: String,
    callNumber: String,
    simLabel: String,
    isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
    onSilent: () -> Unit,
    onMessage: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    },
                    onDragEnd = {
                        val threshold = 150f
                        when {
                            offset.y < -threshold -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAnswer() }
                            offset.y > threshold -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDecline() }
                            offset.x < -threshold -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMessage() }
                            offset.x > threshold -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSilent() }
                        }
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    }
                )
            }
            .padding(bottom = 80.dp), 
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.KeyboardArrowUp, null, tint = Color(0xFF24C98A), modifier = Modifier.size(40.dp))
            Text("Swipe Up to Answer", color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color(0xFF2979FF))
                    Text("Message", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Box(Modifier.size(80.dp).background(Color.White.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.TouchApp, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Yellow)
                    Text("Silent", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(40.dp))
            Text("Swipe Down to Reject", color = Color.White.copy(alpha = 0.7f))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Color(0xFFE53E36), modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun CircleCallTheme(
    callerName: String,
    callNumber: String,
    simLabel: String,
    isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
    onSilent: () -> Unit,
    onMessage: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.padding(bottom = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSilent) { Icon(Icons.Outlined.NotificationsOff, null, tint = Color.White.copy(alpha = 0.6f)) }
                
                Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val rippleScale by infiniteTransition.animateFloat(1f, 1.5f, infiniteRepeatable(tween(1500), RepeatMode.Restart))
                    val rippleAlpha by infiniteTransition.animateFloat(0.5f, 0f, infiniteRepeatable(tween(1500), RepeatMode.Restart))

                    Box(Modifier.size(100.dp).graphicsLayer { scaleX = rippleScale; scaleY = rippleScale; alpha = rippleAlpha }.background(Color.White.copy(alpha = 0.2f), CircleShape))
                    
                    Surface(modifier = Modifier.size(100.dp), shape = CircleShape, color = Color.White, shadowElevation = 8.dp) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Call, null, tint = Color.Black, modifier = Modifier.size(40.dp)) }
                    }
                    
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.align(Alignment.CenterStart).size(64.dp).background(Color(0xFFE53E36).copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                            IconButton(onClick = onDecline) { Icon(Icons.Default.CallEnd, null, tint = Color(0xFFE53E36)) }
                        }
                        Box(Modifier.align(Alignment.CenterEnd).size(64.dp).background(Color(0xFF24C98A).copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                            IconButton(onClick = onAnswer) { Icon(Icons.Default.Call, null, tint = Color(0xFF24C98A)) }
                        }
                    }
                }

                IconButton(onClick = onMessage) { Icon(Icons.AutoMirrored.Outlined.Message, null, tint = Color.White.copy(alpha = 0.6f)) }
            }
            TextButton(onClick = onScreen) { Text("AI SCREENING", color = Color(0xFF24C98A), fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        }
    }
}

@Composable
private fun PremiumCallTheme(
    callerName: String,
    callNumber: String,
    simLabel: String,
    isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
    onSilent: () -> Unit,
    onMessage: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Row(Modifier.padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            IconButton(onClick = onSilent) { Icon(Icons.AutoMirrored.Outlined.VolumeOff, null, tint = Color.White.copy(alpha = 0.5f)) }
            IconButton(onClick = onMessage) { Icon(Icons.AutoMirrored.Outlined.Message, null, tint = Color.White.copy(alpha = 0.5f)) }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = Color.Black.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                PremiumCallButton(Icons.Default.CallEnd, Color(0xFFE53E36), onDecline)
                PremiumCallButton(Icons.Outlined.AutoAwesome, Color.White.copy(alpha = 0.1f), onScreen)
                PremiumCallButton(Icons.Default.Call, Color(0xFF24C98A), onAnswer)
            }
        }
    }
}

@Composable
private fun PremiumCallButton(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(68.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = color,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun MinimalCallTheme(
    callerName: String,
    callNumber: String,
    simLabel: String,
    isSpam: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onScreen: () -> Unit,
    onSilent: () -> Unit,
    onMessage: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Text("SILENT", Modifier.clickable { onSilent() }, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
            Text("MESSAGE", Modifier.clickable { onMessage() }, color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
        }
        TextButton(onClick = onScreen) { Text("SCREEN CALL", color = Color.White.copy(alpha = 0.6f), letterSpacing = 2.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text(
                "DECLINE", 
                Modifier.clickable { onDecline() }.padding(10.dp),
                color = Color(0xFFE53E36), 
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                "ANSWER", 
                Modifier.clickable { onAnswer() }.padding(10.dp),
                color = Color(0xFF24C98A), 
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

private fun formatElapsed(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
private fun hasRecordPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
private fun normalized(number: String) = number.filter(Char::isDigit).takeLast(10)

private fun shouldAutoRecord(context: Context, number: String, incoming: Boolean, knownContact: Boolean): Boolean {
    val prefs = context.getSharedPreferences("recording_settings", Context.MODE_PRIVATE)
    if (prefs.getString("recording_mode", "AUTO") != "AUTO") return false
    val normalizedNumber = normalized(number)
    val excluded = prefs.getStringSet("excluded_numbers", emptySet()).orEmpty()
    if (excluded.any { normalized(it) == normalizedNumber }) return false
    return when (prefs.getString("recording_scope", "ALL")) {
        "INCOMING" -> incoming
        "OUTGOING" -> !incoming
        "UNKNOWN" -> !knownContact
        "SELECTED" -> prefs.getStringSet("selected_record_numbers", emptySet()).orEmpty().any { normalized(it) == normalizedNumber }
        else -> true
    }
}
