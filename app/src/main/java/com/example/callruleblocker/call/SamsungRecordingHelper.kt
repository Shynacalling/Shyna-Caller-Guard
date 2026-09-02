package com.example.callruleblocker.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * Helper to manage audio routing during call recording, especially for Samsung devices.
 * On many Samsung devices, call recording for third-party apps only captures both
 * sides acoustically if the speakerphone is active.
 */
object SamsungRecordingHelper {

    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.contains("samsung", ignoreCase = true)
    }

    /**
     * Attempts to ensure the audio route is optimized for recording.
     * Returns true if speakerphone was enabled.
     */
    fun ensureOptimalRoute(context: Context, enableSpeaker: Boolean): Boolean {
        if (!isSamsungDevice()) return false

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        return try {
            if (enableSpeaker) {
                audioManager.isSpeakerphoneOn = true
                true
            } else {
                audioManager.isSpeakerphoneOn = false
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the device has a built-in speaker.
     */
    fun hasSpeaker(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
        } else {
            true
        }
    }

    /**
     * Scans standard Samsung and app-specific directories for call recordings matching a number.
     */
    fun findRecordings(context: Context, number: String): List<File> {
        val normalized = number.filter(Char::isDigit).takeLast(10)
        val files = mutableListOf<File>()
        
        // 1. App internal folder
        context.getExternalFilesDir(null)?.let { base ->
            File(base, "CallRecordings").listFiles()?.filter { it.isFile && it.name.contains(normalized) }?.let { files.addAll(it) }
        }

        // 2. Standard Samsung/Android External paths (Requires Permission)
        val externalBase = Environment.getExternalStorageDirectory()
        val commonPaths = listOf(
            "Call", "Recordings", "Recordings/Call", "Call/CallRecordings",
            "Android/data/com.samsung.android.app.telephonyui"
        )
        
        commonPaths.forEach { path ->
            val dir = File(externalBase, path)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter { 
                    it.isFile && (it.name.contains(normalized) || it.name.contains(number)) && 
                    (it.extension.equals("m4a", true) || it.extension.equals("mp4", true) || it.extension.equals("amr", true))
                }?.let { files.addAll(it) }
            }
        }
        
        return files.sortedByDescending { it.lastModified() }
    }
}
