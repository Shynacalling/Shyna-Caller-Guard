package com.example.callruleblocker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.callruleblocker.data.LocationService
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

private const val TAG = "ShynaLocation"

data class ShynaPlace(
    val id: String,
    val name: String,
    val address: String,
    val latLng: LatLng
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendLocationScreen(onBack: () -> Unit, onSendLocation: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val placesClient = remember { Places.createClient(context) }
    val design = ShynaDesign.colors

    var isFullscreen by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ShynaPlace>>(emptyList()) }
    
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var accuracy by remember { mutableFloatStateOf(0f) }
    var isLocating by remember { mutableStateOf(true) }
    
    var nearbyPlaces by remember { mutableStateOf<List<ShynaPlace>>(emptyList()) }
    var selectedPlace by remember { mutableStateOf<ShynaPlace?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }
    var showLiveLocationDialog by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 4f)
    }

    val mapStyle = remember(design.isDark) {
        if (design.isDark) {
            MapStyleOptions("""
                [
                  { "elementType": "geometry", "stylers": [{ "color": "#242f3e" }] },
                  { "elementType": "labels.text.fill", "stylers": [{ "color": "#746855" }] },
                  { "elementType": "labels.text.stroke", "stylers": [{ "color": "#242f3e" }] },
                  { "featureType": "administrative.locality", "elementType": "labels.text.fill", "stylers": [{ "color": "#d59563" }] },
                  { "featureType": "poi", "elementType": "labels.text.fill", "stylers": [{ "color": "#d59563" }] },
                  { "featureType": "poi.park", "elementType": "geometry", "stylers": [{ "color": "#263c3f" }] },
                  { "featureType": "poi.park", "elementType": "labels.text.fill", "stylers": [{ "color": "#6b9a76" }] },
                  { "featureType": "road", "elementType": "geometry", "stylers": [{ "color": "#38414e" }] },
                  { "featureType": "road", "elementType": "geometry.stroke", "stylers": [{ "color": "#212a37" }] },
                  { "featureType": "road", "elementType": "labels.text.fill", "stylers": [{ "color": "#9ca5b3" }] },
                  { "featureType": "road.highway", "elementType": "geometry", "stylers": [{ "color": "#746855" }] },
                  { "featureType": "road.highway", "elementType": "geometry.stroke", "stylers": [{ "color": "#1f2835" }] },
                  { "featureType": "road.highway", "elementType": "labels.text.fill", "stylers": [{ "color": "#f3d19c" }] },
                  { "featureType": "transit", "elementType": "geometry", "stylers": [{ "color": "#2f3948" }] },
                  { "featureType": "transit.station", "elementType": "labels.text.fill", "stylers": [{ "color": "#d59563" }] },
                  { "featureType": "water", "elementType": "geometry", "stylers": [{ "color": "#17263c" }] },
                  { "featureType": "water", "elementType": "labels.text.fill", "stylers": [{ "color": "#515c6d" }] },
                  { "featureType": "water", "elementType": "labels.text.stroke", "stylers": [{ "color": "#17263c" }] }
                ]
            """.trimIndent())
        } else null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            isLocating = true
        } else {
            Toast.makeText(context, "Fine location permission is required for better accuracy", Toast.LENGTH_LONG).show()
        }
    }

    fun fetchNearbyPlaces() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val fields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)
        val request = FindCurrentPlaceRequest.newInstance(fields)

        scope.launch(Dispatchers.IO) {
            try {
                placesClient.findCurrentPlace(request).addOnSuccessListener { response ->
                    val list = response.placeLikelihoods
                        .sortedByDescending { it.likelihood }
                        .take(15)
                        .map { likelihood ->
                            val place = likelihood.place
                            ShynaPlace(
                                id = place.id ?: "",
                                name = place.displayName ?: "Unknown",
                                address = place.formattedAddress ?: "Nearby Place",
                                latLng = place.location ?: LatLng(0.0, 0.0)
                            )
                        }
                    nearbyPlaces = list
                }.addOnFailureListener {
                    Log.e(TAG, "FindCurrentPlace failed, trying fallback search", it)
                    currentLocation?.let { loc ->
                        val bias = RectangularBounds.newInstance(
                            LatLng(loc.latitude - 0.005, loc.longitude - 0.005),
                            LatLng(loc.latitude + 0.005, loc.longitude + 0.005)
                        )
                        val autoRequest = FindAutocompletePredictionsRequest.builder()
                            .setQuery("points of interest") 
                            .setLocationBias(bias)
                            .build()
                        placesClient.findAutocompletePredictions(autoRequest).addOnSuccessListener { autoRes ->
                             val fallbackList = autoRes.autocompletePredictions.take(10).map {
                                 ShynaPlace(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString(), LatLng(loc.latitude, loc.longitude))
                             }
                             nearbyPlaces = fallbackList
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Nearby places failed", e)
            }
        }
    }

    fun updateLocation(location: Location) {
        val oldLocation = currentLocation
        currentLocation = location
        accuracy = location.accuracy
        isLocating = false
        val latLng = LatLng(location.latitude, location.longitude)
        
        // Initial zoom or if far from last or if accuracy improved significantly
        val distance = oldLocation?.distanceTo(location) ?: 100f
        val accuracyImproved = (oldLocation?.accuracy ?: 100f) - accuracy > 5f

        if (cameraPositionState.position.target.latitude == 20.5937 || 
            (accuracy < 25 && cameraPositionState.position.zoom < 15f) ||
            (accuracy < 20 && distance > 10) ||
            accuracyImproved) {
            
            scope.launch {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
            }
            fetchNearbyPlaces()
        }
    }

    DisposableEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setDurationMillis(Long.MAX_VALUE)
                .setMaxUpdateDelayMillis(2000L)
                .setWaitForAccurateLocation(true)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { updateLocation(it) }
                }
            }
            fusedLocationClient.requestLocationUpdates(request, callback, context.mainLooper)
            onDispose { fusedLocationClient.removeLocationUpdates(callback) }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            onDispose {}
        }
    }

    fun requestFreshLocation() {
        isLocating = true
        scope.launch {
            try {
                val loc = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                loc?.let { updateLocation(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Location request failed", e)
                isLocating = false
            }
        }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) {
            searchResults = emptyList()
            return
        }
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .build()
        
        placesClient.findAutocompletePredictions(request).addOnSuccessListener { response ->
            searchResults = response.autocompletePredictions.map { p ->
                ShynaPlace(p.placeId, p.getPrimaryText(null).toString(), p.getSecondaryText(null).toString(), LatLng(0.0, 0.0))
            }
        }.addOnFailureListener {
            Log.e(TAG, "Search failed", it)
        }
    }

    fun selectPlace(shynaPlace: ShynaPlace) {
        if (shynaPlace.latLng.latitude == 0.0) {
            val fields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)
            val request = FetchPlaceRequest.newInstance(shynaPlace.id, fields)
            
            placesClient.fetchPlace(request).addOnSuccessListener { response ->
                val place = response.place
                place.location?.let { latLng ->
                    val finalPlace = shynaPlace.copy(latLng = latLng, address = place.formattedAddress ?: shynaPlace.address)
                    selectedPlace = finalPlace
                    isSearchMode = false
                    searchQuery = ""
                    searchResults = emptyList()
                    scope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
                    }
                }
            }
        } else {
            selectedPlace = shynaPlace
            isSearchMode = false
            searchQuery = ""
            searchResults = emptyList()
            scope.launch {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(shynaPlace.latLng, 18f))
            }
        }
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let { fetchNearbyPlaces() }
    }

    LaunchedEffect(Unit) {
        requestFreshLocation()
    }

    BackHandler(isSearchMode || isFullscreen) {
        if (isSearchMode) isSearchMode = false
        else if (isFullscreen) isFullscreen = false
    }

    if (showLiveLocationDialog) {
        LiveLocationDurationPicker(
            onDismiss = { showLiveLocationDialog = false },
            onSelect = { durationMs ->
                showLiveLocationDialog = false
                val expiry = System.currentTimeMillis() + durationMs
                
                // Start Foreground Location Service with expiry time
                val serviceIntent = Intent(context, LocationService::class.java).apply {
                    putExtra("expiryTime", expiry)
                }
                runCatching {
                    ContextCompat.startForegroundService(context, serviceIntent)
                }

                onSendLocation("LIVE|${expiry}")
                onBack()
            }
        )
    }

    Scaffold(
        containerColor = design.PrimaryBg,
        topBar = {
            if (!isSearchMode) {
                TopAppBar(
                    title = { Text("Send location", color = design.TextPrimary, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = design.TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchMode = true }) {
                            Icon(Icons.Default.Search, "Search", tint = design.TextPrimary)
                        }
                        IconButton(onClick = { requestFreshLocation() }) {
                            Icon(Icons.Default.Refresh, "Refresh", tint = design.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = design.HeaderBg)
                )
            } else {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { 
                                searchQuery = it
                                performSearch(it)
                            },
                            placeholder = { Text("Search places...", color = design.TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = design.TextPrimary,
                                unfocusedTextColor = design.TextPrimary,
                                cursorColor = design.BrandGreen
                            ),
                            singleLine = true
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { isSearchMode = false; searchQuery = ""; searchResults = emptyList() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel", tint = design.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = design.HeaderBg)
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(if (isFullscreen) 1f else 0.45f)
                ) {
                    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val mapProperties = remember(hasPermission, isMapLoaded, mapStyle) {
                        MapProperties(
                            mapStyleOptions = mapStyle,
                            isMyLocationEnabled = hasPermission,
                            isBuildingEnabled = true,
                            isIndoorEnabled = true,
                            mapType = MapType.NORMAL
                        )
                    }
                    val mapUiSettings = remember {
                        MapUiSettings(
                            zoomControlsEnabled = false,
                            myLocationButtonEnabled = false,
                            compassEnabled = true,
                            mapToolbarEnabled = true
                        )
                    }

                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        onMapLoaded = { isMapLoaded = true },
                        properties = mapProperties,
                        uiSettings = mapUiSettings
                    ) {
                        selectedPlace?.let { p ->
                            Marker(
                                state = rememberMarkerState(position = p.latLng),
                                title = p.name,
                                snippet = p.address
                            )
                        }
                    }

                    Row(
                        Modifier.align(Alignment.TopStart).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = design.SurfaceBg,
                            onClick = { isFullscreen = !isFullscreen },
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, 
                                    null, tint = design.TextPrimary
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(54.dp),
                        shape = CircleShape,
                        color = design.SurfaceBg,
                        shadowElevation = 6.dp,
                        onClick = { requestFreshLocation() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isLocating || !isMapLoaded) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = design.BrandGreen, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.MyLocation, null, tint = design.BrandGreen, modifier = Modifier.size(28.dp))
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = design.HeaderBg.copy(alpha = 0.8f)
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isLocating) {
                                Text("Finding your location...", color = design.TextPrimary, fontSize = 12.sp)
                            } else if (accuracy > 0) {
                                val color = if (accuracy <= 15) design.BrandGreen else Color.Yellow
                                Text("Accurate to ${accuracy.toInt()} meters", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Waiting for GPS satellites...", color = design.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (!isFullscreen) {
                    Box(Modifier.weight(0.55f).background(design.PrimaryBg)) {
                        if (isSearchMode) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(searchResults) { p ->
                                    PlaceRow(p.name, p.address) { selectPlace(p) }
                                }
                                if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                                    item {
                                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                            Text("No places found", color = design.TextSecondary)
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                                item {
                                    LocationActionItem(
                                        title = "Share live location",
                                        subtitle = "Continuously update your position",
                                        icon = Icons.Default.WifiTethering,
                                        iconBg = if(design.isDark) Color.White else Color.Black,
                                        iconTint = if(design.isDark) Color.Black else Color.White
                                    ) {
                                        showLiveLocationDialog = true
                                    }
                                }
                                
                                item {
                                    val isReady = currentLocation != null
                                    val subtitleText = if (currentLocation != null) {
                                        if (accuracy > 0) "Accurate to ${accuracy.toInt()}m • Tap to send"
                                        else "Tap to send current location"
                                    } else {
                                        "Acquiring GPS location..."
                                    }
                                    LocationActionItem(
                                        title = "Send your current location",
                                        subtitle = subtitleText,
                                        icon = Icons.Default.MyLocation,
                                        iconBg = design.HeaderBg,
                                        iconTint = if (isReady) design.BrandGreen else design.TextSecondary,
                                        isHighlighted = isReady
                                    ) {
                                        val fix = currentLocation
                                        val now = System.currentTimeMillis()
                                        // A fix is acceptable if it's less than 30s old and accurate to 30m
                                        val isAcceptable = fix != null && (now - fix.time < 30000) && fix.accuracy <= 30f
                                        
                                        if (isAcceptable && fix != null) {
                                            onSendLocation("${fix.latitude},${fix.longitude}|${fix.accuracy.toInt()}")
                                            onBack()
                                        } else {
                                            requestFreshLocation()
                                            Toast.makeText(context, "Acquiring fresh high-accuracy location...", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }

                                item {
                                    Text("Nearby places", color = design.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                                }

                                items(nearbyPlaces) { p ->
                                    PlaceRow(p.name, p.address) { selectPlace(p) }
                                }
                                
                                if (nearbyPlaces.isEmpty()) {
                                    item {
                                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                            Text("No nearby places found", color = design.TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (selectedPlace != null && !isSearchMode) {
                FloatingActionButton(
                    onClick = {
                        val p = selectedPlace!!
                        onSendLocation("${p.latLng.latitude},${p.latLng.longitude}|0|${p.name}")
                        onBack()
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    containerColor = design.BrandGreen,
                    contentColor = if(design.isDark) Color.Black else Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, "Send Place")
                }
            }
        }
    }
}

@Composable
private fun LiveLocationDurationPicker(onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    val options = listOf(
        "15 Minutes" to 15 * 60 * 1000L,
        "30 Minutes" to 30 * 60 * 1000L,
        "1 Hour" to 60 * 60 * 1000L,
        "2 Hours" to 2 * 60 * 60 * 1000L,
        "4 Hours" to 4 * 60 * 60 * 1000L,
        "8 Hours" to 8 * 60 * 60 * 1000L,
        "24 Hours" to 24 * 60 * 60 * 1000L
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Live Location", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Participants will see your location in real-time. This feature requires background location access.", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                options.forEach { (label, duration) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        modifier = Modifier.clickable { onSelect(duration) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = ShynaDesign.colors.BrandGreen) }
        },
        containerColor = ShynaDesign.colors.SurfaceBg
    )
}

@Composable
private fun LocationActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val design = ShynaDesign.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = iconBg,
            border = if (isHighlighted) BorderStroke(2.dp, design.BrandGreen) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = design.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = design.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PlaceRow(name: String, address: String, onClick: () -> Unit) {
    val design = ShynaDesign.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.size(40.dp), shape = CircleShape, color = design.DividerColor) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LocationOn, null, tint = design.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(name, color = design.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(address, color = design.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
