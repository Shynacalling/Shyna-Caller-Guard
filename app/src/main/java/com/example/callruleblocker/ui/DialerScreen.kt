package com.example.callruleblocker.ui

import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.callruleblocker.AppCallActivity
import com.example.callruleblocker.call.AppCallType
import com.example.callruleblocker.call.CallSignalingManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var number by remember { mutableStateOf("") }
    val db = remember { FirebaseFirestore.getInstance() }
    
    var registeredUser by remember { mutableStateOf<RealUser?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    fun playHaptic() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    LaunchedEffect(number) {
        if (number.length >= 10) {
            isChecking = true
            val normalized = number.filter { it.isDigit() }.takeLast(10)
            db.collection("users")
                .whereEqualTo("phone", normalized)
                .limit(1)
                .get()
                .addOnSuccessListener { sn ->
                    registeredUser = sn.documents.firstOrNull()?.toObject(RealUser::class.java)
                    isChecking = false
                }
                .addOnFailureListener {
                    isChecking = false
                    registeredUser = null
                }
        } else {
            registeredUser = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dialer", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Number Display
            Text(
                text = number.ifEmpty { "Enter Number" },
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = if(number.isEmpty()) ShynaDesign.colors.TextSecondary else ShynaDesign.colors.TextPrimary,
                modifier = Modifier.padding(vertical = 32.dp)
            )

            // User Preview
            Box(Modifier.height(80.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (isChecking) {
                    CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen)
                } else if (registeredUser != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(ShynaDesign.colors.BrandGreen.copy(0.1f), RoundedCornerShape(40.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Surface(Modifier.size(40.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                            if (!registeredUser!!.photoUrl.isNullOrBlank()) {
                                AsyncImage(registeredUser!!.photoUrl, null)
                            } else {
                                Icon(Icons.Default.Person, null, modifier = Modifier.padding(8.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(registeredUser!!.name, color = ShynaDesign.colors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                } else if (number.length >= 10) {
                    Text("Not registered on Shyna", color = Color.Red, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Dialpad Grid
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
            val labels = listOf("", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "+", "")
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(keys) { index, key ->
                    DialButton(key, labels[index]) {
                        playHaptic()
                        if (number.length < 15) number += key
                    }
                }
            }

            // Bottom Actions
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { 
                    if (number.isNotEmpty()) {
                        playHaptic()
                        number = number.dropLast(1)
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.size(32.dp))
                }

                FloatingActionButton(
                    onClick = {
                        if (registeredUser != null) {
                            CallSignalingManager.startCall(context, registeredUser!!.uid, AppCallType.VOICE, { created ->
                                context.startActivity(Intent(context, AppCallActivity::class.java).apply {
                                    putExtra("callId", created.id)
                                    putExtra("isIncoming", false)
                                })
                            }, { e -> Toast.makeText(context, "Unable to start voice call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() })
                        } else if (number.length >= 10) {
                            Toast.makeText(context, "This number is not registered", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = ShynaDesign.colors.BrandGreen,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(32.dp))
                }

                IconButton(onClick = {
                    if (registeredUser != null) {
                        CallSignalingManager.startCall(context, registeredUser!!.uid, AppCallType.VIDEO, { created ->
                            context.startActivity(Intent(context, AppCallActivity::class.java).apply {
                                putExtra("callId", created.id)
                                putExtra("isIncoming", false)
                            })
                        }, { e -> Toast.makeText(context, "Unable to start video call: ${e.message ?: "network/server error"}", Toast.LENGTH_LONG).show() })
                    } else {
                        Toast.makeText(context, "Video call only available for registered users", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.Videocam, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun DialButton(digit: String, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.aspectRatio(1.5f), // More compact
        shape = RoundedCornerShape(12.dp),
        color = ShynaDesign.colors.HeaderBg.copy(alpha = 0.8f),
        onClick = onClick
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(digit, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
            if (label.isNotEmpty()) {
                Text(label, fontSize = 9.sp, color = ShynaDesign.colors.TextSecondary, fontWeight = FontWeight.Normal)
            }
        }
    }
}
