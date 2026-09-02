package com.example.callruleblocker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.callruleblocker.ui.theme.*

private const val FEATURE_PREFS = "advanced_feature_control_v5"
private enum class FeatureMode { AUTO, MANUAL }
private enum class FeatureRoute { INTERNAL, RULES, SETTINGS, SYSTEM_CALLS, SIM, NETWORK, NOTIFICATIONS, SECURITY, BATTERY, BLUETOOTH, ACCESSIBILITY, CONTACTS, PARTNER }

private data class AdvancedFeature(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val defaultEnabled: Boolean = false,
    val modeSupported: Boolean = true,
    val route: FeatureRoute = FeatureRoute.INTERNAL,
    val packageName: String? = null,
    val options: List<String> = emptyList()
)

private data class AdvancedSection(
    val key: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val features: List<AdvancedFeature>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureHubScreen(
    onBack: () -> Unit,
    onOpenRules: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenReport: (String) -> Unit = {},
    onThemeChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    val prefs = remember { context.getSharedPreferences(FEATURE_PREFS, Context.MODE_PRIVATE) }
    val sections = remember { advancedSections() }
    var refresh by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(sections.first().key) }
    var modeFeature by remember { mutableStateOf<AdvancedFeature?>(null) }
    var optionsFeature by remember { mutableStateOf<AdvancedFeature?>(null) }
    var showRecommended by remember { mutableStateOf(false) }

    fun enabled(feature: AdvancedFeature): Boolean {
        refresh
        return prefs.getBoolean("feature_${feature.key}", feature.defaultEnabled)
    }
    fun setEnabled(feature: AdvancedFeature, value: Boolean) {
        prefs.edit().putBoolean("feature_${feature.key}", value).apply(); refresh++
    }
    fun mode(feature: AdvancedFeature): FeatureMode {
        refresh
        return runCatching { FeatureMode.valueOf(prefs.getString("mode_${feature.key}", FeatureMode.AUTO.name) ?: FeatureMode.AUTO.name) }.getOrDefault(FeatureMode.AUTO)
    }
    fun setMode(feature: AdvancedFeature, value: FeatureMode) {
        prefs.edit().putString("mode_${feature.key}", value.name).apply(); refresh++
    }

    val total = sections.sumOf { it.features.size }
    val active = sections.sumOf { s -> s.features.count { enabled(it) } }
    val filtered = sections.mapNotNull { section ->
        val items = section.features.filter { search.isBlank() || it.title.contains(search, true) || it.subtitle.contains(search, true) }
        if (items.isEmpty()) null else section.copy(features = items)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Column { Text("Advanced Control Center", fontWeight = FontWeight.Bold); Text("$active of $total features enabled", style = MaterialTheme.typography.labelMedium) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { showRecommended = true }) { Icon(Icons.Outlined.AutoFixHigh, "Recommended setup") } }
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PremiumPurpleTop, PremiumPurpleMid, PremiumPurpleBottom))).padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(shape = RoundedCornerShape(28.dp), color = PremiumCard.copy(alpha = .97f), tonalElevation = 5.dp) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(18.dp), color = PremiumAccent.copy(alpha = .16f)) {
                                    Icon(Icons.Outlined.Hub, null, tint = PremiumAccent, modifier = Modifier.padding(12.dp).size(28.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Shyna Premium Intelligence", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("Every feature has its own switch, operating mode and linked control.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                AssistChip(onClick = { showRecommended = true }, label = { Text("Smart setup") }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)) })
                            }
                            LinearProgressIndicator(progress = { if (total == 0) 0f else active.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(7.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Icon(Icons.Outlined.Close, "Clear") } },
                                placeholder = { Text("Search features, rules or integrations") },
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                    }
                }

                filtered.forEach { section ->
                    item(key = section.key) {
                        val fullSection = sections.first { it.key == section.key }
                        val sectionEnabled = fullSection.features.count { enabled(it) }
                        Surface(
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                            shape = RoundedCornerShape(24.dp),
                            color = PremiumCard.copy(alpha = .97f),
                            tonalElevation = 3.dp
                        ) {
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().clickable { expanded = if (expanded == section.key) "" else section.key }.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(shape = RoundedCornerShape(15.dp), color = PremiumAccent.copy(alpha = .14f)) {
                                        Icon(section.icon, null, tint = PremiumAccent, modifier = Modifier.padding(10.dp).size(22.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(section.title, fontWeight = FontWeight.SemiBold)
                                        Text("$sectionEnabled/${fullSection.features.size} active · ${section.description}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = {
                                        val turnOn = fullSection.features.any { !enabled(it) }
                                        fullSection.features.forEach { prefs.edit().putBoolean("feature_${it.key}", turnOn).apply() }
                                        refresh++
                                    }) { Text(if (sectionEnabled == fullSection.features.size) "Disable all" else "Enable all") }
                                    Icon(if (expanded == section.key || search.isNotBlank()) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                                }
                                AnimatedVisibility(
                                    visible = expanded == section.key || search.isNotBlank(),
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                                        section.features.forEachIndexed { index, feature ->
                                            val animatedAlpha by animateFloatAsState(
                                                targetValue = if (expanded == section.key || search.isNotBlank()) 1f else 0f,
                                                animationSpec = tween(durationMillis = 400, delayMillis = index * 50),
                                                label = "featureEntrance"
                                            )
                                            Box(modifier = Modifier.graphicsLayer { alpha = animatedAlpha }) {
                                                AdvancedFeatureRow(
                                                    feature = feature,
                                                    enabled = enabled(feature),
                                                    mode = mode(feature),
                                                    onToggle = { setEnabled(feature, it) },
                                                    onMode = { modeFeature = feature },
                                                    onOptions = { optionsFeature = feature },
                                                    onOpen = {
                                                        if (feature.key in setOf("daily_report", "weekly_report", "ai_summary", "spam_report", "missed_stats")) {
                                                            onOpenReport(feature.key)
                                                        } else {
                                                            openFeatureRoute(context, feature, onOpenRules, onOpenSettings)
                                                        }
                                                    }
                                                )
                                            }
                                            if (index != section.features.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Note: Android, system, carrier, Shyna Calling, cloud and AI services require their official permissions/APIs. Shyna stores your selection and opens the correct linked control; unavailable platform capabilities are never falsely shown as active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }

    modeFeature?.let { feature ->
        AlertDialog(
            onDismissRequest = { modeFeature = null },
            icon = { Icon(Icons.Outlined.Tune, null) },
            title = { Text("${feature.title} mode") },
            text = {
                Column {
                    ListItem(headlineContent = { Text("Automatic") }, supportingContent = { Text("Runs according to rules, schedules and supported system events") }, leadingContent = { RadioButton(selected = mode(feature) == FeatureMode.AUTO, onClick = { setMode(feature, FeatureMode.AUTO); modeFeature = null }) })
                    ListItem(headlineContent = { Text("Manual") }, supportingContent = { Text("Runs only when you explicitly activate it") }, leadingContent = { RadioButton(selected = mode(feature) == FeatureMode.MANUAL, onClick = { setMode(feature, FeatureMode.MANUAL); modeFeature = null }) })
                }
            },
            confirmButton = { TextButton(onClick = { modeFeature = null }) { Text("Done") } }
        )
    }

    optionsFeature?.let { feature ->
        AlertDialog(
            onDismissRequest = { optionsFeature = null },
            icon = { Icon(feature.icon, null) },
            title = { Text(feature.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(feature.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    feature.options.forEach { option ->
                        if (feature.key == "theme_color") {
                            // Specialized Premium Color Picker
                            val color = premiumColorPresets[option] ?: Color.White
                            ListItem(
                                headlineContent = { Text(option) },
                                leadingContent = { Box(Modifier.size(24.dp).background(color, CircleShape)) },
                                trailingContent = { 
                                    RadioButton(
                                        selected = appearance.accentColor == color,
                                        onClick = { 
                                            PersonalizationManager.saveAccentColor(context, color)
                                            onThemeChanged()
                                        }
                                    ) 
                                }
                            )
                        } else if (feature.key == "font_scale") {
                            // Specialized Font Scale Picker
                            val scale = uiScalePresets[option] ?: 1.0f
                            ListItem(
                                headlineContent = { Text(option) },
                                trailingContent = { 
                                    RadioButton(
                                        selected = appearance.uiScale == scale,
                                        onClick = { 
                                            PersonalizationManager.saveUiScale(context, scale)
                                            onThemeChanged()
                                        }
                                    ) 
                                }
                            )
                        } else {
                            var checked by remember(feature.key, option, refresh) { mutableStateOf(prefs.getBoolean("option_${feature.key}_$option", false)) }
                            ListItem(
                                headlineContent = { Text(option) },
                                trailingContent = { Switch(checked = checked, onCheckedChange = { checked = it; prefs.edit().putBoolean("option_${feature.key}_$option", it).apply(); refresh++ }) }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { optionsFeature = null }) { Text("Done") } }
        )
    }

    if (showRecommended) {
        AlertDialog(
            onDismissRequest = { showRecommended = false },
            icon = { Icon(Icons.Outlined.AutoFixHigh, null) },
            title = { Text("Apply recommended setup?") },
            text = { Text("Enables safe daily-use protection, recording controls, reminders, VIP bypass and recovery. High-risk automation and partner integrations stay off until you choose them.") },
            confirmButton = {
                Button(onClick = {
                    sections.flatMap { it.features }.forEach { feature -> prefs.edit().putBoolean("feature_${feature.key}", feature.defaultEnabled).apply() }
                    refresh++; showRecommended = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRecommended = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdvancedFeatureRow(
    feature: AdvancedFeature,
    enabled: Boolean,
    mode: FeatureMode,
    onToggle: (Boolean) -> Unit,
    onMode: () -> Unit,
    onOptions: () -> Unit,
    onOpen: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = (if (enabled) PremiumGreen else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = .13f)) {
                Icon(feature.icon, null, tint = if (enabled) PremiumGreen else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(9.dp).size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.title, fontWeight = FontWeight.Medium)
                Text(feature.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        Row(Modifier.fillMaxWidth().padding(start = 54.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (feature.modeSupported) AssistChip(onClick = onMode, enabled = enabled, label = { Text(if (mode == FeatureMode.AUTO) "Auto" else "Manual") }, leadingIcon = { Icon(if (mode == FeatureMode.AUTO) Icons.Outlined.AutoMode else Icons.Outlined.TouchApp, null, Modifier.size(17.dp)) })
            if (feature.options.isNotEmpty()) AssistChip(onClick = onOptions, enabled = enabled, label = { Text("Sub-options") }, leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(17.dp)) })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpen) { Text(if (feature.route == FeatureRoute.INTERNAL) "Details" else "Open"); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp)) }
        }
    }
}

private fun advancedSections(): List<AdvancedSection> = listOf(
    AdvancedSection("calls", "Call Management", "screening, rules and dual-SIM", Icons.Outlined.Call, listOf(
        f("ai_spam", "AI spam detection", "Local risk flags with caller-ID provider support", Icons.Outlined.Security, true, options = listOf("Warn only", "Auto block high risk", "Community report")),
        f("fast_blocking", "Fast call blocking", "Instant microsecond-level pre-ring rejection", Icons.Outlined.FlashOn, true),
        f("no_ring_cut", "No call ring cut auto", "Ensure zero ring by auto-terminating known spam", Icons.Outlined.DoNotDisturbOn, true),
        f("risk_score", "Unknown number risk score", "Shows low, medium or high risk before answering", Icons.Outlined.GppMaybe, true),
        f("geo_block", "Country / state wise blocking", "Create geographic and prefix-based blocking rules", Icons.Outlined.Public, true, route = FeatureRoute.RULES, options = listOf("International", "State prefixes", "Except contacts")),
        f("time_block", "Time based blocking", "Office hours, night mode and custom schedules", Icons.Outlined.Schedule, true, route = FeatureRoute.RULES, options = listOf("Office hours", "Night mode", "Weekend")),
        f("silent_unknown", "Auto silent unknown calls", "Silence callers not saved in contacts", Icons.Outlined.VolumeOff, true),
        f("vip", "VIP contact mode", "Always ring selected family or priority contacts", Icons.Outlined.Star, true, options = listOf("Bypass DND", "Loud ringtone", "Repeat alert")),
        f("repeat", "Repeat caller rule", "Allow after 3 calls within 5 minutes", Icons.Outlined.Repeat, true),
        f("hidden", "Hidden / private number block", "Reject calls without caller number", Icons.Outlined.VisibilityOff, true, route = FeatureRoute.RULES),
        f("series", "Number series blocking", "Block prefixes such as +91 98765*****", Icons.Outlined.FilterAlt, true, route = FeatureRoute.RULES),
        f("dual_sim_rules", "Dual SIM independent rules", "Separate SIM 1, SIM 2 and eSIM behavior", Icons.Filled.SimCard, true, route = FeatureRoute.SIM, options = listOf("SIM 1 rules", "SIM 2 rules", "Remember last SIM")),
        f("greylist", "Whitelist + blacklist + greylist", "Allow, block or screen uncertain callers", Icons.Outlined.Rule, true, route = FeatureRoute.RULES),
        f("call_screen", "Pixel-style call screening", "Screen unknown calls when platform support is available", Icons.Outlined.PhoneInTalk, true),
        f("floating", "Floating caller window", "Compact caller overlay while using other apps", Icons.Outlined.PictureInPictureAlt, true, route = FeatureRoute.SETTINGS),
        f("reason", "Call reason popup", "Mark calls as spam, bank, delivery or family", Icons.Outlined.Label, true),
        f("block_report", "One-tap Block & Report", "Block locally and prepare a spam report", Icons.Outlined.Report, true, route = FeatureRoute.RULES)
    )),

    AdvancedSection("video", "Video Caller ID", "full-screen identity and offline media", Icons.Outlined.Videocam, listOf(
        f("video_full", "Full-screen video caller", "Play a selected video on incoming calls", Icons.Outlined.Fullscreen, true, route = FeatureRoute.SETTINGS),
        f("video_contact", "Different video per contact", "Assign individual contact videos", Icons.Outlined.Contacts, true),
        f("video_unknown", "Unknown caller video", "Use a safe default animation for unknown callers", Icons.Outlined.PersonOff, true),
        f("video_events", "Birthday and festival videos", "Date-based caller video presets", Icons.Outlined.Celebration, true),
        f("video_library", "Offline video library", "Manage local caller videos without internet", Icons.Outlined.VideoLibrary, true)
    )),
    AdvancedSection("recording", "Recording & AI Notes", "capture, search and summaries", Icons.Outlined.Mic, listOf(
        f("auto_record", "Automatic call recording", "Uses Shyna recording scope and exclusions", Icons.Outlined.FiberManualRecord, true, route = FeatureRoute.SETTINGS, options = listOf("Incoming", "Outgoing", "Unknown only", "Exclude selected")),
        f("record_search", "Search recordings", "Find by name, number, date, duration and notes", Icons.Outlined.ManageSearch, true, route = FeatureRoute.SETTINGS),
        f("record_notes", "Recording notes", "Attach editable notes and callback action", Icons.Outlined.NoteAlt, true),
        f("transcription", "Voice-to-text transcription", "Requires an on-device or approved cloud speech engine", Icons.Outlined.Subtitles, true),
        f("ai_summary", "AI call summary", "Generate summary only after explicit user action", Icons.Outlined.Summarize, true),
        f("ai_notes", "AI conversation notes", "Extract tasks, dates and follow-ups", Icons.Outlined.AutoAwesome, true),
        f("ai_reminder", "AI callback reminder", "Create reminders such as call back in 2 hours", Icons.Outlined.Alarm, true),
        f("cloud_record", "Cloud recording backup", "Requires a connected cloud provider", Icons.Outlined.CloudUpload, false)
    )),
    AdvancedSection("contacts", "Contacts", "clean, group, restore and sync", Icons.Outlined.Contacts, listOf(
        f("duplicate", "Duplicate contact cleaner", "Find matching phone numbers and names", Icons.Outlined.ContentCopy, true, route = FeatureRoute.CONTACTS),
        f("merge", "Contact merge", "Preview before safely merging duplicates", Icons.Outlined.Merge, true, route = FeatureRoute.CONTACTS),
        f("favorites", "Smart favorites", "Suggest frequent contacts; manual pin remains available", Icons.Outlined.Favorite, true),
        f("groups", "Family and office groups", "Group-based ringtone, SIM and blocking behavior", Icons.Outlined.GroupWork, true),
        f("contact_backup", "Contact backup & restore", "CSV and Android contacts provider workflow", Icons.Outlined.ImportExport, true, route = FeatureRoute.SETTINGS),
        f("deleted_contacts", "Deleted contacts recovery", "Recovery depends on available local backup", Icons.Outlined.RestoreFromTrash, true)
    )),
    AdvancedSection("security", "Security & Privacy", "lock, vault and permissions", Icons.Outlined.Lock, listOf(
        f("app_lock", "App lock", "Protect settings and recordings", Icons.Outlined.Lock, true, route = FeatureRoute.SECURITY),
        f("biometric", "Fingerprint / face unlock", "Uses Android biometric enrollment", Icons.Outlined.Fingerprint, true, route = FeatureRoute.SECURITY),
        f("hidden_mode", "Hidden mode", "Hide sensitive recording and rule previews", Icons.Outlined.VisibilityOff, false),
        f("fake_pin", "Fake PIN", "Open a decoy view with a separate PIN", Icons.Outlined.PinDrop, false),
        f("vault", "Private vault", "Keep selected recordings and contacts private", Icons.Outlined.FolderSpecial, true),
        f("permission_audit", "Permission audit", "Review phone, contacts, SMS and microphone access", Icons.Outlined.AdminPanelSettings, true, false, FeatureRoute.SECURITY)
    )),
    AdvancedSection("analytics", "Analytics", "private on-device usage insights", Icons.Outlined.Analytics, listOf(
        f("daily_report", "Daily call report", "Incoming, outgoing, missed and blocked totals", Icons.Outlined.Today, true),
        f("weekly_report", "Weekly report", "Trend summary with privacy-first local data", Icons.Outlined.DateRange, true),
        f("missed_stats", "Missed call statistics", "Counts, response time and reminder completion", Icons.Outlined.PhoneMissed, true),
        f("spam_report", "Spam report", "Blocked categories, prefixes and risk distribution", Icons.Outlined.Assessment, true),
        f("frequency", "Contact frequency graph", "Most contacted people by count and duration", Icons.Outlined.BarChart, true),
        f("size_duration", "Call size & duration filters", "Filter history and recordings by duration and file size", Icons.Outlined.FilterList, true, route = FeatureRoute.SETTINGS)
    )),
    AdvancedSection("automation", "Automation & Integrations", "device context and companion apps", Icons.Outlined.SettingsSuggest, listOf(
        f("tasker", "Automation integration", "Expose supported Shyna actions to automation", Icons.Outlined.Extension, true, route = FeatureRoute.PARTNER, pkg = "net.dinglisch.android.taskerm"),
        f("location", "Location based rules", "Apply profiles only after location permission", Icons.Outlined.LocationOn, true),
        f("bluetooth", "Bluetooth rules", "Car, headset and watch-specific profiles", Icons.Outlined.Bluetooth, true, route = FeatureRoute.BLUETOOTH),
        f("wifi", "Wi-Fi rules", "Apply home or office profiles by network", Icons.Outlined.Wifi, true),
        f("charging", "Charging rules", "Switch profile while charging or docked", Icons.Outlined.BatteryChargingFull, true),
        f("headset", "Headset rules", "Audio route and auto-answer preferences", Icons.Outlined.Headphones, true, route = FeatureRoute.BLUETOOTH),
        f("wear", "Smartwatch integration", "Open watch companion app", Icons.Outlined.Watch, true, route = FeatureRoute.PARTNER, pkg = "com.samsung.android.app.watchmanager"),
        f("android_auto", "In-car support", "Open connected-device controls", Icons.Outlined.DirectionsCar, true, route = FeatureRoute.BLUETOOTH),
        f("bixby", "Voice assistant integration", "Open system voice assistant", Icons.Outlined.RecordVoiceOver, true, route = FeatureRoute.PARTNER, pkg = "com.samsung.android.bixby.agent"),
        f("edge", "Side panel shortcut", "System edge panel configuration", Icons.Outlined.ViewSidebar, true, route = FeatureRoute.SETTINGS),
        f("widgets", "System widgets & AOD caller", "Widget and always-on-display linked controls", Icons.Outlined.Widgets, true, route = FeatureRoute.SETTINGS),
        f("goodlock", "Advanced customization", "Open system customization tools when installed", Icons.Outlined.DashboardCustomize, true, route = FeatureRoute.PARTNER, pkg = "com.samsung.android.goodlock")
    )),
    AdvancedSection("backup", "Premium, Backup & Recovery", "sync, themes and recycle retention", Icons.Outlined.CloudSync, listOf(
        f("cloud_sync", "Cloud sync", "Requires an explicitly connected cloud account", Icons.Outlined.CloudSync, true),
        f("multi_device", "Multiple device sync", "Encrypted sync design; provider required", Icons.Outlined.Devices, true),
        f("drive_backup", "Cloud drive backup", "Requires cloud storage authorization", Icons.Outlined.Backup, true),
        f("rules_export", "Import / export rules", "Portable local rule backup", Icons.Outlined.ImportExport, true, route = FeatureRoute.RULES),
        f("unlimited", "Unlimited rules", "No artificial local rule limit", Icons.Outlined.AllInclusive, true, false, FeatureRoute.RULES),
        f("themes", "Premium theme engine", "Select call, dialer and control-center themes", Icons.Outlined.Palette, true, route = FeatureRoute.SETTINGS),
        f("plugins", "Plugin support", "Future-safe integration registry", Icons.Outlined.Extension, true),
        f("recycle", "Recycle bin recovery", "Deleted rules, logs and recordings retention", Icons.Outlined.RestoreFromTrash, true, route = FeatureRoute.SETTINGS, options = listOf("30 days", "60 days", "90 days")),
        f("no_ads", "No ads", "Premium interface remains distraction-free", Icons.Outlined.Block, true, false)
    )),

    AdvancedSection("personalization", "Personalization", "theme colors, fonts and adaptive UI", Icons.Outlined.Palette, listOf(
        f("theme_color", "App Theme Color", "Select the primary premium accent color", Icons.Outlined.ColorLens, true, options = listOf("Royal Purple", "Samsung Blue", "Emerald Green", "Sunset Orange", "Rose Pink")),
        f("font_scale", "Global UI Scale", "Adjust entire interface size for better visibility", Icons.Outlined.TextFields, true, options = listOf("Small", "Standard", "Large", "Extra Large", "Huge")),
        f("glass_ui", "Glassmorphism Effects", "Apply high-end blur and transparency to all cards", Icons.Outlined.BlurOn, true),
        f("adaptive_dialer", "Smart Adaptive Dialer", "Auto-font scaling and multi-line support", Icons.Outlined.SettingsCell, true)
    ))
)

private fun f(key: String, title: String, subtitle: String, icon: ImageVector, default: Boolean = false, mode: Boolean = true, route: FeatureRoute = FeatureRoute.INTERNAL, pkg: String? = null, options: List<String> = emptyList()) =
    AdvancedFeature(key, title, subtitle, icon, default, mode, route, pkg, options)

private fun openFeatureRoute(context: Context, feature: AdvancedFeature, onOpenRules: () -> Unit, onOpenSettings: () -> Unit) {
    when (feature.route) {
        FeatureRoute.RULES -> onOpenRules()
        FeatureRoute.SETTINGS, FeatureRoute.INTERNAL -> onOpenSettings()
        FeatureRoute.SIM -> safeStart(context, listOf(Intent("com.samsung.settings.SIMCARD_MGR"), Intent(Settings.ACTION_WIRELESS_SETTINGS)))
        FeatureRoute.SYSTEM_CALLS -> safeStart(context, listOf(Intent("android.settings.CALL_SETTINGS"), Intent(Settings.ACTION_WIRELESS_SETTINGS)))
        FeatureRoute.NETWORK -> safeStart(context, listOf(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS)))
        FeatureRoute.NOTIFICATIONS -> safeStart(context, listOf(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)))
        FeatureRoute.SECURITY -> safeStart(context, listOf(Intent(Settings.ACTION_BIOMETRIC_ENROLL), Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))))
        FeatureRoute.BATTERY -> safeStart(context, listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)))
        FeatureRoute.BLUETOOTH -> safeStart(context, listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS)))
        FeatureRoute.ACCESSIBILITY -> safeStart(context, listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
        FeatureRoute.CONTACTS -> safeStart(context, listOf(Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)))
        FeatureRoute.PARTNER -> openPackageOrMarket(context, feature.packageName)
    }
}

private fun safeStart(context: Context, intents: List<Intent>) {
    val intent = intents.firstOrNull { it.resolveActivity(context.packageManager) != null } ?: Intent(Settings.ACTION_SETTINGS)
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openPackageOrMarket(context: Context, packageName: String?) {
    if (packageName.isNullOrBlank()) return safeStart(context, listOf(Intent(Settings.ACTION_SETTINGS)))
    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launch != null) safeStart(context, listOf(launch)) else safeStart(context, listOf(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")), Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))))
}
