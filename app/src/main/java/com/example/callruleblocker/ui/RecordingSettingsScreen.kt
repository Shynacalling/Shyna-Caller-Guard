package com.example.callruleblocker.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.provider.ContactsContract
import android.media.RingtoneManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.callruleblocker.ui.theme.*

private const val PREFS = "call_settings"
private const val RECORD_PREFS = "recording_settings"

val BRAND_THEMES = listOf(
    "Shyna Classic", "Shyna Fruit", "Shyna One", "Shyna Pure", "Shyna Oxygen", "Shyna Flow", "Shyna Dot", 
    "Shyna Moto", "Shyna Finn", "Shyna Alpha", "Shyna Zen", "Shyna Color", "Shyna Vivid", "Shyna Realm", 
    "Shyna Honor", "Shyna Harmony", "Shyna Red", "Shyna Blade", "Shyna Think", "Shyna Dream", "Shyna Hi", "Shyna Infinite"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSettingsScreen(
    onBack: () -> Unit,
    onOpenCsv: () -> Unit = {},
    onOpenFeatureHub: () -> Unit = {},
    onOpenRules: () -> Unit = {},
    onOpenRingtone: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenRecycleBin: () -> Unit = {},
    onOpenSupplementaryServices: () -> Unit = {},
    onThemeChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    var refresh by remember { mutableIntStateOf(0) }
    var buttonSoundEnabled by remember { mutableStateOf(UiFeedback.isSoundEnabled(context)) }
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val recordPrefs = remember { context.getSharedPreferences(RECORD_PREFS, Context.MODE_PRIVATE) }

    var callThemeDialog by remember { mutableStateOf(false) }
    var dialPadThemeDialog by remember { mutableStateOf(false) }
    var displayModeDialog by remember { mutableStateOf(false) }
    var callerInfoDialog by remember { mutableStateOf(false) }
    var recordingModeDialog by remember { mutableStateOf(false) }
    var exclusionsDialog by remember { mutableStateOf(false) }
    var recordingScopeDialog by remember { mutableStateOf(false) }
    var selectedRecordingDialog by remember { mutableStateOf(false) }
    var backgroundDialog by remember { mutableStateOf(false) }
    var securityDialog by remember { mutableStateOf(false) }
    var recordingMode by remember { mutableStateOf(recordPrefs.getString("recording_mode", "AUTO") ?: "OFF") }
    var recordingScope by remember { mutableStateOf(recordPrefs.getString("recording_scope", "ALL") ?: "ALL") }
    var backgroundMode by remember { mutableStateOf(prefs.getString("call_background", "AURORA") ?: "AURORA") }
    
    var showUpdateDialog by remember { mutableStateOf(false) }

    val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            recordPrefs.edit().putString("recording_mode", "AUTO").apply()
            recordingMode = "AUTO"
            refresh++
        } else {
            android.widget.Toast.makeText(context, "Recording requires microphone permission", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val customBackgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            prefs.edit()
                .putString("call_background", "CUSTOM")
                .putString("call_background_uri", uri.toString())
                .apply()
            backgroundMode = "CUSTOM"
            backgroundDialog = false
            refresh++
        }
    }

    val videoBackgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            prefs.edit()
                .putString("call_background_video_uri", uri.toString())
                .apply()
            refresh++
            android.widget.Toast.makeText(context, "Video background updated", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun bool(key: String, default: Boolean = false): Boolean {
        refresh
        return prefs.getBoolean(key, default)
    }
    fun setBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        refresh++
    }
    fun recordBool(key: String, default: Boolean = false): Boolean {
        refresh
        return recordPrefs.getBoolean(key, default)
    }
    fun setRecordBool(key: String, value: Boolean) {
        recordPrefs.edit().putBoolean(key, value).apply()
        refresh++
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Call settings", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PremiumPurpleTop, PremiumPurpleMid, PremiumPurpleBottom))).padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SettingsCard {
                        ToggleSettingsRow(
                            icon = Icons.Outlined.TouchApp,
                            title = "Button press sound",
                            subtitle = "Play a soft click so every press is easy to notice",
                            checked = buttonSoundEnabled
                        ) { enabled ->
                            buttonSoundEnabled = enabled
                            UiFeedback.setSoundEnabled(context, enabled)
                        }
                        DividerInset()
                        ToggleSettingsRow(
                            icon = Icons.Outlined.Vibration,
                            title = "Vibrate for incoming calls",
                            subtitle = "Feel the call alert even in noisy places",
                            checked = bool("vibrate_incoming", true)
                        ) { setBool("vibrate_incoming", it) }
                        DividerInset()
                        ToggleSettingsRow(
                            icon = Icons.Outlined.Dialpad,
                            title = "Keypad tones",
                            subtitle = "Play tones when typing on the dial pad",
                            checked = bool("keypad_tones", true)
                        ) { setBool("keypad_tones", it) }
                    }
                }

                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.Security, "App security and lock", "Manage PIN and biometric (Face/Fingerprint) protection") {
                            securityDialog = true
                        }
                    }
                }

                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.Palette, "Call screen theme", appearance.callScreenThemeId) {
                            callThemeDialog = true
                        }
                        DividerInset()
                        SettingsRow(Icons.Outlined.Dialpad, "Dial pad theme", appearance.dialPadThemeId) {
                            dialPadThemeDialog = true
                        }
                    }
                }

                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.TextFields, "Text call", "Accessibility, RTT and caption options") {
                            openTextCallSettings(context)
                        }
                    }
                }

                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.FiberManualRecord, "Record calls", recordingSummary(recordPrefs)) {
                            recordingModeDialog = true
                        }
                        if (recordingMode == "AUTO") {
                            DividerInset()
                            SettingsRow(
                                Icons.Outlined.PersonOff,
                                "Do not record selected numbers",
                                excludedSummary(recordPrefs)
                            ) { exclusionsDialog = true }
                            DividerInset()
                            SettingsRow(
                                Icons.Outlined.FilterAlt,
                                "Automatic recording scope",
                                recordingScopeSummary(recordPrefs)
                            ) { recordingScopeDialog = true }
                            if (recordingScope == "SELECTED") {
                                DividerInset()
                                SettingsRow(
                                    Icons.Outlined.PersonAddAlt,
                                    "Record only selected numbers",
                                    selectedRecordingSummary(recordPrefs)
                                ) { selectedRecordingDialog = true }
                            }
                        }
                        DividerInset()
                        ToggleSettingsRow(
                            icon = Icons.Outlined.VolumeUp,
                            title = "Speaker-assisted recording",
                            subtitle = "Auto-toggle speakerphone for clearer capture",
                            checked = recordBool("speaker_assisted", false)
                        ) { setRecordBool("speaker_assisted", it) }
                        DividerInset()
                        ToggleSettingsRow(
                            icon = Icons.Outlined.Security,
                            title = "Professional recording bypass",
                            subtitle = "Saves file even if system blocks audio signal",
                            checked = recordBool("professional_bypass", false)
                        ) { setRecordBool("professional_bypass", it) }
                        DividerInset()
                        ToggleSettingsRow(
                            icon = Icons.Outlined.Subtitles,
                            title = "Call captions",
                            subtitle = "Show live call captions when supported",
                            checked = bool("call_captions")
                        ) { setBool("call_captions", it) }
                    }
                }

                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.Block, "Block numbers", "Manage blocked numbers and SIM-based rules") {
                            onOpenRules()
                        }
                        DividerInset()
                        ToggleSettingsRow(
                            icon = Icons.Outlined.PersonOff,
                            title = "Block unknown callers",
                            subtitle = "Automatically reject calls from numbers not in contacts",
                            checked = bool("block_unknown", false)
                        ) { setBool("block_unknown", it) }
                        DividerInset()
                        ToggleSettingsRow(
                            icon = Icons.Outlined.Security,
                            title = "Caller ID and spam protection",
                            subtitle = "Warn for suspected spam calls",
                            checked = bool("spam_protection", true)
                        ) { setBool("spam_protection", it) }
                        DividerInset()
                        SettingsRow(
                            Icons.Outlined.VerifiedUser,
                            "External caller ID provider",
                            "Open Truecaller when installed; direct data sharing requires its official SDK/API"
                        ) { openTruecaller(context) }
                    }
                }

                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.Wallpaper, "Call background", backgroundSummary(backgroundMode)) {
                            backgroundDialog = true
                        }
                        DividerInset()
                        SettingsRow(Icons.Outlined.ContactPhone, "Caller information", callerInfoSummary(prefs)) {
                            callerInfoDialog = true
                        }
                        DividerInset()
                        SettingsRow(Icons.Outlined.NotificationsActive, "Call alerts and ringtone", "Choose ringtone with compact preview") {
                            onOpenRingtone()
                        }
                        DividerInset()
                        SettingsRow(Icons.Outlined.CallEnd, "Answering and ending calls", answerSummary(prefs)) {
                            setBool("volume_answer", !bool("volume_answer"))
                        }
                        DividerInset()

                        SettingsRow(Icons.Outlined.PictureInPictureAlt, "Call display while using apps", prefs.getString("display_mode", "Small pop-up") ?: "Small pop-up") {
                            displayModeDialog = true
                        }
                    }
                }

                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.AutoAwesome, "Advanced feature hub", "Calling, AI, network, accessibility, integration and device features") {
                            onOpenFeatureHub()
                        }
                        DividerInset()
                        SettingsRow(Icons.Outlined.Speed, "Speed dial numbers", "Open system Phone speed-dial settings") {
                            openSystemPhoneSettings(context)
                        }
                        DividerInset()
                        SettingsRow(Icons.Outlined.ImportExport, "Import / export contacts", "CSV backup, preview and duplicate-safe import") {
                            onOpenCsv()
                        }
                        DividerInset()
                        SettingsRow(Icons.Outlined.RecordVoiceOver, "Voice calling", "System assistant voice-call entry") {
                            openVoiceHelp(context)
                        }
                    }
                }

                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.Voicemail, "Voicemail", "Carrier voicemail settings") {
                            openVoicemailSettings(context)
                        }
                    }
                }

                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.RestoreFromTrash, "Recycle bin", "Restore deleted blocked numbers and rules within 30 days") {
                            onOpenRecycleBin()
                        }
                        DividerInset()
                        SettingsRow(Icons.Outlined.Tune, "Supplementary services", "Caller ID, call forwarding, barring, waiting and fixed dialling numbers") {
                            onOpenSupplementaryServices()
                        }
                        DividerInset()
                        SettingsRow(Icons.Outlined.SettingsPhone, "Other call settings", "Open system Phone call settings") {
                            openSystemPhoneSettings(context)
                        }
                    }
                }

                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.Storage, "Storage management", "Manage recordings and clear cache") {
                            runCatching {
                                context.cacheDir.deleteRecursively()
                                android.widget.Toast.makeText(context, "Cache cleared successfully", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        DividerInset()
                        ToggleSettingsRow(
                            icon = Icons.Outlined.AutoDelete,
                            title = "Auto-delete recordings",
                            subtitle = "Automatically remove recordings older than 30 days",
                            checked = bool("auto_delete_recordings", true)
                        ) { setBool("auto_delete_recordings", it) }
                        DividerInset()
                        SettingsRow(Icons.Outlined.SystemUpdate, "Upgred available", "Current version: 4.14.6") {
                            showUpdateDialog = true
                        }
                        DividerInset()
                        SettingsRow(Icons.Outlined.Info, "App information", "Manage permissions and full system storage") {
                            runCatching {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                    }
                }

                item { Text("Privacy", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(start = 16.dp)) }
                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.Notifications, "Notifications", "Professional alerts and category controls") {
                            onOpenNotifications()
                        }
                        DividerInset()
                        SettingsRow(Icons.Outlined.AdminPanelSettings, "Permissions", "Phone, contacts and microphone") {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }
                    }
                }

                item {
                    SettingsCard {
                        SettingsRow(Icons.Outlined.Info, "About Shyna Caller Guard Pro", "Version 4.14.6 · Final Pro Suite") { }
                    }
                }
            }
        }
    }

    if (callThemeDialog) {
        ThemeSelectorDialog(
            title = "Call screen theme",
            options = BRAND_THEMES,
            selected = appearance.callScreenThemeId,
            onDismiss = { callThemeDialog = false },
            onSelect = { 
                PersonalizationManager.saveCallScreenTheme(context, it)
                onThemeChanged()
                callThemeDialog = false 
            }
        )
    }

    if (dialPadThemeDialog) {
        ThemeSelectorDialog(
            title = "Dial pad theme",
            options = BRAND_THEMES,
            selected = appearance.dialPadThemeId,
            onDismiss = { dialPadThemeDialog = false },
            onSelect = { 
                PersonalizationManager.saveDialPadTheme(context, it)
                onThemeChanged()
                dialPadThemeDialog = false 
            }
        )
    }

    if (recordingModeDialog) {
        val currentMode = recordPrefs.getString("recording_mode", "AUTO") ?: "OFF"
        ChoiceDialog(
            title = "Record calls",
            options = listOf("Off", "Manual", "Automatic"),
            selected = when (currentMode) { "MANUAL" -> "Manual"; "AUTO" -> "Automatic"; else -> "Off" },
            onDismiss = { recordingModeDialog = false },
            onSelect = { label ->
                val value = when (label) { "Manual" -> "MANUAL"; "Automatic" -> "AUTO"; else -> "OFF" }
                if (value == "AUTO" && ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    recordPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                } else {
                    recordPrefs.edit().putString("recording_mode", value).apply()
                    recordingMode = value
                    refresh++
                }
                recordingModeDialog = false
            }
        )
    }

    if (exclusionsDialog) {
        RecordingExclusionsDialog(
            context = context,
            initial = recordPrefs.getStringSet("excluded_numbers", emptySet()).orEmpty(),
            onDismiss = { exclusionsDialog = false },
            onSave = { values ->
                recordPrefs.edit().putStringSet("excluded_numbers", values).apply()
                exclusionsDialog = false
                refresh++
            }
        )
    }

    if (recordingScopeDialog) {
        val current = recordPrefs.getString("recording_scope", "ALL") ?: "ALL"
        ChoiceDialog(
            title = "Automatic recording scope",
            options = listOf("All calls", "Incoming calls", "Outgoing calls", "Unknown numbers only", "Selected numbers only"),
            selected = when (current) {
                "INCOMING" -> "Incoming calls"
                "OUTGOING" -> "Outgoing calls"
                "UNKNOWN" -> "Unknown numbers only"
                "SELECTED" -> "Selected numbers only"
                else -> "All calls"
            },
            onDismiss = { recordingScopeDialog = false },
            onSelect = { label ->
                val value = when (label) {
                    "Incoming calls" -> "INCOMING"
                    "Outgoing calls" -> "OUTGOING"
                    "Unknown numbers only" -> "UNKNOWN"
                    "Selected numbers only" -> "SELECTED"
                    else -> "ALL"
                }
                recordPrefs.edit().putString("recording_scope", value).apply()
                recordingScope = value
                recordingScopeDialog = false
                refresh++
            }
        )
    }

    if (selectedRecordingDialog) {
        RecordingExclusionsDialog(
            context = context,
            initial = recordPrefs.getStringSet("selected_record_numbers", emptySet()).orEmpty(),
            onDismiss = { selectedRecordingDialog = false },
            onSave = { values ->
                recordPrefs.edit().putStringSet("selected_record_numbers", values).apply()
                selectedRecordingDialog = false
                refresh++
            },
            title = "Record only these numbers",
            description = "Use this list with Selected numbers only automatic recording."
        )
    }

    if (displayModeDialog) {
        ChoiceDialog(
            title = "Call display while using apps",
            options = listOf("Full screen", "Small pop-up", "Mini pop-up"),
            selected = prefs.getString("display_mode", "Small pop-up") ?: "Small pop-up",
            onDismiss = { displayModeDialog = false },
            onSelect = {
                prefs.edit().putString("display_mode", it).apply()
                displayModeDialog = false
            }
        )
    }

    if (backgroundDialog) {
        ChoiceDialog(
            title = "Call background",
            options = CALL_BACKGROUND_OPTIONS + listOf("Custom image", "Video background", "Reset custom image"),
            selected = backgroundSummary(backgroundMode),
            onDismiss = { backgroundDialog = false },
            onSelect = { label ->
                when (label) {
                    "Custom image" -> customBackgroundPicker.launch(arrayOf("image/*"))
                    "Video background" -> videoBackgroundPicker.launch(arrayOf("video/*"))
                    "Reset custom image" -> {
                        prefs.edit().remove("call_background_uri").remove("call_background_video_uri").putString("call_background", "AURORA").apply()
                        backgroundMode = "AURORA"
                        backgroundDialog = false
                        refresh++
                    }
                    else -> {
                        backgroundMode = backgroundCodeForLabel(label)
                        prefs.edit().putString("call_background", backgroundMode).apply()
                        backgroundDialog = false
                        refresh++
                    }
                }
            }
        )
    }

    if (callerInfoDialog) {
        CallerInfoDialog(
            showName = bool("caller_name", true),
            showNumber = bool("caller_number", true),
            showSim = bool("caller_sim", true),
            showSpam = bool("caller_spam", true),
            onDismiss = { callerInfoDialog = false },
            onSave = { name, number, sim, spam ->
                prefs.edit()
                    .putBoolean("caller_name", name)
                    .putBoolean("caller_number", number)
                    .putBoolean("caller_sim", sim)
                    .putBoolean("caller_spam", spam)
                    .apply()
                callerInfoDialog = false
            }
        )
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Upgred Required", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A newer version (v4.15.0) is available with premium features:", color = Color.White.copy(alpha = 0.8f))
                    Text("• New Glassmorphic UI elements", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    Text("• Enhanced AI Call Screening", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    Text("• Stability and battery optimizations", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showUpdateDialog = false
                        android.widget.Toast.makeText(context, "Upgred started...", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appearance.accentColor, contentColor = Color.Black)
                ) { Text("Upgred Now") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("Later", color = Color.White.copy(alpha = 0.6f)) }
            }
        )
    }

    if (securityDialog) {
        SecuritySettingsDialog(
            prefs = prefs,
            onDismiss = { securityDialog = false }
        )
    }
}

