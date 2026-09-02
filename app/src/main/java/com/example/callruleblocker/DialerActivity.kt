package com.example.callruleblocker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.example.callruleblocker.ui.theme.CallRuleBlockerTheme
import com.example.callruleblocker.call.SimCallManager
import com.example.callruleblocker.MainActivity

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.example.callruleblocker.ui.AppLockScreen

/**
 * Handles ACTION_DIAL / ACTION_VIEW(tel:) — the outgoing-call side of
 * being a default Phone app. Deliberately minimal: a number field and a
 * Call button. The system still places the call through normal Telecom;
 * this app doesn't need its own calling logic.
 */
class DialerActivity : FragmentActivity() {

    private val requestCallPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) pendingNumber?.let { placeCall(it, pendingSimIndex) } }

    private var pendingNumber: String? = null
    private var pendingSimIndex: Int? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefillNumber = intent?.data?.schemeSpecificPart

        setContent {
            CallRuleBlockerTheme {
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("call_settings", Context.MODE_PRIVATE) }
                var isLocked by remember { 
                    mutableStateOf(prefs.getBoolean("app_lock_pin", false) || prefs.getBoolean("app_lock_biometric", false)) 
                }

                if (isLocked) {
                    AppLockScreen(onUnlocked = { isLocked = false })
                } else {
                    Scaffold(
                        containerColor = Color.Black,
                        topBar = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .height(72.dp)
                                    .padding(horizontal = 22.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Phone",
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                IconButton(onClick = {
                                    val intent = Intent(this@DialerActivity, MainActivity::class.java).apply {
                                        putExtra(MainActivity.EXTRA_START_SEARCH, true)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                    startActivity(intent)
                                    finish()
                                }) {
                                    Icon(
                                        Icons.Outlined.Search, 
                                        "Search", 
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            com.example.callruleblocker.ui.KeypadScreen(
                                prefill = prefillNumber,
                                onCall = { number, simIndex -> requestCallPermissionAndCall(number, simIndex) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestCallPermissionAndCall(number: String, simIndex: Int?) {
        pendingNumber = number
        pendingSimIndex = simIndex
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            placeCall(number, simIndex)
        } else {
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun placeCall(number: String, simIndex: Int?) {
        SimCallManager.placeCall(this, number, simIndex)
        finish()
    }
}
