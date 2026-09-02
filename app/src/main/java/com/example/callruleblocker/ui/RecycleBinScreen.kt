package com.example.callruleblocker.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.callruleblocker.data.CallLogTrashStore
import com.example.callruleblocker.data.RuleRepository
import com.example.callruleblocker.data.TrashedCallGroup
import com.example.callruleblocker.data.TrashedRule
import com.example.callruleblocker.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(repository: RuleRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callTrash = remember { CallLogTrashStore(context.applicationContext) }
    var ruleItems by remember { mutableStateOf(repository.trashedRules()) }
    var callItems by remember { mutableStateOf(callTrash.list()) }
    var confirmEmpty by remember { mutableStateOf(false) }

    fun refresh() {
        ruleItems = repository.trashedRules()
        callItems = callTrash.list()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Recycle bin", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White, navigationIconContentColor = Color.White),
                actions = {
                    if (ruleItems.isNotEmpty() || callItems.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Empty bin") }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PremiumPurpleTop, PremiumPurpleMid, PremiumPurpleBottom))).padding(padding)
        ) {
            if (ruleItems.isEmpty() && callItems.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Surface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.05f)) {
                        Icon(Icons.Outlined.RestoreFromTrash, null, modifier = Modifier.padding(24.dp).size(64.dp), tint = Color.White.copy(alpha = 0.4f))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Recycle bin is empty", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Items stay here for 30 days before permanent deletion.", color = Color.White.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.08f)) {
                            Text("Items are automatically deleted after 30 days.", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                        }
                    }
                    if (callItems.isNotEmpty()) item { Text("Deleted call history", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) }
                    items(callItems.size) { index ->
                        val item = callItems[index]
                        val animAlpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(400, delayMillis = index * 60), label = "trashAnim")
                        Box(Modifier.graphicsLayer { alpha = animAlpha }) {
                            TrashCallCard(
                                item = item,
                                onRestore = {
                                    val restored = runCatching { callTrash.restore(item) }.getOrDefault(0)
                                    Toast.makeText(context, if (restored > 0) "$restored call entries restored" else "Call history could not be restored", Toast.LENGTH_SHORT).show()
                                    refresh()
                                },
                                onDelete = { callTrash.remove(item.trashId); refresh() }
                            )
                        }
                    }
                    if (ruleItems.isNotEmpty()) item { Text("Deleted rules", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) }
                    items(ruleItems.size) { index ->
                        val item = ruleItems[index]
                        val animAlpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(400, delayMillis = (index + callItems.size) * 60), label = "ruleTrashAnim")
                        Box(Modifier.graphicsLayer { alpha = animAlpha }) {
                            TrashRuleCard(
                                item = item,
                                onRestore = { scope.launch { repository.restoreRule(item); refresh() } },
                                onDelete = { repository.permanentlyDelete(item); refresh() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty recycle bin?") },
            text = { Text("All deleted call history and rules will be permanently removed.") },
            confirmButton = { TextButton(onClick = { repository.emptyTrash(); callTrash.clear(); confirmEmpty = false; refresh() }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TrashCallCard(item: TrashedCallGroup, onRestore: () -> Unit, onDelete: () -> Unit) {
    val date = remember(item.deletedAt) { SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date(item.deletedAt)) }
    val name = item.entries.firstOrNull()?.name
    val appearance = LocalAppearance.current
    Surface(shape = RoundedCornerShape(22.dp), color = PremiumCard.copy(alpha = 0.85f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), tonalElevation = 3.dp) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = appearance.accentColor.copy(alpha = 0.12f)) {
                    Icon(Icons.Outlined.Call, null, tint = appearance.accentColor, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(name ?: item.displayNumber, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!name.isNullOrBlank()) Text(item.displayNumber, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("${item.entries.size} call entries · Deleted $date", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRestore, colors = ButtonDefaults.buttonColors(containerColor = appearance.accentColor, contentColor = Color.Black), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Restore") }
                OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))) { Icon(Icons.Outlined.DeleteForever, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Delete") }
            }
        }
    }
}

@Composable
private fun TrashRuleCard(item: TrashedRule, onRestore: () -> Unit, onDelete: () -> Unit) {
    val rule = item.rule
    val appearance = LocalAppearance.current
    val title = when (rule.matchType) {
        "SPECIFIC_NUMBER" -> rule.matchValue.ifBlank { "Specific number" }
        "UNKNOWN" -> "Unknown callers"
        "FAMILY_CONTACTS" -> "Family contacts"
        else -> rule.matchType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
    val date = remember(item.deletedAt) { SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date(item.deletedAt)) }
    Surface(shape = RoundedCornerShape(22.dp), color = PremiumCard.copy(alpha = 0.85f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), tonalElevation = 3.dp) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("${if (rule.action == "BLOCK") "Blocked" else "Allowed"} · SIM ${rule.simSlotIndex + 1}", color = Color.White.copy(alpha = 0.7f))
            Text("Deleted $date", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRestore, colors = ButtonDefaults.buttonColors(containerColor = appearance.accentColor, contentColor = Color.Black), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Restore") }
                OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))) { Icon(Icons.Outlined.DeleteForever, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Delete") }
            }
        }
    }
}
