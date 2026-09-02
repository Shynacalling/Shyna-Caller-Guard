package com.example.callruleblocker.ui

import android.content.Context
import android.content.ContentValues
import android.content.ContentProviderOperation
import android.content.Intent
import android.util.Log
import android.speech.RecognizerIntent
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.core.content.edit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Call
import com.example.callruleblocker.call.CallStateController
import com.example.callruleblocker.call.MainCallType
import com.example.callruleblocker.call.GlobalCallState
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callruleblocker.call.SimCallManager
import com.example.callruleblocker.call.SimChoice
import com.example.callruleblocker.data.Rule
import com.example.callruleblocker.data.RuleRepository
import com.example.callruleblocker.data.CallLogTrashStore
import com.example.callruleblocker.data.BlockedCallStore
import com.example.callruleblocker.data.TrashedCallEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.callruleblocker.call.SamsungRecordingHelper
import com.example.callruleblocker.call.RecordingPlayback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.io.File
import kotlin.math.abs

private enum class PhoneTab { KEYPAD, RECENTS, CONTACTS }
private enum class CallTypeFilter { ALL, MISSED, REJECTED, OUTGOING, INCOMING, BLOCKED, AUTO_BLOCKED }

data class RecentCall(
    val id: Long = 0L,
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val durationSeconds: Long,
    val phoneAccountId: String?,
    val features: Int = 0,
    val simSlotIndex: Int = 0,
    val photoUri: String? = null
)
data class RecentCallGroup(val latest: RecentCall, val count: Int)
data class RecentSection(val label: String, val groups: List<RecentCallGroup>)
data class PhoneContact(
    val id: Long,
    val name: String,
    val number: String,
    val photoUri: String?,
    val accountType: String?
)

@Composable
fun PhoneHomeScreen(
    initialSearchVisible: Boolean = false,
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenOfflineCall: () -> Unit,
    onOpenOnlineCall: () -> Unit,
    onOpenRecordings: () -> Unit,
    onFontScaleChanged: (Float) -> Unit = {},
    onCall: (String, Int?) -> Unit
) {
    val appearance = LocalAppearance.current
    val context = LocalContext.current
    var selectedTab by rememberSaveable { 
        mutableStateOf(if (initialSearchVisible) PhoneTab.CONTACTS else PhoneTab.RECENTS) 
    }
    
    // --- SMOOTH PINCH ZOOM STATE ---
    var isPinching by remember { mutableStateOf(false) }
    var gestureScale by remember { mutableFloatStateOf(1f) }
    
    val displayScale by animateFloatAsState(
        targetValue = if (isPinching) appearance.uiScale * gestureScale else appearance.uiScale,
        animationSpec = if (isPinching) snap() else spring(dampingRatio = 0.82f, stiffness = 450f),
        label = "premiumZoom"
    )

    // --- PREMIUM INTERCEPTOR PINCH ZOOM ---
    // --- OPTIMIZED PINCH ZOOM ---
    val pinchModifier = Modifier.pointerInput(appearance.uiScale) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                if (event.changes.size >= 2) {
                    isPinching = true
                    val zoomChange = event.calculateZoom()
                    if (zoomChange != 1f) {
                        gestureScale *= zoomChange
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })

            if (isPinching) {
                val finalTarget = (appearance.uiScale * gestureScale).coerceIn(0.85f, 1.30f)
                val snapped = PersonalizationManager.ALL_SCALES.minByOrNull { abs(it - finalTarget) } ?: 0.95f
                
                isPinching = false
                gestureScale = 1f
                
                if (snapped != appearance.uiScale) {
                    onFontScaleChanged(snapped)
                    PersonalizationManager.saveUiScale(context, snapped)
                }
            }
        }
    }
    
    var menuExpanded by remember { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(initialSearchVisible) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var recentsFilterRequest by remember { mutableIntStateOf(0) }
    var keypadHasNumber by rememberSaveable { mutableStateOf(false) }
    var detailNumber by rememberSaveable { mutableStateOf<String?>(null) }
    val popupPrefs = remember { context.getSharedPreferences("recents_popup", Context.MODE_PRIVATE) }
    var dismissedMissedDate by rememberSaveable {
        mutableLongStateOf(popupPrefs.getLong("dismissed_missed_date", -1L))
    }
    var missedPopupRevision by remember { mutableIntStateOf(0) }
    var latestMissedForPopup by remember { mutableStateOf<RecentCall?>(null) }
    var latestMissedCount by remember { mutableIntStateOf(0) }

    var contactsRevision by remember { mutableIntStateOf(0) }
    FastContactsObserver { contactsRevision++ }

    DisposableEffect(context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return@DisposableEffect onDispose {}
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { missedPopupRevision++ }
        }
        try {
            context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        } catch (e: SecurityException) {
            Log.e("PhoneHome", "Failed to register CallLog observer", e)
        }
        onDispose { 
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }

    LaunchedEffect(missedPopupRevision, selectedTab) {
        if (selectedTab == PhoneTab.RECENTS) {
            runCatching { loadRecentCalls(context) }.onSuccess { allCalls ->
                val latest = allCalls.firstOrNull { it.type == CallLog.Calls.MISSED_TYPE }
                latestMissedForPopup = latest
                latestMissedCount = latest?.let { missed ->
                    allCalls.count { call ->
                        call.type == CallLog.Calls.MISSED_TYPE &&
                            normalizePhone(call.number) == normalizePhone(missed.number) &&
                            dayKey(call.date) == dayKey(missed.date)
                    }
                } ?: 0
            }
        }
    }

    val showRootMissedPopup = selectedTab == PhoneTab.RECENTS &&
        !searchVisible &&
        latestMissedForPopup?.date?.let { it != dismissedMissedDate } == true

    val recentsListState = rememberLazyListState()
    val density = LocalDensity.current
    val recentsHeaderMaxDp = with(density) { 800.toDp() }
    val recentsHeaderTarget by remember {
        derivedStateOf {
            val scale = appearance.uiScale
            if (selectedTab != PhoneTab.RECENTS) recentsHeaderMaxDp.scaled(scale)
            else with(density) {
                val scrollPx = if (recentsListState.firstVisibleItemIndex > 0) {
                    recentsHeaderMaxDp.scaled(scale).toPx()
                } else {
                    recentsListState.firstVisibleItemScrollOffset.toFloat()
                }
                (recentsHeaderMaxDp.scaled(scale).toPx() - scrollPx).coerceIn(0f, recentsHeaderMaxDp.scaled(scale).toPx()).toDp()
            }
        }
    }
    val recentsHeaderHeight by animateDpAsState(
        targetValue = recentsHeaderTarget,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 650f),
        label = "recentsHeaderSmooth"
    )

    detailNumber?.let { number ->
        RecentNumberDetailsScreen(number = number, onBack = { detailNumber = null }, onCall = { onCall(it, null) })
        return
    }

    CompositionLocalProvider(LocalAppearance provides appearance.copy(uiScale = displayScale)) {
        Box(Modifier.fillMaxSize().then(pinchModifier)) {
            Scaffold(
                containerColor = Color(0xFF100B18),
                topBar = {
                    PhoneTopBar(
                        selectedTab = selectedTab,
                        searchVisible = searchVisible,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onSearchToggle = {
                            if (selectedTab == PhoneTab.KEYPAD) {
                                selectedTab = PhoneTab.CONTACTS
                                searchVisible = true
                            } else {
                                searchVisible = !searchVisible
                                if (!searchVisible) searchQuery = ""
                            }
                        },
                        onAddContact = { openNewContact(context, "") },
                        onRecentsFilter = { recentsFilterRequest++ },
                        recentsHeaderHeight = recentsHeaderHeight,
                        onOpenRules = onOpenRules,
                        onOpenSettings = onOpenSettings,
                        onOpenRecycleBin = onOpenRecycleBin,
                        onOpenOfflineCall = onOpenOfflineCall,
                        onOpenOnlineCall = onOpenOnlineCall,
                        onOpenRecordings = onOpenRecordings,
                        menuExpanded = menuExpanded,
                        onMenuExpandedChange = { menuExpanded = it }
                    )
                },
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp.scaled())
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!(selectedTab == PhoneTab.KEYPAD && keypadHasNumber)) {
                            TextOnlyBottomNav(
                                selectedTab = selectedTab,
                                onSelect = { tab ->
                                    selectedTab = tab
                                    if (tab != PhoneTab.CONTACTS && tab != PhoneTab.RECENTS) searchVisible = false
                                }
                            )
                        }
                    }
                }
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black, Color(0xFF100B18), Color(0xFF24112E))))
                        .padding(padding)
                ) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (selectedTab) {
                            PhoneTab.KEYPAD -> KeypadScreen(
                                contactsRevision = contactsRevision,
                                onCall = onCall, 
                                onNumberEntryChanged = { keypadHasNumber = it }
                            )
                            PhoneTab.RECENTS -> RecentsScreen(searchQuery.trim(), contactsRevision, filterRequest = recentsFilterRequest, listState = recentsListState, onCall = { number -> onCall(number, null) }, onOpenDetails = { detailNumber = it })
                            PhoneTab.CONTACTS -> ContactsScreen(searchQuery.trim(), contactsRevision) { number -> onCall(number, null) }
                        }
                    }
                }
            }
            if (showRootMissedPopup) {
                latestMissedForPopup?.let { missedCall ->
                    MissedCallPopupCard(
                        call = missedCall,
                        count = latestMissedCount.coerceAtLeast(1),
                        onDismiss = {
                            dismissedMissedDate = missedCall.date
                            popupPrefs.edit { putLong("dismissed_missed_date", missedCall.date) }
                        },
                        onCallBack = {
                            dismissedMissedDate = missedCall.date
                            popupPrefs.edit { putLong("dismissed_missed_date", missedCall.date) }
                            onCall(missedCall.number, null)
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 74.dp.scaled())
                    )
                }
            }
        }
    }
}

@Composable
private fun TextOnlyBottomNav(
    selectedTab: PhoneTab,
    onSelect: (PhoneTab) -> Unit
) {
    Surface(color = Color.Black) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp.scaled(), vertical = 12.dp.scaled())) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomTextTab("Keypad", selectedTab == PhoneTab.KEYPAD) { onSelect(PhoneTab.KEYPAD) }
                BottomTextTab("Recents", selectedTab == PhoneTab.RECENTS) { onSelect(PhoneTab.RECENTS) }
                BottomTextTab("Contacts", selectedTab == PhoneTab.CONTACTS) { onSelect(PhoneTab.CONTACTS) }
            }
        }
    }
}

@Composable
private fun BottomTextTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val appearance = LocalAppearance.current
    val scale by animateFloatAsState(if (selected) 1.15f else 1f, label = "tabScale")
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp.scaled(), vertical = 4.dp.scaled())
            .graphicsLayer { scaleX = scale; scaleY = scale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp.scaled())
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = 17.sp.scaled(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        Box(
            Modifier
                .height(4.dp.scaled())
                .width(if (selected) 48.dp.scaled() else 0.dp)
                .clip(CircleShape)
                .background(
                    if (selected) Brush.horizontalGradient(listOf(appearance.accentColor, appearance.accentColor.copy(alpha = 0.6f)))
                    else SolidColor(Color.Transparent)
                )
                .animateContentSize()
        )
    }
}

@Composable
private fun UnifiedSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onClose: () -> Unit,
    placeholder: String = "Type to search..."
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp), // Fixed to professional 48dp standard
        shape = CircleShape,
        color = Color(0xFFF9F9FA),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E3E6).copy(alpha = 0.6f)),
        shadowElevation = 3.dp.scaled()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp.scaled(), end = 4.dp.scaled()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "Search",
                tint = Color(0xFF263445),
                modifier = Modifier.size(22.dp.scaled())
            )
            Spacer(Modifier.width(10.dp.scaled()))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFF20252B),
                    fontSize = 16.sp.scaled(),
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(Color(0xFF263445)),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color(0xFF9AA0A8),
                            fontSize = 16.sp.scaled(),
                            fontWeight = FontWeight.Normal
                        )
                    }
                    innerTextField()
                }
            )
            IconButton(
                onClick = onVoiceClick,
                modifier = Modifier.size(40.dp.scaled())
            ) {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = "Voice search",
                    tint = Color(0xFF58616D),
                    modifier = Modifier.size(24.dp.scaled())
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(34.dp.scaled())
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Close search",
                    tint = Color(0xFF8A9098),
                    modifier = Modifier.size(16.dp.scaled())
                )
            }
        }
    }
}

