package com.example.callruleblocker

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callruleblocker.ui.theme.CallRuleBlockerTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.example.callruleblocker.ui.SmartCommunicationScreen
import com.example.callruleblocker.ui.AppLockScreen
import com.example.callruleblocker.ui.PhoneHomeScreen
import com.example.callruleblocker.ui.RuleListScreen
import com.example.callruleblocker.ui.AddRuleScreen
import com.example.callruleblocker.ui.RecordingSettingsScreen
import com.example.callruleblocker.ui.ContactCsvScreen
import com.example.callruleblocker.ui.FeatureHubScreen
import com.example.callruleblocker.ui.RingtoneSettingsScreen
import com.example.callruleblocker.ui.NotificationSettingsScreen
import com.example.callruleblocker.ui.SupplementaryServicesScreen
import com.example.callruleblocker.ui.ReportGeneratorScreen
import com.example.callruleblocker.ui.OfflineCallScreen
import com.example.callruleblocker.ui.CallRecordingsScreen
import com.example.callruleblocker.ui.RecycleBinScreen
import com.example.callruleblocker.ui.PermissionGateScreen
import com.example.callruleblocker.ui.hasAllRequiredPermissions
import com.example.callruleblocker.data.RuleRepository
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import com.example.callruleblocker.call.CallStateController
import com.example.callruleblocker.call.MainCallType
import com.example.callruleblocker.call.GlobalCallState
import com.example.callruleblocker.data.DiscoveryWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.work.*

