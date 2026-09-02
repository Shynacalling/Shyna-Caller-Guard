package com.example.callruleblocker.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(UnstableApi::class)
@Composable
fun ChatMediaViewerScreen(
    initialIndex: Int,
    mediaList: List<UniversalMessage>,
    onDismiss: () -> Unit
) {
    // Determine the actual index from the list (safety check)
    val startIndex = remember(initialIndex, mediaList) {
        initialIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0))
    }
    
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { mediaList.size })
    var controlsVisible by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2, // Load more for smoother swipe
            userScrollEnabled = true
        ) { page ->
            val media = mediaList[page]
            val isCurrentPage = pagerState.currentPage == page

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (media.messageType == MessageType.VIDEO) {
                    VideoPlayerPage(
                        videoUrl = media.metadata ?: "",
                        isActive = isCurrentPage,
                        onToggleControls = { controlsVisible = !controlsVisible }
                    )
                } else {
                    ZoomableImagePage(
                        imageUrl = media.metadata ?: "",
                        onToggleControls = { controlsVisible = !controlsVisible }
                    )
                }
            }
        }

        // Overlay UI
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top Bar
                val currentMedia = mediaList.getOrNull(pagerState.currentPage)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(0.4f))
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (currentMedia?.isMine == true) "You" else "Peer",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        val dateStr = remember(currentMedia?.time) {
                            currentMedia?.time?.let {
                                SimpleDateFormat("dd/MM/yy, HH:mm", Locale.getDefault()).format(Date(it))
                            } ?: ""
                        }
                        Text(dateStr, color = Color.White.copy(0.7f), fontSize = 12.sp)
                    }
                    val context = LocalContext.current
                    IconButton(onClick = { 
                        currentMedia?.let { m ->
                            val url = m.metadata
                            if (url != null) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    // Fallback if no browser/viewer
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, "Download", tint = Color.White)
                    }
                }

                // Page Indicator (Bottom)
                Text(
                    text = "${pagerState.currentPage + 1} of ${mediaList.size}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ZoomableImagePage(imageUrl: String, onToggleControls: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it.toSize() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 3f
                            val maxX = (size.width * (3f - 1f)) / 2f
                            val maxY = (size.height * (3f - 1f)) / 2f
                            val newX = ((size.width / 2f) - tapOffset.x) * 2f
                            val newY = ((size.height / 2f) - tapOffset.y) * 2f
                            offset = Offset(newX.coerceIn(-maxX, maxX), newY.coerceIn(-maxY, maxY))
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    if (newScale > 1f) {
                        val maxX = (size.width * (newScale - 1f)) / 2f
                        val maxY = (size.height * (newScale - 1f)) / 2f
                        val newX = (offset.x + pan.x * newScale).coerceIn(-maxX, maxX)
                        val newY = (offset.y + pan.y * newScale).coerceIn(-maxY, maxY)
                        scale = newScale
                        offset = Offset(newX, newY)
                    } else {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerPage(
    videoUrl: String,
    isActive: Boolean,
    onToggleControls: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    var isEnded by remember { mutableStateOf(false) }

    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isEnded = state == Player.STATE_ENDED
            }
        }
        exoPlayer.addListener(listener)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(videoUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        exoPlayer.prepare()
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size = it.toSize() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 3f
                            offset = Offset.Zero
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = Offset.Zero
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )

        if (isEnded) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.5f))
                    .clickable { 
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                        isEnded = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
    }
}
