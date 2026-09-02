package com.example.callruleblocker.ui

import android.net.Uri
import android.util.Patterns
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil.compose.AsyncImage
import com.example.callruleblocker.data.LinkPreviewData
import com.example.callruleblocker.data.StatusBackendManager
import com.example.callruleblocker.data.StatusLocalStore
import com.example.callruleblocker.data.StatusUploadWorker
import kotlinx.coroutines.launch
import java.util.UUID

import androidx.compose.ui.viewinterop.AndroidView

val PRESET_BACKGROUND_COLORS = listOf(
    Color(0xFF008069), // Dark Teal
    Color(0xFF1F2C34), // Dark Slate
    Color(0xFF536771), // Steel
    Color(0xFF233B47), // Deep Blue
    Color(0xFF8B0000), // Dark Red
    Color(0xFF800080), // Purple
    Color(0xFF2E8B57), // Forest Green
    Color(0xFFD2691E), // Chocolate
    Color(0xFFC71585), // Medium Violet Red
    Color(0xFF000000)  // Pitch Black
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextAndLinkStatusComposer(
    currentUser: RealUser,
    onBack: () -> Unit,
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    val localStore = remember { StatusLocalStore.getInstance(context) }

    var textInput by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var fontStyleIndex by remember { mutableIntStateOf(0) }
    var textAlignment by remember { mutableStateOf(TextAlign.Center) }
    var linkPreview by remember { mutableStateOf<LinkPreviewData?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }

    val fontFamilies = listOf(
        FontFamily.Default,
        FontFamily.Serif,
        FontFamily.Monospace,
        FontFamily.Cursive,
        FontFamily.SansSerif
    )
    val fontNames = listOf("Standard", "Serif", "Mono", "Cursive", "Sans")

    // Live URL Detection for Link Status Preview
    LaunchedEffect(textInput) {
        val trimmed = textInput.trim()
        val matcher = Patterns.WEB_URL.matcher(trimmed)
        if (matcher.find()) {
            val detectedUrl = matcher.group()
            if (detectedUrl != null && detectedUrl != linkPreview?.url) {
                isLoadingPreview = true
                linkPreview = StatusBackendManager.fetchLinkPreview(detectedUrl)
                isLoadingPreview = false
            }
        } else {
            linkPreview = null
        }
    }

    val backgroundColor = PRESET_BACKGROUND_COLORS[selectedColorIndex % PRESET_BACKGROUND_COLORS.size]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Toggle Font
                IconButton(onClick = { fontStyleIndex = (fontStyleIndex + 1) % fontFamilies.size }) {
                    Icon(Icons.Default.TextFields, contentDescription = "Font", tint = Color.White)
                }
                // Toggle Background Color
                IconButton(onClick = { selectedColorIndex = (selectedColorIndex + 1) % PRESET_BACKGROUND_COLORS.size }) {
                    Icon(Icons.Default.Palette, contentDescription = "Color", tint = Color.White)
                }
                // Toggle Text Alignment
                IconButton(onClick = {
                    textAlignment = when (textAlignment) {
                        TextAlign.Center -> TextAlign.Left
                        TextAlign.Left -> TextAlign.Right
                        else -> TextAlign.Center
                    }
                }) {
                    val icon = when (textAlignment) {
                        TextAlign.Left -> Icons.Default.FormatAlignLeft
                        TextAlign.Right -> Icons.Default.FormatAlignRight
                        else -> Icons.Default.FormatAlignCenter
                    }
                    Icon(icon, contentDescription = "Alignment", tint = Color.White)
                }
            }
        }

        // Main Text / Link Input Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 100.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BasicTextField(
                    value = textInput,
                    onValueChange = { if (it.length <= 700) textInput = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = if (textInput.length > 200) 22.sp else 32.sp,
                        fontFamily = fontFamilies[fontStyleIndex],
                        fontWeight = FontWeight.Bold,
                        textAlign = textAlignment
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (textInput.isEmpty()) {
                                Text(
                                    "Type a status...",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 32.sp,
                                    fontFamily = fontFamilies[fontStyleIndex],
                                    textAlign = textAlignment
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Link Preview Card
                if (isLoadingPreview) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else if (linkPreview != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!linkPreview?.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = linkPreview?.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = linkPreview?.title ?: linkPreview?.url ?: "",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!linkPreview?.description.isNullOrBlank()) {
                                    Text(
                                        text = linkPreview?.description ?: "",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = linkPreview?.domain ?: "",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Color Bar & Send FAB
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                fontNames[fontStyleIndex],
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable { fontStyleIndex = (fontStyleIndex + 1) % fontFamilies.size }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            FloatingActionButton(
                onClick = {
                    if (textInput.trim().isEmpty() && linkPreview == null) return@FloatingActionButton
                    isPublishing = true
                    val statusId = UUID.randomUUID().toString()
                    val type = if (linkPreview != null) "LINK" else "TEXT"

                    val workData = workDataOf(
                        "statusId" to statusId,
                        "userId" to currentUser.uid,
                        "userName" to currentUser.name,
                        "userPhoto" to currentUser.photoUrl,
                        "type" to type,
                        "text" to textInput.trim(),
                        "backgroundColor" to String.format("#%06X", 0xFFFFFF and backgroundColor.toArgb()),
                        "fontId" to fontNames[fontStyleIndex],
                        "linkUrl" to linkPreview?.url,
                        "privacyMode" to localStore.getPrivacyMode(),
                        "durationMs" to 5000L
                    )

                    val uploadWork = OneTimeWorkRequestBuilder<StatusUploadWorker>()
                        .setInputData(workData)
                        .build()

                    WorkManager.getInstance(context).enqueue(uploadWork)
                    onPublished()
                },
                containerColor = Color(0xFF25D366),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Publish Status")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoVideoStatusComposer(
    currentUser: RealUser,
    mediaUri: Uri,
    isVideo: Boolean,
    onBack: () -> Unit,
    onPublished: () -> Unit
) {
    val context = LocalContext.current
    var caption by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    val localStore = remember { StatusLocalStore.getInstance(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Media Preview
        if (isVideo) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(mediaUri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = mediaUri,
                contentDescription = "Media Preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            if (isVideo) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Video Status (Max 90s)", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Bottom Caption & Send Row
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Add a caption...", color = Color.White.copy(alpha = 0.6f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF25D366),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                FloatingActionButton(
                    onClick = {
                        isUploading = true
                        val statusId = UUID.randomUUID().toString()
                        val type = if (isVideo) "VIDEO" else "IMAGE"

                        val workData = workDataOf(
                            "statusId" to statusId,
                            "userId" to currentUser.uid,
                            "userName" to currentUser.name,
                            "userPhoto" to currentUser.photoUrl,
                            "type" to type,
                            "filePath" to mediaUri.toString(),
                            "caption" to caption.trim(),
                            "privacyMode" to localStore.getPrivacyMode(),
                            "durationMs" to if (isVideo) 30000L else 5000L
                        )

                        val uploadWork = OneTimeWorkRequestBuilder<StatusUploadWorker>()
                            .setInputData(workData)
                            .build()

                        WorkManager.getInstance(context).enqueue(uploadWork)
                        onPublished()
                    },
                    containerColor = Color(0xFF25D366),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
