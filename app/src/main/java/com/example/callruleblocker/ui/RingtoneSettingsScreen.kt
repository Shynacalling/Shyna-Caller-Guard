package com.example.callruleblocker.ui

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.provider.Settings
import android.content.Intent
import android.widget.Toast
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CALL_PREFS = "call_settings"
private const val KEY_RINGTONE_URI = "ringtone_uri"

data class RingtoneOption(val title: String, val uri: Uri?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(CALL_PREFS, Context.MODE_PRIVATE) }
    var options by remember { mutableStateOf<List<RingtoneOption>>(emptyList()) }
    var selectedUri by remember { mutableStateOf(prefs.getString(KEY_RINGTONE_URI, null)) }
    var preview: Ringtone? by remember { mutableStateOf(null) }
    var previewUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { options = loadRingtones(context) }
    DisposableEffect(Unit) { onDispose { preview?.stop() } }

    fun stopPreview() {
        preview?.stop()
        preview = null
        previewUri = null
    }

    fun previewSelected() {
        val uri = selectedUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        if (previewUri == uri.toString() && preview?.isPlaying == true) {
            stopPreview()
        } else {
            stopPreview()
            runCatching {
                RingtoneManager.getRingtone(context, uri)?.also {
                    preview = it
                    previewUri = uri.toString()
                    it.play()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call ringtone", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = { stopPreview(); onBack() }) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = {
                        val chosen = selectedUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        prefs.edit().putString(KEY_RINGTONE_URI, selectedUri).apply()
                        if (Settings.System.canWrite(context)) {
                            runCatching {
                                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, chosen)
                            }.onFailure {
                                Toast.makeText(context, "Saved in Shyna; system ringtone could not be changed", Toast.LENGTH_LONG).show()
                            }
                            stopPreview()
                            onBack()
                        } else {
                            Toast.makeText(context, "Allow Modify system settings, then tap Save again", Toast.LENGTH_LONG).show()
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                            }
                        }
                    }) { Text("Save", fontWeight = FontWeight.SemiBold) }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.MusicNote, null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        options.firstOrNull { it.uri?.toString() == selectedUri }?.title ?: "Default ringtone",
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    FilledIconButton(onClick = { previewSelected() }) {
                        Icon(if (preview?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Preview")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                RingtoneRow(
                    title = "Default ringtone",
                    selected = selectedUri == null,
                    onClick = { selectedUri = null; stopPreview() }
                )
            }
            items(options, key = { it.uri?.toString().orEmpty() }) { option ->
                RingtoneRow(
                    title = option.title,
                    selected = selectedUri == option.uri?.toString(),
                    onClick = { selectedUri = option.uri?.toString(); stopPreview() }
                )
            }
        }
    }
}

@Composable
private fun RingtoneRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Text(title, modifier = Modifier.weight(1f), maxLines = 1)
        }
    }
}

private suspend fun loadRingtones(context: Context): List<RingtoneOption> = withContext(Dispatchers.IO) {
    val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_RINGTONE) }
    val result = mutableListOf<RingtoneOption>()
    manager.cursor.use { cursor ->
        while (cursor.moveToNext()) {
            val position = cursor.position
            val uri = manager.getRingtoneUri(position)
            val title = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
                ?: "Ringtone ${position + 1}"
            result += RingtoneOption(title, uri)
        }
    }
    result.distinctBy { it.uri?.toString() }
}
