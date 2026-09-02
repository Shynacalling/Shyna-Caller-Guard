package com.example.callruleblocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
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
    val db = FirebaseFirestore.getInstance()
    var livePos by remember { mutableStateOf<LatLng?>(null) }
    var lastUpdated by remember { mutableLongStateOf(0L) }
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
                val newPos = LatLng(lat, lng)
                livePos = newPos
                lastUpdated = ts
                
                // Auto-center on first update
                if (cameraPositionState.position.target.latitude == 0.0) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(newPos, 15f)
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
                        Text("$peerName's Live Location", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                        title = peerName,
                        snippet = "Live Location"
                    )
                }
            }

            // Status Card
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = design.SurfaceBg,
                shadowElevation = 8.dp
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val now = System.currentTimeMillis()
                    val remaining = expiryTime - now
                    if (remaining > 0) {
                        val min = remaining / 60000
                        Text("Sharing for another $min minutes", fontWeight = FontWeight.Bold, color = design.TextPrimary)
                    } else {
                        Text("Live location shared has expired", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            livePos?.let {
                                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(it, 17f))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = design.BrandGreen)
                    ) {
                        Text("Center on $peerName")
                    }
                }
            }
        }
    }
}