@Composable
private fun PhoneTopBar(
    selectedTab: PhoneTab,
    searchVisible: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onAddContact: () -> Unit,
    onRecentsFilter: () -> Unit,
    recentsHeaderHeight: androidx.compose.ui.unit.Dp,
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenOfflineCall: () -> Unit,
    onOpenOnlineCall: () -> Unit,
    onOpenRecordings: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) onSearchQueryChange(spoken)
        }
    }
    val startVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a name or number")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Voice search is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        modifier = Modifier.statusBarsPadding(), 
        color = Color.Black // Unified black background
    ) {
        val barHeight = 78.dp.scaled()
        val effectiveTopBarHeight = when {
            searchVisible -> 64.dp // Balanced container height for 48dp search pill
            selectedTab == PhoneTab.RECENTS -> recentsHeaderHeight.coerceAtLeast(barHeight)
            else -> barHeight
        }
        Box(modifier = Modifier.fillMaxWidth().height(effectiveTopBarHeight)) {
            when {
                searchVisible -> {
                    Box(Modifier.fillMaxSize().padding(horizontal = 18.dp.scaled()), contentAlignment = Alignment.Center) {
                        UnifiedSearchBar(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onVoiceClick = startVoiceSearch,
                            onClose = onSearchToggle
                        )
                    }
                }
                else -> {
                    val title = when (selectedTab) {
                        PhoneTab.KEYPAD -> "Phone"
                        PhoneTab.RECENTS -> "Recents"
                        PhoneTab.CONTACTS -> "Contacts"
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 22.dp.scaled()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 23.sp.scaled(), // Updated to 23 point as requested
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = onSearchToggle) {
                                Icon(
                                    Icons.Outlined.Search, 
                                    "Search", 
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp.scaled())
                                )
                            }
                            
                            if (selectedTab == PhoneTab.RECENTS) {
                                IconButton(onClick = onRecentsFilter) {
                                    Icon(
                                        Icons.Outlined.FilterList, 
                                        "Filter", 
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp.scaled())
                                    )
                                }
                            } else {
                                IconButton(onClick = onAddContact) {
                                    Icon(
                                        Icons.Outlined.Add, 
                                        "Add", 
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp.scaled())
                                    )
                                }
                            }
                            
                            MoreMenuButton(
                                menuExpanded, 
                                onMenuExpandedChange, 
                                onOpenRules, 
                                onOpenSettings, 
                                onOpenRecycleBin, 
                                onOpenOfflineCall, 
                                onOpenOnlineCall,
                                onOpenRecordings
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreMenuButton(
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenOfflineCall: () -> Unit,
    onOpenOnlineCall: () -> Unit,
    onOpenRecordings: () -> Unit
) {
    Box {
        IconButton(onClick = { onMenuExpandedChange(true) }) { 
            Icon(
                Icons.Outlined.MoreVert, 
                "More", 
                tint = Color.White,
                modifier = Modifier.size(26.dp.scaled()) // Standardized size
            ) 
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { onMenuExpandedChange(false) },
            modifier = Modifier.background(Color(0xFF17121F))
        ) {
            val secondaryFeatures = CallStateController.getSecondaryFeatures()
            secondaryFeatures.forEach { feature ->
                when (feature) {
                    MainCallType.PHONE_DIALER -> {
                        DropdownMenuItem(
                            text = { Text("Phone Dialer", color = Color.White) },
                            leadingIcon = { Icon(Icons.Outlined.Dialpad, null, tint = Color(0xFFD4A017)) },
                            onClick = { onMenuExpandedChange(false); /* Navigate to Dialer - though we are already here */ }
                        )
                    }
                    MainCallType.SHYNA_LINK -> {
                        DropdownMenuItem(
                            text = { Text("Shyna Link", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.WorkspacePremium, null, tint = Color(0xFFD4A017)) },
                            onClick = { 
                                onMenuExpandedChange(false)
                                CallStateController.setPrimaryFeature(MainCallType.SHYNA_LINK)
                                onOpenOnlineCall() 
                            }
                        )
                    }
                    MainCallType.OFFLINE_CALL -> {
                        DropdownMenuItem(
                            text = { Text("Offline Call", color = Color.White) },
                            leadingIcon = { Icon(Icons.Outlined.SettingsInputAntenna, null, tint = Color(0xFFD4A017)) },
                            onClick = { 
                                onMenuExpandedChange(false)
                                CallStateController.setPrimaryFeature(MainCallType.OFFLINE_CALL)
                                onOpenOfflineCall() 
                            }
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF3C3245))
            DropdownMenuItem(
                text = { Text("Block & SIM rules") },
                leadingIcon = { Icon(Icons.Outlined.Block, null) },
                onClick = { onMenuExpandedChange(false); onOpenRules() }
            )
            DropdownMenuItem(
                text = { Text("Call settings") },
                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                onClick = { onMenuExpandedChange(false); onOpenSettings() }
            )
            DropdownMenuItem(
                text = { Text("Recycle bin") },
                leadingIcon = { Icon(Icons.Outlined.RestoreFromTrash, null) },
                onClick = { onMenuExpandedChange(false); onOpenRecycleBin() }
            )
            DropdownMenuItem(
                text = { Text("Call recordings", color = Color.White) },
                leadingIcon = { Icon(Icons.Outlined.Mic, null, tint = Color(0xFFFFCC65)) },
                onClick = { onMenuExpandedChange(false); onOpenRecordings() }
            )
        }
    }
}

@Composable
fun KeypadScreen(
    contactsRevision: Int = 0,
    prefill: String? = null,
    onCall: (String, Int?) -> Unit,
    onNumberEntryChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val appearance = LocalAppearance.current

    LaunchedEffect(prefill) {
        if (!prefill.isNullOrBlank()) {
            DialerState.clear()
            prefill.forEach { DialerState.append(it.toString()) }
        }
    }

    val textFieldValue = DialerState.textFieldValue
    val number = textFieldValue.text
    val keypadTonesEnabled = remember(context) { 
        context.getSharedPreferences("call_settings", Context.MODE_PRIVATE).getBoolean("keypad_tones", true) 
    }

    var contacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    var contactsUnavailable by remember { mutableStateOf(false) }
    val dialerPrefs = remember { context.getSharedPreferences(SimCallManager.PREFS, Context.MODE_PRIVATE) }
    var simChoices by remember { mutableStateOf<List<SimChoice>>(emptyList()) }
    var simMode by rememberSaveable { mutableStateOf(dialerPrefs.getString(SimCallManager.KEY_DEFAULT_SIM, SimCallManager.MODE_ASK) ?: SimCallManager.MODE_ASK) }
    var showSimMenu by remember { mutableStateOf(false) }
    var showAskDialog by remember { mutableStateOf(false) }
    var askForVideo by remember { mutableStateOf(false) }
    LaunchedEffect(number) { onNumberEntryChanged(number.isNotBlank()) }
    
    androidx.activity.compose.BackHandler(enabled = number.isNotEmpty()) {
        DialerState.clear()
    }

    DisposableEffect(Unit) { onDispose { onNumberEntryChanged(false) } }
    val keys = remember {
        listOf(
            "1" to "", "2" to "ABC", "3" to "DEF",
            "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
            "*" to "", "0" to "+", "#" to ""
        )
    }

    LaunchedEffect(contactsRevision) {
        simChoices = SimCallManager.getChoices(context)
        runCatching { loadContacts(context) }
            .onSuccess { contacts = it; contactsUnavailable = false }
            .onFailure { contactsUnavailable = true }
    }

    val cleanQuery = remember(number) { number.filter(Char::isDigit) }
    var suggestions by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    LaunchedEffect(cleanQuery, contacts) {
        if (cleanQuery.isBlank()) {
            suggestions = emptyList()
        } else {
            // Speed optimization: move calculation to background
            withContext(Dispatchers.Default) {
                val result = contacts.asSequence()
                    .map { contact ->
                        val phoneDigits = contact.number.filter(Char::isDigit)
                        val t9Name = contact.name.toT9Digits()
                        val rank = when {
                            phoneDigits == cleanQuery -> 0
                            phoneDigits.startsWith(cleanQuery) -> 1
                            phoneDigits.contains(cleanQuery) -> 2
                            t9Name.startsWith(cleanQuery) -> 3
                            t9Name.contains(cleanQuery) -> 4
                            else -> 99
                        }
                        contact to rank
                    }
                    .filter { it.second < 99 }
                    .sortedWith(compareBy<Pair<PhoneContact, Int>> { it.second }.thenBy { it.first.name })
                    .map { it.first }
                    .distinctBy { it.number.filter(Char::isDigit) }
                    .take(3)
                    .toList()
                suggestions = result
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp.scaled()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp.scaled()))
        val fixedNumberAreaHeight = 150.dp.scaled() 
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(fixedNumberAreaHeight)
                .padding(horizontal = 2.dp.scaled()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val selectionColors = TextSelectionColors(
                    handleColor = appearance.accentColor,
                    backgroundColor = appearance.accentColor.copy(alpha = 0.4f)
                )
                CompositionLocalProvider(
                    LocalTextSelectionColors provides selectionColors
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { DialerState.update(it) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true, // KEY FIX: Allows selection but hides soft keyboard
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = dialNumberFontSize(number.length).scaled(),
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(appearance.accentColor),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            autoCorrectEnabled = false,
                            showKeyboardOnFocus = false
                        ),
                        singleLine = false,
                        maxLines = 3
                    )
                }
                
                suggestions.firstOrNull()?.let { match ->
                    Spacer(Modifier.height(4.dp.scaled()))
                    Text(
                        text = match.name,
                        color = appearance.accentColor.copy(alpha = 0.6f),
                        fontSize = 15.sp.scaled(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        
        val onDigitClickMemoized = remember {
            { digit: String ->
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                DialerState.append(digit)
                if (keypadTonesEnabled) {
                    UiFeedback.playClick(context, view)
                }
            }
        }
        
        val onDigitLongClickMemoized = remember {
            { digit: String ->
                if (digit == "0") {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    DialerState.append("+")
                    if (keypadTonesEnabled) {
                        UiFeedback.playClick(context, view)
                    }
                }
            }
        }

        ThemeableKeypad(
            themeId = appearance.dialPadThemeId,
            keys = keys,
            onDigitClick = onDigitClickMemoized,
            onDigitLongClick = onDigitLongClickMemoized
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp.scaled()),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (number.isNotBlank()) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        askForVideo = true
                        placeVideoUsingSelectedSim(
                            context = context,
                            number = number,
                            mode = simMode,
                            choices = simChoices,
                            onAsk = { showAskDialog = true }
                        )
                    }
                },
                enabled = number.isNotBlank()
            ) {
                Icon(Icons.Outlined.Videocam, null, tint = if (number.isNotBlank()) appearance.accentColor else Color(0xFF686173), modifier = Modifier.size(28.dp.scaled()))
            }
            AnimatedCallButton(
                enabled = true, 
                color = appearance.accentColor,
                onClick = { 
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (number.isBlank()) {
                        val last = getLastOutgoingCallNumber(context)
                        if (last != null) {
                            last.forEach { DialerState.append(it.toString()) }
                        } else {
                            Toast.makeText(context, "No recent outgoing calls", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        number.trim().takeIf(String::isNotEmpty)?.let { value -> 
                            askForVideo = false
                            placeUsingSelectedSim(value, simMode, simChoices, { showAskDialog = true }, onCall) 
                        }
                    }
                }
            )
            RepeatingBackspaceButton(
                enabled = number.isNotEmpty(),
                onDelete = { 
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    DialerState.delete() 
                }
            )
        }

        Spacer(Modifier.height(8.dp.scaled()))
        SimSelector(
            choices = simChoices,
            selectedMode = simMode,
            expanded = showSimMenu,
            onExpandedChange = { showSimMenu = it },
            onModeSelected = { mode ->
                simMode = mode
                dialerPrefs.edit { putString(SimCallManager.KEY_DEFAULT_SIM, mode) }
                showSimMenu = false
            }
        )
        Spacer(Modifier.height(8.dp.scaled()))
    }

    if (showAskDialog) {
        AlertDialog(
            onDismissRequest = { showAskDialog = false },
            title = { Text("Call using") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp.scaled())) {
                    simChoices.forEach { choice ->
                        FilledTonalButton(
                            onClick = {
                                showAskDialog = false
                                if (askForVideo) SimCallManager.placeVideoCall(context, number, choice.index)
                                else onCall(number, choice.index)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(choice.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAskDialog = false }) { Text("Cancel") } }
        )
    }
}

private fun getLastOutgoingCallNumber(context: Context): String? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return null
    return context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER),
        "${CallLog.Calls.TYPE} = ?",
        arrayOf(CallLog.Calls.OUTGOING_TYPE.toString()),
        "${CallLog.Calls.DATE} DESC LIMIT 1"
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}

@Composable
private fun SimSelector(
    choices: List<SimChoice>,
    selectedMode: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onModeSelected: (String) -> Unit
) {
    val selectedIndex = selectedMode.removePrefix(SimCallManager.MODE_SIM_PREFIX).toIntOrNull()
    val selectedLabel = when {
        selectedMode == SimCallManager.MODE_ASK -> "Ask every time"
        selectedMode.startsWith(SimCallManager.MODE_SIM_PREFIX) -> choices.getOrNull(selectedIndex ?: -1)?.label ?: "Ask every time"
        else -> "Ask every time"
    }
    Box {
        Surface(
            modifier = Modifier.clickable { onExpandedChange(true) },
            color = Color(0xFF3B3347),
            shape = RoundedCornerShape(18.dp.scaled())
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp.scaled(), vertical = 8.dp.scaled()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp.scaled())
            ) {
                if (selectedMode == SimCallManager.MODE_ASK) {
                    Icon(Icons.Filled.SimCard, null, tint = Color.White, modifier = Modifier.size(16.dp.scaled()))
                } else {
                    SimMiniBadge(index = (selectedIndex ?: 0) + 1)
                }
                Text(selectedLabel, color = Color.White, style = MaterialTheme.typography.labelLarge, fontSize = 14.sp.scaled())
                Icon(Icons.Outlined.ArrowDropDown, null, tint = Color.White, modifier = Modifier.size(18.dp.scaled()))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text("Ask every time", fontSize = 14.sp.scaled()) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, tint = Color(0xFF6B5C82), modifier = Modifier.size(18.dp.scaled())) },
                onClick = { onModeSelected(SimCallManager.MODE_ASK) }
            )
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label, fontSize = 14.sp.scaled()) },
                    leadingIcon = { SimMiniBadge(index = choice.index + 1) },
                    trailingIcon = {
                        if (selectedMode == "${SimCallManager.MODE_SIM_PREFIX}${choice.index}") {
                            Icon(Icons.Outlined.Check, null, tint = Color(0xFF25C983), modifier = Modifier.size(18.dp.scaled()))
                        }
                    },
                    onClick = { onModeSelected("${SimCallManager.MODE_SIM_PREFIX}${choice.index}") }
                )
            }
        }
    }
}

