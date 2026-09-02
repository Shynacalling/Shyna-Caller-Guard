package com.example.callruleblocker.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callruleblocker.data.Rule
import com.example.callruleblocker.data.BlockedCallStore
import com.example.callruleblocker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import android.widget.Toast

private fun matchTypeLabel(rule: Rule) = when (rule.matchType) {
    "FAMILY_CONTACTS" -> "Family contacts"
    "UNKNOWN" -> "Unknown numbers"
    "SPECIFIC_NUMBER" -> rule.matchValue.ifBlank { "Specific number" }
    else -> rule.matchType
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    rules: List<Rule>,
    onAddRule: () -> Unit,
    onDeleteRule: (Rule) -> Unit,
    onToggleRule: (Rule) -> Unit,
    onBack: () -> Unit = {}
) {
    val blockedRules = rules.filter { it.action == "BLOCK" }
    val allowRules = rules.filter { it.action != "BLOCK" }
    val appearance = LocalAppearance.current
    
    var showBlockedHistory by remember { mutableStateOf(false) }

    if (showBlockedHistory) {
        BlockedHistoryScreen(onBack = { showBlockedHistory = false })
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Blocked numbers", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRule, 
                containerColor = appearance.accentColor,
                contentColor = Color.Black,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Filled.Add, null) }, 
                text = { Text("Add block rule", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PremiumPurpleTop, PremiumPurpleMid, PremiumPurpleBottom))).padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.05f), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = Color.Red.copy(alpha = 0.12f)) {
                                Icon(Icons.Outlined.Block, null, tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Blocked callers", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Automated screening and SIM rules", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                item {
                    Surface(
                        onClick = { showBlockedHistory = true },
                        shape = RoundedCornerShape(22.dp),
                        color = PremiumCard.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = appearance.accentColor.copy(alpha = 0.15f)) {
                                Icon(Icons.Default.Folder, null, tint = appearance.accentColor, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Auto-blocked history", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Calls cut by software automatically", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f))
                        }
                    }
                }

                if (blockedRules.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("No blocked numbers yet", color = Color.White.copy(alpha = 0.4f))
                        }
                    }
                } else {
                    blockedRules.groupBy { it.simSlotIndex }.toSortedMap().forEach { (simIndex, simRules) ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                                CompactSimBadge(index = simIndex + 1)
                                Spacer(Modifier.width(8.dp))
                                Text(if (simIndex == 0) "SIM 1" else "SIM 2", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        items(simRules.size) { index ->
                            val rule = simRules[index]
                            val animAlpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(400, delayMillis = index * 60), label = "ruleAnim")
                            Box(Modifier.graphicsLayer { alpha = animAlpha }) {
                                RuleCard(rule, onDelete = { onDeleteRule(rule) }, onToggle = { onToggleRule(rule) })
                            }
                        }
                    }
                }

                if (allowRules.isNotEmpty()) {
                    item { HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f)) }
                    item { 
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(Icons.Filled.SimCard, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Allowed & Other SIM rules", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 13.sp) 
                        }
                    }
                    items(allowRules.size) { index ->
                        val rule = allowRules[index]
                        RuleCard(rule, onDelete = { onDeleteRule(rule) }, onToggle = { onToggleRule(rule) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: Rule, onDelete: () -> Unit, onToggle: () -> Unit) {
    val appearance = LocalAppearance.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = PremiumCard.copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        tonalElevation = 2.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(matchTypeLabel(rule), color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionChip(rule.action)
                    Spacer(Modifier.width(10.dp))
                    CompactSimBadge(index = rule.simSlotIndex + 1)
                    Spacer(Modifier.width(6.dp))
                    Text(if (rule.simSlotIndex == 0) "SIM 1" else "SIM 2", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
            Switch(
                checked = rule.enabled, 
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = appearance.accentColor, checkedTrackColor = appearance.accentColor.copy(alpha = 0.5f))
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete rule", tint = Color.White.copy(alpha = 0.4f)) }
        }
    }
}

@Composable
private fun ActionChip(action: String) {
    val color = if (action == "ALLOW") AllowColor else BlockColor
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text = if (action == "ALLOW") "ALLOW" else "BLOCK", 
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { BlockedCallStore(context) }
    var history by remember { mutableStateOf(store.getAll()) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Auto-blocked History", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) } },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { 
                            context.getSharedPreferences("blocked_call_audit", Context.MODE_PRIVATE).edit().clear().apply()
                            history = emptyList()
                            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.DeleteSweep, null, tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PremiumPurpleTop, PremiumPurpleMid, PremiumPurpleBottom))).padding(padding)
        ) {
            if (history.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.History, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No auto-blocked calls yet", color = Color.White.copy(alpha = 0.4f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(history) { entry ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = PremiumCard.copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = Color.Red.copy(alpha = 0.1f)) {
                                    Icon(Icons.Default.Block, null, tint = Color.Red, modifier = Modifier.padding(8.dp).size(20.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    val dateStr = remember(entry.time) { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(entry.time)) }
                                    Text(dateStr, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                }
                                if (entry.simSlot >= 0) {
                                    CompactSimBadge(index = entry.simSlot + 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
