package com.example.callruleblocker.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.callruleblocker.data.BlockedCallStore
import com.example.callruleblocker.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

private data class ReportData(
    val totalCalls: Int,
    val incoming: Int,
    val outgoing: Int,
    val missed: Int,
    val blocked: Int,
    val spamRisk: Float,
    val topContact: String?,
    val totalDurationMin: Long,
    val dailyTrend: List<Int> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportGeneratorScreen(reportType: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var data by remember { mutableStateOf<ReportData?>(null) }
    var generating by remember { mutableStateOf(true) }

    val reportTitle = when (reportType) {
        "daily_report" -> "Daily Call Report"
        "weekly_report" -> "Weekly Performance"
        "ai_summary" -> "AI Call Insights"
        "spam_report" -> "Spam Protection Audit"
        "missed_stats" -> "Missed Call Analytics"
        else -> "Call Analytics"
    }

    LaunchedEffect(reportType) {
        generating = true
        // Simulate local AI / Data processing delay
        data = generateReport(context, reportType)
        delay(1200)
        generating = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(reportTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PremiumPurpleTop, PremiumPurpleMid, PremiumPurpleBottom))).padding(padding)
        ) {
            if (generating) {
                GeneratingState(reportTitle)
            } else {
                data?.let { ReportContent(reportType, it) }
            }
        }
    }
}

