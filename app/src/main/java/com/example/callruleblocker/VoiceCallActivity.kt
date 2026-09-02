package com.example.callruleblocker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.callruleblocker.call.SimCallManager
import com.example.callruleblocker.ui.theme.CallRuleBlockerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Voice/deep-link entry point.
 * Supported examples:
 *   shyna://call?number=9876543210
 *   shyna://call?name=Ravi
 *   Intent ACTION_CALL/ACTION_DIAL with a tel: URI
 *   extras: contact_name, phone_number, query
 *
 * The speech-to-text step is intentionally owned by the launching assistant
 * (Bixby/Gemini/Android voice action). This activity safely resolves the
 * delivered name/number and hands the final call to Android Telecom.
 */
class VoiceCallActivity : ComponentActivity() {
    private var pendingCallNumber: String? = null

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val number = pendingCallNumber
        pendingCallNumber = null
        if (granted && !number.isNullOrBlank()) {
            launchCallSafely(number)
        } else if (!granted) {
            Toast.makeText(this, "Phone permission is required to place the call", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallRuleBlockerTheme {
                VoiceCallScreen(
                    rawQuery = extractQuery(),
                    onCall = ::requestOrPlaceCall,
                ) { finish() }
            }
        }
    }

    private fun requestOrPlaceCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            launchCallSafely(number)
        } else {
            pendingCallNumber = number
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun launchCallSafely(number: String) {
        runCatching {
            SimCallManager.placeCall(this, number, null)
        }.onSuccess {
            Toast.makeText(this, "Calling…", Toast.LENGTH_SHORT).show()
            finish()
        }.onFailure {
            Toast.makeText(this, "Unable to start call. Check Phone app and SIM permissions.", Toast.LENGTH_LONG).show()
        }
    }

    private fun extractQuery(): String {
        val data = intent?.data
        return when (data?.scheme) {
            "tel" -> data.schemeSpecificPart.orEmpty()
            "shyna" -> data.getQueryParameter("number")
                ?: data.getQueryParameter("name")
                ?: data.getQueryParameter("query")
                ?: ""
            else -> intent?.getStringExtra("phone_number")
                ?: intent?.getStringExtra("contact_name")
                ?: intent?.getStringExtra("query")
                ?: intent?.getStringExtra(android.app.SearchManager.QUERY)
                ?: intent?.getStringExtra(android.content.Intent.EXTRA_PHONE_NUMBER)
                ?: intent?.getStringExtra("android.intent.extra.NAME")
                ?: intent?.getCharSequenceExtra(android.content.Intent.EXTRA_PROCESS_TEXT)?.toString()
                ?: intent?.getStringExtra(android.content.Intent.EXTRA_TEXT)
                ?: ""
        }.trim()
    }
}

@Composable
private fun VoiceCallScreen(rawQuery: String, onCall: (String) -> Unit, onCancel: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var state by remember(rawQuery) { mutableStateOf<VoiceResolution>(VoiceResolution.Loading) }
    var permissionRevision by remember { mutableIntStateOf(0) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionRevision++ }

    LaunchedEffect(rawQuery, permissionRevision) {
        state = VoiceResolution.Loading
        state = resolveVoiceQuery(context, rawQuery)
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VoicePulse(active = state is VoiceResolution.Loading)
                    Column {
                        Text("Voice call", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = normalizedVoiceQuery(rawQuery).ifBlank { "Waiting for a name or number" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedContent(targetState = state, label = "voice-resolution") { current ->
                    when (current) {
                        VoiceResolution.Loading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Finding the best contact match…")
                            }
                        }
                        VoiceResolution.ContactsPermissionRequired -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Contacts access is needed to find a person by name.")
                                Button(onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                                    Icon(Icons.Outlined.Contacts, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Allow contacts")
                                }
                                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                            }
                        }
                        is VoiceResolution.Found -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(current.name ?: current.number, style = MaterialTheme.typography.titleLarge)
                                if (current.name != null) Text(current.number)
                                Text("Ready to call using Shyna Caller Guard")
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = { onCall(current.number) }) {
                                        Icon(Icons.Outlined.Call, contentDescription = null)
                                        Spacer(Modifier.width(7.dp))
                                        Text("Call")
                                    }
                                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                                }
                            }
                        }
                        is VoiceResolution.Multiple -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("More than one contact matched ‘${current.query}’. Choose the correct number:")
                                current.matches.take(5).forEach { match ->
                                    OutlinedButton(
                                        onClick = { onCall(match.second) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Outlined.Call, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("${match.first}  •  ${match.second}")
                                    }
                                }
                                TextButton(onClick = onCancel) { Text("Close") }
                            }
                        }
                        is VoiceResolution.NotFound -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("No contact or valid number found for ‘${current.query}’.")
                                Text("Try the full contact name or say the phone number.", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = onCancel) { Text("Close") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoicePulse(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "voice-pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.16f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice-pulse-scale"
    )
    Surface(
        modifier = Modifier.size(46.dp).scale(scale),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.RecordVoiceOver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private sealed interface VoiceResolution {
    data object Loading : VoiceResolution
    data object ContactsPermissionRequired : VoiceResolution
    data class Found(val name: String?, val number: String) : VoiceResolution
    data class Multiple(val query: String, val matches: List<Pair<String, String>>) : VoiceResolution
    data class NotFound(val query: String) : VoiceResolution
}

private fun normalizedVoiceQuery(query: String): String {
    var value = query.trim().replace(Regex("\\s+"), " ")
    value = value.replace(Regex("^(call|dial|phone|ring)\\s+", RegexOption.IGNORE_CASE), "")
    value = value.replace(Regex("\\s+(please|now)$", RegexOption.IGNORE_CASE), "")
    return value.trim()
}

private suspend fun resolveVoiceQuery(context: Context, query: String): VoiceResolution = withContext(Dispatchers.IO) {
    val cleaned = normalizedVoiceQuery(query)
    val digits = cleaned.filter { (it.isDigit()) || (it == '+') }
    if (digits.count(Char::isDigit) >= 3 && cleaned.all { it.isDigit() || it in "+ -()" }) {
        return@withContext VoiceResolution.Found(null, digits)
    }
    if (cleaned.isBlank()) {
        return@withContext VoiceResolution.NotFound(cleaned)
    }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return@withContext VoiceResolution.ContactsPermissionRequired
    }

    val matches = mutableListOf<Pair<String, String>>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
    val args = arrayOf("%$cleaned%")

    try {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            args,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && matches.size < 30) {
                val name = cursor.getString(nameIndex).orEmpty().trim()
                val number = cursor.getString(numberIndex).orEmpty().trim()
                if (name.isNotBlank() && number.isNotBlank()) matches += name to number
            }
        }
    } catch (_: Exception) {
        return@withContext VoiceResolution.NotFound(cleaned)
    }

    val unique = matches.distinctBy { (name, number) -> "${name.lowercase()}|${number.filter(Char::isDigit)}" }
    val exact = unique.filter { it.first.equals(cleaned, ignoreCase = true) }
    when {
        exact.size == 1 -> VoiceResolution.Found(exact.first().first, exact.first().second)
        unique.size == 1 -> VoiceResolution.Found(unique.first().first, unique.first().second)
        exact.size > 1 -> VoiceResolution.Multiple(cleaned, exact)
        unique.isNotEmpty() -> VoiceResolution.Multiple(cleaned, unique)
        else -> VoiceResolution.NotFound(cleaned)
    }
}
