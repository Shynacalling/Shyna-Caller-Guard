package com.example.callruleblocker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.callruleblocker.data.Rule
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleScreen(
    onSave: (Rule) -> Unit,
    onCancel: () -> Unit
) {
    var simSlot by remember { mutableStateOf(0) }
    var matchType by remember { mutableStateOf("SPECIFIC_NUMBER") }
    var specificNumber by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("BLOCK") }

    Scaffold(topBar = { TopAppBar(title = { Text("Add blocked number") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("SIM slot", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SIM 1" to 0, "SIM 2" to 1).forEach { (label, value) ->
                    FilterChip(
                        selected = simSlot == value,
                        onClick = { simSlot = value },
                        label = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CompactSimBadge(index = value + 1)
                                Spacer(Modifier.width(6.dp))
                                Text(label) 
                            }
                        }
                    )
                }
            }

            Text("Applies to", style = MaterialTheme.typography.titleSmall)
            SingleChoiceRow(
                options = listOf(
                    "Family contacts" to "FAMILY_CONTACTS",
                    "Unknown numbers" to "UNKNOWN",
                    "Specific number" to "SPECIFIC_NUMBER"
                ),
                selected = matchType,
                onSelect = { matchType = it }
            )

            if (matchType == "SPECIFIC_NUMBER") {
                OutlinedTextField(
                    value = specificNumber,
                    onValueChange = { specificNumber = it },
                    label = { Text("Phone number") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("Action", style = MaterialTheme.typography.titleSmall)
            SingleChoiceRow(
                options = listOf("Allow" to "ALLOW", "Block" to "BLOCK"),
                selected = action,
                onSelect = { action = it }
            )

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onSave(
                            Rule(
                                simSlotIndex = simSlot,
                                matchType = matchType,
                                matchValue = specificNumber,
                                action = action
                            )
                        )
                    },
                    enabled = matchType != "SPECIFIC_NUMBER" || specificNumber.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save rule")
                }
            }
        }
    }
}

@Composable
private fun <T> SingleChoiceRow(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}
