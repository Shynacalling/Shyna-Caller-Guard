package com.example.callruleblocker.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import android.content.ContextWrapper

@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("call_settings", Context.MODE_PRIVATE) }
    val savedPin = prefs.getString("app_pin_code", "") ?: ""
    val pinEnabled = prefs.getBoolean("app_lock_pin", false)
    val biometricEnabled = prefs.getBoolean("app_lock_biometric", false)

    var enteredPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) {
            val activity = context.findFragmentActivity()
            if (activity != null) {
                showBiometricPrompt(activity) {
                    onUnlocked()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color.Black))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "App Locked",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Enter your PIN to continue",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
            
            Spacer(Modifier.height(48.dp))
            
            // PIN Dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { index ->
                    val active = enteredPin.length > index
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (active) Color(0xFF8B6DFF) else Color.White.copy(alpha = 0.1f))
                    )
                }
            }

            if (error) {
                Text(
                    "Invalid PIN",
                    color = Color.Red,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(Modifier.height(48.dp))

            // Num Pad
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { num ->
                            PinButton(num) {
                                if (enteredPin.length < 4) {
                                    enteredPin += num
                                    error = false
                                    if (enteredPin.length == 4) {
                                        if (enteredPin == savedPin) onUnlocked()
                                        else {
                                            enteredPin = ""
                                            error = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Biometric icon
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        if (biometricEnabled) {
                            IconButton(onClick = { 
                                val activity = context.findFragmentActivity()
                                if (activity != null) showBiometricPrompt(activity) { onUnlocked() }
                            }) {
                                Icon(Icons.Outlined.Fingerprint, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    PinButton("0") {
                        if (enteredPin.length < 4) {
                            enteredPin += "0"
                            error = false
                            if (enteredPin.length == 4) {
                                if (enteredPin == savedPin) onUnlocked()
                                else {
                                    enteredPin = ""
                                    error = true
                                }
                            }
                        }
                    }
                    // Backspace
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable { if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Backspace, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PinButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        color = Color.White.copy(alpha = 0.05f),
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
        }
    })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("App Unlocked")
        .setSubtitle("Authenticate to continue")
        .setNegativeButtonText("Use PIN")
        .build()

    biometricPrompt.authenticate(promptInfo)
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}