class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_START_SEARCH = "start_search"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Log.d("ShynaCall", "MainActivity onCreate started")

        setContent {
            val auth = remember { 
                try { 
                    FirebaseAuth.getInstance().also { Log.d("ShynaCall", "Auth Instance Created") }
                } catch(err: Exception) { 
                    Log.e("ShynaCall", "Auth Init Error", err)
                    null 
                } 
            }
            val user = remember(auth) { auth?.currentUser }

            CallRuleBlockerTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val repository = remember { RuleRepository(context) }
                val rules by repository.observeAll().collectAsState(initial = emptyList())
                
                val processedCallIds = remember { mutableStateSetOf<String>() }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("AUTH_CHECK") }
                    var selectedReportType by remember { mutableStateOf("MONTHLY") }
                    val primaryFeature by CallStateController.primaryFeature.collectAsState()
                    val globalState by CallStateController.globalState.collectAsState()

                    // Back Button Rule during calling: Prevent accidental exit during an active conversation
                    BackHandler(enabled = globalState == GlobalCallState.ACTIVE || globalState == GlobalCallState.CONNECTING) {
                        Log.d("ShynaCall", "Back blocked during active session: $globalState")
                    }

                    when (currentScreen) {
                        "AUTH_CHECK" -> {
                            val prefs = remember { context.getSharedPreferences("call_settings", Context.MODE_PRIVATE) }
                            val pinEnabled = prefs.getBoolean("app_lock_pin", false)
                            val biometricEnabled = prefs.getBoolean("app_lock_biometric", false)
                            
                            Log.d("ShynaCall", "AUTH_CHECK: pin=$pinEnabled bio=$biometricEnabled")
                            
                            if (pinEnabled || biometricEnabled) {
                                AppLockScreen(
                                    onUnlocked = { 
                                        Log.d("ShynaCall", "Unlocked successfully")
                                        currentScreen = if (hasAllRequiredPermissions(context)) "PHONE_HOME" else "PERMISSIONS"
                                    }
                                )
                            } else {
                                LaunchedEffect(Unit) { 
                                    Log.d("ShynaCall", "No lock, checking permissions")
                                    currentScreen = if (hasAllRequiredPermissions(context)) "PHONE_HOME" else "PERMISSIONS"
                                }
                            }
                        }
                        "PERMISSIONS" -> {
                            PermissionGateScreen(
                                onPermissionsGranted = { 
                                    Log.d("ShynaCall", "Permissions granted, going HOME")
                                    currentScreen = "PHONE_HOME" 
                                }
                            )
                        }
                        "SHYNA_LINK" -> {
                            SmartCommunicationScreen(
                                onBack = { 
                                    CallStateController.setPrimaryFeature(MainCallType.PHONE_DIALER)
                                    currentScreen = "PHONE_HOME"
                                }
                            )
                        }
                        "RULES" -> {
                            RuleListScreen(
                                rules = rules,
                                onAddRule = { currentScreen = "ADD_RULE" },
                                onDeleteRule = { scope.launch { repository.deleteRule(it) } },
                                onToggleRule = { scope.launch { repository.updateRule(it.copy(enabled = !it.enabled)) } },
                                onBack = { currentScreen = "PHONE_HOME" }
                            )
                        }
                        "ADD_RULE" -> {
                            AddRuleScreen(
                                onSave = { scope.launch { repository.addRule(it) }; currentScreen = "RULES" },
                                onCancel = { currentScreen = "RULES" }
                            )
                        }
                        "SETTINGS" -> {
                            RecordingSettingsScreen(
                                onBack = { currentScreen = "PHONE_HOME" },
                                onOpenCsv = { currentScreen = "CSV" },
                                onOpenFeatureHub = { currentScreen = "FEATURE_HUB" },
                                onOpenRules = { currentScreen = "RULES" },
                                onOpenRingtone = { currentScreen = "RINGTONE" },
                                onOpenNotifications = { currentScreen = "NOTIFICATIONS" },
                                onOpenRecycleBin = { currentScreen = "RECYCLE_BIN" },
                                onOpenSupplementaryServices = { currentScreen = "SUPPLEMENTARY" },
                                onThemeChanged = {}
                            )
                        }
                        "CSV" -> {
                            ContactCsvScreen(
                                onBack = { currentScreen = "SETTINGS" }
                            )
                        }
                        "FEATURE_HUB" -> {
                            FeatureHubScreen(
                                onBack = { currentScreen = "SETTINGS" },
                                onOpenRules = { currentScreen = "RULES" },
                                onOpenSettings = { currentScreen = "SETTINGS" },
                                onOpenReport = { type -> selectedReportType = type; currentScreen = "REPORT" },
                                onThemeChanged = {}
                            )
                        }
                        "RINGTONE" -> {
                            RingtoneSettingsScreen(
                                onBack = { currentScreen = "SETTINGS" }
                            )
                        }
                        "NOTIFICATIONS" -> {
                            NotificationSettingsScreen(
                                onBack = { currentScreen = "SETTINGS" }
                            )
                        }
                        "SUPPLEMENTARY" -> {
                            SupplementaryServicesScreen(
                                onBack = { currentScreen = "SETTINGS" }
                            )
                        }
                        "REPORT" -> {
                            ReportGeneratorScreen(
                                reportType = selectedReportType,
                                onBack = { currentScreen = "FEATURE_HUB" }
                            )
                        }
                        "PHONE_HOME" -> {
                            PhoneHomeScreen(
                                onOpenRules = { currentScreen = "RULES" },
                                onOpenSettings = { currentScreen = "SETTINGS" },
                                onOpenRecycleBin = { currentScreen = "RECYCLE_BIN" },
                                onOpenOfflineCall = { 
                                    CallStateController.setPrimaryFeature(MainCallType.OFFLINE_CALL)
                                    currentScreen = "OFFLINE_CALL"
                                },
                                onOpenOnlineCall = { 
                                    CallStateController.setPrimaryFeature(MainCallType.SHYNA_LINK)
                                    currentScreen = "SHYNA_LINK"
                                },
                                onOpenRecordings = { currentScreen = "RECORDINGS" },
                                onCall = { number, sim -> com.example.callruleblocker.call.SimCallManager.placeCall(context, number, sim) }
                            )
                        }
                        "RECORDINGS" -> {
                            CallRecordingsScreen(
                                onBack = { currentScreen = "PHONE_HOME" }
                            )
                        }
                        "RECYCLE_BIN" -> {
                            RecycleBinScreen(
                                repository = repository,
                                onBack = { currentScreen = "PHONE_HOME" }
                            )
                        }
                        "OFFLINE_CALL" -> {
                            OfflineCallScreen(
                                onBack = { 
                                    CallStateController.setPrimaryFeature(MainCallType.PHONE_DIALER)
                                    currentScreen = "PHONE_HOME" 
                                }
                            )
                        }
                        else -> {
                            // Default entry based on primary feature
                            LaunchedEffect(primaryFeature) {
                                currentScreen = when(primaryFeature) {
                                    MainCallType.PHONE_DIALER -> "PHONE_HOME"
                                    MainCallType.SHYNA_LINK -> "SHYNA_LINK"
                                    MainCallType.OFFLINE_CALL -> "OFFLINE_CALL"
                                }
                            }
                        }
                    }

                    // Global feature listener to handle menu switches
                    LaunchedEffect(primaryFeature) {
                        val target = when(primaryFeature) {
                            MainCallType.PHONE_DIALER -> "PHONE_HOME"
                            MainCallType.SHYNA_LINK -> "SHYNA_LINK"
                            MainCallType.OFFLINE_CALL -> "OFFLINE_CALL"
                        }
                        if (currentScreen != target && currentScreen in setOf("PHONE_HOME", "SHYNA_LINK", "OFFLINE_CALL")) {
                            currentScreen = target
                        }
                    }
                }
                
                // ASYNC LISTENERS & TOKEN SYNC (SAFE WRAPPER)
                LaunchedEffect(user) {
                    if (user != null) {
                        // Background Discovery (No UI blocking)
                        try {
                            val workRequest = OneTimeWorkRequestBuilder<DiscoveryWorker>()
                                .setInputData(workDataOf("uid" to user.uid))
                                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                .build()
                            WorkManager.getInstance(context).enqueueUniqueWork("discovery_${user.uid}", ExistingWorkPolicy.KEEP, workRequest)
                        } catch (e: Exception) {
                            Log.e("ShynaCall", "WorkManager enqueue failed", e)
                        }

                        // SAVE FCM TOKEN FOR CALL NOTIFICATIONS
                        try {
                            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val fcmToken = task.result
                                    FirebaseFirestore.getInstance().collection("users").document(user.uid)
                                        .set(mapOf("fcmToken" to fcmToken), SetOptions.merge())
                                        .addOnSuccessListener { Log.d("ShynaCall", "FCM_TOKEN_SYNCED") }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ShynaCall", "FCM Token sync failed on start", e)
                        }

                        // APP-TO-APP CALL LISTENER (Real-time Signaling)
                        try {
                            com.example.callruleblocker.call.CallSignalingManager.listenForIncomingCalls(user.uid) { call ->
                                val activeSession = CallStateController.activeSession.value
                                val currentState = CallStateController.globalState.value
                                
                                // IDEMPOTENT LAUNCH LOGIC:
                                val isSameCall = activeSession?.callId == call.id
                                
                                if (isSameCall && currentState != GlobalCallState.IDLE) {
                                    Log.d("ShynaCall", "Firestore: Call ${call.id} already handling ($currentState). Skipping launch.")
                                    return@listenForIncomingCalls
                                }

                                val isTrulyBusy = currentState != GlobalCallState.IDLE && currentState != GlobalCallState.ENDED && !isSameCall

                                if (isTrulyBusy) {
                                    Log.d("ShynaCall", "User Truly Busy: Auto-rejecting new incoming call ${call.id} (Current: ${activeSession?.callId} State: $currentState)")
                                    com.example.callruleblocker.call.CallSignalingManager.updateCallStatus(call.id, com.example.callruleblocker.call.AppCallStatus.DECLINED, "user_busy_firestore")
                                } else {
                                    // Prevent rapid re-launch loops for the SAME call
                                    if (processedCallIds.contains(call.id)) {
                                        Log.d("ShynaCall", "Call ${call.id} already processed, skipping Firestore launch.")
                                        return@listenForIncomingCalls
                                    }
                                    
                                    processedCallIds.add(call.id)
                                    scope.launch {
                                        delay(8000) // 8s safety window
                                        processedCallIds.remove(call.id)
                                    }

                                    Log.d("ShynaCall", "LAUNCHING_CALL_ACTIVITY: id=${call.id} isSameCall=$isSameCall")
                                    CallStateController.reportCallEvent(
                                        MainCallType.SHYNA_LINK,
                                        GlobalCallState.INCOMING,
                                        call.id,
                                        call.callerUid
                                    )
                                    val intent = Intent(this@MainActivity, AppCallActivity::class.java).apply {
                                        putExtra("callId", call.id)
                                        putExtra("isIncoming", true)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    startActivity(intent)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ShynaCall", "Call listener failed on start", e)
                        }
                    }
                }

                // --- ROBUST PRESENCE SYSTEM ---
                val presenceUser = user
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                if (presenceUser != null) {
                    DisposableEffect(presenceUser.uid, lifecycleOwner) {
                        val presenceRef = FirebaseFirestore.getInstance().collection("users").document(presenceUser.uid)
                        var isForeground = true
                        
                        fun syncPresence() {
                            val isNetworkAvailable = com.example.callruleblocker.data.NetworkDetector.isWifi(context) || 
                                                     com.example.callruleblocker.data.NetworkDetector.isMobile(context)
                            
                            val status = isForeground && isNetworkAvailable
                            presenceRef.update("isOnline", status, "lastSeen", com.google.firebase.Timestamp.now())
                        }

                        // Monitor Lifecycle
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            when (event) {
                                androidx.lifecycle.Lifecycle.Event.ON_START -> { isForeground = true; syncPresence() }
                                androidx.lifecycle.Lifecycle.Event.ON_STOP -> { isForeground = false; syncPresence() }
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        
                        // Monitor Network Changes
                        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                        val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: android.net.Network) { syncPresence() }
                            override fun onLost(network: android.net.Network) { syncPresence() }
                        }
                        cm.registerDefaultNetworkCallback(networkCallback)
                        
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            cm.unregisterNetworkCallback(networkCallback)
                            // Final safety check: Go Offline
                            presenceRef.update("isOnline", false, "lastSeen", com.google.firebase.Timestamp.now())
                        }
                    }
                }
            }
        }
    }
}
