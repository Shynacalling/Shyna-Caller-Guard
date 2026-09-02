package com.example.callruleblocker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import com.example.callruleblocker.AppCallActivity
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.callruleblocker.call.CallStateController
import com.example.callruleblocker.call.GlobalCallState
import com.example.callruleblocker.call.MainCallType
import com.example.callruleblocker.call.ShynaHandshake
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

private enum class OfflineMode { HOME, BLUETOOTH, WIFI_DIRECT, RADIO, NEARBY_USERS, USER_DETAILS, CHAT, CALL }

data class OfflineUser(
    val id: String,
    val name: String,
    val status: String,
    val transport: List<String>,
    val handshake: ShynaHandshake? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineCallScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(OfflineMode.HOME) }
    var selectedUser by remember { mutableStateOf<OfflineUser?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    val nearbyUsers = remember { mutableStateListOf<OfflineUser>() }
    val db = remember { FirebaseFirestore.getInstance() }
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    // REAL USER DISCOVERY FROM FIRESTORE (No dummy users)
    LaunchedEffect(mode) {
        if (mode == OfflineMode.BLUETOOTH || mode == OfflineMode.WIFI_DIRECT || mode == OfflineMode.NEARBY_USERS) {
            nearbyUsers.clear()
            db.collection("users").addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val realUsers = snapshot.documents.mapNotNull { d ->
                    val uid = d.id
                    if (uid == currentUid) return@mapNotNull null
                    val name = d.getString("name") ?: d.getString("userId") ?: "User"
                    val isOnline = d.getBoolean("isOnline") ?: false
                    val statusStr = if (isOnline) "Live Online" else "Nearby"
                    val transportList = when(mode) {
                        OfflineMode.BLUETOOTH -> listOf("Bluetooth P2P")
                        OfflineMode.WIFI_DIRECT -> listOf("Wi-Fi Direct")
                        else -> listOf("Bluetooth", "Wi-Fi Direct")
                    }
                    OfflineUser(
                        id = uid,
                        name = name,
                        status = statusStr,
                        transport = transportList
                    )
                }
                nearbyUsers.clear()
                nearbyUsers.addAll(realUsers)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(getModeTitle(mode), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (mode == OfflineMode.HOME) onBack()
                        else mode = OfflineMode.HOME
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, null, tint = ShynaDesign.colors.TextPrimary) }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(ShynaDesign.colors.HeaderBg)
                        ) {
                            val secondaryFeatures = CallStateController.getSecondaryFeatures()
                            secondaryFeatures.forEach { feature ->
                                when (feature) {
                                    MainCallType.PHONE_DIALER -> {
                                        DropdownMenuItem(
                                            text = { Text("Phone Dialer", color = ShynaDesign.colors.TextPrimary) },
                                            leadingIcon = { Icon(Icons.Default.Call, null, tint = ShynaDesign.colors.BrandGreen) },
                                            onClick = { 
                                                menuExpanded = false
                                                CallStateController.setPrimaryFeature(MainCallType.PHONE_DIALER)
                                                onBack() 
                                            }
                                        )
                                    }
                                    MainCallType.SHYNA_LINK -> {
                                        DropdownMenuItem(
                                            text = { Text("Shyna Link", color = ShynaDesign.colors.TextPrimary) },
                                            leadingIcon = { Icon(Icons.Default.WorkspacePremium, null, tint = ShynaDesign.colors.BrandGreen) },
                                            onClick = { 
                                                menuExpanded = false
                                                CallStateController.setPrimaryFeature(MainCallType.SHYNA_LINK)
                                                onBack() 
                                            }
                                        )
                                    }
                                    MainCallType.OFFLINE_CALL -> {}
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (mode) {
                OfflineMode.HOME -> OfflineHomeUI { mode = it }
                OfflineMode.BLUETOOTH -> ActiveUsersScreen("Bluetooth", nearbyUsers) { selectedUser = it; mode = OfflineMode.USER_DETAILS }
                OfflineMode.WIFI_DIRECT -> ActiveUsersScreen("Wi-Fi Direct", nearbyUsers) { selectedUser = it; mode = OfflineMode.USER_DETAILS }
                OfflineMode.RADIO -> RadioScreenUI()
                OfflineMode.NEARBY_USERS -> ActiveUsersScreen("All Nearby Users", nearbyUsers) { selectedUser = it; mode = OfflineMode.USER_DETAILS }
                OfflineMode.USER_DETAILS -> UserSelectionScreen(
                    user = selectedUser!!, 
                    onChat = { mode = OfflineMode.CHAT }, 
                    onVoiceCall = { mode = OfflineMode.CALL }, 
                    onVideoCall = { mode = OfflineMode.CALL }, 
                    onCancel = { mode = OfflineMode.HOME }
                )
                OfflineMode.CHAT -> OfflineChatUI(selectedUser!!)
                OfflineMode.CALL -> OfflineCallingUI(selectedUser!!) { mode = OfflineMode.HOME }
            }
        }
    }
}

@Composable
private fun OfflineHomeUI(onModeSelect: (OfflineMode) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(16.dp)).background(ShynaDesign.colors.HeaderBg).padding(16.dp)) {
            Column {
                Text("Offline Call", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                Text("Connect without internet or SIM network", fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
            }
        }
        
        OfflineModeCard("Bluetooth", "Connect nearby SHYNA users", Icons.Default.Bluetooth, Color(0xFF2979FF)) { onModeSelect(OfflineMode.BLUETOOTH) }
        OfflineModeCard("Wi-Fi Direct", "Direct offline connection", Icons.Default.Wifi, Color(0xFF00C853)) { onModeSelect(OfflineMode.WIFI_DIRECT) }
        OfflineModeCard("Radio", "Connect using compatible external radio hardware", Icons.Default.Radio, Color(0xFFFFAB00)) { onModeSelect(OfflineMode.RADIO) }
        OfflineModeCard("All Nearby Users", "View users discovered from all offline modes", Icons.Default.Groups, Color(0xFF7C4DFF)) { onModeSelect(OfflineMode.NEARBY_USERS) }
    }
}

