package com.example.callruleblocker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class StrokePoint(val x: Float = 0f, val y: Float = 0f)
data class WhiteboardStroke(
    val id: String = "",
    val points: List<StrokePoint> = emptyList(),
    val color: Long = 0xFFFFFFFF,
    val strokeWidth: Float = 8f,
    val userId: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardScreen(
    meetingId: String,
    onClose: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
    var isBoardActive by remember { mutableStateOf(true) }
    
    val strokes = remember { mutableStateListOf<WhiteboardStroke>() }
    var currentPathPoints = remember { mutableStateListOf<StrokePoint>() }
    var currentColor by remember { mutableStateOf(Color.White) }
    var currentStrokeWidth by remember { mutableStateOf(8f) }
    var isEraser by remember { mutableStateOf(false) }

    DisposableEffect(meetingId) {
        val listener = db.collection("app_calls").document(meetingId).collection("whiteboard_strokes")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    isBoardActive = true
                    strokes.clear()
                    for (doc in snapshot.documents) {
                        val stroke = doc.toObject(WhiteboardStroke::class.java)
                        if (stroke != null) {
                            strokes.add(stroke.copy(id = doc.id))
                        }
                    }
                }
            }
        onDispose { listener.remove() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121B22))) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shyna Whiteboard", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Surface(
                            color = ShynaDesign.colors.BrandGreen,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        strokes.clear()
                        db.collection("app_calls").document(meetingId).collection("whiteboard_strokes")
                            .get().addOnSuccessListener { snapshot ->
                                for (doc in snapshot.documents) {
                                    doc.reference.delete()
                                }
                            }
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Board", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1F2C34))
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0B141B))
                    .pointerInput(currentColor, currentStrokeWidth, isEraser) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPathPoints.clear()
                                currentPathPoints.add(StrokePoint(offset.x, offset.y))
                            },
                            onDrag = { change, _ ->
                                currentPathPoints.add(StrokePoint(change.position.x, change.position.y))
                            },
                            onDragEnd = {
                                if (currentPathPoints.isNotEmpty()) {
                                    val newStroke = WhiteboardStroke(
                                        id = "stroke_${System.currentTimeMillis()}_${Math.random().toString().take(4)}",
                                        points = currentPathPoints.toList(),
                                        color = if (isEraser) 0xFF0B141B else currentColor.value.toLong(),
                                        strokeWidth = if (isEraser) 32f else currentStrokeWidth,
                                        userId = currentUid
                                    )
                                    strokes.add(newStroke)
                                    
                                    db.collection("app_calls").document(meetingId).collection("whiteboard_strokes")
                                        .document(newStroke.id)
                                        .set(newStroke)
                                    currentPathPoints.clear()
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (stroke in strokes) {
                        if (stroke.points.size > 1) {
                            val path = Path().apply {
                                moveTo(stroke.points[0].x, stroke.points[0].y)
                                for (i in 1 until stroke.points.size) {
                                    lineTo(stroke.points[i].x, stroke.points[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = Color(stroke.color.toULong()),
                                style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }

                    if (currentPathPoints.size > 1) {
                        val path = Path().apply {
                            moveTo(currentPathPoints[0].x, currentPathPoints[0].y)
                            for (i in 1 until currentPathPoints.size) {
                                lineTo(currentPathPoints[i].x, currentPathPoints[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = if (isEraser) Color(0xFF0B141B) else currentColor,
                            style = Stroke(width = if (isEraser) 32f else currentStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1F2C34),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(Color.White, Color(0xFF25D366), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFFE53935)).forEach { col ->
                        Surface(
                            modifier = Modifier.size(36.dp).clip(CircleShape).border(2.dp, if (currentColor == col && !isEraser) Color.White else Color.Transparent, CircleShape),
                            color = col,
                            onClick = { currentColor = col; isEraser = false }
                        ) {}
                    }

                    VerticalDivider(modifier = Modifier.height(30.dp), color = Color.Gray.copy(0.4f))

                    IconButton(
                        onClick = { isEraser = true },
                        modifier = Modifier.background(if (isEraser) Color.White.copy(0.2f) else Color.Transparent, CircleShape)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = "Eraser", tint = Color.White)
                    }
                }
            }
        }
    }
}
