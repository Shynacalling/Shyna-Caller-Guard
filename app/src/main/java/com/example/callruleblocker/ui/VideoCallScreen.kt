package com.example.callruleblocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.callruleblocker.call.LiveKitCallManager
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.renderer.TextureViewRenderer
import kotlinx.coroutines.launch

import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.track.RemoteVideoTrack
import io.livekit.android.room.track.LocalVideoTrack

@Composable
fun VideoCallScreen(roomName: String, userId: String, callId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callManager = remember { LiveKitCallManager(context) }
    var room by remember { mutableStateOf<Room?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    
    var isMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val r = callManager.joinRoom(roomName, userId, callId)
            room = r
            
            // Local track
            localVideoTrack = r.localParticipant.videoTrackPublications.firstOrNull()?.second as? VideoTrack
            
            // Listen for remote tracks using the LaunchedEffect's own CoroutineScope
            r.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed -> {
                        if (event.track is VideoTrack) {
                            remoteVideoTrack = event.track as VideoTrack
                        }
                    }
                    is RoomEvent.TrackUnsubscribed -> {
                        if (event.track == remoteVideoTrack) {
                            remoteVideoTrack = null
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            callManager.leaveRoom()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Remote Video (Full Screen)
        val currentRoom = room
        if (currentRoom != null && remoteVideoTrack != null) {
            VideoRenderer(remoteVideoTrack!!, currentRoom, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Waiting for remote participant...", color = Color.White)
            }
        }

        // Local Video (PiP)
        if (currentRoom != null && localVideoTrack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(120.dp, 180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray)
            ) {
                VideoRenderer(localVideoTrack!!, currentRoom, modifier = Modifier.fillMaxSize())
            }
        }

        // Top Info
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text("Room: $roomName", color = Color.White, fontSize = 18.sp)
            Text("User: $userId", color = Color.Gray, fontSize = 14.sp)
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { 
                    isMuted = !isMuted
                    scope.launch {
                        room?.localParticipant?.setMicrophoneEnabled(!isMuted)
                    }
                },
                modifier = Modifier.background(if (isMuted) Color.Red else Color.DarkGray, CircleShape)
            ) {
                Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, null, tint = Color.White)
            }

            FloatingActionButton(
                onClick = { onBack() },
                containerColor = Color.Red,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.CallEnd, null)
            }

            IconButton(
                onClick = { 
                    isCameraOff = !isCameraOff
                    scope.launch {
                        room?.localParticipant?.setCameraEnabled(!isCameraOff)
                    }
                },
                modifier = Modifier.background(if (isCameraOff) Color.Red else Color.DarkGray, CircleShape)
            ) {
                Icon(if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun VideoRenderer(track: VideoTrack, room: Room, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            TextureViewRenderer(context).apply {
                room.initVideoRenderer(this)
            }
        },
        modifier = modifier,
        update = { renderer ->
            track.addRenderer(renderer)
        },
        onRelease = { renderer ->
            track.removeRenderer(renderer)
            renderer.release()
        }
    )
}
