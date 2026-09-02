package com.example.callruleblocker.ui

import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callruleblocker.call.NativeRecordingStore
import com.example.callruleblocker.call.RecordingPlayback
import com.example.callruleblocker.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallRecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var recordings by remember { mutableStateOf<List<NativeRecordingStore.RecordingEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var playingId by remember { mutableStateOf<String?>(null) }
    var playbackPaused by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var mediaPermissionGranted by remember { mutableStateOf(NativeRecordingStore.hasMediaReadPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        mediaPermissionGranted = granted || NativeRecordingStore.hasMediaReadPermission(context)
        if (!mediaPermissionGranted) {
            Toast.makeText(
                context,
                "Audio permission was not granted. Shyna recordings will still be shown.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            RecordingPlayback.stopAndRelease(player)
            player = null
        }
    }

    LaunchedEffect(Unit) {
        if (!mediaPermissionGranted) {
            NativeRecordingStore.mediaReadPermission()?.let(permissionLauncher::launch)
        }
    }

    LaunchedEffect(mediaPermissionGranted) {
        isLoading = true
        recordings = withContext(Dispatchers.IO) {
            NativeRecordingStore.scan(context, includeSharedMedia = mediaPermissionGranted)
        }
        isLoading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Call recordings", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PremiumPurpleTop, PremiumPurpleMid, PremiumPurpleBottom))).padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            } else if (recordings.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.MicOff, null, modifier = Modifier.size(64.dp), tint = Color.White.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("No recordings found", color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recordings, key = { it.stableId }) { recording ->
                        RecordingItem(
                            recording = recording,
                            isPlaying = playingId == recording.stableId && !playbackPaused,
                            isPaused = playingId == recording.stableId && playbackPaused,
                            onPlayPause = {
                                if (playingId == recording.stableId) {
                                    if (playbackPaused) {
                                        if (RecordingPlayback.resume(player)) playbackPaused = false
                                        else {
                                            RecordingPlayback.stopAndRelease(player)
                                            player = null
                                            playingId = null
                                            playbackPaused = false
                                            Toast.makeText(context, "Recording could not resume", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        if (RecordingPlayback.pause(player)) playbackPaused = true
                                    }
                                } else {
                                    RecordingPlayback.stopAndRelease(player)
                                    player = null

                                    val result = when {
                                        recording.file != null -> RecordingPlayback.openAndPlay(
                                            file = recording.file,
                                            onCompletion = {
                                                player = null
                                                playingId = null
                                                playbackPaused = false
                                            },
                                            onError = { message ->
                                                player = null
                                                playingId = null
                                                playbackPaused = false
                                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                        recording.uri != null -> RecordingPlayback.openAndPlay(
                                            context = context,
                                            uri = recording.uri,
                                            onCompletion = {
                                                player = null
                                                playingId = null
                                                playbackPaused = false
                                            },
                                            onError = { message ->
                                                player = null
                                                playingId = null
                                                playbackPaused = false
                                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                        else -> Result.failure(IllegalStateException("Recording source is unavailable"))
                                    }

                                    result.onSuccess { opened ->
                                        player = opened
                                        playingId = recording.stableId
                                        playbackPaused = false
                                    }.onFailure { error ->
                                        playingId = null
                                        playbackPaused = false
                                        Toast.makeText(context, error.message ?: "Recording could not be played", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onStop = {
                                RecordingPlayback.stopAndRelease(player)
                                player = null
                                playingId = null
                                playbackPaused = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingItem(
    recording: NativeRecordingStore.RecordingEntry,
    isPlaying: Boolean,
    isPaused: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit
) {
    val dateStr = remember(recording.modifiedAt) {
        SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault()).format(Date(recording.modifiedAt))
    }
    val fileName = recording.displayName

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.07f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (isPlaying) Color(0xFF24C98A).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                        null,
                        tint = if (isPlaying) Color(0xFF24C98A) else Color.White
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateStr,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                if (isPlaying || isPaused) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Filled.Stop, null, tint = Color(0xFFFF8D94), modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