@Composable
fun SimMiniBadge(index: Int) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.SimCard,
            null,
            tint = if (index % 2 == 0) Color(0xFF7F66B2) else Color(0xFF5C77D8),
            modifier = Modifier.size(22.dp.scaled())
        )
        Text(
            text = "$index",
            color = Color.White,
            fontSize = 9.sp.scaled(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 1.dp.scaled())
        )
    }
}

private fun placeVideoUsingSelectedSim(
    context: Context,
    number: String,
    mode: String,
    choices: List<SimChoice>,
    onAsk: () -> Unit
) {
    if (choices.size <= 1) {
        SimCallManager.placeVideoCall(context, number, choices.firstOrNull()?.index)
        return
    }
    if (mode == SimCallManager.MODE_ASK) onAsk()
    else {
        val index = mode.removePrefix(SimCallManager.MODE_SIM_PREFIX).toIntOrNull()
        SimCallManager.placeVideoCall(context, number, index)
    }
}

private fun placeUsingSelectedSim(
    number: String,
    mode: String,
    choices: List<SimChoice>,
    onAsk: () -> Unit,
    onCall: (String, Int?) -> Unit
) {
    if (choices.size <= 1) {
        onCall(number, choices.firstOrNull()?.index)
        return
    }
    if (mode == SimCallManager.MODE_ASK) {
        onAsk()
    } else {
        val index = mode.removePrefix(SimCallManager.MODE_SIM_PREFIX).toIntOrNull()
        onCall(number, index)
    }
}

@Composable
private fun ThemeableKeypad(
    themeId: String,
    keys: List<Pair<String, String>>,
    onDigitClick: (String) -> Unit,
    onDigitLongClick: (String) -> Unit = {}
) {
    when (themeId) {
        "Shyna One" -> SamsungKeypad(keys, onDigitClick, onDigitLongClick)
        "Shyna Fruit" -> IPhoneKeypad(keys, onDigitClick, onDigitLongClick)
        "Shyna Pure", "Shyna Moto", "Shyna Finn", "Shyna Alpha", "Shyna Think" -> MaterialYouKeypad(keys, onDigitClick, onDigitLongClick)
        "Shyna Oxygen", "Shyna Color", "Shyna Vivid", "Shyna Realm" -> ColorOSKeypad(keys, onDigitClick, onDigitLongClick)
        "Shyna Flow", "Shyna Red", "Shyna Blade" -> MIUIKeypad(keys, onDigitClick, onDigitLongClick)
        "Shyna Dot" -> NothingKeypad(keys, onDigitClick, onDigitLongClick)
        "Shyna Honor", "Shyna Harmony", "Shyna Dream" -> EMUIKeypad(keys, onDigitClick, onDigitLongClick)
        "Shyna Hi", "Shyna Infinite" -> HiOSKeypad(keys, onDigitClick, onDigitLongClick)
        "Business" -> BusinessKeypad(keys, onDigitClick, onDigitLongClick)
        "AMOLED" -> AmoledKeypad(keys, onDigitClick, onDigitLongClick)
        "Glass" -> GlassKeypad(keys, onDigitClick, onDigitLongClick)
        else -> ClassicKeypad(keys, onDigitClick, onDigitLongClick)
    }
}

@Composable
private fun MaterialYouKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.width(100.dp.scaled()), contentAlignment = Alignment.Center) {
                        MaterialYouDialKey(digit, letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp.scaled()))
    }
}

@Composable
private fun ColorOSKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.width(100.dp.scaled()), contentAlignment = Alignment.Center) {
                        ColorOSDialKey(digit, letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp.scaled()))
    }
}

@Composable
private fun MIUIKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.width(100.dp.scaled()), contentAlignment = Alignment.Center) {
                        MIUIDialKey(digit, letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp.scaled()))
    }
}

@Composable
private fun NothingKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.width(100.dp.scaled()), contentAlignment = Alignment.Center) {
                        NothingDialKey(digit, letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp.scaled()))
    }
}

@Composable
private fun EMUIKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.width(100.dp.scaled()), contentAlignment = Alignment.Center) {
                        EMUIDialKey(digit, letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp.scaled()))
    }
}

@Composable
private fun HiOSKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.width(100.dp.scaled()), contentAlignment = Alignment.Center) {
                        HiOSDialKey(digit, letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp.scaled()))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaterialYouDialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val appearance = LocalAppearance.current
    Surface(
        modifier = Modifier.size(80.dp.scaled()).combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = CircleShape,
        color = if (pressed) appearance.accentColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.03f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(digit, color = Color.White, fontSize = 32.sp.scaled(), fontWeight = FontWeight.Medium)
                if (letters.isNotEmpty()) Text(letters, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp.scaled())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorOSDialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        modifier = Modifier.size(width = 94.dp.scaled(), height = 66.dp.scaled()).combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp.scaled()),
        color = if (pressed) Color.White.copy(alpha = 0.15f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (pressed) 0.2f else 0.05f))
    ) {
        Row(Modifier.padding(horizontal = 16.dp.scaled()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(digit, color = Color.White, fontSize = 30.sp.scaled(), fontWeight = FontWeight.Light)
            if (letters.isNotEmpty()) Text(letters, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp.scaled())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MIUIDialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val appearance = LocalAppearance.current
    Surface(
        modifier = Modifier.size(72.dp.scaled()).combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp.scaled()),
        color = if (pressed) appearance.accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(digit, color = Color.White, fontSize = 28.sp.scaled(), fontWeight = FontWeight.Bold)
                if (letters.isNotEmpty()) Text(letters, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp.scaled())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NothingDialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        modifier = Modifier.size(76.dp.scaled()).combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = CircleShape,
        color = if (pressed) Color.Red.copy(alpha = 0.15f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (pressed) Color.Red else Color.White.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(digit, color = Color.White, fontSize = 34.sp.scaled(), fontWeight = FontWeight.Light)
                if (letters.isNotEmpty()) Text(letters, color = Color.White.copy(alpha = 0.3f), fontSize = 9.sp.scaled(), letterSpacing = 1.sp.scaled())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EMUIDialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        modifier = Modifier.size(width = 88.dp.scaled(), height = 62.dp.scaled()).combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(24.dp.scaled()),
        color = if (pressed) Color(0xFF2979FF).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(digit, color = Color.White, fontSize = 26.sp.scaled(), fontWeight = FontWeight.Normal)
                if (letters.isNotEmpty()) Text(letters, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp.scaled())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiOSDialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        modifier = Modifier.size(width = 82.dp.scaled(), height = 68.dp.scaled()).combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp.scaled()),
        color = if (pressed) Color(0xFF00C853).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
    ) {
        Column(Modifier.padding(8.dp.scaled()), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit, color = Color.White, fontSize = 24.sp.scaled(), fontWeight = FontWeight.ExtraBold)
            if (letters.isNotEmpty()) Text(letters, color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp.scaled())
        }
    }
}

@Composable
private fun ClassicKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.width(100.dp.scaled()), contentAlignment = Alignment.Center) {
                        DialKey(digit = digit, letters = letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp.scaled()))
    }
}

@Composable
private fun SamsungKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.width(100.dp.scaled()), contentAlignment = Alignment.Center) {
                        SamsungDialKey(digit, letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp.scaled()))
    }
}

@Composable
private fun IPhoneKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.width(100.dp.scaled()), contentAlignment = Alignment.Center) {
                        IPhoneDialKey(digit, letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp.scaled()))
    }
}

@Composable
private fun BusinessKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        BusinessDialKey(digit, letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp.scaled()))
    }
}

@Composable
private fun AmoledKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.width(100.dp.scaled()), contentAlignment = Alignment.Center) {
                        AmoledDialKey(digit, letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp.scaled()))
    }
}

