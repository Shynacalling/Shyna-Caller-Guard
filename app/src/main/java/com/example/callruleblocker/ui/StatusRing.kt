package com.example.callruleblocker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DynamicSegmentedStatusRing(
    statuses: List<UserStatus>,
    seenStatusIds: Set<String>,
    currentUid: String,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 2.5.dp,
    unseenColor: Color = Color(0xFF25D366), // WhatsApp Green
    seenColor: Color = Color(0xFF8696A0),   // WhatsApp Gray
    content: @Composable () -> Unit
) {
    val totalSegments = statuses.size

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (totalSegments > 0) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val strokeWidthPx = ringWidth.toPx()
                val radiusPx = (size.minDimension - strokeWidthPx) / 2f

                if (totalSegments == 1) {
                    val isSeen = seenStatusIds.contains(statuses[0].id) || statuses[0].seenBy.contains(currentUid)
                    val color = if (isSeen) seenColor else unseenColor
                    drawCircle(
                        color = color,
                        radius = radiusPx,
                        style = Stroke(width = strokeWidthPx)
                    )
                } else {
                    val gapAngle = if (totalSegments > 15) 2f else if (totalSegments > 8) 3f else 4.5f
                    val totalGapsAngle = totalSegments * gapAngle
                    val segmentSweep = (360f - totalGapsAngle) / totalSegments

                    var startAngle = -90f // 12 o'clock top center

                    statuses.forEach { status ->
                        val isSeen = seenStatusIds.contains(status.id) || status.seenBy.contains(currentUid)
                        val color = if (isSeen) seenColor else unseenColor

                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = segmentSweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                        startAngle += (segmentSweep + gapAngle)
                    }
                }
            }
        }

        Box(modifier = Modifier.padding(ringWidth + 3.dp)) {
            content()
        }
    }
}
