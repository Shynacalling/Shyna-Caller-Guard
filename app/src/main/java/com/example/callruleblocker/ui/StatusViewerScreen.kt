package com.example.callruleblocker.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.callruleblocker.data.StatusBackendManager
import com.example.callruleblocker.data.StatusLocalStore
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(UnstableApi::class)
@Composable
fun FullscreenStatusViewerScreen(
    groups: List<StatusGroup>,
    initialGroupIndex: Int,
    currentUser: RealUser,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localStore = remember { StatusLocalStore.getInstance(context) }

    var groupIndex by remember { mutableIntStateOf(initialGroupIndex.coerceIn(0, (groups.size - 1).coerceAtLeast(0))) }
    val currentGroup = groups.getOrNull(groupIndex) ?: run { onClose(); return }

    var statusIndex by remember {
        mutableIntStateOf(
            if (currentGroup.firstUnreadIndex >= 0 && currentGroup.firstUnreadIndex < currentGroup.statuses.size) {
                currentGroup.firstUnreadIndex
            } else 0
        )
    }

    val currentStatus = currentGroup.statuses.getOrNull(statusIndex) ?: run { onClose(); return }

    var isPaused by remember { mutableStateOf(false) }
    var isVideoBuffering by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showViewersSheet by remember { mutableStateOf(false) }
    var viewersList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    val progressAnimatable = remember { Animatable(0f) }

    // Mark current status as seen locally & queue server view receipt
    LaunchedEffect(currentStatus.id) {
        localStore.markStatusSeenLocally(currentStatus.id)
        StatusBackendManager.recordServerView(currentStatus.id, currentUser.uid, currentUser.name)
    }

    // Auto-advance progress driver
    LaunchedEffect(statusIndex, groupIndex, isPaused, isVideoBuffering) {
        if (isPaused || isVideoBuffering) return@LaunchedEffect

        val duration = currentStatus.durationMs.coerceAtLeast(3000L)
        val remainingFraction = 1f - progressAnimatable.value
        val animDuration = (duration * remainingFraction).toInt()

        if (remainingFraction > 0) {
            progressAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = animDuration, easing = LinearEasing)
            )
        }

        // On status completion: advance status or group
        if (progressAnimatable.value >= 0.99f) {
            if (statusIndex < currentGroup.statuses.size - 1) {
                progressAnimatable.snapTo(0f)
                statusIndex++
            } else if (groupIndex < groups.size - 1) {
                progressAnimatable.snapTo(0f)
                groupIndex++
                val nextGroup = groups[groupIndex]
                statusIndex = if (nextGroup.firstUnreadIndex >= 0) nextGroup.firstUnreadIndex else 0
            } else {
                onClose()
            }
        }
    }

    // Reset progress on status change
    LaunchedEffect(statusIndex, groupIndex) {
        progressAnimatable.snapTo(0f)
    }

    val isOwnStatus = currentStatus.userId == currentUser.uid

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main Status Media Content Renderer
        when (currentStatus.type) {
            MessageType.IMAGE, MessageType.GIF -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    var isLoading by remember(currentStatus.id) { mutableStateOf(true) }
                    var isError by remember(currentStatus.id) { mutableStateOf(false) }

                    AsyncImage(
                        model = currentStatus.mediaUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onLoading = { isLoading = true; isError = false },
                        onSuccess = { isLoading = false; isError = false },
                        onError = { isLoading = false; isError = true }
                    )

                    if (isLoading) {
                        CircularProgressIndicator(color = Color(0xFF25D366), modifier = Modifier.size(36.dp))
                    }

                    if (isError) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Unable to load image", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }

            MessageType.VIDEO -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    var isError by remember(currentStatus.id) { mutableStateOf(false) }

                    if (currentStatus.mediaUrl.isBlank() || isError) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Unable to play video", color = Color.White, fontSize = 14.sp)
                        }
                    } else {
                        StatusVideoPlayer(
                            videoUrl = currentStatus.mediaUrl,
                            isPaused = isPaused,
                            onBufferingStateChange = { isVideoBuffering = it },
                            onVideoEnded = {
                                if (statusIndex < currentGroup.statuses.size - 1) {
                                    scope.launch { progressAnimatable.snapTo(0f) }
                                    statusIndex++
                                } else if (groupIndex < groups.size - 1) {
                                    scope.launch { progressAnimatable.snapTo(0f) }
                                    groupIndex++
                                    val nextGroup = groups[groupIndex]
                                    statusIndex = if (nextGroup.firstUnreadIndex >= 0) nextGroup.firstUnreadIndex else 0
                                } else {
                                    onClose()
                                }
                            },
                            onError = { isError = true }
                        )
                    }
                }
            }

            MessageType.TEXT, MessageType.LINK -> {
                val bgParsed = try {
                    Color(android.graphics.Color.parseColor(currentStatus.backgroundColor ?: "#008069"))
                } catch (_: Exception) {
                    Color(0xFF008069)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgParsed)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (!currentStatus.text.isNullOrBlank()) {
                            Text(
                                text = currentStatus.text,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }

                        val effectiveLinkUrl = currentStatus.linkUrl ?: if (currentStatus.text?.contains("http", ignoreCase = true) == true) currentStatus.text else null
                        if (currentStatus.type == MessageType.LINK && !effectiveLinkUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val urlToOpen = if (effectiveLinkUrl.startsWith("http", ignoreCase = true)) effectiveLinkUrl else "https://$effectiveLinkUrl"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Log.e("StatusViewer", "Error opening link: ${e.message}")
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    if (!currentStatus.linkImageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = currentStatus.linkImageUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    Text(
                                        text = currentStatus.linkTitle ?: effectiveLinkUrl,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 2
                                    )
                                    if (!currentStatus.linkDescription.isNullOrBlank()) {
                                        Text(
                                            text = currentStatus.linkDescription,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            maxLines = 2
                                        )
                                    }
                                    Text(
                                        text = currentStatus.linkDomain ?: effectiveLinkUrl,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                AsyncImage(
                    model = currentStatus.mediaUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Gesture Detector Overlay (Middle Area for Tap/Hold Navigation, leaving top/bottom bars interactive)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 120.dp)
                .pointerInput(statusIndex, groupIndex) {
                    detectTapGestures(
                        onPress = {
                            isPaused = true
                            tryAwaitRelease()
                            isPaused = false
                        },
                        onTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x > screenWidth * 0.5f) {
                                // Tap Right -> Next status / group
                                if (statusIndex < currentGroup.statuses.size - 1) {
                                    scope.launch { progressAnimatable.snapTo(0f) }
                                    statusIndex++
                                } else if (groupIndex < groups.size - 1) {
                                    scope.launch { progressAnimatable.snapTo(0f) }
                                    groupIndex++
                                    val nextG = groups[groupIndex]
                                    statusIndex = if (nextG.firstUnreadIndex >= 0) nextG.firstUnreadIndex else 0
                                } else {
                                    onClose()
                                }
                            } else {
                                // Tap Left -> Previous status / group
                                if (statusIndex > 0) {
                                    scope.launch { progressAnimatable.snapTo(0f) }
                                    statusIndex--
                                } else if (groupIndex > 0) {
                                    scope.launch { progressAnimatable.snapTo(0f) }
                                    groupIndex--
                                    statusIndex = 0
                                }
                            }
                        }
                    )
                }
        )

        // Overlay Controls (Hidden when long-pressing)
        AnimatedVisibility(
            visible = !isPaused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Progress Bar Row & Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Top Horizontal Progress Segments
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        currentGroup.statuses.forEachIndexed { idx, _ ->
                            val progress = when {
                                idx < statusIndex -> 1f
                                idx == statusIndex -> progressAnimatable.value
                                else -> 0f
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(CircleShape),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Metadata Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.DarkGray,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (!currentGroup.userPhoto.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentGroup.userPhoto,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.padding(8.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentGroup.userName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            val timeStr = remember(currentStatus.timestamp) {
                                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(currentStatus.timestamp))
                            }
                            Text(
                                text = timeStr,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        // 3-dot Overflow Menu
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (isOwnStatus) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Status", color = Color.Red) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                                        onClick = {
                                            showMenu = false
                                            scope.launch {
                                                StatusBackendManager.deleteStatus(currentStatus.id, currentUser.uid)
                                                onClose()
                                            }
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(if (currentGroup.isMuted) "Unhide / Unmute" else "Mute / Hide Status") },
                                        leadingIcon = { Icon(Icons.Default.VolumeOff, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            localStore.setMuteUser(currentGroup.userId, !currentGroup.isMuted)
                                            onClose()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Bar: Caption, Quick Reactions & Private Reply
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                    // Caption
                    if (!currentStatus.caption.isNullOrBlank()) {
                        Text(
                            text = currentStatus.caption,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                    }

                    if (isOwnStatus) {
                        // Own Status: Views & Reactions Count Footer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .clickable {
                                    // Fetch viewers list from Firestore
                                    scope.launch {
                                        val db = FirebaseFirestore.getInstance()
                                        val snap = db.collection("statuses").document(currentStatus.id)
                                            .collection("views").get().await()
                                        viewersList = snap.documents.mapNotNull { it.data }
                                        showViewersSheet = true
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.RemoveRedEye, contentDescription = "Views", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${currentStatus.seenBy.size} views",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        // Quick Reaction Emojis Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("❤️", "👍", "😂", "😮", "😢", "🔥").forEach { emoji ->
                                Surface(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clickable {
                                            scope.launch {
                                                try {
                                                    StatusBackendManager.toggleStatusLike(
                                                        currentStatus.id,
                                                        currentUser.uid,
                                                        true
                                                    )
                                                    Toast.makeText(context, "Reaction $emoji sent!", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Log.e("StatusViewer", "Like error: ${e.message}")
                                                }
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(emoji, fontSize = 18.sp)
                                    }
                                }
                            }
                        }

                        // Private Reply Input Field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = replyText,
                                onValueChange = { 
                                    replyText = it
                                    isPaused = it.isNotEmpty()
                                },
                                placeholder = { Text("Reply to ${currentGroup.userName}...", color = Color.White.copy(alpha = 0.6f)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF25D366),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (replyText.trim().isEmpty()) return@IconButton
                                    val textToSend = replyText.trim()
                                    replyText = ""
                                    isPaused = false
                                    scope.launch {
                                        try {
                                            val db = FirebaseFirestore.getInstance()
                                            val chatId = listOf(currentUser.uid, currentGroup.userId).sorted().joinToString("_")
                                            val msgData = mapOf(
                                                "id" to UUID.randomUUID().toString(),
                                                "text" to textToSend,
                                                "content" to textToSend,
                                                "senderId" to currentUser.uid,
                                                "receiverId" to currentGroup.userId,
                                                "type" to MessageType.TEXT.name,
                                                "status" to MessageStatus.SENT.name,
                                                "timestamp" to Timestamp.now(),
                                                "sentAt" to System.currentTimeMillis(),
                                                "quotedStatusId" to currentStatus.id,
                                                "quotedStatusType" to currentStatus.type.name,
                                                "quotedStatusMedia" to currentStatus.mediaUrl
                                            )
                                            db.collection("chats").document(chatId)
                                                .collection("messages").add(msgData).await()
                                            Toast.makeText(context, "Reply sent!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("StatusViewer", "Reply error: ${e.message}")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Reply", tint = Color(0xFF25D366))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun StatusVideoPlayer(
    videoUrl: String,
    isPaused: Boolean,
    onBufferingStateChange: (Boolean) -> Unit,
    onVideoEnded: () -> Unit = {},
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                onBufferingStateChange(isBuffering)
                if (playbackState == Player.STATE_ENDED) {
                    onVideoEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("StatusVideoPlayer", "Player error for $videoUrl: ${error.message}")
                onError()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPaused) {
        if (isPaused) exoPlayer.pause() else exoPlayer.play()
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
