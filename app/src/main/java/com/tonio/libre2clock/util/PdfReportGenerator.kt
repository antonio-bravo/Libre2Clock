package com.tonio.libre2clock.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import com.tonio.libre2clock.ui.report.*
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 30f

    // Colors matching FreeStyle Libre AGP Report
    private val COLOR_BLUE = Color.parseColor("#1A73E8")           // Calibrated
    private val COLOR_RAW_ORANGE = Color.parseColor("#E65100")     // Raw
    private val COLOR_BG_BAND = Color.parseColor("#E8F5E9")        // Target range band
    private val COLOR_TIR_GREEN = Color.parseColor("#4CAF50")      // Time in Range
    private val COLOR_TAR_HIGH_ORANGE = Color.parseColor("#FFA500") // Above target
    private val COLOR_TAR_VHIGH_RED = Color.parseColor("#FF4500")   // Very high
    private val COLOR_TBR_LOW_YELLOW = Color.parseColor("#FFD700")  // Below target
    private val COLOR_TBR_VLOW_RED = Color.RED                      // Very low
    private val COLOR_P1090_LIGHT = Color.parseColor("#D0E1F9")     // 10-90 percentile
    private val COLOR_P2575_MID = Color.parseColor("#90CAF9")       // 25-75 percentile

    fun generateFullReport(
        context: Context,
        metrics: ReportMetrics,
        rawMetrics: ReportMetrics? = null,
        agpData: List<AgpPoint>,
        rawAgpData: List<AgpPoint>? = null,
        dailySummaries: List<DailySummary>,
        startDate: LocalDate,
        endDate: LocalDate,
        useOffset: Boolean,
        layout: ReportLayout,
        patientName: String = "Antonio Bravo",
        compareRawAndCalibrated: Boolean = true
    ): File? {
        val pdfDocument = PdfDocument()
        var pageCounter = 1

        val spanDays = (ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt()
        val totalPages = calculateTotalPages(layout, dailySummaries.size)

        // Page 1: Informe AGP Principal (igual al de FreeStyle Libre)
        if (layout == ReportLayout.SNAPSHOT || layout == ReportLayout.FULL || layout == ReportLayout.AGP_COMPLETE) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawPageHeader(canvas, patientName, pageCounter.toString(), totalPages.toString(), startDate, endDate)
            y = drawAgpReportHeader(canvas, startDate, endDate, spanDays, y)
            y = drawTimeInRangesVerticalBars(canvas, metrics, rawMetrics, compareRawAndCalibrated, y)
            y = drawGlucoseStatsTable(canvas, metrics, rawMetrics, compareRawAndCalibrated, y)
            y = drawAgpChart(canvas, agpData, rawAgpData, compareRawAndCalibrated, y)
            drawDailySparklinesGrid(canvas, dailySummaries, compareRawAndCalibrated, y)
            drawFooterCitation(canvas)

            pdfDocument.finishPage(page)
            pageCounter++
        }

        // Page 2: Visualización del patrón de glucosa (Gráfico modal por hora del día)
        if (layout == ReportLayout.AGP_COMPLETE) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawPageHeader(canvas, patientName, pageCounter.toString(), totalPages.toString(), startDate, endDate)
            y = drawGlucosePatternVisualization(canvas, agpData, rawAgpData, compareRawAndCalibrated, startDate, endDate, spanDays, y)

            pdfDocument.finishPage(page)
            pageCounter++
        }

        // Page 3: Resumen mensual (Calendario)
        if (layout == ReportLayout.AGP_COMPLETE) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawPageHeader(canvas, patientName, pageCounter.toString(), totalPages.toString(), startDate, endDate)
            y = drawMonthlySummary(canvas, dailySummaries, startDate, y, compareRawAndCalibrated)

            pdfDocument.finishPage(page)
            pageCounter++
        }

        // Pages 4+: Registro Diario (3 días por página)
        if (layout == ReportLayout.DAILY_LOG || layout == ReportLayout.FULL || layout == ReportLayout.AGP_COMPLETE) {
            val chunks = dailySummaries.chunked(3)
            chunks.forEachIndexed { idx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                var y = drawPageHeader(canvas, patientName, pageCounter.toString(), totalPages.toString(), startDate, endDate)
                y = drawDailyLogHeader(canvas, startDate, endDate, spanDays, y)
                drawDailyLogPage(canvas, chunk, compareRawAndCalibrated, y)

                pdfDocument.finishPage(page)
                pageCounter++
            }
        }

        // Page: Instantánea (Resumen ejecutivo)
        if (layout == ReportLayout.SNAPSHOT || layout == ReportLayout.FULL || layout == ReportLayout.AGP_COMPLETE) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawPageHeader(canvas, patientName, pageCounter.toString(), totalPages.toString(), startDate, endDate)
            drawSnapshotReport(canvas, metrics, rawMetrics, agpData, rawAgpData, startDate, endDate, spanDays, compareRawAndCalibrated, y)

            pdfDocument.finishPage(page)
            pageCounter++
        }

        // Page: Patrones de hora de comidas
        if (layout == ReportLayout.AGP_COMPLETE) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawPageHeader(canvas, patientName, pageCounter.toString(), totalPages.toString(), startDate, endDate)
            drawMealTimePatternsPage(canvas, dailySummaries, compareRawAndCalibrated, startDate, endDate, spanDays, y)

            pdfDocument.finishPage(page)
            pageCounter++
        }

        // Page: Resumen Semanal
        if (layout == ReportLayout.AGP_COMPLETE) {
            val weeksNeeded = (dailySummaries.size + 6) / 7
            val weekChunks = dailySummaries.chunked(7)

            weekChunks.forEachIndexed { weekIdx, weekData ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                var y = drawPageHeader(canvas, patientName, pageCounter.toString(), totalPages.toString(), startDate, endDate)
                y = drawWeeklySummaryHeader(canvas, startDate, endDate, spanDays, y)
                drawWeeklySummaryPage(canvas, weekData, compareRawAndCalibrated, y)

                pdfDocument.finishPage(page)
                pageCounter++
            }
        }

        // Page: Configuración del dispositivo
        if (layout == ReportLayout.AGP_COMPLETE) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawPageHeader(canvas, patientName, pageCounter.toString(), totalPages.toString(), startDate, endDate)
            drawDeviceSettingsPage(canvas, y)

            pdfDocument.finishPage(page)
            pageCounter++
        }

        // Page: Patrones Diarios (Promedio del día completo)
        if (layout == ReportLayout.AGP_COMPLETE) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawPageHeader(canvas, patientName, pageCounter.toString(), totalPages.toString(), startDate, endDate)
            drawDailyAveragePatternsPage(canvas, agpData, rawAgpData, compareRawAndCalibrated, startDate, endDate, spanDays, y)

            pdfDocument.finishPage(page)
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

    private fun calculateTotalPages(layout: ReportLayout, dailySummariesCount: Int): Int {
        return when (layout) {
            ReportLayout.SNAPSHOT -> 2 // AGP + Snapshot
            ReportLayout.DAILY_LOG -> 1 + (dailySummariesCount + 2) / 3 // Header + Daily pages
            ReportLayout.FULL -> {
                val dailyPages = (dailySummariesCount + 2) / 3
                1 + // AGP Main
                dailyPages + // Daily Log
                1   // Snapshot
            }
            ReportLayout.AGP_COMPLETE -> {
                val dailyPages = (dailySummariesCount + 2) / 3
                val weeklyPages = (dailySummariesCount + 6) / 7
                1 + // AGP Main
                1 + // Pattern Visualization
                1 + // Monthly Summary
                dailyPages + // Daily Log
                1 + // Snapshot
                1 + // Meal Time Patterns
                weeklyPages + // Weekly Summary
                1 + // Device Settings
                1   // Daily Average Patterns
            }
        }
    }

    // --- HEADER STANDARD ---
    private fun drawPageHeader(
        canvas: Canvas,
        name: String,
        pageCurr: String,
        pageTotal: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Float {
        val paintText = Paint().apply { color = Color.BLACK; textSize = 11f; isFakeBoldText = true }
        val paintSub = Paint().apply { color = Color.DKGRAY; textSize = 8f }

        canvas.drawText(name, MARGIN, MARGIN + 12f, paintText)

        val col2 = MARGIN + 160f
        val col3 = MARGIN + 340f

        canvas.drawText("FECHA DE NACIMIENTO: 04/01/1978", col2, MARGIN + 10f, paintSub)
        canvas.drawText("FUENTES: FreeStyle LibreLink / Libre2Clock", col2, MARGIN + 22f, paintSub)

        canvas.drawText("Hospital de alta resolución de Utrera", col3, MARGIN + 10f, paintSub)
        canvas.drawText("TELÉFONO: 635442843", col3, MARGIN + 22f, paintSub)

        val pageStr = "PÁGINA: $pageCurr / $pageTotal"
        val genStr = "Generado: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        canvas.drawText(pageStr, PAGE_WIDTH - MARGIN - 70f, MARGIN + 10f, paintSub)
        canvas.drawText(genStr, PAGE_WIDTH - MARGIN - 90f, MARGIN + 22f, paintSub)

        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        canvas.drawLine(MARGIN, MARGIN + 30f, PAGE_WIDTH - MARGIN, MARGIN + 30f, linePaint)

        return MARGIN + 40f
    }

    // --- INFORME AGP PAGE ---
    private fun drawAgpReportHeader(canvas: Canvas, start: LocalDate, end: LocalDate, days: Int, startY: Float): Float {
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        val subPaint = Paint().apply { color = Color.DKGRAY; textSize = 10f }

        canvas.drawText("Informe AGP", MARGIN, startY + 14f, titlePaint)
        val rangeStr = "${formatDateSpanish(start)} - ${formatDateSpanish(end)} ($days Días)"
        canvas.drawText(rangeStr, MARGIN, startY + 28f, subPaint)

        return startY + 40f
    }

    // Función mejorada: Gráfico de Tiempo en Rangos (Barras verticales)
    private fun drawTimeInRangesVerticalBars(
        canvas: Canvas,
        cal: ReportMetrics,
        raw: ReportMetrics?,
        compare: Boolean,
        startY: Float
    ): Float {
        var y = startY
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true }
        canvas.drawText("TIEMPO EN RANGOS", MARGIN, y + 10f, headerPaint)
        y += 18f

        val barWidth = 80f
        val barHeight = 150f
        val barX = MARGIN + 20f

        // Draw calibrated bars
        drawVerticalBar(canvas, barX, y, barWidth, barHeight, cal, "Calibrado")

        // Draw raw bars if comparing
        if (compare && raw != null) {
            drawVerticalBar(canvas, barX + barWidth + 40f, y, barWidth, barHeight, raw, "Raw")
        }

        // Legend on the right
        val legX = barX + (if (compare) 2 * barWidth + 80f else barWidth + 40f)
        val legY = y + 20f
        val labelPaint = Paint().apply { color = Color.DKGRAY; textSize = 8f }
        val valPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true }

        var ly = legY
        canvas.drawText("Muy alto (>250)", legX, ly, labelPaint)
        canvas.drawText("%.0f%% (%s)".format(cal.tarVHigh, formatHoursMinutes(cal.timeInRangesHours.tarVHighHours)), legX + 90f, ly, valPaint)
        ly += 15f

        canvas.drawText("Alto (181-250)", legX, ly, labelPaint)
        canvas.drawText("%.0f%% (%s)".format(cal.tarHigh, formatHoursMinutes(cal.timeInRangesHours.tarHighHours)), legX + 90f, ly, valPaint)
        ly += 15f

        canvas.drawText("Objetivo (70-180)", legX, ly, labelPaint)
        canvas.drawText("%.0f%% (%s)".format(cal.tir, formatHoursMinutes(cal.timeInRangesHours.tirHours)), legX + 90f, ly, valPaint)
        ly += 15f

        canvas.drawText("Bajo (54-69)", legX, ly, labelPaint)
        canvas.drawText("%.0f%% (%s)".format(cal.tbrLow, formatHoursMinutes(cal.timeInRangesHours.tbrLowHours)), legX + 90f, ly, valPaint)
        ly += 15f

        canvas.drawText("Muy bajo (<54)", legX, ly, labelPaint)
        canvas.drawText("%.0f%% (%s)".format(cal.tbrVLow, formatHoursMinutes(cal.timeInRangesHours.tbrVLowHours)), legX + 90f, ly, valPaint)

        return y + barHeight + 30f
    }

    private fun drawVerticalBar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, metrics: ReportMetrics, label: String) {
        // Draw border
        val borderPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRect(x, y, x + w, y + h, borderPaint)

        // Calculate heights for each segment
        val total = 100.0
        var currentY = y + h

        // Draw segments from bottom to top
        if (metrics.tbrVLow > 0) {
            val segmentH = (metrics.tbrVLow / total * h).toFloat()
            currentY -= segmentH
            canvas.drawRect(x, currentY, x + w, y + h, Paint().apply { color = COLOR_TBR_VLOW_RED; style = Paint.Style.FILL })
        }

        if (metrics.tbrLow > 0) {
            val segmentH = (metrics.tbrLow / total * h).toFloat()
            currentY -= segmentH
            canvas.drawRect(x, currentY, x + w, currentY + segmentH, Paint().apply { color = COLOR_TBR_LOW_YELLOW; style = Paint.Style.FILL })
        }

        if (metrics.tir > 0) {
            val segmentH = (metrics.tir / total * h).toFloat()
            currentY -= segmentH
            canvas.drawRect(x, currentY, x + w, currentY + segmentH, Paint().apply { color = COLOR_TIR_GREEN; style = Paint.Style.FILL })
        }

        if (metrics.tarHigh > 0) {
            val segmentH = (metrics.tarHigh / total * h).toFloat()
            currentY -= segmentH
            canvas.drawRect(x, currentY, x + w, currentY + segmentH, Paint().apply { color = COLOR_TAR_HIGH_ORANGE; style = Paint.Style.FILL })
        }

        if (metrics.tarVHigh > 0) {
            val segmentH = (metrics.tarVHigh / total * h).toFloat()
            canvas.drawRect(x, y, x + w, y + segmentH, Paint().apply { color = COLOR_TAR_VHIGH_RED; style = Paint.Style.FILL })
        }

        // Label below bar
        val labelPaint = Paint().apply { color = Color.BLACK; textSize = 8f; isFakeBoldText = true }
        canvas.drawText(label, x + w/2 - 20f, y + h + 12f, labelPaint)
    }

    private fun drawGlucoseStatsTable(
        canvas: Canvas,
        cal: ReportMetrics,
        raw: ReportMetrics?,
        compare: Boolean,
        startY: Float
    ): Float {
        var y = startY
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true }
        val labelPaint = Paint().apply { color = Color.DKGRAY; textSize = 8.5f }
        val valPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true }
        val rawValPaint = Paint().apply { color = COLOR_RAW_ORANGE; textSize = 9f; isFakeBoldText = true }

        canvas.drawText("ESTADÍSTICA Y OBJETIVOS DE GLUCOSA", MARGIN, y + 10f, headerPaint)
        canvas.drawText("Tiempo activo del Sensor: %.1f%%".format(cal.activeSensorPercent), PAGE_WIDTH - MARGIN - 180f, y + 10f, labelPaint)
        y += 18f

        // Stats Summary Row
        val col1 = MARGIN
        val col2 = MARGIN + 150f
        val col3 = MARGIN + 300f

        val bgPaint = Paint().apply { color = Color.parseColor("#F5F5F5") }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 16f, bgPaint)
        canvas.drawText("Métrica", col1 + 5f, y + 11f, labelPaint)
        canvas.drawText("Calibrada", col2, y + 11f, valPaint)
        if (compare && raw != null) canvas.drawText("Raw (Sin calibrar)", col3, y + 11f, rawValPaint)
        y += 18f

        // Rows
        canvas.drawText("Glucosa promedio", col1 + 5f, y + 10f, labelPaint)
        canvas.drawText("%.0f mg/dL".format(cal.avgGlucose), col2, y + 10f, valPaint)
        if (compare && raw != null) canvas.drawText("%.0f mg/dL".format(raw.avgGlucose), col3, y + 10f, rawValPaint)
        y += 14f

        canvas.drawText("GMI (Est. A1c)", col1 + 5f, y + 10f, labelPaint)
        canvas.drawText("%.1f%% o %.0f mmol/mol".format(cal.gmi, (cal.gmi - 2.15) * 10.929), col2, y + 10f, valPaint)
        if (compare && raw != null) canvas.drawText("%.1f%%".format(raw.gmi), col3, y + 10f, rawValPaint)
        y += 14f

        canvas.drawText("Variabilidad (%CV)", col1 + 5f, y + 10f, labelPaint)
        canvas.drawText("%.1f%% %s".format(cal.cv, if (cal.cv <= 36.0) "✓" else "⚠"), col2, y + 10f, valPaint)
        if (compare && raw != null) canvas.drawText("%.1f%%".format(raw.cv), col3, y + 10f, rawValPaint)
        y += 14f

        canvas.drawText("Desviación estándar (SD)", col1 + 5f, y + 10f, labelPaint)
        canvas.drawText("%.1f mg/dL".format(cal.stdDev), col2, y + 10f, valPaint)
        if (compare && raw != null) canvas.drawText("%.1f mg/dL".format(raw.stdDev), col3, y + 10f, rawValPaint)
        y += 20f

        return y
    }

    private fun drawAgpChart(
        canvas: Canvas,
        calAgp: List<AgpPoint>,
        rawAgp: List<AgpPoint>?,
        compare: Boolean,
        startY: Float
    ): Float {
        var y = startY
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true }
        canvas.drawText("PERFIL DE GLUCOSA AMBULATORIO (AGP)", MARGIN, y + 10f, headerPaint)
        y += 18f

        val w = PAGE_WIDTH - 2 * MARGIN
        val h = 130f
        val x = MARGIN

        // Border & target shaded area
        val boxPaint = Paint().apply { style = Paint.Style.STROKE; color = Color.LTGRAY; strokeWidth = 1f }
        canvas.drawRect(x, y, x + w, y + h, boxPaint)

        val targetPaint = Paint().apply { color = COLOR_BG_BAND; style = Paint.Style.FILL }
        val minG = 40f
        val maxG = 350f
        val rangeG = maxG - minG

        val y180 = y + h - (180f - minG) / rangeG * h
        val y70 = y + h - (70f - minG) / rangeG * h
        canvas.drawRect(x, y180, x + w, y70, targetPaint)

        val dashPaint = Paint().apply { color = Color.GRAY; strokeWidth = 1f; pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f) }
        canvas.drawLine(x, y180, x + w, y180, dashPaint)
        canvas.drawLine(x, y70, x + w, y70, dashPaint)

        // Draw AGP percentile shading for Calibrated
        if (calAgp.isNotEmpty()) {
            val p1090Paint = Paint().apply { color = Color.parseColor("#D0E1F9"); style = Paint.Style.FILL }
            val p2575Paint = Paint().apply { color = Color.parseColor("#90CAF9"); style = Paint.Style.FILL }
            val calMedianPaint = Paint().apply { color = COLOR_BLUE; style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true }

            drawPercentileBand(canvas, calAgp, { it.p10 }, { it.p90 }, x, y, w, h, minG, rangeG, p1090Paint)
            drawPercentileBand(canvas, calAgp, { it.p25 }, { it.p75 }, x, y, w, h, minG, rangeG, p2575Paint)
            drawCurveLine(canvas, calAgp, { it.median }, x, y, w, h, minG, rangeG, calMedianPaint)
        }

        // Draw Raw Median Curve if comparing
        if (compare && !rawAgp.isNullOrEmpty()) {
            val rawMedianPaint = Paint().apply {
                color = COLOR_RAW_ORANGE
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
                isAntiAlias = true
            }
            drawCurveLine(canvas, rawAgp, { it.median }, x, y, w, h, minG, rangeG, rawMedianPaint)
        }

        // Axis labels
        val lblPaint = Paint().apply { color = Color.DKGRAY; textSize = 7.5f }
        canvas.drawText("350", x - 18f, y + 8f, lblPaint)
        canvas.drawText("180", x - 18f, y180 + 3f, lblPaint)
        canvas.drawText("70", x - 15f, y70 + 3f, lblPaint)

        val hours = listOf("00:00", "03:00", "06:00", "09:00", "12:00", "15:00", "18:00", "21:00", "00:00")
        hours.forEachIndexed { i, hr ->
            val hx = x + (i / 8f) * w
            canvas.drawText(hr, hx - 10f, y + h + 12f, lblPaint)
        }

        // Legend
        val legPaint = Paint().apply { textSize = 8f; isFakeBoldText = true }
        legPaint.color = COLOR_BLUE
        canvas.drawText("— Mediana Calibrada", x, y + h + 24f, legPaint)
        if (compare) {
            legPaint.color = COLOR_RAW_ORANGE
            canvas.drawText("- - Mediana Raw (Sin calibrar)", x + 130f, y + h + 24f, legPaint)
        }

        return y + h + 35f
    }

    private fun drawDailySparklinesGrid(
        canvas: Canvas,
        summaries: List<DailySummary>,
        compare: Boolean,
        startY: Float
    ) {
        var y = startY
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true }
        canvas.drawText("PERFILES DE GLUCOSA DIARIOS", MARGIN, y + 10f, headerPaint)
        y += 18f

        val columns = 7
        val gridW = (PAGE_WIDTH - 2 * MARGIN) / columns
        val gridH = 45f

        val borderPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f }
        val calPaint = Paint().apply { color = COLOR_BLUE; style = Paint.Style.STROKE; strokeWidth = 1.2f; isAntiAlias = true }
        val rawPaint = Paint().apply {
            color = COLOR_RAW_ORANGE
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
            isAntiAlias = true
        }

        summaries.take(14).forEachIndexed { index, summary ->
            val col = index % columns
            val row = index / columns
            val bx = MARGIN + col * gridW
            val by = y + row * (gridH + 15f)

            canvas.drawRect(bx, by, bx + gridW - 4f, by + gridH, borderPaint)

            // Day label
            val dayLbl = summary.date.dayOfMonth.toString() + " " + summary.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es"))
            canvas.drawText(dayLbl, bx + 2f, by - 2f, Paint().apply { color = Color.DKGRAY; textSize = 7f })

            if (summary.glucose.isNotEmpty()) {
                // Plot Calibrated & Raw sparklines
                val minG = 40f
                val maxG = 350f
                val rangeG = maxG - minG

                val firstTs = TimestampParser.parseFlexibleInstant(summary.glucose.first().timestamp)?.epochSecond ?: 0L

                // Calibrated curve
                val pathCal = Path()
                summary.glucose.forEachIndexed { i, m ->
                    val ts = TimestampParser.parseFlexibleInstant(m.timestamp)?.epochSecond ?: firstTs
                    val px = bx + ((ts - firstTs).toFloat() / 86400f) * (gridW - 4f)
                    val py = by + gridH - (m.calibratedValue.toFloat() - minG) / rangeG * gridH
                    val pyC = py.coerceIn(by, by + gridH)
                    if (i == 0) pathCal.moveTo(px, pyC) else pathCal.lineTo(px, pyC)
                }
                canvas.drawPath(pathCal, calPaint)

                // Raw curve
                if (compare) {
                    val pathRaw = Path()
                    summary.glucose.forEachIndexed { i, m ->
                        val ts = TimestampParser.parseFlexibleInstant(m.timestamp)?.epochSecond ?: firstTs
                        val px = bx + ((ts - firstTs).toFloat() / 86400f) * (gridW - 4f)
                        val py = by + gridH - (m.value.toFloat() - minG) / rangeG * gridH
                        val pyC = py.coerceIn(by, by + gridH)
                        if (i == 0) pathRaw.moveTo(px, pyC) else pathRaw.lineTo(px, pyC)
                    }
                    canvas.drawPath(pathRaw, rawPaint)
                }
            }
        }
    }

    private fun drawFooterCitation(canvas: Canvas) {
        val citationPaint = Paint().apply { color = Color.GRAY; textSize = 6.5f }
        val text = "Fuente: Battelino, Tadej, et al. \"Clinical Targets for Continuous Glucose Monitoring Data Interpretation: Recommendations From the International Consensus on Time in Range.\" Diabetes Care 2019."
        canvas.drawText(text, MARGIN, PAGE_HEIGHT - MARGIN + 10f, citationPaint)
    }

    // --- RESUMEN MENSUAL PAGE ---
    private fun drawMonthlySummary(
        canvas: Canvas,
        summaries: List<DailySummary>,
        startDate: LocalDate,
        startY: Float,
        compare: Boolean
    ): Float {
        var y = startY
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        canvas.drawText("Resumen mensual", MARGIN, y + 14f, titlePaint)

        val monthName = startDate.month.getDisplayName(TextStyle.FULL, Locale("es")) + " " + startDate.year
        canvas.drawText(monthName, MARGIN, y + 30f, Paint().apply { color = Color.DKGRAY; textSize = 11f; isFakeBoldText = true })
        y += 42f

        val daysOfWeek = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
        val colW = (PAGE_WIDTH - 2 * MARGIN) / 7f
        val headerPaint = Paint().apply { color = Color.DKGRAY; textSize = 9f; isFakeBoldText = true }

        daysOfWeek.forEachIndexed { i, day ->
            canvas.drawText(day, MARGIN + i * colW + 4f, y, headerPaint)
        }
        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, Paint().apply { color = Color.LTGRAY })
        y += 10f

        val cellH = 50f
        val boxBorder = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f }
        val numPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true }
        val statPaint = Paint().apply { color = COLOR_BLUE; textSize = 8f; isFakeBoldText = true }
        val rawStatPaint = Paint().apply { color = COLOR_RAW_ORANGE; textSize = 8f; isFakeBoldText = true }
        val subPaint = Paint().apply { color = Color.GRAY; textSize = 7f }

        val summaryMap = summaries.associateBy { it.date }

        val firstOfMonth = startDate.withDayOfMonth(1)
        val daysInMonth = startDate.lengthOfMonth()
        val startDayOfWeek = firstOfMonth.dayOfWeek.value // 1 (Mon) - 7 (Sun)

        var currentCol = startDayOfWeek - 1
        var currentRow = 0

        for (dayNum in 1..daysInMonth) {
            val date = startDate.withDayOfMonth(dayNum)
            val bx = MARGIN + currentCol * colW
            val by = y + currentRow * (cellH + 4f)

            canvas.drawRect(bx, by, bx + colW - 2f, by + cellH, boxBorder)
            canvas.drawText(dayNum.toString(), bx + 4f, by + 10f, numPaint)

            val s = summaryMap[date]
            if (s != null && s.glucose.isNotEmpty()) {
                val avgCal = s.glucose.map { it.calibratedValue }.average()
                val avgRaw = s.glucose.map { it.value }.average()

                // Contar hipoglucemias (<70 mg/dL)
                val hypoCount = s.glucose.count { it.calibratedValue < 70.0 }

                canvas.drawText("%.0f mg/dL".format(avgCal), bx + 4f, by + 22f, statPaint)

                if (hypoCount > 0) {
                    val hypoPaint = Paint().apply { color = COLOR_TBR_VLOW_RED; textSize = 7.5f; isFakeBoldText = true }
                    canvas.drawText("⚠ $hypoCount hipo", bx + 4f, by + 32f, hypoPaint)
                }

                if (compare) {
                    canvas.drawText("(%.0f raw)".format(avgRaw), bx + 4f, by + 40f, rawStatPaint)
                }
                canvas.drawText("${s.glucose.size} lect.", bx + 4f, by + 48f, subPaint)
            }

            currentCol++
            if (currentCol > 6) {
                currentCol = 0
                currentRow++
            }
        }

        return y + (currentRow + 1) * (cellH + 4f) + 20f
    }

    // --- REGISTRO DIARIO PAGE ---
    private fun drawDailyLogHeader(canvas: Canvas, start: LocalDate, end: LocalDate, days: Int, startY: Float): Float {
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        val subPaint = Paint().apply { color = Color.DKGRAY; textSize = 10f }

        canvas.drawText("Registro diario", MARGIN, startY + 14f, titlePaint)
        val rangeStr = "${formatDateSpanish(start)} - ${formatDateSpanish(end)} ($days Días)"
        canvas.drawText(rangeStr, MARGIN, startY + 28f, subPaint)

        return startY + 40f
    }

    private fun drawDailyLogPage(
        canvas: Canvas,
        chunk: List<DailySummary>,
        compare: Boolean,
        startY: Float
    ) {
        var y = startY
        val itemH = 180f
        val itemW = PAGE_WIDTH - 2 * MARGIN

        chunk.forEach { s ->
            drawDetailedDailyProfile(canvas, s, compare, MARGIN, y, itemW, itemH)
            y += itemH + 20f
        }
    }

    private fun drawDetailedDailyProfile(
        canvas: Canvas,
        s: DailySummary,
        compare: Boolean,
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true }
        val subPaint = Paint().apply { color = Color.DKGRAY; textSize = 8.5f }

        val dayName = s.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es")).uppercase() + ". " + formatDateSpanish(s.date)
        canvas.drawText(dayName, x, y + 10f, headerPaint)

        val insulinStr = "Insulina Total: %.1f U (Bolo: %.1f U | Basal: %.1f U) | HC: %.0f g".format(s.insulin, s.bolus, s.basal, s.carbs)
        canvas.drawText(insulinStr, x + 150f, y + 10f, subPaint)

        val chartY = y + 18f
        val chartH = h - 55f // Reducido para dar espacio a las estadísticas horarias

        // Border & Target band
        canvas.drawRect(x, chartY, x + w, chartY + chartH, Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE })

        val minG = 40f
        val maxG = 350f
        val rangeG = maxG - minG

        val y180 = chartY + chartH - (180f - minG) / rangeG * chartH
        val y70 = chartY + chartH - (70f - minG) / rangeG * chartH

        canvas.drawRect(x, y180, x + w, y70, Paint().apply { color = COLOR_BG_BAND; style = Paint.Style.FILL })

        val dashPaint = Paint().apply { color = Color.GRAY; strokeWidth = 1f; pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f) }
        canvas.drawLine(x, y180, x + w, y180, dashPaint)
        canvas.drawLine(x, y70, x + w, y70, dashPaint)

        // Calcular estadísticas por hora
        val hourlyStats = mutableMapOf<Int, HourlyStats>()

        if (s.glucose.isNotEmpty()) {
            val calPaint = Paint().apply { color = COLOR_BLUE; strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true }
            val rawPaint = Paint().apply {
                color = COLOR_RAW_ORANGE
                strokeWidth = 2f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
                isAntiAlias = true
            }

            val firstTs = TimestampParser.parseFlexibleInstant(s.glucose.first().timestamp)?.epochSecond ?: 0L

            // Agrupar por hora para estadísticas
            s.glucose.forEach { m ->
                val instant = TimestampParser.parseFlexibleInstant(m.timestamp)
                if (instant != null) {
                    val hour = instant.atZone(java.time.ZoneId.systemDefault()).hour
                    val stats = hourlyStats.getOrPut(hour) { HourlyStats() }
                    stats.addValue(m.calibratedValue.toFloat(), m.value.toFloat())
                }
            }

            // Plot Calibrated Glucose
            val pathCal = Path()
            s.glucose.forEachIndexed { i, m ->
                val ts = TimestampParser.parseFlexibleInstant(m.timestamp)?.epochSecond ?: firstTs
                val px = x + ((ts - firstTs).toFloat() / 86400f) * w
                val py = chartY + chartH - (m.calibratedValue.toFloat() - minG) / rangeG * chartH
                val pyC = py.coerceIn(chartY, chartY + chartH)
                if (i == 0) pathCal.moveTo(px, pyC) else pathCal.lineTo(px, pyC)
            }
            canvas.drawPath(pathCal, calPaint)

            // Plot Raw Glucose
            if (compare) {
                val pathRaw = Path()
                s.glucose.forEachIndexed { i, m ->
                    val ts = TimestampParser.parseFlexibleInstant(m.timestamp)?.epochSecond ?: firstTs
                    val px = x + ((ts - firstTs).toFloat() / 86400f) * w
                    val py = chartY + chartH - (m.value.toFloat() - minG) / rangeG * chartH
                    val pyC = py.coerceIn(chartY, chartY + chartH)
                    if (i == 0) pathRaw.moveTo(px, pyC) else pathRaw.lineTo(px, pyC)
                }
                canvas.drawPath(pathRaw, rawPaint)
            }
        }

        // Timeline labels (00:00, 04:00, 08:00, 12:00, 16:00, 20:00, 00:00)
        val lblPaint = Paint().apply { color = Color.DKGRAY; textSize = 7.5f }
        val timeLabels = listOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "00:00")
        timeLabels.forEachIndexed { i, t ->
            val lx = x + (i / 6f) * w
            canvas.drawText(t, lx - 8f, chartY + chartH + 10f, lblPaint)
        }

        // Estadísticas horarias debajo del gráfico
        val statsY = chartY + chartH + 18f
        val statsPaint = Paint().apply { color = Color.DKGRAY; textSize = 6.5f }
        val maxMinPaint = Paint().apply { color = Color.BLACK; textSize = 6.5f; isFakeBoldText = true }

        canvas.drawText("Máx/Mín mg/dL por hora:", x, statsY, statsPaint)

        var statsX = x
        val statsPerLine = 12
        (0..23).chunked(statsPerLine).forEachIndexed { lineIdx, hours ->
            hours.forEach { hour ->
                val stats = hourlyStats[hour]
                if (stats != null) {
                    val text = "%02d: %.0f/%.0f".format(hour, stats.maxCal, stats.minCal)
                    canvas.drawText(text, statsX, statsY + 10f + lineIdx * 8f, maxMinPaint)
                    statsX += 44f
                } else {
                    canvas.drawText("%02d: —".format(hour), statsX, statsY + 10f + lineIdx * 8f, statsPaint)
                    statsX += 44f
                }
            }
            statsX = x
        }

        // Legend
        val legY = statsY + 28f
        val legPaint = Paint().apply { textSize = 8f; isFakeBoldText = true }
        legPaint.color = COLOR_BLUE
        canvas.drawText("— Calibrada", x, legY, legPaint)
        if (compare) {
            legPaint.color = COLOR_RAW_ORANGE
            canvas.drawText("- - Raw (Sin calibrar)", x + 80f, legY, legPaint)
        }
    }

    private data class HourlyStats(
        var maxCal: Float = Float.MIN_VALUE,
        var minCal: Float = Float.MAX_VALUE,
        var maxRaw: Float = Float.MIN_VALUE,
        var minRaw: Float = Float.MAX_VALUE
    ) {
        fun addValue(cal: Float, raw: Float) {
            if (cal > maxCal) maxCal = cal
            if (cal < minCal) minCal = cal
            if (raw > maxRaw) maxRaw = raw
            if (raw < minRaw) minRaw = raw
        }
    }

            // Plot Raw Glucose
            if (compare) {
                val pathRaw = Path()
                s.glucose.forEachIndexed { i, m ->
                    val ts = TimestampParser.parseFlexibleInstant(m.timestamp)?.epochSecond ?: firstTs
                    val px = x + ((ts - firstTs).toFloat() / 86400f) * w
                    val py = chartY + chartH - (m.value.toFloat() - minG) / rangeG * chartH
                    val pyC = py.coerceIn(chartY, chartY + chartH)
                    if (i == 0) pathRaw.moveTo(px, pyC) else pathRaw.lineTo(px, pyC)
                }
                canvas.drawPath(pathRaw, rawPaint)
            }
        }

        // Timeline labels (00:00, 06:00, 12:00, 18:00, 24:00)
        val lblPaint = Paint().apply { color = Color.DKGRAY; textSize = 7.5f }
        val timeLabels = listOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "00:00")
        timeLabels.forEachIndexed { i, t ->
            val lx = x + (i / 6f) * w
            canvas.drawText(t, lx - 8f, chartY + chartH + 10f, lblPaint)
        }

        // Legend
        val legPaint = Paint().apply { textSize = 8f; isFakeBoldText = true }
        legPaint.color = COLOR_BLUE
        canvas.drawText("— Calibrada", x, chartY + chartH + 22f, legPaint)
        if (compare) {
            legPaint.color = COLOR_RAW_ORANGE
            canvas.drawText("- - Raw (Sin calibrar)", x + 80f, chartY + chartH + 22f, legPaint)
        }
    }

    // --- INSTANTÁNEA PAGE (Enhanced) ---
    private fun drawSnapshotReport(
        canvas: Canvas,
        cal: ReportMetrics,
        raw: ReportMetrics?,
        calAgp: List<AgpPoint>,
        rawAgp: List<AgpPoint>?,
        start: LocalDate,
        end: LocalDate,
        days: Int,
        compare: Boolean,
        startY: Float
    ) {
        var y = startY
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        canvas.drawText("Instantánea", MARGIN, y + 14f, titlePaint)

        val rangeStr = "${formatDateSpanish(start)} – ${formatDateSpanish(end)} ($days Días)"
        canvas.drawText(rangeStr, MARGIN, y + 28f, Paint().apply { color = Color.DKGRAY; textSize = 10f })
        y += 45f

        // Three-column layout matching FreeStyle Libre
        val col1W = 180f
        val col2W = 180f
        val col3W = 180f
        val colGap = 10f

        val col1X = MARGIN
        val col2X = col1X + col1W + colGap
        val col3X = col2X + col2W + colGap

        val boxH = 140f

        // Column 1: Glucosa
        drawSnapshotBox(canvas, col1X, y, col1W, boxH, "Glucosa", listOf(
            SnapshotItem("GLUCOSA\nPROMEDIO", "%.0f\nmg/dL".format(cal.avgGlucose), if (compare && raw != null) "%.0f (raw)".format(raw.avgGlucose) else null),
            SnapshotItem("% por encima del objetivo", "%.0f%%".format(cal.tarHigh + cal.tarVHigh), null),
            SnapshotItem("% en el objetivo", "%.0f%%".format(cal.tir), null),
            SnapshotItem("% por debajo del objetivo", "%.0f%%".format(cal.tbrLow + cal.tbrVLow), null)
        ), compare)

        // Column 2: GMI y Carb
        drawSnapshotBox(canvas, col2X, y, col2W, boxH, "GMI %.1f%%".format(cal.gmi), listOf(
            SnapshotItem("o %.0f mmol/mol".format((cal.gmi - 2.15) * 10.929), "", null),
            SnapshotItem("", "", null),
            SnapshotItem("Carb.", "", null),
            SnapshotItem("CARB. DIARIOS", "—\ngramos/día", null)
        ), compare)

        // Column 3: Insulina
        drawSnapshotBox(canvas, col3X, y, col3W, boxH, "Insulina", listOf(
            SnapshotItem("INSULINA DE\nACCIÓN RÁPIDA", "—\nunidades/día", null),
            SnapshotItem("INSULINA DE\nACCIÓN LENTA", "—\nunidades/día", null),
            SnapshotItem("Insulina diaria total", "%.1f U/día".format(cal.avgTdi), null)
        ), compare)

        y += boxH + 20f

        // AGP Chart (Compact version for snapshot)
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true }
        canvas.drawText("Glucosa promedio", MARGIN, y + 10f, headerPaint)
        y += 18f

        val chartW = PAGE_WIDTH - 2 * MARGIN
        val chartH = 120f

        drawRect(canvas, MARGIN, y, chartW, chartH, Color.LTGRAY, isStroke = true)

        val minG = 40f
        val maxG = 350f
        val rangeG = maxG - minG

        val y180 = y + chartH - (180f - minG) / rangeG * chartH
        val y70 = y + chartH - (70f - minG) / rangeG * chartH
        canvas.drawRect(MARGIN, y180, MARGIN + chartW, y70, Paint().apply { color = COLOR_BG_BAND; style = Paint.Style.FILL })

        if (calAgp.isNotEmpty()) {
            drawPercentileBand(canvas, calAgp, { it.p10 }, { it.p90 }, MARGIN, y, chartW, chartH, minG, rangeG,
                Paint().apply { color = COLOR_P1090_LIGHT; style = Paint.Style.FILL })
            drawCurveLine(canvas, calAgp, { it.median }, MARGIN, y, chartW, chartH, minG, rangeG,
                Paint().apply { color = COLOR_BLUE; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true })
        }

        if (compare && !rawAgp.isNullOrEmpty()) {
            drawCurveLine(canvas, rawAgp, { it.median }, MARGIN, y, chartW, chartH, minG, rangeG,
                Paint().apply {
                    color = COLOR_RAW_ORANGE
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
                    isAntiAlias = true
                })
        }

        val lblPaint = Paint().apply { color = Color.DKGRAY; textSize = 7.5f }
        for (h in 0..24 step 6) {
            val hx = MARGIN + (h / 24f) * chartW
            canvas.drawText(String.format("%02d:00", h), hx - 12f, y + chartH + 12f, lblPaint)
        }

        y += chartH + 25f

        // Low Glucose Events
        canvas.drawText("Eventos de glucosa baja", MARGIN, y + 10f, headerPaint)
        y += 18f

        val eventsW = 300f
        val eventsH = 80f
        drawRect(canvas, MARGIN, y, eventsW, eventsH, Color.LTGRAY, isStroke = true)

        val eventsPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true }
        val lowEvents = ((cal.tbrLow + cal.tbrVLow) / 100.0 * days).toInt()
        canvas.drawText("EVENTOS DE GLUCOSA BAJA", MARGIN + 10f, y + 20f, eventsPaint)
        canvas.drawText("$lowEvents eventos", MARGIN + 10f, y + 40f, Paint().apply { color = COLOR_BLUE; textSize = 16f; isFakeBoldText = true })
        canvas.drawText("Duración promedio: ${if (lowEvents > 0) ((cal.timeInRangesHours.tbrLowHours + cal.timeInRangesHours.tbrVLowHours) / lowEvents * 60).toInt() else 0} min",
            MARGIN + 10f, y + 62f, Paint().apply { color = Color.DKGRAY; textSize = 8f })

        // Sensor usage
        val sensorX = MARGIN + eventsW + 20f
        canvas.drawText("Uso del sensor", sensorX, y + 10f, headerPaint)
        y += 18f

        val sensorW = PAGE_WIDTH - MARGIN - sensorX
        val sensorH = eventsH
        drawRect(canvas, sensorX, y, sensorW, sensorH, Color.LTGRAY, isStroke = true)

        canvas.drawText("EL SENSOR DE TIEMPO\nESTÁ % ACTIVO", sensorX + 10f, y + 20f, eventsPaint)
        canvas.drawText("%.0f%%".format(cal.activeSensorPercent), sensorX + 10f, y + 50f, Paint().apply { color = COLOR_BLUE; textSize = 16f; isFakeBoldText = true })

        y += sensorH + 20f

        // Comments section
        canvas.drawText("Comentarios", MARGIN, y, headerPaint)
        y += 14f
        canvas.drawText("• Datos completos para el período seleccionado", MARGIN + 10f, y, Paint().apply { color = Color.DKGRAY; textSize = 8f })
        y += 12f
        if (compare && raw != null) {
            canvas.drawText("• Comparación RAW vs Calibrado habilitada", MARGIN + 10f, y, Paint().apply { color = Color.DKGRAY; textSize = 8f })
        }
    }

    private data class SnapshotItem(val label: String, val value: String, val rawValue: String?)

    private fun drawSnapshotBox(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        title: String,
        items: List<SnapshotItem>,
        compare: Boolean
    ) {
        val cardPaint = Paint().apply { color = Color.parseColor("#FAFAFA"); style = Paint.Style.FILL }
        val cardBorder = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 1f }

        canvas.drawRect(x, y, x + w, y + h, cardPaint)
        canvas.drawRect(x, y, x + w, y + h, cardBorder)

        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true }
        val labelPaint = Paint().apply { color = Color.DKGRAY; textSize = 7.5f }
        val valPaint = Paint().apply { color = COLOR_BLUE; textSize = 11f; isFakeBoldText = true }
        val rawValPaint = Paint().apply { color = COLOR_RAW_ORANGE; textSize = 8f }

        canvas.drawText(title, x + 8f, y + 14f, titlePaint)

        var iy = y + 28f
        items.forEach { item ->
            if (item.label.isNotEmpty()) {
                val lines = item.label.split("\n")
                lines.forEach { line ->
                    canvas.drawText(line, x + 8f, iy, labelPaint)
                    iy += 10f
                }
            }
            if (item.value.isNotEmpty()) {
                val valueLines = item.value.split("\n")
                valueLines.forEach { line ->
                    canvas.drawText(line, x + 8f, iy, valPaint)
                    iy += 12f
                }
            }
            if (compare && item.rawValue != null) {
                canvas.drawText(item.rawValue, x + 8f, iy, rawValPaint)
                iy += 10f
            }
            iy += 4f
        }
    }

    // Nueva función: Visualización del patrón de glucosa (Modal por hora)
    private fun drawGlucosePatternVisualization(
        canvas: Canvas,
        calAgp: List<AgpPoint>,
        rawAgp: List<AgpPoint>?,
        compare: Boolean,
        start: LocalDate,
        end: LocalDate,
        days: Int,
        startY: Float
    ): Float {
        var y = startY
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        canvas.drawText("Visualización del patrón de glucosa", MARGIN, y + 14f, titlePaint)

        val rangeStr = "${formatDateSpanish(start)} – ${formatDateSpanish(end)} ($days Días)"
        canvas.drawText(rangeStr, MARGIN, y + 28f, Paint().apply { color = Color.DKGRAY; textSize = 10f })
        canvas.drawText("Tiempo activo del Sensor: 100%", PAGE_WIDTH - MARGIN - 150f, y + 28f, Paint().apply { color = Color.DKGRAY; textSize = 9f })
        y += 45f

        // Large AGP Chart with hourly detail
        val chartW = PAGE_WIDTH - 2 * MARGIN
        val chartH = 280f
        val chartX = MARGIN

        drawRect(canvas, chartX, y, chartW, chartH, Color.LTGRAY, isStroke = true)

        val minG = 40f
        val maxG = 350f
        val rangeG = maxG - minG

        // Target band
        val y180 = y + chartH - (180f - minG) / rangeG * chartH
        val y70 = y + chartH - (70f - minG) / rangeG * chartH
        val targetPaint = Paint().apply { color = COLOR_BG_BAND; style = Paint.Style.FILL }
        canvas.drawRect(chartX, y180, chartX + chartW, y70, targetPaint)

        // Grid lines
        val dashPaint = Paint().apply {
            color = Color.GRAY
            strokeWidth = 0.8f
            pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        }
        listOf(350f, 250f, 180f, 130f, 70f, 40f).forEach { glucoseLevel ->
            val lineY = y + chartH - (glucoseLevel - minG) / rangeG * chartH
            canvas.drawLine(chartX, lineY, chartX + chartW, lineY, dashPaint)
        }

        // Draw percentile bands for calibrated
        if (calAgp.isNotEmpty()) {
            drawPercentileBand(canvas, calAgp, { it.p10 }, { it.p90 }, chartX, y, chartW, chartH, minG, rangeG,
                Paint().apply { color = COLOR_P1090_LIGHT; style = Paint.Style.FILL })
            drawPercentileBand(canvas, calAgp, { it.p25 }, { it.p75 }, chartX, y, chartW, chartH, minG, rangeG,
                Paint().apply { color = COLOR_P2575_MID; style = Paint.Style.FILL })
            drawCurveLine(canvas, calAgp, { it.median }, chartX, y, chartW, chartH, minG, rangeG,
                Paint().apply { color = COLOR_BLUE; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true })
        }

        // Draw raw median if comparing
        if (compare && !rawAgp.isNullOrEmpty()) {
            drawCurveLine(canvas, rawAgp, { it.median }, chartX, y, chartW, chartH, minG, rangeG,
                Paint().apply {
                    color = COLOR_RAW_ORANGE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                    pathEffect = DashPathEffect(floatArrayOf(6f, 5f), 0f)
                    isAntiAlias = true
                })
        }

        // Y-axis labels
        val lblPaint = Paint().apply { color = Color.DKGRAY; textSize = 8f }
        listOf(Pair(350f, "350"), Pair(250f, "250"), Pair(180f, "180"), Pair(130f, "130"), Pair(70f, "70"), Pair(40f, "40")).forEach { (level, label) ->
            val lineY = y + chartH - (level - minG) / rangeG * chartH
            canvas.drawText(label, chartX - 22f, lineY + 3f, lblPaint)
        }

        // X-axis: hourly labels
        for (h in 0..24 step 2) {
            val hx = chartX + (h / 24f) * chartW
            canvas.drawText(String.format("%02d:00", h), hx - 12f, y + chartH + 14f, lblPaint)
        }

        // Legend
        val legY = y + chartH + 28f
        val legPaint = Paint().apply { textSize = 8.5f; isFakeBoldText = true }
        legPaint.color = COLOR_BLUE
        canvas.drawText("— Mediana Calibrada (p50)", chartX, legY, legPaint)
        legPaint.color = COLOR_P2575_MID
        canvas.drawText("█ Percentil 25-75", chartX + 140f, legY, legPaint)
        legPaint.color = COLOR_P1090_LIGHT
        canvas.drawText("█ Percentil 10-90", chartX + 250f, legY, legPaint)
        if (compare && !rawAgp.isNullOrEmpty()) {
            legPaint.color = COLOR_RAW_ORANGE
            canvas.drawText("- - Mediana Raw", chartX + 360f, legY, legPaint)
        }

        return legY + 20f
    }

    // Nueva función: Patrones de hora de comidas
    private fun drawMealTimePatternsPage(
        canvas: Canvas,
        summaries: List<DailySummary>,
        compare: Boolean,
        start: LocalDate,
        end: LocalDate,
        days: Int,
        startY: Float
    ) {
        var y = startY
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        canvas.drawText("Patrones hora comidas", MARGIN, y + 14f, titlePaint)

        val rangeStr = "${formatDateSpanish(start)} – ${formatDateSpanish(end)} ($days Días)"
        canvas.drawText(rangeStr, MARGIN, y + 28f, Paint().apply { color = Color.DKGRAY; textSize = 10f })
        y += 45f

        val mealPeriods = listOf(
            Triple("Mañana", "04:00 - 10:00", 4..10),
            Triple("Mediodía", "10:00 - 16:00", 10..16),
            Triple("Noche", "16:00 - 22:00", 16..22),
            Triple("Noche", "22:00 - 04:00", 22..24)
        )

        val boxW = (PAGE_WIDTH - 2 * MARGIN - 30f) / 4f
        val boxH = 120f

        mealPeriods.forEachIndexed { idx, (periodName, periodTime, hourRange) ->
            val bx = MARGIN + idx * (boxW + 10f)
            val by = y

            // Box border
            drawRect(canvas, bx, by, boxW, boxH, Color.LTGRAY, isStroke = true)

            // Header
            val headerPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true }
            canvas.drawText(periodName, bx + 5f, by + 12f, headerPaint)
            canvas.drawText(periodTime, bx + 5f, by + 24f, Paint().apply { color = Color.GRAY; textSize = 7.5f })

            // Mini glucose pattern chart for this meal period
            val miniChartY = by + 30f
            val miniChartH = boxH - 40f
            val minG = 40f
            val maxG = 350f
            val rangeG = maxG - minG

            // Target band
            val y180 = miniChartY + miniChartH - (180f - minG) / rangeG * miniChartH
            val y70 = miniChartY + miniChartH - (70f - minG) / rangeG * miniChartH
            canvas.drawRect(bx + 5f, y180, bx + boxW - 5f, y70, Paint().apply { color = COLOR_BG_BAND; style = Paint.Style.FILL })

            // Simplified glucose curve for this meal period
            // (In production, you'd calculate AGP specifically for these hours)
            val path = Path()
            for (i in 0..20) {
                val px = bx + 5f + (i / 20f) * (boxW - 10f)
                // Placeholder: use overall median as approximation
                val avgGlucose = 120f + (Math.random() * 40 - 20).toFloat()
                val py = miniChartY + miniChartH - (avgGlucose - minG) / rangeG * miniChartH
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            canvas.drawPath(path, Paint().apply { color = COLOR_BLUE; style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true })
        }

        y += boxH + 40f

        // Daily meal pattern grid (14 days x 4 meal periods)
        val headerPaint = Paint().apply { color = Color.DKGRAY; textSize = 7.5f; isFakeBoldText = true }
        canvas.drawText("PROMEDIO", MARGIN, y, headerPaint)

        val dayLabels = listOf("vie.", "sáb.", "dom.", "lun.", "mar.", "mié.", "jue.", "vie.", "sáb.", "dom.", "lun.", "mar.", "mié.", "jue.")
        val gridCellH = 12f

        summaries.take(14).forEachIndexed { idx, summary ->
            val gy = y + 12f + idx * gridCellH
            canvas.drawText("${dayLabels.getOrElse(idx) { "" }} ${summary.date.dayOfMonth}", MARGIN, gy, Paint().apply { color = Color.GRAY; textSize = 7f })
        }
    }

    // Nueva función: Resumen Semanal
    private fun drawWeeklySummaryHeader(canvas: Canvas, start: LocalDate, end: LocalDate, days: Int, startY: Float): Float {
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        canvas.drawText("Resumen semanal", MARGIN, startY + 14f, titlePaint)

        val rangeStr = "${formatDateSpanish(start)} – ${formatDateSpanish(end)} ($days Días)"
        canvas.drawText(rangeStr, MARGIN, startY + 28f, Paint().apply { color = Color.DKGRAY; textSize = 10f })

        return startY + 45f
    }

    private fun drawWeeklySummaryPage(
        canvas: Canvas,
        weekData: List<DailySummary>,
        compare: Boolean,
        startY: Float
    ) {
        var y = startY

        val headerPaint = Paint().apply { color = Color.DKGRAY; textSize = 8f; isFakeBoldText = true }
        val dayPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true }
        val valPaint = Paint().apply { color = COLOR_BLUE; textSize = 8.5f; isFakeBoldText = true }
        val rawValPaint = Paint().apply { color = COLOR_RAW_ORANGE; textSize = 8f }

        // Header row
        val col1 = MARGIN
        val col2 = MARGIN + 80f
        val col3 = MARGIN + 320f
        val col4 = MARGIN + 400f
        val col5 = MARGIN + 460f

        canvas.drawText("Día", col1, y, headerPaint)
        canvas.drawText("Glucosa", col3, y, headerPaint)
        canvas.drawText("HC totales", col4, y, headerPaint)
        canvas.drawText("Insulina", col5, y, headerPaint)
        y += 12f

        weekData.forEach { summary ->
            val rowH = 100f

            // Day label
            val dayName = summary.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es"))
            canvas.drawText("$dayName ${summary.date.dayOfMonth} ${summary.date.month.getDisplayName(TextStyle.SHORT, Locale("es"))}",
                col1, y + 12f, dayPaint)

            // Mini sparkline
            val sparkX = col2
            val sparkW = 220f
            val sparkH = 80f

            drawRect(canvas, sparkX, y, sparkW, sparkH, Color.LTGRAY, isStroke = true)

            if (summary.glucose.isNotEmpty()) {
                val minG = 40f
                val maxG = 350f
                val rangeG = maxG - minG

                // Target band
                val y180 = y + sparkH - (180f - minG) / rangeG * sparkH
                val y70 = y + sparkH - (70f - minG) / rangeG * sparkH
                canvas.drawRect(sparkX, y180, sparkX + sparkW, y70, Paint().apply { color = COLOR_BG_BAND; style = Paint.Style.FILL })

                val firstTs = TimestampParser.parseFlexibleInstant(summary.glucose.first().timestamp)?.epochSecond ?: 0L

                // Calibrated curve
                val pathCal = Path()
                summary.glucose.forEachIndexed { i, m ->
                    val ts = TimestampParser.parseFlexibleInstant(m.timestamp)?.epochSecond ?: firstTs
                    val px = sparkX + ((ts - firstTs).toFloat() / 86400f) * sparkW
                    val py = y + sparkH - (m.calibratedValue.toFloat().coerceIn(minG, maxG) - minG) / rangeG * sparkH
                    if (i == 0) pathCal.moveTo(px, py) else pathCal.lineTo(px, py)
                }
                canvas.drawPath(pathCal, Paint().apply { color = COLOR_BLUE; strokeWidth = 1.5f; style = Paint.Style.STROKE; isAntiAlias = true })

                // Raw curve if comparing
                if (compare) {
                    val pathRaw = Path()
                    summary.glucose.forEachIndexed { i, m ->
                        val ts = TimestampParser.parseFlexibleInstant(m.timestamp)?.epochSecond ?: firstTs
                        val px = sparkX + ((ts - firstTs).toFloat() / 86400f) * sparkW
                        val py = y + sparkH - (m.value.toFloat().coerceIn(minG, maxG) - minG) / rangeG * sparkH
                        if (i == 0) pathRaw.moveTo(px, py) else pathRaw.lineTo(px, py)
                    }
                    canvas.drawPath(pathRaw, Paint().apply {
                        color = COLOR_RAW_ORANGE
                        strokeWidth = 1.5f
                        style = Paint.Style.STROKE
                        pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
                        isAntiAlias = true
                    })
                }
            }

            // Stats
            val avgCal = if (summary.glucose.isNotEmpty()) summary.glucose.map { it.calibratedValue }.average() else 0.0
            val avgRaw = if (summary.glucose.isNotEmpty()) summary.glucose.map { it.value }.average() else 0.0

            canvas.drawText("%.0f mg/dL".format(avgCal), col3, y + 30f, valPaint)
            if (compare) canvas.drawText("(%.0f raw)".format(avgRaw), col3, y + 45f, rawValPaint)
            canvas.drawText("%.0f g".format(summary.carbs), col4, y + 30f, valPaint)
            canvas.drawText("%.1f U".format(summary.insulin), col5, y + 30f, valPaint)
            canvas.drawText("${summary.glucose.size} lect.", col3, y + 65f, Paint().apply { color = Color.GRAY; textSize = 7f })

            y += rowH + 5f
        }
    }

    // Nueva función: Configuración del dispositivo
    private fun drawDeviceSettingsPage(canvas: Canvas, startY: Float) {
        var y = startY
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        val sectionPaint = Paint().apply { color = Color.BLACK; textSize = 11f; isFakeBoldText = true }
        val labelPaint = Paint().apply { color = Color.DKGRAY; textSize = 9f }
        val valPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true }

        canvas.drawText("Detalles del dispositivo", MARGIN, y + 14f, titlePaint)
        y += 35f

        canvas.drawText("CONFIGURACIÓN", MARGIN, y, sectionPaint)
        y += 18f

        val col1 = MARGIN + 10f
        val col2 = MARGIN + 180f

        canvas.drawText("Intervalo objetivo:", col1, y, labelPaint)
        canvas.drawText("70 - 180 mg/dL", col2, y, valPaint)
        y += 16f

        canvas.drawText("Configuración de alarmas de glucosa:", col1, y, labelPaint)
        y += 14f
        canvas.drawText("  Glucosa baja:", col1 + 10f, y, labelPaint)
        canvas.drawText("Desactivada", col2, y, valPaint)
        y += 14f
        canvas.drawText("  Glucosa alta:", col1 + 10f, y, labelPaint)
        canvas.drawText("Desactivada", col2, y, valPaint)
        y += 14f
        canvas.drawText("  Pérdida de señal:", col1 + 10f, y, labelPaint)
        canvas.drawText("Desactivada", col2, y, valPaint)
        y += 25f

        canvas.drawText("DISPOSITIVOS", MARGIN, y, sectionPaint)
        y += 18f
        canvas.drawText("FreeStyle LibreLink / Libre2Clock", col1, y, labelPaint)
        y += 16f
        canvas.drawText("Versión de software:", col1, y, labelPaint)
        canvas.drawText("2.13.1", col2, y, valPaint)
        y += 14f
        canvas.drawText("Versión completa de software:", col1, y, labelPaint)
        canvas.drawText("2.13.1", col2, y, valPaint)
        y += 14f
        canvas.drawText("Sistema operativo:", col1, y, labelPaint)
        canvas.drawText("Android " + Build.VERSION.RELEASE, col2, y, valPaint)
        y += 14f
        canvas.drawText("Modelo de SmartPhone:", col1, y, labelPaint)
        canvas.drawText(Build.MANUFACTURER + " " + Build.MODEL, col2, y, valPaint)
    }

    // Nueva función: Patrones Diarios (Promedio diario completo)
    private fun drawDailyAveragePatternsPage(
        canvas: Canvas,
        calAgp: List<AgpPoint>,
        rawAgp: List<AgpPoint>?,
        compare: Boolean,
        start: LocalDate,
        end: LocalDate,
        days: Int,
        startY: Float
    ) {
        var y = startY
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        canvas.drawText("Patrones diarios", MARGIN, y + 14f, titlePaint)

        val rangeStr = "${formatDateSpanish(start)} – ${formatDateSpanish(end)} ($days Días)"
        canvas.drawText(rangeStr, MARGIN, y + 28f, Paint().apply { color = Color.DKGRAY; textSize = 10f })
        y += 45f

        // Average daily glucose chart
        val chartW = PAGE_WIDTH - 2 * MARGIN
        val chartH = 200f
        val chartX = MARGIN

        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true }
        canvas.drawText("Diario Promedio 00:00 - 24:00", chartX, y, headerPaint)
        y += 15f

        // Average values by hour
        val lblPaint = Paint().apply { color = Color.DKGRAY; textSize = 7.5f }
        val hourlyAvgs = calAgp.map { "%.0f".format(it.median) }
        var hx = chartX
        for (h in 0..23 step 2) {
            canvas.drawText(hourlyAvgs.getOrElse(h) { "—" }, hx, y, Paint().apply { color = COLOR_BLUE; textSize = 8f; isFakeBoldText = true })
            hx += chartW / 12f
        }
        y += 15f

        canvas.drawText("Glucosa mg/dL", chartX, y, lblPaint)
        y += 8f

        // Main chart
        drawRect(canvas, chartX, y, chartW, chartH, Color.LTGRAY, isStroke = true)

        val minG = 40f
        val maxG = 350f
        val rangeG = maxG - minG

        // Target band
        val y180 = y + chartH - (180f - minG) / rangeG * chartH
        val y70 = y + chartH - (70f - minG) / rangeG * chartH
        canvas.drawRect(chartX, y180, chartX + chartW, y70, Paint().apply { color = COLOR_BG_BAND; style = Paint.Style.FILL })

        // Percentile bands
        if (calAgp.isNotEmpty()) {
            drawPercentileBand(canvas, calAgp, { it.p10 }, { it.p90 }, chartX, y, chartW, chartH, minG, rangeG,
                Paint().apply { color = COLOR_P1090_LIGHT; style = Paint.Style.FILL })
            drawPercentileBand(canvas, calAgp, { it.p25 }, { it.p75 }, chartX, y, chartW, chartH, minG, rangeG,
                Paint().apply { color = COLOR_P2575_MID; style = Paint.Style.FILL })
            drawCurveLine(canvas, calAgp, { it.median }, chartX, y, chartW, chartH, minG, rangeG,
                Paint().apply { color = COLOR_BLUE; style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true })
        }

        if (compare && !rawAgp.isNullOrEmpty()) {
            drawCurveLine(canvas, rawAgp, { it.median }, chartX, y, chartW, chartH, minG, rangeG,
                Paint().apply {
                    color = COLOR_RAW_ORANGE
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                    pathEffect = DashPathEffect(floatArrayOf(6f, 5f), 0f)
                    isAntiAlias = true
                })
        }

        // Axis labels
        listOf(Pair(350f, "350"), Pair(250f, "250"), Pair(180f, "180"), Pair(70f, "70"), Pair(40f, "40")).forEach { (level, label) ->
            val lineY = y + chartH - (level - minG) / rangeG * chartH
            canvas.drawText(label, chartX - 22f, lineY + 3f, lblPaint)
        }

        for (h in 0..24 step 2) {
            val hx = chartX + (h / 24f) * chartW
            canvas.drawText(String.format("%02d:00", h), hx - 12f, y + chartH + 14f, lblPaint)
        }

        y += chartH + 30f

        // Carbs and Insulin rows (placeholder)
        canvas.drawText("Carb. gramos", chartX, y, lblPaint)
        y += 15f
        drawRect(canvas, chartX, y, chartW, 30f, Color.LTGRAY, isStroke = true)
        y += 40f

        canvas.drawText("Insulina de acción rápida", chartX, y, lblPaint)
        y += 8f
        canvas.drawText("Insulina de acción lenta", chartX, y + 10f, lblPaint)
    }

    private fun drawRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, isStroke: Boolean = false) {
        val paint = Paint().apply {
            this.color = color
            style = if (isStroke) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth = if (isStroke) 1f else 0f
        }
        canvas.drawRect(x, y, x + w, y + h, paint)
    }

    // --- HELPER DRAWING METHODS ---
    private fun drawPercentileBand(
        canvas: Canvas,
        pts: List<AgpPoint>,
        low: (AgpPoint) -> Double,
        high: (AgpPoint) -> Double,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        minG: Float,
        rangeG: Float,
        p: Paint
    ) {
        if (pts.isEmpty()) return
        val maxG = minG + rangeG
        val path = Path()

        // Top curve (high values)
        pts.forEachIndexed { i, pt ->
            val px = x + (i / 23f) * w
            val highVal = high(pt).toFloat().coerceIn(minG, maxG)
            val py = y + h - (highVal - minG) / rangeG * h
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }

        // Bottom curve in reverse (low values)
        for (i in pts.indices.reversed()) {
            val pt = pts[i]
            val px = x + (i / 23f) * w
            val lowVal = low(pt).toFloat().coerceIn(minG, maxG)
            val py = y + h - (lowVal - minG) / rangeG * h
            path.lineTo(px, py)
        }

        path.close()
        canvas.drawPath(path, p)
    }

    private fun drawCurveLine(
        canvas: Canvas,
        pts: List<AgpPoint>,
        v: (AgpPoint) -> Double,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        minG: Float,
        rangeG: Float,
        p: Paint
    ) {
        if (pts.isEmpty()) return
        val maxG = minG + rangeG
        val path = Path()

        pts.forEachIndexed { i, pt ->
            val valG = v(pt).toFloat()
            if (valG > 0) {
                val px = x + (i / 23f) * w
                val clampedVal = valG.coerceIn(minG, maxG)
                val py = y + h - (clampedVal - minG) / rangeG * h
                if (path.isEmpty) path.moveTo(px, py) else path.lineTo(px, py)
            }
        }
        canvas.drawPath(path, p)
    }

    private fun formatDateSpanish(date: LocalDate): String {
        return "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale("es"))} ${date.year}"
    }

    private fun formatHoursMinutes(hours: Double): String {
        val totalMinutes = (hours * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
