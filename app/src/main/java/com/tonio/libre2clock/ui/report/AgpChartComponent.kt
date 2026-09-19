package com.tonio.libre2clock.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonio.libre2clock.R

private val ColorCalibratedBlue = Color(0xFF1A73E8)
private val ColorRawOrange = Color(0xFFE65100)
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(12.dp)
    ) {
        Text(
            text = stringResource(R.string.report_agp_profile_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            if (agpData.isEmpty()) return@Canvas

            val leftMargin = 32.dp.toPx()
            val rightMargin = 8.dp.toPx()
            val topMargin = 12.dp.toPx()
            val bottomMargin = 24.dp.toPx()

            val chartWidth = size.width - leftMargin - rightMargin
            val chartHeight = size.height - topMargin - bottomMargin

            val minG = 40f
            val maxG = 350f
            val rangeG = maxG - minG

            // 1. Target Green Band (70 - 180 mg/dL)
            val y180 = topMargin + chartHeight - ((180f - minG) / rangeG * chartHeight)
            val y70 = topMargin + chartHeight - ((70f - minG) / rangeG * chartHeight)

            drawRect(
                color = ColorTargetGreenBand,
                topLeft = Offset(leftMargin, y180),
                size = Size(chartWidth, y70 - y180)
            )

            // 2. Dashed Target Lines (70 and 180)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
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

            // Chart border
            drawRect(
                color = Color.LightGray.copy(alpha = 0.5f),
                topLeft = Offset(leftMargin, topMargin),
                size = Size(chartWidth, chartHeight),
                style = Stroke(width = 1.dp.toPx())
            )

            // 3. Percentile Bands for Calibrated Data
            drawPercentileBand(
                agpData = agpData,
                lowSelector = { it.p10 },
                highSelector = { it.p90 },
                leftMargin = leftMargin,
                topMargin = topMargin,
                chartWidth = chartWidth,
                chartHeight = chartHeight,
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
                chartWidth = chartWidth,
                chartHeight = chartHeight,
                minG = minG,
                rangeG = rangeG,
                color = ColorP2575MidBlue
            )

            // 4. Calibrated Median Line (p50)
            drawMedianCurve(
                agpData = agpData,
                leftMargin = leftMargin,
                topMargin = topMargin,
                chartWidth = chartWidth,
                chartHeight = chartHeight,
                minG = minG,
                rangeG = rangeG,
                color = ColorCalibratedBlue,
                isDashed = false
            )

            // 5. Raw Median Line (if comparing)
            if (compareRawAndCalibrated && !rawAgpData.isNullOrEmpty()) {
                drawMedianCurve(
                    agpData = rawAgpData,
                    leftMargin = leftMargin,
                    topMargin = topMargin,
                    chartWidth = chartWidth,
                    chartHeight = chartHeight,
                    minG = minG,
                    rangeG = rangeG,
                    color = ColorRawOrange,
                    isDashed = true
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Legend Row
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
    minG: Float,
    rangeG: Float,
    color: Color
) {
    if (agpData.isEmpty()) return
    val maxG = minG + rangeG
    val path = Path()

    // Top curve (high values)
    agpData.forEachIndexed { i, pt ->
        val x = leftMargin + (i / 23f) * chartWidth
        val highVal = highSelector(pt).toFloat().coerceIn(minG, maxG)
        val y = topMargin + chartHeight - ((highVal - minG) / rangeG * chartHeight)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    // Bottom curve in reverse (low values)
    for (i in agpData.indices.reversed()) {
        val pt = agpData[i]
        val x = leftMargin + (i / 23f) * chartWidth
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
            val x = leftMargin + (i / 23f) * chartWidth
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