@Composable
private fun OfflineModeCard(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
            }
        }
    }
}

@Composable
private fun ActiveUsersScreen(transport: String, users: List<OfflineUser>, onUserSelect: (OfflineUser) -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(users, search) { users.filter { it.name.contains(search, true) } }
    var isScanning by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Discovery effect
        delay(3000)
        isScanning = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(transport, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = ShynaDesign.colors.TextPrimary, modifier = Modifier.weight(1f))
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ShynaDesign.colors.BrandGreen)
            }
        }
        Spacer(Modifier.height(8.dp))
        
        Text(
            if (isScanning) "Searching for nearby SHYNA users..." else "${filtered.size} users found via $transport", 
            fontSize = 14.sp, 
            color = if(isScanning) Color.Gray else ShynaDesign.colors.BrandGreen
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text("Search name or Shyna ID", fontSize = 15.sp, color = ShynaDesign.colors.TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ShynaDesign.colors.BrandGreen,
                unfocusedBorderColor = ShynaDesign.colors.DividerColor,
                focusedTextColor = ShynaDesign.colors.TextPrimary,
                unfocusedTextColor = ShynaDesign.colors.TextPrimary
            )
        )
        Spacer(Modifier.height(24.dp))
        Text("Active Users", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        
        if (!isScanning && filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Radar, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(0.3f))
                    Text("No users found nearby", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { user ->
                    OfflineUserRow(user) { onUserSelect(user) }
                }
            }
        }
    }
}

@Composable
private fun OfflineUserRow(user: OfflineUser, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(ShynaDesign.colors.DividerColor, CircleShape), contentAlignment = Alignment.Center) {
                Text(user.name.take(1), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 20.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ShynaDesign.colors.TextPrimary)
                Text("${user.status} • ${user.transport.joinToString(", ")}", fontSize = 13.sp, color = ShynaDesign.colors.TextSecondary)
            }
        }
    }
}

