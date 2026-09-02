package com.example.callruleblocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(userId: String, onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var starredMsgs by remember { mutableStateOf<List<UniversalMessage>>(emptyList()) }
    
    LaunchedEffect(userId) {
        db.collectionGroup("messages")
            .whereEqualTo("isStarred", true)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    starredMsgs = it.documents.mapNotNull { d -> d.toObject(UniversalMessage::class.java) }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Starred Messages", color = ShynaDesign.colors.TextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = ShynaDesign.colors.TextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ShynaDesign.colors.HeaderBg)
            )
        },
        containerColor = ShynaDesign.colors.PrimaryBg
    ) { p ->
        LazyColumn(Modifier.padding(p).fillMaxSize()) {
            items(starredMsgs) { msg ->
                PremiumMessageBubble(
                    m = msg, 
                    isSelected = false, 
                    isSearchMatch = false, 
                    currentUserId = userId,
                    onLongClick = {}, 
                    onClick = {}, 
                    onMediaClick = {}
                )
                HorizontalDivider(color = ShynaDesign.colors.DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (starredMsgs.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("No starred messages", color = ShynaDesign.colors.TextSecondary)
                    }
                }
            }
        }
    }
}