@Composable
private fun GlassKeypad(keys: List<Pair<String, String>>, onDigitClick: (String) -> Unit, onDigitLongClick: (String) -> Unit) {
    keys.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { (digit, letters) ->
                key(digit) {
                    Box(Modifier.width(100.dp.scaled()), contentAlignment = Alignment.Center) {
                        GlassDialKey(digit, letters, onClick = { onDigitClick(digit) }, onLongClick = { onDigitLongClick(digit) })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp.scaled()))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SamsungDialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val appearance = LocalAppearance.current
    Surface(
        modifier = Modifier.size(width = 90.dp.scaled(), height = 64.dp.scaled())
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(24.dp.scaled()),
        color = if (pressed) appearance.accentColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(digit, color = Color.White, fontSize = 28.sp.scaled(), fontWeight = FontWeight.Bold)
                if (letters.isNotEmpty()) Text(letters, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp.scaled())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IPhoneDialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        modifier = Modifier.size(76.dp.scaled())
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = CircleShape,
        color = if (pressed) Color.Gray.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(digit, color = Color.White, fontSize = 32.sp.scaled())
                if (letters.isNotEmpty()) Text(letters, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp.scaled(), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BusinessDialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        modifier = Modifier.fillMaxWidth(0.3f).height(62.dp.scaled())
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(4.dp.scaled()),
        color = if (pressed) Color.White.copy(alpha = 0.15f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(Modifier.padding(horizontal = 16.dp.scaled()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(digit, color = Color.White, fontSize = 34.sp.scaled(), fontWeight = FontWeight.Light)
            if (letters.isNotEmpty()) Text(letters, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp.scaled())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AmoledDialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val appearance = LocalAppearance.current
    Box(
        modifier = Modifier.size(80.dp.scaled())
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (pressed) Box(Modifier.size(60.dp.scaled()).background(appearance.accentColor.copy(alpha = 0.1f), CircleShape))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit, color = if (pressed) appearance.accentColor else Color.White, fontSize = 36.sp.scaled(), fontWeight = FontWeight.Normal)
            if (letters.isNotEmpty()) Text(letters, color = if (pressed) appearance.accentColor.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f), fontSize = 10.sp.scaled())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassDialKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        modifier = Modifier.size(84.dp.scaled())
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp.scaled()),
        color = if (pressed) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(digit, color = Color.White, fontSize = 30.sp.scaled(), fontWeight = FontWeight.Thin)
                if (letters.isNotEmpty()) Text(letters, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp.scaled())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val appearance = LocalAppearance.current
    val scale by animateFloatAsState(targetValue = if (pressed) 0.82f else 1f, animationSpec = spring(dampingRatio = 0.45f, stiffness = 800f), label = "dial-key-scale")
    val keyColor by animateColorAsState(targetValue = if (pressed) appearance.accentColor.copy(alpha = 0.15f) else Color.Transparent, animationSpec = tween(150), label = "dial-key-color")
    val textColor by animateColorAsState(targetValue = if (pressed) appearance.accentColor else Color.White, label = "dial-key-text-color")
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val view = LocalView.current

    Box(contentAlignment = Alignment.Center) {
        if (pressed) {
            Box(modifier = Modifier.size(94.dp.scaled()).background(Brush.radialGradient(listOf(appearance.accentColor.copy(alpha = 0.25f), Color.Transparent)), CircleShape))
        }
        Surface(
            modifier = Modifier.size(82.dp.scaled()).graphicsLayer { scaleX = scale; scaleY = scale }
                .combinedClickable(interactionSource = interaction, indication = null, onClick = { haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap); UiFeedback.playClick(context, view); onClick() }, onLongClick = onLongClick),
            shape = CircleShape, color = keyColor,
            border = if (pressed) androidx.compose.foundation.BorderStroke(1.dp, appearance.accentColor.copy(alpha = 0.2f)) else null,
            shadowElevation = if (pressed) 12.dp.scaled() else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(digit, color = textColor, fontSize = 34.sp.scaled(), fontWeight = FontWeight.Normal)
                    if (letters.isNotEmpty()) {
                        Text(letters, color = if (pressed) appearance.accentColor.copy(alpha = 0.7f) else Color(0xFF7A7485), fontSize = 11.sp.scaled(), letterSpacing = 1.2.sp.scaled(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RepeatingBackspaceButton(enabled: Boolean, onDelete: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.84f else 1f, label = "backspace-scale")
    val background by animateColorAsState(if (isPressed) Color(0xFF4B3D58) else Color.Transparent, label = "backspace-background")
    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) {
            delay(380)
            while (true) { onDelete(); haptics.performHapticFeedback(HapticFeedbackType.LongPress); delay(110) }
        }
    }
    Surface(
        modifier = Modifier.size(56.dp.scaled()).graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = { haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap); onDelete() }),
        shape = CircleShape, color = background
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = if (enabled) Color.White else Color(0xFF686173), modifier = Modifier.size(28.dp.scaled()))
        }
    }
}

@Composable
private fun AnimatedCallButton(enabled: Boolean, color: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "call-button-scale")
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val view = LocalView.current
    Surface(
        modifier = Modifier.size(76.dp.scaled()).graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); UiFeedback.playClick(context, view); onClick() }),
        shape = CircleShape, color = if (enabled) color else Color(0xFF25222D), shadowElevation = if (enabled) 8.dp.scaled() else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Call, "Call", Modifier.size(34.dp.scaled()), tint = Color.White)
        }
    }
}

private fun String.toT9Digits(): String = buildString {
    this@toT9Digits.uppercase(Locale.getDefault()).forEach { char ->
        val digit = when (char) {
            in "ABC" -> '2'
            in "DEF" -> '3'
            in "GHI" -> '4'
            in "JKL" -> '5'
            in "MNO" -> '6'
            in "PQRS" -> '7'
            in "TUV" -> '8'
            in "WXYZ" -> '9'
            else -> char.takeIf(Char::isDigit)
        }
        digit?.let { append(it) }
    }
}

