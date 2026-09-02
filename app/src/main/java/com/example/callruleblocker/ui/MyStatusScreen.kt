package com.example.callruleblocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.callruleblocker.data.StatusBackendManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStatusManagementScreen(
    currentUser: RealUser,
    userStatuses: List<UserStatus>,
    onBack: () -> Unit,
    onAddMoreStatus: () -> Unit,
    onViewStatus: (UserStatus) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedStatusForViewers by remember { mutableStateOf<UserStatus?>(null) }
    var viewersList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoadingViewers by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Status", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ShynaDesign.colors.TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = onAddMoreStatus) {
                        Icon(Icons.Default.Add, contentDescription = "Add Status", tint = ShynaDesign.colors.BrandGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (userStatuses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DonutLarge, contentDescription = null, modifier = Modifier.size(64.dp), tint = ShynaDesign.colors.TextSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No active status updates", color = ShynaDesign.colors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Status updates disappear after 24 hours.", color = ShynaDesign.colors.TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onAddMoreStatus,
                            colors = ButtonDefaults.buttonColors(containerColor = ShynaDesign.colors.BrandGreen)
                        ) {
                            Text("Add Status Update")
                        }
                    }
                }
            } else {
                Text(
                    text = "MY UPDATES",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = ShynaDesign.colors.TextSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(userStatuses) { status ->
                        val timeStr = remember(status.timestamp) {
                            SimpleDateFormat("h:mm a • MMM d", Locale.getDefault()).format(Date(status.timestamp))
                        }

                        ListItem(
                            headlineContent = {
                                Text("${status.seenBy.size} views", fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary)
                            },
                            supportingContent = {
                                Text(timeStr, color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp)
                            },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(52.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = ShynaDesign.colors.DividerColor
                                ) {
                                    if (!status.mediaUrl.isNullOrBlank()) {
                                        AsyncImage(model = status.mediaUrl, contentDescription = null, contentScale = ContentScale.Crop)
                                    } else {
                                        Box(
                                            modifier = Modifier.background(
                                                try {
                                                    Color(android.graphics.Color.parseColor(status.backgroundColor ?: "#008069"))
                                                } catch (_: Exception) {
                                                    ShynaDesign.colors.BrandGreen
                                                }
                                            ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(status.text?.take(3) ?: "Aa", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            selectedStatusForViewers = status
                                            isLoadingViewers = true
                                            scope.launch {
                                                try {
                                                    val db = FirebaseFirestore.getInstance()
                                                    val snap = db.collection("statuses").document(status.id)
                                                        .collection("views").get().await()
                                                    viewersList = snap.documents.mapNotNull { it.data }
                                                } catch (_: Exception) {
                                                    viewersList = emptyList()
                                                }
                                                isLoadingViewers = false
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.RemoveRedEye, contentDescription = "Viewers", tint = ShynaDesign.colors.BrandGreen)
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                StatusBackendManager.deleteStatus(status.id, currentUser.uid)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onViewStatus(status) }
                        )
                        HorizontalDivider(color = ShynaDesign.colors.DividerColor)
                    }
                }
            }
        }

        // Viewers Modal Sheet
        if (selectedStatusForViewers != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedStatusForViewers = null },
                containerColor = ShynaDesign.colors.PrimaryBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Viewed by ${viewersList.size}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ShynaDesign.colors.TextPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (isLoadingViewers) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ShynaDesign.colors.BrandGreen)
                        }
                    } else if (viewersList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text("No views yet", color = ShynaDesign.colors.TextSecondary)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(viewersList) { viewData ->
                                val vName = viewData["viewerName"] as? String ?: "Contact"
                                val vTime = (viewData["lastViewedAt"] as? Long)?.let {
                                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it))
                                } ?: ""

                                ListItem(
                                    headlineContent = { Text(vName, fontWeight = FontWeight.Bold, color = ShynaDesign.colors.TextPrimary) },
                                    supportingContent = { Text(vTime, color = ShynaDesign.colors.TextSecondary, fontSize = 12.sp) },
                                    leadingContent = {
                                        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = ShynaDesign.colors.DividerColor) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = ShynaDesign.colors.TextSecondary, modifier = Modifier.padding(8.dp))
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
