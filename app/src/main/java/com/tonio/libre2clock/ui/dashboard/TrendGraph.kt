package com.tonio.libre2clock.ui.dashboard

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.util.TimestampParser
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

private val ORIGINAL_LINE_COLOR = Color.Gray.copy(alpha = 0.5f)
private val CALIBRATED_LINE_COLOR = Color(0xFF00BCD4)

private fun parseTimestamp(timestamp: String): Instant? {
    return TimestampParser.parseFlexibleInstant(timestamp)
}

private fun measurementInstant(measurement: GlucoseMeasurement): Instant? {
    return parseTimestamp(measurement.factoryTimestamp) ?: parseTimestamp(measurement.timestamp)
}

@Composable
fun InteractiveTrendGraph(
    measurements: List<GlucoseMeasurement>,
    modifier: Modifier = Modifier
) {
    var selectedMeasurement by remember { mutableStateOf<GlucoseMeasurement?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Glucose Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(ORIGINAL_LINE_COLOR))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Original", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.size(8.dp).background(CALIBRATED_LINE_COLOR))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Calibrated", style = MaterialTheme.typography.labelSmall)
                    }
                }
                selectedMeasurement?.let {
                    val displayValue = GlucoseProcessor.formatDualValue(it.value, it.calibratedValue)
                    Text(
                        text = "$displayValue mg/dL at ${it.timestamp}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (measurements.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data available", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val minGlucose = 50
                val maxGlucose = 350
                val range = (maxGlucose - minGlucose).toFloat().coerceAtLeast(1f)

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(measurements) {
                            detectTapGestures { offset ->
                                val width = size.width
                                val stepX = width / (measurements.size - 1).coerceAtLeast(1)
                                val index = (offset.x / stepX).toInt().coerceIn(0, measurements.size - 1)
                                selectedMeasurement = measurements[index]
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    val bottomLabelSpace = 36.dp.toPx()
                    val plotHeight = (height - bottomLabelSpace).coerceAtLeast(1f)
                    val stepX = width / (measurements.size - 1).coerceAtLeast(1)

                    val rawPath = Path()
                    val calibratedPath = Path()

                    measurements.forEachIndexed { index, measurement ->
                        val x = index * stepX
                        
                        // Raw Value Y
                        val rawY = plotHeight - ((measurement.value - minGlucose) / range * plotHeight)
                        if (index == 0) rawPath.moveTo(x, rawY) else rawPath.lineTo(x, rawY)
                        
                        // Calibrated Value Y
                        val calY = plotHeight - ((measurement.calibratedValue - minGlucose) / range * plotHeight)
                        if (index == 0) calibratedPath.moveTo(x, calY) else calibratedPath.lineTo(x, calY)
                    }

                    // Draw Raw Path (Original)
                    drawPath(
                        path = rawPath,
                        color = ORIGINAL_LINE_COLOR,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw Calibrated Path
                    drawPath(
                        path = calibratedPath,
                        color = CALIBRATED_LINE_COLOR,
                        style = Stroke(width = 4.dp.toPx())
                    )
                    
                    val targetLowY = plotHeight - ((70 - minGlucose) / range * plotHeight)
                    val targetHighY = plotHeight - ((180 - minGlucose) / range * plotHeight)

                    drawLine(
                        color = Color.Gray.copy(alpha = 0.3f),
                        start = Offset(0f, targetLowY),
                        end = Offset(width, targetLowY),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.3f),
                        start = Offset(0f, targetHighY),
                        end = Offset(width, targetHighY),
                        strokeWidth = 1.dp.toPx()
                    )

                    val tickValues = listOf(50, 100, 150, 200, 250, 300, 350)
                    tickValues.forEach { value ->
                        val y = plotHeight - ((value - minGlucose) / range * plotHeight)
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.15f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }

                    val startInstant = measurements.firstOrNull()?.let(::measurementInstant)
                    val endInstant = measurements.lastOrNull()?.let(::measurementInstant)
                    if (startInstant != null && endInstant != null) {
                        val effectiveStart = startInstant
                        val effectiveEnd = endInstant
                        if (!effectiveStart.isAfter(effectiveEnd)) {
                            val labelPaint = Paint().apply {
                                color = android.graphics.Color.GRAY
                                textSize = 11.sp.toPx()
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                            val hourFormatter = DateTimeFormatter.ofPattern("HH:mm")
                            val zone = ZoneId.systemDefault()
                            val intervalSeconds = 3L * 60L * 60L
                            val startDateTime = LocalDateTime.ofInstant(effectiveStart, zone)
                            val alignedStartHour = (startDateTime.hour / 3) * 3
                            var cursor = startDateTime
                                .withHour(alignedStartHour)
                                .withMinute(0)
                                .withSecond(0)
                                .withNano(0)
                                .atZone(zone)
                                .toInstant()
                            while (cursor.isBefore(effectiveStart)) {
                                cursor = cursor.plusSeconds(intervalSeconds)
                            }

                            val totalSpan = (effectiveEnd.epochSecond - effectiveStart.epochSecond).coerceAtLeast(1L)
                            while (!cursor.isAfter(effectiveEnd)) {
                                val x = ((cursor.epochSecond - effectiveStart.epochSecond).toFloat() / totalSpan.toFloat()) * width
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.25f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, plotHeight),
                                    strokeWidth = 0.5.dp.toPx()
                                )

                                val localDateTime = LocalDateTime.ofInstant(cursor, zone)
                                drawContext.canvas.nativeCanvas.drawText(
                                    dateFormatter.format(localDateTime),
                                    x,
                                    plotHeight + 14.dp.toPx(),
                                    labelPaint
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    hourFormatter.format(localDateTime),
                                    x,
                                    plotHeight + 28.dp.toPx(),
                                    labelPaint
                                )
                                cursor = cursor.plusSeconds(intervalSeconds)
                            }
                        }
                    }
                }
            }
        }
    }
}
