package com.example.callruleblocker.ui

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.callruleblocker.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TextMessageBubble(m: UniversalMessage) {
    Text(m.text, color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp)
}

@Composable
fun ImageMessageBubble(m: UniversalMessage) {
    Column {
        SubcomposeAsyncImage(
            model = m.metadata,
            contentDescription = null,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = ShynaDesign.colors.BrandGreen)
                }
            }
        )
        if (!m.caption.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(m.caption, color = ShynaDesign.colors.TextPrimary, fontSize = 15.sp)
        }
    }
}

@Composable
fun VideoMessageBubble(m: UniversalMessage) {
    Column {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val thumbnailUrl = remember(m.metadata) {
                if (m.metadata?.startsWith("http") == true && m.metadata.contains("/video/upload/")) {
                    m.metadata.replace("/video/upload/", "/video/upload/w_500,h_500,c_fill,so_0/").replace(".mp4", ".jpg")
                } else {
                    m.metadata
                }
            }
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = Color.Black.copy(0.5f)
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.padding(10.dp))
            }
            val dur = remember(m.durationMs) {
                if (m.durationMs > 0) {
                    val sec = m.durationMs / 1000
                    String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60)
                } else "0:00"
            }
            Text(
                dur, 
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 4f))
            )
        }
        if (!m.caption.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(m.caption, color = ShynaDesign.colors.TextPrimary, fontSize = 15.sp)
        }
    }
}

@Composable
fun VoiceMessageBubble(m: UniversalMessage) {
    BuiltInAudioPlayer(m.metadata)
}

@Composable
fun AudioMessageBubble(m: UniversalMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AudioFile, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.fileName ?: "Audio File", color = ShynaDesign.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatFileSize(m.fileSize), color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
        }
        IconButton(onClick = { /* Play */ }) {
            Icon(Icons.Default.PlayArrow, null, tint = ShynaDesign.colors.BrandGreen)
        }
    }
}