@Composable
private fun RecentsScreen(query: String, contactsRevision: Int, filterRequest: Int, listState: LazyListState, onCall: (String) -> Unit, onOpenDetails: (String) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val appearance = LocalAppearance.current
    var calls by remember { mutableStateOf<List<RecentCall>>(emptyList()) }
    var callTypeFilter by rememberSaveable { mutableStateOf(CallTypeFilter.ALL) }
    var simFilter by rememberSaveable { mutableStateOf("ALL") }
    var filterDialog by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var selectedCallIds by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    suspend fun refreshCalls() { runCatching { loadRecentCalls(context) }.onSuccess { calls = it; loadFailed = false }.onFailure { loadFailed = true } }
    LaunchedEffect(contactsRevision) {
        contactInfoCache.clear()
        refreshCalls()
    }
    LaunchedEffect(filterRequest) { if (filterRequest > 0) filterDialog = true }
    val shown = remember(calls, query, callTypeFilter, simFilter) {
        calls.filter { call ->
            val queryMatch = query.isBlank() || call.number.contains(query, true) || call.name.orEmpty().contains(query, true)
            val typeMatch = when (callTypeFilter) {
                CallTypeFilter.ALL -> call.type != 99 && call.type != CallLog.Calls.BLOCKED_TYPE
                CallTypeFilter.MISSED -> call.type == CallLog.Calls.MISSED_TYPE
                CallTypeFilter.REJECTED -> call.type == CallLog.Calls.REJECTED_TYPE
                CallTypeFilter.OUTGOING -> call.type == CallLog.Calls.OUTGOING_TYPE
                CallTypeFilter.INCOMING -> call.type == CallLog.Calls.INCOMING_TYPE
                CallTypeFilter.BLOCKED -> call.type == CallLog.Calls.BLOCKED_TYPE
                CallTypeFilter.AUTO_BLOCKED -> call.type == 99
            }
            val simMatch = simFilter == "ALL" || simFilter == simLabel(call.simSlotIndex)
            queryMatch && typeMatch && simMatch
        }
    }
    val sections = remember(shown) { sectionRecentCalls(shown) }
    val selectionMode = selectedCallIds.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(selectionMode) {
            Surface(color = Color(0xFF241B2D), tonalElevation = 4.dp.scaled()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp.scaled(), vertical = 8.dp.scaled()), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedCallIds = emptySet() }) { Icon(Icons.Outlined.Close, "Exit selection", tint = Color.White) }
                    Text("${selectedCallIds.size} selected", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), fontSize = 16.sp.scaled())
                    TextButton(onClick = { selectedCallIds = shown.map { it.id }.toSet() }) { Text("Select all", fontSize = 14.sp.scaled()) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete selected", tint = Color(0xFFFF7B78)) }
                }
            }
        }
        when {
            loadFailed -> EmptyState(Icons.Outlined.Warning, "Call log permission is required")
            sections.isEmpty() -> EmptyState(Icons.Outlined.History, "No recent calls")
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp.scaled(), bottom = 12.dp.scaled())) {
                sections.forEach { section ->
                    item(key = "header-${section.label}") {
                        Text(section.label, color = Color(0xFFAAA5B0), fontSize = 15.sp.scaled(), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 44.dp.scaled(), top = 14.dp.scaled(), bottom = 8.dp.scaled()))
                    }
                    itemsIndexed(section.groups, key = { _, g -> "${section.label}-${g.latest.id}" }) { index, group ->
                        val r = 24.dp.scaled(appearance.uiScale)
                        val shape = when {
                            section.groups.size == 1 -> RoundedCornerShape(r)
                            index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
                            index == section.groups.lastIndex -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
                            else -> androidx.compose.ui.graphics.RectangleShape
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp.scaled(appearance.uiScale)),
                            shape = shape,
                            color = Color(0xFF17171B)
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp.scaled())) {
                                val callId = group.latest.id
                                SwipeRecentGroupRow(
                                    group = group,
                                    selected = callId in selectedCallIds,
                                    selectionMode = selectionMode,
                                    onToggleSelection = { selectedCallIds = if (callId in selectedCallIds) selectedCallIds - callId else selectedCallIds + callId },
                                    onCall = onCall,
                                    onOpenDetails = onOpenDetails
                                )
                                if (index != section.groups.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 46.dp.scaled()), color = Color(0xFF37343B))
                                } else {
                                    Spacer(Modifier.height(3.dp.scaled()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete call history?") }, text = { Text("Delete history for ${selectedCallIds.size} selected items?") },
            confirmButton = { TextButton(onClick = { val numbers = calls.filter { it.id in selectedCallIds }.map { it.number }.distinct(); confirmDelete = false; scope.launch { val deleted = withContext(Dispatchers.IO) { numbers.sumOf { deleteCallHistory(context, it) } }; selectedCallIds = emptySet(); refreshCalls(); Toast.makeText(context, "$deleted deleted", Toast.LENGTH_SHORT).show() } }) { Text("Delete", color = Color(0xFFD73333)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    }
    if (filterDialog) {
        RecentsFilterDialog(simFilter = simFilter, callTypeFilter = callTypeFilter, onDismiss = { filterDialog = false }, onApply = { sim, type -> simFilter = sim; callTypeFilter = type; filterDialog = false })
    }
}

@Composable
private fun MissedCallPopupCard(call: RecentCall, count: Int, onDismiss: () -> Unit, onCallBack: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) 0.97f else 1f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 540f), label = "missedPopupScale")
    val displayName = call.name?.trim()?.takeIf { it.isNotEmpty() } ?: call.number.ifBlank { "Unknown number" }
    val headline = if (count > 1) "$count missed calls from $displayName" else "1 missed call from $displayName"
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp.scaled()), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible = true, enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(animationSpec = tween(400)), exit = fadeOut()) {
            Surface(
                modifier = Modifier.widthIn(min = 196.dp.scaled(), max = 228.dp.scaled()).fillMaxWidth(0.56f).graphicsLayer { scaleX = scale; scaleY = scale }.clickable(onClick = onDismiss),
                shape = RoundedCornerShape(12.dp.scaled()), color = Color(0xFFF6F4FB).copy(alpha = 0.98f), shadowElevation = 6.dp.scaled()
            ) {
                Box(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp.scaled(), end = 1.dp.scaled()).size(16.dp.scaled())) {
                        Icon(Icons.Outlined.Close, "Dismiss", tint = Color(0xFF4B4556), modifier = Modifier.size(11.dp.scaled()))
                    }
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 14.dp.scaled(), end = 14.dp.scaled(), top = 12.dp.scaled(), bottom = 10.dp.scaled()), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = headline, color = Color(0xFF17141D), fontSize = 12.sp.scaled(), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp.scaled()))
                        Surface(modifier = Modifier.clickable(interactionSource = interaction, indication = null, onClick = onCallBack), shape = RoundedCornerShape(10.dp.scaled()), color = Color(0xFFECE8F7)) {
                            Text("Call back", modifier = Modifier.padding(horizontal = 14.dp.scaled(), vertical = 6.dp.scaled()), color = Color(0xFF251E31), fontSize = 13.sp.scaled(), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

private fun sectionRecentCalls(calls: List<RecentCall>): List<RecentSection> {
    val grouped = groupRecentCalls(calls)
    return grouped.groupBy { dayKey(it.latest.date) }.toList().sortedByDescending { it.first }.map { (_, groups) -> RecentSection(dayLabel(groups.first().latest.date), groups) }
}

private fun groupRecentCalls(calls: List<RecentCall>): List<RecentCallGroup> {
    if (calls.isEmpty()) return emptyList()
    val result = mutableListOf<RecentCallGroup>()
    var current = calls.first()
    var count = 1
    for (index in 1 until calls.size) {
        val next = calls[index]
        if (normalizePhone(current.number) == normalizePhone(next.number) && current.type == next.type && dayKey(current.date) == dayKey(next.date)) count++
        else { result += RecentCallGroup(current, count); current = next; count = 1 }
    }
    result += RecentCallGroup(current, count)
    return result
}

private fun normalizePhone(value: String): String = value.filter(Char::isDigit).takeLast(10)
private fun dayKey(time: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(time))
private fun dayLabel(time: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = time }
    if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) return "Yesterday"
    return SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(time))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeRecentGroupRow(group: RecentCallGroup, selected: Boolean, selectionMode: Boolean, onToggleSelection: () -> Unit, onCall: (String) -> Unit, onOpenDetails: (String) -> Unit) {
    val context = LocalContext.current
    val call = group.latest
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var thresholdFeedbackSent by remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(targetValue = dragOffset, animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f), label = "recentSwipe")
    val haptics = LocalHapticFeedback.current
    val actionThreshold = 300f
    val maxDrag = 320f
    val progress = (abs(animatedOffset) / actionThreshold).coerceIn(0f, 1f)
    val bounceScale by animateFloatAsState(targetValue = if (abs(dragOffset) >= actionThreshold) 1.2f else 1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f), label = "iconBounce")
    val displayName = remember(call.number, call.name) { call.name?.trim()?.takeIf { it.isNotEmpty() } ?: call.number.ifBlank { "Unknown number" } }
    val simIndex = call.simSlotIndex + 1
    val appearance = LocalAppearance.current
    val timeStr = remember(call.date) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(call.date)) }
    
    val directionIcon = remember(call.type) {
        when (call.type) {
            CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Outlined.CallReceived
            CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Outlined.CallMade
            CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Outlined.CallMissed
            CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Outlined.CallMissedOutgoing
            CallLog.Calls.BLOCKED_TYPE -> Icons.Outlined.Block
            else -> Icons.Outlined.Call
        }
    }
    
    val directionTint = remember(call.type) {
        when (call.type) {
            CallLog.Calls.OUTGOING_TYPE, CallLog.Calls.INCOMING_TYPE -> Color(0xFF24C98A)
            CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> Color(0xFFF06B68)
            else -> Color(0xFFAAA5B0)
        }
    }
    val animatedBaseColor by animateColorAsState(targetValue = when { dragOffset > 15f -> Color(0xFF0D2B1D); dragOffset < -15f -> Color(0xFF0D1E42); selected -> Color(0xFF332842); else -> Color(0xFF17171B) }, label = "rowBaseColor")
    val activeContentColor by animateColorAsState(targetValue = when { dragOffset > 10f -> Color(0xFF24C98A); dragOffset < -10f -> Color(0xFF2979FF); isPressed -> Color(0xFFAAA5B0); else -> Color.White }, label = "swipeContentColor")

    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp.scaled()).clip(RoundedCornerShape(16.dp.scaled())), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.matchParentSize().background(Color(0xFF0F0F12))) {
            if (dragOffset > 0f) {
                Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(Color(0xFF0D5A32), Color(0xFF16A663), Color(0xFF24C98A)))))
            } else if (dragOffset < 0f) {
                Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(Color(0xFF339AF0), Color(0xFF2979FF), Color(0xFF1C3AA9)))))
            }
        }
        if (animatedOffset > 0f) {
            Row(modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp.scaled()).graphicsLayer { alpha = progress; scaleX = progress * bounceScale; scaleY = progress * bounceScale }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(26.dp.scaled()))
                Spacer(Modifier.width(12.dp.scaled()))
                Text("Call", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp.scaled())
            }
        } else if (animatedOffset < 0f) {
            Row(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp.scaled()).graphicsLayer { alpha = progress; scaleX = progress * bounceScale; scaleY = progress * bounceScale }, verticalAlignment = Alignment.CenterVertically) {
                Text("Message", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp.scaled())
                Spacer(Modifier.width(12.dp.scaled()))
                Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = Color.White, modifier = Modifier.size(26.dp.scaled()))
            }
        }
        val scaledShadow = 20f * appearance.uiScale
        val scaledCorner = 16.dp.scaled()
        
        // Optimized Row with direct translation to avoid parent recomposition
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { 
                    translationX = animatedOffset
                    shadowElevation = if (animatedOffset != 0f) scaledShadow else 0f
                    clip = true
                    shape = RoundedCornerShape(scaledCorner)
                }
                .background(animatedBaseColor)
                .combinedClickable(
                    interactionSource = interactionSource, 
                    indication = null, 
                    onClick = { if (selectionMode) onToggleSelection() else onOpenDetails(call.number) }, 
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onToggleSelection() }
                )
                .pointerInput(call.number) {
                    detectHorizontalDragGestures(
                        onDragStart = { thresholdFeedbackSent = false },
                        onHorizontalDrag = { change, dragAmount -> 
                            if (!selectionMode) { 
                                change.consume()
                                dragOffset = (dragOffset + dragAmount).coerceIn(-maxDrag, maxDrag)
                                val crossed = abs(dragOffset) >= actionThreshold
                                if (crossed && !thresholdFeedbackSent) { 
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    thresholdFeedbackSent = true 
                                } else if (!crossed) {
                                    thresholdFeedbackSent = false
                                }
                            } 
                        },
                        onDragEnd = { 
                            if (!selectionMode) { 
                                when { 
                                    dragOffset >= actionThreshold -> onCall(call.number)
                                    dragOffset <= -actionThreshold -> sendSms(context, call.number) 
                                } 
                            }
                            dragOffset = 0f
                            thresholdFeedbackSent = false 
                        },
                        onDragCancel = { 
                            dragOffset = 0f
                            thresholdFeedbackSent = false 
                        }
                    )
                }
                .padding(horizontal = 4.dp.scaled(), vertical = 10.dp.scaled()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(selectionMode) { Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (selected) Color(0xFF8B6DFF) else Color(0xFFAAA5B0), modifier = Modifier.padding(end = 8.dp.scaled()).size(24.dp.scaled())) }
            Box(contentAlignment = Alignment.BottomEnd) { ContactAvatar(displayName, call.photoUri); Surface(modifier = Modifier.size(19.dp.scaled()), shape = CircleShape, color = Color(0xFF17171B)) { Icon(directionIcon, null, tint = directionTint, modifier = Modifier.padding(2.dp.scaled())) } }
            Spacer(Modifier.width(13.dp.scaled()))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayName, color = activeContentColor, fontSize = 17.sp.scaled(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (group.count > 1) { Spacer(Modifier.width(4.dp.scaled())); Text("(${group.count})", color = activeContentColor.copy(alpha = 0.85f), fontSize = 15.sp.scaled()) }
                    Spacer(Modifier.width(6.dp.scaled())); CompactSimBadge(index = simIndex); Spacer(Modifier.width(4.dp.scaled()))

                    IconButton(onClick = { val contactId = findContactIdByNumber(context, call.number); if (contactId != null) openContact(context, contactId) else onOpenDetails(call.number) }, modifier = Modifier.size(32.dp.scaled())) { Icon(Icons.Outlined.Info, "Details", tint = Color(0xFFD8D2DF), modifier = Modifier.size(16.dp.scaled())) }
                }
                Spacer(Modifier.height(3.dp.scaled()))
                val durationStr = if (call.durationSeconds > 0) " • ${formatDuration(call.durationSeconds)}" else ""
                Text(timeStr + durationStr, color = activeContentColor.copy(alpha = 0.65f), fontSize = 12.sp.scaled(), maxLines = 1)
                if (group.latest.durationSeconds > 0) {
                    Spacer(Modifier.height(6.dp.scaled()))
                    Surface(shape = RoundedCornerShape(6.dp.scaled()), color = Color(0xFF24C98A).copy(alpha = 0.12f), border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF24C98A).copy(alpha = 0.4f))) {
                        Row(Modifier.padding(horizontal = 6.dp.scaled(), vertical = 2.dp.scaled()), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = Color(0xFF24C98A), modifier = Modifier.size(10.dp.scaled()))
                            Spacer(Modifier.width(4.dp.scaled())); Text("AI SUMMARY AVAILABLE", color = Color(0xFF24C98A), fontSize = 9.sp.scaled(), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeContactRow(contact: PhoneContact, favourite: Boolean, onCall: (String) -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current; val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }; val isPressed by interactionSource.collectIsPressedAsState()
    var offset by remember(contact.id, contact.number) { mutableFloatStateOf(0f) }; var feedbackSent by remember { mutableStateOf(false) }
    val animated by animateFloatAsState(targetValue = offset, animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f), label = "contactSwipe")
    val threshold = 300f; val progress = (abs(animated) / threshold).coerceIn(0f, 1f)
    val maxDrag = 320f
    val bounceScale by animateFloatAsState(targetValue = if (abs(offset) >= threshold) 1.2f else 1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f), label = "contactBounce")
    val animatedBaseColor by animateColorAsState(targetValue = when { offset > 15f -> Color(0xFF0D2B1D); offset < -15f -> Color(0xFF0D1E42); else -> Color(0xFF17171B) }, label = "contactRowBaseColor")
    val activeContentColor by animateColorAsState(targetValue = when { offset > 10f -> Color(0xFF24C98A); offset < -10f -> Color(0xFF2979FF); isPressed -> Color(0xFFAAA5B0); else -> Color.White }, label = "contactSwipeColor")
    val appearance = LocalAppearance.current
    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp.scaled()).clip(RoundedCornerShape(16.dp.scaled()))) {
        Box(modifier = Modifier.matchParentSize().background(Color(0xFF0F0F12))) {
            if (offset > 0f) {
                Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(Color(0xFF0D5A32), Color(0xFF16A663), Color(0xFF24C98A)))))
            } else if (offset < 0f) {
                Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(Color(0xFF339AF0), Color(0xFF2979FF), Color(0xFF1C3AA9)))))
            }
        }
        if (animated > 0f) {
            Row(modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp.scaled()).graphicsLayer { alpha = progress; scaleX = progress * bounceScale; scaleY = progress * bounceScale }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(26.dp.scaled())); Spacer(Modifier.width(12.dp.scaled())); Text("Call", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp.scaled())
            }
        } else if (animated < 0f) {
            Row(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp.scaled()).graphicsLayer { alpha = progress; scaleX = progress * bounceScale; scaleY = progress * bounceScale }, verticalAlignment = Alignment.CenterVertically) {
                Text("Message", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp.scaled()); Spacer(Modifier.width(12.dp.scaled())); Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = Color.White, modifier = Modifier.size(26.dp.scaled()))
            }
        }
        val scaledShadow = 20f * appearance.uiScale
        val scaledCorner = 16.dp.scaled()
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { 
                    translationX = animated
                    shadowElevation = if (animated != 0f) scaledShadow else 0f
                    clip = true
                    shape = RoundedCornerShape(scaledCorner)
                }
                .background(animatedBaseColor)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
                .pointerInput(contact.id, contact.number) {
                    detectHorizontalDragGestures(
                        onDragStart = { feedbackSent = false },
                        onHorizontalDrag = { change, dragAmount -> 
                            change.consume()
                            offset = (offset + dragAmount).coerceIn(-maxDrag, maxDrag)
                            val crossed = abs(offset) >= threshold
                            if (crossed && !feedbackSent) { 
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                feedbackSent = true 
                            } else if (!crossed) {
                                feedbackSent = false
                            }
                        },
                        onDragEnd = { 
                            when { 
                                offset >= threshold -> onCall(contact.number)
                                offset <= -threshold -> sendSms(context, contact.number) 
                            }
                            offset = 0f
                            feedbackSent = false 
                        },
                        onDragCancel = { 
                            offset = 0f
                            feedbackSent = false 
                        }
                    )
                }
                .padding(horizontal = 16.dp.scaled(), vertical = 11.dp.scaled()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(contact.name, contact.photoUri)
            Spacer(Modifier.width(14.dp.scaled()))
            Text(contact.name, color = activeContentColor, fontSize = 18.sp.scaled(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (favourite) Icon(Icons.Outlined.Star, "Favourite", tint = Color(0xFFFFCC65), modifier = Modifier.size(16.dp.scaled()))
        }
    }
}