@Composable
private fun RadioScreenUI() {
    val mContext = LocalContext.current
    var selectedChannel by remember { mutableIntStateOf(1) }
    var isTransmitting by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    var squelch by remember { mutableFloatStateOf(0.5f) }
    
    val scope = rememberCoroutineScope()
    var pttJob by remember { mutableStateOf<Job?>(null) }

    fun startPttAudio() {
        pttJob?.cancel()
        pttJob = scope.launch(Dispatchers.IO) {
            var record: AudioRecord? = null
            var track: AudioTrack? = null
            try {
                val sampleRate = 16000
                val minIn = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val minOut = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val bufSize = Math.max(minIn, minOut) * 2

                if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(mContext, "Microphone permission required for Walkie-Talkie", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                record = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
                track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                record.startRecording()
                track.play()

                val buffer = ByteArray(bufSize)
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        track.write(buffer, 0, read)
                    }
                }
            } catch (e: Exception) {
                Log.e("RadioPTT", "Audio error: ${e.message}")
            } finally {
                runCatching {
                    record?.stop()
                    record?.release()
                    track?.stop()
                    track?.release()
                }
            }
        }
    }

    fun stopPttAudio() {
        pttJob?.cancel()
        pttJob = null
    }
    
    val channels = remember {
        listOf(
            1 to "462.5625 MHz (CH 1)",
            2 to "462.5875 MHz (CH 2)",
            3 to "462.6125 MHz (CH 3)",
            4 to "462.6375 MHz (CH 4)",
            5 to "462.6625 MHz (CH 5)",
            6 to "462.6875 MHz (CH 6)",
            7 to "462.7125 MHz (CH 7)",
            8 to "467.5625 MHz (CH 8)",
            9 to "467.5875 MHz (CH 9)",
            10 to "467.6125 MHz (CH 10)"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E261D),
            border = BorderStroke(2.dp, ShynaDesign.colors.BrandGreen)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Radio, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("RADIO WALKIE-TALKIE", color = ShynaDesign.colors.BrandGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = channels.find { it.first == selectedChannel }?.second ?: "462.5625 MHz",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if(isTransmitting) Color.Red else ShynaDesign.colors.BrandGreen, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isTransmitting) "TRANSMITTING (TALKING)..." else "LISTENING (READY)",
                        color = if (isTransmitting) Color.Red else ShynaDesign.colors.BrandGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("Channel Selector", color = ShynaDesign.colors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            channels.forEach { (chNum, _) ->
                FilterChip(
                    selected = selectedChannel == chNum,
                    onClick = { selectedChannel = chNum },
                    label = { Text("CH $chNum", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ShynaDesign.colors.BrandGreen,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Surface(
            modifier = Modifier
                .size(160.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isTransmitting = true
                            startPttAudio()
                            try {
                                awaitRelease()
                            } finally {
                                isTransmitting = false
                                stopPttAudio()
                            }
                        }
                    )
                },
            shape = CircleShape,
            color = if (isTransmitting) Color(0xFFE53935) else ShynaDesign.colors.BrandGreen,
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isTransmitting) Icons.Default.GraphicEq else Icons.Default.Mic,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isTransmitting) "TALKING" else "PUSH TO TALK",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            if (isTransmitting) "Release button to stop transmitting" else "Press & hold button to talk on Channel $selectedChannel",
            color = ShynaDesign.colors.TextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.weight(1f))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ShynaDesign.colors.SurfaceBg
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Volume", color = ShynaDesign.colors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(70.dp))
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = ShynaDesign.colors.BrandGreen, activeTrackColor = ShynaDesign.colors.BrandGreen)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Squelch", color = ShynaDesign.colors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(70.dp))
                    Slider(
                        value = squelch,
                        onValueChange = { squelch = it },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = ShynaDesign.colors.BrandGreen, activeTrackColor = ShynaDesign.colors.BrandGreen)
                    )
                }
            }
        }
    }
}

@Composable
private fun UserSelectionScreen(
    user: OfflineUser, 
    onChat: () -> Unit, 
    onVoiceCall: () -> Unit, 
    onVideoCall: () -> Unit, 
    onCancel: () -> Unit
) {
    val mContext = LocalContext.current
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    Column(
        Modifier.fillMaxSize().padding(24.dp), 
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            color = ShynaDesign.colors.BrandGreen.copy(alpha = 0.15f),
            border = BorderStroke(3.dp, ShynaDesign.colors.BrandGreen)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(user.name.take(1).uppercase(), fontSize = 42.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(user.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
        Text("Available via ${user.transport.first()}", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Medium)
        Text("Live SHYNA User", color = ShynaDesign.colors.TextSecondary, fontSize = 13.sp)
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = onChat, 
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Message", fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                if (currentUid.isNotEmpty()) {
                    com.example.callruleblocker.call.CallSignalingManager.startCall(
                        mContext, user.id,
                        com.example.callruleblocker.call.AppCallType.VOICE,
                        { created ->
                            mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply {
                                putExtra("callId", created.id)
                                putExtra("isIncoming", false)
                            })
                        },
                        { e -> Toast.makeText(mContext, "Unable to start Shyna voice call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() }
                    )
                } else {
                    onVoiceCall()
                }
            }, 
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
        ) {
            Icon(Icons.Default.Call, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Shyna Voice Call", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                if (currentUid.isNotEmpty()) {
                    com.example.callruleblocker.call.CallSignalingManager.startCall(
                        mContext, user.id,
                        com.example.callruleblocker.call.AppCallType.VIDEO,
                        { created ->
                            mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply {
                                putExtra("callId", created.id)
                                putExtra("isIncoming", false)
                            })
                        },
                        { e -> Toast.makeText(mContext, "Unable to start Shyna video call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() }
                    )
                } else {
                    onVideoCall()
                }
            }, 
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF))
        ) {
            Icon(Icons.Default.Videocam, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Shyna Video Call", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onCancel) {
            Text("Cancel", color = ShynaDesign.colors.TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OfflineChatUI(user: OfflineUser) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(ShynaDesign.colors.HeaderBg).padding(16.dp)) {
            Text("${user.name} (${user.transport.first()})", fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f)) { /* Messages list */ }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(value = "", onValueChange = {}, modifier = Modifier.weight(1f), placeholder = { Text("Type a message...") })
            IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = ShynaDesign.colors.BrandGreen) }
        }
    }
}

