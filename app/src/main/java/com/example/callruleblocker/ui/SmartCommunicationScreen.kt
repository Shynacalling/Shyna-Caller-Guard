package com.example.callruleblocker.ui

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.callruleblocker.AppCallActivity
import com.example.callruleblocker.call.*
import com.example.callruleblocker.data.AudioRecorder
import com.example.callruleblocker.data.SessionManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.callruleblocker.data.CloudinaryConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import android.location.Geocoder
import android.provider.ContactsContract
import android.provider.MediaStore
import android.graphics.Bitmap
import android.util.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.*
import com.example.callruleblocker.data.DiscoveryWorker
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.callruleblocker.data.StatusLocalStore
import com.google.firebase.firestore.DocumentSnapshot

private const val TAG = "ShynaCall"
private const val COMM_PREFS = "smart_communication_premium_v2"
private const val GOOGLE_WEB_CLIENT_ID = "118812641303-0ulisr49hrhaj8tflf5kq078rjmjjgne.apps.googleusercontent.com"

fun parseUserStatus(d: DocumentSnapshot): UserStatus? {
    if (!d.exists()) return null
    try {
        val obj = d.toObject(UserStatus::class.java)
        if (obj != null) {
            val mediaUrlClean = obj.mediaUrl.ifBlank { d.getString("imageUrl") ?: d.getString("videoUrl") ?: "" }
            return obj.copy(id = d.id, mediaUrl = mediaUrlClean)
        }
    } catch (e: Exception) {
        Log.w("UserStatusParser", "toObject failed for doc ${d.id}: ${e.message}. Using fallback map...")
    }

    return try {
        val typeStr = d.getString("type") ?: "IMAGE"
        val typeEnum = try {
            MessageType.valueOf(typeStr.uppercase(Locale.US))
        } catch (_: Exception) {
            when (typeStr.lowercase(Locale.US)) {
                "text" -> MessageType.TEXT
                "video", "video_status" -> MessageType.VIDEO
                "link", "link_status" -> MessageType.LINK
                else -> MessageType.IMAGE
            }
        }

        val rawTimestamp = d.get("timestamp")
        val timestampLong = when (rawTimestamp) {
            is Long -> rawTimestamp
            is Number -> rawTimestamp.toLong()
            is Timestamp -> rawTimestamp.toDate().time
            else -> System.currentTimeMillis()
        }

        val rawExpiresAt = d.get("expiresAt")
        val expiresAtLong = when (rawExpiresAt) {
            is Long -> rawExpiresAt
            is Number -> rawExpiresAt.toLong()
            is Timestamp -> rawExpiresAt.toDate().time
            else -> timestampLong + 86400000L
        }

        UserStatus(
            id = d.id,
            userId = d.getString("userId") ?: "",
            userName = d.getString("userName") ?: "User",
            userPhoto = d.getString("userPhoto"),
            mediaUrl = d.getString("mediaUrl") ?: d.getString("imageUrl") ?: d.getString("videoUrl") ?: "",
            thumbnailUrl = d.getString("thumbnailUrl"),
            caption = d.getString("caption"),
            type = typeEnum,
            backgroundColor = d.getString("backgroundColor"),
            textColor = d.getString("textColor"),
            text = d.getString("text"),
            fontId = d.getString("fontId"),
            textAlignment = d.getString("textAlignment"),
            linkUrl = d.getString("linkUrl") ?: d.getString("url"),
            linkTitle = d.getString("linkTitle") ?: d.getString("title"),
            linkDescription = d.getString("linkDescription") ?: d.getString("description"),
            linkDomain = d.getString("linkDomain") ?: d.getString("domain"),
            linkImageUrl = d.getString("linkImageUrl") ?: d.getString("previewImageUrl"),
            durationMs = d.getLong("durationMs") ?: 5000L,
            timestamp = timestampLong,
            expiresAt = expiresAtLong,
            privacyMode = d.getString("privacyMode") ?: "MY_CONTACTS",
            seenBy = (d.get("seenBy") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            likesBy = (d.get("likesBy") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            uploadState = d.getString("uploadState") ?: "PUBLISHED"
        )
    } catch (ex: Exception) {
        Log.e("UserStatusParser", "Failed manual parse for doc ${d.id}: ${ex.message}")
        null
    }
}

private object MediaUploader {
    fun upload(uri: Uri, context: Context, resourceType: String = "auto", onResult: (String?) -> Unit) {
        try {
            MediaManager.get().upload(uri)
                .unsigned(CloudinaryConfig.UPLOAD_PRESET)
                .option("resource_type", resourceType)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                        val url = resultData?.get("secure_url") as? String
                        val bytes = (resultData?.get("bytes") as? Number)?.toLong() ?: 0L
                        com.example.callruleblocker.data.NetworkUsageTracker.track(context, "media", sent = bytes)
                        
                        // For videos, ensures proper playback in some players
                        val finalUrl = if (url != null && resourceType == "video" && !url.contains(".")) "$url.mp4" else url
                        onResult(finalUrl)
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        Log.e("MediaUploader", "Upload failed: ${error?.description}")
                        onResult(null)
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                })
                .dispatch()
        } catch (err: Exception) {
            Log.e("MediaUploader", "Upload exception", err)
            onResult(null)
        }
    }
    
    fun getFileMetadata(context: Context, uri: Uri): Map<String, Any> {
        val result = mutableMapOf<String, Any>(
            "name" to "file",
            "size" to 0L,
            "mime" to "application/octet-stream"
        )
        
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIdx != -1) result["name"] = cursor.getString(nameIdx) ?: "file"
                if (sizeIdx != -1) result["size"] = cursor.getLong(sizeIdx)
            }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        result["mime"] = mime
        
        if (mime.startsWith("video")) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (duration != null) result["durationMs"] = duration.toLong()
                retriever.release()
            } catch (e: Exception) {
                Log.e("MediaUploader", "Metadata extraction failed", e)
            }
        }
        
        return result
    }
}

// Messaging models moved to MessagingModel.kt