@Composable
fun LocationMessageBubble(m: UniversalMessage) {
    val loc = m.metadata ?: "0,0"
    val parts = loc.split("|")
    val coords = parts.getOrNull(0)?.split(",") ?: listOf("0", "0")
    val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: 0.0
    val lon = coords.getOrNull(1)?.toDoubleOrNull() ?: 0.0
    val accuracy = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val name = parts.getOrNull(2) ?: m.fileName

    Column(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ShynaDesign.colors.SurfaceBg)
    ) {
        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            val mapUrl = "https://maps.googleapis.com/maps/api/staticmap?center=${lat},${lon}&zoom=15&size=500x320&markers=color:red%7C${lat},${lon}&key=AIzaSyCN4fFi1IDkR2BYmjybqn0bzuu598i-A9U"
            AsyncImage(
                model = mapUrl,
                contentDescription = "Map",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Icon(Icons.Default.LocationOn, null, tint = Color.Red, modifier = Modifier.size(32.dp))
        }
        
        Column(Modifier.padding(10.dp)) {
            Text(
                text = if (!name.isNullOrBlank()) name else "Shared Location",
                color = ShynaDesign.colors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (accuracy > 0) {
                Text(
                    "Accurate to ${accuracy}m",
                    color = ShynaDesign.colors.TextSecondary,
                    fontSize = 11.sp
                )
            } else {
                Text(
                    "${String.format(Locale.US, "%.4f", lat)}, ${String.format(Locale.US, "%.4f", lon)}",
                    color = ShynaDesign.colors.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun LiveLocationMessageBubble(
    m: UniversalMessage,
    onOpenLiveLocation: ((UniversalMessage) -> Unit)? = null
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var livePos by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isSessionActive by remember { mutableStateOf(true) }
    
    val expiry = m.liveLocationExpiry ?: 0L
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Live Location Firestore observer for sender's real-time position
    DisposableEffect(m.senderId) {
        val listener = db.collection("live_locations").document(m.senderId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val lat = snapshot.getDouble("lat")
                    val lng = snapshot.getDouble("lng")
                    val active = snapshot.getBoolean("isActive") ?: true
                    if (lat != null && lng != null) {
                        livePos = Pair(lat, lng)
                    }
                    isSessionActive = active
                }
            }
        onDispose { listener.remove() }
    }

    // Ticking timer for countdown
    LaunchedEffect(expiry) {
        while (currentTime < expiry && isSessionActive) {
            delay(10000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val remainingMs = expiry - currentTime
    val isExpired = remainingMs <= 0 || !isSessionActive

    val formattedRemaining = remember(remainingMs, isExpired) {
        if (isExpired) "Live location ended"
        else {
            val totalSec = remainingMs / 1000
            val min = (totalSec / 60) % 60
            val hrs = totalSec / 3600
            if (hrs > 0) {
                "${hrs} hr ${min} min remaining"
            } else {
                "${(min).coerceAtLeast(1)} min remaining"
            }
        }
    }

    val lat = livePos?.first ?: 0.0
    val lon = livePos?.second ?: 0.0
    val hasCoords = lat != 0.0 || lon != 0.0

    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .clickable {
                if (onOpenLiveLocation != null) {
                    onOpenLiveLocation(m)
                }
            }
    ) {
        // Map Preview Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (hasCoords) {
                val mapUrl = "https://maps.googleapis.com/maps/api/staticmap?center=${lat},${lon}&zoom=15&size=520x300&markers=color:green%7C${lat},${lon}&key=AIzaSyCN4fFi1IDkR2BYmjybqn0bzuu598i-A9U"
                AsyncImage(
                    model = mapUrl,
                    contentDescription = "Live Map Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Map, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
            }

            // Live badge overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = if (!isExpired) ShynaDesign.colors.BrandGreen else Color.Red
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WifiTethering, 
                        contentDescription = null, 
                        tint = Color.White, 
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (!isExpired) "LIVE" else "ENDED",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Info Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn, 
                contentDescription = null, 
                tint = if (!isExpired) ShynaDesign.colors.BrandGreen else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live Location",
                    fontWeight = FontWeight.Bold,
                    color = ShynaDesign.colors.TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = formattedRemaining,
                    fontSize = 12.sp,
                    color = if (!isExpired) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary
                )
            }
        }
    }
}

@Composable
fun LinkMessageBubble(m: UniversalMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .padding(12.dp)
    ) {
        Text(m.text, color = Color(0xFF00A5F4), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
    }
}

@Composable
fun DocMessageBubble(m: UniversalMessage) {
    val mContext = LocalContext.current
    val isDownloading = DocInteraction.isDownloading(m.id)
    val ext = remember(m.fileName) { m.fileName?.substringAfterLast(".", "")?.lowercase() ?: "" }

    val fileExists = remember(m.id, m.fileName, isDownloading) {
        val sanitizedName = m.fileName?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: ""
        File(mContext.cacheDir, "${m.id}_$sanitizedName").exists()
    }

    val badgeColor = when (ext) {
        "pdf" -> Color(0xFFE53935) // Red
        in listOf("doc", "docx") -> Color(0xFF1E88E5) // Blue
        in listOf("xls", "xlsx") -> Color(0xFF43A047) // Green
        in listOf("ppt", "pptx") -> Color(0xFFFB8C00) // Orange
        "apk" -> Color(0xFF00897B) // Teal
        in listOf("zip", "rar", "7z") -> Color(0xFF8E24AA) // Purple
        else -> ShynaDesign.colors.BrandGreen
    }

    val badgeIcon = when (ext) {
        "pdf" -> Icons.Default.PictureAsPdf
        in listOf("doc", "docx") -> Icons.Default.Description
        in listOf("xls", "xlsx") -> Icons.Default.TableChart
        "apk" -> Icons.Default.Android
        in listOf("zip", "rar", "7z") -> Icons.Default.FolderZip
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(badgeIcon, null, tint = badgeColor, modifier = Modifier.size(24.dp))
                    if (ext.isNotBlank() && ext.length <= 4) {
                        Text(ext.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = badgeColor)
                    }
                }
            }
            
            Spacer(Modifier.width(10.dp))
            
            Column(Modifier.weight(1f)) {
                Text(m.fileName ?: "Document", color = ShynaDesign.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val info = remember(m.fileSize, m.fileName) {
                    val size = formatFileSize(m.fileSize)
                    if (ext.isNotBlank()) "$ext • $size".uppercase() else size
                }
                Text(info, color = ShynaDesign.colors.TextSecondary, fontSize = 11.sp)
            }
            
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ShynaDesign.colors.BrandGreen, strokeWidth = 2.dp)
            } else if (fileExists) {
                Icon(Icons.Default.CheckCircle, "Downloaded", tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(22.dp))
            } else {
                Icon(Icons.Default.Download, "Download", tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun ContactMessageBubble(m: UniversalMessage, onOpenContact: (UniversalMessage) -> Unit = {}) {
    val parts = m.metadata?.split("|") ?: listOf("Contact", "")
    val name = parts.getOrNull(0) ?: "Contact"
    val rawPhones = parts.getOrNull(1) ?: ""
    val photoUri = parts.getOrNull(2)
    val phoneList = remember(rawPhones) { rawPhones.split(",").filter { it.isNotBlank() } }
    val displayPhone = if (phoneList.size > 1) "${phoneList.size} Phone Numbers" else phoneList.firstOrNull() ?: ""

    Column(
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .clickable { onOpenContact(m) }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, modifier = Modifier.size(44.dp), color = ShynaDesign.colors.BrandGreen.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) {
                    if (!photoUri.isNullOrBlank()) {
                        AsyncImage(model = photoUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 18.sp)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                Text(displayPhone, fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = ShynaDesign.colors.DividerColor)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { onOpenContact(m) }) {
                Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("View Contact", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun PollMessageBubble(m: UniversalMessage, userId: String, isSelectionMode: Boolean = false, onVote: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .padding(12.dp)
    ) {
        Text(m.pollQuestion ?: "Poll", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(if(m.allowMultipleAnswers) "Select one or more" else "Select one", color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        val totalVotes = m.pollVotes.values.sumOf { it.size }.coerceAtLeast(1)
        val attempts = m.interactionAttempts[userId] ?: 0
        val firstTime = m.firstInteractionTime[userId] ?: m.lastInteractionTime[userId] ?: 0L
        val now = System.currentTimeMillis()
        
        val isTimeLocked = firstTime > 0L && (now - firstTime >= 30 * 60 * 1000L)
        val canInteract = attempts < 5 && !isTimeLocked

        val statusText = when {
            attempts >= 5 -> "Responses closed (5/5 attempts used)"
            isTimeLocked -> "Voting locked (30 min time limit reached)"
            attempts == 0 -> "5 chances left"
            attempts == 1 -> "4 chances left"
            attempts == 2 -> "3 chances left"
            attempts == 3 -> "2 chances left"
            attempts == 4 -> "1 chance left (Final chance)"
            else -> "Responses closed"
        }

        val statusColor = when {
            attempts >= 5 || isTimeLocked -> Color.Red
            attempts == 4 -> Color(0xFFFF9800)
            else -> ShynaDesign.colors.BrandGreen
        }

        Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        m.pollOptions.forEachIndexed { index, option ->
            val voters = m.pollVotes[index.toString()] ?: emptyList()
            val hasVoted = voters.contains(userId)
            val percentage = (voters.size.toFloat() / totalVotes)
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canInteract && !isSelectionMode) { onVote(index) }
                    .padding(vertical = 6.dp)
                    .graphicsLayer { alpha = if (!canInteract && !hasVoted) 0.6f else 1.0f }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = hasVoted, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = ShynaDesign.colors.BrandGreen))
                    Spacer(Modifier.width(8.dp))
                    Text(option, color = ShynaDesign.colors.TextPrimary, modifier = Modifier.weight(1f))
                    Text(voters.size.toString(), color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { percentage },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = if(hasVoted) ShynaDesign.colors.BrandGreen else Color.Gray.copy(alpha = 0.3f),
                    trackColor = Color.Gray.copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
fun EventMessageBubble(m: UniversalMessage, userId: String, isSelectionMode: Boolean = false, onRSVP: () -> Unit) {
    val mContext = LocalContext.current
    val goingCount = m.eventRSVPs["going"]?.size ?: 0
    val maybeCount = m.eventRSVPs["maybe"]?.size ?: 0
    val notGoingCount = m.eventRSVPs["not_going"]?.size ?: 0
    
    val currentUserStatus = when {
        m.eventRSVPs["going"]?.contains(userId) == true -> "Going"
        m.eventRSVPs["maybe"]?.contains(userId) == true -> "Maybe"
        m.eventRSVPs["not_going"]?.contains(userId) == true -> "Not Going"
        else -> null
    }
    val attempts = m.interactionAttempts[userId] ?: 0
    val firstTime = m.firstInteractionTime[userId] ?: m.lastInteractionTime[userId] ?: 0L
    val now = System.currentTimeMillis()
    
    val isTimeLocked = firstTime > 0L && (now - firstTime >= 30 * 60 * 1000L)
    val canInteract = attempts < 5 && !isTimeLocked

    val statusText = when {
        attempts >= 5 -> "RSVP Finalized (5/5 attempts used)"
        isTimeLocked -> "RSVP locked (30 min time limit reached)"
        attempts == 0 -> "5 chances left"
        attempts == 1 -> "4 chances left"
        attempts == 2 -> "3 chances left"
        attempts == 3 -> "2 chances left"
        attempts == 4 -> "1 chance left (Final chance)"
        else -> "RSVP Finalized"
    }

    val statusColor = when {
        attempts >= 5 || isTimeLocked -> Color.Red
        attempts == 4 -> Color(0xFFFF9800)
        else -> ShynaDesign.colors.BrandGreen
    }

    val dateStr = remember(m.eventStartAt, m.eventEndAt) {
        val start = if (m.eventStartAt > 0) m.eventStartAt else System.currentTimeMillis()
        val startDateStr = SimpleDateFormat("dd MMM, yyyy • h:mm a", Locale.getDefault()).format(Date(start))
        if (m.eventEndAt > start) {
            val endTimeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.eventEndAt))
            "$startDateStr - $endTimeStr"
        } else {
            startDateStr
        }
    }

    Column(
        modifier = Modifier
            .width(270.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF8E1))
            .clickable(enabled = canInteract && !isSelectionMode) { onRSVP() }
            .padding(12.dp)
            .graphicsLayer { alpha = if (!canInteract) 0.85f else 1.0f }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Event, null, tint = Color(0xFFFBC02D), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(m.eventTitle ?: "Event", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 15.sp)
                Text(dateStr, fontSize = 11.sp, color = Color.DarkGray)
            }
        }
        if (!m.eventDescription.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(m.eventDescription, color = Color.DarkGray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!m.eventLocation.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(m.eventLocation, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        
        Spacer(Modifier.height(6.dp))
        Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
        Spacer(Modifier.height(6.dp))
        
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Going: $goingCount • Maybe: $maybeCount • No: $notGoingCount", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        if (currentUserStatus != null) {
            Text("Your response: $currentUserStatus", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }

        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, m.eventTitle ?: "Event")
                    putExtra(CalendarContract.Events.DESCRIPTION, m.eventDescription ?: "")
                    putExtra(CalendarContract.Events.EVENT_LOCATION, m.eventLocation ?: "")
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, m.eventStartAt)
                    val endTime = if (m.eventEndAt > m.eventStartAt) m.eventEndAt else m.eventStartAt + 3600000L
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching {
                    mContext.startActivity(intent)
                }.onFailure {
                    Toast.makeText(mContext, "No calendar app found", Toast.LENGTH_SHORT).show()
                }
            },
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFFBC02D).copy(alpha = 0.2f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarToday, null, tint = Color(0xFFE65100), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add to Calendar", color = Color(0xFFE65100), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CallMessageBubble(m: UniversalMessage, isSelectionMode: Boolean = false, onCallAgain: () -> Unit) {
    val isVideo = m.callType == "VIDEO"
    val status = m.callStatus ?: "ENDED"
    
    val icon = when (status) {
        "MISSED" -> Icons.AutoMirrored.Filled.PhoneMissed
        "DECLINED" -> Icons.Default.Block
        else -> if (isVideo) Icons.Default.Videocam else Icons.Default.Call
    }
    
    val color = if (status == "MISSED") Color.Red else ShynaDesign.colors.BrandGreen
    
    val text = when (status) {
        "MISSED" -> if (isVideo) "Missed video call" else "Missed audio call"
        "DECLINED" -> if (isVideo) "Video call declined" else "Audio call declined"
        else -> {
            val duration = formatCallDuration(m.callDuration)
            if (isVideo) "Video call · $duration" else "Audio call · $duration"
        }
    }

    Surface(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isSelectionMode) { onCallAgain() },
        color = ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Tap to call back", color = ShynaDesign.colors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

private fun formatCallDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}

@Composable
private fun BuiltInAudioPlayer(url: String?) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val mediaPlayer = remember { MediaPlayer() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }

    Row(
        modifier = Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ShynaDesign.colors.SurfaceBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            if (isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
            } else {
                if (progress == 0f) {
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(url)
                    mediaPlayer.prepareAsync()
                    mediaPlayer.setOnPreparedListener { 
                        it.start()
                        isPlaying = true
                        scope.launch {
                            while (isPlaying && isActive) {
                                progress = mediaPlayer.currentPosition.toFloat() / mediaPlayer.duration
                                delay(100)
                            }
                        }
                    }
                    mediaPlayer.setOnCompletionListener { isPlaying = false; progress = 0f }
                } else {
                    mediaPlayer.start()
                    isPlaying = true
                }
            }
        }) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = ShynaDesign.colors.BrandGreen)
        }
        
        Slider(
            value = progress,
            onValueChange = { /* seek */ },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = ShynaDesign.colors.BrandGreen, activeTrackColor = ShynaDesign.colors.BrandGreen)
        )
        
        Icon(Icons.Default.Mic, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(16.dp).padding(end = 4.dp))
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatChatDate(time: Long): String {
    if (time == 0L) return ""
    return SimpleDateFormat("dd/MM/yy, HH:mm", Locale.getDefault()).format(Date(time))
}

object DocInteraction {
    private val downloadingIds = mutableStateSetOf<String>()
    
    fun isDownloading(id: String) = downloadingIds.contains(id)

    fun downloadAndOpen(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, m: UniversalMessage) {
        val url = m.metadata ?: return
        val rawName = m.fileName ?: "attachment_${System.currentTimeMillis()}"
        val sanitizedName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        
        val finalFile = File(context.cacheDir, "${m.id}_$sanitizedName")
        val partFile = File(context.cacheDir, "${m.id}_$sanitizedName.part")

        if (finalFile.exists() && finalFile.length() > 0) {
            FileOpener.open(context, finalFile, m.mimeType)
            return
        }

        if (downloadingIds.contains(m.id)) return

        downloadingIds.add(m.id)
        
        scope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connect()
                val expectedSize = connection.contentLength.toLong()
                
                connection.getInputStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val data = ByteArray(8192)
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            output.write(data, 0, count)
                        }
                        output.flush()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    downloadingIds.remove(m.id)
                    if (partFile.exists() && (expectedSize <= 0 || partFile.length() == expectedSize)) {
                        if (partFile.renameTo(finalFile)) {
                            FileOpener.open(context, finalFile, m.mimeType)
                        }
                    } else {
                        partFile.delete()
                        Toast.makeText(context, "File verification failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadingIds.remove(m.id)
                    partFile.delete()
                    Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

object FileOpener {
    fun open(context: Context, file: File, mimeType: String?) {
        val ext = file.extension.lowercase(Locale.getDefault())
        val resolvedMime = when {
            !mimeType.isNullOrBlank() && mimeType != "*/*" -> mimeType
            ext == "apk" -> "application/vnd.android.package-archive"
            ext == "pdf" -> "application/pdf"
            ext in listOf("doc", "docx") -> "application/msword"
            ext in listOf("xls", "xlsx") -> "application/vnd.ms-excel"
            ext in listOf("ppt", "pptx") -> "application/vnd.ms-powerpoint"
            ext in listOf("png", "jpg", "jpeg", "webp", "gif") -> "image/*"
            ext in listOf("mp4", "mkv", "3gp", "webm", "avi") -> "video/*"
            ext in listOf("mp3", "m4a", "wav", "aac", "ogg") -> "audio/*"
            ext in listOf("txt", "log", "csv") -> "text/plain"
            ext in listOf("zip", "rar", "7z") -> "application/zip"
            else -> "*/*"
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open file", Toast.LENGTH_SHORT).show()
        }
    }
}
