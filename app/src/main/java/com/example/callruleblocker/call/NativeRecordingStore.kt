package com.example.callruleblocker.call

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Read-only discovery of call recordings already created by the phone/OEM dialer.
 *
 * This intentionally uses public Android storage APIs only. It does not try to access
 * protected telephony audio or private app folders. App-owned recordings are included
 * directly; shared/OEM recordings are read through MediaStore when permission is granted.
 */
object NativeRecordingStore {

    data class RecordingEntry(
        val stableId: String,
        val displayName: String,
        val modifiedAt: Long,
        val sizeBytes: Long,
        val file: File? = null,
        val uri: Uri? = null,
        val nativeRecording: Boolean = false
    )

    fun mediaReadPermission(): String? = when {
        Build.VERSION.SDK_INT >= 33 -> Manifest.permission.READ_MEDIA_AUDIO
        Build.VERSION.SDK_INT >= 23 -> Manifest.permission.READ_EXTERNAL_STORAGE
        else -> null
    }

    fun hasMediaReadPermission(context: Context): Boolean {
        val permission = mediaReadPermission() ?: return true
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun scan(context: Context, includeSharedMedia: Boolean = hasMediaReadPermission(context)): List<RecordingEntry> {
        val found = LinkedHashMap<String, RecordingEntry>()

        // Always include Shyna's own recordings; this does not need shared-storage permission.
        context.getExternalFilesDir(null)?.let { base ->
            File(base, "CallRecordings").listFiles()
                ?.asSequence()
                ?.filter { it.isFile && it.length() > 0L && isSupportedName(it.name) }
                ?.forEach { file ->
                    found["file:${file.absolutePath}"] = RecordingEntry(
                        stableId = "file:${file.absolutePath}",
                        displayName = file.name,
                        modifiedAt = file.lastModified(),
                        sizeBytes = file.length(),
                        file = file,
                        nativeRecording = false
                    )
                }
        }

        if (includeSharedMedia) {
            val nativeEntries = queryAudioCollection(context) + queryFilesCollection(context)
            nativeEntries.forEach { entry ->
                val duplicate = found.values.any { existing -> samePhysicalRecording(existing, entry) }
                if (!duplicate) found[entry.stableId] = entry
            }
        }

        return found.values.sortedByDescending { it.modifiedAt }
    }

    private fun queryAudioCollection(context: Context): List<RecordingEntry> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        return queryCollection(context, collection)
    }

    private fun queryFilesCollection(context: Context): List<RecordingEntry> {
        val collection = MediaStore.Files.getContentUri("external")
        return queryCollection(context, collection)
    }

    private fun queryCollection(context: Context, collection: Uri): List<RecordingEntry> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.MediaColumns.RELATIVE_PATH)
        }.toTypedArray()

        return runCatching {
            val result = mutableListOf<RecordingEntry>()
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val modifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val addedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val pathIndex = if (Build.VERSION.SDK_INT >= 29) cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val relativePath = if (pathIndex >= 0) cursor.getString(pathIndex).orEmpty() else ""
                    val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""
                    if (!isLikelyCallRecording(name, relativePath, mime)) continue

                    val id = cursor.getLong(idIndex)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val modifiedSeconds = if (modifiedIndex >= 0) cursor.getLong(modifiedIndex) else 0L
                    val addedSeconds = if (addedIndex >= 0) cursor.getLong(addedIndex) else 0L
                    val timestamp = maxOf(modifiedSeconds, addedSeconds) * 1000L
                    val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    if (size == 0L) continue

                    result += RecordingEntry(
                        stableId = "uri:$uri",
                        displayName = name,
                        modifiedAt = timestamp,
                        sizeBytes = size,
                        uri = uri,
                        nativeRecording = true
                    )
                }
            }
            result
        }.getOrDefault(emptyList())
    }

    private fun samePhysicalRecording(a: RecordingEntry, b: RecordingEntry): Boolean {
        if (!a.displayName.equals(b.displayName, ignoreCase = true)) return false
        if (a.sizeBytes > 0L && b.sizeBytes > 0L && a.sizeBytes != b.sizeBytes) return false
        return kotlin.math.abs(a.modifiedAt - b.modifiedAt) <= 2_000L
    }

    private fun isLikelyCallRecording(name: String, relativePath: String, mime: String): Boolean {
        if (!isSupportedName(name)) return false
        val n = name.lowercase()
        val p = relativePath.replace('\\', '/').lowercase()
        val m = mime.lowercase()

        val knownName = n.startsWith("call ") || n.startsWith("call_") || n.startsWith("call-") ||
            n.contains("call recording") || n.contains("callrecording") || n.contains("phone call")
        val knownPath = p.contains("recordings/call") || p.contains("call/record") ||
            p.endsWith("/call/") || p.contains("callrecordings") || p.contains("call recording")
        val mediaLooksAudio = m.startsWith("audio/") || m == "video/mp4" || m == "application/mp4" || m.isBlank()

        return mediaLooksAudio && (knownName || knownPath)
    }

    private fun isSupportedName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".m4a") || n.endsWith(".mp4") || n.endsWith(".3gp") ||
            n.endsWith(".amr") || n.endsWith(".aac") || n.endsWith(".wav") ||
            n.endsWith(".ogg") || n.endsWith(".m4a.mp4")
    }
}