@Composable
fun SmartCommunicationScreen(onBack: () -> Unit) {
    val mContext = LocalContext.current
    val prefs = remember { mContext.getSharedPreferences(COMM_PREFS, Context.MODE_PRIVATE) }
    var themeMode by remember { 
        mutableStateOf(
            try { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name) } 
            catch(_: Exception) { ThemeMode.DARK }
        ) 
    }

    ShynaTheme(mode = themeMode) {
        SmartCommunicationContent(
            onBack = onBack,
            themeMode = themeMode,
            onThemeChange = { themeMode = it; prefs.edit().putString("theme_mode", it.name).apply() },
            prefs = prefs
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SmartCommunicationContent(
    onBack: () -> Unit, 
    themeMode: ThemeMode, 
    onThemeChange: (ThemeMode) -> Unit,
    prefs: android.content.SharedPreferences
) {
    val mContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var currentUid by remember { mutableStateOf(auth.currentUser?.uid) }
    
    val allUsers = remember { mutableStateListOf<RealUser>() }
    var selectedPeerId by remember { mutableStateOf<String?>(null) }
    val drafts = remember { mutableStateMapOf<String, String>() }
    var selectedTab by remember { mutableStateOf(LinkTab.CHATS) }
    var search by remember { mutableStateOf("") }
    var archivedOpen by remember { mutableStateOf(false) }
    var showArchivePicker by remember { mutableStateOf(false) }
    var showSelectContact by remember { mutableStateOf(false) }
    var showGalleryByChatId by remember { mutableStateOf<String?>(null) }
    var showAudioPickerByChatId by remember { mutableStateOf<String?>(null) }
    var showCameraByChatId by remember { mutableStateOf<String?>(null) }
    var startVideoImmediately by remember { mutableStateOf(false) }
    var showLocationByChatId by remember { mutableStateOf<String?>(null) }
    var showFullDPUser by remember { mutableStateOf<RealUser?>(null) }
    var showProfileEdit by remember { mutableStateOf(false) }
    var showStarredMessages by remember { mutableStateOf(false) }
    var showStatusDetailFor by remember { mutableStateOf<String?>(null) }
    var showTextStatusComposer by remember { mutableStateOf(false) }
    var selectedMediaForStatus by remember { mutableStateOf<Uri?>(null) }
    var isMediaVideoForStatus by remember { mutableStateOf(false) }
    var showMyStatusManager by remember { mutableStateOf(false) }
    var showStatusViewerGroupIndex by remember { mutableStateOf<Int?>(null) }
    var showStatusPrivacyDialog by remember { mutableStateOf(false) }
    var showChannelDetailFor by remember { mutableStateOf<String?>(null) }
    var showFindChannels by remember { mutableStateOf(false) }
    var showSearchInChatId by remember { mutableStateOf<String?>(null) }
    var showMediaInChatId by remember { mutableStateOf<String?>(null) }
    var forwardingMessage by remember { mutableStateOf<UniversalMessage?>(null) }
    var showNewGroup by remember { mutableStateOf(false) }
    var showNewBroadcast by remember { mutableStateOf(false) }
    var showDialer by remember { mutableStateOf(false) }
    var showScheduleCall by remember { mutableStateOf(false) }
    
    var isSearchVisible by remember { mutableStateOf(false) }
    var globalSearchQuery by remember { mutableStateOf("") }

    var aiQuery by remember { mutableStateOf<String?>(null) }
    var aiResponse by remember { mutableStateOf<String?>(null) }
    var isAiLoading by remember { mutableStateOf(false) }

    var pendingMedia by remember { mutableStateOf<List<Pair<Uri, Boolean>>?>(null) }
    var pendingMediaChatId by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var showNetworkUsageOverlay by remember { mutableStateOf(false) }

    // Safety reset for isUploading
    LaunchedEffect(isUploading) {
        if (isUploading) {
            delay(60000L) // 1 minute timeout
            isUploading = false
        }
    }
    
    var pendingGalleryChatId by remember { mutableStateOf<String?>(null) }
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[android.Manifest.permission.READ_MEDIA_IMAGES] == true &&
            permissions[android.Manifest.permission.READ_MEDIA_VIDEO] == true
        } else {
            permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (granted) {
            showGalleryByChatId = pendingGalleryChatId
        } else {
            Toast.makeText(mContext, "Gallery permission is required to select media", Toast.LENGTH_LONG).show()
        }
        pendingGalleryChatId = null
    }

    var pendingAudioChatId by remember { mutableStateOf<String?>(null) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[android.Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (granted) {
            showAudioPickerByChatId = pendingAudioChatId
        } else {
            Toast.makeText(mContext, "Permission required to access audio files", Toast.LENGTH_LONG).show()
        }
        pendingAudioChatId = null
    }

    var selectedFilter by remember { mutableStateOf("All") }
    var menuExpanded by remember { mutableStateOf(false) }

    val favouriteChatIds = remember { mutableStateSetOf<String>() }
    val customLists = remember { mutableStateListOf<CustomChatList>() }
    val archivedChatIds = remember { mutableStateSetOf<String>() }
    val recentChats = remember { mutableStateListOf<ChatRowItem>() }
    val allStatuses = remember { mutableStateListOf<UserStatus>() }
    val allChannels = remember { mutableStateListOf<ShynaChannel>() }
    val chatDeletedAtMap = remember { mutableStateMapOf<String, Long>() }
    
    var showLiveLocationForMsg by remember { mutableStateOf<UniversalMessage?>(null) }
    var privacySettings by remember { mutableStateOf(UserPrivacySettings()) }
    var storageSettings by remember { mutableStateOf(UserStorageSettings()) }

    val statusImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isUploading = true
            MediaUploader.upload(it, mContext) { url ->
                isUploading = false
                if (url != null) {
                    val me = allUsers.find { it.uid == currentUid }
                    val status = UserStatus(
                        userId = currentUid!!,
                        userName = me?.name ?: "User",
                        userPhoto = me?.photoUrl,
                        mediaUrl = url,
                        type = MessageType.IMAGE,
                        timestamp = System.currentTimeMillis(),
                        expiresAt = System.currentTimeMillis() + 86400000L
                    )
                    db.collection("statuses").add(status)
                    Toast.makeText(mContext, "Status updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val statusPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedMediaForStatus = it
            val mime = mContext.contentResolver.getType(it)
            isMediaVideoForStatus = mime?.startsWith("video") == true
        }
    }

    // Load persisted data
    LaunchedEffect(currentUid) {
        val uid = currentUid ?: return@LaunchedEffect
        
        val favJson = prefs.getString("fav_chats_$uid", "[]")
        val customJson = prefs.getString("custom_lists_$uid", "[]")
        val archivedJson = prefs.getString("archived_chats_$uid", "[]")
        val gson = Gson()
        
        try {
            val favs: List<String> = gson.fromJson(favJson, object : TypeToken<List<String>>() {}.type)
            favouriteChatIds.clear()
            favouriteChatIds.addAll(favs)
            
            val lists: List<CustomChatList> = gson.fromJson(customJson, object : TypeToken<List<CustomChatList>>() {}.type)
            customLists.clear()
            customLists.addAll(lists)

            val archived: List<String> = gson.fromJson(archivedJson, object : TypeToken<List<String>>() {}.type)
            archivedChatIds.clear()
            archivedChatIds.addAll(archived)
        } catch (e: Exception) {
            Log.e("ShynaLink", "Load data failed", e)
        }

        // --- NEW: Sync Archive from Firestore for multi-device support ---
        db.collection("users").document(uid).collection("archivedChats")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    val remoteArchived = it.documents.map { d -> d.id }
                    archivedChatIds.addAll(remoteArchived)
                }
            }

        // Fetch Real Settings from Firestore
        db.collection("users").document(uid).collection("settings").document("privacy").get().addOnSuccessListener { d ->
            d?.toObject(UserPrivacySettings::class.java)?.let { privacySettings = it }
        }
        db.collection("users").document(uid).collection("settings").document("storage").get().addOnSuccessListener { d ->
            d?.toObject(UserStorageSettings::class.java)?.let { storageSettings = it }
        }
    }

    fun saveFavs() {
        val uid = currentUid ?: return
        prefs.edit().putString("fav_chats_$uid", Gson().toJson(favouriteChatIds.toList())).apply()
    }
    fun saveCustomLists() {
        val uid = currentUid ?: return
        prefs.edit().putString("custom_lists_$uid", Gson().toJson(customLists.toList())).apply()
    }
    fun saveArchived() {
        val uid = currentUid ?: return
        prefs.edit().putString("archived_chats_$uid", Gson().toJson(archivedChatIds.toList())).apply()
    }

    DisposableEffect(currentUid) {
        val uid = currentUid ?: return@DisposableEffect onDispose {}
        
        // --- PROTECTED ACCOUNT SECURITY OBSERVER ---
        // Monitor own account status for instant server-side deletion/blocking
        val ownStatusListener = db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot == null || !snapshot.exists()) {
                Log.d("ShynaCall", "Account deleted from server. Logging out.")
                auth.signOut()
                currentUid = null
                onBack() // Exit to main screen
            } else if (snapshot.getBoolean("isBlocked") == true) {
                Log.d("ShynaCall", "Account blocked by admin. Logging out.")
                auth.signOut()
                currentUid = null
                onBack()
            }
        }

        // Listen to Users (Optimized with Delta Updates)
        val userListener = db.collection("users").addSnapshotListener { snapshots, _ ->
            if (snapshots == null) return@addSnapshotListener
            
            snapshots.documentChanges.forEach { dc ->
                val d = dc.document
                val user = RealUser(
                    uid = d.id, 
                    userId = d.getString("userId") ?: "",
                    name = d.getString("name") ?: "", 
                    email = d.getString("email") ?: "", 
                    phone = d.getString("phone") ?: "",
                    isOnline = d.getBoolean("isOnline") ?: false, 
                    lastSeen = d.getTimestamp("lastSeen")?.toDate()?.time, 
                    photoUrl = d.getString("photoUrl"),
                    followedChannels = d.get("followedChannels") as? List<String> ?: emptyList(),
                    district = d.getString("district"),
                    pincode = d.getString("pincode"),
                    state = d.getString("state"),
                    country = d.getString("country")
                )

                when (dc.type) {
                    com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                        if (allUsers.none { it.uid == d.id }) {
                            allUsers.add(user)
                        }
                    }
                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                        val idx = allUsers.indexOfFirst { it.uid == d.id }
                        if (idx != -1) {
                            allUsers[idx] = user
                        } else {
                            allUsers.add(user)
                        }
                    }
                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                        allUsers.removeAll { it.uid == d.id }
                    }
                }
            }
        }

        // Listen to Chat Settings (to handle Deleted chats)
        val settingsGlobalListener = db.collection("users").document(uid).collection("chatSettings")
            .addSnapshotListener { snapshots, _ ->
                snapshots?.documents?.forEach { d ->
                    chatDeletedAtMap[d.id] = d.getTimestamp("deletedAt")?.toDate()?.time ?: 0L
                }
            }

        // Listen to Chats (Optimized with Delta Updates)
        val chatListener = db.collection("chats")
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                
                val currentList = recentChats.toMutableList()
                var listChanged = false

                snapshots.documentChanges.forEach { dc ->
                    val d = dc.document
                    val participants = d.get("participants") as? List<String> ?: emptyList()
                    val u1 = d.getString("user1") ?: ""
                    val u2 = d.getString("user2") ?: ""
                    
                    if (u1 != uid && u2 != uid && !participants.contains(uid)) return@forEach
                    
                    val isGroup = d.getBoolean("isGroup") ?: false
                    val peerUid = if (isGroup) "GROUP" else if (u1 == uid) u2 else u1
                    val lastMsg = d.getString("lastMessage") ?: ""
                    val gName = d.getString("name")
                    val timestamp = d.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                    val unread = (d.get("unreadCount_$uid") as? Number)?.toInt() ?: 0
                    val mTypeStr = d.getString("type") ?: "TEXT"
                    val mType = try { MessageType.valueOf(mTypeStr) } catch(_: Exception) { MessageType.TEXT }
                    val lastStatusStr = d.getString("lastStatus") ?: MessageStatus.SENT.name
                    val lastStatus = try { MessageStatus.valueOf(lastStatusStr) } catch(_: Exception) { MessageStatus.SENT }
                    val lastSender = d.getString("lastSenderId") ?: ""

                    val newItem = ChatRowItem(
                        id = d.id,
                        peerUid = peerUid,
                        lastMessage = lastMsg,
                        time = timestamp,
                        unreadCount = unread,
                        isPinned = favouriteChatIds.contains(d.id),
                        isGroup = isGroup,
                        messageType = mType,
                        groupName = gName,
                        lastMessageStatus = lastStatus,
                        lastMessageMine = lastSender == uid
                    )

                    when (dc.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                            val index = currentList.indexOfFirst { it.id == d.id }
                            
                            // Notification logic for modifications
                            if (dc.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED && index != -1) {
                                val old = currentList[index]
                                if (newItem.time > old.time && newItem.unreadCount > old.unreadCount) {
                                    val peerName = allUsers.find { it.uid == newItem.peerUid }?.name ?: "Shyna User"
                                    
                                    // Mute/Block Check
                                    db.collection("users").document(uid).collection("chatSettings").document(newItem.id)
                                        .get().addOnSuccessListener { d ->
                                            val mutedUntil = d.getTimestamp("mutedUntil")?.toDate()?.time ?: 0L
                                            if (mutedUntil < System.currentTimeMillis()) {
                                                db.collection("users").document(uid).collection("blockedUsers").document(newItem.peerUid)
                                                    .get().addOnSuccessListener { bd ->
                                                        if (!bd.exists()) {
                                                            showSystemNotification(mContext, "New Message from $peerName", newItem.lastMessage)
                                                        }
                                                    }
                                            }
                                        }
                                }
                            }

                            if (index != -1) currentList[index] = newItem
                            else currentList.add(newItem)
                            listChanged = true
                        }
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                            currentList.removeAll { it.id == d.id }
                            listChanged = true
                        }
                    }
                }

                if (listChanged) {
                    // Sorting on a background thread for maximum smoothness
                    scope.launch(Dispatchers.Default) {
                        val sorted = currentList.sortedByDescending { it.time }
                        withContext(Dispatchers.Main) {
                            recentChats.clear()
                            recentChats.addAll(sorted)
                        }
                    }
                }
            }

        // Listen for Real Statuses (Updates)
        val statusListener = db.collection("statuses")
            .whereGreaterThan("timestamp", System.currentTimeMillis() - 24 * 60 * 60 * 1000)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("SmartComm", "Error listening to statuses: ${error.message}")
                    return@addSnapshotListener
                }
                snapshots?.let {
                    val list = it.documents.mapNotNull { d -> parseUserStatus(d) }
                    allStatuses.clear()
                    allStatuses.addAll(list.sortedByDescending { s -> s.timestamp })
                }
            }

        // Listen for Channels
        val channelListener = db.collection("channels").addSnapshotListener { snapshots, _ ->
            snapshots?.let {
                val list = it.documents.mapNotNull { d -> d.toObject(ShynaChannel::class.java) }
                allChannels.clear()
                allChannels.addAll(list.sortedByDescending { c -> c.lastUpdateTime })
            }
        }

        onDispose {
            ownStatusListener.remove()
            userListener.remove()
            chatListener.remove()
            statusListener.remove()
            channelListener.remove()
        }
    }

    BackHandler(selectedPeerId != null || archivedOpen || showCameraByChatId != null || showLocationByChatId != null || showGalleryByChatId != null || showStarredMessages || showFullDPUser != null) { 
        if (showCameraByChatId != null) showCameraByChatId = null
        else if (showLocationByChatId != null) showLocationByChatId = null
        else if (showGalleryByChatId != null) showGalleryByChatId = null
        else if (showStarredMessages) showStarredMessages = false
        else if (showFullDPUser != null) showFullDPUser = null
        else if (showProfileEdit) showProfileEdit = false
        else if (archivedOpen) archivedOpen = false 
        else selectedPeerId = null 
    }

    var showCreateListDialog by remember { mutableStateOf(false) }

    if (showCreateListDialog) {
        CreateCustomListDialog(
            chats = recentChats,
            users = allUsers,
            onDismiss = { showCreateListDialog = false },
            onSave = { newList: CustomChatList ->
                customLists.add(newList)
                saveCustomLists()
                showCreateListDialog = false
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (showGalleryByChatId != null) {
            val targetId = showGalleryByChatId!!
            PremiumGalleryScreen(
                onBack = { showGalleryByChatId = null },
                onMediaSelected = { mediaList ->
                    pendingMedia = mediaList
                    pendingMediaChatId = targetId
                    showGalleryByChatId = null
                }
            )
        } else if (showCameraByChatId != null) {
            val targetId = showCameraByChatId!!
            ShynaCameraScreen(
                onBack = { showCameraByChatId = null; startVideoImmediately = false },
                onMediaCaptured = { uri, isVideo ->
                    pendingMedia = listOf(uri to isVideo)
                    pendingMediaChatId = targetId
                    showCameraByChatId = null
                    startVideoImmediately = false
                },
                startVideoImmediately = startVideoImmediately
            )
        } else if (showAudioPickerByChatId != null) {
            PremiumAudioPickerScreen(
                onBack = { showAudioPickerByChatId = null },
                onAudioSelected = { audioList ->
                    val uid = currentUid ?: return@PremiumAudioPickerScreen
                    val targetId = showAudioPickerByChatId ?: return@PremiumAudioPickerScreen
                    isUploading = true
                    var uploadCount = 0
                    audioList.forEach { uri ->
                        val meta = MediaUploader.getFileMetadata(mContext, uri)
                        val name = (meta["name"] as? String) ?: "Audio File"
                        val size = (meta["size"] as? Long) ?: 0L
                        val mime = (meta["mime"] as? String) ?: "audio/mpeg"
                        MediaUploader.upload(uri, mContext, "video") { url ->
                            uploadCount++
                            if (url != null) {
                                val msg = mapOf(
                                    "text" to "🎵 $name", 
                                    "senderId" to uid, 
                                    "timestamp" to Timestamp.now(), 
                                    "type" to MessageType.VOICE.name, 
                                    "metadata" to url,
                                    "fileName" to name,
                                    "fileSize" to size,
                                    "mimeType" to mime,
                                    "status" to MessageStatus.SENT.name
                                )
                                db.collection("chats").document(targetId).collection("messages").add(msg)
                            }
                            if (uploadCount == audioList.size) {
                                isUploading = false
                                db.collection("chats").document(targetId).set(mapOf("lastMessage" to "🎵 Audio", "timestamp" to Timestamp.now()), SetOptions.merge())
                            }
                        }
                    }
                    showAudioPickerByChatId = null
                }
            )
        } else if (pendingMedia != null) {
            AttachmentPreviewScreen(
                media = pendingMedia!!,
                onSend = { media: List<Pair<Uri, Boolean>>, caption: String ->
                    val targetId = pendingMediaChatId ?: return@AttachmentPreviewScreen
                    val uid = currentUid ?: return@AttachmentPreviewScreen
                    val currentPendingMedia = media.toList()
                    
                    isUploading = true
                    var uploadCount = 0
                    currentPendingMedia.forEach { (uri, isV) ->
                        val meta = MediaUploader.getFileMetadata(mContext, uri)
                        MediaUploader.upload(uri, mContext, if (isV) "video" else "image") { url ->
                            uploadCount++
                            if (url != null) {
                                val type = if (isV) MessageType.VIDEO else MessageType.IMAGE
                                val label = if (isV) "📹 Video" else "📷 Photo"
                                val msg = mutableMapOf(
                                    "text" to label, 
                                    "caption" to caption,
                                    "senderId" to uid, 
                                    "timestamp" to Timestamp.now(), 
                                    "sentAt" to System.currentTimeMillis(),
                                    "type" to type.name, 
                                    "metadata" to url,
                                    "fileName" to (meta["name"] as? String),
                                    "fileSize" to (meta["size"] as? Long ?: 0L),
                                    "mimeType" to (meta["mime"] as? String),
                                    "status" to MessageStatus.SENT.name
                                )
                                if (isV) {
                                    msg["durationMs"] = meta["durationMs"] as? Long ?: 0L
                                }
                                db.collection("chats").document(targetId).collection("messages").add(msg)
                            } else {
                                Log.e(TAG, "Failed to upload media: $uri")
                            }
                            
                            if (uploadCount == currentPendingMedia.size) {
                                isUploading = false
                                val finalLabel = if(currentPendingMedia.size > 1) "📎 Multiple Media" else if(currentPendingMedia.first().second) "📹 Video" else "📷 Photo"
                                db.collection("chats").document(targetId).set(mapOf(
                                    "lastMessage" to finalLabel, 
                                    "timestamp" to Timestamp.now(),
                                    "lastStatus" to MessageStatus.SENT.name,
                                    "lastSenderId" to uid
                                ), SetOptions.merge())
                            }
                        }
                    }
                    pendingMedia = null
                    pendingMediaChatId = null
                },
                onDismiss = { 
                    pendingMedia = null
                    pendingMediaChatId = null
                }
            )
        if (showLiveLocationForMsg != null) {
            val msg = showLiveLocationForMsg!!
            Dialog(
                onDismissRequest = { showLiveLocationForMsg = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val peerName = if (msg.isMine) "You" else (allUsers.find { it.uid == msg.senderId }?.name ?: "User")
                ViewLiveLocationScreen(
                    peerId = msg.senderId,
                    peerName = peerName,
                    expiryTime = msg.liveLocationExpiry ?: 0L,
                    onBack = { showLiveLocationForMsg = null }
                )
            }
        }

      } else if (showLocationByChatId != null) {
            val targetId = showLocationByChatId!!
            SendLocationScreen(
                onBack = { showLocationByChatId = null },
                onSendLocation = { loc ->
                    val uid = currentUid ?: return@SendLocationScreen
                    val isLive = loc.startsWith("LIVE|")
                    val type = if (isLive) MessageType.LIVE_LOCATION else MessageType.LOCATION
                    val label = if (isLive) "📍 Live Location" else "📍 Location"
                    
                    val msg = mutableMapOf<String, Any>(
                        "text" to label, 
                        "senderId" to uid, 
                        "timestamp" to Timestamp.now(), 
                        "sentAt" to System.currentTimeMillis(),
                        "type" to type.name, 
                        "metadata" to loc,
                        "status" to MessageStatus.SENT.name
                    )
                    
                    if (isLive) {
                        val expiry = loc.substringAfter("|").toLongOrNull() ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
                        msg["liveLocationExpiry"] = expiry
                        
                        // Start Background Service for Live Location
                        val intent = Intent(mContext, com.example.callruleblocker.data.LocationService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            mContext.startForegroundService(intent)
                        } else {
                            mContext.startService(intent)
                        }
                    }

                    db.collection("chats").document(targetId).collection("messages").add(msg)
                    db.collection("chats").document(targetId).set(mapOf(
                        "lastMessage" to label, 
                        "timestamp" to Timestamp.now(),
                        "lastStatus" to MessageStatus.SENT.name,
                        "lastSenderId" to uid
                    ), SetOptions.merge())
                    showLocationByChatId = null
                }
            )
        } else if (showStarredMessages) {
            StarredMessagesScreen(userId = currentUid ?: "", onBack = { showStarredMessages = false })
        } else if (currentUid == null) {
            ShynaAuthFlow(onLoginSuccess = { currentUid = auth.currentUser?.uid }, onBack = onBack)
        } else if (selectedPeerId != null) {
            SmartChatDetailScreen(
                peerId = selectedPeerId!!, 
                userId = currentUid!!, 
                allUsers = allUsers, 
                storageSettings = storageSettings,
                onBack = { selectedPeerId = null },
                onOpenCamera = { chatId, isVideo -> 
                    showCameraByChatId = chatId
                    startVideoImmediately = isVideo
                },
                onOpenLocation = { showLocationByChatId = it },
                onOpenGallery = { id -> 
                    pendingGalleryChatId = id
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    galleryPermissionLauncher.launch(permissions)
                },
                onOpenAudio = { id -> 
                    pendingAudioChatId = id
                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    audioPermissionLauncher.launch(permissions)
                },
                onAvatarClick = { showFullDPUser = it },
                drafts = drafts,
                onUploadingChange = { isUploading = it },
                initialSearchMode = showSearchInChatId == selectedPeerId,
                initialShowMedia = showMediaInChatId == selectedPeerId,
                onForward = { forwardingMessage = it },
                onOpenLiveLocation = { showLiveLocationForMsg = it }
            )
            // Reset triggers
            if (showSearchInChatId == selectedPeerId) showSearchInChatId = null
            if (showMediaInChatId == selectedPeerId) showMediaInChatId = null
        } else {
            Scaffold(
                modifier = Modifier.imePadding(),
                containerColor = ShynaDesign.colors.PrimaryBg,
                topBar = {
                    Column(Modifier.background(ShynaDesign.colors.HeaderBg).shadow(4.dp)) {
                        if (isSearchVisible) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = ShynaDesign.colors.HeaderBg,
                                tonalElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { isSearchVisible = false; globalSearchQuery = "" }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary)
                                    }
                                    PremiumSearchBar(
                                        query = globalSearchQuery,
                                        onQueryChange = { globalSearchQuery = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            if (selectedTab != LinkTab.YOU && selectedTab != LinkTab.UPDATES) {
                                val topTitle = when(selectedTab) {
                                    LinkTab.CHATS -> if(archivedOpen) "Archived" else "Shyna Link"
                                    LinkTab.COMMUNITIES -> "Shyna Calling"
                                    LinkTab.CALLS -> "Shyna Calling"
                                    else -> ""
                                }
                                TopAppBar(
                                    title = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(topTitle, fontWeight = FontWeight.ExtraBold, color = ShynaDesign.colors.TextPrimary, fontSize = 24.sp)
                                            if (!archivedOpen && (selectedTab == LinkTab.CALLS || selectedTab == LinkTab.COMMUNITIES || selectedTab == LinkTab.CHATS)) {
                                                Spacer(Modifier.width(10.dp))
                                                Icon(
                                                    Icons.Default.WorkspacePremium, 
                                                    null, 
                                                    tint = Color(0xFFE5B800), 
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Surface(
                                                    color = Color.Transparent,
                                                    shape = RoundedCornerShape(14.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFE5B800).copy(alpha = 0.6f))
                                                ) {
                                                    Text(
                                                        "Premium", 
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                                        fontSize = 11.sp, 
                                                        color = Color(0xFFE5B800),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    navigationIcon = { 
                                        if (archivedOpen) {
                                            IconButton(onClick = { archivedOpen = false }) { 
                                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) 
                                            }
                                        } 
                                    },
                                    actions = {
                                        if (!archivedOpen) {
                                            IconButton(onClick = { isSearchVisible = true }) { 
                                                Icon(Icons.Outlined.Search, null, tint = ShynaDesign.colors.TextPrimary) 
                                            }
                                        }
                                        Box {
                                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, null, tint = ShynaDesign.colors.TextPrimary) }
                                            DropdownMenu(
                                                expanded = menuExpanded,
                                                onDismissRequest = { menuExpanded = false },
                                                modifier = Modifier.background(ShynaDesign.colors.HeaderBg)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("New group", color = ShynaDesign.colors.TextPrimary) },
                                                    leadingIcon = { Icon(Icons.Outlined.GroupAdd, null, tint = ShynaDesign.colors.BrandGreen) },
                                                    onClick = { menuExpanded = false; showNewGroup = true }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("New broadcast", color = ShynaDesign.colors.TextPrimary) },
                                                    leadingIcon = { Icon(Icons.Outlined.Campaign, null, tint = ShynaDesign.colors.BrandGreen) },
                                                    onClick = { menuExpanded = false; showNewBroadcast = true }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Starred messages", color = ShynaDesign.colors.TextPrimary) },
                                                    leadingIcon = { Icon(Icons.Outlined.Star, null, tint = ShynaDesign.colors.BrandGreen) },
                                                    onClick = { menuExpanded = false; showStarredMessages = true }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Mark all as read", color = ShynaDesign.colors.TextPrimary) },
                                                    leadingIcon = { Icon(Icons.Outlined.DoneAll, null, tint = ShynaDesign.colors.BrandGreen) },
                                                    onClick = {
                                                        menuExpanded = false
                                                        val currentUid = auth.currentUser?.uid ?: return@DropdownMenuItem
                                                        db.collection("chats").get().addOnSuccessListener { snapshot ->
                                                            val batch = db.batch()
                                                            var count = 0
                                                            snapshot.documents.forEach { doc ->
                                                                val unreadKey = "unreadCount_$currentUid"
                                                                if ((doc.getLong(unreadKey) ?: 0L) > 0L) {
                                                                    batch.update(doc.reference, unreadKey, 0)
                                                                    count++
                                                                }
                                                            }
                                                            if (count > 0) {
                                                                batch.commit().addOnSuccessListener {
                                                                    Toast.makeText(mContext, "All messages marked as read", Toast.LENGTH_SHORT).show()
                                                                }
                                                            } else {
                                                                Toast.makeText(mContext, "All messages are already read", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Settings", color = ShynaDesign.colors.TextPrimary) },
                                                    leadingIcon = { Icon(Icons.Outlined.Settings, null, tint = ShynaDesign.colors.BrandGreen) },
                                                    onClick = { menuExpanded = false; selectedTab = LinkTab.YOU }
                                                )
                                                HorizontalDivider(color = ShynaDesign.colors.DividerColor)
                                                val secondaryFeatures = CallStateController.getSecondaryFeatures()
                                                secondaryFeatures.forEach { feature ->
                                                    if (feature == MainCallType.PHONE_DIALER) {
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
                                                }
                                            }
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                                )
                            }
                        }
                        if (!isSearchVisible && !archivedOpen && selectedTab == LinkTab.CHATS) {
                            PremiumSearchBar(
                                query = search, 
                                onQueryChange = { search = it }, 
                                onAiQuery = {
                                    val queryStr = it.trim()
                                    if (queryStr.isEmpty()) {
                                        Toast.makeText(mContext, "Please enter a valid question", Toast.LENGTH_SHORT).show()
                                        return@PremiumSearchBar
                                    }
                                    aiQuery = queryStr
                                    isAiLoading = true
                                    aiResponse = ""
                                    scope.launch {
                                        try {
                                            com.example.callruleblocker.data.GeminiManager.askAiStream(queryStr).collect { chunk ->
                                                isAiLoading = false
                                                aiResponse = (aiResponse ?: "") + chunk
                                            }
                                        } catch (e: Exception) {
                                            aiResponse = "AI Error: ${e.message}"
                                            isAiLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            PremiumFilterRow(
                                selectedFilter = selectedFilter,
                                customLists = customLists,
                                onFilterChange = { selectedFilter = it },
                                onAddClick = { showCreateListDialog = true },
                                onDeleteList = { name ->
                                    customLists.removeAll { it.name == name }
                                    saveCustomLists()
                                }
                            )
                            HorizontalDivider(color = ShynaDesign.colors.DividerColor, thickness = 0.5.dp)
                        }
                    }
                },
                bottomBar = { 
                    val unreadChatsCount by remember(recentChats) { derivedStateOf { recentChats.count { it.unreadCount > 0 } } }
                    PremiumBottomBar(
                        selected = selectedTab, 
                        userPhotoUrl = allUsers.find { it.uid == currentUid }?.photoUrl,
                        unreadChats = unreadChatsCount,
                        unreadUpdates = 0,
                        onSelect = { selectedTab = it }
                    )
                },
                floatingActionButton = {
                    if (selectedTab == LinkTab.CHATS && !archivedOpen) {
                        FloatingActionButton(
                            onClick = { showSelectContact = true },
                            containerColor = ShynaDesign.colors.BrandGreen,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = "New Chat")
                        }
                    } else if (selectedTab == LinkTab.CALLS) {
                        FloatingActionButton(
                            onClick = { showSelectContact = true },
                            containerColor = Color(0xFFE5B800), // Match screenshot gold
                            contentColor = Color.Black,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "New Call", modifier = Modifier.size(28.dp))
                        }
                    }
                }
            ) { p ->
                val activeQuery = if (isSearchVisible) globalSearchQuery else search
                Box(Modifier.padding(p).fillMaxSize().background(ShynaDesign.colors.PrimaryBg)) {
                    when (selectedTab) {
                        LinkTab.CHATS -> {
                            val filteredChats by remember(recentChats) {
                                derivedStateOf {
                                    recentChats.filter { it.time >= (chatDeletedAtMap[it.id] ?: 0L) }
                                }
                            }
                            ChatsList(
                                users = allUsers, 
                                query = activeQuery, 
                                filter = selectedFilter, 
                                favourites = favouriteChatIds, 
                                archived = archivedChatIds, 
                                recentChats = filteredChats, 
                                customLists = customLists, 
                                isArchivedMode = archivedOpen,
                                onOpen = { selectedPeerId = it },
                                onAvatarClick = { showFullDPUser = it },
                                onToggleFav = { id -> 
                                    if (favouriteChatIds.contains(id)) favouriteChatIds.remove(id) 
                                    else favouriteChatIds.add(id)
                                    saveFavs()
                                },
                                onArchive = { id ->
                                    val uid = currentUid ?: return@ChatsList
                                    archivedChatIds.add(id)
                                    db.collection("users").document(uid).collection("archivedChats").document(id).set(mapOf("archived" to true, "timestamp" to Timestamp.now()))
                                    saveArchived()
                                },
                                onUnarchive = { id ->
                                    val uid = currentUid ?: return@ChatsList
                                    archivedChatIds.remove(id)
                                    db.collection("users").document(uid).collection("archivedChats").document(id).delete()
                                    saveArchived()
                                },
                                onMarkUnread = { id -> 
                                    val uid = currentUid ?: return@ChatsList
                                    db.collection("chats").document(id).update("unreadCount_$uid", 1) 
                                },
                                onDeleteChat = { id -> 
                                    val uid = currentUid ?: return@ChatsList
                                    db.collection("users").document(uid).collection("chatSettings").document(id)
                                        .set(mapOf("deletedAt" to Timestamp.now()), SetOptions.merge())
                                    if (archivedChatIds.contains(id)) {
                                        archivedChatIds.remove(id)
                                        saveArchived()
                                    }
                                    if (favouriteChatIds.contains(id)) {
                                        favouriteChatIds.remove(id)
                                        saveFavs()
                                    }
                                }
                            )
                        }
                        LinkTab.UPDATES -> UpdatesPage(
                            currentUser = allUsers.find { it.uid == currentUid },
                            allUsers = allUsers,
                            statuses = allStatuses,
                            channels = allChannels,
                            onAddStatus = { statusPickerLauncher.launch("*/*") },
                            onOpenTextComposer = { showTextStatusComposer = true },
                            onOpenMyStatusManager = { showMyStatusManager = true },
                            onOpenMyStatusViewer = {
                                val activeGroups = allStatuses.filter { it.expiresAt > System.currentTimeMillis() && (it.deletedAt == null || it.deletedAt == 0L) }
                                    .groupBy { it.userId }
                                    .mapNotNull { (uId, list) ->
                                        val u = allUsers.find { it.uid == uId }
                                        val uName = u?.name ?: list.firstOrNull()?.userName ?: "Contact"
                                        val uPhoto = u?.photoUrl ?: list.firstOrNull()?.userPhoto
                                        val sortedL = list.sortedBy { it.timestamp }
                                        val unreadCount = sortedL.count { s -> !com.example.callruleblocker.data.StatusLocalStore.getInstance(mContext).isStatusSeenLocally(s.id) && !s.seenBy.contains(currentUid) }
                                        val firstUnreadIdx = sortedL.indexOfFirst { s -> !com.example.callruleblocker.data.StatusLocalStore.getInstance(mContext).isStatusSeenLocally(s.id) && !s.seenBy.contains(currentUid) }.let { if (it >= 0) it else 0 }
                                        StatusGroup(uId, uName, uPhoto, sortedL, sortedL.size, unreadCount, sortedL.lastOrNull()?.timestamp ?: 0L, firstUnreadIdx)
                                    }
                                val myIdx = activeGroups.indexOfFirst { it.userId == currentUid }
                                if (myIdx >= 0) {
                                    showStatusViewerGroupIndex = myIdx
                                } else {
                                    showMyStatusManager = true
                                }
                            },
                            onOpenStatusViewerForGroup = { selectedGroup ->
                                val activeGroups = allStatuses.filter { it.expiresAt > System.currentTimeMillis() && (it.deletedAt == null || it.deletedAt == 0L) }
                                    .groupBy { it.userId }
                                    .mapNotNull { (uId, list) ->
                                        val u = allUsers.find { it.uid == uId }
                                        val uName = u?.name ?: list.firstOrNull()?.userName ?: "Contact"
                                        val uPhoto = u?.photoUrl ?: list.firstOrNull()?.userPhoto
                                        val sortedL = list.sortedBy { it.timestamp }
                                        val unreadCount = sortedL.count { s -> !com.example.callruleblocker.data.StatusLocalStore.getInstance(mContext).isStatusSeenLocally(s.id) && !s.seenBy.contains(currentUid) }
                                        val firstUnreadIdx = sortedL.indexOfFirst { s -> !com.example.callruleblocker.data.StatusLocalStore.getInstance(mContext).isStatusSeenLocally(s.id) && !s.seenBy.contains(currentUid) }.let { if (it >= 0) it else 0 }
                                        StatusGroup(uId, uName, uPhoto, sortedL, sortedL.size, unreadCount, sortedL.lastOrNull()?.timestamp ?: 0L, firstUnreadIdx)
                                    }
                                val idx = activeGroups.indexOfFirst { it.userId == selectedGroup.userId }
                                showStatusViewerGroupIndex = if (idx >= 0) idx else 0
                            },
                            onOpenPrivacySettings = { showStatusPrivacyDialog = true },
                            onOpenChannel = { showChannelDetailFor = it.id },
                            onFindChannels = { showFindChannels = true }
                        )
                        LinkTab.COMMUNITIES -> ChatRoomMeetingsPage(
                            currentUid = currentUid ?: "",
                            allUsers = allUsers,
                            globalSearchQuery = activeQuery,
                            onStartMeeting = { mItem, isShareScreen ->
                                val uid = currentUid ?: ""
                                val currentUserProfile = allUsers.find { it.uid == uid }
                                CallSignalingManager.startMeeting(
                                    mContext,
                                    uid,
                                    currentUserProfile?.name ?: "Host",
                                    currentUserProfile?.photoUrl,
                                    mItem.meetingId,
                                    mItem.title,
                                    AppCallType.VIDEO,
                                    { created ->
                                        mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply {
                                            putExtra("callId", created.id)
                                            putExtra("isIncoming", false)
                                            putExtra("isMeeting", true)
                                            putExtra("initialMute", !mItem.hostAudioOn)
                                            putExtra("initialVideoOff", !mItem.hostVideoOn)
                                            if (isShareScreen) putExtra("autoShareScreen", true)
                                        })
                                    },
                                    { e -> Toast.makeText(mContext, "Failed to start meeting: ${e.message}", Toast.LENGTH_SHORT).show() }
                                )
                            },
                            onShareToChat = { invite ->
                                // Implementation to open chat picker
                                forwardingMessage = UniversalMessage(text = invite, messageType = MessageType.TEXT)
                            }
                        )
                        LinkTab.CALLS -> CallsPage(
                            userId = currentUid ?: "", 
                            allUsers = allUsers, 
                            searchQuery = activeQuery,
                            onNewCall = { showSelectContact = true },
                            onOpenDialer = { showDialer = true },
                            onOpenSchedule = { showScheduleCall = true },
                            onStartMeeting = { mItem, isShareScreen ->
                                val uid = currentUid ?: ""
                                val currentUserProfile = allUsers.find { it.uid == uid }
                                CallSignalingManager.startMeeting(
                                    mContext,
                                    uid,
                                    currentUserProfile?.name ?: "Host",
                                    currentUserProfile?.photoUrl,
                                    mItem.meetingId,
                                    mItem.title,
                                    AppCallType.VIDEO,
                                    { created ->
                                        mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply {
                                            putExtra("callId", created.id)
                                            putExtra("isIncoming", false)
                                            putExtra("isMeeting", true)
                                            putExtra("initialMute", !mItem.hostAudioOn)
                                            putExtra("initialVideoOff", !mItem.hostVideoOn)
                                            if (isShareScreen) putExtra("autoShareScreen", true)
                                        })
                                    },
                                    { e -> Toast.makeText(mContext, "Failed to start meeting: ${e.message}", Toast.LENGTH_SHORT).show() }
                                )
                            },
                            onShareMeeting = { invite ->
                                forwardingMessage = UniversalMessage(text = invite, messageType = MessageType.TEXT)
                            }
                        )
                        LinkTab.YOU -> {
                            val currentUser = allUsers.find { it.uid == currentUid }
                            if (showProfileEdit && currentUser != null) {
                                ProfileEditScreen(
                                    user = currentUser,
                                    onBack = { showProfileEdit = false },
                                    onUpdateName = { newName: String ->
                                        db.collection("users").document(currentUid!!).update("name", newName)
                                    },
                                    onUpdatePhoto = { uri: Uri ->
                                        isUploading = true
                                        MediaUploader.upload(uri, mContext) { url ->
                                            isUploading = false
                                            if (url != null) {
                                                db.collection("users").document(currentUid!!).update("photoUrl", url)
                                            }
                                        }
                                    },
                                    onChangePhone = { newPhone: String ->
                                        // WhatsApp style: require email verification for sensitive changes
                                        auth.currentUser?.sendEmailVerification()?.addOnSuccessListener {
                                            Toast.makeText(mContext, "Verification email sent to ${auth.currentUser?.email}. Verify to enable phone change.", Toast.LENGTH_LONG).show()
                                            // In a real app, we'd listen for auth state change or wait for next login
                                            // For now, we update if they are already verified or just show the process
                                            if (auth.currentUser?.isEmailVerified == true) {
                                                db.collection("users").document(currentUid!!).update("phone", newPhone)
                                                Toast.makeText(mContext, "Phone number updated", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            } else {
                                YouPage(
                                    user = currentUser, 
                                    mode = themeMode, 
                                    privacy = privacySettings,
                                    storage = storageSettings,
                                    onThemeChange = onThemeChange, 
                                    onUpdatePrivacy = { privacySettings = it; db.collection("users").document(currentUid!!).collection("settings").document("privacy").set(it) },
                                    onUpdateStorage = { storageSettings = it; db.collection("users").document(currentUid!!).collection("settings").document("storage").set(it) },
                                    onLogout = { auth.signOut(); onBack() },
                                    onOpenStarred = { showStarredMessages = true },
                                    onEditProfile = { showProfileEdit = true },
                                    onOpenNetworkUsage = { showNetworkUsageOverlay = true }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showNetworkUsageOverlay) {
            Box(Modifier.fillMaxSize().background(ShynaDesign.colors.PrimaryBg).clickable(enabled = false) {}) {
                NetworkUsageScreen(onBack = { showNetworkUsageOverlay = false })
            }
        }

        val currentUserObj = allUsers.find { it.uid == currentUid }
        val effectiveUser = currentUserObj ?: RealUser(
            uid = currentUid ?: "",
            name = auth.currentUser?.displayName ?: "User",
            email = auth.currentUser?.email ?: ""
        )

        if (showStatusPrivacyDialog) {
            StatusPrivacyDialog(onDismiss = { showStatusPrivacyDialog = false })
        }

        if (showTextStatusComposer) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
                TextAndLinkStatusComposer(
                    currentUser = effectiveUser,
                    onBack = { showTextStatusComposer = false },
                    onPublished = {
                        showTextStatusComposer = false
                        Toast.makeText(mContext, "Status published!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        if (selectedMediaForStatus != null) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
                PhotoVideoStatusComposer(
                    currentUser = effectiveUser,
                    mediaUri = selectedMediaForStatus!!,
                    isVideo = isMediaVideoForStatus,
                    onBack = { selectedMediaForStatus = null },
                    onPublished = {
                        selectedMediaForStatus = null
                        Toast.makeText(mContext, "Status publishing...", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        if (showMyStatusManager) {
            val myStatuses = allStatuses.filter { it.userId == currentUid && it.expiresAt > System.currentTimeMillis() && (it.deletedAt == null || it.deletedAt == 0L) }
            Box(Modifier.fillMaxSize().background(ShynaDesign.colors.PrimaryBg).clickable(enabled = false) {}) {
                MyStatusManagementScreen(
                    currentUser = effectiveUser,
                    userStatuses = myStatuses,
                    onBack = { showMyStatusManager = false },
                    onAddMoreStatus = {
                        showMyStatusManager = false
                        statusPickerLauncher.launch("*/*")
                    },
                    onViewStatus = { status ->
                        val activeGroups = allStatuses.filter { it.expiresAt > System.currentTimeMillis() && (it.deletedAt == null || it.deletedAt == 0L) }
                            .groupBy { it.userId }
                            .mapNotNull { (uId, list) ->
                                val u = allUsers.find { it.uid == uId }
                                val uName = u?.name ?: list.firstOrNull()?.userName ?: "Contact"
                                val uPhoto = u?.photoUrl ?: list.firstOrNull()?.userPhoto
                                val sortedL = list.sortedBy { it.timestamp }
                                val unreadCount = sortedL.count { s -> !com.example.callruleblocker.data.StatusLocalStore.getInstance(mContext).isStatusSeenLocally(s.id) && !s.seenBy.contains(currentUid) }
                                val firstUnreadIdx = sortedL.indexOfFirst { s -> !com.example.callruleblocker.data.StatusLocalStore.getInstance(mContext).isStatusSeenLocally(s.id) && !s.seenBy.contains(currentUid) }.let { if (it >= 0) it else 0 }
                                StatusGroup(uId, uName, uPhoto, sortedL, sortedL.size, unreadCount, sortedL.lastOrNull()?.timestamp ?: 0L, firstUnreadIdx)
                            }
                        val idx = activeGroups.indexOfFirst { it.userId == currentUid }
                        showMyStatusManager = false
                        showStatusViewerGroupIndex = if (idx >= 0) idx else 0
                    }
                )
            }
        }

        if (showStatusViewerGroupIndex != null) {
            val activeGroups = allStatuses.filter { it.expiresAt > System.currentTimeMillis() && (it.deletedAt == null || it.deletedAt == 0L) }
                .groupBy { it.userId }
                .mapNotNull { (uId, list) ->
                    val u = allUsers.find { it.uid == uId }
                    val uName = u?.name ?: list.firstOrNull()?.userName ?: "Contact"
                    val uPhoto = u?.photoUrl ?: list.firstOrNull()?.userPhoto
                    val sortedL = list.sortedBy { it.timestamp }
                    val unreadCount = sortedL.count { s -> !com.example.callruleblocker.data.StatusLocalStore.getInstance(mContext).isStatusSeenLocally(s.id) && !s.seenBy.contains(currentUid) }
                    val firstUnreadIdx = sortedL.indexOfFirst { s -> !com.example.callruleblocker.data.StatusLocalStore.getInstance(mContext).isStatusSeenLocally(s.id) && !s.seenBy.contains(currentUid) }.let { if (it >= 0) it else 0 }
                    StatusGroup(uId, uName, uPhoto, sortedL, sortedL.size, unreadCount, sortedL.lastOrNull()?.timestamp ?: 0L, firstUnreadIdx)
                }

            if (activeGroups.isNotEmpty()) {
                Box(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
                    FullscreenStatusViewerScreen(
                        groups = activeGroups,
                        initialGroupIndex = showStatusViewerGroupIndex!!.coerceIn(0, activeGroups.size - 1),
                        currentUser = effectiveUser,
                        onClose = { showStatusViewerGroupIndex = null }
                    )
                }
            } else {
                showStatusViewerGroupIndex = null
            }
        }

        if (showChannelDetailFor != null) {
            val channel = allChannels.find { it.id == showChannelDetailFor }
            if (channel != null) {
                ChannelDetailScreen(
                    channel = channel,
                    onBack = { showChannelDetailFor = null },
                    onJoin = {
                        db.collection("channels").document(channel.id).update("followersCount", FieldValue.increment(1))
                        Toast.makeText(mContext, "Joined ${channel.name}", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                showChannelDetailFor = null
            }
        }

        if (showFindChannels) {
            FindChannelsScreen(
                onBack = { showFindChannels = false },
                onOpenChannel = { 
                    showChannelDetailFor = it.id
                    showFindChannels = false
                }
            )
        }

        if (showDialer) {
        DialerScreen(onBack = { showDialer = false })
    } else if (showScheduleCall) {
        ScheduleCallScreen(onBack = { showScheduleCall = false })
    } else if (showSelectContact) {
        if (selectedTab == LinkTab.CALLS) {
            NewCallScreen(allUsers = allUsers, onBack = { showSelectContact = false })
        } else {
            SelectContactDialog(
                users = allUsers,
                onDismiss = { showSelectContact = false },
                onSelect = { user: RealUser ->
                    showSelectContact = false
                    selectedPeerId = user.uid
                }
            )
        }
    }

        if (showNewGroup) {
            val currentUser = allUsers.find { it.uid == currentUid }
            if (currentUser != null) {
                NewGroupScopedScreen(
                    peer = currentUser,
                    allUsers = allUsers,
                    onBack = { showNewGroup = false },
                    onCreate = { name, members ->
                        // Handle group creation logic here or in the screen
                        showNewGroup = false
                        Toast.makeText(mContext, "Group $name created", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                showNewGroup = false
            }
        }

        if (showNewBroadcast) {
            val uid = currentUid ?: auth.currentUser?.uid ?: ""
            NewBroadcastScreen(
                currentUid = uid,
                allUsers = allUsers,
                onBack = { showNewBroadcast = false },
                onCreate = { name, memberUids ->
                    val broadcastId = UUID.randomUUID().toString()
                    val data = mapOf(
                        "id" to broadcastId,
                        "name" to name,
                        "participants" to memberUids,
                        "broadcastRecipients" to memberUids.filter { it != uid },
                        "isBroadcast" to true,
                        "createdBy" to uid,
                        "timestamp" to Timestamp.now(),
                        "lastMessage" to "Broadcast list created",
                        "user1" to uid,
                        "user2" to "BROADCAST"
                    )
                    db.collection("chats").document(broadcastId).set(data)
                    
                    val sysMsg = mapOf(
                        "text" to "You created a broadcast list with ${memberUids.size - 1} recipients",
                        "senderId" to "SYSTEM",
                        "timestamp" to Timestamp.now(),
                        "type" to MessageType.SYSTEM.name
                    )
                    db.collection("chats").document(broadcastId).collection("messages").add(sysMsg)
                    
                    showNewBroadcast = false
                    Toast.makeText(mContext, "Broadcast list created", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showArchivePicker) {
            AlertDialog(
                onDismissRequest = { showArchivePicker = false },
                title = { Text("Archive Chats", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
                containerColor = ShynaDesign.colors.HeaderBg,
                text = {
                    val nonArchived = recentChats.filter { !archivedChatIds.contains(it.id) }
                    if (nonArchived.isEmpty()) {
                        Text("No chats available to archive", color = ShynaDesign.colors.TextSecondary)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 400.dp)) {
                            items(nonArchived) { chat ->
                                val peer = allUsers.find { it.uid == chat.peerUid }
                                peer?.let {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                archivedChatIds.add(chat.id)
                                                saveArchived()
                                                showArchivePicker = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(shape = CircleShape, modifier = Modifier.size(40.dp), color = ShynaDesign.colors.DividerColor) {
                                            if (!it.photoUrl.isNullOrBlank()) AsyncImage(it.photoUrl, null, contentScale = ContentScale.Crop)
                                            else Box(contentAlignment = Alignment.Center) { Text(it.name.take(1).uppercase(), color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold) }
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Text(it.name, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showArchivePicker = false }) { Text("Close", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold) }
                }
            )
        }


        if (showFullDPUser != null) {
            PeerDetailScreen(
                user = showFullDPUser!!,
                allUsers = allUsers,
                db = db,
                auth = auth,
                onBack = { showFullDPUser = null },
                onMessage = { 
                    selectedPeerId = showFullDPUser!!.uid
                    showFullDPUser = null
                },
                onSearchInChat = {
                    selectedPeerId = showFullDPUser!!.uid
                    showSearchInChatId = showFullDPUser!!.uid
                    showFullDPUser = null
                },
                onMediaClick = {
                    selectedPeerId = showFullDPUser!!.uid
                    showMediaInChatId = showFullDPUser!!.uid
                    showFullDPUser = null
                }
            )
        }

        if (aiQuery != null) {
            AlertDialog(
                onDismissRequest = { aiQuery = null; aiResponse = null },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = ShynaDesign.colors.BrandGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Shyna AI", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                    }
                },
                text = {
                    Column {
                        Text("Query: $aiQuery", style = MaterialTheme.typography.labelMedium, color = ShynaDesign.colors.TextSecondary)
                        Spacer(Modifier.height(16.dp))
                        if (isAiLoading) {
                            CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen)
                        } else {
                            Text(aiResponse ?: "No response from AI.", color = ShynaDesign.colors.TextPrimary)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { aiQuery = null; aiResponse = null }) {
                        Text("Close", color = ShynaDesign.colors.BrandGreen)
                    }
                },
                containerColor = ShynaDesign.colors.SurfaceBg
            )
        }

        if (isUploading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = RoundedCornerShape(20.dp), color = ShynaDesign.colors.SurfaceBg, shadowElevation = 12.dp) {
                    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen, strokeWidth = 3.dp)
                        Spacer(Modifier.height(20.dp))
                        Text("Uploading media...", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Please wait", color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (forwardingMessage != null) {
            ForwardChatPicker(
                msg = forwardingMessage!!,
                chats = recentChats,
                allUsers = allUsers,
                onDismiss = { forwardingMessage = null },
                onSend = { targetChatId, peerUid ->
                    val m = forwardingMessage!!
                    val newMsg = mutableMapOf(
                        "text" to m.text,
                        "senderId" to currentUid!!,
                        "timestamp" to Timestamp.now(),
                        "sentAt" to System.currentTimeMillis(),
                        "type" to m.messageType.name,
                        "isForwarded" to true,
                        "metadata" to m.metadata,
                        "fileName" to m.fileName,
                        "fileSize" to m.fileSize,
                        "mimeType" to m.mimeType,
                        "status" to MessageStatus.SENT.name
                    )
                    db.collection("chats").document(targetChatId).collection("messages").add(newMsg)
                    db.collection("chats").document(targetChatId).set(mapOf("lastMessage" to m.text, "timestamp" to Timestamp.now()), SetOptions.merge())
                    forwardingMessage = null
                    selectedPeerId = peerUid // Optionally open the chat
                    Toast.makeText(mContext, "Message forwarded", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun ForwardChatPicker(
    @Suppress("UNUSED_PARAMETER") msg: UniversalMessage,
    chats: List<ChatRowItem>,
    allUsers: List<RealUser>,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward message", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(chats) { chat ->
                    val peer = allUsers.find { it.uid == chat.peerUid }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSend(chat.id, chat.peerUid) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, modifier = Modifier.size(40.dp), color = ShynaDesign.colors.DividerColor) {
                            if (!peer?.photoUrl.isNullOrBlank()) AsyncImage(peer?.photoUrl, null, contentScale = ContentScale.Crop)
                            else Box(contentAlignment = Alignment.Center) { Text(peer?.name?.take(1)?.uppercase() ?: "?", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(peer?.name ?: "Unknown", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun PremiumSearchBar(
    query: String, 
    onQueryChange: (String) -> Unit, 
    onAiQuery: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp), 
        color = ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query, onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = ShynaDesign.colors.TextPrimary, fontSize = 17.sp),
                decorationBox = { if (query.isEmpty()) Text(if(onAiQuery != null) "Ask Shyna AI (Gemini)" else "Search...", color = ShynaDesign.colors.TextSecondary); it() }
            )
            if (onAiQuery != null) {
                IconButton(onClick = { if (query.isNotBlank()) onAiQuery(query) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun PremiumFilterRow(
    selectedFilter: String,
    customLists: List<CustomChatList>,
    onFilterChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onDeleteList: (String) -> Unit
) {
    val mainFilters = listOf("All", "Unread", "Favourites", "Groups")
    var showPlusMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        mainFilters.forEach { f ->
            val isSelected = f == selectedFilter
            FilterChipCompact(
                label = f,
                isSelected = isSelected,
                onClick = { onFilterChange(f) }
            )
        }

        // The "+" Chip for Custom Folders
        Box {
            Surface(
                modifier = Modifier
                    .size(width = 40.dp, height = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showPlusMenu = true },
                shape = RoundedCornerShape(16.dp),
                color = ShynaDesign.colors.SurfaceBg,
                border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            DropdownMenu(
                expanded = showPlusMenu,
                onDismissRequest = { showPlusMenu = false },
                containerColor = ShynaDesign.colors.SurfaceBg
            ) {
                DropdownMenuItem(
                    text = { Text("New List", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen) },
                    onClick = { showPlusMenu = false; onAddClick() },
                    leadingIcon = { Icon(Icons.Default.AddCircle, null, tint = ShynaDesign.colors.BrandGreen) }
                )
                if (customLists.isNotEmpty()) HorizontalDivider(color = ShynaDesign.colors.DividerColor)
                customLists.forEach { list ->
                    val isListSelected = selectedFilter == list.name
                    DropdownMenuItem(
                        text = { Text(list.name, color = if(isListSelected) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextPrimary) },
                        onClick = { 
                            showPlusMenu = false
                            onFilterChange(list.name)
                        },
                        trailingIcon = {
                            IconButton(onClick = { onDeleteList(list.name); if(isListSelected) onFilterChange("All") }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f), modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipCompact(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) ShynaDesign.colors.BrandGreen.copy(0.1f) else ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, if (isSelected) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.DividerColor)
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (isSelected) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PremiumBottomBar(
    selected: LinkTab, 
    userPhotoUrl: String?, 
    unreadChats: Int,
    unreadUpdates: Int,
    onSelect: (LinkTab) -> Unit
) {
    NavigationBar(containerColor = ShynaDesign.colors.HeaderBg, tonalElevation = 8.dp) {
        LinkTab.entries.forEach { tab ->
            val isSelected = selected == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { 
                    BadgedBox(badge = {
                        if (tab == LinkTab.CHATS && unreadChats > 0) {
                            Badge(containerColor = ShynaDesign.colors.BrandGreen, modifier = Modifier.offset(x = (-4).dp, y = 4.dp)) { 
                                Text(unreadChats.toString(), color = Color.White, fontSize = 10.sp) 
                            }
                        } else if (tab == LinkTab.UPDATES && unreadUpdates > 0) {
                            Badge(containerColor = ShynaDesign.colors.BrandGreen, modifier = Modifier.offset(x = (-4).dp, y = 4.dp)) { 
                                Text(unreadUpdates.toString(), color = Color.White, fontSize = 10.sp) 
                            }
                        } else if (tab == LinkTab.COMMUNITIES) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF25D366), CircleShape).border(1.dp, ShynaDesign.colors.HeaderBg, CircleShape).offset(x = 12.dp, y = (-4).dp))
                        }
                    }) {
                        if (tab == LinkTab.YOU) {
                            Surface(Modifier.size(28.dp), shape = CircleShape, border = if(isSelected) BorderStroke(2.dp, ShynaDesign.colors.BrandGreen) else null) {
                                if (!userPhotoUrl.isNullOrBlank()) AsyncImage(userPhotoUrl, null, contentScale = ContentScale.Crop)
                                else Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.TextSecondary)
                            }
                        } else {
                            Icon(getTabIcon(tab, isSelected), tab.name, modifier = Modifier.size(28.dp))
                        }
                    }
                },
                label = { Text(getTabLabel(tab), fontSize = 12.sp, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ShynaDesign.colors.BrandGreen,
                    selectedTextColor = ShynaDesign.colors.BrandGreen,
                    unselectedIconColor = ShynaDesign.colors.TextSecondary,
                    unselectedTextColor = ShynaDesign.colors.TextSecondary,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

private fun getTabLabel(tab: LinkTab) = when(tab) {
    LinkTab.CHATS -> "Chats"
    LinkTab.UPDATES -> "Updates"
    LinkTab.COMMUNITIES -> "Chat Room"
    LinkTab.CALLS -> "Calls"
    LinkTab.YOU -> "You"
}

private fun getTabIcon(tab: LinkTab, selected: Boolean) = when(tab) {
    LinkTab.CHATS -> if (selected) Icons.Default.ChatBubble else Icons.Outlined.ChatBubbleOutline
    LinkTab.UPDATES -> if (selected) Icons.Default.DonutLarge else Icons.Outlined.DonutLarge
    LinkTab.COMMUNITIES -> if (selected) Icons.Default.Groups else Icons.Outlined.Groups
    LinkTab.CALLS -> if (selected) Icons.Default.Call else Icons.Outlined.Call
    LinkTab.YOU -> if (selected) Icons.Default.Person else Icons.Outlined.Person
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val d1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val d2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return d1.get(Calendar.YEAR) == d2.get(Calendar.YEAR) && d1.get(Calendar.DAY_OF_YEAR) == d2.get(Calendar.DAY_OF_YEAR)
}

private fun openMessageItem(
    context: Context,
    scope: CoroutineScope,
    m: UniversalMessage,
    onMediaClick: (UniversalMessage) -> Unit = {},
    onOpenContact: (UniversalMessage) -> Unit = {},
    onOpenLiveLocation: (UniversalMessage) -> Unit = {}
) {
    if (m.deleteForEveryone) return

    when (m.messageType) {
        MessageType.IMAGE, MessageType.VIDEO -> {
            onMediaClick(m)
        }
        MessageType.DOC -> {
            DocInteraction.downloadAndOpen(context, scope, m)
        }
        MessageType.LOCATION, MessageType.LIVE_LOCATION -> {
            if (m.messageType == MessageType.LIVE_LOCATION) {
                onOpenLiveLocation(m)
                return
            }
            val loc = m.metadata ?: "0,0"
            val parts = loc.substringBefore("|").split(",")
            val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val label = m.fileName ?: m.caption ?: "Shared Location"

            val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(mapIntent)
            } catch (e: Exception) {
                val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, browserUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }.onFailure {
                    Toast.makeText(context, "Cannot open map", Toast.LENGTH_SHORT).show()
                }
            }
        }
        MessageType.LINK -> {
            var url = m.text.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            }.onFailure {
                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
            }
        }
        MessageType.CONTACT -> {
            onOpenContact(m)
        }
        else -> {
            val textOrMeta = (m.metadata ?: m.text).trim()
            if (textOrMeta.startsWith("http://") || textOrMeta.startsWith("https://")) {
                val ext = textOrMeta.substringBefore("?").substringAfterLast(".", "").lowercase()
                if (ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "apk", "zip", "m4a", "mp3")) {
                    DocInteraction.downloadAndOpen(context, scope, m)
                } else {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(textOrMeta)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SmartChatDetailScreen(
    peerId: String, 
    userId: String, 
    allUsers: List<RealUser>, 
    storageSettings: UserStorageSettings,
    onBack: () -> Unit,
    onOpenCamera: (String, Boolean) -> Unit,
    onOpenLocation: (String) -> Unit,
    onOpenGallery: (String) -> Unit,
    onOpenAudio: (String) -> Unit,
    onAvatarClick: (RealUser) -> Unit,
    drafts: MutableMap<String, String>,
    onUploadingChange: (Boolean) -> Unit,
    initialSearchMode: Boolean = false,
    initialShowMedia: Boolean = false,
    onForward: (UniversalMessage) -> Unit = {},
    onOpenLiveLocation: (UniversalMessage) -> Unit = {}
) {
    val db = FirebaseFirestore.getInstance()
    val peer = allUsers.find { it.uid == peerId }
    val currentUserProfile = allUsers.find { it.uid == userId }
    val chatId = if (userId < peerId) "${userId}_${peerId}" else "${peerId}_${userId}"
    var text by remember { mutableStateOf(drafts[chatId] ?: "") }
    val msgs = remember { mutableStateListOf<UniversalMessage>() }
    val listState = rememberLazyListState()
    val selectedMsgs = remember { mutableStateListOf<String>() }
    val isSelectionMode by remember { derivedStateOf { selectedMsgs.isNotEmpty() } }
    val mContext = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showAttachments by remember { mutableStateOf(false) }
    var showEmojis by remember { mutableStateOf(false) }
    var fullScreenMedia by remember { mutableStateOf<UniversalMessage?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showEventDialog by remember { mutableStateOf(false) }
    var showMediaScoped by remember { mutableStateOf(initialShowMedia) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showNewGroupScoped by remember { mutableStateOf(false) }
    var showRsvpFor by remember { mutableStateOf<UniversalMessage?>(null) }
    
    var pendingDocument by remember { mutableStateOf<Map<String, Any>?>(null) }
    var pendingContactInfo by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showContactDetailsFor by remember { mutableStateOf<UniversalMessage?>(null) }

    Box(Modifier.fillMaxSize()) {
        val onVote: (UniversalMessage, Int) -> Unit = { m, index ->
            val attempts = (m.interactionAttempts[userId] ?: 0)
            val firstTime = m.firstInteractionTime[userId] ?: m.lastInteractionTime[userId] ?: 0L
            val now = System.currentTimeMillis()
            
            val isTimeLocked = firstTime > 0L && (now - firstTime >= 30 * 60 * 1000L)

            if (attempts >= 5) {
                Toast.makeText(mContext, "Maximum 5 attempts reached. Vote is final.", Toast.LENGTH_SHORT).show()
            } else if (isTimeLocked) {
                Toast.makeText(mContext, "Voting locked (30 min time limit reached)", Toast.LENGTH_SHORT).show()
            } else {
                val votes = m.pollVotes.toMutableMap()
                val key = index.toString()
                
                val newAttempts = attempts + 1
                val newFirstTime = if (firstTime == 0L) now else firstTime
                
                if (m.allowMultipleAnswers) {
                    val list = (votes[key] ?: emptyList()).toMutableList()
                    if (list.contains(userId)) list.remove(userId) else list.add(userId)
                    votes[key] = list
                } else {
                    votes.keys.forEach { k ->
                        val l = (votes[k] ?: emptyList()).toMutableList()
                        if (l.contains(userId)) {
                            l.remove(userId)
                            votes[k] = l
                        }
                    }
                    val newList = (votes[key] ?: emptyList()).toMutableList()
                    newList.add(userId)
                    votes[key] = newList
                }
                
                db.collection("chats").document(chatId).collection("messages").document(m.id).update(
                    "pollVotes", votes,
                    "interactionAttempts.$userId", newAttempts,
                    "firstInteractionTime.$userId", newFirstTime,
                    "lastInteractionTime.$userId", now
                )
            }
        }

        val onRSVP: (UniversalMessage, String) -> Unit = { m, status ->
            val attempts = (m.interactionAttempts[userId] ?: 0)
            val firstTime = m.firstInteractionTime[userId] ?: m.lastInteractionTime[userId] ?: 0L
            val now = System.currentTimeMillis()
            
            val isTimeLocked = firstTime > 0L && (now - firstTime >= 30 * 60 * 1000L)

            if (attempts >= 5) {
                Toast.makeText(mContext, "Maximum 5 attempts reached. RSVP is final.", Toast.LENGTH_SHORT).show()
            } else if (isTimeLocked) {
                Toast.makeText(mContext, "RSVP locked (30 min time limit reached)", Toast.LENGTH_SHORT).show()
            } else {
                val rsvps = m.eventRSVPs.toMutableMap()
                val newAttempts = attempts + 1
                val newFirstTime = if (firstTime == 0L) now else firstTime
                
                listOf("going", "maybe", "not_going").forEach { s ->
                    val l = (rsvps[s] ?: emptyList()).toMutableList()
                    if (l.contains(userId)) {
                        l.remove(userId)
                        rsvps[s] = l
                    }
                }
                val list = (rsvps[status] ?: emptyList()).toMutableList()
                if (!list.contains(userId)) list.add(userId)
                rsvps[status] = list
                
                db.collection("chats").document(chatId).collection("messages").document(m.id).update(
                    "eventRSVPs", rsvps,
                    "interactionAttempts.$userId", newAttempts,
                    "firstInteractionTime.$userId", newFirstTime,
                    "lastInteractionTime.$userId", now
                )
            }
            showRsvpFor = null
        }


    var userClearedAt by remember { mutableLongStateOf(0L) }
    var userDeletedAt by remember { mutableLongStateOf(0L) }
    
    var isSearchMode by remember { mutableStateOf(initialSearchMode) }
    var searchChatQuery by remember { mutableStateOf("") }
    var searchResultsIndices = remember(searchChatQuery, msgs.size) {
        if (searchChatQuery.isEmpty()) emptyList<Int>()
        else msgs.indices.filter { msgs[it].text.contains(searchChatQuery, ignoreCase = true) }
    }
    var currentSearchMatchIndex by remember { mutableIntStateOf(-1) }

    var menuExpanded by remember { mutableStateOf(false) }
    
    var isPeerBlocked by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }

    var replyingTo by remember { mutableStateOf<UniversalMessage?>(null) }
    var editingMessage by remember { mutableStateOf<UniversalMessage?>(null) }

    var isTyping by remember { mutableStateOf(false) }
    
    // Draft Sync
    LaunchedEffect(text) {
        if (text.isNotEmpty()) drafts[chatId] = text
        else drafts.remove(chatId)
    }

    // Scroll to bottom logic
    // Auto-scroll to bottom handled by reverseLayout = true in LazyColumn
    LaunchedEffect(msgs.size, text) {
        if (msgs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    if (showRsvpFor != null) {
        val m = showRsvpFor!!
        val attempts = m.interactionAttempts[userId] ?: 0
        val firstTime = m.firstInteractionTime[userId] ?: m.lastInteractionTime[userId] ?: 0L
        val now = System.currentTimeMillis()
        val isTimeLocked = firstTime > 0L && (now - firstTime >= 30 * 60 * 1000L)
        val isBlocked = attempts >= 5 || isTimeLocked

        val remainingChances = (5 - attempts).coerceAtLeast(0)
        val statusMsg = when {
            attempts >= 5 -> "Maximum 5 attempts reached. RSVP is final."
            isTimeLocked -> "RSVP locked (30 min time limit reached)."
            else -> "Are you attending this event? ($remainingChances chances left)"
        }

        val rsvpOptions = listOf(
            "going" to ("GOING" to ShynaDesign.colors.BrandGreen),
            "maybe" to ("MAYBE" to Color(0xFFFFB300)),
            "not_going" to ("NOT GOING" to Color(0xFFE53935))
        )

        AlertDialog(
            onDismissRequest = { showRsvpFor = null },
            title = { Text(m.eventTitle ?: "Event RSVP", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
            text = {
                Column {
                    Text(statusMsg, color = ShynaDesign.colors.TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    rsvpOptions.forEach { (status, optionData) ->
                        val (label, btnColor) = optionData
                        Button(
                            onClick = { onRSVP(m, status) },
                            enabled = !isBlocked,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = btnColor)
                        ) {
                            Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
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
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, ShynaDesign.colors.BrandGreen)
                    ) {
                        Icon(Icons.Default.CalendarToday, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add to Calendar", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showRsvpFor = null }) { Text("Close", color = ShynaDesign.colors.BrandGreen) } },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    if (pendingDocument != null) {
        val doc = pendingDocument!!
        val uri = doc["uri"] as Uri
        val name = doc["name"] as String
        val size = doc["size"] as Long
        val mime = doc["mime"] as String
        val sizeStr = android.text.format.Formatter.formatFileSize(mContext, size)

        AlertDialog(
            onDismissRequest = { pendingDocument = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Send Document", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(name, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${name.substringAfterLast(".").uppercase()} • $sizeStr", color = ShynaDesign.colors.TextSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDocument = null
                        onUploadingChange(true)
                        MediaUploader.upload(uri, mContext, "raw") { url ->
                            onUploadingChange(false)
                            if (url != null) {
                                val msg = mapOf(
                                    "text" to name, 
                                    "senderId" to userId, 
                                    "timestamp" to Timestamp.now(), 
                                    "sentAt" to System.currentTimeMillis(),
                                    "type" to MessageType.DOC.name, 
                                    "metadata" to url,
                                    "fileName" to name,
                                    "fileSize" to size,
                                    "mimeType" to mime,
                                    "status" to MessageStatus.SENT.name
                                )
                                db.collection("chats").document(chatId).collection("messages").add(msg)
                                db.collection("chats").document(chatId).set(mapOf(
                                    "lastMessage" to "📄 $name", 
                                    "timestamp" to Timestamp.now(),
                                    "lastStatus" to MessageStatus.SENT.name,
                                    "lastSenderId" to userId
                                ), SetOptions.merge())
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDocument = null }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    if (pendingContactInfo != null) {
        val info = pendingContactInfo!!
        val name = info["name"] as String
        val photo = info["photo"] as String
        @Suppress("UNCHECKED_CAST")
        val numbers = info["numbers"] as List<Pair<String, String>>
        val selectedNumbers = remember { mutableStateListOf<String>().apply { addAll(numbers.map { it.first }) } }

        AlertDialog(
            onDismissRequest = { pendingContactInfo = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Send Contact", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                }
            },
            text = {
                Column {
                    Text("Select number(s) to send for $name:", color = ShynaDesign.colors.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    numbers.forEach { (num, label) ->
                        val isChecked = selectedNumbers.contains(num)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) {
                                        if (selectedNumbers.size > 1) selectedNumbers.remove(num)
                                    } else {
                                        selectedNumbers.add(num)
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selectedNumbers.add(num)
                                    else if (selectedNumbers.size > 1) selectedNumbers.remove(num)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = ShynaDesign.colors.BrandGreen)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(num, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(label, color = ShynaDesign.colors.TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingContactInfo = null
                        val joinedPhones = selectedNumbers.joinToString(",")
                        val metadata = "$name|$joinedPhones|$photo"
                        val msg = mapOf(
                            "text" to "👤 $name", 
                            "senderId" to userId, 
                            "timestamp" to Timestamp.now(), 
                            "sentAt" to System.currentTimeMillis(),
                            "type" to MessageType.CONTACT.name, 
                            "metadata" to metadata,
                            "status" to MessageStatus.SENT.name
                        )
                        db.collection("chats").document(chatId).collection("messages").add(msg)
                        db.collection("chats").document(chatId).set(mapOf(
                            "lastMessage" to "👤 $name", 
                            "timestamp" to Timestamp.now(),
                            "lastStatus" to MessageStatus.SENT.name,
                            "lastSenderId" to userId
                        ), SetOptions.merge())
                    },
                    enabled = selectedNumbers.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
                ) {
                    Text("SEND CONTACT (${selectedNumbers.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingContactInfo = null }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    if (showContactDetailsFor != null) {
        val m = showContactDetailsFor!!
        val parts = m.metadata?.split("|") ?: listOf("Contact", "")
        val name = parts.getOrNull(0) ?: "Contact"
        val rawPhones = parts.getOrNull(1) ?: ""
        val photoUri = parts.getOrNull(2)
        val phoneList = remember(rawPhones) { rawPhones.split(",").filter { it.isNotBlank() } }

        AlertDialog(
            onDismissRequest = { showContactDetailsFor = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, modifier = Modifier.size(48.dp), color = ShynaDesign.colors.BrandGreen.copy(alpha = 0.2f)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!photoUri.isNullOrBlank()) {
                                AsyncImage(model = photoUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            } else {
                                Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 20.sp)
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 18.sp)
                        Text("Contact Details", fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    phoneList.forEach { phone ->
                        Surface(
                            color = ShynaDesign.colors.HeaderBg,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(phone, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text("Mobile", color = ShynaDesign.colors.TextSecondary, fontSize = 11.sp)
                                }
                                Row {
                                    IconButton(onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                        runCatching { mContext.startActivity(intent) }
                                    }) {
                                        Icon(Icons.Default.Call, "Call", tint = ShynaDesign.colors.BrandGreen)
                                    }
                                    IconButton(onClick = {
                                        val intent = Intent(Intent.ACTION_INSERT).apply {
                                            type = ContactsContract.Contacts.CONTENT_TYPE
                                            putExtra(ContactsContract.Intents.Insert.NAME, name)
                                            putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                                        }
                                        runCatching { mContext.startActivity(intent) }
                                    }) {
                                        Icon(Icons.Default.PersonAdd, "Save", tint = ShynaDesign.colors.BrandGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val primaryPhone = phoneList.firstOrNull() ?: ""
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            type = ContactsContract.Contacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.NAME, name)
                            putExtra(ContactsContract.Intents.Insert.PHONE, primaryPhone)
                        }
                        runCatching { mContext.startActivity(intent) }
                        showContactDetailsFor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save to Contacts")
                }
            },
            dismissButton = {
                TextButton(onClick = { showContactDetailsFor = null }) {
                    Text("Close", color = ShynaDesign.colors.TextSecondary)
                }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    if (showEventDialog) {
        var eventTitle by remember { mutableStateOf("") }
        var eventDesc by remember { mutableStateOf("") }
        var eventLoc by remember { mutableStateOf("") }
        
        var eventStartAt by remember { mutableLongStateOf(System.currentTimeMillis() + 3600000L) }
        var eventEndAt by remember { mutableLongStateOf(System.currentTimeMillis() + 7200000L) }

        var showStartDatePicker by remember { mutableStateOf(false) }
        var showStartTimePicker by remember { mutableStateOf(false) }
        var showEndDatePicker by remember { mutableStateOf(false) }
        var showEndTimePicker by remember { mutableStateOf(false) }

        val startDatePickerState = rememberDatePickerState(initialSelectedDateMillis = eventStartAt)
        val endDatePickerState = rememberDatePickerState(initialSelectedDateMillis = eventEndAt)

        val placesClient = remember { com.google.android.libraries.places.api.Places.createClient(mContext) }
        var eventLocSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

        fun queryEventLocationSuggestions(q: String) {
            if (q.isBlank() || q.length < 2) {
                eventLocSuggestions = emptyList()
                return
            }
            val req = com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest.builder()
                .setQuery(q)
                .build()
            placesClient.findAutocompletePredictions(req).addOnSuccessListener { res ->
                eventLocSuggestions = res.autocompletePredictions.take(5).map { 
                    "${it.getPrimaryText(null)}, ${it.getSecondaryText(null)}" 
                }
            }.addOnFailureListener {
                eventLocSuggestions = emptyList()
            }
        }

        if (showStartDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showStartDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val selected = startDatePickerState.selectedDateMillis ?: eventStartAt
                        val cal = Calendar.getInstance().apply { timeInMillis = selected }
                        val currCal = Calendar.getInstance().apply { timeInMillis = eventStartAt }
                        cal.set(Calendar.HOUR_OF_DAY, currCal.get(Calendar.HOUR_OF_DAY))
                        cal.set(Calendar.MINUTE, currCal.get(Calendar.MINUTE))
                        val chosen = cal.timeInMillis
                        val now = System.currentTimeMillis()
                        if (chosen < now - 60000L) {
                            Toast.makeText(mContext, "Event start date/time cannot be in the past!", Toast.LENGTH_SHORT).show()
                            eventStartAt = now + 5 * 60 * 1000L
                        } else {
                            eventStartAt = chosen
                        }
                        if (eventEndAt <= eventStartAt) eventEndAt = eventStartAt + 3600000L
                        showStartDatePicker = false
                        showStartTimePicker = true
                    }) { Text("NEXT (SET TIME)", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
                }
            ) {
                DatePicker(state = startDatePickerState)
            }
        }

        if (showStartTimePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = eventStartAt }
            val timePickerState = rememberTimePickerState(
                initialHour = cal.get(Calendar.HOUR_OF_DAY),
                initialMinute = cal.get(Calendar.MINUTE),
                is24Hour = false
            )
            AlertDialog(
                onDismissRequest = { showStartTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        cal.set(Calendar.MINUTE, timePickerState.minute)
                        val chosenStart = cal.timeInMillis
                        val now = System.currentTimeMillis()
                        if (chosenStart < now - 60000L) {
                            Toast.makeText(mContext, "Event start time cannot be in the past!", Toast.LENGTH_SHORT).show()
                            eventStartAt = now + 5 * 60 * 1000L
                        } else {
                            eventStartAt = chosenStart
                        }
                        if (eventEndAt <= eventStartAt) eventEndAt = eventStartAt + 3600000L
                        showStartTimePicker = false
                    }) { Text("SET START TIME", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
                },
                title = { Text("Select Event Start Time", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
                text = { TimePicker(state = timePickerState) },
                containerColor = ShynaDesign.colors.SurfaceBg
            )
        }

        if (showEndDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showEndDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val selected = endDatePickerState.selectedDateMillis ?: eventEndAt
                        val cal = Calendar.getInstance().apply { timeInMillis = selected }
                        val currCal = Calendar.getInstance().apply { timeInMillis = eventEndAt }
                        cal.set(Calendar.HOUR_OF_DAY, currCal.get(Calendar.HOUR_OF_DAY))
                        cal.set(Calendar.MINUTE, currCal.get(Calendar.MINUTE))
                        val chosen = cal.timeInMillis
                        if (chosen <= eventStartAt) {
                            Toast.makeText(mContext, "End date must be after start date!", Toast.LENGTH_SHORT).show()
                            eventEndAt = eventStartAt + 3600000L
                        } else {
                            eventEndAt = chosen
                        }
                        showEndDatePicker = false
                        showEndTimePicker = true
                    }) { Text("NEXT (SET TIME)", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
                }
            ) {
                DatePicker(state = endDatePickerState)
            }
        }

        if (showEndTimePicker) {
            val cal = Calendar.getInstance().apply { timeInMillis = eventEndAt }
            val timePickerState = rememberTimePickerState(
                initialHour = cal.get(Calendar.HOUR_OF_DAY),
                initialMinute = cal.get(Calendar.MINUTE),
                is24Hour = false
            )
            AlertDialog(
                onDismissRequest = { showEndTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        cal.set(Calendar.MINUTE, timePickerState.minute)
                        val chosenEnd = cal.timeInMillis
                        if (chosenEnd <= eventStartAt) {
                            Toast.makeText(mContext, "Event end time must be after start time!", Toast.LENGTH_SHORT).show()
                            eventEndAt = eventStartAt + 3600000L
                        } else {
                            eventEndAt = chosenEnd
                        }
                        showEndTimePicker = false
                    }) { Text("SET END TIME", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
                },
                title = { Text("Select Event End Time", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
                text = { TimePicker(state = timePickerState) },
                containerColor = ShynaDesign.colors.SurfaceBg
            )
        }

        AlertDialog(
            onDismissRequest = { showEventDialog = false },
            title = { Text("Create New Event", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = eventTitle,
                        onValueChange = { eventTitle = it },
                        label = { Text("Event Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = eventDesc,
                        onValueChange = { eventDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = eventLoc,
                        onValueChange = { 
                            eventLoc = it
                            queryEventLocationSuggestions(it)
                        },
                        label = { Text("Location / District / State") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
                    )
                    if (eventLocSuggestions.isNotEmpty()) {
                        Surface(
                            color = ShynaDesign.colors.HeaderBg,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column {
                                eventLocSuggestions.forEach { suggestion ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                eventLoc = suggestion
                                                eventLocSuggestions = emptyList()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.LocationOn, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(suggestion, color = ShynaDesign.colors.TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    HorizontalDivider(color = ShynaDesign.colors.DividerColor)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    
                    val startDateStr = remember(eventStartAt) { SimpleDateFormat("dd MMM, yyyy • h:mm a", Locale.getDefault()).format(Date(eventStartAt)) }
                    val endDateStr = remember(eventEndAt) { SimpleDateFormat("dd MMM, yyyy • h:mm a", Locale.getDefault()).format(Date(eventEndAt)) }

                    Text("Event Date & Time", color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    
                    Surface(
                        onClick = { showStartDatePicker = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.LightGray),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, null, tint = ShynaDesign.colors.BrandGreen)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Start Time", fontSize = 11.sp, color = ShynaDesign.colors.TextSecondary)
                                Text(text = startDateStr, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        onClick = { showEndDatePicker = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.LightGray),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, null, tint = ShynaDesign.colors.BrandGreen)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("End Time", fontSize = 11.sp, color = ShynaDesign.colors.TextSecondary)
                                Text(text = endDateStr, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (eventTitle.isNotBlank()) {
                            val msg = mapOf(
                                "text" to "📅 $eventTitle",
                                "senderId" to userId,
                                "timestamp" to Timestamp.now(),
                                "type" to MessageType.EVENT.name,
                                "eventTitle" to eventTitle,
                                "eventDescription" to eventDesc,
                                "eventStartAt" to eventStartAt,
                                "eventEndAt" to eventEndAt,
                                "eventLocation" to eventLoc
                            )
                            db.collection("chats").document(chatId).collection("messages").add(msg)
                            db.collection("chats").document(chatId).set(mapOf("lastMessage" to "📅 $eventTitle", "timestamp" to Timestamp.now()), SetOptions.merge())
                        }
                        showEventDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen),
                    enabled = eventTitle.isNotBlank()
                ) { Text("Create Event") }
            },
            dismissButton = {
                TextButton(onClick = { showEventDialog = false }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    if (showPollDialog) {
        var question by remember { mutableStateOf("") }
        var multi by remember { mutableStateOf(false) }
        val options = remember { mutableStateListOf("", "") }
        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text("Create Poll", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen))
                    options.forEachIndexed { i, opt ->
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = opt, 
                            onValueChange = { options[i] = it }, 
                            label = { Text("Option ${i+1}") }, 
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { if(options.size > 2) IconButton(onClick = { options.removeAt(i) }) { Icon(Icons.Default.Close, null) } }
                        )
                    }
                    if (options.size < 12) {
                        TextButton(onClick = { options.add("") }) { Text("+ Add Option", color = ShynaDesign.colors.BrandGreen) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = multi, onCheckedChange = { multi = it }, colors = CheckboxDefaults.colors(checkedColor = ShynaDesign.colors.BrandGreen))
                        Text("Allow multiple answers", color = ShynaDesign.colors.TextPrimary)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (question.isNotBlank() && options.filter { it.isNotBlank() }.size >= 2) {
                        val msg = mapOf(
                            "text" to "📊 $question", 
                            "senderId" to userId, 
                            "timestamp" to Timestamp.now(), 
                            "type" to MessageType.POLL.name,
                            "pollQuestion" to question,
                            "pollOptions" to options.filter { it.isNotBlank() },
                            "pollVotes" to emptyMap<String, List<String>>(),
                            "allowMultipleAnswers" to multi
                        )
                        db.collection("chats").document(chatId).collection("messages").add(msg)
                        db.collection("chats").document(chatId).set(mapOf("lastMessage" to "📊 $question", "timestamp" to Timestamp.now()), SetOptions.merge())
                    }
                    showPollDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen), enabled = question.isNotBlank() && options.filter { it.isNotBlank() }.size >= 2) { Text("Create") }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    if (showUrlDialog) {
        var urlInput by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Send Link", color = ShynaDesign.colors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (urlInput.isNotBlank()) {
                        val msg = mapOf("text" to urlInput, "senderId" to userId, "timestamp" to Timestamp.now(), "type" to MessageType.LINK.name)
                        db.collection("chats").document(chatId).collection("messages").add(msg)
                        db.collection("chats").document(chatId).set(mapOf("lastMessage" to "🔗 Link", "timestamp" to Timestamp.now()), SetOptions.merge())
                    }
                    showUrlDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear this chat?", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
            text = { Text("This will remove all messages from this chat permanently.", color = ShynaDesign.colors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        val nowTs = Timestamp.now()
                        db.collection("users").document(userId).collection("chatSettings").document(chatId).set(mapOf("clearedAt" to nowTs), SetOptions.merge())
                        
                        db.collection("chats").document(chatId).collection("messages").get().addOnSuccessListener { snapshot ->
                            val batch = db.batch()
                            snapshot.documents.forEach { doc ->
                                batch.delete(doc.reference)
                            }
                            batch.commit()
                        }
                        
                        db.collection("chats").document(chatId).set(mapOf(
                            "lastMessage" to "",
                            "timestamp" to nowTs
                        ), SetOptions.merge())
                        
                        msgs.clear()
                        Toast.makeText(mContext, "Chat cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("CLEAR CHAT", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("CANCEL", color = ShynaDesign.colors.TextSecondary)
                }
            },
            containerColor = ShynaDesign.colors.SurfaceBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this chat?", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
            text = { Text("This will delete all messages and remove the chat permanently.", color = ShynaDesign.colors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        val nowTs = Timestamp.now()
                        db.collection("users").document(userId).collection("chatSettings").document(chatId).set(mapOf("deletedAt" to nowTs), SetOptions.merge())
                        
                        db.collection("chats").document(chatId).collection("messages").get().addOnSuccessListener { snapshot ->
                            val batch = db.batch()
                            snapshot.documents.forEach { doc ->
                                batch.delete(doc.reference)
                            }
                            batch.delete(db.collection("chats").document(chatId))
                            batch.commit()
                        }
                        
                        msgs.clear()
                        Toast.makeText(mContext, "Chat deleted", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("DELETE CHAT", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("CANCEL", color = ShynaDesign.colors.TextSecondary)
                }
            },
            containerColor = ShynaDesign.colors.SurfaceBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Media Launchers
    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val meta = MediaUploader.getFileMetadata(mContext, it)
            val name = (meta["name"] as? String) ?: "Document"
            val size = (meta["size"] as? Long) ?: 0L
            val mime = (meta["mime"] as? String) ?: "application/octet-stream"
            
            // VALIDATION
            if (size <= 0) {
                Toast.makeText(mContext, "Unable to send file: File is empty", Toast.LENGTH_SHORT).show()
                return@let
            }
            if (size > 500 * 1024 * 1024) { // 500MB Limit
                Toast.makeText(mContext, "Unable to send file: Size exceeds 500MB", Toast.LENGTH_SHORT).show()
                return@let
            }

            pendingDocument = mapOf(
                "uri" to it,
                "name" to name,
                "size" to size,
                "mime" to mime
            )
        }
    }
    val contactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let { contactUri ->
            var name = ""
            var photoUriStr = ""
            val numberList = mutableListOf<Pair<String, String>>()
            
            runCatching {
                mContext.contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                        if (nameIdx != -1) name = cursor.getString(nameIdx) ?: "Contact"
                        
                        val contactIdIdx = cursor.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                        val contactId = if (contactIdIdx != -1) cursor.getString(contactIdIdx) else ""
                        
                        val photoIdx = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                        if (photoIdx != -1) photoUriStr = cursor.getString(photoIdx) ?: ""
                        
                        if (contactId.isNotBlank()) {
                            mContext.contentResolver.query(
                                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                arrayOf(contactId),
                                null
                            )?.use { pCursor ->
                                val numIdx = pCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                                val typeIdx = pCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE)
                                while (pCursor.moveToNext()) {
                                    val num = if (numIdx != -1) pCursor.getString(numIdx) else ""
                                    val typeVal = if (typeIdx != -1) pCursor.getInt(typeIdx) else android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                                    val label = android.provider.ContactsContract.CommonDataKinds.Phone.getTypeLabel(mContext.resources, typeVal, "Mobile").toString()
                                    if (num.isNotBlank() && numberList.none { it.first == num }) {
                                        numberList.add(num to label)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (name.isNotEmpty() && numberList.isNotEmpty()) {
                pendingContactInfo = mapOf(
                    "name" to name,
                    "photo" to photoUriStr,
                    "numbers" to numberList
                )
            } else {
                Toast.makeText(mContext, "No phone numbers found for this contact", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Block/Mute State
    LaunchedEffect(peerId) {
        db.collection("users").document(userId).collection("blockedUsers").document(peerId)
            .addSnapshotListener { d, _ -> isPeerBlocked = d?.exists() == true }
            
        db.collection("users").document(userId).collection("chatSettings").document(chatId)
            .addSnapshotListener { d, _ -> isMuted = d?.getTimestamp("mutedUntil")?.let { it.toDate().time > System.currentTimeMillis() } ?: false }
    }

    DisposableEffect(chatId, userClearedAt, userDeletedAt) {
        // Clear unread count when opening chat
        db.collection("chats").document(chatId).update("unreadCount_$userId", 0)
        
        val settingsListener = db.collection("users").document(userId).collection("chatSettings").document(chatId)
            .addSnapshotListener { d, _ ->
                userClearedAt = d?.getTimestamp("clearedAt")?.toDate()?.time ?: 0L
                userDeletedAt = d?.getTimestamp("deletedAt")?.toDate()?.time ?: 0L
            }

        msgs.clear()
        val l = db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                
                val currentMsgs = msgs.toMutableList()
                var changed = false

                snapshots.documentChanges.forEach { dc ->
                    val d = dc.document
                    val time = d.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                    if (time < userClearedAt || time < userDeletedAt) return@forEach
                    
                    val typeStr = d.getString("type") ?: "TEXT"
                    val mType = try { MessageType.valueOf(typeStr) } catch(_: Exception) { MessageType.TEXT }
                    val statusStr = d.getString("status") ?: MessageStatus.SENT.name
                    val mStatus = try { MessageStatus.valueOf(statusStr) } catch(_: Exception) { MessageStatus.SENT }
                    
                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val senderId = d.getString("senderId") ?: ""
                        if (senderId != userId) {
                            val msgText = d.getString("text") ?: ""
                            com.example.callruleblocker.data.NetworkUsageTracker.track(mContext, "messages", received = msgText.length.toLong())
                            
                            // Tick System: Mark as DELIVERED if not already
                            if (mStatus == MessageStatus.SENT || mStatus == MessageStatus.SENDING) {
                                db.collection("chats").document(chatId).collection("messages").document(d.id)
                                    .update("status", MessageStatus.DELIVERED.name, "deliveredAt", System.currentTimeMillis())
                                // Update chat status if it's the last message
                                db.collection("chats").document(chatId).get().addOnSuccessListener { chatDoc ->
                                    val chatTime = chatDoc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                                    if (chatTime <= time) {
                                        db.collection("chats").document(chatId).update("lastStatus", MessageStatus.DELIVERED.name)
                                    }
                                }
                            }

                            // Mark as READ immediately if screen is active
                            if (mStatus != MessageStatus.READ) {
                                db.collection("chats").document(chatId).collection("messages").document(d.id)
                                    .update("status", MessageStatus.READ.name, "readAt", System.currentTimeMillis(), "isRead", true)
                                db.collection("chats").document(chatId).get().addOnSuccessListener { chatDoc ->
                                    val chatTime = chatDoc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                                    if (chatTime <= time) {
                                        db.collection("chats").document(chatId).update("lastStatus", MessageStatus.READ.name)
                                    }
                                }
                            }

                            // Auto-save to gallery if enabled AND allowed by network settings
                            val isWifi = com.example.callruleblocker.data.NetworkDetector.isWifi(mContext)
                            val allowedByNetwork = if(isWifi) {
                                storageSettings.wifiMedia.contains(if(mType == MessageType.VIDEO) "video" else "photo")
                            } else {
                                storageSettings.mobileDataMedia.contains(if(mType == MessageType.VIDEO) "video" else "photo")
                            }

                            if (storageSettings.saveToGallery && allowedByNetwork && (mType == MessageType.IMAGE || mType == MessageType.VIDEO)) {
                                val url = d.getString("metadata")
                                if (!url.isNullOrBlank()) {
                                    val name = d.getString("fileName") ?: "media_${System.currentTimeMillis()}"
                                    scope.launch {
                                        com.example.callruleblocker.data.MediaSaver.saveToGallery(mContext, url, name, mType == MessageType.VIDEO)
                                    }
                                }
                            }
                        }
                    }
                    
                    val m = UniversalMessage(
                        id = d.id, 
                        text = d.getString("text") ?: "", 
                        caption = d.getString("caption"),
                        senderId = d.getString("senderId") ?: "",
                        isMine = d.getString("senderId") == userId, 
                        time = d.getTimestamp("timestamp")?.toDate()?.time ?: 0L, 
                        messageType = mType, 
                        status = mStatus,
                        sentAt = d.getLong("sentAt"),
                        deliveredAt = d.getLong("deliveredAt"),
                        readAt = d.getLong("readAt"),
                        metadata = d.getString("metadata"),
                        replyToMessageId = d.getString("replyToMessageId"),
                        replyToText = d.getString("replyToText"),
                        isForwarded = d.getBoolean("isForwarded") ?: false,
                        isStarred = d.getBoolean("isStarred") ?: false,
                        isDeleted = d.getBoolean("isDeleted") ?: false,
                        deleteForEveryone = d.getBoolean("deleteForEveryone") ?: false,
                        deletedFor = d.get("deletedFor") as? List<String> ?: emptyList(),
                        editedAt = d.getLong("editedAt"),
                        reactions = d.get("reactions") as? Map<String, String> ?: emptyMap(),
                        liveLocationExpiry = d.getLong("liveLocationExpiry"),
                        isRead = d.getBoolean("isRead") ?: false,
                        fileName = d.getString("fileName"),
                        fileSize = d.getLong("fileSize") ?: 0L,
                        mimeType = d.getString("mimeType"),
                        thumbnailUrl = d.getString("thumbnailUrl"),
                        durationMs = d.getLong("durationMs") ?: 0L,
                        // Poll
                        pollQuestion = d.getString("pollQuestion"),
                        pollOptions = d.get("pollOptions") as? List<String> ?: emptyList(),
                        pollVotes = d.get("pollVotes") as? Map<String, List<String>> ?: emptyMap(),
                        allowMultipleAnswers = d.getBoolean("allowMultipleAnswers") ?: false,
                        interactionAttempts = (d.get("interactionAttempts") as? Map<*, *>)?.mapNotNull { (k, v) ->
                            val key = k?.toString() ?: return@mapNotNull null
                            val value = (v as? Number)?.toInt() ?: 0
                            key to value
                        }?.toMap() ?: emptyMap(),
                        firstInteractionTime = (d.get("firstInteractionTime") as? Map<*, *>)?.mapNotNull { (k, v) ->
                            val key = k?.toString() ?: return@mapNotNull null
                            val value = (v as? Number)?.toLong() ?: 0L
                            key to value
                        }?.toMap() ?: emptyMap(),
                        lastInteractionTime = (d.get("lastInteractionTime") as? Map<*, *>)?.mapNotNull { (k, v) ->
                            val key = k?.toString() ?: return@mapNotNull null
                            val value = (v as? Number)?.toLong() ?: 0L
                            key to value
                        }?.toMap() ?: emptyMap(),
                        // Event
                        eventTitle = d.getString("eventTitle"),
                        eventDescription = d.getString("eventDescription"),
                        eventStartAt = d.getLong("eventStartAt") ?: 0L,
                        eventLocation = d.getString("eventLocation"),
                        eventRSVPs = d.get("eventRSVPs") as? Map<String, List<String>> ?: emptyMap()
                    )

                    when (dc.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                            if (currentMsgs.none { it.id == d.id }) {
                                currentMsgs.add(m)
                                changed = true
                            }
                        }
                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                            val idx = currentMsgs.indexOfFirst { it.id == d.id }
                            if (idx != -1) {
                                currentMsgs[idx] = m
                                changed = true
                            }
                        }
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                            currentMsgs.removeAll { it.id == d.id }
                            changed = true
                        }
                    }
                }

                if (changed || userClearedAt > 0) {
                    scope.launch(Dispatchers.Default) {
                        val finalMsgs = currentMsgs.filter { it.time >= userClearedAt && !it.deletedFor.contains(userId) }.sortedByDescending { it.time }
                        withContext(Dispatchers.Main) {
                            msgs.clear()
                            msgs.addAll(finalMsgs)
                        }
                    }
                }
            }
        onDispose { 
            l.remove() 
            settingsListener.remove()
        }
    }

    LaunchedEffect(userClearedAt) {
        if (userClearedAt > 0) {
            msgs.removeAll { it.time < userClearedAt }
        }
    }

    // No visible scroll animation on load
    // LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    var infoMessage by remember { mutableStateOf<UniversalMessage?>(null) }
    
    if (infoMessage != null) {
        MessageInfoDialog(infoMessage!!) { infoMessage = null }
    }

    if (showMuteDialog) {
        MuteDialog(
            onDismiss = { showMuteDialog = false },
            onMute = { durationHours ->
                val until = if (durationHours == -1) {
                    Timestamp(Date(System.currentTimeMillis() + 100L * 365 * 24 * 60 * 60 * 1000)) // ~100 years
                } else {
                    Timestamp(Date(System.currentTimeMillis() + durationHours * 60 * 60 * 1000L))
                }
                db.collection("users").document(userId).collection("chatSettings").document(chatId).set(mapOf("mutedUntil" to until), SetOptions.merge())
                showMuteDialog = false
            }
        )
    }

    val chatMediaList = remember(msgs.size) {
        msgs.filter { it.messageType == MessageType.IMAGE || it.messageType == MessageType.VIDEO }
    }

    if (fullScreenMedia != null) {
        val initialIndex = chatMediaList.indexOfFirst { it.id == fullScreenMedia?.id }.coerceAtLeast(0)
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullScreenMedia = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            ChatMediaViewerScreen(
                initialIndex = initialIndex,
                mediaList = chatMediaList,
                onDismiss = { fullScreenMedia = null }
            )
        }
    }

    if (showMediaScoped) {
        MediaScopedScreen(
            peerName = peer?.name ?: "User",
            messages = msgs,
            onBack = { showMediaScoped = false },
            onMediaClick = { fullScreenMedia = it }
        )
    }

    if (showNewGroupScoped && peer != null) {
        NewGroupScopedScreen(
            peer = peer,
            allUsers = allUsers,
            onBack = { showNewGroupScoped = false },
            onCreate = { name, uids ->
                val gId = UUID.randomUUID().toString()
                val data = mapOf(
                    "id" to gId,
                    "name" to name,
                    "participants" to uids,
                    "isGroup" to true,
                    "createdBy" to userId,
                    "timestamp" to Timestamp.now(),
                    "lastMessage" to "Group created",
                    "user1" to userId, // For simple group list query if needed
                    "user2" to "GROUP"
                )
                db.collection("chats").document(gId).set(data)
                
                // Add system message
                val sysMsg = mapOf(
                    "text" to "You created group \"$name\"",
                    "senderId" to "SYSTEM",
                    "timestamp" to Timestamp.now(),
                    "type" to MessageType.SYSTEM.name
                )
                db.collection("chats").document(gId).collection("messages").add(sysMsg)
                
                showNewGroupScoped = false
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.imePadding(),
        topBar = {
            if (isSearchMode) {
                TopAppBar(
                    title = {
                        BasicTextField(
                            value = searchChatQuery,
                            onValueChange = { 
                                searchChatQuery = it
                                currentSearchMatchIndex = if (it.isEmpty()) -1 else 0
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = ShynaDesign.colors.TextPrimary, fontSize = 18.sp),
                            cursorBrush = SolidColor(ShynaDesign.colors.BrandGreen),
                            decorationBox = { innerTextField ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        if (searchChatQuery.isEmpty()) Text("Search...", color = ShynaDesign.colors.TextSecondary)
                                        innerTextField()
                                    }
                                    if (searchResultsIndices.isNotEmpty()) {
                                        Text("${currentSearchMatchIndex + 1}/${searchResultsIndices.size}", color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                                        IconButton(onClick = {
                                            currentSearchMatchIndex = (currentSearchMatchIndex - 1 + searchResultsIndices.size) % searchResultsIndices.size
                                            scope.launch { listState.animateScrollToItem(searchResultsIndices[currentSearchMatchIndex]) }
                                        }) { Icon(Icons.Default.KeyboardArrowUp, null, tint = ShynaDesign.colors.TextSecondary) }
                                        IconButton(onClick = {
                                            currentSearchMatchIndex = (currentSearchMatchIndex + 1) % searchResultsIndices.size
                                            scope.launch { listState.animateScrollToItem(searchResultsIndices[currentSearchMatchIndex]) }
                                        }) { Icon(Icons.Default.KeyboardArrowDown, null, tint = ShynaDesign.colors.TextSecondary) }
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = { IconButton(onClick = { isSearchMode = false; searchChatQuery = "" }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
                )
            } else if (isSelectionMode) {
                TopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedMsgs.clear() }) { Icon(Icons.Default.Close, null, tint = ShynaDesign.colors.TextPrimary) }
                            Text("${selectedMsgs.size}", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        val allSelected = msgs.isNotEmpty() && selectedMsgs.size == msgs.size
                        IconButton(onClick = { 
                            if (allSelected) {
                                selectedMsgs.clear()
                            } else {
                                msgs.forEach { if (!selectedMsgs.contains(it.id)) selectedMsgs.add(it.id) }
                            }
                        }) { 
                            Icon(if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll, null, tint = if(allSelected) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextPrimary) 
                        }

                        val firstSelId = selectedMsgs.firstOrNull()
                        val firstSelMsg = msgs.find { it.id == firstSelId }
                        
                        if (selectedMsgs.size == 1 && firstSelMsg != null) {
                            val type = firstSelMsg.messageType
                            val isInteractable = type == MessageType.IMAGE || type == MessageType.VIDEO || type == MessageType.DOC || type == MessageType.LOCATION || type == MessageType.LIVE_LOCATION || type == MessageType.LINK
                            
                            if (isInteractable) {
                                IconButton(onClick = { 
                                    openMessageItem(mContext, scope, firstSelMsg) { fullScreenMedia = it }
                                    selectedMsgs.clear()
                                }) { Icon(Icons.Outlined.Visibility, null, tint = ShynaDesign.colors.TextPrimary) }
                            }

                            IconButton(onClick = { 
                                replyingTo = firstSelMsg
                                selectedMsgs.clear()
                            }) { Icon(Icons.AutoMirrored.Outlined.Reply, null, tint = ShynaDesign.colors.TextPrimary) }
                            
                            if (PermissionEngine.canEdit(firstSelMsg)) {
                                IconButton(onClick = { 
                                    editingMessage = firstSelMsg
                                    text = firstSelMsg.text
                                    selectedMsgs.clear()
                                }) { Icon(Icons.Outlined.Edit, null, tint = ShynaDesign.colors.TextPrimary) }
                            }
                            
                            IconButton(onClick = {
                                db.collection("chats").document(chatId).collection("messages").document(firstSelId!!).update("isStarred", !firstSelMsg.isStarred)
                                selectedMsgs.clear()
                            }) { Icon(if(firstSelMsg.isStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder, null, tint = ShynaDesign.colors.TextPrimary) }

                            IconButton(onClick = { 
                                infoMessage = firstSelMsg
                                selectedMsgs.clear()
                            }) { Icon(Icons.Outlined.Info, null, tint = ShynaDesign.colors.TextPrimary) }
                        }
                        
                        IconButton(onClick = {
                            if (selectedMsgs.size == 1 && firstSelMsg != null) {
                                onForward(firstSelMsg)
                            } else {
                                Toast.makeText(mContext, "Forwarding multiple not supported yet", Toast.LENGTH_SHORT).show()
                            }
                            selectedMsgs.clear()
                        }) { Icon(Icons.AutoMirrored.Outlined.Forward, null, tint = ShynaDesign.colors.TextPrimary) }

                        var showDeleteDialog by remember { mutableStateOf(false) }
                        if (showDeleteDialog) {
                            val canDeleteForEveryone = selectedMsgs.all { id -> 
                                val msg = msgs.find { it.id == id }
                                msg?.isMine == true && !msg.isDeleted
                            }
                            
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Delete message?", fontWeight = FontWeight.Bold)
                                    }
                                },
                                text = { 
                                    Text(
                                        if (selectedMsgs.size == 1) "Do you want to delete this message?" 
                                        else "Do you want to delete ${selectedMsgs.size} messages?",
                                        color = ShynaDesign.colors.TextSecondary
                                    ) 
                                },
                                confirmButton = {
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (canDeleteForEveryone) {
                                            Surface(
                                                onClick = {
                                                    selectedMsgs.forEach { id -> 
                                                        val m = msgs.find { it.id == id }
                                                        val updates = mutableMapOf<String, Any>(
                                                            "isDeleted" to true, 
                                                            "deleteForEveryone" to true, 
                                                            "text" to "This message was deleted"
                                                        )
                                                        if (m?.messageType != MessageType.TEXT) {
                                                            updates["metadata"] = com.google.firebase.firestore.FieldValue.delete()
                                                            updates["fileName"] = com.google.firebase.firestore.FieldValue.delete()
                                                            updates["thumbnailUrl"] = com.google.firebase.firestore.FieldValue.delete()
                                                        }
                                                        db.collection("chats").document(chatId).collection("messages").document(id).update(updates)
                                                    }
                                                    selectedMsgs.clear()
                                                    showDeleteDialog = false
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color.Transparent
                                            ) {
                                                Text("Delete for everyone", modifier = Modifier.fillMaxWidth().padding(16.dp), color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                            }
                                        }
                                        Surface(
                                            onClick = {
                                                selectedMsgs.forEach { id -> 
                                                    db.collection("chats").document(chatId).collection("messages").document(id)
                                                        .update("deletedFor", com.google.firebase.firestore.FieldValue.arrayUnion(userId)) 
                                                }
                                                selectedMsgs.clear()
                                                showDeleteDialog = false
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.Transparent
                                        ) {
                                            Text("Delete for me", modifier = Modifier.fillMaxWidth().padding(16.dp), color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                        }
                                        Surface(
                                            onClick = { showDeleteDialog = false },
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.Transparent
                                        ) {
                                            Text("Cancel", modifier = Modifier.fillMaxWidth().padding(16.dp), color = Color.Gray, textAlign = TextAlign.Center)
                                        }
                                    }
                                },
                                dismissButton = null,
                                containerColor = ShynaDesign.colors.SurfaceBg,
                                shape = RoundedCornerShape(28.dp)
                            )
                        }

                        IconButton(onClick = { showDeleteDialog = true }) { 
                            Icon(Icons.Outlined.Delete, null, tint = ShynaDesign.colors.TextPrimary) 
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(40.dp).clickable { peer?.let { onAvatarClick(it) } }, 
                                shape = CircleShape, 
                                color = ShynaDesign.colors.DividerColor
                            ) {
                                if (!peer?.photoUrl.isNullOrBlank()) AsyncImage(peer?.photoUrl, null, contentScale = ContentScale.Crop)
                                else Icon(Icons.Outlined.Person, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.clickable { peer?.let { onAvatarClick(it) } }) {
                                Text(peer?.name ?: "Chat", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                                val statusText = if (peer?.isOnline == true) "online" else formatLastSeen(peer?.lastSeen)
                                Text(statusText, fontSize = 12.sp, color = if(peer?.isOnline == true) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary)
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) } },
                    actions = {
                        IconButton(onClick = { 
                                if (isPeerBlocked) {
                                    Toast.makeText(mContext, "Unblock contact to call", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                peer?.let { p -> 
                                    CallSignalingManager.startCall(
                                        mContext, 
                                        p.uid, 
                                        AppCallType.VIDEO, 
                                        { created -> 
                                            mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { 
                                                putExtra("callId", created.id)
                                                putExtra("isIncoming", false) 
                                            }) 
                                        },
                                        { e -> Toast.makeText(mContext, "Unable to start call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() }
                                    ) 
                                } 
                        }) { Icon(Icons.Outlined.Videocam, null, tint = ShynaDesign.colors.TextPrimary) }
                        
                        IconButton(onClick = { 
                                if (isPeerBlocked) {
                                    Toast.makeText(mContext, "Unblock contact to call", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                peer?.let { p -> 
                                    CallSignalingManager.startCall(
                                        mContext, 
                                        p.uid, 
                                        AppCallType.VOICE, 
                                        { created -> 
                                            mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { 
                                                putExtra("callId", created.id)
                                                putExtra("isIncoming", false) 
                                            }) 
                                        },
                                        { e -> Toast.makeText(mContext, "Unable to start call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() }
                                    ) 
                                } 
                        }) { Icon(Icons.Outlined.Call, null, tint = ShynaDesign.colors.TextPrimary) }
                        
                        Box {
                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, null, tint = ShynaDesign.colors.TextPrimary) }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                offset = DpOffset(0.dp, 0.dp),
                                containerColor = ShynaDesign.colors.SurfaceBg
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New group", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { menuExpanded = false; showNewGroupScoped = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("View contact", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { menuExpanded = false; peer?.let { onAvatarClick(it) } }
                                )
                                DropdownMenuItem(
                                    text = { Text("Media, links, and docs", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { menuExpanded = false; showMediaScoped = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Search", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { menuExpanded = false; isSearchMode = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(if(isMuted) "Unmute notifications" else "Mute notifications", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { 
                                        menuExpanded = false
                                        if (isMuted) {
                                            db.collection("users").document(userId).collection("chatSettings").document(chatId).update("mutedUntil", null)
                                        } else {
                                            showMuteDialog = true
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear chat", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { 
                                        menuExpanded = false
                                        showClearConfirm = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete chat", color = Color.Red) },
                                    onClick = { 
                                        menuExpanded = false
                                        showDeleteConfirm = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if(isPeerBlocked) "Unblock" else "Block", color = if(isPeerBlocked) ShynaDesign.colors.TextPrimary else Color.Red) },
                                    onClick = { 
                                        menuExpanded = false
                                        if (isPeerBlocked) {
                                            db.collection("users").document(userId).collection("blockedUsers").document(peerId).delete()
                                        } else {
                                            db.collection("users").document(userId).collection("blockedUsers").document(peerId).set(mapOf("timestamp" to Timestamp.now()))
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export chat", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { menuExpanded = false; exportChat(mContext, peer?.name ?: "User", msgs) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to contacts", color = ShynaDesign.colors.TextPrimary) },
                                    onClick = { 
                                        menuExpanded = false
                                        val intent = Intent(Intent.ACTION_INSERT).apply {
                                            type = ContactsContract.Contacts.CONTENT_TYPE
                                            putExtra(ContactsContract.Intents.Insert.NAME, peer?.name)
                                            putExtra(ContactsContract.Intents.Insert.PHONE, peer?.phone)
                                            putExtra(ContactsContract.Intents.Insert.EMAIL, peer?.email)
                                        }
                                        mContext.startActivity(intent)
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
                )
            }
        }
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().background(ShynaDesign.colors.PrimaryBg)) {
            Box(Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                showEmojis = false
                showAttachments = false
            }) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(), 
                    state = listState, 
                    reverseLayout = true,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    itemsIndexed(
                        items = msgs,
                        key = { _, it -> it.id }
                    ) { index, m ->
                        val isHighlighted = isSearchMode && searchChatQuery.isNotEmpty() && m.text.contains(searchChatQuery, ignoreCase = true)
                        val olderMsg = if (index < msgs.size - 1) msgs[index + 1] else null
                        val showDateDivider = olderMsg == null || !isSameDay(m.time, olderMsg.time)
                        
                        Column {
                            if (showDateDivider) {
                                DateDivider(m.time)
                            }
                            
                            if (selectedMsgs.size == 1 && selectedMsgs.contains(m.id)) {
                                Row(
                                    Modifier.padding(horizontal = 24.dp, vertical = 4.dp).align(if (m.isMine) Alignment.End else Alignment.Start).background(ShynaDesign.colors.HeaderBg, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("❤️", "👍", "😂", "😮", "😢", "🙏").forEach { emoji ->
                                        Text(emoji, Modifier.clickable {
                                            val newReactions = m.reactions.toMutableMap()
                                            newReactions[userId] = emoji
                                            db.collection("chats").document(chatId).collection("messages").document(m.id).update("reactions", newReactions)
                                            selectedMsgs.clear()
                                        }, fontSize = 20.sp)
                                    }
                                }
                            }
                            PremiumMessageBubble(
                                m = m, 
                                isSelected = selectedMsgs.contains(m.id), 
                                isSearchMatch = isHighlighted,
                                currentUserId = userId,
                                isSelectionMode = isSelectionMode,
                                onLongClick = { 
                                    if (selectedMsgs.contains(m.id)) {
                                        selectedMsgs.remove(m.id)
                                    } else {
                                        selectedMsgs.add(m.id)
                                    }
                                },
                                onClick = {
                                    if (selectedMsgs.isNotEmpty()) {
                                        if (selectedMsgs.contains(m.id)) selectedMsgs.remove(m.id)
                                        else selectedMsgs.add(m.id)
                                    } else {
                                        openMessageItem(
                                            mContext, 
                                            scope, 
                                            m, 
                                            onMediaClick = { fullScreenMedia = it }, 
                                            onOpenContact = { showContactDetailsFor = it },
                                            onOpenLiveLocation = { onOpenLiveLocation(it) }
                                        )
                                    }
                                },
                                onMediaClick = { fullScreenMedia = it },
                                onPollVote = { index -> onVote(m, index) },
                                onEventRSVP = { showRsvpFor = m },
                                onCallAgain = { callMsg ->
                                    val isVideo = callMsg.callType == "VIDEO"
                                    peer?.let { p ->
                                        CallSignalingManager.startCall(
                                            mContext, 
                                            p.uid, 
                                            if(isVideo) AppCallType.VIDEO else AppCallType.VOICE, 
                                            { created -> 
                                                mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) }) 
                                            },
                                            { e -> Toast.makeText(mContext, "Unable to start call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (showAttachments) {
                AttachmentPanel(
                    onMediaClick = { type ->
                        showAttachments = false
                        when (type) {
                            "CAMERA" -> onOpenCamera(chatId, false)
                            "GALLERY" -> onOpenGallery(chatId)
                            "LINK" -> showUrlDialog = true
                            "AUDIO" -> onOpenAudio(chatId)
                            "DOC" -> docLauncher.launch("*/*")
                            "CONTACT" -> contactLauncher.launch(null)
                            "LOCATION" -> onOpenLocation(chatId)
                            "POLL" -> showPollDialog = true
                            "EVENT" -> showEventDialog = true
                            else -> Toast.makeText(mContext, "$type Feature Active", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            PremiumChatComposer(
                text = text, onTextChange = { 
                    text = it
                    // Simulated typing indicator logic
                    if (it.isNotEmpty() && !isTyping) {
                        isTyping = true
                        scope.launch { delay(3000); isTyping = false }
                    }
                },
                onSend = {
                    if (editingMessage != null) {
                        db.collection("chats").document(chatId).collection("messages").document(editingMessage!!.id).update(
                            "text", text,
                            "editedAt", System.currentTimeMillis()
                        )
                        editingMessage = null
                        text = ""
                    } else if (text.isNotBlank()) {
                        val msg = mutableMapOf(
                            "text" to text, 
                            "senderId" to userId, 
                            "timestamp" to Timestamp.now(), 
                            "sentAt" to System.currentTimeMillis(),
                            "type" to MessageType.TEXT.name,
                            "status" to MessageStatus.SENT.name
                        )
                        if (replyingTo != null) {
                            msg["replyToMessageId"] = replyingTo!!.id
                            msg["replyToText"] = replyingTo!!.text
                            replyingTo = null
                        }
                        db.collection("chats").document(chatId).collection("messages").add(msg)
                        com.example.callruleblocker.data.NetworkUsageTracker.track(mContext, "messages", sent = text.length.toLong())
                        db.collection("chats").document(chatId).set(mapOf(
                            "lastMessage" to text, 
                            "timestamp" to Timestamp.now(), 
                            "lastStatus" to MessageStatus.SENT.name,
                            "lastSenderId" to userId,
                            "type" to MessageType.TEXT.name,
                            "user1" to (if (userId < peerId) userId else peerId), 
                            "user2" to (if (userId < peerId) peerId else userId)
                        ), SetOptions.merge())
                        text = ""
                        showEmojis = false
                    }
                },
                onVoiceComplete = { file ->
                    onUploadingChange(true)
                    val fileSize = file.length()
                    
                    // Get Duration
                    val duration = try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                        retriever.release()
                        dur
                    } catch(e: Exception) { 0L }

                    MediaUploader.upload(Uri.fromFile(file), mContext, "video") { url ->
                        onUploadingChange(false)
                        if (url != null) {
                            com.example.callruleblocker.data.NetworkUsageTracker.track(mContext, "media", sent = fileSize)
                            val msg = mapOf(
                                "text" to "🎤 Voice Note", 
                                "senderId" to userId, 
                                "timestamp" to Timestamp.now(), 
                                "sentAt" to System.currentTimeMillis(),
                                "type" to MessageType.VOICE.name, 
                                "metadata" to url,
                                "status" to MessageStatus.SENT.name,
                                "mimeType" to "audio/mp4",
                                "durationMs" to duration,
                                "fileSize" to fileSize
                            )
                            db.collection("chats").document(chatId).collection("messages").add(msg)
                            db.collection("chats").document(chatId).set(mapOf(
                                "lastMessage" to "🎤 Voice Note", 
                                "timestamp" to Timestamp.now(),
                                "lastStatus" to MessageStatus.SENT.name,
                                "lastSenderId" to userId
                            ), SetOptions.merge())
                        } else {
                            Toast.makeText(mContext, "Failed to send voice note", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onAttachClick = { 
                    showEmojis = false
                    showAttachments = !showAttachments 
                },
                onEmojiClick = { 
                    showAttachments = false
                    showEmojis = !showEmojis 
                },
                onCameraClick = { isVideo ->
                    showEmojis = false
                    showAttachments = false
                    onOpenCamera(chatId, isVideo)
                },
                isEmojiVisible = showEmojis,
                replyingTo = replyingTo,
                editingMessage = editingMessage,
                onCancelAction = {
                    if (editingMessage != null) text = ""
                    replyingTo = null
                    editingMessage = null
                },
                isPeerActive = peer != null // Disable composer if peer is deleted
            )


            if (showEmojis) {
                EmojiPicker(onEmojiSelected = { text += it })
            }
        }

        // --- OVERLAYS ON TOP OF SCAFFOLD ---
        if (showMediaScoped) {
            MediaScopedScreen(
                peerName = peer?.name ?: "User",
                messages = msgs,
                onBack = { showMediaScoped = false },
                onMediaClick = { fullScreenMedia = it }
            )
        }
    }
}
}
}


    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun PremiumMessageBubble(
        m: UniversalMessage, 
        isSelected: Boolean, 
        isSearchMatch: Boolean = false,
        currentUserId: String = "",
        isSelectionMode: Boolean = false,
        onLongClick: () -> Unit,
        onClick: () -> Unit,
        onMediaClick: (UniversalMessage) -> Unit,
        onPollVote: (Int) -> Unit = {},
        onEventRSVP: () -> Unit = {},
        onCallAgain: (UniversalMessage) -> Unit = {}
    ) {
        // Handle SYSTEM message
        if (m.messageType == MessageType.SYSTEM) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) ShynaDesign.colors.SelectionOverlay else Color.Transparent)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { onLongClick() },
                            onTap = { onClick() }
                        )
                    }
                    .padding(vertical = 8.dp), 
                contentAlignment = Alignment.Center
            ) {
                Surface(color = ShynaDesign.colors.HeaderBg.copy(0.5f), shape = CircleShape) {
                    Text(m.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
                }
            }
            return
        }

        val align = if (m.isMine) Alignment.CenterEnd else Alignment.CenterStart
        val color = when {
            isSelected -> ShynaDesign.colors.SelectionOverlay
            isSearchMatch -> ShynaDesign.colors.BrandGreen.copy(alpha = 0.3f)
            else -> Color.Transparent
        }
        val displayTime = m.editedAt ?: m.time
        val timeStr = remember(displayTime) { 
            try { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(displayTime)) }
            catch(e: Exception) { "" }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color)
                .padding(horizontal = 16.dp, vertical = 4.dp), 
            contentAlignment = align
        ) {
            Surface(
                color = if (m.isMine) ShynaDesign.colors.OutgoingBubble else ShynaDesign.colors.IncomingBubble,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (m.isMine) 16.dp else 2.dp, bottomEnd = if (m.isMine) 2.dp else 16.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongClick() },
                        onTap = { 
                            onClick() 
                        },
                        onDoubleTap = {
                            if (m.messageType == MessageType.IMAGE || m.messageType == MessageType.VIDEO || (m.messageType == MessageType.DOC && m.metadata != null)) {
                                onMediaClick(m)
                            }
                        }
                    )
                }
            ) {
                val contentPadding = if (m.messageType == MessageType.IMAGE || m.messageType == MessageType.VIDEO || m.messageType == MessageType.LOCATION) 0.dp else 10.dp
                Column(Modifier.padding(contentPadding)) {
                    if (m.isForwarded) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp)) {
                            Icon(Icons.Default.Reply, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(12.dp).graphicsLayer(scaleX = -1f))
                            Text("Forwarded", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = ShynaDesign.colors.TextSecondary, fontSize = 11.sp)
                        }
                    }
                    
                    if (m.replyToText != null) {
                        Surface(
                            color = Color.Black.copy(0.05f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth()
                        ) {
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                Box(Modifier.width(3.dp).fillMaxHeight().background(ShynaDesign.colors.BrandGreen))
                                Column(Modifier.padding(8.dp)) {
                                    Text("Reply", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 12.sp)
                                    Text(m.replyToText, color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    if (m.deleteForEveryone) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                            Icon(Icons.Default.Block, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("This message was deleted", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp)
                        }
                    } else {
                        when (m.messageType) {
                            MessageType.TEXT -> TextMessageBubble(m)
                            MessageType.IMAGE -> ImageMessageBubble(m)
                            MessageType.VIDEO -> VideoMessageBubble(m)
                            MessageType.VOICE -> VoiceMessageBubble(m)
                            MessageType.AUDIO -> AudioMessageBubble(m)
                            MessageType.LOCATION -> LocationMessageBubble(m)
                            MessageType.LIVE_LOCATION -> LiveLocationMessageBubble(m)
                            MessageType.LINK -> LinkMessageBubble(m)
                            MessageType.DOC -> DocMessageBubble(m)
                            MessageType.CONTACT -> ContactMessageBubble(m)
                            MessageType.EVENT -> EventMessageBubble(m, currentUserId, isSelectionMode, onEventRSVP)
                            MessageType.POLL -> PollMessageBubble(m, currentUserId, isSelectionMode, onPollVote)
                            MessageType.CALL -> CallMessageBubble(m, isSelectionMode) { onCallAgain(m) }
                            else -> TextMessageBubble(m)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(
                                top = if (m.messageType == MessageType.IMAGE || m.messageType == MessageType.VIDEO || m.messageType == MessageType.LOCATION) 0.dp else 4.dp,
                                bottom = if (m.messageType == MessageType.IMAGE || m.messageType == MessageType.VIDEO || m.messageType == MessageType.LOCATION) 8.dp else 0.dp,
                                end = if (m.messageType == MessageType.IMAGE || m.messageType == MessageType.VIDEO || m.messageType == MessageType.LOCATION) 12.dp else 0.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (m.isStarred) Icon(Icons.Default.Star, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(10.dp).padding(end = 4.dp))
                        if (m.editedAt != null && !m.deleteForEveryone) {
                            Text("edited", fontSize = 10.sp, color = ShynaDesign.colors.TextSecondary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(timeStr, fontSize = 10.sp, color = ShynaDesign.colors.TextSecondary)
                        Spacer(Modifier.width(4.dp))
                        MessageStatusTicks(m.status, m.isMine)
                    }
                }
            }

            if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(ShynaDesign.colors.SelectionOverlay)
            ) {
                Surface(
                    modifier = Modifier.align(if(m.isMine) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 8.dp).size(24.dp),
                    shape = CircleShape,
                    color = ShynaDesign.colors.BrandGreen
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                }
            }
        }
            
            if (m.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(if (m.isMine) Alignment.BottomEnd else Alignment.BottomStart)
                        .offset(y = 12.dp, x = if (m.isMine) (-12).dp else 12.dp)
                        .background(ShynaDesign.colors.SurfaceBg, CircleShape)
                        .border(1.dp, ShynaDesign.colors.DividerColor, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    m.reactions.values.distinct().take(3).forEach { emoji ->
                        Text(emoji, fontSize = 12.sp)
                    }
                    if (m.reactions.size > 1) {
                        Text(m.reactions.size.toString(), fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp), color = ShynaDesign.colors.TextSecondary)
                    }
                }
            }
        }
    }

@Composable
private fun PremiumChatComposer(
    text: String, 
    onTextChange: (String) -> Unit, 
    onSend: () -> Unit, 
    onVoiceComplete: (File) -> Unit,
    onAttachClick: () -> Unit,
    onEmojiClick: () -> Unit,
    onCameraClick: (Boolean) -> Unit,
    isEmojiVisible: Boolean,
    replyingTo: UniversalMessage? = null,
    editingMessage: UniversalMessage? = null,
    onCancelAction: () -> Unit = {},
    isPeerActive: Boolean = true
) {
    val mContext = LocalContext.current
    val recorder = remember { AudioRecorder(mContext) }
    var isRecording by remember { mutableStateOf(false) }
    val amplitudes = remember { mutableStateListOf<Float>() }

    if (!isPeerActive) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            color = ShynaDesign.colors.HeaderBg,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "This user is no longer available. You cannot send messages.",
                modifier = Modifier.padding(16.dp),
                color = ShynaDesign.colors.TextSecondary,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        }
        return
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isActive) {
                amplitudes.add((recorder.getAmplitude() / 32767f).coerceIn(0.1f, 1f))
                if (amplitudes.size > 50) amplitudes.removeAt(0)
                delay(100)
            }
        } else amplitudes.clear()
    }

    Column(Modifier.fillMaxWidth()) {
        if (replyingTo != null || editingMessage != null) {
            Surface(
                color = ShynaDesign.colors.HeaderBg,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(ShynaDesign.colors.BrandGreen, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val title = if (editingMessage != null) "Edit Message" else "Replying to"
                        val content = editingMessage?.text ?: replyingTo?.text ?: "Message"
                        Text(title, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 12.sp)
                        Text(content, color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onCancelAction) {
                        Icon(Icons.Default.Close, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Row(Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Surface(Modifier.weight(1f), shape = if (replyingTo != null || editingMessage != null) RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp) else RoundedCornerShape(28.dp), color = ShynaDesign.colors.HeaderBg) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEmojiClick) {
                        Icon(if (isEmojiVisible) Icons.Default.Keyboard else Icons.Default.EmojiEmotions, null, tint = ShynaDesign.colors.TextSecondary)
                    }
                    if (isRecording) {
                        Box(Modifier.weight(1f).padding(horizontal = 12.dp).height(40.dp)) {
                            RecordingWaveform(amplitudes)
                        }
                    } else {
                        BasicTextField(
                            value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f).padding(8.dp),
                            textStyle = TextStyle(color = ShynaDesign.colors.TextPrimary, fontSize = 17.sp),
                            decorationBox = { if (text.isEmpty()) Text("Message", color = ShynaDesign.colors.TextSecondary); it() }
                        )
                    }
                    IconButton(onClick = onAttachClick) {
                        Icon(Icons.Filled.AttachFile, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.graphicsLayer(rotationZ = -45f))
                    }
                    if (text.isEmpty() && !isRecording && editingMessage == null) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = { onCameraClick(false) },
                                    onLongClick = { onCameraClick(true) }
                                )
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, null, tint = ShynaDesign.colors.TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(ShynaDesign.colors.BrandGreen).pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (text.isEmpty() && editingMessage == null) {
                                val file = File(mContext.cacheDir, "voice_${System.currentTimeMillis()}.mp4")
                                recorder.start(file)
                                isRecording = true
                                try { awaitRelease(); recorder.stop(); onVoiceComplete(file) } finally { isRecording = false }
                            } else onSend()
                        }
                    )
                }, contentAlignment = Alignment.Center
            ) {
                Icon(if (editingMessage != null) Icons.Default.Check else if (text.isNotEmpty()) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun RecordingWaveform(amplitudes: List<Float>) {
    Canvas(Modifier.fillMaxSize()) {
        val spacing = 6f
        val centerY = size.height / 2
        amplitudes.forEachIndexed { i, amp ->
            val x = size.width - (amplitudes.size - i) * spacing
            val h = amp * size.height
            if (x > 0) drawLine(Color.Red, Offset(x, centerY - h / 2), Offset(x, centerY + h / 2), strokeWidth = 3f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun AttachmentPanel(onMediaClick: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(24.dp), color = ShynaDesign.colors.HeaderBg, shadowElevation = 8.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                AttachmentItem("Gallery", Icons.Filled.Image, Color(0xFFC059FF)) { onMediaClick("GALLERY") }
                AttachmentItem("Camera", Icons.Filled.PhotoCamera, Color(0xFFFF2E74)) { onMediaClick("CAMERA") }
                AttachmentItem("Audio", Icons.Filled.Headphones, Color(0xFFFF8E2D)) { onMediaClick("AUDIO") }
                AttachmentItem("Poll", Icons.Filled.BarChart, Color(0xFFFFBC38)) { onMediaClick("POLL") }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                AttachmentItem("Document", Icons.AutoMirrored.Filled.InsertDriveFile, Color(0xFF7F66FF)) { onMediaClick("DOC") }
                AttachmentItem("Contact", Icons.Filled.Person, Color(0xFF00A5F4)) { onMediaClick("CONTACT") }
                AttachmentItem("Location", Icons.Filled.LocationOn, Color(0xFF00C659)) { onMediaClick("LOCATION") }
                AttachmentItem("Event", Icons.Filled.Event, Color(0xFF00D1B2)) { onMediaClick("EVENT") }
            }
        }
    }
}

@Composable
private fun AttachmentItem(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Surface(Modifier.size(54.dp), shape = CircleShape, color = color.copy(0.1f)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
    }
}

@Composable
private fun DateDivider(time: Long) {
    val dateStr = remember(time) { 
        val now = Calendar.getInstance()
        val msgTime = Calendar.getInstance().apply { timeInMillis = time }
        if (now.get(Calendar.DATE) == msgTime.get(Calendar.DATE) && now.get(Calendar.MONTH) == msgTime.get(Calendar.MONTH) && now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR)) "Today"
        else if (now.get(Calendar.DATE) - msgTime.get(Calendar.DATE) == 1 && now.get(Calendar.MONTH) == msgTime.get(Calendar.MONTH) && now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR)) "Yesterday"
        else SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(time))
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp)) {
            Text(dateStr, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun MessageStatusTicks(status: MessageStatus, isMine: Boolean) {
    if (!isMine) return
    val icon = when (status) {
        MessageStatus.SENDING -> Icons.Default.AccessTime
        MessageStatus.SENT -> Icons.Default.Done
        MessageStatus.DELIVERED, MessageStatus.READ -> Icons.Default.DoneAll
        MessageStatus.FAILED -> Icons.Default.Error
    }
    val color = if (status == MessageStatus.READ) Color(0xFF25D366) else ShynaDesign.colors.TextSecondary
    Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
}

@Composable
private fun MessageInfoDialog(m: UniversalMessage, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message info", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoRow(Icons.Default.Done, "Sent", m.sentAt ?: m.time)
                if (m.deliveredAt != null) InfoRow(Icons.Default.DoneAll, "Delivered", m.deliveredAt)
                if (m.readAt != null) InfoRow(Icons.Default.DoneAll, "Read", m.readAt, Color(0xFF25D366))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = ShynaDesign.colors.BrandGreen) } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, time: Long, iconColor: Color = ShynaDesign.colors.TextSecondary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
            val dateStr = remember(time) { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(time)) }
            Text(dateStr, color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmojiPicker(onEmojiSelected: (String) -> Unit) {
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    
    val categories = remember {
        listOf(
            "😀" to listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻", "👽", "👾", "🤖", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾"
            ),
            "👍" to listOf(
                "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "舌", "👄"
            ),
            "🐶" to listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌", "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️", "🕸️", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🦭", "🐊", "🐅", "🐆", "zebra", "🦍", "🦧", "🦣", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🦬", "🐃", "🐂", "🐎", "🐖", "🐏", "🐑", "🦙", "🐐", "🦌", "🐕", "🐩", "🦮", "🐕‍🦺", "🐈", "🐈‍⬛", "🐓", "🦃", "🦚", "🦜", "🦩", "🕊️", "🐇", "🦝", "🦨", "🦡", "🦫", "🦦", "🦥", "🐁", "🐀", "🐿️", "🦔"
            ),
            "🍎" to listOf(
                "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🫑", "🌽", "🥕", "🫒", "🧄", "🧅", "🥔", "🍠", "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🦴", "🌭", "🍔", "🍟", "🍕", "🫓", "🥪", "🥙", "🧆", "🌮", "🌯", "🫔", "🥗", "🥘", "🫕", "🥫", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🦪", "🍤", "🍙", "🍚", "🍘", "🍥", "🥠", "🥮", "🍢", "🍡", "🍧", "🍨", "🍦", "🥧", "🧁", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "🌰", "🥜", "🍯", "🥛", "🍼", "🫖", "☕", "🍵", "🧃", "🥤", "🧋", "🍶", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹", "🍾", "🧊"
            ),
            "⚽" to listOf(
                "⚽", "🏀", "🏈", "⚾", "🥎", "tennis", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️", "🥌", "🎿", "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "⛹️", "🤺", "🤾", "🏌️", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣", "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🏵️", "🎗️", "🎫", "🎟️", "🎪", "🤹", "🎭", "🩰", "🎨", "🎬", "🎤", "🎧", "🎼", "🎵", "🎶", "🥁", "🎷", "🎺", "🎸", "🪕", "🎻", "🎲", "🎯", " bowling", "🎮", "🎰", "🧩"
            ),
            "✈️" to listOf(
                "🚗", "🚕", "🚙", "🚌", "🛺", "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🦯", "🦽", "🦼", "🛴", "🚲", "🛵", "🏍️", "🛞", "🚨", "🚔", "🚍", "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚝", "🚄", "🚅", "🚈", "🚂", "🚆", "🚇", "🚊", "🚉", "✈️", "🛫", "🛬", "🛩️", "💺", "🛰️", "🚀", "🛸", "🚁", "🛶", "⛵", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓", "🛟", "⛽", "🚧", "🚦", "🚥", "🛑", "🎡", "🎢", "🎠", "🏗️", "🧳", "⌛", "⏳", "⌚", "⏰", "⏱️", "⏲️", "🕰️", "🌡️", "☀️", "🌝", "🌞", "🪐", "⭐", "🌟", "🌠", "🌌", "☁️", "⛅", "⛈️", "🌤️", "🌦️", "🌧️", "🌩️", "🌪️", "🌫️", "🌬️", "🌀", "🌈", "🌂", "☂️", "☔", "⚡", "❄️", "☃️", "⛄", "🔥", "💧", "🌊"
            ),
            "💡" to listOf(
                "👓", "🕶️", "🥽", "🥼", "🦺", "👔", "👕", "👖", "🧣", "Gloves", "🧥", "🧦", "👗", "👘", "🥻", "🩱", "👙", "🩲", "🩳", "💄", "💋", "👣", "🦔", "👞", "👟", "🥾", "🥿", "👠", "🩰", "👡", "👢", "👑", "👒", "🎩", "🎓", "🧢", "🪖", "🎒", "👝", "👛", "👜", "💼", "🧳", "☂️", "💍", "💎", "🔇", "🔈", "🔉", "🔊", "📢", "📣", "📯", "🔔", "🔕", "🎼", "🎵", "🎶", "🎙️", "🎚️", "🎛️", "🎤", "🎧", "📻", "🎷", "🪗", "🎸", "🎹", "🎺", "🎻", "🪕", "🥁", "🪘", "📱", "📲", "☎️", "📞", "📟", "📠", "🔋", "🔌", "💻", "🖥️", "🖨️", "⌨️", "🖱️", "🖲️", "💽", "💾", "💿", "DVD", "🧮", "🎥", "🎞️", "📽️", "🎬", "📺", "📷", "📸", "📹", "📼", "🔍", "🔎", "🕯️", "💡", "🔦", "🏮", "🪔", "📔", "📕", "📖", "📗", "📘", "📙", "📚", "📓", "📒", "📃", "📜", "📄", "📰", "🗞️", "📑", "🔖", "🏷️", "💰", "🪙", "💴", "💵", "💶", "💷", "💸", "💳", "🧾", "✉️", "📧", "📨", "📩", "📤", "📥", "📦", "📫", "📪", "📬", "📭", "📮", "🗳️", "✏️", "✒️", "🖋️", "🖊️", "🖌️", "🖍️", "📝", "💼", "📁", "📂", "🗂️", "📅", "📆", "🗓️", "📇", "📈", "📉", "📊", "📋", "📌", "📍", "📎", "🖇️", "📏", "📐", "✂️", "🗃️", "🗄️", "🗑️", "🔒", "🔓", "🔏", "🔐", "🔑", "🗝️", "🔨", "🪓", "⛏️", "⚒️", "🛠️", "🗡️", "⚔️", "💣", "🪃", "🏹", "🛡️", "🪚", "🔧", "🪛", "🔩", "⚙️", "🗜️", "⚖️", "🦯", "🔗", "⛓️", "🪝", "🧰", "🧲", "🪜", "🧪", "🧫", "🧬", "🔬", "🔭", "📡", "💉", "🩸", "💊", "🩹", "🩺", "🚪", "🛗", "🪞", "🪟", "🛏️", "🛋️", "🪑", "🚽", "🪠", "Shower", "🛁", "🪤", "🪒", "Lotion", "🧷", "🧹", "Basket", "🧻", "Bucket", "Soap", "Toothbrush", "Sponge", "🧯", "Cart", "Cigarette", "Coffin", "Placard"
            ),
            "❤️" to listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐", "⛎", "♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐", "♑", "♒", "♓", "🔀", "🔁", "🔂", "▶️", "⏩", "⏭️", "⏯️", "◀️", "⏪", "⏮️", "🔼", "⏫", "🔽", "⏬", "⏸️", "⏹️", "⏺️", "⏏️", "🎦", "🔅", "🔆", "📶", "📳", "📴", "♀️", "♂️", "⚧️", "✖️", "➕", "➖", "➗", "♾️", "‼️", "⁉️", "❓", "❔", "❕", "❗", "〰️", "💱", "💲", "⚕️", "♻️", "⚜️", "🔱", "📛", "🔰", "⭕", "✅", "☑️", "✔️", "❌", "❎", "➰", "➿", "〽️", "✳️", "✴️", "❇️", "©️", "®️", "™️", "#️⃣", "*️⃣", "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟", "🔠", "🔡", "🔢", "🔣", "🔤", "🅰️", "🆎", "🅱️", "🆑", "🆒", "🆓", "ℹ️", "🆔", "Ⓜ️", "🆕", "🆖", "🅾️", "🆗", "🅿️", "🆘", "🆙", "VS", "🈁", "🈂️", "🈷️", "🈶", "🈯", "🉐", "🈹", "🈚", "🈲", "🉑", "🈸", "🈴", "🈳", "㊗️", "㊙️", "🈺", "🈵", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪", "🟤", "🔺", "🔻", "🔸", "🔹", "🔶", "🔷", "🔳", "🔲", "▪️", "▫️", "◾", "◽", "◼️", "◻️", "⬛", "⬜", "🔈", "🔇", "📢", "🔔", "🔕", "📣", "🏁", "🚩", "🎌", "🏴", "🏳️"
            )
        )
    }

    Column(Modifier.fillMaxWidth().height(280.dp).background(ShynaDesign.colors.HeaderBg)) {
        ScrollableTabRow(
            selectedTabIndex = selectedCategoryIndex,
            edgePadding = 8.dp,
            containerColor = ShynaDesign.colors.HeaderBg,
            contentColor = ShynaDesign.colors.BrandGreen,
            divider = {}
        ) {
            categories.forEachIndexed { index, (tabIcon, _) ->
                Tab(
                    selected = selectedCategoryIndex == index,
                    onClick = { selectedCategoryIndex = index },
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(tabIcon, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
        }

        val currentEmojis = categories[selectedCategoryIndex].second

        LazyVerticalGrid(
            columns = GridCells.Adaptive(42.dp),
            contentPadding = PaddingValues(8.dp),
            state = rememberLazyGridState(),
            modifier = Modifier.fillMaxSize()
        ) {
            items(currentEmojis, key = { it }) { e ->
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { onEmojiSelected(e) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(e, fontSize = 22.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewBroadcastScreen(
    currentUid: String,
    allUsers: List<RealUser>,
    onBack: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    val eligibleUsers = remember(allUsers, currentUid) {
        allUsers.filter { it.uid != currentUid }
    }
    var searchQuery by remember { mutableStateOf("") }
    var broadcastName by remember { mutableStateOf("") }
    val selectedUids = remember { mutableStateListOf<String>() }
    var showNameDialog by remember { mutableStateOf(false) }

    val filteredUsers = remember(eligibleUsers, searchQuery) {
        if (searchQuery.isBlank()) eligibleUsers
        else eligibleUsers.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery) }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Name Broadcast List", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
            text = {
                Column {
                    Text("Enter a name for this broadcast list (${selectedUids.size} recipients):", color = ShynaDesign.colors.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = broadcastName,
                        onValueChange = { broadcastName = it },
                        placeholder = { Text("e.g. Work Team, Family") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = broadcastName.ifBlank { "Broadcast (${selectedUids.size} recipients)" }
                        val members = selectedUids.toList() + currentUid
                        showNameDialog = false
                        onCreate(finalName, members)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
                ) {
                    Text("CREATE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel", color = ShynaDesign.colors.TextSecondary)
                }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("New broadcast", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${selectedUids.size} of ${eligibleUsers.size} selected", color = ShynaDesign.colors.BrandGreen, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = ShynaDesign.colors.TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (selectedUids.size == eligibleUsers.size) selectedUids.clear()
                        else {
                            selectedUids.clear()
                            selectedUids.addAll(eligibleUsers.map { it.uid })
                        }
                    }) {
                        Icon(
                            if (selectedUids.size == eligibleUsers.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                            "Select All",
                            tint = ShynaDesign.colors.BrandGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        floatingActionButton = {
            if (selectedUids.size >= 2) {
                FloatingActionButton(
                    onClick = { showNameDialog = true },
                    containerColor = ShynaDesign.colors.BrandGreen,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Check, "Create Broadcast")
                }
            }
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize()) {
            Surface(
                color = ShynaDesign.colors.HeaderBg.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Only contacts with your number in their address book will receive your broadcast messages.",
                    color = ShynaDesign.colors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    textAlign = TextAlign.Center
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search contacts...", color = ShynaDesign.colors.TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = ShynaDesign.colors.TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ShynaDesign.colors.BrandGreen,
                    unfocusedBorderColor = ShynaDesign.colors.DividerColor
                )
            )

            if (selectedUids.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedUids) { uid ->
                        val u = eligibleUsers.find { it.uid == uid }
                        u?.let { user ->
                            InputChip(
                                selected = true,
                                onClick = { selectedUids.remove(uid) },
                                label = { Text(user.name, fontSize = 12.sp) },
                                trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = ShynaDesign.colors.BrandGreen.copy(alpha = 0.2f),
                                    selectedLabelColor = ShynaDesign.colors.BrandGreen
                                )
                            )
                        }
                    }
                }
                HorizontalDivider(color = ShynaDesign.colors.DividerColor)
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(filteredUsers) { user ->
                    val isSelected = selectedUids.contains(user.uid)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) selectedUids.remove(user.uid)
                                else selectedUids.add(user.uid)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Surface(shape = CircleShape, modifier = Modifier.size(46.dp), color = ShynaDesign.colors.BrandGreen.copy(alpha = 0.15f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (!user.photoUrl.isNullOrBlank()) {
                                        AsyncImage(model = user.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                    } else {
                                        Text(user.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 18.sp)
                                    }
                                }
                            }
                            if (isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = ShynaDesign.colors.BrandGreen,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.padding(2.dp))
                                }
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(Modifier.weight(1f)) {
                            Text(user.name, fontWeight = FontWeight.SemiBold, color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp)
                            if (user.phone.isNotBlank()) {
                                Text(user.phone, color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                            }
                        }

                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) selectedUids.add(user.uid)
                                else selectedUids.remove(user.uid)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = ShynaDesign.colors.BrandGreen)
                        )
                    }
                    HorizontalDivider(color = ShynaDesign.colors.DividerColor, modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatsList(
    users: List<RealUser>, 
    query: String, 
    filter: String, 
    favourites: Set<String>, 
    archived: Set<String>, 
    recentChats: List<ChatRowItem>,
    customLists: List<CustomChatList>,
    isArchivedMode: Boolean = false,
    onOpen: (String) -> Unit,
    onAvatarClick: (RealUser) -> Unit,
    onToggleFav: (String) -> Unit,
    onArchive: (String) -> Unit = {},
    onUnarchive: (String) -> Unit = {},
    onMarkUnread: (String) -> Unit,
    onDeleteChat: (String) -> Unit
) {
    val displayList by remember(recentChats, query, filter, favourites, archived, customLists, isArchivedMode) {
        derivedStateOf {
            recentChats.filter { chat ->
                val isGroup = chat.isGroup
                val peer = if (isGroup) null else users.find { it.uid == chat.peerUid }
                
                // Archived check
                val isChatArchived = archived.contains(chat.id)
                if (isArchivedMode != isChatArchived) return@filter false
                
                // Search Match (Local Chat)
                val chatName = if (isGroup) chat.groupName ?: "Group" else peer?.name ?: ""
                
                val matchesSearch = query.isEmpty() || chatName.contains(query, true)
                if (!matchesSearch) return@filter false
                
                // Filter Match
                when (filter) {
                    "All" -> true
                    "Unread" -> chat.unreadCount > 0
                    "Favourites" -> favourites.contains(chat.id)
                    "Groups" -> isGroup
                    else -> {
                        val custom = customLists.find { it.name == filter }
                        if (custom != null) custom.chatIds.contains(chat.id) else true
                    }
                }
            }
        }
    }

    val searchResults by remember(users, query, recentChats) {
        derivedStateOf {
            if (query.isEmpty()) emptyList<RealUser>()
            else users.filter { u ->
                u.name.contains(query, true) && recentChats.none { it.peerUid == u.uid }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState()) {
        if (displayList.isEmpty() && searchResults.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().fillParentMaxHeight(0.8f), 
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline, 
                            contentDescription = null, 
                            modifier = Modifier.size(80.dp), 
                            tint = ShynaDesign.colors.TextSecondary.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (query.isNotEmpty()) "No results for '$query'" else "No chats found in $filter", 
                            color = ShynaDesign.colors.TextSecondary
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = displayList,
            key = { _, chat -> chat.id }
        ) { index, chat ->
            val peer = users.find { it.uid == chat.peerUid }
            peer?.let {
                PremiumChatItem(
                    it, 
                    chat, 
                    isArchived = archived.contains(chat.id),
                    onClick = { onOpen(it.uid) },
                    onAvatarClick = { onAvatarClick(it) },
                    onToggleFav = { onToggleFav(chat.id) },
                    onArchive = { onArchive(chat.id) },
                    onUnarchive = { onUnarchive(chat.id) },
                    onMarkUnread = { onMarkUnread(chat.id) },
                    onDelete = { onDeleteChat(chat.id) }
                )
                if (index < displayList.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 84.dp), 
                        color = ShynaDesign.colors.DividerColor, 
                        thickness = 0.5.dp
                    )
                }
            }
        }

        if (searchResults.isNotEmpty()) {
            item {
                Text(
                    "GLOBAL SEARCH", 
                    modifier = Modifier.padding(16.dp), 
                    style = MaterialTheme.typography.labelMedium, 
                    color = ShynaDesign.colors.BrandGreen
                )
            }
            items(searchResults) { user ->
                PremiumChatItem(user, ChatRowItem("new", user.uid, "Start a new conversation", 0, 0, false), onClick = { onOpen(user.uid) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumChatItem(
    user: RealUser, 
    chat: ChatRowItem, 
    isArchived: Boolean = false,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit = {},
    onToggleFav: () -> Unit = {},
    onArchive: () -> Unit = {},
    onUnarchive: () -> Unit = {},
    onMarkUnread: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val unreadCount = remember(chat.unreadCount) { if (chat.unreadCount > 0) chat.unreadCount.toString() else null }
    val isCommunity = remember(user.name) { user.name.contains("Decathlon") }
    val dateStr = remember(chat.time) { formatChatDate(chat.time) }
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    modifier = Modifier.size(56.dp).clickable { onAvatarClick() },
                    shape = if (isCommunity) RoundedCornerShape(12.dp) else CircleShape,
                    color = if (isCommunity) Color(0xFFE8F5E9) else ShynaDesign.colors.DividerColor
                ) {
                    if (!user.photoUrl.isNullOrBlank()) {
                        AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            if (isCommunity) {
                                Icon(Icons.Default.Groups, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                            } else {
                                Text(user.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 20.sp)
                            }
                        }
                    }
                }
                if (user.isOnline && !isCommunity) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color(0xFF25D366), CircleShape)
                            .border(2.dp, ShynaDesign.colors.PrimaryBg, CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.name, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 18.sp, 
                        color = ShynaDesign.colors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        dateStr,
                        fontSize = 12.sp, 
                        color = if (unreadCount != null) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (chat.lastMessageMine && !isCommunity) {
                        MessageStatusTicks(chat.lastMessageStatus, true)
                        Spacer(Modifier.width(4.dp))
                    }
                    if (!isCommunity) {
                        val icon = when(chat.messageType) {
                            MessageType.IMAGE -> Icons.Default.Image
                            MessageType.VIDEO -> Icons.Default.Videocam
                            MessageType.VOICE -> Icons.Default.Mic
                            MessageType.LOCATION -> Icons.Default.LocationOn
                            MessageType.DOC -> Icons.Default.InsertDriveFile
                            else -> null
                        }
                        icon?.let { Icon(it, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                    }
                    Text(
                        if (isCommunity) "Decathlon skating community  ▶  जय..." else chat.lastMessage, 
                        fontSize = 14.sp, 
                        color = ShynaDesign.colors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.isPinned) {
                        Icon(Icons.Default.PushPin, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(14.dp).graphicsLayer(rotationZ = 45f))
                        if (unreadCount != null) Spacer(Modifier.width(8.dp))
                    }
                    if (unreadCount != null) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(ShynaDesign.colors.BrandGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(unreadCount, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (isCommunity) {
                        Icon(Icons.AutoMirrored.Default.KeyboardArrowRight, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(if (chat.isPinned) "Unfavourite" else "Mark as Favourite") },
                onClick = { onToggleFav(); showMenu = false },
                leadingIcon = { Icon(if (chat.isPinned) Icons.Default.StarOutline else Icons.Default.Star, null) }
            )
            DropdownMenuItem(
                text = { Text(if (isArchived) "Unarchive" else "Archive") },
                onClick = { if (isArchived) onUnarchive() else onArchive(); showMenu = false },
                leadingIcon = { Icon(if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) }
            )
            DropdownMenuItem(
                text = { Text("Mark as Unread") },
                onClick = { onMarkUnread(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.MarkChatUnread, null) }
            )
            DropdownMenuItem(
                text = { Text("Delete Chat", color = Color.Red) },
                onClick = { onDelete(); showMenu = false },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatesPage(
    currentUser: RealUser?,
    allUsers: List<RealUser>,
    statuses: List<UserStatus>,
    channels: List<ShynaChannel>,
    onAddStatus: () -> Unit,
    onOpenTextComposer: () -> Unit,
    onOpenMyStatusManager: () -> Unit,
    onOpenMyStatusViewer: () -> Unit,
    onOpenStatusViewerForGroup: (StatusGroup) -> Unit,
    onOpenPrivacySettings: () -> Unit,
    onOpenChannel: (ShynaChannel) -> Unit,
    onFindChannels: () -> Unit
) {
    val context = LocalContext.current
    val localStore = remember { com.example.callruleblocker.data.StatusLocalStore.getInstance(context) }
    val currentUid = currentUser?.uid ?: ""

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showMutedUpdates by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    
    // Automatic cleanup of current user's expired statuses from server
    LaunchedEffect(currentUid) {
        if (currentUid.isNotBlank()) {
            val db = FirebaseFirestore.getInstance()
            db.collection("statuses")
                .whereEqualTo("userId", currentUid)
                .get()
                .addOnSuccessListener { snapshot ->
                    val expiredIds = snapshot.documents.filter { 
                        val expires = it.getLong("expiresAt") ?: 0L
                        expires > 0 && expires < System.currentTimeMillis()
                    }.map { it.id }
                    
                    expiredIds.forEach { id ->
                        db.collection("statuses").document(id).delete()
                    }
                }
        }
    }

    val activeStatuses = remember(statuses) {
        statuses.filter { it.expiresAt > now && (it.deletedAt == null || it.deletedAt == 0L) }
    }

    val myActiveStatuses = remember(activeStatuses, currentUid) {
        activeStatuses.filter { it.userId == currentUid }.sortedBy { it.timestamp }
    }

    val seenStatusIds = remember { localStore.getSeenStatusIds() }
    val mutedUserIds = remember { localStore.getMutedUserIds() }

    val contactGroups = remember(activeStatuses, allUsers, currentUid, seenStatusIds, mutedUserIds, searchQuery) {
        val grouped = activeStatuses.filter { it.userId != currentUid }.groupBy { it.userId }
        grouped.mapNotNull { (uId, userStatusList) ->
            val user = allUsers.find { it.uid == uId }
            val userName = user?.name ?: userStatusList.firstOrNull()?.userName ?: "Contact"
            val userPhoto = user?.photoUrl ?: userStatusList.firstOrNull()?.userPhoto

            if (searchQuery.isNotBlank() && !userName.contains(searchQuery, ignoreCase = true)) {
                return@mapNotNull null
            }

            val sortedList = userStatusList.sortedBy { it.timestamp }
            val unreadCount = sortedList.count { s ->
                !seenStatusIds.contains(s.id) && !s.seenBy.contains(currentUid)
            }
            val firstUnreadIdx = sortedList.indexOfFirst { s ->
                !seenStatusIds.contains(s.id) && !s.seenBy.contains(currentUid)
            }.let { if (it >= 0) it else 0 }

            val isMuted = mutedUserIds.contains(uId)
            val isNotifEnabled = localStore.isNotificationEnabledForUser(uId)

            StatusGroup(
                userId = uId,
                userName = userName,
                userPhoto = userPhoto,
                statuses = sortedList,
                activeCount = sortedList.size,
                unreadCount = unreadCount,
                latestTimestamp = sortedList.lastOrNull()?.timestamp ?: 0L,
                firstUnreadIndex = firstUnreadIdx,
                isMuted = isMuted,
                notificationsEnabled = isNotifEnabled
            )
        }.sortedByDescending { it.latestTimestamp }
    }

    val recentGroups = remember(contactGroups) { contactGroups.filter { it.unreadCount > 0 && !it.isMuted } }
    val viewedGroups = remember(contactGroups) { contactGroups.filter { it.unreadCount == 0 && !it.isMuted } }
    val mutedGroups = remember(contactGroups) { contactGroups.filter { it.isMuted } }

    val goldAccent = Color(0xFFE5B800) // WhatsApp Gold Accent

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Clean Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search updates...", color = Color.Gray) },
                        leadingIcon = {
                            IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search", tint = Color.White)
                            }
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = goldAccent,
                            unfocusedBorderColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Updates",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        Box {
                            IconButton(onClick = { showTopMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showTopMenu,
                                onDismissRequest = { showTopMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Status privacy") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        onOpenPrivacySettings()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Status settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        onOpenPrivacySettings()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Section Title: Status
                item {
                    Text(
                        text = "Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
                    )
                }

                // Own "My status" Row
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "My status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        },
                        supportingContent = {
                            val statusSubtext = if (myActiveStatuses.isNotEmpty()) {
                                "${myActiveStatuses.size} active update${if (myActiveStatuses.size > 1) "s" else ""} • Disappears after 24 hours"
                            } else {
                                "Disappears after 24 hours"
                            }
                            Text(statusSubtext, color = Color.Gray, fontSize = 13.sp)
                        },
                        leadingContent = {
                            Box(contentAlignment = Alignment.Center) {
                                if (myActiveStatuses.isNotEmpty()) {
                                    DynamicSegmentedStatusRing(
                                        statuses = myActiveStatuses,
                                        seenStatusIds = seenStatusIds,
                                        currentUid = currentUid,
                                        unseenColor = goldAccent
                                    ) {
                                        Box {
                                            Surface(
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clickable { onOpenMyStatusViewer() },
                                                shape = CircleShape,
                                                color = Color.DarkGray
                                            ) {
                                                if (!currentUser?.photoUrl.isNullOrBlank()) {
                                                    AsyncImage(model = currentUser?.photoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                                                } else {
                                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.padding(12.dp))
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .align(Alignment.BottomEnd)
                                                    .background(goldAccent, CircleShape)
                                                    .border(2.dp, Color.Black, CircleShape)
                                                    .clickable { onAddStatus() },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(13.dp))
                                            }
                                        }
                                    }
                                } else {
                                    Box {
                                        Surface(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clickable { onAddStatus() },
                                            shape = CircleShape,
                                            color = Color.DarkGray
                                        ) {
                                            if (!currentUser?.photoUrl.isNullOrBlank()) {
                                                AsyncImage(model = currentUser?.photoUrl, contentDescription = null, contentScale = ContentScale.Crop)
                                            } else {
                                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.padding(14.dp))
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .align(Alignment.BottomEnd)
                                                .background(goldAccent, CircleShape)
                                                .border(2.dp, Color.Black, CircleShape)
                                                .clickable { onAddStatus() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            if (myActiveStatuses.isNotEmpty()) onOpenMyStatusViewer() else onAddStatus()
                        }
                    )
                }

                // Recent Updates Section
                if (recentGroups.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent updates",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                        )
                    }

                    items(recentGroups) { group ->
                        StatusContactRow(
                            group = group,
                            seenStatusIds = seenStatusIds,
                            currentUid = currentUid,
                            onClick = { onOpenStatusViewerForGroup(group) }
                        )
                    }
                }

                // Viewed Updates Section
                if (viewedGroups.isNotEmpty()) {
                    item {
                        Text(
                            text = "Viewed updates",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                        )
                    }

                    items(viewedGroups) { group ->
                        StatusContactRow(
                            group = group,
                            seenStatusIds = seenStatusIds,
                            currentUid = currentUid,
                            onClick = { onOpenStatusViewerForGroup(group) }
                        )
                    }
                }

                // Muted Updates Section
                if (mutedGroups.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMutedUpdates = !showMutedUpdates }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Muted updates (${mutedGroups.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                            Icon(
                                if (showMutedUpdates) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }
                    }

                    if (showMutedUpdates) {
                        items(mutedGroups) { group ->
                            StatusContactRow(
                                group = group,
                                seenStatusIds = seenStatusIds,
                                currentUid = currentUid,
                                onClick = { onOpenStatusViewerForGroup(group) }
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 16.dp))
                }

                // Channels Section Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Channels", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                        IconButton(onClick = onFindChannels) {
                            Icon(Icons.Default.Add, contentDescription = "Find Channels", tint = goldAccent)
                        }
                    }
                }

                if (channels.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No channels followed", color = Color.Gray)
                        }
                    }
                } else {
                    items(channels) { channel ->
                        ListItem(
                            headlineContent = { Text(channel.name, color = Color.White, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(channel.lastMessage, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = Color.DarkGray) {
                                    if (!channel.photoUrl.isNullOrBlank()) AsyncImage(channel.photoUrl, null, contentScale = ContentScale.Crop)
                                    else Icon(Icons.Default.Public, null, tint = Color.LightGray, modifier = Modifier.padding(12.dp))
                                }
                            },
                            trailingContent = {
                                val time = remember(channel.lastUpdateTime) { formatChatDate(channel.lastUpdateTime) }
                                Text(time, color = Color.Gray, fontSize = 12.sp)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onOpenChannel(channel) }
                        )
                    }
                }
            }
        }

        // Floating Action Buttons (FABs)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Small Pencil FAB
            FloatingActionButton(
                onClick = onOpenTextComposer,
                containerColor = Color(0xFF1F2C34),
                contentColor = Color.White,
                modifier = Modifier.size(44.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Text Status", modifier = Modifier.size(20.dp))
            }

            // Large Camera FAB
            FloatingActionButton(
                onClick = onAddStatus,
                containerColor = goldAccent,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Camera Status")
            }
        }
    }
}

@Composable
private fun StatusContactRow(
    group: StatusGroup,
    seenStatusIds: Set<String>,
    currentUid: String,
    onClick: () -> Unit
) {
    val formattedTime = remember(group.latestTimestamp) {
        val now = System.currentTimeMillis()
        val diff = now - group.latestTimestamp
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        when {
            diff < 60 * 1000L -> "Just now"
            diff < 60 * 60 * 1000L -> "${(diff / (60 * 1000L)).coerceAtLeast(1)} minutes ago"
            isSameDay(group.latestTimestamp, now) -> "Today, ${timeFormat.format(Date(group.latestTimestamp))}"
            isSameDay(group.latestTimestamp + 24 * 3600 * 1000L, now) -> "Yesterday, ${timeFormat.format(Date(group.latestTimestamp))}"
            else -> SimpleDateFormat("M/d/yy, h:mm a", Locale.getDefault()).format(Date(group.latestTimestamp))
        }
    }

    ListItem(
        headlineContent = {
            Text(group.userName, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(formattedTime, color = ShynaDesign.colors.TextSecondary, fontSize = 13.sp)
        },
        leadingContent = {
            DynamicSegmentedStatusRing(
                statuses = group.statuses,
                seenStatusIds = seenStatusIds,
                currentUid = currentUid
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = ShynaDesign.colors.DividerColor
                ) {
                    if (!group.userPhoto.isNullOrBlank()) {
                        AsyncImage(model = group.userPhoto, contentDescription = null, contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun StatusPrivacyDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { com.example.callruleblocker.data.StatusLocalStore.getInstance(context) }
    var selectedOption by remember { mutableStateOf(store.getPrivacyMode()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Status Privacy", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Who can see my status updates?", fontSize = 14.sp, color = ShynaDesign.colors.TextSecondary)
                Spacer(modifier = Modifier.height(16.dp))
                listOf(
                    "MY_CONTACTS" to "My contacts",
                    "EXCEPT" to "My contacts except...",
                    "ONLY_SHARE" to "Only share with..."
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedOption = mode
                                store.setPrivacyMode(mode)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == mode,
                            onClick = {
                                selectedOption = mode
                                store.setPrivacyMode(mode)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, color = ShynaDesign.colors.TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = ShynaDesign.colors.BrandGreen)
            }
        }
    )
}

@Composable
private fun CommunitiesPage(channels: List<ShynaChannel>, currentUser: RealUser?, onJoin: (ShynaChannel) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Start Community", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val c = ShynaChannel(name = name, description = desc)
                    db.collection("channels").document(c.id).set(c)
                    showCreateDialog = false
                }, enabled = name.isNotBlank()) { Text("Create") }
            },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Groups, null, Modifier.size(80.dp), tint = ShynaDesign.colors.BrandGreen.copy(0.3f))
            Spacer(Modifier.height(16.dp))
            Text("Stay connected with communities", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 20.sp, textAlign = TextAlign.Center)
            Text("Communities bring members together in topic-based groups.", color = ShynaDesign.colors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { showCreateDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) {
                Text("Start your community")
            }
        }
        
        HorizontalDivider(color = ShynaDesign.colors.DividerColor)
        Text("Suggested Communities", Modifier.padding(16.dp), color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold)
        
        LazyColumn(Modifier.weight(1f)) {
            items(channels) { channel ->
                ListItem(
                    headlineContent = { Text(channel.name, color = ShynaDesign.colors.TextPrimary) },
                    supportingContent = { Text(channel.description, color = ShynaDesign.colors.TextSecondary, maxLines = 1) },
                    leadingContent = { 
                        Surface(Modifier.size(48.dp), shape = RoundedCornerShape(12.dp), color = ShynaDesign.colors.DividerColor) {
                            if(!channel.photoUrl.isNullOrBlank()) AsyncImage(channel.photoUrl, null, contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Public, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(12.dp))
                        }
                    },
                    trailingContent = {
                        val isFollowing = currentUser?.followedChannels?.contains(channel.id) == true
                        if (isFollowing) {
                            Text("JOINED", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            TextButton(onClick = { onJoin(channel) }) {
                                Text("JOIN", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallsPage(
    userId: String, 
    allUsers: List<RealUser>, 
    searchQuery: String, 
    onNewCall: () -> Unit, 
    onOpenDialer: () -> Unit, 
    onOpenSchedule: () -> Unit,
    onStartMeeting: (MeetingItem, Boolean) -> Unit,
    onShareMeeting: (String) -> Unit
) {
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) } // 0: Calls, 1: Meetings
    
    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = ShynaDesign.colors.HeaderBg,
            contentColor = ShynaDesign.colors.BrandGreen,
            divider = { HorizontalDivider(color = ShynaDesign.colors.DividerColor, thickness = 0.5.dp) }
        ) {
            Tab(selectedSubTab == 0, onClick = { selectedSubTab = 0 }) {
                Text("Calls", modifier = Modifier.padding(16.dp), fontWeight = if(selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal)
            }
            Tab(selectedSubTab == 1, onClick = { selectedSubTab = 1 }) {
                Text("Meetings", modifier = Modifier.padding(16.dp), fontWeight = if(selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal)
            }
        }
        
        Box(Modifier.weight(1f)) {
            if (selectedSubTab == 0) {
                CallsListContent(userId, allUsers, searchQuery, onNewCall, onOpenDialer, onOpenSchedule)
            } else {
                MeetingsHubContent(userId, allUsers, searchQuery, onStartMeeting, onShareMeeting)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallsListContent(userId: String, allUsers: List<RealUser>, searchQuery: String, onNewCall: () -> Unit, onOpenDialer: () -> Unit, onOpenSchedule: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var history by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<RealUser>>(emptyList()) }
    val mContext = LocalContext.current
    
    val filteredHistory = remember(history, searchQuery) {
        if (searchQuery.isEmpty()) history
        else history.filter { 
            val name = (it["receiverName"] as? String ?: it["callerName"] as? String ?: "").lowercase()
            name.contains(searchQuery.lowercase())
        }
    }

    val searchResultsFromUsers = remember(allUsers, searchQuery) {
        if (searchQuery.isEmpty()) emptyList<RealUser>()
        else allUsers.filter { it.name.lowercase().contains(searchQuery.lowercase()) && it.uid != userId }
    }
    
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    DisposableEffect(userId, allUsers) {
        if (userId.isEmpty()) return@DisposableEffect onDispose { }

        val historyRegistration = db.collection("users").document(userId).collection("call_history")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "CALL_HISTORY_LISTENER_FAILED", error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    history = it.documents.map { d ->
                        (d.data ?: emptyMap()) + ("id" to d.id)
                    }
                }
            }

        val favoritesRegistration = db.collection("users").document(userId).collection("favorites")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "FAVORITES_LISTENER_FAILED", error)
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val favIds = it.documents.map { d -> d.id }
                    favorites = allUsers.filter { u -> favIds.contains(u.uid) }
                }
            }

        onDispose {
            historyRegistration.remove()
            favoritesRegistration.remove()
        }
    }

    BackHandler(isSelectionMode) {
        selectedIds = emptySet()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Action Row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                QuickActionItem("Call", Icons.Default.Call) { onNewCall() }
                Spacer(Modifier.width(24.dp))
                QuickActionItem("Schedule", Icons.Default.CalendarToday) { onOpenSchedule() }
                Spacer(Modifier.width(24.dp))
                QuickActionItem("Keypad", Icons.Default.Dialpad) { onOpenDialer() }
            }

            Text(
                "Recent", 
                Modifier.padding(start = 24.dp, bottom = 12.dp), 
                fontWeight = FontWeight.Bold, 
                color = Color.White, 
                fontSize = 20.sp
            )
            
            val groupedHistory = remember(filteredHistory) { groupCallHistory(filteredHistory) }

            if (history.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No recent calls", 
                        color = Color.Gray, 
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    // Search Results from Contacts
                    if (searchQuery.isNotEmpty() && searchResultsFromUsers.isNotEmpty()) {
                        item { Text("Contacts", Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen) }
                        items(searchResultsFromUsers) { user ->
                            ListItem(
                                headlineContent = { Text(user.name, color = ShynaDesign.colors.TextPrimary) },
                                leadingContent = { 
                                    Surface(Modifier.size(40.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                                        if (!user.photoUrl.isNullOrBlank()) AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
                                        else Icon(Icons.Default.Person, null, modifier = Modifier.padding(10.dp))
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { CallSignalingManager.startCall(mContext, user.uid, AppCallType.VOICE, { created -> mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) }) }, { e -> Toast.makeText(mContext, "Unable to start voice call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() }) }) { Icon(Icons.Default.Call, null, tint = ShynaDesign.colors.BrandGreen) }
                                        IconButton(onClick = { CallSignalingManager.startCall(mContext, user.uid, AppCallType.VIDEO, { created -> mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) }) }, { e -> Toast.makeText(mContext, "Unable to start video call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() }) }) { Icon(Icons.Default.Videocam, null, tint = ShynaDesign.colors.BrandGreen) }
                                    }
                                }
                            )
                        }
                        item { HorizontalDivider(Modifier.padding(vertical = 8.dp), color = ShynaDesign.colors.DividerColor, thickness = 0.5.dp) }
                    }

                    items(groupedHistory) { group ->
                        val mainCall = group.first()
                        val groupCount = group.size
                        
                        val id = mainCall["id"] as? String ?: ""
                        val type = mainCall["type"] as? String ?: "VOICE"
                        val status = mainCall["status"] as? String ?: "ENDED"
                        val direction = mainCall["direction"] as? String ?: "outgoing"
                        val name = mainCall["receiverName"] as? String ?: mainCall["callerName"] as? String ?: "Unknown"
                        val photo = if(direction == "outgoing") mainCall["receiverPhoto"] as? String else mainCall["callerPhoto"] as? String
                        val time = (mainCall["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                        val timeStr = remember(time) { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(time)) }
                        val isVideo = type == "VIDEO"
                        val isMissed = status in setOf("MISSED", "DECLINED", "NO_ANSWER", "REJECTED")
                        val peerUid = if(direction == "outgoing") mainCall["receiverUid"] as? String else mainCall["callerUid"] as? String
                        
                        val isSelected = selectedIds.contains(id)

                        ListItem(
                            headlineContent = { 
                                Text(
                                    text = if (groupCount > 1) "$name ($groupCount)" else name,
                                    color = if(isMissed && direction == "incoming") Color.Red else ShynaDesign.colors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = if(direction == "outgoing") Icons.Default.CallMade else if(isMissed) Icons.AutoMirrored.Default.CallMissed else Icons.AutoMirrored.Default.CallReceived
                                    Icon(icon, null, tint = if(isMissed) Color.Red else ShynaDesign.colors.BrandGreen, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(timeStr, color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                                }
                            },
                            leadingContent = { 
                                Box {
                                    Surface(
                                        Modifier.size(48.dp), 
                                        shape = CircleShape, 
                                        color = if(isSelected) ShynaDesign.colors.BrandGreen.copy(0.2f) else ShynaDesign.colors.DividerColor
                                    ) { 
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.padding(12.dp))
                                        } else if (!photo.isNullOrBlank()) {
                                            AsyncImage(photo, null, contentScale = ContentScale.Crop)
                                        } else {
                                            Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(12.dp)) 
                                        }
                                    } 
                                }
                            },
                            trailingContent = { 
                                if (!isSelectionMode) {
                                    IconButton(onClick = {
                                        if (peerUid != null) {
                                            CallSignalingManager.startCall(
                                                mContext, 
                                                peerUid, 
                                                if(isVideo) AppCallType.VIDEO else AppCallType.VOICE, 
                                                { created ->
                                                    mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) })
                                                }, 
                                                { e -> Toast.makeText(mContext, "Unable to start call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() }
                                            )
                                        }
                                    }) {
                                        Icon(if (isVideo) Icons.Default.Videocam else Icons.Default.Call, null, tint = ShynaDesign.colors.BrandGreen)
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if(isSelected) ShynaDesign.colors.SelectionOverlay else Color.Transparent
                            ),
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - id else selectedIds + id
                                    }
                                },
                                onLongClick = {
                                    selectedIds = selectedIds + id
                                }
                            )
                        )
                    }
                }
            }
        }

        // Selection Toolbar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).height(64.dp),
                shape = RoundedCornerShape(32.dp),
                color = ShynaDesign.colors.HeaderBg,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, ShynaDesign.colors.BrandGreen.copy(0.3f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(Icons.Default.Close, null, tint = ShynaDesign.colors.TextPrimary)
                    }
                    Text("${selectedIds.size} selected", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = { 
                            selectedIds = history.map { it["id"] as String }.toSet()
                        }) {
                            Icon(Icons.Default.SelectAll, null, tint = ShynaDesign.colors.TextPrimary)
                        }
                        IconButton(onClick = {
                            selectedIds.forEach { id ->
                                db.collection("users").document(userId).collection("call_history").document(id).delete()
                            }
                            selectedIds = emptySet()
                            Toast.makeText(mContext, "Call history deleted", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun exportChat(context: Context, name: String, msgs: List<UniversalMessage>) {
    try {
        val sdf = SimpleDateFormat("dd/MM/yyyy, hh:mm a", Locale.getDefault())
        val content = msgs.joinToString("\n") { m ->
            val typeLabel = if(m.messageType == MessageType.TEXT) "" else " [${m.messageType.name}]"
            "${sdf.format(Date(m.time))} - ${if(m.isMine) "Me" else name}: ${m.text}$typeLabel"
        }
        
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(context.cacheDir, "Chat_with_$sanitizedName.txt")
        
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                file.writeText(content)
                withContext(Dispatchers.Main) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val chooser = Intent.createChooser(intent, "Export Chat").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ShynaLink", "File write failed", e)
                    Toast.makeText(context, "Failed to save export file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } catch (e: Exception) {
        Log.e("ShynaLink", "Export failed", e)
        Toast.makeText(context, "Failed to export chat", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun QuickActionItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = Color(0xFFE5B800), // Gold background from screenshot
            onClick = onClick,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.Black, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            label, 
            fontSize = 13.sp, 
            color = Color.White, 
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

private val meetingSecureRandom = java.security.SecureRandom()

private fun generateMeetingId(): String {
    // Ten digits, first digit non-zero for Shyna readable ID.
    return buildString {
        append((1 + meetingSecureRandom.nextInt(9)))
        repeat(9) { append(meetingSecureRandom.nextInt(10)) }
    }
}

private fun generateMeetingPasscode(): String =
    buildString { repeat(6) { append(meetingSecureRandom.nextInt(10)) } }

private fun formatMeetingIdForDisplay(value: String): String {
    val clean = value.filter { it.isDigit() }
    return if (clean.length == 10) "${clean.substring(0, 3)} ${clean.substring(3, 6)} ${clean.substring(6, 10)}" else value
}

private fun createMeetingDocumentAtomically(
    db: FirebaseFirestore,
    meeting: MeetingItem,
    onSuccess: (MeetingItem) -> Unit,
    onError: (Exception) -> Unit
) {
    val cleanId = meeting.meetingId.filter { it.isDigit() }
    if (cleanId.isBlank()) {
        onError(IllegalArgumentException("Invalid Meeting ID"))
        return
    }
    val normalized = meeting.copy(id = cleanId, meetingId = cleanId)
    val ref = db.collection("meetings").document(cleanId)
    db.runTransaction { tx ->
        val existing = tx.get(ref)
        if (existing.exists()) throw IllegalStateException("Meeting ID already exists")
        tx.set(ref, normalized)
        normalized
    }.addOnSuccessListener(onSuccess)
        .addOnFailureListener(onError)
}

@Composable
private fun MeetingsHubContent(
    userId: String,
    allUsers: List<RealUser>,
    searchQuery: String,
    onStartMeeting: (MeetingItem, Boolean) -> Unit,
    onShareToChat: (String) -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val activeUid = userId.ifBlank { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    var hostedMeetings by remember { mutableStateOf<List<MeetingItem>>(emptyList()) }
    var invitedMeetings by remember { mutableStateOf<List<MeetingItem>>(emptyList()) }
    var showNewMeeting by remember { mutableStateOf(false) }
    var showJoinMeeting by remember { mutableStateOf(false) }
    var showScheduleMeeting by remember { mutableStateOf(false) }
    var shareScreenAfterCreate by remember { mutableStateOf(false) }
    var meetingActionBusy by remember { mutableStateOf(false) }
    var selectedMeetingTab by rememberSaveable { mutableIntStateOf(0) }

    val currentUser = remember(allUsers, activeUid) { allUsers.find { it.uid == activeUid } }
    val pmi = remember(activeUid) {
        val digits = activeUid.filter { it.isDigit() }
        val seed = activeUid.hashCode().toLong().let { if (it < 0) -it else it }.toString()
        val raw = if (digits.length >= 10) digits.take(10) else (seed + "9876543210").padEnd(10, '8').take(10)
        "${raw.substring(0, 3)} ${raw.substring(3, 6)} ${raw.substring(6, 10)}"
    }

    val meetingsList = remember(hostedMeetings, invitedMeetings, searchQuery) {
        val combined = (hostedMeetings + invitedMeetings).distinctBy { it.id }
        if (searchQuery.isBlank()) combined
        else combined.filter {
            it.title.contains(searchQuery, true) || it.meetingId.contains(searchQuery.filter { ch -> ch.isDigit() })
        }
    }

    DisposableEffect(activeUid) {
        if (activeUid.isBlank()) return@DisposableEffect onDispose { }

        val invitedRegistration = db.collection("meetings")
            .whereArrayContains("invitees", activeUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "MEETING_INVITED_LISTENER_FAILED", error)
                    return@addSnapshotListener
                }
                invitedMeetings = snapshot?.documents?.mapNotNull { it.toObject(MeetingItem::class.java) }.orEmpty()
            }

        val hostedRegistration = db.collection("meetings")
            .whereEqualTo("hostUid", activeUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "MEETING_HOSTED_LISTENER_FAILED", error)
                    return@addSnapshotListener
                }
                hostedMeetings = snapshot?.documents?.mapNotNull { it.toObject(MeetingItem::class.java) }.orEmpty()
            }

        onDispose {
            invitedRegistration.remove()
            hostedRegistration.remove()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(ShynaDesign.colors.PrimaryBg)) {
            if (meetingActionBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ScrollableTabRow(
                selectedTabIndex = selectedMeetingTab,
                containerColor = Color.Transparent,
                contentColor = ShynaDesign.colors.BrandGreen,
                edgePadding = 16.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedMeetingTab]),
                        color = ShynaDesign.colors.BrandGreen
                    )
                }
            ) {
                listOf("Home", "My Meetings", "History").forEachIndexed { index, title ->
                    Tab(selectedMeetingTab == index, onClick = { selectedMeetingTab = index }) {
                        Text(title, modifier = Modifier.padding(16.dp), fontSize = 14.sp)
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                when (selectedMeetingTab) {
                    0 -> MeetingHomeContent(
                        onNew = {
                            shareScreenAfterCreate = false
                            showNewMeeting = true
                        },
                        onJoin = { showJoinMeeting = true },
                        onSchedule = { showScheduleMeeting = true },
                        onShareScreen = {
                            shareScreenAfterCreate = true
                            showNewMeeting = true
                        }
                    )
                    1 -> MyMeetingsContent(
                        meetings = meetingsList,
                        currentUid = userId,
                        onOpenMeeting = { meeting ->
                            if (userId.isBlank()) {
                                Toast.makeText(context, "Please login to open a meeting", Toast.LENGTH_LONG).show()
                            } else if (meeting.hostUid == userId && meeting.status == "UPCOMING") {
                                val now = System.currentTimeMillis()
                                db.collection("meetings").document(meeting.id).update(
                                    mapOf("status" to "LIVE", "actualStartTime" to now, "updatedAt" to now)
                                ).addOnSuccessListener {
                                    onStartMeeting(meeting.copy(status = "LIVE", actualStartTime = now, updatedAt = now), false)
                                }.addOnFailureListener { e ->
                                    Toast.makeText(context, "Unable to start meeting: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            } else if (meeting.status == "LIVE") {
                                CallSignalingManager.joinMeeting(
                                    context = context,
                                    userUid = userId,
                                    meetingId = meeting.meetingId,
                                    passcode = meeting.passcode,
                                    onJoined = { call ->
                                        context.startActivity(Intent(context, AppCallActivity::class.java).apply {
                                            putExtra("callId", call.id)
                                            putExtra("isIncoming", false)
                                            putExtra("isMeeting", true)
                                            putExtra("initialMute", if (meeting.hostUid == userId) !meeting.hostAudioOn else meeting.muteOnEntry)
                                            putExtra("initialVideoOff", if (meeting.hostUid == userId) !meeting.hostVideoOn else !meeting.participantVideoOn)
                                        })
                                    },
                                    onError = { e -> Toast.makeText(context, "Unable to join meeting: ${e.message}", Toast.LENGTH_LONG).show() }
                                )
                            } else {
                                Toast.makeText(context, "Meeting has not started yet", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShare = onShareToChat
                    )
                    2 -> MeetingHistoryContent(meetingsList)
                }
            }
        }

        if (showNewMeeting) {
            NewMeetingScreen(
                pmi = pmi,
                onBack = {
                    showNewMeeting = false
                    shareScreenAfterCreate = false
                    meetingActionBusy = false
                },
                onStart = { requestedId, passcode, videoOn, microphoneOn ->
                    if (meetingActionBusy) return@NewMeetingScreen
                    
                    val auth = FirebaseAuth.getInstance()
                    val user = auth.currentUser ?: FirebaseAuth.getInstance().currentUser
                    val effectiveUid = user?.uid ?: activeUid
                    if (effectiveUid.isBlank()) {
                        Toast.makeText(context, "Please login again to continue.", Toast.LENGTH_LONG).show()
                        meetingActionBusy = false
                        return@NewMeetingScreen
                    }
                    
                    meetingActionBusy = true
                    val cleanId = requestedId.filter { it.isDigit() }
                    
                    Log.d("ShynaMeeting", "START_MEETING: id=$cleanId uid=$effectiveUid")
                    
                    val item = MeetingItem(
                        id = cleanId,
                        meetingId = cleanId,
                        title = "${currentUser?.name ?: "User"}'s Shyna Meeting",
                        hostUid = effectiveUid,
                        hostName = currentUser?.name ?: "Host",
                        passcode = passcode,
                        hostVideoOn = videoOn,
                        hostAudioOn = microphoneOn,
                        status = "LIVE",
                        startAt = System.currentTimeMillis(),
                        actualStartTime = System.currentTimeMillis(),
                        invitees = listOf(effectiveUid),
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    // Direct creation attempt. If document exists, it will trigger addOnFailureListener or be handled by transaction.
                    db.collection("meetings").document(cleanId).set(item)
                        .addOnSuccessListener {
                            Log.d("ShynaMeeting", "MEETING_CREATED_SUCCESS: $cleanId")
                            meetingActionBusy = false
                            showNewMeeting = false
                            onStartMeeting(item, shareScreenAfterCreate)
                            shareScreenAfterCreate = false
                        }
                        .addOnFailureListener { e ->
                            Log.e("ShynaMeeting", "MEETING_CREATE_FAILED: ${e.message}", e)
                            if (e.message?.contains("permission") == true) {
                                meetingActionBusy = false
                                Toast.makeText(context, "Meeting permission error. Please ensure rules are published.", Toast.LENGTH_LONG).show()
                                return@addOnFailureListener
                            }
                            
                            // Try atomic transaction if set failed for other reasons
                            createMeetingDocumentAtomically(
                                db = db,
                                meeting = item,
                                onSuccess = { createdMeeting ->
                                    meetingActionBusy = false
                                    showNewMeeting = false
                                    onStartMeeting(createdMeeting, shareScreenAfterCreate)
                                    shareScreenAfterCreate = false
                                },
                                onError = { transError ->
                                    meetingActionBusy = false
                                    val msg = when {
                                        transError.message?.contains("permission") == true -> "Permission Denied. Please ensure Firestore rules are deployed."
                                        transError.message?.contains("exists") == true -> "Meeting ID already exists. Tap New Meeting ID."
                                        else -> "Unable to start meeting: ${transError.message}"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                }
            )
        }

        if (showJoinMeeting) {
            JoinMeetingScreen(
                initialName = currentUser?.name ?: "",
                onBack = { 
                    showJoinMeeting = false 
                    meetingActionBusy = false
                },
                onJoin = { rawId, passcode, noAudio, noVideo ->
                    if (meetingActionBusy) return@JoinMeetingScreen
                    val cleanId = rawId.filter { it.isDigit() }
                    val effectiveUid = activeUid.ifBlank { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
                    if (effectiveUid.isBlank()) {
                        Toast.makeText(context, "Please login to join a meeting", Toast.LENGTH_LONG).show()
                        meetingActionBusy = false
                        return@JoinMeetingScreen
                    }
                    meetingActionBusy = true
                    db.collection("meetings").document(cleanId).get()
                        .addOnSuccessListener { doc ->
                            val item = doc?.toObject(MeetingItem::class.java)
                            if (!doc.exists() || item == null) {
                                meetingActionBusy = false
                                Toast.makeText(context, "Invalid Meeting ID", Toast.LENGTH_LONG).show()
                                return@addOnSuccessListener
                            }
                            if (item.status != "LIVE") {
                                meetingActionBusy = false
                                val msg = if (item.status == "ENDED" || item.status == "CANCELLED") "Meeting Ended" else "Meeting Not Started"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                return@addOnSuccessListener
                            }
                            if (item.passcode.isNotBlank() && item.passcode != passcode) {
                                meetingActionBusy = false
                                Toast.makeText(context, "Incorrect Password", Toast.LENGTH_LONG).show()
                                return@addOnSuccessListener
                            }

                            doc.reference.update(
                                mapOf(
                                    "invitees" to FieldValue.arrayUnion(userId),
                                    "updatedAt" to System.currentTimeMillis()
                                )
                            ).addOnCompleteListener {
                                CallSignalingManager.joinMeeting(
                                    context = context,
                                    userUid = userId,
                                    meetingId = cleanId,
                                    passcode = passcode,
                                    onJoined = { call ->
                                        meetingActionBusy = false
                                        showJoinMeeting = false
                                        context.startActivity(Intent(context, AppCallActivity::class.java).apply {
                                            putExtra("callId", call.id)
                                            putExtra("isIncoming", false)
                                            putExtra("isMeeting", true)
                                            putExtra("initialMute", noAudio)
                                            putExtra("initialVideoOff", noVideo)
                                        })
                                    },
                                    onError = { e ->
                                        meetingActionBusy = false
                                        Toast.makeText(context, "Unable to join meeting: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                        .addOnFailureListener { e ->
                            meetingActionBusy = false
                            Toast.makeText(context, "Meeting lookup failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            )
        }

        if (showScheduleMeeting) {
            ScheduleMeetingScreen(
                pmi = pmi,
                initialTitle = "${currentUser?.name ?: "User"}'s Scheduled Meeting",
                onBack = { 
                    showScheduleMeeting = false 
                    meetingActionBusy = false
                },
                onDone = { scheduled ->
                    val cleanScheduledId = scheduled.meetingId.filter { it.isDigit() }
                    val effectiveUid = activeUid.ifBlank { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
                    val normalized = scheduled.copy(
                        id = cleanScheduledId,
                        meetingId = cleanScheduledId,
                        hostUid = effectiveUid,
                        hostName = currentUser?.name ?: "Host",
                        invitees = (scheduled.invitees + effectiveUid).distinct(),
                        updatedAt = System.currentTimeMillis()
                    )
                    meetingActionBusy = true
                    
                    createMeetingDocumentAtomically(
                        db = db,
                        meeting = normalized,
                        onSuccess = {
                            meetingActionBusy = false
                            showScheduleMeeting = false
                            Toast.makeText(context, "Meeting scheduled", Toast.LENGTH_SHORT).show()
                        },
                        onError = { e ->
                            meetingActionBusy = false
                            val msg = if (e.message?.contains("exists") == true) "Meeting ID already in use. Choose a new ID." else "Unable to schedule meeting: ${e.message}"
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun MeetingHomeContent(
    onNew: () -> Unit,
    onJoin: () -> Unit,
    onSchedule: () -> Unit,
    onShareScreen: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MeetingHubAction("New Meeting", Icons.Default.VideoCall, ShynaDesign.colors.BrandGreen, onNew)
            MeetingHubAction("Join", Icons.Default.AddBox, Color(0xFF2196F3), onJoin)
        }
        Spacer(Modifier.height(40.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MeetingHubAction("Schedule", Icons.Default.CalendarToday, Color(0xFFFF9800), onSchedule)
            MeetingHubAction("Share Screen", Icons.Default.PresentToAll, Color(0xFF9C27B0), onShareScreen)
        }
        
        Spacer(Modifier.height(60.dp))
        Text(
            "Start or join secure Shyna meetings with high-quality audio and video.",
            color = ShynaDesign.colors.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun MeetingHubAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(20.dp),
            color = color,
            onClick = onClick,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(label, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
private fun MyMeetingsContent(
    meetings: List<MeetingItem>,
    currentUid: String,
    onOpenMeeting: (MeetingItem) -> Unit,
    onShare: (String) -> Unit
) {
    val myMeetings = meetings.filter { it.status == "UPCOMING" || it.status == "LIVE" }
    if (myMeetings.isEmpty()) {
        EmptyMeetingPlaceholder("No upcoming meetings", "Scheduled meetings will appear here")
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(myMeetings) { m ->
                MeetingInvitationCard(m, currentUid == m.hostUid, onStart = { onOpenMeeting(m) }, onShareToChat = onShare)
            }
        }
    }
}

@Composable
private fun MeetingHistoryContent(meetings: List<MeetingItem>) {
    val history = meetings.filter { it.status == "ENDED" || it.status == "CANCELLED" }.sortedByDescending { it.startAt }
    if (history.isEmpty()) {
        EmptyMeetingPlaceholder("No meeting history", "Past meetings will be listed here")
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(history) { m ->
                MeetingHistoryItem(m)
            }
        }
    }
}

@Composable
private fun MeetingRecordingsContent() {
    EmptyMeetingPlaceholder("No recordings", "Recorded meeting videos will be saved here")
}

@Composable
private fun MeetingHistoryItem(meeting: MeetingItem) {
    val dateStr = remember(meeting.startAt) { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(meeting.startAt)) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Color.Gray.copy(0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.History, null, tint = Color.Gray)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(meeting.title, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                Text(dateStr, fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
            }
            Text(meeting.status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(meeting.status == "ENDED") Color.Gray else Color.Red)
        }
    }
}

@Composable
private fun MeetingInvitationCard(
    meeting: MeetingItem, 
    isHost: Boolean, 
    onStart: () -> Unit,
    onShareToChat: (String) -> Unit
) {
    val mContext = LocalContext.current
    val dateStr = remember(meeting.startAt) { SimpleDateFormat("EEEE, dd MMM • hh:mm a", Locale.getDefault()).format(Date(meeting.startAt)) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(meeting.title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = ShynaDesign.colors.TextPrimary)
                if (meeting.status == "LIVE") {
                    Surface(color = Color.Red, shape = RoundedCornerShape(4.dp)) {
                        Text("LIVE", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Host: ${meeting.hostName}", fontSize = 13.sp, color = ShynaDesign.colors.TextSecondary)
            Text(dateStr, fontSize = 13.sp, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Medium)
            
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = ShynaDesign.colors.DividerColor)
            Spacer(Modifier.height(16.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Meeting ID", fontSize = 11.sp, color = ShynaDesign.colors.TextSecondary)
                    Text(meeting.meetingId, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, letterSpacing = 1.sp)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        val invite = "Join Shyna Meeting: ${meeting.title}\nID: ${meeting.meetingId}\nPasscode: ${meeting.passcode}\nLink: https://shyna.app/join/${meeting.meetingId}"
                        val cb = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cb.setPrimaryClip(android.content.ClipData.newPlainText("Invite", invite))
                        Toast.makeText(mContext, "Invitation copied", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.Share, null, tint = ShynaDesign.colors.BrandGreen) }
                    
                    IconButton(onClick = {
                        val invite = "Join Shyna Meeting: ${meeting.title}\nID: ${meeting.meetingId}\nPasscode: ${meeting.passcode}\nLink: https://shyna.app/join/${meeting.meetingId}"
                        onShareToChat(invite)
                    }) { Icon(Icons.Default.Send, null, tint = ShynaDesign.colors.BrandGreen) }
                    
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = if(meeting.status == "LIVE") Color.Red else ShynaDesign.colors.BrandGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if(isHost) "START" else "JOIN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun groupCallHistory(history: List<Map<String, Any>>): List<List<Map<String, Any>>> {
    if (history.isEmpty()) return emptyList()
    val grouped = mutableListOf<MutableList<Map<String, Any>>>()
    var currentGroup = mutableListOf(history[0])
    
    for (i in 1 until history.size) {
        val current = history[i]
        val prev = history[i - 1]
        
        val currentPeer = if (current["direction"] == "outgoing") current["receiverUid"] else current["callerUid"]
        val prevPeer = if (prev["direction"] == "outgoing") prev["receiverUid"] else prev["callerUid"]
        
        val currentType = current["type"]
        val prevType = prev["type"]
        
        val currentStatus = current["status"]
        val prevStatus = prev["status"]

        if (currentPeer == prevPeer && currentType == prevType && currentStatus == prevStatus) {
            currentGroup.add(current)
        } else {
            grouped.add(currentGroup)
            currentGroup = mutableListOf(current)
        }
    }
    grouped.add(currentGroup)
    return grouped
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeerDetailScreen(
    user: RealUser,
    allUsers: List<RealUser>,
    db: FirebaseFirestore,
    auth: FirebaseAuth,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onSearchInChat: () -> Unit,
    onMediaClick: () -> Unit
) {
    val design = ShynaDesign.colors
    val mContext = LocalContext.current
    val currentUid = auth.currentUser?.uid ?: ""

    var showBlockConfirm by remember { mutableStateOf(false) }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, null, tint = Color.Red)
                    Spacer(Modifier.width(12.dp))
                    Text("Block ${user.name}?", fontWeight = FontWeight.Bold)
                }
            },
            text = { Text("Blocked contacts will no longer be able to call you or send you messages.") },
            confirmButton = {
                Button(
                    onClick = {
                        val cUid = auth.currentUser?.uid ?: return@Button
                        db.collection("users").document(cUid).collection("blockedUsers").document(user.uid)
                            .set(mapOf("blockedAt" to Timestamp.now(), "name" to user.name))
                        Toast.makeText(mContext, "${user.name} blocked", Toast.LENGTH_SHORT).show()
                        showBlockConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Block", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) { Text("Cancel", color = design.TextSecondary) }
            },
            containerColor = design.SurfaceBg,
            shape = RoundedCornerShape(28.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Info", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = design.HeaderBg)
            )
        },
        containerColor = design.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                if (!user.photoUrl.isNullOrBlank()) {
                    AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(design.DividerColor), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(100.dp), tint = design.TextSecondary)
                    }
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))))
                Text(
                    user.name, 
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                    color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                DetailAction(Icons.AutoMirrored.Outlined.Message, "Message") { onMessage() }
                DetailAction(Icons.Outlined.Call, "Audio") {
                    CallSignalingManager.startCall(mContext, user.uid, AppCallType.VOICE, { created -> mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) }) }, { e -> Toast.makeText(mContext, "Unable to start voice call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() })
                }
                DetailAction(Icons.Outlined.Videocam, "Video") {
                    CallSignalingManager.startCall(mContext, user.uid, AppCallType.VIDEO, { created -> mContext.startActivity(Intent(mContext, AppCallActivity::class.java).apply { putExtra("callId", created.id); putExtra("isIncoming", false) }) }, { e -> Toast.makeText(mContext, "Unable to start video call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() })
                }
                DetailAction(Icons.Outlined.Search, "Search") { onSearchInChat() }
            }
            
            Spacer(Modifier.height(24.dp))
            
            ProfileItem("User ID", user.userId, Icons.Outlined.AlternateEmail) {
                val clipboard = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("User ID", user.userId))
                Toast.makeText(mContext, "User ID copied", Toast.LENGTH_SHORT).show()
            }
            ProfileItem("Phone", user.phone.ifBlank { "Not provided" }, Icons.Outlined.Phone) {
                if (user.phone.isNotBlank()) { runCatching { mContext.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${user.phone}"))) } }
            }
            ProfileItem("Location", "${user.district ?: "Unknown"}, ${user.state ?: ""}", Icons.Outlined.LocationOn) {
                if (user.district != null) { runCatching { mContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${user.district},${user.state}"))) } }
            }
            
            Spacer(Modifier.height(16.dp))
            ProfileItem("Media, links, and docs", "View shared files", Icons.Default.PermMedia) { onMediaClick() }
            ProfileItem("Block ${user.name}", "Report or block this contact", Icons.Outlined.Block) { showBlockConfirm = true }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(icon, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(24.dp))
        Text(label, color = ShynaDesign.colors.BrandGreen, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MuteDialog(onDismiss: () -> Unit, onMute: (Int) -> Unit) {
    val options = listOf("8 hours" to 8, "1 week" to 168, "Always" to -1)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mute notifications for...", color = ShynaDesign.colors.TextPrimary) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().clickable { onMute(value) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = false, onClick = { onMute(value) }, colors = RadioButtonDefaults.colors(selectedColor = ShynaDesign.colors.BrandGreen))
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = ShynaDesign.colors.TextPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewGroupScopedScreen(peer: RealUser, allUsers: List<RealUser>, onBack: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    var groupName by remember { mutableStateOf("") }
    val selectedUids = remember { mutableStateListOf(peer.uid) }
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = { if(groupName.isNotBlank()) onCreate(groupName, selectedUids.toList()) }) {
                        Text("CREATE", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(16.dp)) {
            RoyalTextField(groupName, { groupName = it }, "Group Name", Icons.Outlined.Group)
            Spacer(Modifier.height(24.dp))
            Text("Participants: ${selectedUids.size}", color = ShynaDesign.colors.TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(allUsers.filter { it.uid != currentUid }) { user ->
                    val isSelected = selectedUids.contains(user.uid)
                    ListItem(
                        headlineContent = { Text(user.name, color = ShynaDesign.colors.TextPrimary) },
                        leadingContent = { 
                            Surface(Modifier.size(40.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                                if(!user.photoUrl.isNullOrBlank()) AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
                                else Icon(Icons.Default.Person, null, modifier = Modifier.padding(8.dp))
                            }
                        },
                        trailingContent = {
                            Checkbox(isSelected, { if(it) selectedUids.add(user.uid) else if(user.uid != peer.uid) selectedUids.remove(user.uid) })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { 
                            if(isSelected) { if(user.uid != peer.uid) selectedUids.remove(user.uid) } 
                            else selectedUids.add(user.uid)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomMeetingsPage(
    currentUid: String,
    allUsers: List<RealUser>,
    globalSearchQuery: String = "",
    onStartMeeting: (MeetingItem, Boolean) -> Unit,
    onShareToChat: (String) -> Unit
) {
    MeetingsHubContent(currentUid, allUsers, globalSearchQuery, onStartMeeting, onShareToChat)
}

@Composable
private fun PersonalIdContent(
    currentUser: RealUser?,
    pmi: String,
    onStartMeeting: (MeetingItem, Boolean) -> Unit,
    currentUid: String,
    db: FirebaseFirestore
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()), 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = ShynaDesign.colors.BrandGreen.copy(alpha = 0.15f), modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center) { 
                Icon(Icons.Default.Person, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(40.dp)) 
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(currentUser?.name ?: "User", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ShynaDesign.colors.TextPrimary)
        Text("Personal Meeting Room", color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = ShynaDesign.colors.SurfaceBg, border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Personal Meeting ID (PMI)", fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Text(pmi, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = ShynaDesign.colors.BrandGreen, letterSpacing = 2.sp)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = ShynaDesign.colors.DividerColor)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { 
                    Text("Passcode:", color = ShynaDesign.colors.TextSecondary)
                    Text("123456", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) 
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { 
                val mItem = MeetingItem(
                    id = UUID.randomUUID().toString(),
                    meetingId = pmi.replace(" ", ""),
                    title = "${currentUser?.name ?: "User"}'s Personal Meeting Room",
                    hostUid = currentUid,
                    hostName = currentUser?.name ?: "Host",
                    status = "LIVE"
                )
                db.collection("meetings").document(mItem.id).set(mItem)
                onStartMeeting(mItem, false)
            }, 
            modifier = Modifier.fillMaxWidth().height(52.dp), 
            shape = RoundedCornerShape(26.dp), 
            colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
        ) {
            Text("Start Personal Meeting", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinMeetingScreen(
    initialName: String,
    onBack: () -> Unit,
    onJoin: (String, String, Boolean, Boolean) -> Unit
) {
    var meetingId by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(initialName) }
    var noAudio by remember { mutableStateOf(false) }
    var noVideo by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(ShynaDesign.colors.PrimaryBg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Surface(modifier = Modifier.fillMaxWidth(), color = ShynaDesign.colors.HeaderBg) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ShynaDesign.colors.BrandGreen)
                    }
                    Text("Join a meeting", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(modifier = Modifier.fillMaxWidth(), color = ShynaDesign.colors.SurfaceBg, border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)) {
                Column {
                    TextField(
                        value = meetingId,
                        onValueChange = { meetingId = it.filter { ch -> ch.isDigit() }.take(12) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Meeting ID", color = ShynaDesign.colors.TextSecondary) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = ShynaDesign.colors.TextPrimary,
                            unfocusedTextColor = ShynaDesign.colors.TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = ShynaDesign.colors.BrandGreen
                        )
                    )
                    HorizontalDivider(color = ShynaDesign.colors.DividerColor)
                    TextField(
                        value = passcode,
                        onValueChange = { passcode = it.filterNot(Char::isWhitespace).take(20) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Meeting passcode", color = ShynaDesign.colors.TextSecondary) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = ShynaDesign.colors.TextPrimary,
                            unfocusedTextColor = ShynaDesign.colors.TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = ShynaDesign.colors.BrandGreen
                        )
                    )
                    HorizontalDivider(color = ShynaDesign.colors.DividerColor)
                    TextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Your name", color = ShynaDesign.colors.TextSecondary) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = ShynaDesign.colors.TextPrimary,
                            unfocusedTextColor = ShynaDesign.colors.TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = ShynaDesign.colors.BrandGreen
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onJoin(meetingId.filter { it.isDigit() }, passcode, noAudio, noVideo) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (meetingId.isNotBlank()) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.DividerColor,
                    contentColor = if (meetingId.isNotBlank()) Color.White else ShynaDesign.colors.TextSecondary
                ),
                enabled = meetingId.isNotBlank()
            ) {
                Text("Join", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Text(
                "Enter the Meeting ID and passcode shared by the host.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
                color = ShynaDesign.colors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Text("Join options", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = ShynaDesign.colors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Surface(modifier = Modifier.fillMaxWidth(), color = ShynaDesign.colors.SurfaceBg, border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Don't connect to audio", color = ShynaDesign.colors.TextPrimary)
                        Switch(checked = noAudio, onCheckedChange = { noAudio = it }, colors = SwitchDefaults.colors(checkedTrackColor = ShynaDesign.colors.BrandGreen))
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = ShynaDesign.colors.DividerColor, thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Turn off my video", color = ShynaDesign.colors.TextPrimary)
                        Switch(checked = noVideo, onCheckedChange = { noVideo = it }, colors = SwitchDefaults.colors(checkedTrackColor = ShynaDesign.colors.BrandGreen))
                    }
                }
            }
        }
    }
    BackHandler(onBack = onBack)
}

@Composable
private fun ScheduleMeetingScreen(
    pmi: String,
    initialTitle: String,
    onBack: () -> Unit,
    onDone: (MeetingItem) -> Unit
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var usePmi by rememberSaveable { mutableStateOf(false) }
    var meetingId by rememberSaveable { mutableStateOf(generateMeetingId()) }
    var requirePasscode by rememberSaveable { mutableStateOf(true) }
    var passcode by rememberSaveable { mutableStateOf(generateMeetingPasscode()) }
    var muteOnEntry by rememberSaveable { mutableStateOf(true) }
    var hostVideoOn by rememberSaveable { mutableStateOf(true) }
    var participantVideoOn by rememberSaveable { mutableStateOf(true) }
    var durationMinutes by rememberSaveable { mutableIntStateOf(45) }
    var startAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis() + 60 * 60 * 1000L) }

    val effectiveId = if (usePmi) pmi.filter { it.isDigit() } else meetingId
    val dateText = remember(startAt) { SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(startAt)) }
    val timeText = remember(startAt) { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(startAt)) }
    val endText = remember(startAt, durationMinutes) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(startAt + durationMinutes * 60_000L))
    }

    fun pickDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = startAt }
        android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = startAt
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                startAt = updated.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun pickTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = startAt }
        android.app.TimePickerDialog(
            context,
            { _, hour, minute ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = startAt
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startAt = updated.timeInMillis
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false
        ).show()
    }

    Scaffold(
        topBar = {
            Surface(color = ShynaDesign.colors.HeaderBg, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text("Cancel", color = ShynaDesign.colors.BrandGreen, modifier = Modifier.clickable { onBack() })
                    Text("Schedule meeting", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "Done",
                        color = if (title.isNotBlank() && effectiveId.isNotBlank()) ShynaDesign.colors.BrandGreen else ShynaDesign.colors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = title.isNotBlank() && effectiveId.isNotBlank()) {
                            val now = System.currentTimeMillis()
                            if (startAt <= now) {
                                Toast.makeText(context, "Select a future meeting time", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            onDone(
                                MeetingItem(
                                    id = UUID.randomUUID().toString(),
                                    meetingId = effectiveId,
                                    title = title.trim(),
                                    status = "UPCOMING",
                                    startAt = startAt,
                                    durationMinutes = durationMinutes,
                                    passcode = if (requirePasscode) passcode else "",
                                    waitingRoomEnabled = false,
                                    muteOnEntry = muteOnEntry,
                                    hostAudioOn = true,
                                    hostVideoOn = hostVideoOn,
                                    participantVideoOn = participantVideoOn,
                                    createdAt = now,
                                    updatedAt = now
                                )
                            )
                        }
                    )
                }
            }
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState()).background(ShynaDesign.colors.PrimaryBg)) {
            TextField(
                value = title,
                onValueChange = { title = it.take(100) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ShynaDesign.colors.SurfaceBg,
                    unfocusedContainerColor = ShynaDesign.colors.SurfaceBg,
                    focusedTextColor = ShynaDesign.colors.TextPrimary,
                    unfocusedTextColor = ShynaDesign.colors.TextPrimary,
                    cursorColor = ShynaDesign.colors.BrandGreen,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                placeholder = { Text("Meeting Title", color = ShynaDesign.colors.TextSecondary) }
            )

            SectionHeader("When")
            ScheduleItem("Date", dateText, onClick = ::pickDate)
            ScheduleItem("From", timeText, onClick = ::pickTime)
            ScheduleItem("To", endText)
            ScheduleItem("Duration", "$durationMinutes min", onClick = {
                durationMinutes = when (durationMinutes) { 30 -> 45; 45 -> 60; 60 -> 90; else -> 30 }
            })
            ScheduleItem("Time zone", TimeZone.getDefault().displayName)

            SectionHeader("Meeting ID")
            ScheduleSwitchItem(
                title = "Use personal meeting ID (PMI)",
                subtitle = pmi,
                checked = usePmi,
                onCheckedChange = { usePmi = it }
            )
            ScheduleItem("Meeting ID", formatMeetingIdForDisplay(effectiveId))
            if (!usePmi) {
                TextButton(
                    onClick = { meetingId = generateMeetingId() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("New Meeting ID") }
            }

            SectionHeader("Security")
            ScheduleSwitchItem("Require meeting passcode", checked = requirePasscode, onCheckedChange = { requirePasscode = it })
            if (requirePasscode) {
                ScheduleItem("Passcode", passcode)
                TextButton(
                    onClick = { passcode = generateMeetingPasscode() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("New Password") }
            }

            SectionHeader("Meeting options")
            ScheduleSwitchItem("Mute participants on entry", checked = muteOnEntry, onCheckedChange = { muteOnEntry = it })
            ScheduleSwitchItem("Host video on", checked = hostVideoOn, onCheckedChange = { hostVideoOn = it })
            ScheduleSwitchItem("Participant video on", checked = participantVideoOn, onCheckedChange = { participantVideoOn = it })
            Spacer(Modifier.height(40.dp))
        }
    }
    BackHandler(onBack = onBack)
}

@Composable
private fun NewMeetingScreen(
    pmi: String,
    onBack: () -> Unit,
    onStart: (String, String, Boolean, Boolean) -> Unit
) {
    var videoOn by remember { mutableStateOf(true) }
    var microphoneOn by remember { mutableStateOf(true) }
    var usePmi by remember { mutableStateOf(false) }
    var randomMeetingId by rememberSaveable { mutableStateOf(generateMeetingId()) }
    var passcode by rememberSaveable { mutableStateOf(generateMeetingPasscode()) }

    val effectiveId = if (usePmi) pmi.filter { it.isDigit() } else randomMeetingId

    Box(Modifier.fillMaxSize().background(ShynaDesign.colors.PrimaryBg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Surface(color = ShynaDesign.colors.HeaderBg, tonalElevation = 2.dp) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Cancel", color = ShynaDesign.colors.BrandGreen, modifier = Modifier.align(Alignment.CenterStart).clickable { onBack() })
                    Text("Start a Meeting", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            Surface(
                color = ShynaDesign.colors.SurfaceBg,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Meeting ID", color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                    Text(formatMeetingIdForDisplay(effectiveId), color = ShynaDesign.colors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            usePmi = false
                            randomMeetingId = generateMeetingId()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, ShynaDesign.colors.BrandGreen)
                    ) { Icon(Icons.Default.Refresh, null, tint = ShynaDesign.colors.BrandGreen); Spacer(Modifier.width(8.dp)); Text("New Meeting ID", color = ShynaDesign.colors.BrandGreen) }

                    Spacer(Modifier.height(18.dp))
                    Text("Passcode", color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                    Text(passcode, color = ShynaDesign.colors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { passcode = generateMeetingPasscode() },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, ShynaDesign.colors.BrandGreen)
                    ) { Icon(Icons.Default.Refresh, null, tint = ShynaDesign.colors.BrandGreen); Spacer(Modifier.width(8.dp)); Text("New Password", color = ShynaDesign.colors.BrandGreen) }
                }
            }

            Spacer(Modifier.height(20.dp))
            Surface(color = ShynaDesign.colors.SurfaceBg) {
                Column {
                    ScheduleSwitchItem("Video On", checked = videoOn, onCheckedChange = { videoOn = it })
                    ScheduleSwitchItem("Microphone On", checked = microphoneOn, onCheckedChange = { microphoneOn = it })
                    ScheduleSwitchItem("Use Personal Meeting ID (PMI)", subtitle = pmi, checked = usePmi, onCheckedChange = { usePmi = it })
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { onStart(effectiveId, passcode, videoOn, microphoneOn) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
            ) {
                Text("Start Meeting", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    BackHandler(onBack = onBack)
}

@Composable
private fun ShareScreenDialog(
    onBack: () -> Unit,
    onShare: (String) -> Unit
) {
    var meetingId by remember { mutableStateOf("") }
    
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = Color(0xFF1C1C1E), tonalElevation = 2.dp) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Cancel", color = Color(0xFF2D8CFF), modifier = Modifier.align(Alignment.CenterStart).clickable { onBack() })
                    Text("Share Screen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }

            Spacer(Modifier.height(32.dp))
            
            Text("Enter a sharing key or meeting ID to share to a Zoom Room", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
            
            Spacer(Modifier.height(16.dp))

            Surface(color = Color(0xFF1C1C1E)) {
                BasicTextField(
                    value = meetingId,
                    onValueChange = { meetingId = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    decorationBox = { if(meetingId.isEmpty()) Text("Sharing Key or Meeting ID", color = Color.Gray); it() }
                )
            }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = { onShare(meetingId) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D8CFF), disabledContainerColor = Color(0xFF3A3A3C)),
                enabled = meetingId.isNotBlank()
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        }
    }
    BackHandler(onBack = onBack)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = Color.Gray,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ScheduleItem(label: String, value: String, onClick: (() -> Unit)? = null) {
    Surface(color = ShynaDesign.colors.SurfaceBg) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(16.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text(label, color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = ShynaDesign.colors.TextSecondary, fontSize = 15.sp)
                if (onClick != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.KeyboardArrowRight, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    HorizontalDivider(color = ShynaDesign.colors.DividerColor, thickness = 0.5.dp)
}

@Composable
private fun ScheduleSwitchItem(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(color = ShynaDesign.colors.SurfaceBg) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp)
                if (subtitle != null) {
                    Text(subtitle, color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ShynaDesign.colors.BrandGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = ShynaDesign.colors.DividerColor
                )
            )
        }
    }
    HorizontalDivider(color = ShynaDesign.colors.DividerColor, thickness = 0.5.dp)
}

@Composable
private fun MeetingActionItem(label: String, icon: ImageVector, bgColor: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(85.dp)) {
        Surface(
            modifier = Modifier.size(64.dp), 
            shape = RoundedCornerShape(20.dp), 
            color = bgColor, 
            onClick = onClick,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) { 
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp)) 
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            label, 
            fontSize = 12.sp, 
            color = Color.White, 
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, 
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun MeetingEmptyState(tabIndex: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp), 
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.VideoCameraBack, 
            null, 
            modifier = Modifier.size(100.dp), 
            tint = Color(0xFF1E1E22)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = when(tabIndex) { 
                0 -> "No upcoming meetings scheduled"
                1 -> "No previous meetings found"
                else -> "No recordings available" 
            }, 
            fontWeight = FontWeight.Bold, 
            color = Color.White, 
            fontSize = 18.sp, 
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap Schedule above to set up a meeting", 
            color = Color.Gray, 
            fontSize = 14.sp, 
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MeetingQuickTile(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Surface(
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(16.dp),
            color = color,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MeetingCard(meeting: MeetingItem, currentUid: String, onStart: () -> Unit, onDelete: () -> Unit) {
    val mContext = LocalContext.current
    val isHost = meeting.hostUid == currentUid
    val dateStr = remember(meeting.startAt) { SimpleDateFormat("dd MMM, yyyy • h:mm a", Locale.getDefault()).format(Date(meeting.startAt)) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ShynaDesign.colors.SurfaceBg,
        border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).background(
                            if (meeting.status == "LIVE") Color.Red else ShynaDesign.colors.BrandGreen,
                            CircleShape
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (meeting.status == "LIVE") "LIVE NOW" else "UPCOMING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (meeting.status == "LIVE") Color.Red else ShynaDesign.colors.BrandGreen
                    )
                }
                Text("${meeting.durationMinutes} min", fontSize = 12.sp, color = ShynaDesign.colors.TextSecondary)
            }

            Spacer(Modifier.height(10.dp))
            Text(meeting.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ShynaDesign.colors.TextPrimary)
            if (meeting.description.isNotBlank()) {
                Text(meeting.description, fontSize = 13.sp, color = ShynaDesign.colors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ShynaDesign.colors.DividerColor)
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(dateStr, fontSize = 12.sp, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Medium)
                    Text("ID: ${meeting.meetingId} • Host: ${meeting.hostName}", fontSize = 11.sp, color = ShynaDesign.colors.TextSecondary)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        val invite = "Join Shyna Meeting: ${meeting.title}\nMeeting ID: ${meeting.meetingId}\nPasscode: ${meeting.passcode}\nLink: https://shyna.app/meeting/${meeting.meetingId.replace(" ", "")}"
                        val clipboard = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Meeting Invite", invite))
                        Toast.makeText(mContext, "Meeting invitation copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Share, "Share", tint = ShynaDesign.colors.BrandGreen)
                    }

                    if (isHost) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                        }
                    }

                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = if (meeting.status == "LIVE") Color.Red else ShynaDesign.colors.BrandGreen),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(if (meeting.status == "LIVE") "JOIN LIVE" else if (isHost) "START" else "JOIN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMeetingPlaceholder(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.VideoCameraFront, null, modifier = Modifier.size(64.dp), tint = ShynaDesign.colors.DividerColor)
            Spacer(Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = ShynaDesign.colors.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaScopedScreen(
    peerName: String,
    messages: List<UniversalMessage>,
    onBack: () -> Unit,
    onMediaClick: (UniversalMessage) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("MEDIA", "LINKS", "DOCS")
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(peerName, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ShynaDesign.colors.HeaderBg,
                contentColor = ShynaDesign.colors.BrandGreen,
                divider = { HorizontalDivider(color = ShynaDesign.colors.DividerColor, thickness = 0.5.dp) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Box(Modifier.fillMaxSize().background(ShynaDesign.colors.PrimaryBg)) {
                when (selectedTab) {
                    0 -> {
                        val mediaItems = messages.filter { it.messageType == MessageType.IMAGE || it.messageType == MessageType.VIDEO }
                        if (mediaItems.isEmpty()) {
                            EmptyMediaPlaceholder("No media found")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(mediaItems) { m ->
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clickable { onMediaClick(m) }
                                    ) {
                                        AsyncImage(
                                            model = if (m.messageType == MessageType.VIDEO) m.metadata?.replace("/video/upload/", "/video/upload/w_300,h_300,c_fill,so_0/")?.replace(".mp4", ".jpg") else m.metadata,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (m.messageType == MessageType.VIDEO) {
                                            Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(24.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        val links = messages.filter { it.messageType == MessageType.LINK || it.text.contains("http", ignoreCase = true) || it.text.contains("www.", ignoreCase = true) }
                        val mContext = LocalContext.current
                        if (links.isEmpty()) {
                            EmptyMediaPlaceholder("No links found")
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(links) { m ->
                                    ListItem(
                                        headlineContent = { Text(m.text, color = ShynaDesign.colors.BrandGreen, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = { Text(formatChatDate(m.time), fontSize = 11.sp) },
                                        leadingContent = { Surface(Modifier.size(40.dp), CircleShape, color = ShynaDesign.colors.DividerColor) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Link, null, tint = ShynaDesign.colors.TextSecondary) } } },
                                        modifier = Modifier.clickable { 
                                            try {
                                                val url = if (!m.text.startsWith("http")) "https://${m.text}" else m.text
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                mContext.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(mContext, "Cannot open link", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        val docs = messages.filter { it.messageType == MessageType.DOC }
                        if (docs.isEmpty()) {
                            EmptyMediaPlaceholder("No documents found")
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(docs) { m ->
                                    var isDownloading by remember { mutableStateOf(false) }
                                    var progress by remember { mutableFloatStateOf(0f) }
                                    
                                    val mContext = LocalContext.current
                                    ListItem(
                                        headlineContent = { Text(m.fileName ?: "Document", color = ShynaDesign.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = { Text("${android.text.format.Formatter.formatFileSize(mContext, m.fileSize)} • ${formatChatDate(m.time)}", color = ShynaDesign.colors.TextSecondary) },
                                        leadingContent = { 
                                            if (isDownloading) CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                            else Icon(Icons.Default.InsertDriveFile, null, tint = ShynaDesign.colors.BrandGreen) 
                                        },
                                        trailingContent = { Icon(Icons.Default.Download, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.clickable { 
                                            DocInteraction.downloadAndOpen(mContext, scope, m)
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMediaPlaceholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(64.dp), tint = ShynaDesign.colors.DividerColor)
            Spacer(Modifier.height(16.dp))
            Text(text, color = ShynaDesign.colors.TextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditScreen(
    user: RealUser,
    onBack: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdatePhoto: (Uri) -> Unit,
    onChangePhone: (String) -> Unit
) {
    var name by remember { mutableStateOf(user.name) }
    var phone by remember { mutableStateOf(user.phone) }
    
    LaunchedEffect(user) {
        name = user.name
        phone = user.phone
    }
    
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onUpdatePhoto(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Surface(
                    modifier = Modifier.size(140.dp).clickable { photoLauncher.launch("image/*") },
                    shape = CircleShape,
                    border = BorderStroke(4.dp, ShynaDesign.colors.BrandGreen)
                ) {
                    if (!user.photoUrl.isNullOrBlank()) AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.Person, null, modifier = Modifier.padding(30.dp), tint = ShynaDesign.colors.TextSecondary)
                }
                Box(
                    Modifier.size(44.dp).align(Alignment.BottomEnd).background(ShynaDesign.colors.BrandGreen, CircleShape).border(3.dp, Color.White, CircleShape).clickable { photoLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            OutlinedTextField(
                value = user.userId, onValueChange = {},
                label = { Text("User ID (Locked)") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                enabled = false,
                leadingIcon = { Icon(Icons.Default.Fingerprint, null, tint = ShynaDesign.colors.BrandGreen) }
            )

            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { IconButton(onClick = { onUpdateName(name) }) { Icon(Icons.Default.Check, null, tint = ShynaDesign.colors.BrandGreen) } },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
            )
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = user.email, onValueChange = {},
                label = { Text("Email (Locked)") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                enabled = false
            )
            
            Spacer(Modifier.height(16.dp))
            
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { IconButton(onClick = { onChangePhone(phone) }) { Icon(Icons.Default.Check, null, tint = ShynaDesign.colors.BrandGreen) } },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen)
            )
            
            Spacer(Modifier.height(32.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = ShynaDesign.colors.SurfaceBg,
                border = BorderStroke(1.dp, ShynaDesign.colors.DividerColor)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Discovery Info", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen)
                    Spacer(Modifier.height(8.dp))
                    Text("Location: ${user.district ?: "Unknown"}, ${user.state ?: ""}", color = ShynaDesign.colors.TextPrimary)
                    Text("Pincode: ${user.pincode ?: "Not Set"}", color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouPage(
    user: RealUser?, 
    mode: ThemeMode, 
    privacy: UserPrivacySettings,
    storage: UserStorageSettings,
    onThemeChange: (ThemeMode) -> Unit, 
    onUpdatePrivacy: (UserPrivacySettings) -> Unit,
    onUpdateStorage: (UserStorageSettings) -> Unit,
    onLogout: () -> Unit,
    onOpenStarred: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenNetworkUsage: () -> Unit
) {
    val mContext = LocalContext.current
    var showStorageSettings by remember { mutableStateOf(false) }
    var showPrivacySettings by remember { mutableStateOf(false) }
    var showAccountSettings by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    
    if (showStorageSettings) {
        StorageSettingsDialog(
            current = storage,
            onDismiss = { showStorageSettings = false },
            onSave = onUpdateStorage
        )
    }

    if (showPrivacySettings) {
        PrivacySettingsDialog(
            current = privacy,
            userId = user?.uid ?: "",
            onDismiss = { showPrivacySettings = false },
            onSave = onUpdatePrivacy
        )
    }

    if (showAccountSettings) {
        AccountSettingsDialog(
            user = user,
            onDismiss = { showAccountSettings = false },
            onLogout = onLogout
        )
    }

    if (showChatSettings) {
        ChatSettingsDialog(
            mode = mode,
            onThemeChange = onThemeChange,
            onDismiss = { showChatSettings = false }
        )
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }

    if (showInvite) {
        InviteDialog(onDismiss = { showInvite = false })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(ShynaDesign.premiumGradient()), 
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Surface(
                        modifier = Modifier.size(100.dp).clickable { onEditProfile() }, 
                        shape = CircleShape, 
                        border = BorderStroke(3.dp, Color.White.copy(alpha = 0.5f)),
                        color = Color.Transparent
                    ) {
                        if (!user?.photoUrl.isNullOrBlank()) {
                            AsyncImage(user?.photoUrl, null, contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Person, null, modifier = Modifier.padding(24.dp), tint = Color.White)
                        }
                    }
                    Box(
                        Modifier
                            .size(32.dp)
                            .align(Alignment.BottomEnd)
                            .background(ShynaDesign.colors.BrandGreen, CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                            .clickable { onEditProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(user?.name ?: "User", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("@${user?.userId ?: ""}", color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(user?.email ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = ShynaDesign.colors.SurfaceBg,
            shadowElevation = 2.dp
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                ProfileItem("Profile", "Name, status, phone number", Icons.Outlined.Person) { onEditProfile() }
                ProfileItem("Account", "Security, 2FA, delete account", Icons.Outlined.Key) { showAccountSettings = true }
                ProfileItem("Privacy", "Block contacts, disappearing messages", Icons.Outlined.Lock) { showPrivacySettings = true }
                ProfileItem("Chats", "Theme, wallpapers, chat history", Icons.Outlined.Chat) { showChatSettings = true }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = ShynaDesign.colors.SurfaceBg,
            shadowElevation = 2.dp
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                ProfileItem("Starred Messages", "View all your starred messages", Icons.Outlined.Star) { onOpenStarred() }
                ProfileItem("Storage and Data", "Network usage, auto-download", Icons.Outlined.Storage) { showStorageSettings = true }
                ProfileItem("Network Usage", "Check data used by calls and media", Icons.Outlined.BarChart) { onOpenNetworkUsage() }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = ShynaDesign.colors.SurfaceBg,
            shadowElevation = 2.dp
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                ProfileItem("Help", "Help center, contact us, privacy policy", Icons.Outlined.HelpOutline) { showHelp = true }
                ProfileItem("Invite a friend", "Invite your friends to Shyna Calling", Icons.Outlined.GroupAdd) { showInvite = true }
            }
        }

        Spacer(Modifier.height(24.dp))
        
        TextButton(
            onClick = onLogout, 
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
        ) {
            Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Logout", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StorageSettingsDialog(
    current: UserStorageSettings,
    onDismiss: () -> Unit,
    onSave: (UserStorageSettings) -> Unit
) {
    var wifiAutoDownload by remember { mutableStateOf(current.wifiMedia) }
    var mobileAutoDownload by remember { mutableStateOf(current.mobileDataMedia) }
    var saveToGallery by remember { mutableStateOf(current.saveToGallery) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage and Data", color = ShynaDesign.colors.TextPrimary) },
        text = {
            Column {
                Text("Media auto-download", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 14.sp)
                
                StorageMediaOption("When using mobile data", mobileAutoDownload) { mobileAutoDownload = it }
                StorageMediaOption("When connected on Wi-Fi", wifiAutoDownload) { wifiAutoDownload = it }
                
                HorizontalDivider(color = ShynaDesign.colors.DividerColor)
                ListItem(
                    headlineContent = { Text("Save to gallery", color = ShynaDesign.colors.TextPrimary) },
                    supportingContent = { Text("Automatically save incoming media to your gallery", color = ShynaDesign.colors.TextSecondary) },
                    trailingContent = { Switch(saveToGallery, { saveToGallery = it }, colors = SwitchDefaults.colors(checkedThumbColor = ShynaDesign.colors.BrandGreen)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = { 
            TextButton(onClick = { 
                onSave(current.copy(wifiMedia = wifiAutoDownload, mobileDataMedia = mobileAutoDownload, saveToGallery = saveToGallery))
                onDismiss() 
            }) { Text("Save", color = ShynaDesign.colors.BrandGreen) } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun StorageMediaOption(label: String, selected: Set<String>, onUpdate: (Set<String>) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val options = listOf("photo", "video", "audio", "doc")

    ListItem(
        headlineContent = { Text(label, color = ShynaDesign.colors.TextPrimary) },
        supportingContent = { Text(if (selected.isEmpty()) "No media" else selected.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }, color = ShynaDesign.colors.TextSecondary) },
        modifier = Modifier.clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Column {
                    options.forEach { opt ->
                        Row(Modifier.fillMaxWidth().clickable { 
                            val next = selected.toMutableSet()
                            if (next.contains(opt)) next.remove(opt) else next.add(opt)
                            onUpdate(next)
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(selected.contains(opt), null, colors = CheckboxDefaults.colors(checkedColor = ShynaDesign.colors.BrandGreen))
                            Spacer(Modifier.width(12.dp))
                            Text(opt.replaceFirstChar { it.uppercase() }, color = ShynaDesign.colors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("OK", color = ShynaDesign.colors.BrandGreen) } },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }
}

@Composable
private fun PrivacySettingsDialog(
    current: UserPrivacySettings,
    userId: String,
    onDismiss: () -> Unit,
    onSave: (UserPrivacySettings) -> Unit
) {
    var lastSeen by remember { mutableStateOf(current.lastSeen) }
    var photo by remember { mutableStateOf(current.profilePhoto) }
    var about by remember { mutableStateOf(current.about) }
    var groups by remember { mutableStateOf(current.groups) }
    var readReceipts by remember { mutableStateOf(current.readReceipts) }
    var disappearingMsgs by remember { mutableIntStateOf(current.disappearingMessages) }

    var showBlockedList by remember { mutableStateOf(false) }

    if (showBlockedList) {
        BlockedListDialog(userId = userId, onDismiss = { showBlockedList = false })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PrivacyOption("Last seen", lastSeen, listOf("Everyone", "My Contacts", "Nobody")) { lastSeen = it }
                PrivacyOption("Profile photo", photo, listOf("Everyone", "My Contacts", "Nobody")) { photo = it }
                PrivacyOption("About", about, listOf("Everyone", "My Contacts", "Nobody")) { about = it }
                PrivacyOption("Groups", groups, listOf("Everyone", "My Contacts", "Nobody")) { groups = it }
                
                ListItem(
                    headlineContent = { Text("Read receipts", color = ShynaDesign.colors.TextPrimary) },
                    supportingContent = { Text("If turned off, you won't send or receive read receipts.", color = ShynaDesign.colors.TextSecondary) },
                    trailingContent = { Switch(readReceipts, { readReceipts = it }, colors = SwitchDefaults.colors(checkedThumbColor = ShynaDesign.colors.BrandGreen)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                PrivacyOption("Disappearing messages", if(disappearingMsgs == 0) "Off" else "$disappearingMsgs days", listOf("Off", "24 hours", "7 days", "90 days")) { 
                    disappearingMsgs = when(it) {
                        "24 hours" -> 1
                        "7 days" -> 7
                        "90 days" -> 90
                        else -> 0
                    }
                }

                ListItem(
                    headlineContent = { Text("Blocked contacts", color = ShynaDesign.colors.TextPrimary) },
                    leadingContent = { Icon(Icons.Default.Block, null, tint = ShynaDesign.colors.TextSecondary) },
                    modifier = Modifier.clickable { showBlockedList = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = { 
            TextButton(onClick = { 
                onSave(current.copy(
                    lastSeen = lastSeen, 
                    profilePhoto = photo, 
                    about = about,
                    groups = groups,
                    readReceipts = readReceipts,
                    disappearingMessages = disappearingMsgs
                ))
                onDismiss() 
            }) { Text("Save", color = ShynaDesign.colors.BrandGreen) } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun PrivacyOption(label: String, selected: String, options: List<String>, onUpdate: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label, color = ShynaDesign.colors.TextPrimary) },
        supportingContent = { Text(selected, color = ShynaDesign.colors.TextSecondary) },
        modifier = Modifier.clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Column {
                    options.forEach { opt ->
                        Row(Modifier.fillMaxWidth().clickable { onUpdate(opt); showDialog = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected == opt, null, colors = RadioButtonDefaults.colors(selectedColor = ShynaDesign.colors.BrandGreen))
                            Spacer(Modifier.width(12.dp))
                            Text(opt, color = ShynaDesign.colors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun StatusDetailScreen(user: RealUser, statuses: List<UserStatus>, onBack: () -> Unit) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentStatus = statuses[currentIndex]
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(currentIndex) {
        progress = 0f
        
        // Mark status as viewed in Firestore & Local Store
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val status = statuses[currentIndex]
        StatusLocalStore.getInstance(context).markStatusSeenLocally(status.id)
        if (currentUid.isNotBlank()) {
            FirebaseFirestore.getInstance().collection("statuses").document(status.id)
                .update("seenBy", FieldValue.arrayUnion(currentUid))
        }

        if (currentStatus.type == MessageType.VIDEO) {
            exoPlayer.setMediaItem(MediaItem.fromUri(currentStatus.mediaUrl))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    LaunchedEffect(currentIndex, isPaused) {
        if (isPaused) {
            exoPlayer.pause()
            return@LaunchedEffect
        }
        
        if (currentStatus.type == MessageType.VIDEO) {
            exoPlayer.play()
        }

        val duration = if (currentStatus.type == MessageType.VIDEO) {
            // Wait for player duration or fallback to 10s
            delay(500)
            if (exoPlayer.duration > 0) exoPlayer.duration else 10000L
        } else {
            currentStatus.durationMs.coerceAtLeast(5000L)
        }

        val start = System.currentTimeMillis()
        while (progress < 1f && !isPaused) {
            val elapsed = System.currentTimeMillis() - start
            progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            delay(16)
        }

        if (progress >= 1f) {
            if (currentIndex < statuses.size - 1) {
                currentIndex++
            } else {
                onBack()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        if (offset.x < size.width / 3) {
                            if (currentIndex > 0) currentIndex-- else onBack()
                        } else if (offset.x > size.width * 2 / 3) {
                            if (currentIndex < statuses.size - 1) currentIndex++ else onBack()
                        }
                    }
                )
            }
    ) {
        // Content Layer
        when (currentStatus.type) {
            MessageType.VIDEO -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    var isBuffering by remember(currentStatus.id) { mutableStateOf(true) }
                    var hasError by remember(currentStatus.id) { mutableStateOf(false) }

                    DisposableEffect(currentStatus.id) {
                        val listener = object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                isBuffering = (playbackState == Player.STATE_BUFFERING)
                                if (playbackState == Player.STATE_READY) {
                                    hasError = false
                                } else if (playbackState == Player.STATE_ENDED) {
                                    if (currentIndex < statuses.size - 1) {
                                        currentIndex++
                                    } else {
                                        onBack()
                                    }
                                }
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                Log.e("StatusDetail", "Video player error for ${currentStatus.mediaUrl}: ${error.message}")
                                hasError = true
                                isBuffering = false
                            }
                        }
                        exoPlayer.addListener(listener)
                        onDispose {
                            exoPlayer.removeListener(listener)
                        }
                    }

                    AndroidView(
                        factory = {
                            PlayerView(it).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setBackgroundColor(android.graphics.Color.BLACK)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isBuffering && !hasError) {
                        CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(36.dp))
                    }

                    if (hasError) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Unable to play video", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
            MessageType.TEXT, MessageType.LINK -> {
                val bgColor = try { Color(android.graphics.Color.parseColor(currentStatus.backgroundColor ?: "#005C4B")) } catch (e: Exception) { Color(0xFF005C4B) }
                Box(
                    modifier = Modifier.fillMaxSize().background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!currentStatus.text.isNullOrBlank()) {
                            Text(
                                text = currentStatus.text,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                        
                        val effectiveUrl = currentStatus.linkUrl ?: currentStatus.text
                        if (currentStatus.type == MessageType.LINK && !effectiveUrl.isNullOrBlank()) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                                    .clickable {
                                        try {
                                            val urlToOpen = if (effectiveUrl.startsWith("http", ignoreCase = true)) effectiveUrl else "https://$effectiveUrl"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Log.e("StatusDetail", "Error opening link: ${e.message}")
                                        }
                                    }
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (!currentStatus.linkImageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = currentStatus.linkImageUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.width(12.dp))
                                    } else {
                                        Box(
                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Link, contentDescription = null, tint = Color.White)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = currentStatus.linkTitle ?: effectiveUrl,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!currentStatus.linkDescription.isNullOrBlank()) {
                                            Text(
                                                text = currentStatus.linkDescription,
                                                color = Color.White.copy(0.8f),
                                                fontSize = 12.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = currentStatus.linkDomain ?: effectiveUrl,
                                            color = Color.White.copy(0.6f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    var isLoading by remember(currentStatus.id) { mutableStateOf(true) }
                    var isError by remember(currentStatus.id) { mutableStateOf(false) }

                    AsyncImage(
                        model = currentStatus.mediaUrl,
                        contentDescription = "Status Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onLoading = {
                            isLoading = true
                            isError = false
                        },
                        onSuccess = {
                            isLoading = false
                            isError = false
                        },
                        onError = {
                            isLoading = false
                            isError = true
                        }
                    )

                    if (isLoading) {
                        CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(36.dp))
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
        }
        
        // Progress Bars
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            statuses.forEachIndexed { index, _ ->
                val barProgress = when {
                    index < currentIndex -> 1f
                    index == currentIndex -> progress
                    else -> 0f
                }
                LinearProgressIndicator(
                    progress = { barProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
        
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(Modifier.size(40.dp), shape = CircleShape, color = Color.DarkGray) {
                if (!user.photoUrl.isNullOrBlank()) {
                    AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Person, null, tint = Color.LightGray, modifier = Modifier.padding(8.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    text = formatChatDate(currentStatus.timestamp),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
        
        // Caption
        if (!currentStatus.caption.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f))))
                    .padding(bottom = 48.dp, start = 24.dp, end = 24.dp, top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentStatus.caption,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelDetailScreen(channel: ShynaChannel, onBack: () -> Unit, onJoin: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(channel.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                AsyncImage(channel.photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.padding(16.dp)) {
                Text(channel.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                Text("${channel.followersCount} followers", color = ShynaDesign.colors.TextSecondary)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onJoin, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) {
                    Text("Follow")
                }
                Spacer(Modifier.height(24.dp))
                Text("About", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                Text(channel.description, color = ShynaDesign.colors.TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindChannelsScreen(onBack: () -> Unit, onOpenChannel: (ShynaChannel) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var channels by remember { mutableStateOf<List<ShynaChannel>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        db.collection("channels").limit(20).get().addOnSuccessListener { d ->
            channels = d.documents.mapNotNull { it.toObject(ShynaChannel::class.java) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Channels") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize()) {
            items(channels) { c ->
                ListItem(
                    headlineContent = { Text(c.name, color = ShynaDesign.colors.TextPrimary) },
                    supportingContent = { Text(c.description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Surface(Modifier.size(48.dp), shape = CircleShape) { AsyncImage(c.photoUrl, null) } },
                    modifier = Modifier.clickable { onOpenChannel(c) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkUsageScreen(onBack: () -> Unit) {
    val mContext = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    
    fun getUsageLabel(key: String): String {
        val wifi = com.example.callruleblocker.data.NetworkUsageTracker.getDetailedUsage(mContext, key, true)
        val mobile = com.example.callruleblocker.data.NetworkUsageTracker.getDetailedUsage(mContext, key, false)
        return "Wi-Fi -> $wifi\nMobile -> $mobile"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Usage", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState())) {
            refresh // Trigger
            UsageSection("Calls", getUsageLabel("calls"), Icons.Default.Call)
            UsageSection("Media", getUsageLabel("media"), Icons.Default.Image)
            UsageSection("Messages", getUsageLabel("messages"), Icons.Default.Chat)
            UsageSection("Status", getUsageLabel("status"), Icons.Default.Update)
            
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { 
                    com.example.callruleblocker.data.NetworkUsageTracker.clear(mContext)
                    refresh++
                    Toast.makeText(mContext, "Statistics reset", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f), contentColor = Color.Red)
            ) {
                Text("Reset statistics", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun trackNetworkUsage(context: Context, type: String, sent: Long = 0, received: Long = 0) {
    val prefs = context.getSharedPreferences("shyna_network_usage", Context.MODE_PRIVATE)
    prefs.edit().apply {
        putLong("${type}_sent", prefs.getLong("${type}_sent", 0) + sent)
        putLong("${type}_received", prefs.getLong("${type}_received", 0) + received)
    }.apply()
}

@Composable
private fun UsageSection(title: String, subtitle: String, icon: ImageVector) {
    ListItem(
        headlineContent = { Text(title, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(subtitle, color = ShynaDesign.colors.TextSecondary) },
        leadingContent = { Icon(icon, null, tint = ShynaDesign.colors.BrandGreen) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
    HorizontalDivider(color = ShynaDesign.colors.DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun ProfileItem(title: String, subtitle: String, icon: ImageVector, color: Color = ShynaDesign.colors.TextPrimary, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        supportingContent = { Text(subtitle, color = ShynaDesign.colors.TextSecondary, fontSize = 13.sp) },
        leadingContent = { 
            Surface(Modifier.size(40.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(20.dp))
                }
            }
        },
        trailingContent = { Icon(Icons.AutoMirrored.Default.KeyboardArrowRight, null, tint = ShynaDesign.colors.DividerColor) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun formatLastSeen(time: Long?): String {
    if (time == null || time == 0L) return "last seen recently"
    val now = System.currentTimeMillis()
    val diff = now - time
    
    val chatTime = Calendar.getInstance().apply { timeInMillis = time }
    val nowTime = Calendar.getInstance()
    
    return when {
        diff < 60000 -> "last seen just now"
        diff < 3600000 -> "last seen ${diff / 60000} minutes ago"
        isSameDay(time, now) -> "last seen today at ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(time))}"
        nowTime.get(Calendar.YEAR) == chatTime.get(Calendar.YEAR) && nowTime.get(Calendar.DAY_OF_YEAR) - chatTime.get(Calendar.DAY_OF_YEAR) == 1 -> 
            "last seen yesterday at ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(time))}"
        else -> "last seen on ${SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(time))}"
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    return result
}

// --- PREMIUM GALLERY PICKER ---

private fun formatGalleryDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}

private data class GalleryMedia(
    val id: Long, 
    val uri: Uri, 
    val name: String, 
    val dateAdded: Long, 
    val isVideo: Boolean,
    val album: String,
    val duration: Long = 0
)

@Composable
private fun PremiumGalleryScreen(onBack: () -> Unit, onMediaSelected: (List<Pair<Uri, Boolean>>) -> Unit) {
    val mContext = LocalContext.current
    val mediaItems = remember { mutableStateListOf<GalleryMedia>() }
    val selectedMedia = remember { mutableStateListOf<GalleryMedia>() }
    var selectedTab by remember { mutableStateOf("Albums") }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortByName by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val allMedia = mutableListOf<GalleryMedia>()
        val projection = arrayOf(
            android.provider.MediaStore.MediaColumns._ID,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            android.provider.MediaStore.MediaColumns.DATE_ADDED,
            android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )
        val videoProjection = arrayOf(
            android.provider.MediaStore.MediaColumns._ID,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            android.provider.MediaStore.MediaColumns.DATE_ADDED,
            android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            android.provider.MediaStore.Video.Media.DURATION
        )
        val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_ADDED} DESC"
        
        // Images
        mContext.contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_ADDED)
            val albumCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                allMedia.add(GalleryMedia(id, uri, cursor.getString(nameCol) ?: "", cursor.getLong(dateCol) * 1000, false, cursor.getString(albumCol) ?: "Internal"))
            }
        }
        
        // Videos
        mContext.contentResolver.query(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_ADDED)
            val albumCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val durCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                allMedia.add(GalleryMedia(
                    id, uri, cursor.getString(nameCol) ?: "", 
                    cursor.getLong(dateCol) * 1000, true, 
                    cursor.getString(albumCol) ?: "Internal",
                    cursor.getLong(durCol)
                ))
            }
        }
        
        mediaItems.clear()
        mediaItems.addAll(allMedia.sortedByDescending { it.dateAdded })
    }

    val filteredMedia = remember(mediaItems.size, selectedTab, selectedAlbum, searchQuery, sortByName) {
        var list = mediaItems.filter { 
            val matchTab = when(selectedTab) {
                "Pictures" -> !it.isVideo
                "Videos" -> it.isVideo
                "Albums" -> selectedAlbum == null || it.album == selectedAlbum
                else -> true
            }
            val matchSearch = searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true)
            matchTab && matchSearch
        }
        if (sortByName) list.sortedBy { it.name } else list.sortedByDescending { it.dateAdded }
    }

    val albums = remember(mediaItems.size) {
        mediaItems.groupBy { it.album }.map { (name, items) -> name to items.first().uri }.sortedBy { it.first }
    }

    val groupedMedia = remember(filteredMedia) {
        filteredMedia.groupBy { item ->
            val cal = Calendar.getInstance().apply { timeInMillis = item.dateAdded }
            val now = Calendar.getInstance()
            if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) "Today"
            else if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1) "Yesterday"
            else SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.dateAdded))
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // TOP BAR
        if (isSearchActive) {
            Surface(Modifier.fillMaxWidth().height(64.dp), color = Color.Black) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSearchActive = false; searchQuery = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                    TextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search by name, .ext or number...", color = Color.Gray) },
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = ShynaDesign.colors.BrandGreen)
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (selectedAlbum != null) selectedAlbum = null else onBack() }) { Icon(if (selectedAlbum != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close, null, tint = Color.White) }
                Text(if (selectedAlbum != null) selectedAlbum!! else "Select $selectedTab", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { sortByName = !sortByName }) { Icon(if (sortByName) Icons.Default.SortByAlpha else Icons.Default.Schedule, null, tint = Color.White) }
                if (selectedMedia.isNotEmpty()) {
                    Button(onClick = { onMediaSelected(selectedMedia.map { it.uri to it.isVideo }) }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) { Text("Send (${selectedMedia.size})") }
                }
            }
        }

        // CONTENT
        Box(Modifier.weight(1f)) {
            if (selectedTab == "Albums" && selectedAlbum == null) {
                LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(albums) { (name, thumb) ->
                        Column(Modifier.clickable { selectedAlbum = name }) {
                            AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.height(8.dp))
                            Text(name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${mediaItems.count { it.album == name }} items", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            } else if (filteredMedia.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No items found", color = Color.Gray, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    groupedMedia.forEach { (date, items) ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                                val isAllSelected = items.all { it in selectedMedia }
                                IconButton(onClick = { if (isAllSelected) selectedMedia.removeAll(items) else items.forEach { if (it !in selectedMedia) selectedMedia.add(it) } }) {
                                    Icon(if (isAllSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if(isAllSelected) ShynaDesign.colors.BrandGreen else Color.White)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(date, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        items(items.chunked(3)) { rowItems ->
                            Row(Modifier.fillMaxWidth()) {
                                rowItems.forEach { item ->
                                    val isSelected = selectedMedia.contains(item)
                                    Box(Modifier.weight(1f).aspectRatio(1f).padding(1.dp).clickable { if (isSelected) selectedMedia.remove(item) else selectedMedia.add(item) }) {
                                        if (item.isVideo) {
                                            val thumb by produceState<Bitmap?>(null, item.uri) {
                                                value = try {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                        mContext.contentResolver.loadThumbnail(item.uri, Size(300, 300), null)
                                                    } else {
                                                        @Suppress("DEPRECATION")
                                                        MediaStore.Video.Thumbnails.getThumbnail(mContext.contentResolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                                                    }
                                                } catch (e: Exception) { null }
                                            }
                                            if (thumb != null) {
                                                Image(bitmap = thumb!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            } else {
                                                Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(0.5f))
                                                }
                                            }
                                            
                                            // Duration Badge
                                            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                                Text(formatGalleryDuration(item.duration), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(0.7f), modifier = Modifier.align(Alignment.Center).size(28.dp))
                                        } else {
                                            AsyncImage(model = item.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        }
                                        
                                        Box(Modifier.fillMaxSize().padding(6.dp)) {
                                            Icon(if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if(isSelected) ShynaDesign.colors.BrandGreen else Color.White.copy(0.8f), modifier = Modifier.align(Alignment.TopStart).size(22.dp))
                                        }
                                    }
                                }
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM BAR
        Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                GalleryTabItem("Albums", Icons.Default.PhotoLibrary, selectedTab == "Albums") { selectedTab = "Albums"; selectedAlbum = null; isSearchActive = false }
                GalleryTabItem("Pictures", Icons.Default.Image, selectedTab == "Pictures") { selectedTab = "Pictures"; isSearchActive = false }
                GalleryTabItem("Videos", Icons.Default.VideoLibrary, selectedTab == "Videos") { selectedTab = "Videos"; isSearchActive = false }
                GalleryTabItem("Search", Icons.Default.Search, isSearchActive) { isSearchActive = !isSearchActive }
            }
        }
    }
}

@Composable
private fun GalleryTabItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(8.dp)) {
        Icon(icon, null, tint = if (active) Color.White else Color.Gray, modifier = Modifier.size(24.dp))
        Text(label, color = if (active) Color.White else Color.Gray, fontSize = 10.sp)
    }
}

private data class AudioItem(val id: Long, val uri: Uri, val name: String, val size: Long, val duration: Long, val date: Long)

@Composable
private fun PremiumAudioPickerScreen(onBack: () -> Unit, onAudioSelected: (List<Uri>) -> Unit) {
    val mContext = LocalContext.current
    val audioItems = remember { mutableStateListOf<AudioItem>() }
    val selectedAudio = remember { mutableStateListOf<AudioItem>() }
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media._ID,
            android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
            android.provider.MediaStore.Audio.Media.SIZE,
            android.provider.MediaStore.Audio.Media.DURATION,
            android.provider.MediaStore.Audio.Media.DATE_ADDED
        )
        val sortOrder = "${android.provider.MediaStore.Audio.Media.DATE_ADDED} DESC"
        
        mContext.contentResolver.query(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.SIZE)
            val durCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                audioItems.add(AudioItem(id, uri, cursor.getString(nameCol) ?: "Unknown", cursor.getLong(sizeCol), cursor.getLong(durCol), cursor.getLong(dateCol) * 1000))
            }
        }
    }

    val filtered = remember(audioItems.size, searchQuery) {
        if (searchQuery.isEmpty()) audioItems 
        else audioItems.filter { it.name.contains(searchQuery, true) }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text("Select Audio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (selectedAudio.isNotEmpty()) {
                Button(onClick = { onAudioSelected(selectedAudio.map { it.uri }) }, colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)) {
                    Text("Send (${selectedAudio.size})")
                }
            }
        }
        
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search by name...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ShynaDesign.colors.BrandGreen, unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No audio files found", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(filtered) { audio ->
                    val isSelected = selectedAudio.contains(audio)
                    ListItem(
                        headlineContent = { Text(audio.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { 
                            val sizeStr = android.text.format.Formatter.formatFileSize(mContext, audio.size)
                            val durStr = String.format("%d:%02d", audio.duration / 60000, (audio.duration % 60000) / 1000)
                            Text("$sizeStr • $durStr", color = Color.Gray, fontSize = 12.sp) 
                        },
                        leadingContent = {
                            Surface(Modifier.size(48.dp), shape = CircleShape, color = if(isSelected) ShynaDesign.colors.BrandGreen else Color.DarkGray) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(if(isSelected) Icons.Default.Check else Icons.Default.MusicNote, null, tint = if(isSelected) Color.Black else Color.White)
                                }
                            }
                        },
                        trailingContent = {
                            val dateStr = remember(audio.date) { SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(audio.date)) }
                            Text(dateStr, color = Color.Gray, fontSize = 11.sp)
                        },
                        colors = ListItemDefaults.colors(containerColor = if(isSelected) Color.White.copy(0.05f) else Color.Transparent),
                        modifier = Modifier.clickable { 
                            if (isSelected) selectedAudio.remove(audio) else selectedAudio.add(audio)
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

private fun compressImage(context: Context, uri: Uri, fileName: String): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        val file = File(context.cacheDir, fileName)
        val out = java.io.FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
        out.flush()
        out.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }
}

private fun showSystemNotification(context: Context, title: String, message: String) {
    val channelId = "shyna_messages"
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    val channel = NotificationChannel(channelId, "Shyna Messages", NotificationManager.IMPORTANCE_HIGH)
    nm.createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    nm.notify(System.currentTimeMillis().toInt(), notification)
}

@Composable
private fun CreateCustomListDialog(
    chats: List<ChatRowItem>,
    users: List<RealUser>,
    onDismiss: () -> Unit,
    onSave: (CustomChatList) -> Unit
) {
    var listName by remember { mutableStateOf("") }
    val selectedChatIds = remember { mutableStateListOf<String>() }
    val mContext = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New List", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    label = { Text("List Name (e.g. Family, Work)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("Select Chats", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(300.dp)) {
                    items(chats) { chat ->
                        val peer = users.find { it.uid == chat.peerUid }
                        peer?.let {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        if (selectedChatIds.contains(chat.id)) selectedChatIds.remove(chat.id)
                                        else selectedChatIds.add(chat.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedChatIds.contains(chat.id),
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = ShynaDesign.colors.BrandGreen)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(it.name, color = ShynaDesign.colors.TextPrimary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (listName.isBlank()) {
                        Toast.makeText(mContext, "Please enter a list name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (selectedChatIds.isEmpty()) {
                        Toast.makeText(mContext, "Please select at least one chat", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSave(CustomChatList(name = listName, chatIds = selectedChatIds.toList()))
                },
                colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
            ) { Text("Save List") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

private fun formatChatDate(time: Long): String {
    if (time == 0L) return ""
    val now = Calendar.getInstance()
    val chatTime = Calendar.getInstance().apply { timeInMillis = time }
    
    return if (now.get(Calendar.DATE) == chatTime.get(Calendar.DATE)) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(time))
    } else if (now.get(Calendar.DATE) - chatTime.get(Calendar.DATE) == 1) {
        "Yesterday"
    } else {
        SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(time))
    }
}

private fun renameFile(context: Context, uri: Uri, fileName: String): Uri? {
    return try {
        val file = File(context.cacheDir, fileName)
        val inputStream = context.contentResolver.openInputStream(uri)
        val outputStream = java.io.FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        outputStream.close()
        inputStream?.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }
}

// --- ROYAL AUTH FLOW ---

private enum class AuthStep { LOGIN, SIGNUP, FORGOT_PASSWORD, RESET_PASSWORD }

@Composable
private fun ShynaAuthFlow(onLoginSuccess: () -> Unit, onBack: () -> Unit) {
    val mContext = LocalContext.current
    var step by remember { mutableStateOf(AuthStep.LOGIN) }
    var resetEmail by remember { mutableStateOf("") }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(mContext, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnSuccessListener { authResult -> 
                        val user = authResult.user
                        if (user != null) {
                            val db = FirebaseFirestore.getInstance()
                            // ENSURE USER PROFILE EXISTS IN FIRESTORE TO PREVENT LOGOUT LOOP
                            db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    Toast.makeText(mContext, "Welcome back, ${user.displayName}!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } else {
                                    // Create new profile for Google user
                                    val data = mapOf(
                                        "uid" to user.uid,
                                        "userId" to (user.email?.split("@")?.get(0) ?: user.uid),
                                        "name" to (user.displayName ?: "User"),
                                        "email" to (user.email ?: ""),
                                        "phone" to (user.phoneNumber ?: ""),
                                        "provider" to "google",
                                        "isOnline" to true,
                                        "lastSeen" to com.google.firebase.Timestamp.now()
                                    )
                                    db.collection("users").document(user.uid).set(data).addOnSuccessListener {
                                        Toast.makeText(mContext, "Google Sign-In successful!", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    }.addOnFailureListener { e ->
                                        Toast.makeText(mContext, "Profile creation failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }.addOnFailureListener { e ->
                                Log.e("ShynaAuth", "Firestore profile check failed", e)
                                val msg = if (e.localizedMessage?.contains("permission") == true) 
                                    "Database Permission Denied. Profile check failed."
                                else "Database error: ${e.localizedMessage}"
                                Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .addOnFailureListener { Toast.makeText(mContext, "Google Login Failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show() }
            } else {
                Toast.makeText(mContext, "Google Sign-In failed: No ID Token", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("ShynaAuth", "Google Sign In Error", e)
            if (e is com.google.android.gms.common.api.ApiException && e.statusCode == CommonStatusCodes.DEVELOPER_ERROR) {
                Toast.makeText(mContext, "Error 10: SHA-1 mismatch. Please add your SHA-1 to Firebase Console.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(mContext, "Google Sign-In error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val onGoogleClick = { 
        Toast.makeText(mContext, "Starting Google Sign-In...", Toast.LENGTH_SHORT).show()
        googleLauncher.launch(googleSignInClient.signInIntent) 
    }

    BackHandler(enabled = true) {
        when (step) {
            AuthStep.LOGIN -> onBack()
            AuthStep.SIGNUP -> step = AuthStep.LOGIN
            AuthStep.FORGOT_PASSWORD -> step = AuthStep.LOGIN
            AuthStep.RESET_PASSWORD -> step = AuthStep.LOGIN
        }
    }
    
    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
        label = "auth_flow"
    ) { currentStep ->
        when (currentStep) {
            AuthStep.LOGIN -> LoginScreen(
                onLoginSuccess = onLoginSuccess,
                onSignUpClick = { step = AuthStep.SIGNUP },
                onForgotClick = { step = AuthStep.FORGOT_PASSWORD },
                onGoogleClick = onGoogleClick,
                onBack = onBack
            )
            AuthStep.SIGNUP -> SignUpScreen(
                onBack = { step = AuthStep.LOGIN },
                onLoginClick = { step = AuthStep.LOGIN },
                onGoogleClick = onGoogleClick,
                onSignUpSuccess = onLoginSuccess
            )
            AuthStep.FORGOT_PASSWORD -> ForgotPasswordScreen(
                onBack = { step = AuthStep.LOGIN },
                onResetSent = { email -> 
                    resetEmail = email
                    // Firebase handles the actual reset via email, 
                    // but we can show the custom reset screen if needed for demo/manual flow.
                    step = AuthStep.RESET_PASSWORD 
                }
            )
            AuthStep.RESET_PASSWORD -> ResetPasswordScreen(
                email = resetEmail,
                onBack = { step = AuthStep.LOGIN },
                onResetSuccess = { step = AuthStep.LOGIN }
            )
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            Text(
                "Skip", 
                modifier = Modifier.clickable { onSkip() }.padding(8.dp),
                color = ShynaDesign.colors.BrandGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        
        Spacer(Modifier.height(40.dp))
        
        // Illustration placeholder (using a box with border and icons as per screenshot)
        val brandColor = ShynaDesign.colors.BrandGreen
        Box(contentAlignment = Alignment.Center) {
            // Stylized background circles
            Canvas(Modifier.size(280.dp)) {
                drawCircle(color = Color(0xFFFFF8E1), radius = size.minDimension / 2)
                drawCircle(
                    color = brandColor.copy(0.1f), 
                    radius = size.minDimension / 2.5f, 
                    style = Stroke(
                        width = 1.dp.toPx(), 
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                )
            }
            
            // Central Avatar Placeholder
            Surface(
                modifier = Modifier.size(180.dp),
                shape = CircleShape,
                border = BorderStroke(4.dp, brandColor),
                color = Color.White
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.padding(40.dp), tint = brandColor)
            }
            
            // Orbiting Icons
            val icons = listOf(Icons.Default.Call, Icons.AutoMirrored.Filled.Chat, Icons.Default.Shield, Icons.Default.Groups)
            icons.forEachIndexed { i, icon ->
                val angle = (i * 90f) * (Math.PI / 180f)
                Box(
                    Modifier
                        .offset(
                            x = (Math.cos(angle) * 120).dp,
                            y = (Math.sin(angle) * 120).dp
                        )
                        .size(44.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, brandColor.copy(0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = brandColor, modifier = Modifier.size(20.dp))
                }
            }
        }
        
        Spacer(Modifier.height(60.dp))
        
        Text(
            "Welcome to Shyna Calling!",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(10.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Smart", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("  •  ", color = Color.LightGray)
            Text("Secure", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("  •  ", color = Color.LightGray)
            Text("Reliable", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        
        Spacer(Modifier.height(20.dp))
        
        Text(
            "Experience seamless calling with advanced features and complete privacy.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            lineHeight = 20.sp,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        
        Spacer(Modifier.weight(1f))
        
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Let's Get Started", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Dots Indicator
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(ShynaDesign.colors.BrandGreen, CircleShape))
            repeat(3) { Box(Modifier.size(10.dp).background(Color(0xFFEEEEEE), CircleShape)) }
        }
    }
}

@Composable
private fun LoginScreen(
    onLoginSuccess: () -> Unit, 
    onSignUpClick: () -> Unit, 
    onForgotClick: () -> Unit, 
    onGoogleClick: () -> Unit,
    onBack: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val mContext = LocalContext.current
    val design = ShynaDesign.colors

    LaunchedEffect(Unit) {
        val options = com.google.firebase.FirebaseApp.getInstance().options
        Log.d("ShynaAuth", "Firebase Project: ${options.projectId}")
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(design.PrimaryBg)
    ) {
        // Subtle Background Pattern (Wavy lines & Dots placeholder)
        if (!design.isDark) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw some soft beige decorative lines/dots as seen in image
                drawCircle(color = Color(0xFFFDF5E6), radius = 250f, center = Offset(size.width, size.height * 0.9f))
            }
        } else {
            // Dark mode specific background effect
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(design.AuthAccent.copy(alpha = 0.05f), Color.Transparent)),
                    radius = 800f,
                    center = Offset(size.width, 0f)
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp)) // Moved down from top
            
            // Title: Shyna Calling
            Row {
                Text("Shyna ", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = design.AuthAccent)
                Text("Calling", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = design.TextPrimary)
            }
            
            Spacer(Modifier.height(10.dp))
            
            // Subtitle: --- 👑 WORLD ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.width(36.dp), color = design.AuthAccent.copy(alpha = 0.4f))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.WorkspacePremium, null, tint = design.AuthAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("WORLD", color = design.AuthAccent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                HorizontalDivider(Modifier.width(36.dp), color = design.AuthAccent.copy(alpha = 0.4f))
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Main Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = design.SurfaceBg,
                border = if(design.isDark) BorderStroke(1.dp, design.DividerColor) else null,
                shadowElevation = if(design.isDark) 0.dp else 6.dp
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Welcome back!", 
                        fontSize = 28.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = design.AuthAccent
                    )
                    Text(
                        "Login to continue with Shyna Calling", 
                        color = design.TextSecondary, 
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    
                    Spacer(Modifier.height(28.dp))
                    
                    RoyalTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email address",
                        icon = Icons.Outlined.Person
                    )
                    
                    Spacer(Modifier.height(18.dp))
                    
                    RoyalTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        icon = Icons.Outlined.Lock,
                        isPassword = true
                    )
                    
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            "Forgot Password?", 
                            modifier = Modifier.clickable { onForgotClick() }.padding(vertical = 12.dp),
                            color = design.AuthAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    AuthButton(
                        text = "Login",
                        loading = loading,
                        onClick = {
                            val cleanEmail = email.filterNot { it.isWhitespace() }.lowercase()
                            if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                                Toast.makeText(mContext, "Enter a valid email address", Toast.LENGTH_SHORT).show()
                                return@AuthButton
                            }
                            if (password.isBlank()) {
                                Toast.makeText(mContext, "Please enter your password", Toast.LENGTH_SHORT).show()
                                return@AuthButton
                            }

                            loading = true
                            auth.signInWithEmailAndPassword(cleanEmail, password)
                                .addOnSuccessListener {
                                    loading = false
                                    onLoginSuccess()
                                }
                                .addOnFailureListener { e ->
                                    loading = false
                                    Log.e("ShynaAuth", "Manual email login failed", e)
                                    val errCode = (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode
                                    val msg = when {
                                        errCode == "ERROR_INVALID_CREDENTIAL" ||
                                            e.localizedMessage?.contains("password", ignoreCase = true) == true ||
                                            e.localizedMessage?.contains("credential", ignoreCase = true) == true ->
                                            "Incorrect email or password"
                                        errCode == "ERROR_USER_NOT_FOUND" ||
                                            e.localizedMessage?.contains("user-not-found", ignoreCase = true) == true ->
                                            "Account not found. Please sign up"
                                        e.localizedMessage?.contains("network", ignoreCase = true) == true ->
                                            "No internet connection. Please check your network"
                                        else -> "Login Failed: ${e.localizedMessage ?: "Please try again"}"
                                    }
                                    Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show()
                                }
                        }
                    )
                    
                    Spacer(Modifier.height(28.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = design.DividerColor)
                        Text(" or continue with ", color = design.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
                        HorizontalDivider(Modifier.weight(1f), color = design.DividerColor)
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    SocialLoginButton(
                        icon = Icons.Default.GTranslate, 
                        text = "Continue with Google", 
                        isGoogle = true,
                        onClick = onGoogleClick
                    )
                    
                    Spacer(Modifier.height(32.dp))
                    
                    Row {
                        Text("Don't have an account? ", color = design.TextSecondary, fontSize = 14.sp)
                        Text(
                            "Sign Up", 
                            color = design.AuthAccent, 
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { onSignUpClick() }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AuthButton(text: String, loading: Boolean, onClick: () -> Unit) {
    val design = ShynaDesign.colors
    val gradient = Brush.horizontalGradient(listOf(design.AuthAccent, design.AuthAccent.copy(alpha = 0.8f)))

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(if (design.isDark) gradient else SolidColor(design.AuthAccent), RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        enabled = !loading,
        contentPadding = PaddingValues(0.dp)
    ) {
        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        else Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

@Composable
private fun SignUpScreen(
    onBack: () -> Unit, 
    onLoginClick: () -> Unit, 
    onGoogleClick: () -> Unit,
    onSignUpSuccess: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var userIdInput by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    val mContext = LocalContext.current
    val design = ShynaDesign.colors

    Box(
        Modifier
            .fillMaxSize()
            .background(design.PrimaryBg)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp)) // Moved down from top
            Box(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = design.AuthAccent)
                }
                
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Text("Shyna ", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = design.AuthAccent)
                        Text("Calling", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = design.TextPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.width(24.dp), color = design.AuthAccent.copy(alpha = 0.4f))
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.WorkspacePremium, null, tint = design.AuthAccent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("WORLD", color = design.AuthAccent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        HorizontalDivider(Modifier.width(24.dp), color = design.AuthAccent.copy(alpha = 0.4f))
                    }
                }
            }
            
            Spacer(Modifier.height(28.dp))
            
            Text("Sign Up", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = design.AuthAccent)
            
            Spacer(Modifier.height(28.dp))
            
            RoyalTextField(name, { name = it }, "Full Name", Icons.Outlined.Person)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(userIdInput, { userIdInput = it }, "Unique User ID / Username", Icons.Outlined.Fingerprint)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(email, { email = it }, "Email", Icons.Outlined.Mail)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(phone, { phone = it }, "Mobile Number", Icons.Outlined.Phone)
            Spacer(Modifier.height(14.dp))
            RoyalTextField(password, { password = it }, "Password", Icons.Outlined.Lock, isPassword = true)
            
            Spacer(Modifier.height(18.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = agree, onCheckedChange = { agree = it }, colors = CheckboxDefaults.colors(checkedColor = design.AuthAccent))
                Text(
                    text = buildAnnotatedString {
                        append("I agree to the ")
                        withStyle(SpanStyle(color = design.AuthAccent, fontWeight = FontWeight.Bold)) { append("Terms of Service ") }
                        append("and ")
                        withStyle(SpanStyle(color = design.AuthAccent, fontWeight = FontWeight.Bold)) { append("Privacy Policy") }
                    },
                    fontSize = 13.sp,
                    color = design.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            AuthButton(
                text = "Sign Up",
                loading = loading,
                onClick = {
                    val cleanEmail = email.filterNot { it.isWhitespace() }.lowercase()
                    val cleanUserId = userIdInput.filterNot { it.isWhitespace() }.lowercase()
                    val cleanPhone = phone.filter { it.isDigit() }
                    val trimmedName = name.trim()
                    
                    if (trimmedName.isBlank()) { Toast.makeText(mContext, "Please enter your full name", Toast.LENGTH_SHORT).show(); return@AuthButton }
                    if (cleanUserId.length < 3) { Toast.makeText(mContext, "User ID must be at least 3 characters", Toast.LENGTH_SHORT).show(); return@AuthButton }
                    if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) { Toast.makeText(mContext, "Invalid email address", Toast.LENGTH_SHORT).show(); return@AuthButton }
                    if (cleanPhone.length < 10) { Toast.makeText(mContext, "Invalid mobile number", Toast.LENGTH_SHORT).show(); return@AuthButton }
                    if (password.length < 6) { Toast.makeText(mContext, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show(); return@AuthButton }
                    if (!agree) { Toast.makeText(mContext, "Please accept Terms & Privacy Policy", Toast.LENGTH_SHORT).show(); return@AuthButton }

                    loading = true
                    val db = FirebaseFirestore.getInstance()

                    // Step 1: Create Firebase Auth Account
                    auth.createUserWithEmailAndPassword(cleanEmail, password)
                        .addOnSuccessListener { authResult ->
                            val uid = authResult.user?.uid ?: ""
                            
                            // Step 2: Now that we are AUTHENTICATED, we can safely write to Firestore
                            val data = mapOf(
                                "uid" to uid,
                                "userId" to cleanUserId,
                                "name" to trimmedName,
                                "email" to cleanEmail,
                                "phone" to cleanPhone,
                                "provider" to "password",
                                "status" to "active",
                                "isOnline" to true,
                                "lastSeen" to com.google.firebase.Timestamp.now(),
                                "createdAt" to com.google.firebase.Timestamp.now()
                            )

                            db.runTransaction { transaction ->
                                // Check if User ID is taken (Document check is always allowed if you own the path or have get permission)
                                val reservedRef = db.collection("reserved_ids").document(cleanUserId)
                                if (transaction.get(reservedRef).exists()) {
                                    throw Exception("USER_ID_TAKEN")
                                }
                                
                                // Note: Phone duplicate check is better done via a lookup collection if rules are strict.
                                // For now, we write the main profile and the reservation.
                                transaction.set(reservedRef, mapOf("uid" to uid))
                                transaction.set(db.collection("users").document(uid), data)
                            }.addOnSuccessListener {
                                loading = false
                                Toast.makeText(mContext, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                onSignUpSuccess()
                            }.addOnFailureListener { e ->
                                loading = false
                                Log.e("ShynaAuth", "Profile creation failed", e)
                                val msg = if (e.message == "USER_ID_TAKEN") "User ID already taken" else "Database error: ${e.localizedMessage}"
                                Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show()
                                
                                // ROLLBACK: Delete the auth user if profile creation fails
                                auth.currentUser?.delete()
                            }
                        }
                        .addOnFailureListener { e ->
                            loading = false
                            Log.e("ShynaAuth", "Auth creation failed", e)
                            val msg = when {
                                e is FirebaseAuthUserCollisionException -> "Email already registered"
                                e.localizedMessage?.contains("network", ignoreCase = true) == true -> "Network error. Check connection."
                                else -> "Sign Up Failed: ${e.localizedMessage}"
                            }
                            Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show()
                        }
                }
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text("or continue with", color = design.TextSecondary, fontSize = 13.sp)
            
            Spacer(Modifier.height(16.dp))
            
            SocialLoginButton(
                icon = Icons.Default.GTranslate, 
                text = "Continue with Google", 
                isGoogle = true,
                onClick = onGoogleClick
            )
            
            Spacer(Modifier.height(36.dp))
            
            Row {
                Text("Already have an account? ", color = design.TextSecondary, fontSize = 15.sp)
                Text(
                    "Login", 
                    color = design.AuthAccent, 
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { onLoginClick() }
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                "Back to Login", 
                color = design.AuthAccent, 
                fontWeight = FontWeight.Bold, 
                fontSize = 17.sp,
                modifier = Modifier.clickable { onLoginClick() }
            )
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ForgotPasswordScreen(onBack: () -> Unit, onResetSent: (String) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    var email by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val mContext = LocalContext.current
    val design = ShynaDesign.colors
    
    Box(Modifier.fillMaxSize().background(design.PrimaryBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp)) // Moved down from top
            Box(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = design.AuthAccent)
                }
                Text("Forgot Password", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = design.AuthAccent, modifier = Modifier.align(Alignment.Center))
            }
            
            Spacer(Modifier.height(40.dp))
            
            Box(contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(140.dp), shape = CircleShape, color = design.AuthAccent.copy(0.05f)) { }
                Surface(modifier = Modifier.size(100.dp), shape = CircleShape, color = design.SurfaceBg, shadowElevation = if(design.isDark) 0.dp else 2.dp, border = if(design.isDark) BorderStroke(1.dp, design.DividerColor) else null) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, null, tint = design.AuthAccent, modifier = Modifier.size(48.dp))
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.Send, 
                    null, 
                    tint = design.AuthAccent, 
                    modifier = Modifier.size(28.dp).offset(x = 65.dp, y = (-40).dp)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text("No worries!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = design.AuthAccent)
            Spacer(Modifier.height(14.dp))
            Text(
                "Enter your registered email and we'll send you reset instructions.",
                textAlign = TextAlign.Center,
                color = design.TextSecondary,
                modifier = Modifier.padding(horizontal = 32.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            
            Spacer(Modifier.height(40.dp))
            
            RoyalTextField(email, { email = it }, "Enter your Email-ID", Icons.Outlined.Email)
            
            Spacer(Modifier.height(32.dp))
            
            AuthButton(
                text = "Send Reset Link",
                loading = loading,
                onClick = {
                    if (email.isBlank()) {
                        Toast.makeText(mContext, "Enter your email-ID.", Toast.LENGTH_SHORT).show()
                        return@AuthButton
                    }
                    loading = true
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            loading = false
                            Toast.makeText(mContext, "Password reset link sent to $email. Please check your inbox.", Toast.LENGTH_LONG).show()
                            onResetSent(email)
                        }
                        .addOnFailureListener {
                            loading = false
                            val errorMsg = when {
                                it.message?.contains("user-not-found") == true -> "No account found with this email."
                                it.message?.contains("invalid-email") == true -> "Invalid email address format."
                                else -> it.localizedMessage
                            }
                            Toast.makeText(mContext, errorMsg, Toast.LENGTH_LONG).show()
                        }
                }
            )
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                "Back to Login", 
                modifier = Modifier.clickable { onBack() }.padding(12.dp),
                color = design.AuthAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            
            Spacer(Modifier.weight(1f))
            
            // Bottom Branding
            BottomBranding()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ResetPasswordScreen(email: String, onBack: () -> Unit, onResetSuccess: () -> Unit) {
    val design = ShynaDesign.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(design.PrimaryBg)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, null, tint = design.AuthAccent)
            }
            Text(
                "Reset Password",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = design.AuthAccent,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(Modifier.height(48.dp))
        Surface(
            modifier = Modifier.size(112.dp),
            shape = CircleShape,
            color = design.AuthAccent.copy(alpha = 0.08f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MarkEmailRead, null, tint = design.AuthAccent, modifier = Modifier.size(52.dp))
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Reset link sent", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = design.AuthAccent)
        Spacer(Modifier.height(12.dp))
        Text(
            "We sent Firebase password-reset instructions to $email. Open the link from your email when you are ready, then return to Shyna and sign in with the new password.",
            textAlign = TextAlign.Center,
            color = design.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Shyna will not open Gmail automatically.",
            textAlign = TextAlign.Center,
            color = design.TextSecondary,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(36.dp))
        AuthButton(
            text = "Back to Login",
            loading = false,
            onClick = onResetSuccess
        )

        Spacer(Modifier.weight(1f))
        BottomBranding()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun BottomBranding() {
    val design = ShynaDesign.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            Text("Shyna ", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = design.AuthAccent)
            Text("Calling", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = design.TextPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.width(28.dp), color = design.AuthAccent.copy(alpha = 0.4f))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.WorkspacePremium, null, tint = design.AuthAccent, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text("WORLD", color = design.AuthAccent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 10.sp)
            Spacer(Modifier.width(6.dp))
            HorizontalDivider(Modifier.width(28.dp), color = design.AuthAccent.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun RoyalTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, isPassword: Boolean = false) {
    var passwordVisible by remember { mutableStateOf(false) }
    val design = ShynaDesign.colors
    val brandColor = design.AuthAccent
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(label, color = design.TextSecondary, fontSize = 15.sp) },
        leadingIcon = { Icon(icon, null, tint = brandColor, modifier = Modifier.size(22.dp)) },
        trailingIcon = {
            if (isPassword) {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, 
                        null, 
                        tint = brandColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = brandColor,
            unfocusedBorderColor = design.DividerColor,
            focusedLabelColor = brandColor,
            cursorColor = brandColor,
            selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
                handleColor = brandColor,
                backgroundColor = brandColor.copy(alpha = 0.2f)
            ),
            unfocusedContainerColor = design.SurfaceBg,
            focusedContainerColor = design.SurfaceBg
        ),
        visualTransformation = if (isPassword && !passwordVisible) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        singleLine = true,
        textStyle = TextStyle(fontSize = 16.sp, color = design.TextPrimary)
    )
}

@Composable
private fun SocialLoginButton(
    icon: ImageVector, 
    text: String, 
    iconColor: Color = Color.Unspecified, 
    isGoogle: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(60.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
        color = Color.White
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isGoogle) {
                // Professional Colorful G Logo using Canvas segments
                Box(Modifier.size(24.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val size = size.minDimension
                        val strokeWidth = size * 0.2f
                        
                        // Red segment
                        drawArc(Color(0xFFEA4335), -45f, -90f, false, style = Stroke(strokeWidth))
                        // Yellow segment
                        drawArc(Color(0xFFFBBC05), 45f, 90f, false, style = Stroke(strokeWidth))
                        // Green segment
                        drawArc(Color(0xFF34A853), 135f, 90f, false, style = Stroke(strokeWidth))
                        // Blue segment
                        drawArc(Color(0xFF4285F4), 225f, 90f, false, style = Stroke(strokeWidth))
                        
                        // Blue middle bar
                        drawLine(Color(0xFF4285F4), Offset(size/2, size/2), Offset(size, size/2), strokeWidth = strokeWidth)
                    }
                }
            } else {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(text, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SelectContactDialog(
    users: List<RealUser>,
    onDismiss: () -> Unit,
    onSelect: (RealUser) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(users, query) {
        users.filter { it.name.contains(query, true) || it.userId.contains(query, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Select contact", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                PremiumSearchBar(query = query, onQueryChange = { query = it })
            }
        },
        text = {
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No contacts found", color = ShynaDesign.colors.TextSecondary)
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(filtered) { user ->
                        ListItem(
                            headlineContent = { Text(user.name, color = ShynaDesign.colors.TextPrimary) },
                            supportingContent = { Text(user.userId, color = ShynaDesign.colors.TextSecondary) },
                            leadingContent = {
                                Surface(Modifier.size(40.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                                    if (!user.photoUrl.isNullOrBlank()) AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
                                    else Box(contentAlignment = Alignment.Center) { Text(user.name.take(1).uppercase(), color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold) }
                                }
                            },
                            modifier = Modifier.clickable { onSelect(user) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ShynaDesign.colors.BrandGreen) } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun AccountSettingsDialog(
    user: RealUser?,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val mContext = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = { Text("This action is permanent and cannot be undone. All your chats and data will be erased from Shyna servers.") },
            confirmButton = {
                TextButton(onClick = { 
                    FirebaseAuth.getInstance().currentUser?.delete()
                    onLogout()
                    onDismiss()
                }) { Text("DELETE", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("CANCEL") } },
            containerColor = ShynaDesign.colors.SurfaceBg
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Account", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AccountOption("Security notifications", "Get notified when your security code changes", Icons.Outlined.Shield) {
                    Toast.makeText(mContext, "Security notifications enabled", Toast.LENGTH_SHORT).show()
                }
                AccountOption("Two-step verification", "Add extra security to your account", Icons.Outlined.VerifiedUser) {
                    Toast.makeText(mContext, "Two-step verification is active via Shyna Secure", Toast.LENGTH_SHORT).show()
                }
                AccountOption("Change number", "Migrate your account info to a new number", Icons.Outlined.Smartphone) {
                    Toast.makeText(mContext, "Please contact support to change your verified phone number", Toast.LENGTH_LONG).show()
                }
                AccountOption("Request account info", "Get a report of your account info", Icons.Outlined.Description) {
                    Toast.makeText(mContext, "Request sent. Report will be ready in 3 days.", Toast.LENGTH_LONG).show()
                }
                AccountOption("Delete account", "Permanently delete your account", Icons.Outlined.Delete, Color.Red) {
                    showDeleteConfirm = true
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = ShynaDesign.colors.BrandGreen) } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun AccountOption(title: String, subtitle: String, icon: ImageVector, color: Color = ShynaDesign.colors.TextPrimary, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = color) },
        supportingContent = { Text(subtitle, color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp) },
        leadingContent = { Icon(icon, null, tint = ShynaDesign.colors.TextSecondary) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ChatSettingsDialog(
    mode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val mContext = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chats", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Display", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 14.sp)
                ListItem(
                    headlineContent = { Text("Dark Mode", color = ShynaDesign.colors.TextPrimary) },
                    supportingContent = { Text(if(mode == ThemeMode.DARK) "On" else "Off", color = ShynaDesign.colors.TextSecondary) },
                    leadingContent = { Icon(Icons.Default.DarkMode, null, tint = ShynaDesign.colors.TextSecondary) },
                    trailingContent = { Switch(mode == ThemeMode.DARK, { onThemeChange(if (it) ThemeMode.DARK else ThemeMode.LIGHT) }, colors = SwitchDefaults.colors(checkedThumbColor = ShynaDesign.colors.BrandGreen)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Wallpaper", color = ShynaDesign.colors.TextPrimary) },
                    leadingContent = { Icon(Icons.Outlined.Wallpaper, null, tint = ShynaDesign.colors.TextSecondary) },
                    /*
                    modifier = Modifier.clickable { Toast.makeText(mContext, "Premium Wallpapers coming soon!", Toast.LENGTH_SHORT).show() },
                    */
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                
                Spacer(Modifier.height(16.dp))
                Text("Chat settings", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.BrandGreen, fontSize = 14.sp)
                ListItem(
                    headlineContent = { Text("Enter is send", color = ShynaDesign.colors.TextPrimary) },
                    supportingContent = { Text("Enter key will send your message", color = ShynaDesign.colors.TextSecondary) },
                    trailingContent = { Switch(true, {}, colors = SwitchDefaults.colors(checkedThumbColor = ShynaDesign.colors.BrandGreen)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Media visibility", color = ShynaDesign.colors.TextPrimary) },
                    supportingContent = { Text("Show newly downloaded media in your phone's gallery", color = ShynaDesign.colors.TextSecondary) },
                    trailingContent = { Switch(true, {}, colors = SwitchDefaults.colors(checkedThumbColor = ShynaDesign.colors.BrandGreen)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = ShynaDesign.colors.BrandGreen) } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    val mContext = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Help", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                HelpItem("Help Center", "Get answers, view documentation", Icons.Outlined.HelpCenter)
                HelpItem("Contact us", "Questions? Need help?", Icons.Outlined.SupportAgent)
                HelpItem("Terms and Privacy Policy", "", Icons.Outlined.Description)
                HelpItem("App info", "", Icons.Outlined.Info)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = ShynaDesign.colors.BrandGreen) } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun HelpItem(title: String, subtitle: String, icon: ImageVector) {
    val mContext = LocalContext.current
    ListItem(
        headlineContent = { Text(title, color = ShynaDesign.colors.TextPrimary) },
        supportingContent = if(subtitle.isNotEmpty()) { { Text(subtitle, color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp) } } else null,
        leadingContent = { Icon(icon, null, tint = ShynaDesign.colors.TextSecondary) },
        modifier = Modifier.clickable { Toast.makeText(mContext, "$title opened", Toast.LENGTH_SHORT).show() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun InviteDialog(onDismiss: () -> Unit) {
    val mContext = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite a friend", color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(48.dp), tint = ShynaDesign.colors.BrandGreen)
                Spacer(Modifier.height(16.dp))
                Text("Share Shyna Calling with your friends and family to start secure conversations.", textAlign = TextAlign.Center, color = ShynaDesign.colors.TextSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Hey! Download Shyna Calling for secure and smart communication. Join me here!")
                    }
                    mContext.startActivity(Intent.createChooser(intent, "Invite via"))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
            ) { Text("Invite") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun BlockedListDialog(userId: String, onDismiss: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var blockedUsers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(userId) {
        db.collection("users").document(userId).collection("blockedUsers")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    blockedUsers = it.documents.map { d -> d.id to (d.getString("name") ?: "Blocked User") }
                }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked contacts", fontWeight = FontWeight.Bold) },
        text = {
            if (blockedUsers.isEmpty()) {
                Text("No blocked contacts", color = ShynaDesign.colors.TextSecondary)
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(blockedUsers) { (uid, name) ->
                        ListItem(
                            headlineContent = { Text(name, color = ShynaDesign.colors.TextPrimary) },
                            trailingContent = { 
                                TextButton(onClick = { 
                                    db.collection("users").document(userId).collection("blockedUsers").document(uid).delete()
                                }) { Text("UNBLOCK", color = ShynaDesign.colors.BrandGreen) }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}
