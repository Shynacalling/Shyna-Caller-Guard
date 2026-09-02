package com.example.callruleblocker.ui

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.time.LocalDate

private data class CsvContact(val name: String, val phone: String, val email: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactCsvScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<List<CsvContact>>(emptyList()) }
    var status by remember { mutableStateOf("CSV columns: Name, Phone, Email") }
    var busy by remember { mutableStateOf(false) }
    var writeContactsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val writeContactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        writeContactsGranted = granted
        if (!granted) status = "Contacts write permission is required only for CSV import."
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { readCsv(context, uri) }
                .onSuccess { preview = it; status = "${it.size} contacts ready to import" }
                .onFailure { status = "Import read failed: ${it.message}" }
            busy = false
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { exportContacts(context, uri) }
                .onSuccess { count -> status = "$count contacts exported" }
                .onFailure { status = "Export failed: ${it.message}" }
            busy = false
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Contact CSV import/export", fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv")) }, enabled = !busy) {
                    Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.width(6.dp)); Text("Choose CSV")
                }
                OutlinedButton(onClick = { exportLauncher.launch("Shyna_Contacts_${LocalDate.now()}.csv") }, enabled = !busy) {
                    Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("Export")
                }
            }
            if (preview.isNotEmpty()) {
                Button(
                    onClick = {
                        if (!writeContactsGranted) {
                            writeContactsPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                        } else {
                            scope.launch {
                                busy = true
                                runCatching { importContacts(context, preview) }
                                    .onSuccess { result -> status = "Imported ${result.first}; skipped ${result.second} duplicates/invalid"; preview = emptyList() }
                                    .onFailure { status = "Import failed: ${it.message}" }
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (writeContactsGranted) "Import ${preview.size} contacts" else "Allow permission to import")
                }
                if (!writeContactsGranted) {
                    TextButton(onClick = { writeContactsPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS) }) {
                        Text("Grant Contacts write permission")
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    items(preview.take(200)) { item ->
                        ListItem(
                            headlineContent = { Text(item.name.ifBlank { "Unnamed" }) },
                            supportingContent = { Text(listOf(item.phone, item.email).filter(String::isNotBlank).joinToString(" · ")) }
                        )
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Supported standard pattern", fontWeight = FontWeight.SemiBold)
                        Text("Name,Phone,Email")
                        Text("Ravi Kumar,+919876543210,ravi@example.com")
                        Text("Quoted commas are supported. Existing phone numbers are skipped.")
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

private suspend fun readCsv(context: Context, uri: Uri): List<CsvContact> = withContext(Dispatchers.IO) {
    val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }.orEmpty()
    if (lines.isEmpty()) return@withContext emptyList()
    val rows = lines.map(::parseCsvLine).filter { it.isNotEmpty() }
    val header = rows.first().map { it.trim().lowercase() }
    val hasHeader = header.any { it in setOf("name", "phone", "number", "mobile", "email") }
    val nameIndex = header.indexOfFirst { it == "name" || it == "display name" }.takeIf { it >= 0 } ?: 0
    val phoneIndex = header.indexOfFirst { it in setOf("phone", "number", "mobile", "phone number") }.takeIf { it >= 0 } ?: 1
    val emailIndex = header.indexOfFirst { it == "email" || it == "email address" }.takeIf { it >= 0 }
    rows.drop(if (hasHeader) 1 else 0).mapNotNull { row ->
        val name = row.getOrNull(nameIndex).orEmpty().trim()
        val phone = row.getOrNull(phoneIndex).orEmpty().trim()
        val email = emailIndex?.let { row.getOrNull(it).orEmpty().trim() }.orEmpty()
        if (phone.filter(Char::isDigit).length < 3) null else CsvContact(name, phone, email)
    }.distinctBy { normalizePhone(it.phone) }
}

private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val value = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { value.append('"'); i++ }
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> { result += value.toString(); value.clear() }
            else -> value.append(c)
        }
        i++
    }
    result += value.toString()
    return result
}

private suspend fun exportContacts(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return@withContext 0
    }
    val rows = mutableListOf<CsvContact>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            val email = lookupEmail(context, id)
            rows += CsvContact(cursor.getString(nameIndex).orEmpty(), cursor.getString(phoneIndex).orEmpty(), email)
        }
    }
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
            writer.write("Name,Phone,Email\n")
            rows.distinctBy { normalizePhone(it.phone) }.forEach { row ->
                writer.write(listOf(row.name, row.phone, row.email).joinToString(",") { csvEscape(it) })
                writer.write("\n")
            }
        }
    } ?: throw java.io.IOException("Unable to open the selected export destination")
    rows.distinctBy { normalizePhone(it.phone) }.size
}

private fun lookupEmail(context: Context, contactId: Long): String {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return ""
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
        "${ContactsContract.CommonDataKinds.Email.CONTACT_ID}=?",
        arrayOf(contactId.toString()), null
    )?.use { cursor -> if (cursor.moveToFirst()) return cursor.getString(0).orEmpty() }
    return ""
}

private suspend fun importContacts(context: Context, contacts: List<CsvContact>): Pair<Int, Int> = withContext(Dispatchers.IO) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return@withContext 0 to contacts.size
    }
    val existing = existingPhoneSet(context)
    var imported = 0
    var skipped = 0
    contacts.forEach { contact ->
        val normalized = normalizePhone(contact.phone)
        if (normalized.isBlank() || normalized in existing) { skipped++; return@forEach }
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build()
        ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name.ifBlank { contact.phone })
            .build()
        ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, contact.phone)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            .build()
        if (contact.email.isNotBlank()) {
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, contact.email)
                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_OTHER)
                .build()
        }
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        existing += normalized
        imported++
    }
    imported to skipped
}

private fun existingPhoneSet(context: Context): MutableSet<String> {
    val result = mutableSetOf<String>()
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return result
    }
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null
    )?.use { cursor ->
        val index = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) result += normalizePhone(cursor.getString(index).orEmpty())
    }
    return result
}

private fun normalizePhone(value: String) = value.filter(Char::isDigit).takeLast(10)
private fun csvEscape(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