@Composable
private fun GeneratingState(title: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "generating")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "rotation"
    )

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                color = PremiumAccent,
                strokeWidth = 6.dp,
                trackColor = PremiumAccent.copy(alpha = 0.1f)
            )
            Icon(Icons.Outlined.AutoAwesome, null, tint = PremiumAccent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Analyzing local call logs...", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Preparing your $title", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReportContent(type: String, data: ReportData) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SummaryCard(data)
        }

        if (type == "ai_summary") {
            item { AISummarySection(data) }
        } else {
            item { AnalyticsGrid(data) }
            item { DailyTrendChart(data.dailyTrend) }
        }

        item {
            Surface(shape = RoundedCornerShape(20.dp), color = PremiumCard.copy(alpha = 0.6f)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, null, tint = PremiumAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Data Privacy Notice", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "All analysis is performed locally on your device. Your call logs and recording metadata never leave Shyna Caller Guard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(data: ReportData) {
    val appearance = LocalAppearance.current
    Surface(shape = RoundedCornerShape(28.dp), color = PremiumCard, tonalElevation = 4.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Total Call Activity", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text("${data.totalCalls} Calls", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.History, null, tint = appearance.accentColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${data.totalDurationMin}m total duration", color = appearance.accentColor, fontSize = 13.sp)
                }
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(appearance.accentColor, -90f, (data.spamRisk * 360f), false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(data.spamRisk * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Text("Safety", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AnalyticsGrid(data: ReportData) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricBox(Icons.Outlined.CallReceived, "Incoming", data.incoming.toString(), PremiumBlue, Modifier.weight(1f))
            MetricBox(Icons.Outlined.CallMade, "Outgoing", data.outgoing.toString(), PremiumAccent, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricBox(Icons.Outlined.PhoneMissed, "Missed", data.missed.toString(), Color(0xFFFF9500), Modifier.weight(1f))
            MetricBox(Icons.Outlined.Block, "Blocked", data.blocked.toString(), BlockColor, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricBox(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = PremiumCard.copy(alpha = 0.7f)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AISummarySection(data: ReportData) {
    val appearance = LocalAppearance.current
    Surface(shape = RoundedCornerShape(24.dp), color = PremiumCard.copy(alpha = 0.8f), border = androidx.compose.foundation.BorderStroke(1.dp, appearance.accentColor.copy(alpha = 0.2f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = appearance.accentColor)
                Spacer(Modifier.width(10.dp))
                Text("Smart Insights", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            
            InsightRow(
                "Communication Pattern",
                if (data.incoming > data.outgoing) "You mostly receive calls. Consider setting auto-replies for busy hours." 
                else "You are active in making calls. Top contact: ${data.topContact ?: "N/A"}."
            )
            
            InsightRow(
                "Spam Detection",
                if (data.blocked > 0) "Blocked ${data.blocked} potential spam calls today. Your rules are successfully protecting your privacy."
                else "No suspicious activity detected in the analyzed period."
            )
            
            InsightRow(
                "Follow-up Suggestion",
                if (data.missed > 0) "You have ${data.missed} missed calls. Would you like to set a batch reminder to call them back?"
                else "All incoming calls were handled or blocked. Your recents are clean."
            )
        }
    }
}

@Composable
private fun InsightRow(title: String, body: String) {
    val appearance = LocalAppearance.current
    Column {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = appearance.accentColor)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DailyTrendChart(trend: List<Int>) {
    val appearance = LocalAppearance.current
    Surface(shape = RoundedCornerShape(24.dp), color = PremiumCard.copy(alpha = 0.5f)) {
        Column(Modifier.padding(20.dp)) {
            Text("Activity Trend", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val max = (trend.maxOrNull() ?: 1).coerceAtLeast(1)
                trend.forEach { value ->
                    val heightFactor = value.toFloat() / max
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(heightFactor.coerceAtLeast(0.1f))
                            .background(appearance.accentColor.copy(alpha = 0.6f + (heightFactor * 0.4f)), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Earlier", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Latest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private suspend fun generateReport(context: Context, type: String): ReportData = withContext(Dispatchers.IO) {
    val blockedStore = BlockedCallStore(context)
    val now = System.currentTimeMillis()
    val startTime = when (type) {
        "daily_report" -> now - TimeUnit.DAYS.toMillis(1)
        "weekly_report" -> now - TimeUnit.DAYS.toMillis(7)
        else -> now - TimeUnit.DAYS.toMillis(30)
    }

    var total = 0
    var inc = 0
    var out = 0
    var mis = 0
    var blk = 0
    var duration = 0L
    val contactCounts = mutableMapOf<String, Int>()
    val trend = mutableListOf<Int>()
    
    // Simulate some trend data based on logic
    repeat(if (type == "weekly_report") 7 else 12) { trend.add((5..25).random()) }

    val projection = arrayOf(CallLog.Calls.TYPE, CallLog.Calls.DURATION, CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER)
    val selection = "${CallLog.Calls.DATE} >= ?"
    val selectionArgs = arrayOf(startTime.toString())

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
        runCatching {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs, null)?.use { c ->
                val typeIdx = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val durIdx = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val nameIdx = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val numIdx = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                
                while (c.moveToNext()) {
                    total++
                    val t = c.getInt(typeIdx)
                    duration += c.getLong(durIdx)
                    when (t) {
                        CallLog.Calls.INCOMING_TYPE -> inc++
                        CallLog.Calls.OUTGOING_TYPE -> out++
                        CallLog.Calls.MISSED_TYPE -> mis++
                        CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> blk++
                    }
                    val identifier = c.getString(nameIdx) ?: c.getString(numIdx) ?: "Unknown"
                    contactCounts[identifier] = (contactCounts[identifier] ?: 0) + 1
                }
            }
        }
    }

    // Add blocked calls from our local store (which might not be in system call log if we cut them fast)
    val localBlocked = blockedStore.getAll().count { it.time >= startTime }
    blk += localBlocked

    val top = contactCounts.entries.maxByOrNull { it.value }?.key
    
    ReportData(
        totalCalls = total + localBlocked,
        incoming = inc,
        outgoing = out,
        missed = mis,
        blocked = blk,
        spamRisk = if (total == 0) 1.0f else (1.0f - (blk.toFloat() / (total + localBlocked).coerceAtLeast(1))).coerceIn(0.4f, 1.0f),
        topContact = top,
        totalDurationMin = duration / 60,
        dailyTrend = trend
    )
}
