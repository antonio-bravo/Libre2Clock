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

    private val COLOR_BLUE = Color.parseColor("#1A73E8")       // Calibrated
    private val COLOR_RAW_ORANGE = Color.parseColor("#E65100") // Raw
    private val COLOR_BG_BAND = Color.parseColor("#E8F5E9")

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

        // Page 1: Informe AGP
        if (layout == ReportLayout.SNAPSHOT || layout == ReportLayout.FULL) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter++).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawPageHeader(canvas, patientName, "1", "1", startDate, endDate)
            y = drawAgpReportHeader(canvas, startDate, endDate, spanDays, y)
            y = drawGlucoseStatsTable(canvas, metrics, rawMetrics, compareRawAndCalibrated, y)
            y = drawAgpChart(canvas, agpData, rawAgpData, compareRawAndCalibrated, y)
            drawDailySparklinesGrid(canvas, dailySummaries, compareRawAndCalibrated, y)
            drawFooterCitation(canvas)

            pdfDocument.finishPage(page)
        }

        // Page 2: Resumen mensual
        if (layout == ReportLayout.FULL) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter++).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawPageHeader(canvas, patientName, "1", "1", startDate, endDate)
            y = drawMonthlySummary(canvas, dailySummaries, startDate, y, compareRawAndCalibrated)

            pdfDocument.finishPage(page)
        }

        // Pages 3+: Registro Diario
        if (layout == ReportLayout.DAILY_LOG || layout == ReportLayout.FULL) {
            val chunks = dailySummaries.chunked(3)
            chunks.forEachIndexed { idx, chunk ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter++).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                var y = drawPageHeader(canvas, patientName, "${idx + 1}", "${chunks.size}", startDate, endDate)
                y = drawDailyLogHeader(canvas, startDate, endDate, spanDays, y)
                drawDailyLogPage(canvas, chunk, compareRawAndCalibrated, y)

                pdfDocument.finishPage(page)
            }
        }

        // Page: Instantánea
        if (layout == ReportLayout.SNAPSHOT || layout == ReportLayout.FULL) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter++).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawPageHeader(canvas, patientName, "1", "1", startDate, endDate)
            drawSnapshotReport(canvas, metrics, rawMetrics, startDate, endDate, spanDays, compareRawAndCalibrated, y)

            pdfDocument.finishPage(page)
        }

        // Page: Configuración
        if (layout == ReportLayout.FULL) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageCounter++).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            var y = drawPageHeader(canvas, patientName, "1", "1", startDate, endDate)
            drawSettingsPage(canvas, y)

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

        // Table Header
        val col1 = MARGIN
        val col2 = MARGIN + 170f
        val col3 = MARGIN + 290f
        val col4 = MARGIN + 400f

        val bgPaint = Paint().apply { color = Color.parseColor("#F5F5F5") }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 18f, bgPaint)

        canvas.drawText("Rangos de glucosa", col1 + 5f, y + 12f, labelPaint)
        canvas.drawText("Objetivos %", col2, y + 12f, labelPaint)
        canvas.drawText("Calibrada (Tiempo/día)", col3, y + 12f, valPaint)
        if (compare) canvas.drawText("Raw (Sin calibrar)", col4, y + 12f, rawValPaint)
        y += 20f

        val rows = listOf(
            Triple("Intervalo objetivo 70-180 mg/dL", "Mayor que 70%", Pair(cal.tir to cal.timeInRangesHours.tirHours, raw?.let { it.tir to it.timeInRangesHours.tirHours })),
            Triple("Por debajo 70 mg/dL", "Menor que 4%", Pair(cal.tbrLow + cal.tbrVLow to cal.timeInRangesHours.tbrLowHours + cal.timeInRangesHours.tbrVLowHours, raw?.let { (it.tbrLow + it.tbrVLow) to (it.timeInRangesHours.tbrLowHours + it.timeInRangesHours.tbrVLowHours) })),
            Triple("Por debajo 54 mg/dL", "Menor que 1%", Pair(cal.tbrVLow to cal.timeInRangesHours.tbrVLowHours, raw?.let { it.tbrVLow to it.timeInRangesHours.tbrVLowHours })),
            Triple("Por encima 180 mg/dL", "Menor que 25%", Pair(cal.tarHigh + cal.tarVHigh to cal.timeInRangesHours.tarHighHours + cal.timeInRangesHours.tarVHighHours, raw?.let { (it.tarHigh + it.tarVHigh) to (it.timeInRangesHours.tarHighHours + it.timeInRangesHours.tarVHighHours) })),
            Triple("Por encima 250 mg/dL", "Menor que 5%", Pair(cal.tarVHigh to cal.timeInRangesHours.tarVHighHours, raw?.let { it.tarVHigh to it.timeInRangesHours.tarVHighHours }))
        )

        rows.forEach { (rangeLabel, targetLabel, values) ->
            val calText = "%.0f%% (%s)".format(values.first.first, formatHoursMinutes(values.first.second))
            canvas.drawText(rangeLabel, col1 + 5f, y + 10f, labelPaint)
            canvas.drawText(targetLabel, col2, y + 10f, labelPaint)
            canvas.drawText(calText, col3, y + 10f, valPaint)
            if (compare && values.second != null) {
                val rawText = "%.0f%% (%s)".format(values.second!!.first, formatHoursMinutes(values.second!!.second))
                canvas.drawText(rawText, col4, y + 10f, rawValPaint)
            }
            y += 14f
        }

        y += 6f
        // Overall Stats Row
        canvas.drawText("Glucosa promedio:", col1 + 5f, y + 10f, headerPaint)
        canvas.drawText("%.0f mg/dL".format(cal.avgGlucose), col3, y + 10f, valPaint)
        if (compare && raw != null) canvas.drawText("%.0f mg/dL".format(raw.avgGlucose), col4, y + 10f, rawValPaint)
        y += 14f

        canvas.drawText("Desviación estándar (SD):", col1 + 5f, y + 10f, headerPaint)
        canvas.drawText("%.1f mg/dL".format(cal.stdDev), col3, y + 10f, valPaint)
        if (compare && raw != null) canvas.drawText("%.1f mg/dL".format(raw.stdDev), col4, y + 10f, rawValPaint)
        y += 14f

        canvas.drawText("GMI (Est. A1c):", col1 + 5f, y + 10f, headerPaint)
        canvas.drawText("%.1f %%".format(cal.gmi), col3, y + 10f, valPaint)
        if (compare && raw != null) canvas.drawText("%.1f %%".format(raw.gmi), col4, y + 10f, rawValPaint)
        y += 14f

        canvas.drawText("Variabilidad glucosa (%CV):", col1 + 5f, y + 10f, headerPaint)
        canvas.drawText("%.1f %%".format(cal.cv), col3, y + 10f, valPaint)
        if (compare && raw != null) canvas.drawText("%.1f %%".format(raw.cv), col4, y + 10f, rawValPaint)
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

                canvas.drawText("%.0f mg/dL".format(avgCal), bx + 4f, by + 24f, statPaint)
                if (compare) {
                    canvas.drawText("%.0f mg/dL (raw)".format(avgRaw), bx + 4f, by + 34f, rawStatPaint)
                }
                canvas.drawText("${s.glucose.size} lecturas", bx + 4f, by + 44f, subPaint)
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
        val chartH = h - 35f

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

    // --- INSTANTÁNEA PAGE ---
    private fun drawSnapshotReport(
        canvas: Canvas,
        cal: ReportMetrics,
        raw: ReportMetrics?,
        start: LocalDate,
        end: LocalDate,
        days: Int,
        compare: Boolean,
        startY: Float
    ) {
        var y = startY
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        canvas.drawText("Instantánea", MARGIN, y + 14f, titlePaint)

        val rangeStr = "${formatDateSpanish(start)} - ${formatDateSpanish(end)} ($days Días)"
        canvas.drawText(rangeStr, MARGIN, y + 28f, Paint().apply { color = Color.DKGRAY; textSize = 10f })
        y += 45f

        val boxW = (PAGE_WIDTH - 2 * MARGIN - 20f) / 2f
        val boxH = 110f

        // Box 1: Glucose Average & GMI
        val cardPaint = Paint().apply { color = Color.parseColor("#FAFAFA"); style = Paint.Style.FILL }
        val cardBorder = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE }

        canvas.drawRect(MARGIN, y, MARGIN + boxW, y + boxH, cardPaint)
        canvas.drawRect(MARGIN, y, MARGIN + boxW, y + boxH, cardBorder)

        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 10f; isFakeBoldText = true }
        val bigValPaint = Paint().apply { color = COLOR_BLUE; textSize = 18f; isFakeBoldText = true }
        val rawBigValPaint = Paint().apply { color = COLOR_RAW_ORANGE; textSize = 14f; isFakeBoldText = true }
        val labelPaint = Paint().apply { color = Color.DKGRAY; textSize = 8.5f }

        canvas.drawText("PROMEDIO DE GLUCOSA", MARGIN + 10f, y + 18f, headerPaint)
        canvas.drawText("%.0f mg/dL".format(cal.avgGlucose), MARGIN + 10f, y + 42f, bigValPaint)
        if (compare && raw != null) {
            canvas.drawText("%.0f mg/dL (Raw)".format(raw.avgGlucose), MARGIN + 10f, y + 58f, rawBigValPaint)
        }
        canvas.drawText("GMI (Est. A1c): %.1f%% | CV: %.1f%%".format(cal.gmi, cal.cv), MARGIN + 10f, y + 76f, labelPaint)
        canvas.drawText("SD: %.1f mg/dL | Sensor Activo: %.1f%%".format(cal.stdDev, cal.activeSensorPercent), MARGIN + 10f, y + 92f, labelPaint)

        // Box 2: Time in Range Summary
        val box2X = MARGIN + boxW + 20f
        canvas.drawRect(box2X, y, box2X + boxW, y + boxH, cardPaint)
        canvas.drawRect(box2X, y, box2X + boxW, y + boxH, cardBorder)

        canvas.drawText("TIEMPO EN RANGO (70-180)", box2X + 10f, y + 18f, headerPaint)
        canvas.drawText("%.0f%% (%s)".format(cal.tir, formatHoursMinutes(cal.timeInRangesHours.tirHours)), box2X + 10f, y + 42f, bigValPaint)
        canvas.drawText("Sobre objetivo (>180): %.0f%% (%s)".format(cal.tarHigh + cal.tarVHigh, formatHoursMinutes(cal.timeInRangesHours.tarHighHours + cal.timeInRangesHours.tarVHighHours)), box2X + 10f, y + 65f, labelPaint)
        canvas.drawText("Bajo objetivo (<70): %.0f%% (%s)".format(cal.tbrLow + cal.tbrVLow, formatHoursMinutes(cal.timeInRangesHours.tbrLowHours + cal.timeInRangesHours.tbrVLowHours)), box2X + 10f, y + 80f, labelPaint)

        y += boxH + 25f

        // Insulin & Carbs Summary Box
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 90f, cardPaint)
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 90f, cardBorder)

        canvas.drawText("RESUMEN DE INSULINA Y CARBOHIDRATOS", MARGIN + 10f, y + 18f, headerPaint)
        canvas.drawText("Insulina Diaria Media: %.1f U".format(cal.avgTdi), MARGIN + 10f, y + 40f, labelPaint)
        canvas.drawText("Proporción Basal / Bolo: %.0f%% / %.0f%%".format(cal.basalPercentage, cal.bolusPercentage), MARGIN + 10f, y + 58f, labelPaint)
    }

    // --- CONFIGURACIÓN PAGE ---
    private fun drawSettingsPage(canvas: Canvas, startY: Float) {
        var y = startY
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        val sectionPaint = Paint().apply { color = Color.BLACK; textSize = 11f; isFakeBoldText = true }
        val labelPaint = Paint().apply { color = Color.DKGRAY; textSize = 9f }
        val valPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true }

        canvas.drawText("Configuración", MARGIN, y + 14f, titlePaint)
        y += 35f

        canvas.drawText("CONFIGURACIÓN DE GLUCOSA", MARGIN, y, sectionPaint)
        y += 15f
        canvas.drawText("Intervalo objetivo:", MARGIN, y, labelPaint)
        canvas.drawText("70 - 180 mg/dL", MARGIN + 120f, y, valPaint)
        y += 25f

        canvas.drawText("CONFIGURACIÓN DE ALARMAS", MARGIN, y, sectionPaint)
        y += 15f
        canvas.drawText("Alarma de Glucosa Baja:", MARGIN, y, labelPaint)
        canvas.drawText("Activada (<70 mg/dL)", MARGIN + 140f, y, valPaint)
        y += 14f
        canvas.drawText("Alarma de Glucosa Alta:", MARGIN, y, labelPaint)
        canvas.drawText("Activada (>250 mg/dL)", MARGIN + 140f, y, valPaint)
        y += 25f

        canvas.drawText("DETALLES DEL DISPOSITIVO Y APLICACIÓN", MARGIN, y, sectionPaint)
        y += 15f
        canvas.drawText("Aplicación:", MARGIN, y, labelPaint)
        canvas.drawText("Libre2Clock v2.13.1", MARGIN + 140f, y, valPaint)
        y += 14f
        canvas.drawText("Sistema Operativo:", MARGIN, y, labelPaint)
        canvas.drawText("Android " + Build.VERSION.RELEASE, MARGIN + 140f, y, valPaint)
        y += 14f
        canvas.drawText("Modelo de SmartPhone:", MARGIN, y, labelPaint)
        canvas.drawText(Build.MANUFACTURER + " " + Build.MODEL, MARGIN + 140f, y, valPaint)
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
        val path = Path()
        pts.forEachIndexed { i, pt ->
            val px = x + (i / 23f) * w
            val py = y + h - (high(pt).toFloat() - minG) / rangeG * h
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        for (i in pts.indices.reversed()) {
            val pt = pts[i]
            val px = x + (i / 23f) * w
            val py = y + h - (low(pt).toFloat() - minG) / rangeG * h
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
        val path = Path()
        pts.forEachIndexed { i, pt ->
            val valG = v(pt).toFloat()
            if (valG > 0) {
                val px = x + (i / 23f) * w
                val py = y + h - (valG - minG) / rangeG * h
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
