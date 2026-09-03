package com.example.callruleblocker.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callruleblocker.data.LocationService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewLiveLocationScreen(
    peerId: String,
    peerName: String,
    expiryTime: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val isSender = peerId == currentUid

    var livePos by remember { mutableStateOf<LatLng?>(null) }
    var lastUpdated by remember { mutableLongStateOf(0L) }
    var isSessionActive by remember { mutableStateOf(true) }
    val design = ShynaDesign.colors

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 15f)
    }

    DisposableEffect(peerId) {
        val docRef = db.collection("live_locations").document(peerId)
        val registration = docRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val lat = snapshot.getDouble("lat") ?: 0.0
                val lng = snapshot.getDouble("lng") ?: 0.0
                val ts = snapshot.getLong("timestamp") ?: 0L
                val active = snapshot.getBoolean("isActive") ?: true
                val newPos = LatLng(lat, lng)
                livePos = newPos
                lastUpdated = ts
                isSessionActive = active
                
                // Auto-center on first update
                if (cameraPositionState.position.target.latitude == 0.0 && lat != 0.0) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(newPos, 16f)
                }
            }
        }
        onDispose { registration.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isSender) "Your Live Location" else "$peerName's Live Location", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val timeStr = remember(lastUpdated) {
                            if (lastUpdated > 0) {
                                "Updated " + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastUpdated))
                            } else "Waiting for update..."
                        }
                        Text(timeStr, fontSize = 12.sp, color = design.BrandGreen)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = design.HeaderBg)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = true)
            ) {
                livePos?.let { pos ->
                    Marker(
                        state = rememberMarkerState(position = pos),
                        title = if (isSender) "You" else peerName,
                        snippet = "Live Location"
                    )
                }
            }

            // Bottom Action & Status Card
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = design.SurfaceBg,
                shadowElevation = 8.dp
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val now = System.currentTimeMillis()
                    val remaining = expiryTime - now
                    val isExpired = remaining <= 0 || !isSessionActive

                    if (!isExpired) {
                        val min = (remaining / 60000).coerceAtLeast(1)
                        val hrs = min / 60
                        val durationStr = if (hrs > 0) "$hrs hr ${min % 60} min remaining" else "$min min remaining"
                        Text(durationStr, fontWeight = FontWeight.Bold, color = design.BrandGreen, fontSize = 16.sp)
                    } else {
                        Text("Live location sharing ended", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                livePos?.let { pos ->
                                    try {
                                        val uri = Uri.parse("geo:${pos.latitude},${pos.longitude}?q=${pos.latitude},${pos.longitude}(Live Location)")
                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                            setPackage("com.google.android.apps.maps")
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        val fallbackUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${pos.latitude},${pos.longitude}")
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri)) }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = design.HeaderBg),
                            enabled = livePos != null
                        ) {
                            Icon(Icons.Default.Map, null, modifier = Modifier.size(18.dp), tint = design.TextPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Google Maps", color = design.TextPrimary)
                        }

                        if (isSender && !isExpired) {
                            Button(
                                onClick = {
                                    val stopIntent = Intent(context, LocationService::class.java).apply {
                                        action = "STOP_LIVE_LOCATION"
                                    }
                                    context.startService(stopIntent)
                                    Toast.makeText(context, "Live location stopped", Toast.LENGTH_SHORT).show()
                                    onBack()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop Sharing", color = Color.White)
                            }
                        } else {
                            Button(
                                onClick = {
                                    livePos?.let {
                                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(it, 17f))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = design.BrandGreen),
                                enabled = livePos != null
                            ) {
                                Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Recenter")
                            }
                        }
                    }
                }
            }
        }
    }
}
