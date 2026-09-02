package com.example.callruleblocker.data

import android.util.Log
import com.example.callruleblocker.ui.UserStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

data class LinkPreviewData(
    val url: String,
    val title: String?,
    val description: String?,
    val domain: String?,
    val imageUrl: String?
)

object StatusBackendManager {
    private const val TAG = "StatusBackendManager"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Safe Link Preview Generator with SSRF Protection:
     * Rejects local/private IP addresses (127.0.0.1, 10.x, 172.16-31.x, 192.168.x, 169.254.x, localhost, etc.)
     */
    suspend fun fetchLinkPreview(rawUrl: String): LinkPreviewData? = withContext(Dispatchers.IO) {
        try {
            var formattedUrl = rawUrl.trim()
            if (!formattedUrl.startsWith("http://", ignoreCase = true) &&
                !formattedUrl.startsWith("https://", ignoreCase = true)
            ) {
                formattedUrl = "https://$formattedUrl"
            }

            val uri = URI(formattedUrl)
            val host = uri.host ?: return@withContext null

            // SSRF Check: Resolve IP and block private/loopback addresses
            val addresses = InetAddress.getAllByName(host)
            for (addr in addresses) {
                if (addr.isLoopbackAddress ||
                    addr.isSiteLocalAddress ||
                    addr.isAnyLocalAddress ||
                    addr.isLinkLocalAddress ||
                    addr.isMulticastAddress
                ) {
                    Log.w(TAG, "Blocked SSRF attempt to private address: $host ($addr)")
                    return@withContext null
                }
            }

            val domain = host.removePrefix("www.")
            val request = Request.Builder()
                .url(formattedUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/119.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val html = response.body?.string() ?: ""

            // Extract OpenGraph / HTML metadata
            val title = extractOgTag(html, "og:title") ?: extractTitleTag(html)
            val description = extractOgTag(html, "og:description") ?: extractMetaDescription(html)
            val image = extractOgTag(html, "og:image")

            LinkPreviewData(
                url = formattedUrl,
                title = title,
                description = description,
                domain = domain,
                imageUrl = image
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching link preview for $rawUrl: ${e.message}")
            null
        }
    }

    private fun extractOgTag(html: String, property: String): String? {
        val pattern = Regex("<meta\\s+property=[\"']$property[\"']\\s+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val match = pattern.find(html) ?: run {
            val altPattern = Regex("<meta\\s+content=[\"']([^\"']+)[\"']\\s+property=[\"']$property[\"']", RegexOption.IGNORE_CASE)
            altPattern.find(html)
        }
        return match?.groupValues?.get(1)?.trim()
    }

    private fun extractTitleTag(html: String): String? {
        val pattern = Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE)
        return pattern.find(html)?.groupValues?.get(1)?.trim()
    }

    private fun extractMetaDescription(html: String): String? {
        val pattern = Regex("<meta\\s+name=[\"']description[\"']\\s+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        return pattern.find(html)?.groupValues?.get(1)?.trim()
    }

    /**
     * Server View Receipt sync to Firestore `statuses/{statusId}`
     */
    suspend fun recordServerView(statusId: String, viewerUid: String, viewerName: String) = withContext(Dispatchers.IO) {
        if (statusId.isBlank() || viewerUid.isBlank()) return@withContext
        try {
            val db = FirebaseFirestore.getInstance()
            val statusRef = db.collection("statuses").document(statusId)

            // Add viewerUid to seenBy array atomically
            statusRef.update("seenBy", FieldValue.arrayUnion(viewerUid)).await()

            // Record view detail in subcollection
            val viewRef = statusRef.collection("views").document(viewerUid)
            val viewData = mapOf(
                "viewerUid" to viewerUid,
                "viewerName" to viewerName,
                "firstViewedAt" to System.currentTimeMillis(),
                "lastViewedAt" to System.currentTimeMillis()
            )
            viewRef.set(viewData).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record server view for status $statusId: ${e.message}")
        }
    }

    /**
     * Like / Reaction toggle on status
     */
    suspend fun toggleStatusLike(statusId: String, viewerUid: String, isLiked: Boolean) = withContext(Dispatchers.IO) {
        if (statusId.isBlank() || viewerUid.isBlank()) return@withContext
        try {
            val db = FirebaseFirestore.getInstance()
            val statusRef = db.collection("statuses").document(statusId)

            if (isLiked) {
                statusRef.update("likesBy", FieldValue.arrayUnion(viewerUid)).await()
                statusRef.collection("likes").document(viewerUid).set(
                    mapOf("viewerUid" to viewerUid, "createdAt" to System.currentTimeMillis())
                ).await()
            } else {
                statusRef.update("likesBy", FieldValue.arrayRemove(viewerUid)).await()
                statusRef.collection("likes").document(viewerUid).delete().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle like for status $statusId: ${e.message}")
        }
    }

    /**
     * Delete active status
     */
    suspend fun deleteStatus(statusId: String, ownerUid: String): Boolean = withContext(Dispatchers.IO) {
        if (statusId.isBlank() || ownerUid.isBlank()) return@withContext false
        val currentAuthUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentAuthUid != ownerUid) {
            Log.e(TAG, "Unauthorized delete attempt: $currentAuthUid vs $ownerUid")
            return@withContext false
        }
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("statuses").document(statusId).delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete status $statusId: ${e.message}")
            false
        }
    }
}