@Composable
fun CompactSimBadge(index: Int) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.SimCard,
            null,
            tint = if (index == 2) Color(0xFF7256A0) else Color(0xFF4B66B5),
            modifier = Modifier.size(16.dp.scaled())
        )
        Text(
            text = "$index",
            color = Color.White,
            fontSize = 8.sp.scaled(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 1.dp.scaled())
        )
    }
}

@Composable
private fun RecentsFilterDialog(simFilter: String, callTypeFilter: CallTypeFilter, onDismiss: () -> Unit, onApply: (String, CallTypeFilter) -> Unit) {
    var localSim by remember { mutableStateOf(simFilter) }; var localType by remember { mutableStateOf(callTypeFilter) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Filter calls") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp.scaled())) {
                Text("SIM", style = MaterialTheme.typography.labelLarge)
                listOf("ALL" to "All SIMs", "SIM 1" to "SIM 1", "SIM 2" to "SIM 2").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { localSim = value }.padding(vertical = 8.dp.scaled()), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (localSim == value) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (localSim == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp.scaled())); Text(label, fontSize = 16.sp.scaled())
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp.scaled()))
                Text("Call type", style = MaterialTheme.typography.labelLarge)
                listOf(
                    CallTypeFilter.ALL to "All calls", 
                    CallTypeFilter.MISSED to "Missed calls", 
                    CallTypeFilter.REJECTED to "Rejected calls", 
                    CallTypeFilter.OUTGOING to "Outgoing calls", 
                    CallTypeFilter.INCOMING to "Incoming calls", 
                    CallTypeFilter.BLOCKED to "Blocked calls",
                    CallTypeFilter.AUTO_BLOCKED to "Auto Blocked"
                ).forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { localType = value }.padding(vertical = 8.dp.scaled()), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (localType == value) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (localType == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp.scaled())); Text(label, fontSize = 16.sp.scaled())
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { onApply(localSim, localType) }) { Text("OK") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ContactsScreen(query: String, contactsRevision: Int, onCall: (String) -> Unit) {
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    var contacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }; var loadFailed by remember { mutableStateOf(false) }; var favouritesRevision by remember { mutableIntStateOf(0) }; var duplicateDialog by remember { mutableStateOf(false) }; var duplicateStatus by remember { mutableStateOf<String?>(null) }
    val contactScope = rememberCoroutineScope(); val favouritePrefs = remember { context.getSharedPreferences("favourite_numbers", Context.MODE_PRIVATE) }
    DisposableEffect(favouritePrefs) { val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "numbers") favouritesRevision++ }; favouritePrefs.registerOnSharedPreferenceChangeListener(listener); onDispose { favouritePrefs.unregisterOnSharedPreferenceChangeListener(listener) } }
    LaunchedEffect(contactsRevision) {
        contactInfoCache.clear()
        runCatching { loadContacts(context) }.onSuccess { contacts = it; loadFailed = false }.onFailure { loadFailed = true }
    }
    val favouriteNumbers = remember(favouritesRevision, contacts) { favouriteNumberSet(context) }
    val duplicateGroups = contacts.groupBy { normalizePhone(it.number) }.filter { (number, group) -> number.isNotBlank() && group.map { it.id }.distinct().size > 1 }
    val shown = contacts.filter { query.isBlank() || it.name.contains(query, true) || it.number.contains(query, true) }.distinctBy { normalizePhone(it.number).ifBlank { "${it.id}:${it.number}" } }
    val favourites = remember(shown, favouriteNumbers) { shown.filter { normalizePhone(it.number) in favouriteNumbers } }
    val grouped = shown.groupBy { contactGroup(it.name) }.toSortedMap(compareBy<String> { groupSortKey(it) }.thenBy { it })
    when {
        loadFailed -> EmptyState(Icons.Outlined.Warning, "Contacts permission is required")
        shown.isEmpty() -> EmptyState(Icons.Outlined.Contacts, "No contacts found")
        else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp.scaled()), contentPadding = PaddingValues(bottom = 12.dp.scaled())) {
            if (query.isBlank()) {
                item(key = "favourites-title") {
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp.scaled(), end = 8.dp.scaled(), top = 10.dp.scaled(), bottom = 8.dp.scaled()), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Star, null, tint = Color(0xFFB7B1BE), modifier = Modifier.size(22.dp.scaled()))
                        Spacer(Modifier.width(8.dp.scaled())); Text("Favourites", color = Color(0xFFB7B1BE), fontSize = 18.sp.scaled(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (duplicateGroups.isNotEmpty()) { BadgedBox(badge = { Badge { Text(duplicateGroups.size.coerceAtMost(99).toString()) } }) { IconButton(onClick = { duplicateDialog = true }, modifier = Modifier.size(36.dp.scaled())) { Icon(Icons.Outlined.ContentCopy, null, tint = Color(0xFFB997FF), modifier = Modifier.size(16.dp.scaled())) } } }
                    }
                }
                item(key = "favourites-row") {
                    if (favourites.isEmpty()) { Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp.scaled(), vertical = 4.dp.scaled()), shape = RoundedCornerShape(16.dp.scaled()), color = Color(0xFF17171B)) { Text("Open history and tap Favourite to add here.", color = Color(0xFFAFA8B7), modifier = Modifier.padding(18.dp.scaled()), fontSize = 14.sp.scaled()) } }
                    else {
                        val compact = favourites.size >= 5
                        val cardWidth = if (compact) 90.dp.scaled() else 110.dp.scaled()
                        val cardHeight = if (compact) 102.dp.scaled() else 128.dp.scaled()
                        val avatarSize = if (compact) 48.dp.scaled() else 62.dp.scaled()
                        
                        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp.scaled()), horizontalArrangement = Arrangement.spacedBy(10.dp.scaled())) {
                            items(favourites, key = { "fav-${it.id}-${normalizePhone(it.number)}" }) { contact ->
                                Surface(modifier = Modifier.width(cardWidth).height(cardHeight).clickable { onCall(contact.number) }, shape = RoundedCornerShape(16.dp.scaled()), color = favouriteCardColor(contact.name)) {
                                    Column(Modifier.fillMaxSize().padding(8.dp.scaled()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                                        Surface(Modifier.size(avatarSize), CircleShape, color = Color.White.copy(alpha = .12f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .45f))) {
                                            Box(contentAlignment = Alignment.Center) { Text(contact.name.trim().firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = if (compact) 22.sp.scaled() else 28.sp.scaled()) }
                                        }
                                        Text(contact.name, color = Color.White, fontSize = if (compact) 12.sp.scaled() else 14.sp.scaled(), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            grouped.forEach { (groupName, groupContacts) ->
                item(key = "header-$groupName") { Text(groupName, color = Color(0xFFA9A3AF), fontSize = 14.sp.scaled(), modifier = Modifier.padding(start = 18.dp.scaled(), top = 14.dp.scaled(), bottom = 7.dp.scaled())) }
                itemsIndexed(groupContacts, key = { _, c -> "contact-${c.id}-${normalizePhone(c.number)}" }) { index, contact ->
                    val r = 20.dp.scaled(appearance.uiScale)
                    val shape = when {
                        groupContacts.size == 1 -> RoundedCornerShape(r)
                        index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
                        index == groupContacts.lastIndex -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
                        else -> androidx.compose.ui.graphics.RectangleShape
                    }
                    Surface(
                        shape = shape,
                        color = Color(0xFF17171B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            SwipeContactRow(
                                contact = contact,
                                favourite = normalizePhone(contact.number) in favouriteNumbers,
                                onCall = onCall,
                                onOpen = { openContact(context, contact.id) }
                            )
                            if (index != groupContacts.lastIndex) {
                                HorizontalDivider(Modifier.padding(start = 78.dp.scaled()), color = Color(0xFF353239))
                            }
                        }
                    }
                }
            }
        }
    }
    if (duplicateDialog) {
        AlertDialog(onDismissRequest = { duplicateDialog = false }, title = { Text("Duplicate contacts") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp.scaled())) { Text("${duplicateGroups.size} groups found.", fontSize = 16.sp.scaled()); duplicateStatus?.let { Text(it, color = Color(0xFFB997FF), fontSize = 13.sp.scaled()) } } },
            confirmButton = { TextButton(onClick = { contactScope.launch { duplicateStatus = "Merging…"; val result = runCatching { withContext(Dispatchers.IO) { mergeDuplicateContactGroups(context, duplicateGroups.values.toList()) } }; duplicateStatus = result.fold(onSuccess = { "$it merged" }, onFailure = { "Failed: ${it.message}" }) } }) { Text("Merge", fontSize = 16.sp.scaled()) } },
            dismissButton = { Row { TextButton(onClick = { duplicateDialog = false; launchContactDuplicateManager(context) }) { Text("Open Contacts", fontSize = 16.sp.scaled()) }; TextButton(onClick = { duplicateDialog = false }) { Text("Close", fontSize = 16.sp.scaled()) } } })
    }
}

