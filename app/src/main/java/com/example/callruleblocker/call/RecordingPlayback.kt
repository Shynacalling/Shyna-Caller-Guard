package com.example.callruleblocker.call

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import java.io.File

/** Defensive MediaPlayer helpers for app-owned and MediaStore call recordings. */
object RecordingPlayback {
    fun openAndPlay(
        file: File,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ): Result<MediaPlayer> = runCatching {
        require(file.exists() && file.isFile && file.length() > 0L) { "Recording file is missing or empty" }
        createPlayer(onCompletion, onError).apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
    }

    fun openAndPlay(
        context: Context,
        uri: Uri,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ): Result<MediaPlayer> = runCatching {
        createPlayer(onCompletion, onError).apply {
            setDataSource(context, uri)
            prepare()
            start()
        }
    }

    private fun createPlayer(
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ): MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        setOnCompletionListener {
            onCompletion()
            runCatching { it.reset() }
            runCatching { it.release() }
        }
        setOnErrorListener { mp, _, _ ->
            onError("This recording cannot be played on this device")
            runCatching { mp.reset() }
            runCatching { mp.release() }
            true
        }
    }

    fun resume(player: MediaPlayer?): Boolean = runCatching {
        requireNotNull(player)
        player.start()
        true
    }.getOrDefault(false)

    fun pause(player: MediaPlayer?): Boolean = runCatching {
        requireNotNull(player)
        if (player.isPlaying) player.pause()
        true
    }.getOrDefault(false)

    fun stopAndRelease(player: MediaPlayer?) {
        if (player == null) return
        runCatching { if (player.isPlaying) player.stop() }
        runCatching { player.reset() }
        runCatching { player.release() }
    }
}
