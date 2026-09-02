package com.example.callruleblocker.ui

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryPickerScreen(onBack: () -> Unit, onItemsSelected: (List<String>) -> Unit) {
    val context = LocalContext.current
    var selectedItems by remember { mutableStateOf(setOf<Uri>()) }
    var currentTab by remember { mutableStateOf("Pictures") }
    val mediaItems = remember { mutableStateListOf<MediaItem>() }

    LaunchedEffect(Unit) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateColumn) * 1000L
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                mediaItems.add(MediaItem(contentUri, dateAdded))
            }
        }
    }

    val groupedItems = remember(mediaItems.size) {
        mediaItems.groupBy { 
            val date = Date(it.dateAdded)
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        "Select items",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(Modifier.height(32.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (selectedItems.isEmpty()) "No items selected" else "${selectedItems.size} items selected",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.KeyboardArrowUp, null, tint = Color.Gray)
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color(0xFF1A1A1A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GalleryTabItem("Pictures", Icons.Outlined.Image, currentTab == "Pictures") { currentTab = "Pictures" }
                    GalleryTabItem("Albums", Icons.Outlined.CollectionsBookmark, currentTab == "Albums") { currentTab = "Albums" }
                    GalleryTabItem("Collections", Icons.Outlined.FolderCopy, currentTab == "Collections") { currentTab = "Collections" }
                    GalleryTabItem("Search", Icons.Outlined.Search, currentTab == "Search") { currentTab = "Search" }
                }
            }
        },
        floatingActionButton = {
            if (selectedItems.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { onItemsSelected(selectedItems.map { it.toString() }) },
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Outlined.Check, "Done")
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = 2.dp,
                end = 2.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            groupedItems.forEach { (date, items) ->
                item(span = { GridItemSpan(4) }) {
                    DateHeader(date)
                }
                items(items) { item ->
                    GalleryItem(item.uri, selectedItems.contains(item.uri)) {
                        selectedItems = if (selectedItems.contains(item.uri)) selectedItems - item.uri else selectedItems + item.uri
                    }
                }
            }
        }
    }
}

data class MediaItem(val uri: Uri, val dateAdded: Long)

@Composable
private fun DateHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(2.dp, Color.White, CircleShape)
        )
        Spacer(Modifier.width(16.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun GalleryItem(uri: Uri, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Selection circle
        Box(
            modifier = Modifier
                .padding(8.dp)
                .size(24.dp)
                .border(2.dp, Color.White, CircleShape)
                .background(if (isSelected) Color(0xFF2979FF) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        
        // Expand icon
        Icon(
            Icons.Outlined.OpenInFull,
            null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(16.dp)
        )
    }
}

@Composable
private fun GalleryTabItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) Color(0xFF444444) else Color.Transparent,
            modifier = Modifier.size(width = 64.dp, height = 32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}