private fun favouriteCardColor(name: String): Color {
    val palette = listOf(Color(0xFFD77EB8), Color(0xFFFFAE71), Color(0xFF75C5B9), Color(0xFFA680E4), Color(0xFF6F9ED6))
    return palette[abs(name.hashCode()) % palette.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentNumberDetailsScreen(number: String, onBack: () -> Unit, onCall: (String) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var calls by remember(number) { mutableStateOf<List<RecentCall>>(emptyList()) }
    var playingFile by remember { mutableStateOf<String?>(null) }; var playbackPaused by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }; val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var showDeleteConfirm by remember { mutableStateOf(false) }; var showBlockDialog by remember { mutableStateOf(false) }
    var favourite by remember(number) { mutableStateOf(isFavouriteNumber(context, number)) }
    val ruleRepository = remember(context) { RuleRepository(context.applicationContext) }
    val allRules by ruleRepository.observeAll().collectAsState(initial = emptyList())
    val numberRules = remember(allRules, number) { allRules.filter { it.matchType == "SPECIFIC_NUMBER" && normalizePhone(it.matchValue) == normalizePhone(number) && it.action == "BLOCK" && it.enabled } }
    val blockedOnSim1 = numberRules.any { it.simSlotIndex == 0 }; val blockedOnSim2 = numberRules.any { it.simSlotIndex == 1 }
    DisposableEffect(Unit) { onDispose { RecordingPlayback.stopAndRelease(player); player = null; resetRecordingAudioRoute(audioManager) } }
    
    var recordings by remember { mutableStateOf<List<File>>(emptyList()) }
    var aiSummary by remember { mutableStateOf<String?>(null) }
    var loadingSummary by remember { mutableStateOf(false) }

    LaunchedEffect(number) { 
        withContext(Dispatchers.IO) {
            calls = runCatching { loadRecentCalls(context).filter { normalizePhone(it.number) == normalizePhone(number) } }.getOrDefault(emptyList()) 
            val foundRecs = SamsungRecordingHelper.findRecordings(context, number)
            recordings = foundRecs
            
            if (foundRecs.isNotEmpty()) {
                loadingSummary = true
                aiSummary = com.example.callruleblocker.data.GeminiManager.generateSummary(foundRecs.first())
                loadingSummary = false
            } else {
                aiSummary = null
            }
        }
    }
    
    val recordingByCallDate = remember(recordings, calls) {
        val unused = recordings.toMutableList(); buildMap { calls.forEach { call -> val match = unused.minByOrNull { abs(it.lastModified() - call.date) }?.takeIf { abs(it.lastModified() - call.date) <= 1800000L }; if (match != null) { put(call.date, match); unused.remove(match) } } }
    }
    val title = calls.firstOrNull()?.name?.takeIf(String::isNotBlank) ?: number
    val groupedCalls = remember(calls) { calls.groupBy { dayKey(it.date) }.toList().sortedByDescending { it.first } }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
                title = { Column { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 20.sp.scaled()); if (title != number) Text(number, fontSize = 12.sp.scaled(), color = Color(0xFFB8B2BE)) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                actions = { val contactId = remember(number) { findContactIdByNumber(context, number) }; if (contactId != null) IconButton(onClick = { openContact(context, contactId) }) { Icon(Icons.Outlined.Person, null) }; IconButton(onClick = { onCall(number) }) { Icon(Icons.Outlined.Call, null) } }
            )
        },
        bottomBar = {
            Surface(color = Color.Black, shadowElevation = 12.dp.scaled()) {
                Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp.scaled(), vertical = 12.dp.scaled()), shape = RoundedCornerShape(16.dp.scaled()), color = Color(0xFF232326)) {
                    Row(Modifier.fillMaxWidth().padding(6.dp.scaled()), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DetailAction(Icons.Outlined.Star, if (favourite) "Unfav" else "Fav", favourite) { val target = !favourite; setFavouriteNumber(context, number, target); favourite = isFavouriteNumber(context, number) }
                        DetailAction(Icons.Outlined.Share, "Share") { shareNumber(context, title, number) }
                        DetailAction(Icons.Outlined.Block, if (blockedOnSim1 || blockedOnSim2) "Manage" else "Block") { showBlockDialog = true }
                        DetailAction(Icons.Outlined.Delete, "Delete") { showDeleteConfirm = true }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().background(Color.Black).padding(padding), contentPadding = PaddingValues(horizontal = 12.dp.scaled(), vertical = 7.dp.scaled()), verticalArrangement = Arrangement.spacedBy(9.dp.scaled())) {
            item { Surface(shape = RoundedCornerShape(16.dp.scaled()), color = Color(0xFF18181C), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 15.dp.scaled(), vertical = 12.dp.scaled())) { Text("Call history", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp.scaled()); Text("${calls.size} calls", color = Color(0xFFB9B3BF), fontSize = 14.sp.scaled()) } } }
            if (recordings.isNotEmpty()) { 
                item { 
                    Surface(shape = RoundedCornerShape(16.dp.scaled()), color = Color(0xFF1B1B21), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24C98A).copy(alpha = 0.3f))) { 
                        Column(Modifier.padding(18.dp.scaled())) { 
                            Row(verticalAlignment = Alignment.CenterVertically) { 
                                Icon(Icons.Outlined.AutoAwesome, null, tint = Color(0xFF24C98A), modifier = Modifier.size(16.dp.scaled())); 
                                Spacer(Modifier.width(10.dp.scaled())); 
                                Text("AI Summary", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp.scaled()) 
                            }; 
                            Spacer(Modifier.height(14.dp.scaled())); 
                            if (loadingSummary) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF24C98A), trackColor = Color.White.copy(0.1f))
                            } else {
                                Text(aiSummary ?: "Generating summary...", color = Color.White, fontSize = 14.sp.scaled())
                            }
                            Spacer(Modifier.height(10.dp.scaled())); 
                            Text("Secure Summary • Shyna AI (Gemini)", color = Color(0xFF7A7485), fontSize = 10.sp.scaled()) 
                        } 
                    } 
                } 
            }
            groupedCalls.forEach { (_, dayCalls) ->
                item { Text(detailDayLabel(dayCalls.first().date), color = Color(0xFFB7B1BE), fontSize = 14.sp.scaled(), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp.scaled(), top = 3.dp.scaled())) }
                item { 
                    Surface(shape = RoundedCornerShape(17.dp.scaled()), color = Color(0xFF18181C), modifier = Modifier.fillMaxWidth()) { 
                        Column(Modifier.padding(horizontal = 18.dp.scaled(), vertical = 5.dp.scaled())) { 
                            dayCalls.forEachIndexed { i, c -> 
                                val recFile = recordingByCallDate[c.date]
                                CallHistoryDetailRow(
                                    call = c, 
                                    recording = recFile, 
                                    isPlaying = playingFile == recFile?.absolutePath && !playbackPaused, 
                                    isPaused = playingFile == recFile?.absolutePath && playbackPaused, 
                                    onPlayPause = { file ->
                                        if (playingFile == file.absolutePath) {
                                            if (playbackPaused) {
                                                if (RecordingPlayback.resume(player)) playbackPaused = false
                                                else {
                                                    RecordingPlayback.stopAndRelease(player)
                                                    player = null
                                                    playingFile = null
                                                    playbackPaused = false
                                                    Toast.makeText(context, "Recording could not resume", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                if (RecordingPlayback.pause(player)) playbackPaused = true
                                            }
                                        } else {
                                            RecordingPlayback.stopAndRelease(player)
                                            player = null
                                            RecordingPlayback.openAndPlay(
                                                file = file,
                                                onCompletion = {
                                                    player = null
                                                    playingFile = null
                                                    playbackPaused = false
                                                },
                                                onError = { message ->
                                                    player = null
                                                    playingFile = null
                                                    playbackPaused = false
                                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                                }
                                            ).onSuccess { opened ->
                                                player = opened
                                                playingFile = file.absolutePath
                                                playbackPaused = false
                                            }.onFailure { error ->
                                                playingFile = null
                                                playbackPaused = false
                                                Toast.makeText(context, error.message ?: "Recording could not be played", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }, 
                                    onStop = { 
                                        RecordingPlayback.stopAndRelease(player)
                                        player = null
                                        playingFile = null
                                        playbackPaused = false
                                    }
                                )
                                if (i != dayCalls.lastIndex) HorizontalDivider(color = Color(0xFF3A373E)) 
                            } 
                        } 
                    } 
                }
            }
        }
    }
}

@Composable
private fun DetailAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(72.dp.scaled()).clickable(onClick = onClick).padding(vertical = 4.dp.scaled()), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (active) Color(0xFFFFCC65) else Color.White, modifier = Modifier.size(27.dp.scaled()))
        Spacer(Modifier.height(4.dp.scaled())); Text(label, color = Color(0xFFBDB7C2), fontSize = 11.sp.scaled(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BlockSimActionRow(label: String, blocked: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp.scaled()), color = if (blocked) Color(0xFF3A2028) else Color(0xFF242128)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp.scaled(), vertical = 12.dp.scaled()), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Block, null, tint = if (blocked) Color(0xFFFF8D94) else Color(0xFFCFB2FF), modifier = Modifier.size(24.dp.scaled()))
            Spacer(Modifier.width(12.dp.scaled())); Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 16.sp.scaled())
        }
    }
}

@Composable
private fun CallHistoryDetailRow(call: RecentCall, recording: File?, isPlaying: Boolean, isPaused: Boolean, onPlayPause: (File) -> Unit, onStop: () -> Unit) {
    val isVideo = (call.features and CallLog.Calls.FEATURES_VIDEO) != 0; val tint = if (isVideo) Color(0xFF8C51FF) else Color(0xFF21C982)
    val haptic = LocalHapticFeedback.current
    val timeStr = remember(call.date) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(call.date)) }
    Row(modifier = Modifier.fillMaxWidth().animateContentSize().clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); if (recording != null) onPlayPause(recording) }.padding(vertical = 10.dp.scaled()), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(timeStr, color = Color.White, fontSize = 17.sp.scaled())
                Spacer(Modifier.width(8.dp.scaled()))
                CompactSimBadge(index = call.simSlotIndex + 1)
            }
            Spacer(Modifier.height(5.dp.scaled())); Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (isVideo) Icons.Outlined.Videocam else Icons.Outlined.Call, null, tint = tint, modifier = Modifier.size(18.dp.scaled())); Spacer(Modifier.width(8.dp.scaled())); Text("${callTypeLabel(call.type)}, ${formatDuration(call.durationSeconds)}", color = tint, fontSize = 14.sp.scaled()) }
        }
        if (recording != null) { 
            Row(verticalAlignment = Alignment.CenterVertically) { 
                IconButton(onClick = { onPlayPause(recording) }) { 
                    Icon(if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, tint = Color(0xFF24C98A), modifier = Modifier.size(28.dp.scaled())) 
                }
                if (isPlaying || isPaused) {
                    IconButton(onClick = onStop) { 
                        Icon(Icons.Filled.Stop, null, tint = Color(0xFFFF8D94), modifier = Modifier.size(26.dp.scaled())) 
                    }
                } 
            } 
        }
    }
}

