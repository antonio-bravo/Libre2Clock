package com.tonio.libre2clock.util

import android.content.Context
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.SensorLog
import com.tonio.libre2clock.ui.report.ReportMetrics
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.Locale

@Serializable
data class ClinicalExportPayload(
    val exportedAt: String,
    val metrics: ReportMetrics? = null,
    val glucoseCount: Int,
    val insulinCount: Int,
    val capillaryCount: Int,
    val glucoseReadings: List<GlucoseMeasurement>,
    val insulinDoses: List<InsulinDose>,
    val capillaryReadings: List<CapillaryMeasurement>,
    val sensorLogs: List<SensorLog>
)

object ClinicalExporter {

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun generateCsv(
        glucose: List<GlucoseMeasurement>,
        doses: List<InsulinDose>,
        capillaries: List<CapillaryMeasurement>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("timestamp_iso,record_type,glucose_raw_mg_dl,glucose_calibrated_mg_dl,trend_arrow,insulin_units,insulin_type,carbs_g,notes")

        data class CsvRow(val epochMs: Long, val line: String)
        val rows = ArrayList<CsvRow>(glucose.size + doses.size + capillaries.size)

        for (g in glucose) {
            val instant = TimestampParser.parseMeasurementInstant(g) ?: continue
            val raw = g.value.takeIf { it > 0 } ?: g.valueInMgPerDl
            val cal = g.calibratedValue.takeIf { it > 0 } ?: raw
            val arrow = GlucoseProcessorQuickArrow(g.trendArrow)
            rows.add(
                CsvRow(
                    instant.toEpochMilli(),
                    "${instant},CGM,$raw,$cal,$arrow,,,,"
                )
            )
        }

        for (d in doses) {
            if (d.isDeleted) continue
            val instant = TimestampParser.parseFlexibleInstant(d.timestamp) ?: continue
            val carbsStr = d.carbs?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            rows.add(
                CsvRow(
                    instant.toEpochMilli(),
                    "${instant},INSULIN,,,,${String.format(Locale.US, "%.2f", d.units)},${d.type.name},$carbsStr,"
                )
            )
        }

        for (c in capillaries) {
            if (c.isDeleted) continue
            val instant = TimestampParser.parseFlexibleInstant(c.timestamp) ?: continue
            val sensorVal = c.sensorValue?.toString() ?: ""
            val note = escapeCsv(c.sensorSerialNumber?.let { "SN:$it" } ?: "")
            rows.add(
                CsvRow(
                    instant.toEpochMilli(),
                    "${instant},CAPILLARY,${c.value},$sensorVal,,,,,$note"
                )
            )
        }

        rows.sortBy { it.epochMs }
        for (row in rows) {
            sb.appendLine(row.line)
        }
        return sb.toString()
    }

    fun generateClinicalJson(
        glucose: List<GlucoseMeasurement>,
        doses: List<InsulinDose>,
        capillaries: List<CapillaryMeasurement>,
        sensorLogs: List<SensorLog>,
        metrics: ReportMetrics?
    ): String {
        val payload = ClinicalExportPayload(
            exportedAt = Instant.now().toString(),
            metrics = metrics,
            glucoseCount = glucose.size,
            insulinCount = doses.count { !it.isDeleted },
            capillaryCount = capillaries.count { !it.isDeleted },
            glucoseReadings = glucose,
            insulinDoses = doses.filter { !it.isDeleted },
            capillaryReadings = capillaries.filter { !it.isDeleted },
            sensorLogs = sensorLogs.filter { !it.isDeleted }
        )
        return prettyJson.encodeToString(payload)
    }

    fun writeReportFile(context: Context, fileName: String, content: String): File? {
        return try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(reportsDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun GlucoseProcessorQuickArrow(trend: Int?): String = when (trend) {
        1 -> "DoubleDown"
        2 -> "FortyFiveDown"
        3 -> "Flat"
        4 -> "FortyFiveUp"
        5 -> "DoubleUp"
        else -> ""
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
