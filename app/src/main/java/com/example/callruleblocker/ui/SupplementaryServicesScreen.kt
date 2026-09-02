package com.example.callruleblocker.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telephony.SubscriptionManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class SimServiceInfo(val slot: Int, val label: String, val carrier: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementaryServicesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sims = remember { readActiveSims(context) }
    val visibleSims = sims.ifEmpty { listOf(SimServiceInfo(0, "SIM 1", "Carrier services")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supplementary services", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "These services are controlled by your mobile carrier. Each option opens the matching system carrier page for the selected SIM.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            visibleSims.forEach { sim ->
                item(key = sim.slot) {
                    Column {
                        Text(
                            text = if (sim.carrier.isBlank()) sim.label else "${sim.label} · ${sim.carrier}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                        )
                        Card(Modifier.fillMaxWidth()) {
                            Column {
                                SupplementaryRow(Icons.Outlined.Badge, "Show your caller ID", "Network default") {
                                    openCarrierService(context, sim.slot, "caller_id")
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 18.dp))
                                SupplementaryRow(Icons.Outlined.CallSplit, "Call forwarding", "Forward voice calls to another number") {
                                    openCarrierService(context, sim.slot, "forwarding")
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 18.dp))
                                SupplementaryRow(Icons.Outlined.Block, "Call barring", "Restrict incoming or outgoing calls") {
                                    openCarrierService(context, sim.slot, "barring")
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 18.dp))
                                SupplementaryRow(Icons.Outlined.PhonePaused, "Call waiting", "Receive another call during an active call") {
                                    openCarrierService(context, sim.slot, "waiting")
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 18.dp))
                                SupplementaryRow(Icons.Outlined.Dialpad, "Fixed dialling numbers", "Limit outgoing calls to approved numbers") {
                                    openCarrierService(context, sim.slot, "fdn")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplementaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, null)
    }
}

private fun readActiveSims(context: Context): List<SimServiceInfo> {
    return runCatching {
        val manager = context.getSystemService(SubscriptionManager::class.java)
        manager?.activeSubscriptionInfoList.orEmpty()
            .sortedBy { it.simSlotIndex }
            .map {
                SimServiceInfo(
                    slot = it.simSlotIndex,
                    label = it.displayName?.toString()?.takeIf(String::isNotBlank) ?: "SIM ${it.simSlotIndex + 1}",
                    carrier = it.carrierName?.toString().orEmpty()
                )
            }
    }.getOrDefault(emptyList())
}

private fun openCarrierService(context: Context, slot: Int, service: String) {
    val intents = buildList {
        add(Intent("android.settings.CALL_SETTINGS").putExtra("simSlot", slot).putExtra("slot_id", slot))
        add(Intent("com.samsung.android.app.telephonyui.action.OPEN_CALL_SETTINGS").putExtra("simSlot", slot).putExtra("slot_id", slot))
        when (service) {
            "forwarding" -> {
                add(Intent("android.settings.CALL_FORWARDING_SETTINGS").putExtra("simSlot", slot).putExtra("slot_id", slot))
                add(Intent("com.android.phone.CallFeaturesSetting").putExtra("simSlot", slot))
            }
            "waiting", "caller_id", "barring", "fdn" -> {
                add(Intent("android.settings.SUPPLEMENTARY_SERVICE_SETTINGS").putExtra("simSlot", slot).putExtra("slot_id", slot))
            }
        }
        add(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }
    val resolved = intents.firstOrNull { it.resolveActivity(context.packageManager) != null }
    if (resolved != null) {
        runCatching { context.startActivity(resolved) }
    }
}
