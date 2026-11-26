package com.tonio.libre2clock.util

import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.SensorLog
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

enum class SensorQualityTier {
    EXCELLENT,  // MARD <= 10%
    ACCEPTABLE, // 10% < MARD <= 15%
    DEGRADED    // MARD > 15%
}

data class SensorErrorSummary(
    val serialNumber: String,
    val samples: Int,
    val avgAbsoluteDeviationPct: Double,
    val avgSignedDeviationPct: Double,
    val mardPct: Double = avgAbsoluteDeviationPct,
    val qualityTier: SensorQualityTier = classifyMard(mardPct)
)

data class CompressionLowEvent(
    val timestamp: Instant,
    val nadirMgDl: Int,
    val dropRateMgPerMin: Double,
    val recoveryRateMgPerMin: Double
)

fun classifyMard(mardPct: Double): SensorQualityTier = when {
    mardPct <= 10.0 -> SensorQualityTier.EXCELLENT
    mardPct <= 15.0 -> SensorQualityTier.ACCEPTABLE
    else -> SensorQualityTier.DEGRADED
}

fun buildSensorErrorSummary(
    sensorLogs: List<SensorLog>,
    capillaryReadings: List<CapillaryMeasurement>
): List<SensorErrorSummary> {
    val groupedReadings = capillaryReadings
        .filter { it.sensorSerialNumber != null && it.sensorValue != null && it.sensorValue > 0 && it.value > 0 }
        .groupBy { it.sensorSerialNumber!! }

    val serialNumbers = (sensorLogs.map { it.serialNumber } + groupedReadings.keys).distinct()

    return serialNumbers.mapNotNull { serialNumber ->
        val relevantReadings = groupedReadings[serialNumber].orEmpty()
        if (relevantReadings.isEmpty()) return@mapNotNull null

        val mardValues = relevantReadings.mapNotNull { reading ->
            val sensor = reading.sensorValue ?: return@mapNotNull null
            if (reading.value <= 0) return@mapNotNull null
            abs(sensor - reading.value).toDouble() / reading.value.toDouble() * 100.0
        }

        val absoluteDeviationPct = relevantReadings.mapNotNull { reading ->
            val sensor = reading.sensorValue ?: return@mapNotNull null
            if (sensor <= 0) return@mapNotNull null
            abs(reading.value - sensor).toDouble() / sensor * 100.0
        }

        val signedDeviationPct = relevantReadings.mapNotNull { reading ->
            val sensor = reading.sensorValue ?: return@mapNotNull null
            if (sensor <= 0) return@mapNotNull null
            ((reading.value - sensor).toDouble() / sensor) * 100.0
        }

        val mard = if (mardValues.isNotEmpty()) mardValues.average() else 0.0

        SensorErrorSummary(
            serialNumber = serialNumber,
            samples = relevantReadings.size,
            avgAbsoluteDeviationPct = if (absoluteDeviationPct.isNotEmpty()) absoluteDeviationPct.average() else 0.0,
            avgSignedDeviationPct = if (signedDeviationPct.isNotEmpty()) signedDeviationPct.average() else 0.0,
            mardPct = mard,
            qualityTier = classifyMard(mard)
        )
    }.sortedByDescending { it.samples }
}

/**
 * Detects likely pressure-induced "compression lows":
 * A sharp drop (<= -2.5 mg/dL/min) into < 75 mg/dL followed within <= 45 min by
 * a sharp spontaneous rebound (>= +2.5 mg/dL/min) back >= 85 mg/dL.
 */
fun detectCompressionLows(measurements: List<GlucoseMeasurement>): List<CompressionLowEvent> {
    if (measurements.size < 3) return emptyList()
    val points = measurements.mapNotNull { m ->
        val instant = TimestampParser.parseMeasurementInstant(m) ?: return@mapNotNull null
        val value = m.value.takeIf { it > 0 } ?: m.valueInMgPerDl.takeIf { it > 0 } ?: return@mapNotNull null
        instant to value
    }.sortedBy { it.first }

    if (points.size < 3) return emptyList()
    val events = mutableListOf<CompressionLowEvent>()

    for (i in 1 until points.size - 1) {
        val (nadirInstant, nadirVal) = points[i]
        if (nadirVal >= 75) continue

        // Look back up to 25 minutes for pre-drop baseline
        var bestPreRate = 0.0
        for (j in i - 1 downTo 0) {
            val mins = Duration.between(points[j].first, nadirInstant).toMinutes().toDouble()
            if (mins > 25.0) break
            if (mins >= 3.0 && points[j].second >= 90) {
                val rate = (nadirVal - points[j].second) / mins
                if (rate < bestPreRate) bestPreRate = rate
            }
        }
        if (bestPreRate > -2.5) continue

        // Look forward up to 45 minutes for spontaneous rebound
        var bestRecoveryRate = 0.0
        for (k in i + 1 until points.size) {
            val mins = Duration.between(nadirInstant, points[k].first).toMinutes().toDouble()
            if (mins > 45.0) break
            if (mins >= 3.0 && points[k].second >= 85) {
                val rate = (points[k].second - nadirVal) / mins
                if (rate > bestRecoveryRate) bestRecoveryRate = rate
            }
        }
        if (bestRecoveryRate < 2.5) continue

        val lastEvent = events.lastOrNull()
        if (lastEvent == null || Duration.between(lastEvent.timestamp, nadirInstant).toMinutes() > 45L) {
            events.add(
                CompressionLowEvent(
                    timestamp = nadirInstant,
                    nadirMgDl = nadirVal,
                    dropRateMgPerMin = bestPreRate,
                    recoveryRateMgPerMin = bestRecoveryRate
                )
            )
        }
    }
    return events
}
