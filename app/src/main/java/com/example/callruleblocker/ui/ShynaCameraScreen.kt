package com.example.callruleblocker.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "ShynaCamera"

@Composable
fun ShynaCameraScreen(
    onBack: () -> Unit,
    onMediaCaptured: (Uri, Boolean) -> Unit,
    startVideoImmediately: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    if (hasCameraPermission) {
        CameraContent(onBack, onMediaCaptured, startVideoImmediately)
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Camera permission required", color = Color.White)
        }
    }
}

@Composable
private fun CameraContent(
    onBack: () -> Unit,
    onMediaCaptured: (Uri, Boolean) -> Unit,
    startVideoImmediately: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashEnabled by remember { mutableStateOf(false) }
    
    val previewView = remember { PreviewView(context).apply { 
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    } }
    
    val imageCapture = remember { ImageCapture.Builder().build() }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        VideoCapture.withOutput(recorder)
    }
    
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0L) }

    var cameraReady by remember { mutableStateOf(false) }

    LaunchedEffect(lensFacing) {
        val cameraProviderProvider = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderProvider.get()
        
        val preview = androidx.camera.core.Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                videoCapture
            )
            cameraReady = true
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    LaunchedEffect(cameraReady) {
        if (cameraReady && startVideoImmediately && !isRecordingVideo) {
            isRecordingVideo = true
            recording = startRecording(context, videoCapture, cameraExecutor) { uri ->
                onMediaCaptured(uri, true)
            }
        }
    }

    LaunchedEffect(isRecordingVideo) {
        if (isRecordingVideo) {
            val startTime = System.currentTimeMillis()
            while (isRecordingVideo) {
                recordingDuration = System.currentTimeMillis() - startTime
                kotlinx.coroutines.delay(100)
            }
        } else {
            recordingDuration = 0L
        }
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Top Controls
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            
            IconButton(onClick = { flashEnabled = !flashEnabled }) {
                Icon(
                    if (flashEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                    null, tint = Color.White
                )
            }
        }

        // Bottom Controls
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRecordingVideo) {
                val mins = (recordingDuration / 1000) / 60
                val secs = (recordingDuration / 1000) % 60
                Text(
                    text = "%02d:%02d".format(mins, secs),
                    color = Color.Red,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Shortcut (Placeholder)
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(0.2f))) {
                    Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                }

                // Capture Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown()
                                    var isLongClick = false
                                    val longClickJob = scope.launch {
                                        delay(viewConfiguration.longPressTimeoutMillis)
                                        isLongClick = true
                                        isRecordingVideo = true
                                        recording = startRecording(context, videoCapture, cameraExecutor) { uri ->
                                            onMediaCaptured(uri, true)
                                        }
                                    }
                                    
                                    val up = waitForUpOrCancellation()
                                    longClickJob.cancel()
                                    
                                    if (isLongClick) {
                                        isRecordingVideo = false
                                        recording?.stop()
                                        recording = null
                                    } else if (up != null) {
                                        takePhoto(context, imageCapture, cameraExecutor, flashEnabled) { uri ->
                                            onMediaCaptured(uri, false)
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val scale by animateFloatAsState(if (isRecordingVideo) 1.2f else 1f)
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .border(4.dp, Color.White, CircleShape)
                            .padding(8.dp)
                    ) {
                        Box(
                            Modifier.fillMaxSize()
                                .clip(CircleShape)
                                .background(if (isRecordingVideo) Color.Red else Color.White)
                        )
                    }
                }

                // Switch Camera
                IconButton(
                    onClick = { 
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) 
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK 
                    },
                    modifier = Modifier.background(Color.White.copy(0.2f), CircleShape)
                ) {
                    Icon(Icons.Outlined.FlipCameraAndroid, null, tint = Color.White)
                }
            }
            
            Text(
                text = if (isRecordingVideo) "Recording..." else "Tap for photo, hold for video",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    flashEnabled: Boolean,
    onPhotoCaptured: (Uri) -> Unit
) {
    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ShynaGuard")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    
    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let { onPhotoCaptured(it) }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
            }
        }
    )
}

private fun startRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    executor: Executor,
    onVideoCaptured: (Uri) -> Unit
): Recording? {
    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ShynaGuard")
        }
    }

    val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(contentValues).build()

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        return videoCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)
            .start(executor) { event ->
                when(event) {
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            onVideoCaptured(event.outputResults.outputUri)
                        } else {
                            Log.e(TAG, "Video capture error: ${event.error}")
                        }
                    }
                }
            }
    }

    return videoCapture.output
        .prepareRecording(context, mediaStoreOutputOptions)
        .withAudioEnabled()
        .start(executor) { event ->
            when(event) {
                is VideoRecordEvent.Finalize -> {
                    if (!event.hasError()) {
                        onVideoCaptured(event.outputResults.outputUri)
                    } else {
                        Log.e(TAG, "Video capture error: ${event.error}")
                    }
                }
            }
        }
}
