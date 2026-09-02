package com.example.callruleblocker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.callruleblocker.call.AppCallType
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleCallScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val currentUid = auth.currentUser?.uid ?: ""

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var callType by remember { mutableStateOf(AppCallType.VOICE) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedRecipients by remember { mutableStateOf(setOf<RealUser>()) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRecipientPicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    if (showRecipientPicker) {
        // Simple recipient picker for now
        // In a real app, this would be a full searchable contact list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule Call", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, null) } },
                actions = {
                    TextButton(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            if (selectedRecipients.isEmpty()) {
                                Toast.makeText(context, "Please select at least one recipient", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }

                            val scheduleData = hashMapOf(
                                "title" to title,
                                "description" to description,
                                "organizerId" to currentUid,
                                "participantIds" to selectedRecipients.map { it.uid } + currentUid,
                                "type" to callType.name,
                                "scheduledFor" to Timestamp(selectedDate.time),
                                "status" to "scheduled",
                                "createdAt" to Timestamp.now()
                            )

                            db.collection("scheduled_calls").add(scheduleData)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Call scheduled successfully", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Failed to schedule call", Toast.LENGTH_SHORT).show()
                                }
                        }
                    ) {
                        Text("SAVE", color = ShynaDesign.colors.BrandGreen, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Call Title") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ShynaDesign.colors.BrandGreen,
                    focusedLabelColor = ShynaDesign.colors.BrandGreen
                )
            )
            
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            Text("Date & Time", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
            
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = ShynaDesign.colors.HeaderBg,
                    onClick = {
                        DatePickerDialog(context, { _, y, m, d ->
                            selectedDate.set(y, m, d)
                            showDatePicker = false
                        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
                    }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(dateFormat.format(selectedDate.time), fontSize = 14.sp)
                    }
                }

                Surface(
                    modifier = Modifier.weight(0.7f).height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = ShynaDesign.colors.HeaderBg,
                    onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            selectedDate.set(Calendar.HOUR_OF_DAY, h)
                            selectedDate.set(Calendar.MINUTE, m)
                        }, selectedDate.get(Calendar.HOUR_OF_DAY), selectedDate.get(Calendar.MINUTE), false).show()
                    }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, null, tint = ShynaDesign.colors.BrandGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(timeFormat.format(selectedDate.time), fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Call Type", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilterChip(
                    selected = callType == AppCallType.VOICE,
                    onClick = { callType = AppCallType.VOICE },
                    label = { Text("Voice Call") },
                    leadingIcon = { Icon(Icons.Default.Call, null, Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = callType == AppCallType.VIDEO,
                    onClick = { callType = AppCallType.VIDEO },
                    label = { Text("Video Call") },
                    leadingIcon = { Icon(Icons.Default.Videocam, null, Modifier.size(18.dp)) }
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recipients", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                IconButton(onClick = { /* Open contact selector */ }) {
                    Icon(Icons.Default.PersonAdd, null, tint = ShynaDesign.colors.BrandGreen)
                }
            }

            if (selectedRecipients.isEmpty()) {
                Text("No recipients selected", color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp)
            } else {
                selectedRecipients.forEach { user ->
                    ListItem(
                        headlineContent = { Text(user.name) },
                        leadingContent = { 
                            Surface(Modifier.size(40.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                                if (!user.photoUrl.isNullOrBlank()) AsyncImage(user.photoUrl, null)
                                else Icon(Icons.Default.Person, null, modifier = Modifier.padding(8.dp))
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { selectedRecipients = selectedRecipients - user }) {
                                Icon(Icons.Default.RemoveCircleOutline, null, tint = Color.Red)
                            }
                        }
                    )
                }
            }
        }
    }
}
