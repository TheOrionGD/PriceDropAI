package com.pricedropai.thas.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pricedropai.thas.core.model.PriceSnapshot
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun InteractivePriceChart(
    snapshots: List<PriceSnapshot>,
    modifier: Modifier = Modifier
) {
    var selectedRangeIndex by remember { mutableIntStateOf(1) } // 0: 7D, 1: 30D, 2: ALL
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    val filteredSnapshots = remember(snapshots, selectedRangeIndex) {
        val now = System.currentTimeMillis()
        when (selectedRangeIndex) {
            0 -> snapshots.filter { now - it.timestamp <= TimeUnit.DAYS.toMillis(7) }
            1 -> snapshots.filter { now - it.timestamp <= TimeUnit.DAYS.toMillis(30) }
            else -> snapshots
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Price History",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${snapshots.size} verified price snapshots",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("7D", "30D", "All").forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedRangeIndex == index,
                            onClick = {
                                selectedRangeIndex = index
                                selectedPointIndex = null
                            },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (selectedRangeIndex == index) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (filteredSnapshots.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Not enough historical data yet.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Real snapshots appear here as the app monitors prices.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Interactive inspection header
                if (selectedPointIndex != null && selectedPointIndex!! in filteredSnapshots.indices) {
                    val point = filteredSnapshots[selectedPointIndex!!]
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${point.store.displayName} • ${dateFormatter.format(Date(point.timestamp))}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "₹${"%,.0f".format(point.price)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                } else {
                    Text(
                        text = "Drag or tap across chart to inspect snapshots",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // Chart Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .pointerInput(filteredSnapshots) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val count = filteredSnapshots.size
                                    if (count > 1) {
                                        val spacing = size.width / (count - 1)
                                        val index = ((offset.x / spacing).roundToInt()).coerceIn(0, count - 1)
                                        selectedPointIndex = index
                                    }
                                },
                                onDrag = { change, _ ->
                                    val count = filteredSnapshots.size
                                    if (count > 1) {
                                        val spacing = size.width / (count - 1)
                                        val index = ((change.position.x / spacing).roundToInt()).coerceIn(0, count - 1)
                                        selectedPointIndex = index
                                    }
                                }
                            )
                        }
                        .pointerInput(filteredSnapshots) {
                            detectTapGestures { offset ->
                                val count = filteredSnapshots.size
                                if (count > 1) {
                                    val spacing = size.width / (count - 1)
                                    val index = ((offset.x / spacing).roundToInt()).coerceIn(0, count - 1)
                                    selectedPointIndex = index
                                }
                            }
                        }
                ) {
                    val prices = filteredSnapshots.map { it.price }
                    val maxPrice = prices.maxOrNull() ?: 1.0
                    val minPrice = prices.minOrNull() ?: 0.0
                    val range = if (maxPrice == minPrice) 1.0 else maxPrice - minPrice
                    val count = filteredSnapshots.size
                    val spacing = if (count > 1) size.width / (count - 1) else size.width
                    val path = Path()

                    prices.forEachIndexed { i, p ->
                        val x = i * spacing
                        val norm = ((p - minPrice) / range).toFloat()
                        val y = size.height - (norm * (size.height - 20.dp.toPx())) - 10.dp.toPx()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    // Stroke line
                    drawPath(
                        path = path,
                        color = Color(0xFF6366F1),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Data point circles
                    prices.forEachIndexed { i, p ->
                        val x = i * spacing
                        val norm = ((p - minPrice) / range).toFloat()
                        val y = size.height - (norm * (size.height - 20.dp.toPx())) - 10.dp.toPx()
                        val isSelected = selectedPointIndex == i

                        if (isSelected) {
                            drawCircle(
                                color = Color(0xFF10B981),
                                radius = 7.dp.toPx(),
                                center = Offset(x, y)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = Offset(x, y)
                            )
                        } else {
                            drawCircle(
                                color = Color(0xFF6366F1),
                                radius = 4.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }
        }
    }
}
