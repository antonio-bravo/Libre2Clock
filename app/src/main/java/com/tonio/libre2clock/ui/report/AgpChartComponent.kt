package com.tonio.libre2clock.ui.report

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonio.libre2clock.R
import com.tonio.libre2clock.ui.icons.ZoomIn
import com.tonio.libre2clock.ui.icons.ZoomOut
import kotlin.math.roundToInt

private val ColorCalibratedBlue = Color(0xFF2563EB)
private val ColorRawOrange = Color(0xFFD97706)
private val ColorP1090LightBlue = Color(0xFFD0E1F9)
private val ColorP2575MidBlue = Color(0xFF90CAF9)
private val ColorTargetGreenBand = Color(0xFFE8F5E9)

@Composable
fun AgpChartComponent(
    agpData: List<AgpPoint>,
    rawAgpData: List<AgpPoint>? = null,
    compareRawAndCalibrated: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var selectedHour by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.report_agp_profile_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Controles de zoom
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        scale = (scale - 0.25f).coerceAtLeast(1f)
                        if (scale == 1f) panX = 0f
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomOut,
                        contentDescription = "Zoom out",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${(scale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = {
                        scale = (scale + 0.25f).coerceAtMost(3f)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ZoomIn,
                        contentDescription = "Zoom in",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Inspección interactiva de la hora seleccionada
        selectedHour?.let { hr ->
            val pt = agpData.getOrNull(hr)
            val rawPt = rawAgpData?.getOrNull(hr)
            if (pt != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%02d:00 h", hr),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mediana: ${pt.median.roundToInt()} mg/dL",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = ColorCalibratedBlue
                        )
                        Text(
                            text = "25-75%: ${pt.p25.roundToInt()}-${pt.p75.roundToInt()}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "10-90%: ${pt.p10.roundToInt()}-${pt.p90.roundToInt()}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (compareRawAndCalibrated && rawPt != null) {
                            Text(
                                text = "Raw: ${rawPt.median.roundToInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorRawOrange
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val yTextPaint = remember(density) {
            Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = with(density) { 9.sp.toPx() }
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }
        }

        val xTextPaint = remember(density) {
            Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = with(density) { 9.sp.toPx() }
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(agpData, scale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 3f)
                            if (scale > 1f) {
                                panX += pan.x
                            } else {
                                panX = 0f
                            }
                        }
                    }
                    .pointerInput(agpData) {
                        detectTapGestures { offset ->
                            val leftMargin = 38.dp.toPx()
                            val rightMargin = 8.dp.toPx()
                            val chartWidth = size.width - leftMargin - rightMargin
                            
                            val relX = (offset.x - leftMargin - panX) / (chartWidth * scale)
                            if (relX in 0f..1f) {
                                val hour = (relX * 23f).roundToInt().coerceIn(0, 23)
                                selectedHour = if (selectedHour == hour) null else hour
                            }
                        }
                    }
            ) {
                if (agpData.isEmpty()) return@Canvas

                val leftMargin = 38.dp.toPx()
                val rightMargin = 8.dp.toPx()
                val topMargin = 12.dp.toPx()
                val bottomMargin = 28.dp.toPx()

                val chartWidth = size.width - leftMargin - rightMargin
                val chartHeight = size.height - topMargin - bottomMargin

                val minG = 40f
                val maxG = 350f
                val rangeG = maxG - minG

                // Limitar panX según la escala
                val maxPan = (chartWidth * (scale - 1f))
                panX = panX.coerceIn(-maxPan, 0f)

                // 1. EJE Y: DIBUJO DE ETIQUETAS DE VALORES DE GLUCOSA Y REJILLA HORIZONTAL
                val yTicks = listOf(350, 250, 180, 70, 40)
                yTicks.forEach { level ->
                    val yPos = topMargin + chartHeight - ((level - minG) / rangeG * chartHeight)

                    // Línea de cuadrícula
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.35f),
                        start = Offset(leftMargin, yPos),
                        end = Offset(leftMargin + chartWidth, yPos),
                        strokeWidth = 0.5.dp.toPx()
                    )

                    // Etiqueta del Eje Y
                    drawContext.canvas.nativeCanvas.drawText(
                        level.toString(),
                        leftMargin - 6.dp.toPx(),
                        yPos + 3.dp.toPx(),
                        yTextPaint
                    )
                }

                // 2. Target Green Band (70 - 180 mg/dL)
                val y180 = topMargin + chartHeight - ((180f - minG) / rangeG * chartHeight)
                val y70 = topMargin + chartHeight - ((70f - minG) / rangeG * chartHeight)

                drawRect(
                    color = ColorTargetGreenBand,
                    topLeft = Offset(leftMargin, y180),
                    size = Size(chartWidth, y70 - y180)
                )

                // Líneas punteadas de límite (70 y 180)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.6f),
                    start = Offset(leftMargin, y180),
                    end = Offset(leftMargin + chartWidth, y180),
                    pathEffect = dashEffect,
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.Gray.copy(alpha = 0.6f),
                    start = Offset(leftMargin, y70),
                    end = Offset(leftMargin + chartWidth, y70),
                    pathEffect = dashEffect,
                    strokeWidth = 1.dp.toPx()
                )

                // Borde de la gráfica
                drawRect(
                    color = Color.Gray.copy(alpha = 0.4f),
                    topLeft = Offset(leftMargin, topMargin),
                    size = Size(chartWidth, chartHeight),
                    style = Stroke(width = 1.dp.toPx())
                )

                // 3. EJE X: DIBUJO DE ETIQUETAS DE HORA Y REJILLA VERTICAL
                val hourTicks = listOf(0, 3, 6, 9, 12, 15, 18, 21, 24)
                hourTicks.forEach { hr ->
                    val relX = hr / 24f
                    val xPos = leftMargin + panX + (relX * chartWidth * scale)

                    if (xPos in leftMargin..(leftMargin + chartWidth)) {
                        // Línea vertical
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.35f),
                            start = Offset(xPos, topMargin),
                            end = Offset(xPos, topMargin + chartHeight),
                            strokeWidth = 0.5.dp.toPx()
                        )

                        // Texto de hora
                        val label = String.format("%02d:00", if (hr == 24) 0 else hr)
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            xPos,
                            topMargin + chartHeight + 18.dp.toPx(),
                            xTextPaint
                        )
                    }
                }

                // 4. BANDS DE PERCENTILES PARA DATOS CALIBRADOS
                drawPercentileBand(
                    agpData = agpData,
                    lowSelector = { it.p10 },
                    highSelector = { it.p90 },
                    leftMargin = leftMargin,
                    topMargin = topMargin,
                    chartWidth = chartWidth * scale,
                    chartHeight = chartHeight,
                    panX = panX,
                    minG = minG,
                    rangeG = rangeG,
                    color = ColorP1090LightBlue
                )

                drawPercentileBand(
                    agpData = agpData,
                    lowSelector = { it.p25 },
                    highSelector = { it.p75 },
                    leftMargin = leftMargin,
                    topMargin = topMargin,
                    chartWidth = chartWidth * scale,
                    chartHeight = chartHeight,
                    panX = panX,
                    minG = minG,
                    rangeG = rangeG,
                    color = ColorP2575MidBlue
                )

                // 5. MEDIANA CALIBRADA (P50)
                drawMedianCurve(
                    agpData = agpData,
                    leftMargin = leftMargin,
                    topMargin = topMargin,
                    chartWidth = chartWidth * scale,
                    chartHeight = chartHeight,
                    panX = panX,
                    minG = minG,
                    rangeG = rangeG,
                    color = ColorCalibratedBlue,
                    isDashed = false
                )

                // 6. MEDIANA RAW (SI ESTÁ COMPARANDO)
                if (compareRawAndCalibrated && !rawAgpData.isNullOrEmpty()) {
                    drawMedianCurve(
                        agpData = rawAgpData,
                        leftMargin = leftMargin,
                        topMargin = topMargin,
                        chartWidth = chartWidth * scale,
                        chartHeight = chartHeight,
                        panX = panX,
                        minG = minG,
                        rangeG = rangeG,
                        color = ColorRawOrange,
                        isDashed = true
                    )
                }

                // 7. INDICADOR DE HORA SELECCIONADA
                selectedHour?.let { hr ->
                    val relX = hr / 23f
                    val selX = leftMargin + panX + (relX * chartWidth * scale)
                    if (selX in leftMargin..(leftMargin + chartWidth)) {
                        drawLine(
                            color = ColorCalibratedBlue,
                            start = Offset(selX, topMargin),
                            end = Offset(selX, topMargin + chartHeight),
                            strokeWidth = 2.dp.toPx()
                        )

                        agpData.getOrNull(hr)?.let { pt ->
                            val calY = topMargin + chartHeight - ((pt.median.toFloat().coerceIn(minG, maxG) - minG) / rangeG * chartHeight)
                            drawCircle(color = ColorCalibratedBlue, radius = 5.dp.toPx(), center = Offset(selX, calY))
                            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(selX, calY))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Fila de Leyendas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = ColorP1090LightBlue, label = "Percentil 10-90%")
            LegendItem(color = ColorP2575MidBlue, label = "Percentil 25-75%")
            LegendItem(color = ColorCalibratedBlue, label = "Mediana")
            if (compareRawAndCalibrated && !rawAgpData.isNullOrEmpty()) {
                LegendItem(color = ColorRawOrange, label = "Mediana Raw")
            }
        }
    }
}

