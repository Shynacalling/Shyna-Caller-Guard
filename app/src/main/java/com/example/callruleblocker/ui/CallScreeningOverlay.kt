package com.example.callruleblocker.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun CallScreeningOverlay(
    callerName: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    var messages by remember { mutableStateOf(listOf<String>()) }
    val screeningScript = listOf(
        "Hello, I'm Shyna AI. Can you state your name and the reason for your call?",
        "I'm calling from Galaxy Insurance regarding your policy renewal...",
        "I see. I'll let the user know. Is this urgent?",
        "Yes, it expires tomorrow."
    )

    LaunchedEffect(Unit) {
        screeningScript.forEach { msg ->
            delay(2000)
            messages = messages + msg
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF24C98A).copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24C98A).copy(alpha = 0.3f))
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Color(0xFF24C98A), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shyna AI is screening...", color = Color(0xFF24C98A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            
            Spacer(Modifier.height(20.dp))
            Text(
                callerName,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(30.dp))
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messages) { msg ->
                    val isAI = screeningScript.indexOf(msg) % 2 == 0
                    ScreeningBubble(text = msg, isAI = isAI)
                }
            }

            Spacer(Modifier.height(20.dp))
            
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FloatingActionButton(
                    onClick = onDecline,
                    containerColor = Color(0xFFE53E36),
                    contentColor = Color.White,
                    shape = CircleShape
                ) { Icon(Icons.Default.CallEnd, null) }

                /*
                FloatingActionButton(
                    onClick = {}, // "Ask more" feature
                    containerColor = Color(0xFF232326),
                    contentColor = Color.White,
                    shape = CircleShape
                ) { Icon(Icons.Default.QuestionAnswer, null) }
                */

                FloatingActionButton(
                    onClick = onAnswer,
                    containerColor = Color(0xFF24C98A),
                    contentColor = Color.White,
                    shape = CircleShape
                ) { Icon(Icons.Default.Call, null) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ScreeningBubble(text: String, isAI: Boolean) {
    val alignment = if (isAI) Alignment.CenterStart else Alignment.CenterEnd
    val color = if (isAI) Color(0xFF232326) else Color(0xFF2979FF).copy(alpha = 0.2f)
    val textColor = if (isAI) Color(0xFFBDB7C7) else Color.White
    
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isAI) 4.dp else 16.dp,
                bottomEnd = if (isAI) 16.dp else 4.dp
            ),
            color = color,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text = text,
                color = textColor,
                modifier = Modifier.padding(14.dp),
                fontSize = 15.sp,
                lineHeight = 20.sp
            )
        }
    }
}