private fun routeRecordingAudio(audioManager: AudioManager, speaker: Boolean) {
    runCatching { audioManager.mode = AudioManager.MODE_IN_COMMUNICATION; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == (if (speaker) android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) }; if (device != null) audioManager.setCommunicationDevice(device) } else { @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = speaker } }
}
private fun resetRecordingAudioRoute(audioManager: AudioManager) {
    runCatching { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice() else { @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = false }; audioManager.mode = AudioManager.MODE_NORMAL }
}
private fun formatDuration(sec: Long): String = if (sec < 60) "$sec secs" else "${sec / 60} mins ${sec % 60} secs"
private fun detailDayLabel(time: Long): String {
    val key = dayKey(time); if (key == dayKey(System.currentTimeMillis())) return "Today"; if (key == dayKey(System.currentTimeMillis() - 86400000L)) return "Yesterday"
    return SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(Date(time))
}
private fun favouriteNumberSet(ctx: Context): Set<String> {
    val res = ctx.getSharedPreferences("favourite_numbers", Context.MODE_PRIVATE).getStringSet("numbers", emptySet()).orEmpty().toMutableSet()
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
        runCatching { ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), "${ContactsContract.CommonDataKinds.Phone.STARRED}=1", null, null)?.use { while (it.moveToNext()) normalizePhone(it.getString(0).orEmpty()).takeIf { n -> n.isNotBlank() }?.let(res::add) } }
    }
    return res
}
private fun isFavouriteNumber(ctx: Context, num: String): Boolean = normalizePhone(num) in favouriteNumberSet(ctx)
private fun setFavouriteNumber(ctx: Context, num: String, fav: Boolean): Boolean {
    val key = normalizePhone(num); if (key.isBlank()) return false; val local = ctx.getSharedPreferences("favourite_numbers", Context.MODE_PRIVATE).getStringSet("numbers", emptySet()).orEmpty().toMutableSet()
    if (fav) local.add(key) else local.remove(key); val saved = ctx.getSharedPreferences("favourite_numbers", Context.MODE_PRIVATE).edit().putStringSet("numbers", HashSet(local)).commit()
    val id = findContactIdByNumber(ctx, num); if (id != null && ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
        runCatching { val v = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (fav) 1 else 0) }; ctx.contentResolver.update(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString()), v, null, null) }
    }
    return saved
}
private fun shareNumber(ctx: Context, t: String, n: String) { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "$t\n$n") }, "Share")) }
private fun deleteCallHistory(ctx: Context, num: String): Int {
    val d = normalizePhone(num); if (d.isBlank()) return 0; var del = 0; val ids = mutableListOf<Long>(); val snaps = mutableListOf<TrashedCallEntry>()
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID, CallLog.Calls.FEATURES), null, null, null)?.use { c ->
            while (c.moveToNext()) { val sn = c.getString(1).orEmpty(); if (normalizePhone(sn) == d) { ids += c.getLong(0); snaps += TrashedCallEntry(sn, c.getString(2), c.getInt(3), c.getLong(4), c.getLong(5), c.getString(6), c.getInt(7)) } }
        }
    }
    if (ids.isNotEmpty() && ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
        ids.forEach { del += ctx.contentResolver.delete(Uri.withAppendedPath(CallLog.Calls.CONTENT_URI, it.toString()), null, null) }
    }
    if (del > 0) CallLogTrashStore(ctx.applicationContext).add(num, snaps.take(del))
    return del
}
private fun callTypeLabel(t: Int): String = when (t) { 
    CallLog.Calls.INCOMING_TYPE -> "Incoming"
    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
    CallLog.Calls.MISSED_TYPE -> "Missed"
    CallLog.Calls.REJECTED_TYPE -> "Rejected"
    CallLog.Calls.BLOCKED_TYPE -> "Blocked"
    99 -> "Auto Blocked"
    else -> "Call" 
}
private fun launchContactDuplicateManager(ctx: Context) { val intents = listOf(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI), Intent("com.samsung.android.contacts.action.MERGE_DUPLICATE_CONTACTS")); intents.any { i -> if (i.resolveActivity(ctx.packageManager) != null) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess } else false } }
private fun findContactIdByNumber(ctx: Context, num: String): Long? { 
    val n = normalizePhone(num); if (n.isBlank()) return null; 
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
    return runCatching { ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { while (it.moveToNext()) if (normalizePhone(it.getString(1).orEmpty()) == n) return@use it.getLong(0); null } }.getOrNull() 
}
private fun mergeDuplicateContactGroups(ctx: Context, groups: List<List<PhoneContact>>): Int {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED || 
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) return 0
    var mg = 0; groups.forEach { group -> val ids = group.map { c -> c.id }.distinct(); if (ids.size < 2) return@forEach; val rids = mutableListOf<Long>(); ctx.contentResolver.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID), ContactsContract.RawContacts.CONTACT_ID + " IN (" + ids.joinToString(",") { "?" } + ")", ids.map(Long::toString).toTypedArray(), null)?.use { c -> while (c.moveToNext()) rids += c.getLong(0) }; val base = rids.firstOrNull() ?: return@forEach; val ops = ArrayList<ContentProviderOperation>(); rids.drop(1).forEach { o -> ops += ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI).withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER).withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, base).withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, o).build() }; if (ops.isNotEmpty()) { ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops); mg++ } }
    return mg
}
private fun contactGroup(n: String): String { val f = n.trim().firstOrNull() ?: return "#"; return if (f.isLetter()) f.uppercase() else "#" }
private fun groupSortKey(g: String): Int = if (g.length == 1 && g[0] in 'A'..'Z') g[0].code else 10000
private fun Int?.orEmptyCode(): Int = this ?: 0
private val avatarCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(100)

@Composable
private fun ContactAvatar(text: String, photoUri: String? = null) {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf(photoUri?.let { avatarCache.get(it) }) }
    
    if (bitmap == null && !photoUri.isNullOrBlank()) {
        LaunchedEffect(photoUri) {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                        val b = BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        if (b != null) {
                            avatarCache.put(photoUri, b)
                            bitmap = b
                        }
                    }
                }
            }
        }
    }
    
    Surface(Modifier.size(48.dp.scaled()), CircleShape, color = Color(0xFF134A3A)) {
        if (bitmap != null) {
            Image(bitmap!!, text, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(text.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp.scaled())
            }
        }
    }
}
@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) { Column(Modifier.fillMaxWidth().padding(48.dp.scaled()), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, Modifier.size(52.dp.scaled()), tint = Color(0xFFA59DB0)); Spacer(Modifier.height(12.dp.scaled())); Text(text, color = Color(0xFFA59DB0), textAlign = TextAlign.Center, fontSize = 16.sp.scaled()) } }
private fun openNewContact(ctx: Context, n: String) { 
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(ctx, "Contacts permission required", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching { ctx.startActivity(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply { if (n.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, n) }) } 
}
private fun openContact(ctx: Context, id: Long) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString()))) } }
private val contactInfoCache = mutableMapOf<String, Pair<String?, String?>>()

private suspend fun loadRecentCalls(ctx: Context): List<RecentCall> = withContext(Dispatchers.IO) {
    val result = mutableListOf<RecentCall>()
    try {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return@withContext emptyList()
        
        val bs = BlockedCallStore(ctx)
        val rr = RuleRepository(ctx)
        val pbn = rr.blockedSpecificNumbers()
        
        val subs = if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try { ctx.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty() } catch (_: Exception) { emptyList() }
        } else emptyList()
        val tm = ctx.getSystemService(TelecomManager::class.java)
        val ta = try { tm?.callCapablePhoneAccounts.orEmpty() } catch (_: Exception) { emptyList() }

        if (contactInfoCache.isEmpty() && ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER, 
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, 
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )
            ctx.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                while (cursor.moveToNext()) {
                    val rawNum = cursor.getString(numIdx).orEmpty()
                    val num = normalizePhone(rawNum)
                    if (num.isNotBlank()) {
                        contactInfoCache[num] = cursor.getString(nameIdx) to cursor.getString(photoIdx)
                    }
                }
            }
        }

    val proj = arrayOf(
        CallLog.Calls._ID, 
        CallLog.Calls.NUMBER, 
        CallLog.Calls.CACHED_NAME, 
        CallLog.Calls.TYPE, 
        CallLog.Calls.DATE, 
        CallLog.Calls.DURATION, 
        CallLog.Calls.PHONE_ACCOUNT_ID, 
        CallLog.Calls.FEATURES
    )
    
    ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, proj, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
        val idIdx = c.getColumnIndexOrThrow(CallLog.Calls._ID)
        val numIdx = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val nameIdx = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val typeIdx = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateIdx = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
        val durIdx = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
        val aidIdx = c.getColumnIndexOrThrow(CallLog.Calls.PHONE_ACCOUNT_ID)
        val featIdx = c.getColumnIndexOrThrow(CallLog.Calls.FEATURES)

        while (c.moveToNext() && result.size < 400) {
            val rn = c.getString(numIdx).orEmpty()
            val cd = c.getLong(dateIdx)
            val aid = c.getString(aidIdx)
            val norm = normalizePhone(rn)
            
            val be = bs.find(rn, cd)
            val ci = contactInfoCache[norm]
            val isRuleBlocked = norm.isNotBlank() && norm in pbn

            // LOGIC: Blocked calls are only shown in "ALL" if they are now contacts.
            // Otherwise they go into the "AUTO_BLOCKED" filter only.
            
            val finalType = when {
                be != null -> 99 
                isRuleBlocked -> 99
                else -> c.getInt(typeIdx)
            }

            result += RecentCall(
                id = c.getLong(idIdx),
                number = rn,
                name = ci?.first ?: c.getString(nameIdx),
                type = finalType, // Always use finalType (which is 99 for blocked)
                date = cd,
                durationSeconds = c.getLong(durIdx),
                phoneAccountId = aid,
                features = c.getInt(featIdx),
                simSlotIndex = be?.simSlot ?: resolveSimSlotFaster(aid, subs, tm, ta),
                photoUri = ci?.second
            )
        }
    }

    // --- PHASE 2: MERGE SYSTEM LOG WITH LOCAL BLOCKED STORE ---
    val auditEntries = bs.getAll()
    auditEntries.forEach { entry ->
        val norm = normalizePhone(entry.number)
        val ci = contactInfoCache[norm]
        
        // If this exact blocked call isn't already in our result list (within 10s tolerance)
        val alreadyInList = result.any { normalizePhone(it.number) == norm && abs(it.date - entry.time) < 10000 }
        
        if (!alreadyInList) {
            result.add(RecentCall(
                id = -entry.time, // Negative ID for local-only entries
                number = entry.number,
                name = ci?.first,
                type = 99, // Always 99 for Auto-Blocked
                date = entry.time,
                durationSeconds = 0,
                phoneAccountId = null,
                simSlotIndex = entry.simSlot,
                photoUri = ci?.second
            ))
        }
    }

    result.sortByDescending { it.date }
    result.take(400)
    } catch (e: Exception) {
        android.util.Log.e("PhoneHome", "Recent calls merge failed", e)
    }
    result.sortByDescending { it.date }
    result.take(400)
}
private fun resolveSimSlotFaster(aid: String?, subs: List<android.telephony.SubscriptionInfo>, tm: TelecomManager?, ta: List<android.telecom.PhoneAccountHandle>): Int {
    if (aid.isNullOrBlank()) return 0; aid.toIntOrNull()?.let { v -> subs.firstOrNull { it.subscriptionId == v }?.let { return it.simSlotIndex.coerceAtLeast(0) } }; subs.firstOrNull { (!it.iccId.isNullOrBlank() && aid.contains(it.iccId, true)) || aid.contains(it.subscriptionId.toString()) }?.let { return it.simSlotIndex.coerceAtLeast(0) }
    try { ta.forEach { h -> if (h.id == aid) { val position = ta.indexOf(h); if (position >= 0) return position.coerceIn(0, 1) } } } catch (_: Exception) {}; return 0
}
@Composable
private fun FastContactsObserver(onChanged: () -> Unit) {
    val ctx = LocalContext.current; val curr by rememberUpdatedState(onChanged)
    DisposableEffect(ctx) { 
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@DisposableEffect onDispose {}
        }
        val h = Handler(Looper.getMainLooper()); val r = Runnable { curr() }; val o = object : ContentObserver(h) { override fun onChange(s: Boolean) { h.removeCallbacks(r); h.post(r); h.postDelayed(r, 450L) } }; 
        try {
            ctx.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, o)
        } catch (e: SecurityException) {
            Log.e("PhoneHome", "Failed to register contacts observer", e)
        }
        onDispose { 
            h.removeCallbacks(r)
            runCatching { ctx.contentResolver.unregisterContentObserver(o) }
        } 
    }
}
private suspend fun loadContacts(ctx: Context): List<PhoneContact> = withContext(Dispatchers.IO) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return@withContext emptyList()
    
    val accountTypes = mutableMapOf<Long, String?>()
    ctx.contentResolver.query(
        ContactsContract.RawContacts.CONTENT_URI, 
        arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE), 
        "${ContactsContract.RawContacts.DELETED}=0", 
        null, 
        null
    )?.use { c ->
        val idIdx = c.getColumnIndex(ContactsContract.RawContacts._ID)
        val typeIdx = c.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
        while (c.moveToNext()) {
            accountTypes[c.getLong(idIdx)] = c.getString(typeIdx)
        }
    }

    val res = mutableListOf<PhoneContact>()
    val seen = mutableSetOf<String>()
    val proj = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID, 
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, 
        ContactsContract.CommonDataKinds.Phone.NUMBER, 
        ContactsContract.CommonDataKinds.Phone.PHOTO_URI, 
        ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID
    )
    
    ctx.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI, 
        proj, 
        null, 
        null, 
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"
    )?.use { c ->
        val cidIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val photoIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
        val rawIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)

        while (c.moveToNext()) {
            val ph = c.getString(numIdx).orEmpty()
            val cid = c.getLong(cidIdx)
            if (ph.isNotBlank() && seen.add("$cid:$ph")) {
                res += PhoneContact(
                    id = cid, 
                    name = c.getString(nameIdx) ?: ph, 
                    number = ph, 
                    photoUri = c.getString(photoIdx), 
                    accountType = accountTypes[c.getLong(rawIdx)]
                )
            }
        }
    }
    res
}
private fun sendSms(ctx: Context, n: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$n"))) }.onFailure { Toast.makeText(ctx, "No SMS app", Toast.LENGTH_SHORT).show() } }
private fun dialNumberFontSize(l: Int) = when {
    l <= 12 -> 44.sp
    else -> 32.sp
}
private fun simLabel(idx: Int) = "SIM ${idx.coerceIn(0, 1) + 1}"