@Composable
private fun OfflineCallingUI(user: OfflineUser, onEndCall: () -> Unit) {
    val mContext = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var audioRoute by remember { mutableStateOf("Speaker") }
    var secondsElapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        CallStateController.reportCallEvent(MainCallType.OFFLINE_CALL, GlobalCallState.ACTIVE, userId = user.id)
        while (isActive) {
            delay(1000L)
            secondsElapsed++
        }
    }

    val timeStr = remember(secondsElapsed) {
        val m = secondsElapsed / 60
        val s = secondsElapsed % 60
        String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShynaDesign.colors.PrimaryBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(ShynaDesign.colors.BrandGreen, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(timeStr, color = ShynaDesign.colors.BrandGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(32.dp))
        
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = ShynaDesign.colors.BrandGreen.copy(alpha = 0.15f),
            border = BorderStroke(2.dp, ShynaDesign.colors.BrandGreen)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(user.name.take(1).uppercase(), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen)
            }
        }
        
        Spacer(Modifier.height(20.dp))
        Text(user.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Offline ${user.transport.first()} Call • Audio: $audioRoute", color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp)

        Spacer(Modifier.height(60.dp))
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        isSpeakerOn = !isSpeakerOn
                        audioRoute = if (isSpeakerOn) "Speaker" else "Earpiece"
                    },
                    modifier = Modifier.size(56.dp).background(if(isSpeakerOn) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.HeaderBg, CircleShape)
                ) {
                    Icon(if(isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute, null, tint = if(isSpeakerOn) Color.White else ShynaDesign.colors.TextPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Text("Speaker", fontSize = 11.sp, color = ShynaDesign.colors.TextSecondary)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier.size(56.dp).background(if(isMuted) Color.Red else ShynaDesign.colors.HeaderBg, CircleShape)
                ) {
                    Icon(if(isMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = if(isMuted) Color.White else ShynaDesign.colors.TextPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Text("Mute", fontSize = 11.sp, color = ShynaDesign.colors.TextSecondary)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        val isBt = audioRoute == "Bluetooth"
                        audioRoute = if (isBt) "Speaker" else "Bluetooth"
                        Toast.makeText(mContext, if (!isBt) "Switched to Bluetooth Audio" else "Switched to Speaker", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(56.dp).background(if(audioRoute == "Bluetooth") Color(0xFF2979FF) else ShynaDesign.colors.HeaderBg, CircleShape)
                ) {
                    Icon(Icons.Default.Bluetooth, null, tint = if(audioRoute == "Bluetooth") Color.White else ShynaDesign.colors.TextPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Text("Bluetooth", fontSize = 11.sp, color = ShynaDesign.colors.TextSecondary)
            }
        }
        
        Spacer(Modifier.height(48.dp))
        
        FloatingActionButton(
            onClick = { 
                CallStateController.reportCallEvent(MainCallType.OFFLINE_CALL, GlobalCallState.ENDED)
                onEndCall()
            },
            containerColor = Color.Red,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(Icons.Default.CallEnd, "End Call", modifier = Modifier.size(32.dp))
        }
    }
}


private fun getModeTitle(mode: OfflineMode) = when (mode) {
    OfflineMode.HOME -> "Offline Call"
    OfflineMode.BLUETOOTH -> "Bluetooth"
    OfflineMode.WIFI_DIRECT -> "Wi-Fi Direct"
    OfflineMode.RADIO -> "Radio"
    OfflineMode.NEARBY_USERS -> "All Nearby Users"
    else -> "Offline Communication"
}
