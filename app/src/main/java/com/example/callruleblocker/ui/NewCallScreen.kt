package com.example.callruleblocker.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.callruleblocker.AppCallActivity
import com.example.callruleblocker.call.AppCallType
import com.example.callruleblocker.call.CallSignalingManager
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCallScreen(allUsers: List<RealUser>, onBack: () -> Unit) {
    val context = LocalContext.current
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    var query by remember { mutableStateOf("") }
    var groupMode by remember { mutableStateOf(false) }
    var selectedUsers by remember { mutableStateOf(setOf<RealUser>()) }
    var isStarting by remember { mutableStateOf(false) }

    val filteredUsers = remember(allUsers, query, currentUid) {
        allUsers.filter {
            it.uid != currentUid &&
                (it.name.contains(query, true) || it.userId.contains(query, true))
        }
    }

    fun openCreatedCall(callId: String) {
        isStarting = false
        context.startActivity(Intent(context, AppCallActivity::class.java).apply {
            putExtra("callId", callId)
            putExtra("isIncoming", false)
        })
    }

    fun showCallError(error: Exception) {
        isStarting = false
        Toast.makeText(
            context,
            error.message?.let { "Unable to start call: $it" } ?: "Unable to start Shyna call",
            Toast.LENGTH_LONG
        ).show()
    }

    fun startDirectCall(user: RealUser, type: AppCallType) {
        if (isStarting) return
        isStarting = true
        CallSignalingManager.startCall(
            context = context,
            receiverUid = user.uid,
            type = type,
            onCallCreated = { openCreatedCall(it.id) },
            onError = ::showCallError
        )
    }

    fun startGroupCall(type: AppCallType) {
        if (isStarting) return
        if (selectedUsers.isEmpty()) {
            Toast.makeText(context, "Select at least one contact", Toast.LENGTH_SHORT).show()
            return
        }
        isStarting = true
        CallSignalingManager.startCall(
            context = context,
            receiverUid = "GROUP",
            type = type,
            onCallCreated = { openCreatedCall(it.id) },
            onError = ::showCallError,
            isGroup = true,
            participantIds = selectedUsers.map { it.uid }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (groupMode) "New Group Call" else "New Call", fontWeight = FontWeight.Bold)
                        if (groupMode) {
                            Text(
                                "${selectedUsers.size} selected",
                                fontSize = 12.sp,
                                color = ShynaDesign.colors.TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (groupMode) {
                            groupMode = false
                            selectedUsers = emptySet()
                        } else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (groupMode) {
                        IconButton(
                            enabled = selectedUsers.isNotEmpty() && !isStarting,
                            onClick = { startGroupCall(AppCallType.VOICE) }
                        ) {
                            Icon(Icons.Default.Call, "Start group voice call", tint = ShynaDesign.colors.BrandGreen)
                        }
                        IconButton(
                            enabled = selectedUsers.isNotEmpty() && !isStarting,
                            onClick = { startGroupCall(AppCallType.VIDEO) }
                        ) {
                            Icon(Icons.Default.Videocam, "Start group video call", tint = ShynaDesign.colors.BrandGreen)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!groupMode) {
                ListItem(
                    headlineContent = { Text("New Group Call", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Call multiple Shyna contacts", color = ShynaDesign.colors.TextSecondary) },
                    leadingContent = {
                        Surface(Modifier.size(40.dp), shape = CircleShape, color = ShynaDesign.colors.BrandGreen) {
                            Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.padding(10.dp))
                        }
                    },
                    modifier = Modifier.clickable {
                        groupMode = true
                        selectedUsers = emptySet()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                    }
                },
                placeholder = { Text("Search Shyna contacts") }
            )

            Text(
                if (groupMode) "Select participants" else "Contacts on Shyna",
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                fontSize = 14.sp,
                color = ShynaDesign.colors.TextSecondary,
                fontWeight = FontWeight.Bold
            )

            if (isStarting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            LazyColumn(Modifier.weight(1f)) {
                items(filteredUsers, key = { it.uid }) { user ->
                    val isSelected = selectedUsers.any { it.uid == user.uid }
                    ListItem(
                        headlineContent = {
                            Text(user.name, fontWeight = FontWeight.SemiBold, color = ShynaDesign.colors.TextPrimary)
                        },
                        supportingContent = { Text(user.userId, color = ShynaDesign.colors.TextSecondary) },
                        leadingContent = {
                            Box {
                                Surface(Modifier.size(48.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                                    if (!user.photoUrl.isNullOrBlank()) {
                                        AsyncImage(user.photoUrl, null, contentScale = ContentScale.Crop)
                                    } else {
                                        Icon(Icons.Default.Person, null, modifier = Modifier.padding(12.dp))
                                    }
                                }
                                if (groupMode && isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = ShynaDesign.colors.BrandGreen,
                                        modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).background(Color.White, CircleShape)
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            if (groupMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selectedUsers = if (it) selectedUsers + user else selectedUsers - user
                                    }
                                )
                            } else {
                                Row {
                                    IconButton(
                                        enabled = !isStarting,
                                        onClick = { startDirectCall(user, AppCallType.VOICE) }
                                    ) {
                                        Icon(Icons.Default.Call, "Voice call", tint = ShynaDesign.colors.BrandGreen)
                                    }
                                    IconButton(
                                        enabled = !isStarting,
                                        onClick = { startDirectCall(user, AppCallType.VIDEO) }
                                    ) {
                                        Icon(Icons.Default.Videocam, "Video call", tint = ShynaDesign.colors.BrandGreen)
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(enabled = groupMode) {
                            selectedUsers = if (isSelected) selectedUsers - user else selectedUsers + user
                        }
                    )
                }
            }
        }
    }
}