@Composable
private fun SecuritySettingsDialog(
    prefs: android.content.SharedPreferences,
    onDismiss: () -> Unit
) {
    var pinEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_pin", false)) }
    var biometricEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_biometric", false)) }
    var showPinSetup by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App security", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleSettingsRow(
                    icon = Icons.Outlined.Lock,
                    title = "PIN Lock",
                    subtitle = "Require a 4-digit PIN to open the app",
                    checked = pinEnabled
                ) { enabled ->
                    if (enabled) {
                        showPinSetup = true
                    } else {
                        prefs.edit().putBoolean("app_lock_pin", false).apply()
                        pinEnabled = false
                    }
                }
                DividerInset()
                ToggleSettingsRow(
                    icon = Icons.Outlined.Face,
                    title = "Face / Biometric Lock",
                    subtitle = "Use device security (Face/Fingerprint) to unlock",
                    checked = biometricEnabled
                ) { enabled ->
                    prefs.edit().putBoolean("app_lock_biometric", enabled).apply()
                    biometricEnabled = enabled
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )

    if (showPinSetup) {
        var pinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinSetup = false },
            title = { Text("Set 4-digit PIN") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    label = { Text("Enter PIN") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = pinInput.length == 4,
                    onClick = {
                        prefs.edit().putBoolean("app_lock_pin", true).putString("app_pin_code", pinInput).apply()
                        pinEnabled = true
                        showPinSetup = false
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPinSetup = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ThemeSelectorDialog(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val appearance = LocalAppearance.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(options) { option ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(option) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (option == selected) appearance.accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                        border = if (option == selected) androidx.compose.foundation.BorderStroke(1.5.dp, appearance.accentColor.copy(alpha = 0.5f)) else null
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = option == selected, 
                                    onClick = { onSelect(option) }, 
                                    colors = RadioButtonDefaults.colors(selectedColor = appearance.accentColor, unselectedColor = Color.White.copy(alpha = 0.4f))
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(option, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("PREVIEW: ${option.uppercase()}", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color.White.copy(alpha = 0.6f)) } }
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
    ) { Column(content = content) }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { UiFeedback.playClick(context, view); onClick() }.padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val appearance = LocalAppearance.current
        Icon(icon, null, modifier = Modifier.size(23.dp), tint = appearance.accentColor)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 2)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f))
    }
}

