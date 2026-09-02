package com.example.callruleblocker

import android.app.Application
import android.util.Log
import com.cloudinary.android.MediaManager
import com.example.callruleblocker.data.CloudinaryConfig
import android.app.ActivityManager
import android.os.Build
import android.os.Process

class ShynaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            Log.d("ShynaApplication", "Application onCreate started")
            
            // Initialize Firebase explicitly for multi-process safety
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
                Log.d("ShynaApplication", "Firebase Initialized in ${Process.myPid()}")
            }
            
            // Initialize Cloudinary safely (Main process only)
            if (isMainProcess()) {
                val config = mapOf(
                    "cloud_name" to CloudinaryConfig.CLOUD_NAME,
                    "secure" to true
                )
                
                try {
                    MediaManager.init(this, config)
                    Log.d("ShynaApplication", "Cloudinary Initialized Successfully")
                } catch (t: Throwable) {
                    Log.e("ShynaApplication", "Cloudinary Init Error: ${t.message}")
                }
            }

            // Initialize Google Places SDK
            if (!com.google.android.libraries.places.api.Places.isInitialized()) {
                com.google.android.libraries.places.api.Places.initialize(this, "AIzaSyCN4fFi1IDkR2BYmjybqn0bzuu598i-A9U")
            }
        } catch (e: Exception) {
            Log.e("ShynaApplication", "Global Application Crash Protected", e)
        }
    }

    private fun isMainProcess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName() == packageName
        }
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses
        if (runningProcesses != null) {
            val myPid = Process.myPid()
            for (processInfo in runningProcesses) {
                if (processInfo.pid == myPid) {
                    return processInfo.processName == packageName
                }
            }
        }
        return true
    }
}
