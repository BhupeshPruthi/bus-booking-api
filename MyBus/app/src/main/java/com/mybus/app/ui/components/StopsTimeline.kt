package com.mybus.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class StopTimelineDisplay(
    val title: String,
    val subtitle: String? = null,
)

private val TimelineColumnWidth = 48.dp
private val TimelineLineWidth = 2.dp
private val BusIconSize = 28.dp
private val DashOnPx = 6f
private val DashGapPx = 6f

/**
 * Dotted vertical line through the horizontal center of each bus icon (same column as icons).
 * Bus icons have no background so dots show between stops.
 */
@Composable
fun StopListTimeline(
    stops: List<StopTimelineDisplay>,
    modifier: Modifier = Modifier,
    rowModifierFactory: (index: Int) -> Modifier = { Modifier },
    trailingContent: (@Composable (index: Int) -> Unit)? = null,
) {
    if (stops.isEmpty()) return

    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val strokePx = with(density) { TimelineLineWidth.toPx() }
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(DashOnPx, DashGapPx), 0f)

    Column(modifier = modifier.fillMaxWidth()) {
        stops.forEachIndexed { index, stop ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(rowModifierFactory(index))
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(TimelineColumnWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.matchParentSize()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            drawLine(
                                color = lineColor,
                                start = Offset(cx, 0f),
                                end = Offset(cx, size.height),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round,
                                pathEffect = dashEffect,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.DirectionsBus,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(BusIconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                ) {
                    Text(
                        text = stop.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    stop.subtitle?.let { sub ->
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailingContent?.invoke(index)
            }
        }
    }
}
