package com.example.callruleblocker.call

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallAudioBlockedException(message: String) : Exception(message)

/**
 * Best-effort call audio recorder for a normal third-party Android dialer.
 *
 * Android does not grant third-party apps CAPTURE_AUDIO_OUTPUT, therefore direct
 * VOICE_CALL / VOICE_UPLINK / VOICE_DOWNLINK capture is intentionally not used.
 * We instead try supported microphone-class sources and let speaker-assisted
 * routing (owned by CallActivity/Telecom) acoustically capture both sides where
 * the device allows it.
 */
class CallRecorder(private val context: Context) {

    enum class ExpectedQuality { GOOD_BOTH_SIDES, MIC_ONLY_LIKELY, UNKNOWN }

    private var recorder: MediaRecorder? = null
    private var startedAtMs: Long = 0L
    private var peakAmplitude: Int = 0
    private var amplitudeSamples: Int = 0

    var outputFile: File? = null
        private set

    var audioSourceUsed: Int = MediaRecorder.AudioSource.MIC
        private set

    var isSpeakerOn: Boolean = false

    fun expectedQuality(): ExpectedQuality =
        if (!isRecording()) ExpectedQuality.UNKNOWN
        else if (isSpeakerOn) ExpectedQuality.GOOD_BOTH_SIDES
        else ExpectedQuality.MIC_ONLY_LIKELY

    fun start(number: String = ""): Result<File> = runCatching {
        check(recorder == null) { "Recording is already running" }

        val baseDir = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("Recording storage is unavailable")
        val folder = File(baseDir, "CallRecordings")
        check(folder.exists() || folder.mkdirs()) { "Cannot create recording folder" }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val key = number.filter(Char::isDigit).takeLast(10).ifBlank { "unknown" }
        val file = File(folder, "SCG_${key}_$stamp.m4a")
        outputFile = file

        val sources = buildList {
            // MIC is the most broadly supported choice while a cellular call owns telephony audio.
            add(MediaRecorder.AudioSource.MIC)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.CAMCORDER)
            add(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        }.distinct()

        var lastError: Throwable? = null
        for (source in sources) {
            deletePartial(file)
            val candidate = newRecorder()
            val attempt = runCatching {
                candidate.apply {
                    setAudioSource(source)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioChannels(1)
                    setAudioEncodingBitRate(128_000)
                    setAudioSamplingRate(44_100)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
            }

            if (attempt.isSuccess) {
                recorder = candidate
                audioSourceUsed = source
                startedAtMs = System.currentTimeMillis()
                peakAmplitude = 0
                amplitudeSamples = 0
                return@runCatching file
            }

            lastError = attempt.exceptionOrNull()
            releaseCandidate(candidate)
        }

        throw lastError ?: IllegalStateException("No supported microphone source could start")
    }.onFailure {
        releaseQuietly(deleteFile = true)
    }

    fun stop(): Result<File> = runCatching {
        val active = recorder ?: throw IllegalStateException("Recording is not running")
        val file = outputFile ?: throw IllegalStateException("Recording file is unavailable")
        val elapsed = System.currentTimeMillis() - startedAtMs
        val capturedAmplitudeSamples = amplitudeSamples
        val capturedPeakAmplitude = peakAmplitude

        // MediaRecorder.stop() can throw RuntimeException on short/invalid OEM streams.
        // Always release cleanly and reject the partial file instead of crashing the app.
        val stopError = runCatching { active.stop() }.exceptionOrNull()
        releaseQuietly(deleteFile = false)
        if (stopError != null) {
            deletePartial(file)
            throw IllegalStateException("Recording could not be finalized on this device", stopError)
        }

        check(elapsed >= 500L) { "Recording was too short" }
        check(file.exists() && file.length() >= 512L) { "No audio file was captured" }

        val prefs = context.getSharedPreferences("recording_settings", Context.MODE_PRIVATE)
        val professionalBypass = prefs.getBoolean("professional_bypass", false)
        // Lowered threshold to 5 to avoid false negatives on quiet microphones.
        if (!professionalBypass && (capturedAmplitudeSamples < 1 || capturedPeakAmplitude < 5)) {
            deletePartial(file)
            throw CallAudioBlockedException("Audio capture was blocked or silent; recording removed")
        }

        file
    }.onFailure {
        releaseQuietly(deleteFile = true)
    }

    fun isRecording(): Boolean = recorder != null

    fun currentAmplitude(): Int {
        val active = recorder ?: return 0
        val value = runCatching { active.maxAmplitude }.getOrDefault(0)
        amplitudeSamples++
        if (value > peakAmplitude) peakAmplitude = value
        return value
    }

    private fun newRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }

    private fun releaseCandidate(candidate: MediaRecorder) {
        runCatching { candidate.reset() }
        runCatching { candidate.release() }
    }

    private fun deletePartial(file: File) {
        runCatching { if (file.exists()) file.delete() }
    }

    private fun releaseQuietly(deleteFile: Boolean) {
        recorder?.let(::releaseCandidate)
        recorder = null
        startedAtMs = 0L
        peakAmplitude = 0
        amplitudeSamples = 0
        outputFile?.let { file ->
            if (deleteFile || (file.exists() && file.length() == 0L)) deletePartial(file)
        }
        if (deleteFile) outputFile = null
    }
}
