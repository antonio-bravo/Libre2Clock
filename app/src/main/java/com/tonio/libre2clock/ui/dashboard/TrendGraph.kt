package com.tonio.libre2clock.ui.dashboard

import android.graphics.Paint
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.util.TimestampParser
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.max

// --- Colores estilizados y claros para distinguir mediciones ---
private val RAW_LINE_COLOR = Color(0xFF78909C)           // Gris Pizarra (Línea sin calibrar)
private val CALIBRATED_LINE_COLOR = Color(0xFF00ACC1)    // Cyan (Línea calibrada en rango)
private val LOW_GLUCOSE_COLOR = Color(0xFFE53935)       // Rojo Vivo (Baja < targetLow)
private val HIGH_GLUCOSE_COLOR = Color(0xFFFB8C00)      // Ámbar/Naranja (Alta > targetHigh)
private val HEALTHY_BAND_COLOR = Color(0xFF4CAF50).copy(alpha = 0.12f) // Sombra Verde Rango Saludable
private val HEALTHY_BORDER_COLOR = Color(0xFF4CAF50).copy(alpha = 0.5f)

private fun measurementInstant(measurement: GlucoseMeasurement): Instant? {
    return TimestampParser.parseMeasurementInstant(measurement)
}

private data class NormalizedPoint(
    val relX: Float,
    val relRawY: Float,
    val relCalY: Float,
    val epoch: Long,
    val measurement: GlucoseMeasurement,
    val isLow: Boolean,
    val isHigh: Boolean
)