@Composable
private fun ToggleSettingsRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        val appearance = LocalAppearance.current
        Icon(icon, null, modifier = Modifier.size(23.dp), tint = appearance.accentColor)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = { UiFeedback.playClick(context, view); onChecked(it) }, colors = SwitchDefaults.colors(checkedThumbColor = appearance.accentColor, checkedTrackColor = appearance.accentColor.copy(alpha = 0.5f)))
    }
}

@Composable private fun DividerInset() = HorizontalDivider(Modifier.padding(start = 53.dp), color = Color.White.copy(alpha = 0.1f))

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                items(options) { option ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Spacer(Modifier.width(12.dp)); Text(option, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(alpha = 0.6f)) } }
    )
}

@Composable
private fun CallerInfoDialog(showName: Boolean, showNumber: Boolean, showSim: Boolean, showSpam: Boolean, onDismiss: () -> Unit, onSave: (Boolean, Boolean, Boolean, Boolean) -> Unit) {
    var name by remember { mutableStateOf(showName) }
    var number by remember { mutableStateOf(showNumber) }
    var sim by remember { mutableStateOf(showSim) }
    var spam by remember { mutableStateOf(showSpam) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Caller information", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CheckRow("Contact name", name) { name = it }
                CheckRow("Phone number", number) { number = it }
                CheckRow("SIM used", sim) { sim = it }
                CheckRow("Spam warning", spam) { spam = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, number, sim, spam) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable private fun CheckRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = onChecked); Spacer(Modifier.width(12.dp)); Text(label, color = Color.White)
    }
}

