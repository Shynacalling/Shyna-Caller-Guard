package com.example.callruleblocker.data

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlobPart
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.FileDataPart
import com.google.ai.client.generativeai.type.FunctionCallPart
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.TextPart
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

object GeminiManager {
    private const val TAG = "GeminiManager"
    private const val API_KEY = "AIzaSyCN4fFi1IDkR2BYmjybqn0bzuu598i-A9U" // From ShynaApplication

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = API_KEY
    )

    /**
     * Sanitizes a list of Gemini Content objects according to strict payload validation.
     * This is a production-level fix to prevent INVALID_ARGUMENT errors caused by empty parts.
     */
    fun sanitizeGeminiContents(contents: List<Content>?): List<Content> {
        if (contents == null) return emptyList()
        
        val sanitized = contents.mapNotNull { item ->
            val role = item.role ?: "user"
            // Strict check for valid roles
            if ((role != "user") && (role != "model") && (role != "function")) {
                Log.w(TAG, "Dropping content with invalid role: $role")
                return@mapNotNull null
            }

            val validParts = item.parts.filter { part ->
                when (part) {
                    is TextPart -> {
                        // Remove empty or whitespace-only text parts
                        part.text.trim().isNotEmpty()
                    }
                    is BlobPart -> {
                        // Ensure blob data is not empty
                        part.blob.isNotEmpty()
                    }
                    is FileDataPart -> true
                    is FunctionCallPart -> true
                    is FunctionResponsePart -> true
                    else -> {
                        // Remove any other invalid/null parts
                        false
                    }
                }
            }

            if (validParts.isEmpty()) {
                Log.w(TAG, "Removed invalid Gemini history item (empty parts): Role=$role")
                null
            } else {
                Content(role = role, parts = validParts)
            }
        }
        
        return sanitized
    }

    /**
     * Final validation immediately before API execution.
     */
    private fun validateBeforeRequest(contents: List<Content>) {
        if (contents.isEmpty()) {
            throw IllegalArgumentException("Gemini contents cannot be empty.")
        }
        contents.forEachIndexed { index, content ->
            if (content.parts.isEmpty()) {
                throw IllegalStateException("Invalid Gemini contents detected at index $index: parts array is empty.")
            }
        }
    }

    suspend fun generateSummary(audioFile: File): String = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext "Summary failed: Invalid or empty audio file."
        }
        try {
            val audioBytes = audioFile.readBytes()
            val rawContent = content("user") {
                blob("audio/mpeg", audioBytes)
                text("Summarize this call recording. Identify the caller, the main topic, and any action items.")
            }
            val sanitized = sanitizeGeminiContents(listOf(rawContent))
            
            validateBeforeRequest(sanitized)

            val response = model.generateContent(*sanitized.toTypedArray())
            val resText = response.text?.trim()
            if (resText.isNullOrEmpty()) "No summary generated." else resText
        } catch (e: Exception) {
            Log.e(TAG, "Gemini generateSummary error: ${e.message}", e)
            "Summary failed: ${e.message}"
        }
    }

    suspend fun askAi(query: String, history: List<Content> = emptyList()): String = withContext(Dispatchers.IO) {
        val trimmedPrompt = query.trim()
        if (trimmedPrompt.isEmpty()) {
            Log.w(TAG, "Rejected empty user prompt before calling Gemini.")
            return@withContext "Please enter a valid question or command."
        }

        try {
            val userContent = content("user") {
                text(trimmedPrompt)
            }
            val fullList = history + userContent
            val sanitized = sanitizeGeminiContents(fullList)

            validateBeforeRequest(sanitized)

            val response = model.generateContent(*sanitized.toTypedArray())
            val replyText = response.text?.trim()

            if (replyText.isNullOrEmpty()) {
                Log.w(TAG, "Model returned an empty response. Not saving to history.")
                "I'm sorry, I couldn't process that response."
            } else {
                replyText
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini askAi error: ${e.message}", e)
            "AI Error: ${e.message}"
        }
    }

    /**
     * Streaming version of askAi with the same sanitization and validation logic.
     */
    fun askAiStream(query: String, history: List<Content> = emptyList()): Flow<String> = flow {
        val trimmedPrompt = query.trim()
        if (trimmedPrompt.isEmpty()) {
            emit("Please enter a valid question.")
            return@flow
        }

        try {
            val userContent = content("user") {
                text(trimmedPrompt)
            }
            val fullList = history + userContent
            val sanitized = sanitizeGeminiContents(fullList)

            validateBeforeRequest(sanitized)

            model.generateContentStream(*sanitized.toTypedArray()).collect { response ->
                response.text?.trim()?.let { 
                    if (it.isNotEmpty()) emit(it)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini askAiStream error: ${e.message}", e)
            emit("AI Error: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
}