private fun DrawScope.drawPercentileBand(
    agpData: List<AgpPoint>,
    lowSelector: (AgpPoint) -> Double,
    highSelector: (AgpPoint) -> Double,
    leftMargin: Float,
    topMargin: Float,
    chartWidth: Float,
    chartHeight: Float,
    panX: Float,
    minG: Float,
    rangeG: Float,
    color: Color
) {
    if (agpData.isEmpty()) return
    val maxG = minG + rangeG
    val path = Path()

    // Curva superior (valores altos)
    agpData.forEachIndexed { i, pt ->
        val x = leftMargin + panX + (i / 23f) * chartWidth
        val highVal = highSelector(pt).toFloat().coerceIn(minG, maxG)
        val y = topMargin + chartHeight - ((highVal - minG) / rangeG * chartHeight)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    // Curva inferior invertida (valores bajos)
    for (i in agpData.indices.reversed()) {
        val pt = agpData[i]
        val x = leftMargin + panX + (i / 23f) * chartWidth
        val lowVal = lowSelector(pt).toFloat().coerceIn(minG, maxG)
        val y = topMargin + chartHeight - ((lowVal - minG) / rangeG * chartHeight)
        path.lineTo(x, y)
    }

    path.close()
    drawPath(path = path, color = color)
}

private fun DrawScope.drawMedianCurve(
    agpData: List<AgpPoint>,
    leftMargin: Float,
    topMargin: Float,
    chartWidth: Float,
    chartHeight: Float,
    panX: Float,
    minG: Float,
    rangeG: Float,
    color: Color,
    isDashed: Boolean
) {
    if (agpData.isEmpty()) return
    val maxG = minG + rangeG
    val path = Path()

    agpData.forEachIndexed { i, pt ->
        val medianVal = pt.median.toFloat()
        if (medianVal > 0) {
            val x = leftMargin + panX + (i / 23f) * chartWidth
            val y = topMargin + chartHeight - ((medianVal.coerceIn(minG, maxG) - minG) / rangeG * chartHeight)
            if (path.isEmpty) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    val style = if (isDashed) {
        Stroke(
            width = 2.5f.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    } else {
        Stroke(width = 2.5f.dp.toPx())
    }

    drawPath(path = path, color = color, style = style)
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = MaterialTheme.shapes.extraSmall)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
