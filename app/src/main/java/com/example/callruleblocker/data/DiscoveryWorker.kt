package com.example.callruleblocker.data

import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Backend worker to capture discovery metadata (District, State, Country) 
 * without blocking or crashing the UI.
 */
class DiscoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.failure()
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        val db = FirebaseFirestore.getInstance()

        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                
                val location = try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                } catch (_: Exception) {
                    null
                }
                
                if (location != null) {
                    val geocoder = Geocoder(applicationContext, Locale.getDefault())
                    val addresses = try {
                        geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    } catch (err: Exception) {
                        Log.e("ShynaDiscovery", "Geocoding failed", err)
                        null
                    }
                    
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses.firstOrNull()
                        if (address != null) {
                            val data = mapOf(
                                "district" to (address.subAdminArea ?: address.locality ?: address.subLocality ?: "Unknown"),
                                "pincode" to (address.postalCode ?: ""),
                                "state" to (address.adminArea ?: ""),
                                "country" to (address.countryName ?: "")
                            )
                            if (uid.isNotBlank()) {
                                db.collection("users").document(uid).update(data).await()
                                Log.d("ShynaDiscovery", "Discovery Base Updated Successfully for $uid")
                                return androidx.work.ListenableWorker.Result.success()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ShynaDiscovery", "Background discovery capture failed", e)
        }
        
        return Result.retry()
    }
}
