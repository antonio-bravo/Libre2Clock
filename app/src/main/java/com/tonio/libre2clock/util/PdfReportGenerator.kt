package com.tonio.libre2clock.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.ui.report.*
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun generateFullReport(
        context: Context,
        metrics: ReportMetrics,
        agpData: List<AgpPoint>,
        dailySummaries: List<DailySummary>,
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate,
        useOffset: Boolean,
        layout: ReportLayout
    ): File? {
        val pdfDocument = PdfDocument()

        // Page 1: Summary & AGP (if SNAPSHOT or FULL)
        if (layout == ReportLayout.SNAPSHOT || layout == ReportLayout.FULL) {
            val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            val canvas = page.canvas
            var y = MARGIN
            
            y = drawHeader(canvas, "Ambulatory Glucose Profile (AGP)", startDate, endDate, useOffset, y)
            y = drawExecutiveSummary(canvas, metrics, y)
            y += 20f
            drawAgpPatternGraph(canvas, agpData, y)
            
            pdfDocument.finishPage(page)
        }

        // Subsequent Pages: Daily Logs (if DAILY_LOG or FULL)
        if (layout == ReportLayout.DAILY_LOG || layout == ReportLayout.FULL) {
            // Group daily summaries into chunks of 6 per page
            dailySummaries.chunked(6).forEachIndexed { index, chunk ->
                val pageNum = if (layout == ReportLayout.FULL) index + 2 else index + 1
                val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                val canvas = page.canvas
                var y = MARGIN
                
                y = drawHeader(canvas, "Daily Glucose Profiles (Page ${index + 1})", startDate, endDate, useOffset, y)
                drawDailyLogsGrid(canvas, chunk, useOffset, y)
                
                pdfDocument.finishPage(page)
            }
        }

        val file = File(context.cacheDir, "reports/report_${System.currentTimeMillis()}.pdf")
        file.parentFile?.mkdirs()

        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            pdfDocument.close()
            null
        }
    }

    private fun drawHeader(
        canvas: Canvas,
        title: String,
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate,
        useOffset: Boolean,
        startY: Float
    ): Float {
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 18f; isFakeBoldText = true }
        val subPaint = Paint().apply { color = Color.GRAY; textSize = 10f }
        
        canvas.drawText(title, MARGIN, startY + 20f, titlePaint)
        
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        canvas.drawText("Generated: $now | Range: $startDate to $endDate | Mode: ${if (useOffset) "Calibrated" else "Raw"}", MARGIN, startY + 35f, subPaint)
        
        canvas.drawLine(MARGIN, startY + 45f, PAGE_WIDTH - MARGIN, startY + 45f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        
        return startY + 60f
    }

    private fun drawExecutiveSummary(canvas: Canvas, m: ReportMetrics, startY: Float): Float {
        var y = startY
        val sectionPaint = Paint().apply { color = Color.BLACK; textSize = 14f; isFakeBoldText = true }
        val labelPaint = Paint().apply { color = Color.DKGRAY; textSize = 10f }
        val valPaint = Paint().apply { color = Color.BLACK; textSize = 12f; isFakeBoldText = true }

        canvas.drawText("GLUCOSE STATISTICS", MARGIN, y + 15f, sectionPaint)
        y += 30f

        // Stats Table
        val col1 = MARGIN
        val col2 = MARGIN + 180f
        val col3 = MARGIN + 360f

        drawStat(canvas, "Average Glucose", "%.0f mg/dL".format(m.avgGlucose), col1, y, labelPaint, valPaint)
        drawStat(canvas, "GMI (Est. A1c)", "%.1f %%".format(m.gmi), col2, y, labelPaint, valPaint)
        drawStat(canvas, "Variability (CV)", "%.1f %%".format(m.cv), col3, y, labelPaint, valPaint)
        y += 40f

        // TIR Section
        canvas.drawText("TIME IN RANGE", MARGIN, y + 15f, sectionPaint)
        y += 30f
        
        drawTirBarClinical(canvas, m, MARGIN, y, PAGE_WIDTH - 2 * MARGIN, 40f)
        y += 70f
        
        // Insulin Summary
        canvas.drawText("INSULIN STATISTICS", MARGIN, y + 15f, sectionPaint)
        y += 30f
        drawStat(canvas, "Avg Daily Total (TDI)", "%.1f U".format(m.avgTdi), col1, y, labelPaint, valPaint)
        drawStat(canvas, "Basal Percentage", "%.0f %%".format(m.basalPercentage), col2, y, labelPaint, valPaint)
        drawStat(canvas, "Bolus Percentage", "%.0f %%".format(m.bolusPercentage), col3, y, labelPaint, valPaint)
        
        return y + 50f
    }

    private fun drawStat(canvas: Canvas, label: String, value: String, x: Float, y: Float, lp: Paint, vp: Paint) {
        canvas.drawText(label, x, y, lp)
        canvas.drawText(value, x, y + 15f, vp)
    }

    private fun drawTirBarClinical(canvas: Canvas, m: ReportMetrics, x: Float, y: Float, w: Float, h: Float) {
        val p = Paint().apply { style = Paint.Style.FILL }
        var currX = x
        
        // Use constants for colors
        val colors = listOf("#8B0000", "#FF0000", "#008000", "#FFA500", "#FF4500")
        val pcts = listOf(m.tbrVLow, m.tbrLow, m.tir, m.tarHigh, m.tarVHigh)

        pcts.forEachIndexed { i, pct ->
            if (pct > 0) {
                p.color = Color.parseColor(colors[i])
                val partW = (pct / 100f * w).toFloat()
                canvas.drawRect(currX, y, currX + partW, y + h, p)
                currX += partW
            }
        }

        // Text Labels
        val tp = Paint().apply { color = Color.BLACK; textSize = 9f; textAlign = Paint.Align.CENTER }
        if (m.tir > 5) canvas.drawText("Range: %.0f%%".format(m.tir), x + w/2, y + h + 12f, tp)
    }

    private fun drawAgpPatternGraph(canvas: Canvas, points: List<AgpPoint>, startY: Float) {
        val h = 200f
        val w = PAGE_WIDTH - 2 * MARGIN
        val y = startY + 20f
        val x = MARGIN

        val paint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.LTGRAY }
        canvas.drawRect(x, y, x + w, y + h, paint)

        if (points.isEmpty()) return

        val minG = 40f
        val maxG = 350f
        val rangeG = maxG - minG

        // Shading for percentiles
        val p1090Paint = Paint().apply { color = Color.parseColor("#E0E0E0"); style = Paint.Style.FILL }
        val p2575Paint = Paint().apply { color = Color.parseColor("#BDBDBD"); style = Paint.Style.FILL }
        val medianPaint = Paint().apply { color = Color.parseColor("#1A73E8"); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }

        drawArea(canvas, points, { it.p10 }, { it.p90 }, x, y, w, h, minG, rangeG, p1090Paint)
        drawArea(canvas, points, { it.p25 }, { it.p75 }, x, y, w, h, minG, rangeG, p2575Paint)
        drawLine(canvas, points, { it.median }, x, y, w, h, minG, rangeG, medianPaint)

        // Target lines
        paint.color = Color.DKGRAY; paint.alpha = 100; paint.pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        val y180 = y + h - (180f - minG) / rangeG * h
        val y70 = y + h - (70f - minG) / rangeG * h
        canvas.drawLine(x, y180, x + w, y180, paint)
        canvas.drawLine(x, y70, x + w, y70, paint)

        // Labels
        val tp = Paint().apply { color = Color.BLACK; textSize = 8f }
        canvas.drawText("350", x - 20f, y + 5f, tp)
        canvas.drawText("70", x - 15f, y70 + 3f, tp)
        canvas.drawText("Midnight", x, y + h + 15f, tp)
        canvas.drawText("Noon", x + w/2 - 10f, y + h + 15f, tp)
        canvas.drawText("11 PM", x + w - 20f, y + h + 15f, tp)
    }

    private fun drawArea(canvas: Canvas, pts: List<AgpPoint>, low: (AgpPoint) -> Double, high: (AgpPoint) -> Double, x: Float, y: Float, w: Float, h: Float, minG: Float, rangeG: Float, p: Paint) {
        val path = Path()
        pts.forEachIndexed { i, pt ->
            val px = x + (i.toFloat() / 23f) * w
            val py = y + h - (high(pt).toFloat() - minG) / rangeG * h
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        for (i in pts.indices.reversed()) {
            val pt = pts[i]
            val px = x + (i.toFloat() / 23f) * w
            val py = y + h - (low(pt).toFloat() - minG) / rangeG * h
            path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, p)
    }

    private fun drawLine(canvas: Canvas, pts: List<AgpPoint>, v: (AgpPoint) -> Double, x: Float, y: Float, w: Float, h: Float, minG: Float, rangeG: Float, p: Paint) {
        val path = Path()
        pts.forEachIndexed { i, pt ->
            val valG = v(pt).toFloat()
            if (valG > 0) {
                val px = x + (i.toFloat() / 23f) * w
                val py = y + h - (valG - minG) / rangeG * h
                if (path.isEmpty) path.moveTo(px, py) else path.lineTo(px, py)
            }
        }
        canvas.drawPath(path, p)
    }

    private fun drawDailyLogsGrid(canvas: Canvas, summaries: List<DailySummary>, useOffset: Boolean, startY: Float) {
        var y = startY
        val itemH = 110f
        val itemW = PAGE_WIDTH - 2 * MARGIN

        summaries.forEach { s ->
            drawDailyProfile(canvas, s, useOffset, MARGIN, y, itemW, itemH)
            y += itemH + 15f
        }
    }

    private fun drawDailyProfile(canvas: Canvas, s: DailySummary, useOffset: Boolean, x: Float, y: Float, w: Float, h: Float) {
        val paint = Paint().apply { color = Color.BLACK; textSize = 11f; isFakeBoldText = true }
        canvas.drawText("${s.date} | Insulin: %.1f U | Carbs: %.0f g".format(s.insulin, s.carbs), x, y + 12f, paint)
        
        val chartY = y + 20f
        val chartH = h - 25f
        canvas.drawRect(x, chartY, x + w, chartY + chartH, Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE })

        if (s.glucose.isNotEmpty()) {
            val linePaint = Paint().apply { color = Color.parseColor("#1A73E8"); strokeWidth = 1.5f; style = Paint.Style.STROKE; isAntiAlias = true }
            val path = Path()
            
            // OPTIMIZATION: Downsample daily data to max 144 points (one every 10 mins approx)
            val step = if (s.glucose.size > 144) s.glucose.size / 144 else 1
            val sampled = s.glucose.filterIndexed { i, _ -> i % step == 0 }
            
            val firstTime = TimestampParser.parseFlexibleInstant(s.glucose.first().timestamp)?.epochSecond ?: 0L
            
            sampled.forEachIndexed { i, m ->
                val instant = TimestampParser.parseFlexibleInstant(m.timestamp)?.epochSecond ?: firstTime
                val valG = if (useOffset) m.calibratedValue else m.value
                val px = x + ((instant - firstTime).toFloat() / 86400f) * w
                val py = chartY + chartH - (valG - 40f) / 310f * chartH
                val pyC = py.coerceIn(chartY, chartY + chartH)
                if (i == 0) path.moveTo(px, pyC) else path.lineTo(px, pyC)
            }
            canvas.drawPath(path, linePaint)
        }
    }
}
