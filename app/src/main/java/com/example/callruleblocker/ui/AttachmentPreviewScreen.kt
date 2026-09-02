package com.example.callruleblocker.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage

@Composable
fun AttachmentPreviewScreen(
    media: List<Pair<Uri, Boolean>>,
    onSend: (List<Pair<Uri, Boolean>>, String) -> Unit,
    onDismiss: () -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf(media.firstOrNull()) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Main Preview
        selectedItem?.let { (uri, isVideo) ->
            if (isVideo) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { context ->
                            VideoView(context).apply {
                                setVideoURI(uri)
                                val mc = MediaController(context)
                                mc.setAnchorView(this)
                                setMediaController(mc)
                                setOnPreparedListener { it.isLooping = true; start() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16/9f),
                        update = { it.setVideoURI(uri); it.start() }
                    )
                }
            } else {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Header
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.background(Color.Black.copy(0.4f), CircleShape)) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }

        // Bottom Controls
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(0.6f)).padding(16.dp)
        ) {
            // Thumbnails
            if (media.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(media) { item ->
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedItem == item) Color.White else Color.Transparent)
                                .padding(if (selectedItem == item) 2.dp else 0.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.DarkGray)
                                .clickable { selectedItem = item }
                        ) {
                            AsyncImage(
                                model = item.first,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (item.second) {
                                Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(20.dp))
                            }
                        }
                    }
                }
            }

            // Caption and Send
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(0.1f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    BasicTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        decorationBox = { innerTextField ->
                            if (caption.isEmpty()) Text("Add a caption...", color = Color.White.copy(0.6f))
                            innerTextField()
                        }
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = { onSend(media, caption) },
                    modifier = Modifier.size(48.dp).background(Color(0xFF25D366), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White)
                }
            }
        }
    }
}