@Composable
private fun RecordingExclusionsDialog(
    context: Context,
    initial: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
    title: String = "Do not record these numbers",
    description: String = "Automatic recording stays off for every number in this list."
) {
    var values by remember { mutableStateOf(initial.toMutableSet()) }
    var input by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.Contacts._ID),
                    null, null, null
                )?.use { c ->
                    if (!c.moveToFirst()) return@use
                    val id = c.getLong(0)
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
                        arrayOf(id.toString()),
                        null
                    )?.use { phones ->
                        if (phones.moveToFirst()) {
                            val number = phones.getString(0).orEmpty().trim()
                            if (number.isNotBlank()) values = values.toMutableSet().apply { add(number) }
                        }
                    }
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(description, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter { ch -> ch.isDigit() || ch == '+' }.take(24) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Phone number") }
                    )
                    IconButton(onClick = {
                        input.trim().takeIf { it.isNotBlank() }?.let {
                            values = values.toMutableSet().apply { add(it) }
                            input = ""
                        }
                    }) { Icon(Icons.Outlined.AddCircle, "Add", tint = Color.White) }
                }
                OutlinedButton(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Contacts, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose from phone contacts")
                }
                if (values.isEmpty()) {
                    Text("No numbers in this list", color = Color.White.copy(alpha = 0.4f))
                } else {
                    LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        items(values.sorted()) { number ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(number, color = Color.White, modifier = Modifier.weight(1f))
                                IconButton(onClick = { values = values.toMutableSet().apply { remove(number) } }) {
                                    Icon(Icons.Outlined.Delete, "Remove", tint = Color.Red.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(values.toSet()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


private fun recordingScopeSummary(prefs: android.content.SharedPreferences): String = when (prefs.getString("recording_scope", "ALL")) {
    "INCOMING" -> "Incoming calls only"
    "OUTGOING" -> "Outgoing calls only"
    "UNKNOWN" -> "Unknown numbers only"
    "SELECTED" -> "Selected numbers only"
    else -> "All calls"
}

private fun selectedRecordingSummary(prefs: android.content.SharedPreferences): String {
    val count = prefs.getStringSet("selected_record_numbers", emptySet()).orEmpty().size
    return if (count == 0) "No selected numbers" else "$count selected number${if (count == 1) "" else "s"}"
}

private fun excludedSummary(prefs: android.content.SharedPreferences): String {
    val count = prefs.getStringSet("excluded_numbers", emptySet()).orEmpty().size
    return if (count == 0) "No numbers excluded" else "$count number${if (count == 1) "" else "s"} excluded"
}

private fun openTruecaller(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("com.truecaller")
    if (launch != null) {
        context.startActivity(launch)
    } else {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.truecaller")))
        }.onFailure {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        }
    }
}

private fun recordingSummary(prefs: android.content.SharedPreferences): String = when (prefs.getString("recording_mode", "AUTO")) {
    "AUTO" -> "Automatic recording"
    "MANUAL" -> "Manual recording"
    else -> "Off"
}

private fun callerInfoSummary(prefs: android.content.SharedPreferences): String {
    val active = listOf(
        prefs.getBoolean("caller_name", true), prefs.getBoolean("caller_number", true),
        prefs.getBoolean("caller_sim", true), prefs.getBoolean("caller_spam", true)
    ).count { it }
    return "$active information items enabled"
}

private fun answerSummary(prefs: android.content.SharedPreferences): String =
    if (prefs.getBoolean("volume_answer", false)) "Volume-up answers calls" else "Swipe buttons on call screen"

private val CALL_BACKGROUND_OPTIONS = listOf(
    "Aurora purple", "AMOLED black", "Minimal blue", "Royal violet", "Ocean night",
    "Emerald glow", "Rose dusk", "Sunset orange", "Deep indigo", "Graphite silver",
    "Teal wave", "Crimson night", "Golden hour", "Lavender mist", "Midnight cyan",
    "Forest dark", "Berry gradient", "Electric blue", "Copper glow", "Soft charcoal"
)

private fun backgroundCodeForLabel(label: String): String = when (label) {
    "Aurora purple" -> "BG01"
    "AMOLED black" -> "BG02"
    "Minimal blue" -> "BG03"
    "Royal violet" -> "BG04"
    "Ocean night" -> "BG05"
    "Emerald glow" -> "BG06"
    "Rose dusk" -> "BG07"
    "Sunset orange" -> "BG08"
    "Deep indigo" -> "BG09"
    "Graphite silver" -> "BG10"
    "Teal wave" -> "BG11"
    "Crimson night" -> "BG12"
    "Golden hour" -> "BG13"
    "Lavender mist" -> "BG14"
    "Midnight cyan" -> "BG15"
    "Forest dark" -> "BG16"
    "Berry gradient" -> "BG17"
    "Electric blue" -> "BG18"
    "Copper glow" -> "BG19"
    "Soft charcoal" -> "BG20"
    else -> "BG01"
}

private fun backgroundSummary(current: String): String = when (current) {
    "CUSTOM" -> "Custom image"
    "AURORA", "BG01" -> "Aurora purple"
    "DARK", "BG02" -> "AMOLED black"
    "MINIMAL", "BG03" -> "Minimal blue"
    else -> CALL_BACKGROUND_OPTIONS.getOrElse(current.removePrefix("BG").toIntOrNull()?.minus(1) ?: 0) { "Aurora purple" }
}


private fun openRingtonePicker(context: Context) {
    runCatching {
        context.startActivity(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        })
    }.onFailure { openSystemCallSettings(context) }
}

private fun openSystemCallSettings(context: Context) {
    val intents = listOf(
        Intent("android.settings.CALL_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    intents.firstOrNull { it.resolveActivity(context.packageManager) != null }?.let(context::startActivity)
}

private fun openRecordingSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

private fun openVoiceHelp(context: Context) {
    android.widget.Toast.makeText(context, "Set Shyna Caller Guard as default Phone app. Say: Call Ravi. For app-specific routines use shyna://call?name=Ravi", android.widget.Toast.LENGTH_LONG).show()
}

private fun safeStartSettings(context: Context, intents: List<Intent>) {
    val target = intents.firstOrNull { it.resolveActivity(context.packageManager) != null }
    if (target != null) runCatching { context.startActivity(target) }
    else runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}

private fun openSystemPhoneSettings(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("com.samsung.android.dialer")
        ?: context.packageManager.getLaunchIntentForPackage("com.android.server.telecom")
    safeStartSettings(context, listOfNotNull(
        Intent("com.samsung.android.app.telephonyui.action.OPEN_CALL_SETTINGS"),
        Intent("android.settings.CALL_SETTINGS"),
        launch,
        Intent(Settings.ACTION_SETTINGS)
    ))
}

private fun openWifiCallingSettings(context: Context) {
    val intents = listOf(
        Intent("android.settings.WIFI_CALLING_SETTINGS"),
        Intent("android.settings.WIFI_CALLING_SETTINGS"),
        Intent("com.samsung.android.settings.WIFI_CALLING_SETTINGS"),
        Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
        Intent(Settings.ACTION_WIRELESS_SETTINGS)
    )
    val target = intents.firstOrNull { it.resolveActivity(context.packageManager) != null }
    if (target != null) {
        runCatching { context.startActivity(target) }
            .onFailure { android.widget.Toast.makeText(context, "Open Settings > Connections > Wi-Fi Calling", android.widget.Toast.LENGTH_LONG).show() }
    } else {
        android.widget.Toast.makeText(context, "Open Settings > Connections > Wi-Fi Calling", android.widget.Toast.LENGTH_LONG).show()
        runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
    }
}

private fun openVoicemailSettings(context: Context) = safeStartSettings(context, listOf(
    Intent("android.telephony.action.CONFIGURE_VOICEMAIL"),
    Intent("com.android.phone.CallFeaturesSetting"),
    Intent("android.settings.CALL_SETTINGS")
))

private fun openSupplementaryCallSettings(context: Context) = safeStartSettings(context, listOf(
    Intent("com.samsung.android.app.telephonyui.action.OPEN_CALL_SETTINGS"),
    Intent("android.settings.CALL_SETTINGS"),
    Intent(Settings.ACTION_WIRELESS_SETTINGS)
))

private fun openTextCallSettings(context: Context) = safeStartSettings(context, listOf(
    Intent(Settings.ACTION_CAPTIONING_SETTINGS),
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
    Intent("android.settings.CALL_SETTINGS")
))
