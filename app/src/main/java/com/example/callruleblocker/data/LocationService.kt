package com.example.callruleblocker.data

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.R
import android.content.pm.PackageManager
import com.example.callruleblocker.MainActivity
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var serviceExpiryTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                val now = System.currentTimeMillis()

                if (serviceExpiryTime > 0 && now >= serviceExpiryTime) {
                    Log.d("ShynaLocation", "Live location duration expired. Stopping service.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return
                }

                FirebaseFirestore.getInstance().collection("live_locations")
                    .document(uid)
                    .set(mapOf(
                        "senderId" to uid,
                        "lat" to location.latitude,
                        "lng" to location.longitude,
                        "accuracy" to location.accuracy,
                        "timestamp" to now,
                        "expiresAt" to serviceExpiryTime,
                        "isActive" to (serviceExpiryTime == 0L || now < serviceExpiryTime)
                    ))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_LIVE_LOCATION") {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (!uid.isNullOrBlank()) {
                FirebaseFirestore.getInstance().collection("live_locations")
                    .document(uid)
                    .update("isActive", false, "endedAt", System.currentTimeMillis())
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        
        val expiry = intent?.getLongExtra("expiryTime", 0L) ?: 0L
        if (expiry > 0) {
            serviceExpiryTime = expiry
            getSharedPreferences("location_service_prefs", Context.MODE_PRIVATE)
                .edit().putLong("expiryTime", expiry).apply()
        } else {
            serviceExpiryTime = getSharedPreferences("location_service_prefs", Context.MODE_PRIVATE)
                .getLong("expiryTime", 0L)
        }

        if (serviceExpiryTime > 0 && System.currentTimeMillis() >= serviceExpiryTime) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val stopIntent = Intent(this, LocationService::class.java).apply { action = "STOP_LIVE_LOCATION" }
        val pendingStop = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "location_channel")
            .setContentTitle("Sharing Live Location")
            .setContentText("Your live position is being updated in real-time.")
            .setSmallIcon(R.drawable.ic_menu_mylocation)
            .addAction(R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
        startLocationUpdates()
        
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMaxUpdateDelayMillis(10000L)
            .build()
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.requestLocationUpdates(request, locationCallback, android.os.Looper.getMainLooper())
            } else {
                Log.e("ShynaLocation", "Location permission not granted for service")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e("ShynaLocation", "Failed to request location updates", e)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "location_channel", "Location Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