@Composable
fun InteractiveTrendGraph(
    measurements: List<GlucoseMeasurement>,
    predictedPoints: List<Pair<Instant, Int>> = emptyList(),
    targetLow: Int = 70,
    targetHigh: Int = 180,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    val scrollState = rememberScrollState()
    var selectedMeasurement by remember { mutableStateOf<GlucoseMeasurement?>(null) }

    // 1. PRE-CÁLCULO DE DIBUJO Y RUTAS EN PÍXELES REALES
    val graphData = remember(measurements, predictedPoints, screenWidth, density, targetLow, targetHigh) {
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

        val pixelsPerHourPx = with(density) { (screenWidth.value / 8f).dp.toPx() }
        val totalDurationHours = totalSeconds / 3600.0
        val totalWidthPx = (totalDurationHours * pixelsPerHourPx).toFloat().coerceAtLeast(with(density) { screenWidth.toPx() })

        val totalHeightPx = with(density) { 220.dp.toPx() }
        val topPaddingPx = with(density) { 16.dp.toPx() }
        val bottomLabelSpacePx = with(density) { 36.dp.toPx() }
        val plotHeightPx = (totalHeightPx - bottomLabelSpacePx - topPaddingPx).coerceAtLeast(1f)

        val rawPath = Path()
        val calPath = Path()
        val lowPath = Path()
        val normalizedPoints = ArrayList<NormalizedPoint>(downsampled.size)
        
        var isFirstPoint = true
        var isFirstLow = true
        var lastProcessedEpoch = 0L

        downsampled.forEach { (instant, measurement) ->
            val currentEpoch = instant.epochSecond
            val relX = ((currentEpoch - firstInstant.epochSecond).toFloat() / totalSeconds).coerceIn(0f, 1f)
            val x = relX * totalWidthPx
            
            val relRawY = (1f - ((measurement.value - minGlucose) / range)).coerceIn(0f, 1f)
            val rawY = topPaddingPx + (relRawY * plotHeightPx)
            
            val calValue = measurement.calibratedValue
            val relCalY = (1f - ((calValue - minGlucose) / range)).coerceIn(0f, 1f)
            val calY = topPaddingPx + (relCalY * plotHeightPx)

            val isLow = calValue < targetLow
            val isHigh = calValue > targetHigh

            normalizedPoints.add(
                NormalizedPoint(relX, relRawY, relCalY, currentEpoch, measurement, isLow, isHigh)
            )

            val isGap = !isFirstPoint && (currentEpoch - lastProcessedEpoch) > 900

            if (isFirstPoint || isGap) {
                rawPath.moveTo(x, rawY)
                calPath.moveTo(x, calY)
                isFirstPoint = false
            } else {
                rawPath.lineTo(x, rawY)
                calPath.lineTo(x, calY)
            }

            // Construir sub-ruta específica para glucosa baja en rojo
            if (isLow) {
                if (isFirstLow || isGap) {
                    lowPath.moveTo(x, calY)
                    isFirstLow = false
                } else {
                    lowPath.lineTo(x, calY)
                }
            } else {
                isFirstLow = true
            }

            lastProcessedEpoch = currentEpoch
        }

        val predPath = if (predictedPoints.isNotEmpty()) {
            val path = Path()
            val lastDataPoint = downsampled.last()
            val startRelX = ((lastDataPoint.first.epochSecond - firstInstant.epochSecond).toFloat() / totalSeconds).coerceIn(0f, 1f)
            val startRelY = (1f - ((lastDataPoint.second.calibratedValue - minGlucose) / range)).coerceIn(0f, 1f)
            path.moveTo(startRelX * totalWidthPx, topPaddingPx + (startRelY * plotHeightPx))

            predictedPoints.forEach { (instant, value) ->
                val relX = ((instant.epochSecond - firstInstant.epochSecond).toFloat() / totalSeconds).coerceIn(0f, 1f)
                val relY = (1f - ((value - minGlucose) / range)).coerceIn(0f, 1f)
                path.lineTo(relX * totalWidthPx, topPaddingPx + (relY * plotHeightPx))
            }
            path
        } else null

        GraphData(normalizedPoints, rawPath, calPath, lowPath, predPath, firstInstant, lastInstant, totalSeconds)
    }

    if (graphData == null) {
        Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
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
        Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
            isFakeBoldText = true
        }
    }

    val labelPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = with(density) { 11.sp.toPx() }
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    val hourFormatter = remember(is24Hour) {
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h a")
    }
    val zone = ZoneId.systemDefault()
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // --- Cabecera con Leyenda y Detalles del Punto Seleccionado ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Glucose Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    // Leyenda explicativa de colores
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp, 3.dp).background(RAW_LINE_COLOR))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.graph_legend_raw), style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp, 3.dp).background(CALIBRATED_LINE_COLOR))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.graph_legend_calibrated), style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50).copy(alpha = 0.4f)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.graph_legend_target, targetLow, targetHigh), style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(LOW_GLUCOSE_COLOR))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.graph_legend_low, targetLow), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            selectedMeasurement?.let { selected ->
                Spacer(modifier = Modifier.height(8.dp))
                val dualValue = GlucoseProcessor.formatDualValue(selected.value, selected.calibratedValue)
                val formattedTimestamp = remember(selected) { formatSelectedTimestamp(selected) }
                val statusText = when {
                    selected.calibratedValue < targetLow -> stringResource(R.string.graph_status_low, targetLow)
                    selected.calibratedValue > targetHigh -> stringResource(R.string.graph_status_high, targetHigh)
                    else -> stringResource(R.string.graph_status_in_range)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        selected.calibratedValue < targetLow -> MaterialTheme.colorScheme.errorContainer
                        selected.calibratedValue > targetHigh -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$formattedTimestamp  •  $dualValue mg/dL",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // --- Área del Gráfico con Eje Y Fijo e Interacción ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                // Lienzo Desplazable Horizontalmente para el Gráfico
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState)
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(graphWidth)
                            .fillMaxHeight()
                            .pointerInput(graphData) {
                                detectTapGestures { offset ->
                                    val tapRatio = (offset.x / size.width).coerceIn(0f, 1f)
                                    val targetIndex = (tapRatio * (graphData.normalizedPoints.size - 1))
                                        .toInt()
                                        .coerceIn(0, graphData.normalizedPoints.size - 1)
                                    selectedMeasurement = graphData.normalizedPoints[targetIndex].measurement
                                }
                            }
                    ) {
                        val width = size.width
                        val height = size.height
                        val topPadding = 16.dp.toPx()
                        val bottomLabelSpace = 36.dp.toPx()
                        val plotHeight = (height - bottomLabelSpace - topPadding).coerceAtLeast(1f)

                        // 1. DIBUJO DE LA BANDA SOMBREADA DEL RANGO SALUDABLE
                        val minGlucose = 50f
                        val range = 300f

                        val yTargetHigh = topPadding + (1f - ((targetHigh.toFloat() - minGlucose) / range)) * plotHeight
                        val yTargetLow = topPadding + (1f - ((targetLow.toFloat() - minGlucose) / range)) * plotHeight

                        drawRect(
                            color = HEALTHY_BAND_COLOR,
                            topLeft = Offset(0f, yTargetHigh),
                            size = Size(width, (yTargetLow - yTargetHigh).coerceAtLeast(1f))
                        )

                        // Líneas límite del rango objetivo
                        drawLine(
                            color = HEALTHY_BORDER_COLOR,
                            start = Offset(0f, yTargetHigh),
                            end = Offset(width, yTargetHigh),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f)
                        )
                        drawLine(
                            color = LOW_GLUCOSE_COLOR.copy(alpha = 0.5f),
                            start = Offset(0f, yTargetLow),
                            end = Offset(width, yTargetLow),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f)
                        )

                        // 2. DIBUJO DE LAS LÍNEAS DE GLUCOSA
                        // Línea Original (Raw / Sin calibrar): Gris Punteada
                        drawPath(
                            path = graphData.rawPath,
                            color = RAW_LINE_COLOR,
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()), 0f)
                            )
                        )

                        // Línea Calibrada Principal: Cyan/Azul Continua
                        drawPath(
                            path = graphData.calPath,
                            color = CALIBRATED_LINE_COLOR,
                            style = Stroke(width = 2.5.dp.toPx())
                        )

                        // Sobrescribir segmentos en Rojo Vivo donde la glucosa está baja (< targetLow)
                        drawPath(
                            path = graphData.lowPath,
                            color = LOW_GLUCOSE_COLOR,
                            style = Stroke(width = 3.5.dp.toPx())
                        )

                        // Predicción futura
                        if (graphData.predPath != null) {
                            drawPath(
                                path = graphData.predPath,
                                color = CALIBRATED_LINE_COLOR.copy(alpha = 0.6f),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()), 0f)
                                )
                            )
                        }

                        // 3. DIBUJO DE PUNTOS DESTACADOS (Rojo para glucosa baja)
                        graphData.normalizedPoints.forEach { pt ->
                            val ptX = pt.relX * width
                            val ptCalY = topPadding + (pt.relCalY * plotHeight)
                            
                            if (pt.isLow) {
                                drawCircle(color = LOW_GLUCOSE_COLOR, radius = 3.5.dp.toPx(), center = Offset(ptX, ptCalY))
                                drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(ptX, ptCalY))
                            } else if (pt.isHigh) {
                                drawCircle(color = HIGH_GLUCOSE_COLOR, radius = 3.5.dp.toPx(), center = Offset(ptX, ptCalY))
                                drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(ptX, ptCalY))
                            }
                        }

                        // 4. EJE X Y REJILLA TEMPORAL
                        val tickValues = listOf(50, 100, 150, 200, 250, 300, 350)
                        tickValues.forEach { value ->
                            val relY = (1f - ((value - 50f) / 300f)).coerceIn(0f, 1f)
                            val y = topPadding + (relY * plotHeight)
                            
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.15f), 
                                start = Offset(0f, y), 
                                end = Offset(width, y), 
                                strokeWidth = 0.5.dp.toPx()
                            )
                        }

                        val intervalSeconds = 3L * 60L * 60L
                        val startDateTime = LocalDateTime.ofInstant(graphData.firstInstant, zone)
                        val alignedStartHour = (startDateTime.hour / 3) * 3
                        
                        var cursor = startDateTime.withHour(alignedStartHour).withMinute(0).withSecond(0).withNano(0).atZone(zone).toInstant()
                        
                        while (cursor.isAfter(graphData.firstInstant)) {
                            cursor = cursor.minusSeconds(intervalSeconds)
                        }
                        while (cursor.plusSeconds(intervalSeconds).isBefore(graphData.firstInstant)) {
                            cursor = cursor.plusSeconds(intervalSeconds)
                        }

                        val dateFmt = DateFormat.getDateFormat(context)
                        while (!cursor.isAfter(graphData.lastInstant)) {
                            val relX = ((cursor.epochSecond - graphData.firstInstant.epochSecond).toFloat() / graphData.totalSeconds).coerceIn(0f, 1f)
                            val x = relX * width
                            
                            if (cursor.isAfter(graphData.firstInstant) || cursor == graphData.firstInstant) {
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.15f), 
                                    start = Offset(x, topPadding), 
                                    end = Offset(x, topPadding + plotHeight), 
                                    strokeWidth = 0.5.dp.toPx()
                                )

                                val localDateTime = LocalDateTime.ofInstant(cursor, zone)
                                val showDate = cursor == graphData.firstInstant || localDateTime.hour == 0
                                
                                if (showDate) {
                                    val dateStr = dateFmt.format(Date.from(cursor))
                                    drawContext.canvas.nativeCanvas.drawText(dateStr, x, topPadding + plotHeight + 14.dp.toPx(), labelPaint)
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    hourFormatter.format(localDateTime), 
                                    x, 
                                    topPadding + plotHeight + 28.dp.toPx(), 
                                    labelPaint
                                )
                            }
                            cursor = cursor.plusSeconds(intervalSeconds)
                        }

                        // Indicador de selección al hacer tap
                        selectedMeasurement?.let { selected ->
                            measurementInstant(selected)?.let { selInstant ->
                                val selRelX = ((selInstant.epochSecond - graphData.firstInstant.epochSecond).toFloat() / graphData.totalSeconds).coerceIn(0f, 1f)
                                val selX = selRelX * width
                                drawLine(
                                    color = primaryColor.copy(alpha = 0.7f),
                                    start = Offset(selX, topPadding),
                                    end = Offset(selX, topPadding + plotHeight),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                                )
                            }
                        }
                    }
                }

                // 5. SUPERPOSICIÓN FIJA Y LEGIBLE DEL EJE Y (Sticky Y-Axis)
                Canvas(
                    modifier = Modifier
                        .width(42.dp)
                        .fillMaxHeight()
                ) {
                    val height = size.height
                    val topPadding = 16.dp.toPx()
                    val bottomLabelSpace = 36.dp.toPx()
                    val plotHeight = (height - bottomLabelSpace - topPadding).coerceAtLeast(1f)

                    // Fondo semitransparente para garantizar lectura perfecta del Eje Y sobre el gráfico
                    drawRect(
                        color = Color.Black.copy(alpha = 0.25f),
                        size = Size(42.dp.toPx(), height)
                    )

                    val ticks = listOf(50, targetLow, 100, 150, targetHigh, 200, 250, 300, 350)
                    ticks.distinct().sorted().forEach { value ->
                        val relY = (1f - ((value - 50f) / 300f)).coerceIn(0f, 1f)
                        val y = topPadding + (relY * plotHeight)

                        val paintToUse = when (value) {
                            targetLow -> Paint(tickPaint).apply { color = android.graphics.Color.RED }
                            targetHigh -> Paint(tickPaint).apply { color = android.graphics.Color.GREEN }
                            else -> tickPaint
                        }

                        drawContext.canvas.nativeCanvas.drawText(
                            value.toString(),
                            4.dp.toPx(),
                            y + 3.dp.toPx(),
                            paintToUse
                        )
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
    val lowPath: Path,
    val predPath: Path?,
    val firstInstant: Instant,
    val lastInstant: Instant,
    val totalSeconds: Long
)

private fun formatSelectedTimestamp(measurement: GlucoseMeasurement): String {
    val instant = TimestampParser.parseMeasurementInstant(measurement) ?: return measurement.timestamp
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}
