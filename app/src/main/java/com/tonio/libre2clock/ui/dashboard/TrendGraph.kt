package com.tonio.libre2clock.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.max

private val ORIGINAL_LINE_COLOR = Color.Gray.copy(alpha = 0.7f)
private val CALIBRATED_LINE_COLOR = Color(0xFF00BCD4)

private fun measurementInstant(measurement: GlucoseMeasurement): Instant? {
    measurement.epochSeconds?.let { return Instant.ofEpochSecond(it) }
    return TimestampParser.parseFlexibleInstant(measurement.timestamp)
        ?: TimestampParser.parseFlexibleInstant(measurement.factoryTimestamp)
}

private data class NormalizedPoint(
    val relX: Float,
    val relRawY: Float,
    val relCalY: Float,
    val epoch: Long,
    val measurement: GlucoseMeasurement
)

@Composable
fun InteractiveTrendGraph(
    measurements: List<GlucoseMeasurement>,
    predictedPoints: List<Pair<Instant, Int>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    val scrollState = rememberScrollState()
    var selectedMeasurement by remember { mutableStateOf<GlucoseMeasurement?>(null) }

    // 1. PRE-CÁLCULO EN PÍXELES REALES (Evita distorsión de Stroke por Canvas.scale)
    val graphData = remember(measurements, predictedPoints, screenWidth, density) {
        val raw = measurements.mapNotNull { m ->
            measurementInstant(m)?.let { it to m }
        }.sortedBy { it.first }

        val downsampled = if (raw.size > 1500) {
            val step = max(1, raw.size / 1000)
            val result = ArrayList<Pair<Instant, GlucoseMeasurement>>(1000)
            for (i in raw.indices step step) {
                result.add(raw[i])
            }
            if (result.lastOrNull() != raw.lastOrNull()) result.add(raw.last())
            result
        } else {
            raw
        }

        if (downsampled.isEmpty()) return@remember null

        val minGlucose = 50f
        val maxGlucose = 350f
        val range = (maxGlucose - minGlucose).coerceAtLeast(1f)

        val firstInstant = downsampled.first().first
        val lastDataInstant = downsampled.last().first
        val lastInstant = predictedPoints.lastOrNull()?.first ?: lastDataInstant
        
        val totalSeconds = max(1L, lastInstant.epochSecond - firstInstant.epochSecond)

        // Cálculo de dimensiones en píxeles para construir los Paths correctamente
        // BUGFIX: El ancho debe coincidir con el ancho calculado del Canvas, no solo con el ancho de pantalla
        val pixelsPerHourPx = (screenWidth.value / 8f) * density.density
        val totalDurationHours = totalSeconds / 3600.0
        val totalWidthPx = (totalDurationHours * pixelsPerHourPx).toFloat().coerceAtLeast(screenWidth.value * density.density)

        val totalHeightPx = 220f * density.density
        val bottomLabelSpacePx = 36f * density.density
        val plotHeightPx = (totalHeightPx - bottomLabelSpacePx).coerceAtLeast(1f)

        val rawPath = Path()
        val calPath = Path()
        val normalizedPoints = ArrayList<NormalizedPoint>(downsampled.size)
        
        var isFirstPoint = true
        var lastProcessedEpoch = 0L

        downsampled.forEach { (instant, measurement) ->
            val currentEpoch = instant.epochSecond
            val relX = ((currentEpoch - firstInstant.epochSecond).toFloat() / totalSeconds).coerceIn(0f, 1f)
            val x = relX * totalWidthPx
            
            val relRawY = (1f - ((measurement.value - minGlucose) / range)).coerceIn(0f, 1f)
            val rawY = relRawY * plotHeightPx
            
            val relCalY = (1f - ((measurement.calibratedValue - minGlucose) / range)).coerceIn(0f, 1f)
            val calY = relCalY * plotHeightPx

            normalizedPoints.add(NormalizedPoint(relX, relRawY, relCalY, currentEpoch, measurement))

            val isGap = !isFirstPoint && (currentEpoch - lastProcessedEpoch) > 900

            if (isFirstPoint || isGap) {
                rawPath.moveTo(x, rawY)
                calPath.moveTo(x, calY)
                isFirstPoint = false
            } else {
                rawPath.lineTo(x, rawY)
                calPath.lineTo(x, calY)
            }
            lastProcessedEpoch = currentEpoch
        }

        val predPath = if (predictedPoints.isNotEmpty()) {
            val path = Path()
            val lastDataPoint = downsampled.last()
            val startRelX = ((lastDataPoint.first.epochSecond - firstInstant.epochSecond).toFloat() / totalSeconds).coerceIn(0f, 1f)
            val startRelY = (1f - ((lastDataPoint.second.calibratedValue - minGlucose) / range)).coerceIn(0f, 1f)
            path.moveTo(startRelX * totalWidthPx, startRelY * plotHeightPx)

            predictedPoints.forEach { (instant, value) ->
                val relX = ((instant.epochSecond - firstInstant.epochSecond).toFloat() / totalSeconds).coerceIn(0f, 1f)
                val relY = (1f - ((value - minGlucose) / range)).coerceIn(0f, 1f)
                path.lineTo(relX * totalWidthPx, relY * plotHeightPx)
            }
            path
        } else null

        GraphData(normalizedPoints, rawPath, calPath, predPath, firstInstant, lastInstant, totalSeconds)
    }

    if (graphData == null) {
        Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            // OPTIMIZACIÓN: Altura consistente de 220.dp para evitar saltos de layout
            Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Text("No data available", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    val pixelsPerHour = screenWidth / 8f
    val totalDurationHours = graphData.totalSeconds / 3600.0
    val graphWidth = (totalDurationHours * pixelsPerHour.value).dp.coerceAtLeast(screenWidth)

    LaunchedEffect(graphData.normalizedPoints.size) {
        if (!scrollState.isScrollInProgress) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val tickPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            alpha = 100
            textSize = with(density) { 10.sp.toPx() }
            textAlign = android.graphics.Paint.Align.LEFT
            isAntiAlias = true
        }
    }

    val labelPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = with(density) { 11.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    val hourFormatter = remember(is24Hour) {
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h a")
    }
    val zone = ZoneId.systemDefault()

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
                    Text(text = "Glucose Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                        text = "$displayValue mg/dL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .horizontalScroll(scrollState)
            ) {
                Canvas(
                    modifier = Modifier
                        .width(graphWidth)
                        .fillMaxHeight()
                        .pointerInput(graphData) {
                            detectTapGestures { offset ->
                                val tapRatio = (offset.x / size.width).coerceIn(0f, 1f)
                                val targetIndex = (tapRatio * (graphData.normalizedPoints.size - 1)).toInt().coerceIn(0, graphData.normalizedPoints.size - 1)
                                selectedMeasurement = graphData.normalizedPoints[targetIndex].measurement
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    val bottomLabelSpace = with(density) { 36.dp.toPx() }
                    val plotHeight = (height - bottomLabelSpace).coerceAtLeast(1f)

                    // 2. DIBUJO DIRECTO SIN SCALE (Grosor de línea 100% preciso)
                    val y70 = (1f - ((70f - 50f) / 300f)) * plotHeight
                    val y180 = (1f - ((180f - 50f) / 300f)) * plotHeight
                    
                    drawLine(
                        color = Color.Red.copy(alpha = 0.3f),
                        start = Offset(0f, y70),
                        end = Offset(width, y70),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.Green.copy(alpha = 0.3f),
                        start = Offset(0f, y180),
                        end = Offset(width, y180),
                        strokeWidth = 1.dp.toPx()
                    )

                    drawPath(
                        path = graphData.rawPath,
                        color = ORIGINAL_LINE_COLOR,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawPath(
                        path = graphData.calPath,
                        color = CALIBRATED_LINE_COLOR,
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    if (graphData.predPath != null) {
                        drawPath(
                            path = graphData.predPath,
                            color = CALIBRATED_LINE_COLOR.copy(alpha = 0.6f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(8.dp.toPx(), 8.dp.toPx()), 
                                    0f
                                )
                            )
                        )
                    }

                    // 3. ETIQUETAS Y GRID
                    val tickValues = listOf(50, 100, 150, 200, 250, 300, 350)
                    tickValues.forEach { value ->
                        val relY = (1f - ((value - 50f) / 300f)).coerceIn(0f, 1f)
                        val y = relY * plotHeight
                        
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.15f), 
                            start = Offset(0f, y), 
                            end = Offset(width, y), 
                            strokeWidth = 0.5.dp.toPx()
                        )
                        
                        drawContext.canvas.nativeCanvas.drawText(
                            value.toString(), 
                            4.dp.toPx(), 
                            y - 4.dp.toPx(), 
                            tickPaint
                        )
                    }

                    val intervalSeconds = 3L * 60L * 60L
                    val startDateTime = LocalDateTime.ofInstant(graphData.firstInstant, zone)
                    val alignedStartHour = (startDateTime.hour / 3) * 3
                    
                    var cursor = startDateTime.withHour(alignedStartHour).withMinute(0).withSecond(0).withNano(0).atZone(zone).toInstant()
                    while (cursor.isBefore(graphData.firstInstant)) {
                        cursor = cursor.plusSeconds(intervalSeconds)
                    }

                    val dateFmt = android.text.format.DateFormat.getDateFormat(context)
                    while (!cursor.isAfter(graphData.lastInstant)) {
                        val relX = ((cursor.epochSecond - graphData.firstInstant.epochSecond).toFloat() / graphData.totalSeconds).coerceIn(0f, 1f)
                        val x = relX * width
                        
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.15f), 
                            start = Offset(x, 0f), 
                            end = Offset(x, plotHeight), 
                            strokeWidth = 0.5.dp.toPx()
                        )

                        val localDateTime = LocalDateTime.ofInstant(cursor, zone)
                        val showDate = cursor == graphData.firstInstant || localDateTime.hour == 0
                        
                        if (showDate) {
                            val dateStr = dateFmt.format(java.util.Date.from(cursor))
                            drawContext.canvas.nativeCanvas.drawText(dateStr, x, plotHeight + 14.dp.toPx(), labelPaint)
                        }
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

private data class GraphData(
    val normalizedPoints: List<NormalizedPoint>,
    val rawPath: Path,
    val calPath: Path,
    val predPath: Path?,
    val firstInstant: Instant,
    val lastInstant: Instant,
    val totalSeconds: Long
)