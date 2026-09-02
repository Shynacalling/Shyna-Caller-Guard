package com.example.callruleblocker.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.callruleblocker.ui.MessageType
import com.example.callruleblocker.ui.UserStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class StatusUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val statusId = inputData.getString("statusId") ?: return@withContext Result.failure()
        val userId = inputData.getString("userId") ?: return@withContext Result.failure()
        val userName = inputData.getString("userName") ?: "User"
        val userPhoto = inputData.getString("userPhoto")
        val filePath = inputData.getString("filePath")
        val caption = inputData.getString("caption")
        val typeStr = inputData.getString("type") ?: "IMAGE"
        val bgColor = inputData.getString("backgroundColor")
        val textColor = inputData.getString("textColor")
        val text = inputData.getString("text")
        val fontId = inputData.getString("fontId")
        val linkUrl = inputData.getString("linkUrl")
        val privacyMode = inputData.getString("privacyMode") ?: "MY_CONTACTS"
        val durationMs = inputData.getLong("durationMs", 5000L)

        val type = try { MessageType.valueOf(typeStr) } catch (_: Exception) { MessageType.IMAGE }

        var mediaUrl = ""
        var thumbnailUrl: String? = null

        // 1. Upload local media file or content URI if present
        if (!filePath.isNullOrBlank()) {
            val tempFile = prepareTempFile(applicationContext, filePath)
            val uploadPath = tempFile?.absolutePath ?: filePath

            val uploadedUrl = uploadToCloudinary(uploadPath)
            
            // Clean up temporary cache file if we created one
            tempFile?.delete()

            if (uploadedUrl.isNullOrBlank()) {
                Log.e(TAG, "Status media upload failed for path: $filePath")
                return@withContext Result.retry()
            }
            mediaUrl = uploadedUrl
            if (type == MessageType.VIDEO) {
                thumbnailUrl = uploadedUrl.replace(Regex("\\.[a-zA-Z0-9]+$"), ".jpg")
            }
        }

        // 2. Fetch Link Preview if this is a LINK status
        var linkTitle: String? = null
        var linkDesc: String? = null
        var linkDomain: String? = null
        var linkImg: String? = null

        if (type == MessageType.LINK && !linkUrl.isNullOrBlank()) {
            val preview = StatusBackendManager.fetchLinkPreview(linkUrl)
            if (preview != null) {
                linkTitle = preview.title
                linkDesc = preview.description
                linkDomain = preview.domain
                linkImg = preview.imageUrl
            }
        }

        // 3. Construct authoritative UserStatus object
        val now = System.currentTimeMillis()
        val status = UserStatus(
            id = statusId,
            userId = userId,
            userName = userName,
            userPhoto = userPhoto,
            mediaUrl = mediaUrl,
            thumbnailUrl = thumbnailUrl,
            caption = caption,
            type = type,
            backgroundColor = bgColor,
            textColor = textColor,
            text = text,
            fontId = fontId,
            linkUrl = linkUrl,
            linkTitle = linkTitle,
            linkDescription = linkDesc,
            linkDomain = linkDomain,
            linkImageUrl = linkImg,
            durationMs = durationMs,
            timestamp = now,
            expiresAt = now + 86400000L, // 24 hours trusted lifetime
            privacyMode = privacyMode,
            uploadState = "PUBLISHED"
        )

        // 4. Save to Firestore `statuses` collection
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("statuses").document(statusId).set(status).await()
            Log.d(TAG, "Status $statusId published successfully with mediaUrl: $mediaUrl")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write status to Firestore: ${e.message}", e)
            Result.retry()
        }
    }

    private fun prepareTempFile(context: Context, pathOrUriStr: String): File? {
        return try {
            if (pathOrUriStr.startsWith("content://") || pathOrUriStr.startsWith("file://")) {
                val uri = Uri.parse(pathOrUriStr)
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val ext = if (pathOrUriStr.contains("video")) ".mp4" else ".jpg"
                val tempFile = File(context.cacheDir, "status_upload_${System.currentTimeMillis()}$ext")
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                tempFile
            } else {
                val file = File(pathOrUriStr)
                if (file.exists()) file else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing file for upload: ${e.message}", e)
            null
        }
    }

    private suspend fun uploadToCloudinary(filePath: String): String? = suspendCancellableCoroutine { continuation ->
        try {
            MediaManager.get().upload(filePath)
                .unsigned(CloudinaryConfig.UPLOAD_PRESET)
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val secureUrl = resultData["secure_url"] as? String
                        if (continuation.isActive) {
                            continuation.resume(secureUrl)
                        }
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        Log.e(TAG, "Cloudinary upload error: ${error.description}")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                })
                .dispatch()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Cloudinary upload: ${e.message}", e)
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }

    companion object {
        private const val TAG = "StatusUploadWorker"
    }
}
