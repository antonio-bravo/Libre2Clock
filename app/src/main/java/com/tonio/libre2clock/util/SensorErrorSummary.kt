package com.tonio.libre2clock.util

import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.SensorLog
import kotlin.math.abs

data class SensorErrorSummary(
    val serialNumber: String,
    val samples: Int,
    val avgAbsoluteDeviationPct: Double,
    val avgSignedDeviationPct: Double
)

fun buildSensorErrorSummary(
    sensorLogs: List<SensorLog>,
    capillaryReadings: List<CapillaryMeasurement>
): List<SensorErrorSummary> {
    val groupedReadings = capillaryReadings
        .filter { it.sensorSerialNumber != null && it.sensorValue != null && it.sensorValue != 0 }
        .groupBy { it.sensorSerialNumber!! }

    val serialNumbers = (sensorLogs.map { it.serialNumber } + groupedReadings.keys).distinct()

    return serialNumbers.mapNotNull { serialNumber ->
        val relevantReadings = groupedReadings[serialNumber].orEmpty()
        if (relevantReadings.isEmpty()) return@mapNotNull null

        val absoluteDeviationPct = relevantReadings.map { reading ->
            val sensor = reading.sensorValue ?: return@mapNotNull null
            abs(reading.value - sensor).toDouble() / sensor * 100.0
        }

        val signedDeviationPct = relevantReadings.map { reading ->
            val sensor = reading.sensorValue ?: return@mapNotNull null
            ((reading.value - sensor).toDouble() / sensor) * 100.0
        }

        SensorErrorSummary(
            serialNumber = serialNumber,
            samples = relevantReadings.size,
            avgAbsoluteDeviationPct = absoluteDeviationPct.average(),
            avgSignedDeviationPct = signedDeviationPct.average()
        )
    }.sortedByDescending { it.samples }
}
