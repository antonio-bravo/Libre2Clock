package com.tonio.libre2clock.data.repository

import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.SensorLog
import com.tonio.libre2clock.util.TimestampParser
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Utility to process glucose data based on specific adjustment logic.
 * Optimized for zero-allocation statistics and O(log N) lookups.
 */
object GlucoseProcessor {

    fun process(
        measurement: GlucoseMeasurement,
        manualOffset: Int = 0,
        userRanges: List<GlucoseOffsetRange> = emptyList(),
        autoAdjustEnabled: Boolean = false,
        autoRangeOffsetMode: AutoRangeOffsetMode = AutoRangeOffsetMode.OFF,
        capillaryReadings: List<CapillaryMeasurement> = emptyList(),
        context: CalculationContext? = null
    ): GlucoseMeasurement {
        val measurementInstant = measurement.epochSeconds?.let { Instant.ofEpochSecond(it) }
            ?: TimestampParser.parseFlexibleInstant(measurement.factoryTimestamp)
            ?: TimestampParser.parseFlexibleInstant(measurement.timestamp)

        val calibratedValue = getCalibratedValue(
            rawValue = measurement.value,
            manualOffset = manualOffset,
            userRanges = userRanges,
            autoAdjustEnabled = autoAdjustEnabled,
            autoRangeOffsetMode = autoRangeOffsetMode,
            capillaryReadings = capillaryReadings,
            measurementInstant = measurementInstant,
            context = context
        )

        // Si el valor no cambia, evitamos crear una nueva instancia (ahorro de memoria)
        return if (calibratedValue == measurement.calibratedValue) {
            measurement
        } else {
            measurement.copy(calibratedValue = calibratedValue)
        }
    }

    data class CalculationContext(
        val globalEstimate: RangeOffsetEstimate?,
        val rangeEstimates: Map<GlucoseOffsetRange, RangeOffsetEstimate>,
        val capillariesByTimestamp: List<Pair<Instant, CapillaryMeasurement>>,
        val sensorLogs: List<SensorLog> = emptyList()
    )

    fun buildContext(
        autoRangeOffsetMode: AutoRangeOffsetMode,
        userRanges: List<GlucoseOffsetRange>,
        capillaryReadings: List<CapillaryMeasurement>,
        sensorLogs: List<SensorLog> = emptyList()
    ): CalculationContext {
        val globalEstimate = if (autoRangeOffsetMode == AutoRangeOffsetMode.GLOBAL) {
            estimateGlobalOffsets(capillaryReadings)
        } else null

        val rangeEstimates = if (autoRangeOffsetMode == AutoRangeOffsetMode.BY_RANGE) {
            userRanges.mapNotNull { range ->
                estimateOffsetsForRange(range, capillaryReadings)?.let { range to it }
            }.toMap()
        } else emptyMap()

        // Optimización: Evitar mapNotNull intermedio si es posible, pero este es legible y eficiente
        val capsByTime = capillaryReadings
            .mapNotNull { r -> TimestampParser.parseFlexibleInstant(r.timestamp)?.let { it to r } }
            .sortedByDescending { it.first }

        return CalculationContext(globalEstimate, rangeEstimates, capsByTime, sensorLogs)
    }

    fun getCalibratedValue(
        rawValue: Int,
        manualOffset: Int = 0,
        userRanges: List<GlucoseOffsetRange> = emptyList(),
        autoAdjustEnabled: Boolean = false,
        autoRangeOffsetMode: AutoRangeOffsetMode = AutoRangeOffsetMode.OFF,
        capillaryReadings: List<CapillaryMeasurement> = emptyList(),
        measurementInstant: Instant? = null,
        context: CalculationContext? = null
    ): Int {
        // Búsqueda lineal está bien aquí porque userRanges suele ser muy pequeño (< 10 elementos)
        val matchingRange = userRanges.firstOrNull { range ->
            rawValue >= range.min && (range.max == null || rawValue < range.max)
        }

        val selectedEstimate = when (autoRangeOffsetMode) {
            AutoRangeOffsetMode.OFF -> null
            AutoRangeOffsetMode.GLOBAL -> context?.globalEstimate ?: estimateGlobalOffsets(capillaryReadings)
            AutoRangeOffsetMode.BY_RANGE -> {
                if (context != null && matchingRange != null) {
                    context.rangeEstimates[matchingRange]
                } else {
                    matchingRange?.let { estimateOffsetsForRange(it, capillaryReadings) }
                }
            }
        }

        val rangeFixedOffset = selectedEstimate?.offset ?: (matchingRange?.offset ?: 0)
        
        val rangePercentageOffset = if (selectedEstimate != null) {
            (rawValue * (selectedEstimate.percentage / 100.0)).roundToInt()
        } else {
            matchingRange?.let { (rawValue * (it.percentage / 100.0)).roundToInt() } ?: 0
        }

        val autoAdjustment = if (autoAdjustEnabled) {
            getAutoAdjustment(rawValue, measurementInstant, capillaryReadings, context = context)
        } else {
            0
        }

        return rawValue + rangeFixedOffset + rangePercentageOffset + manualOffset + autoAdjustment
    }

    fun getAutoAdjustment(
        rawValue: Int,
        measurementInstant: Instant?,
        capillaryReadings: List<CapillaryMeasurement>,
        maxHoursDifference: Long = 6,
        context: CalculationContext? = null
    ): Int {
        if (measurementInstant == null) return 0
        
        val capsToUse = context?.capillariesByTimestamp
        if (capsToUse.isNullOrEmpty() && capillaryReadings.isEmpty()) return 0

        val targetMs = measurementInstant.toEpochMilli()
        val targetSensorSn = context?.let { findSensorSnForTimestamp(measurementInstant, it.sensorLogs) }
        val maxDiffMs = maxHoursDifference * 3_600_000L

        // OPTIMIZACIÓN CRÍTICA: Búsqueda binaria + ventana local en lugar de escaneo lineal O(N)
        if (capsToUse != null && capsToUse.isNotEmpty()) {
            var low = 0
            var high = capsToUse.size - 1
            var insertIndex = capsToUse.size

            // 1. Encontrar el punto de inserción para targetMs en la lista ordenada DESCENDENTE
            while (low <= high) {
                val mid = (low + high) ushr 1 // Unsigned shift para evitar overflow
                if (capsToUse[mid].first.toEpochMilli() >= targetMs) {
                    low = mid + 1
                } else {
                    insertIndex = mid
                    high = mid - 1
                }
            }

            // 2. Buscar el mejor candidato en una ventana pequeña alrededor del punto de inserción
            // Esto maneja el caso donde la lectura más cercana en tiempo es de un sensor diferente
            val windowStart = (insertIndex - 5).coerceAtLeast(0)
            val windowEnd = (insertIndex + 5).coerceAtMost(capsToUse.size - 1)

            var bestAdjustment = 0
            var minDiffMs = Long.MAX_VALUE

            for (i in windowStart..windowEnd) {
                val (instant, reading) = capsToUse[i]
                val currentMs = instant.toEpochMilli()
                val diffMs = abs(targetMs - currentMs)
                
                if (diffMs > maxDiffMs) continue

                val isSameSensor = targetSensorSn == null || reading.sensorSerialNumber == targetSensorSn
                if (!isSameSensor) continue

                if (diffMs < minDiffMs) {
                    minDiffMs = diffMs
                    bestAdjustment = reading.delta 
                        ?: reading.sensorValue?.let { reading.value - it } 
                        ?: (reading.value - rawValue)
                }
            }
            return if (minDiffMs <= maxDiffMs) bestAdjustment else 0
        } else {
            // Fallback lineal solo si no hay contexto pre-calculado (raro en producción)
            var bestAdjustment = 0
            var minDiffMs = Long.MAX_VALUE

            for (reading in capillaryReadings) {
                val readingInstant = TimestampParser.parseFlexibleInstant(reading.timestamp) ?: continue
                val diffMs = abs(targetMs - readingInstant.toEpochMilli())
                
                if (diffMs <= maxDiffMs && diffMs < minDiffMs) {
                    minDiffMs = diffMs
                    bestAdjustment = reading.delta 
                        ?: reading.sensorValue?.let { reading.value - it } 
                        ?: (reading.value - rawValue)
                }
            }
            return if (minDiffMs <= maxDiffMs) bestAdjustment else 0
        }
    }

    private fun findSensorSnForTimestamp(timestamp: Instant, logs: List<SensorLog>): String? {
        if (logs.isEmpty()) return null
        
        // Optimización: firstOrNull es más limpio y se detiene en la primera coincidencia
        return logs.firstOrNull { log ->
            val start = TimestampParser.parseFlexibleInstant(log.startDate) ?: return@firstOrNull false
            val end = log.endDate?.let { TimestampParser.parseFlexibleInstant(it) } 
                ?: TimestampParser.parseFlexibleInstant(log.expiryDate)
                ?: Instant.MAX
            
            !timestamp.isBefore(start) && timestamp.isBefore(end)
        }?.serialNumber
    }

    data class RangeOffsetEstimate(
        val offset: Int,
        val percentage: Int,
        val sampleCount: Int
    )

    fun estimateOffsetsForRange(
        range: GlucoseOffsetRange,
        capillaryReadings: List<CapillaryMeasurement>
    ): RangeOffsetEstimate? {
        return estimateOffsetsInternal(capillaryReadings) { sensor ->
            sensor >= range.min && (range.max == null || sensor < range.max)
        }
    }

    fun estimateGlobalOffsets(
        capillaryReadings: List<CapillaryMeasurement>
    ): RangeOffsetEstimate? {
        return estimateOffsetsInternal(capillaryReadings) { true }
    }

    private fun estimateOffsetsInternal(
        capillaryReadings: List<CapillaryMeasurement>,
        sensorFilter: (Int) -> Boolean
    ): RangeOffsetEstimate? {
        // OPTIMIZACIÓN CRÍTICA: Regresión lineal en 2 pasadas SIN crear listas intermedias (Zero-Allocation)
        var sumX = 0.0
        var sumY = 0.0
        var count = 0

        // Pasada 1: Calcular medias
        for (reading in capillaryReadings) {
            val sensor = reading.sensorValue ?: continue
            if (sensor == 0 || !sensorFilter(sensor)) continue
            
            sumX += sensor
            sumY += (reading.value - sensor)
            count++
        }

        if (count == 0) return null
        if (count == 1) {
            return RangeOffsetEstimate(offset = sumY.roundToInt(), percentage = 0, sampleCount = 1)
        }

        val meanX = sumX / count
        val meanY = sumY / count

        var varianceX = 0.0
        var covariance = 0.0

        // Pasada 2: Calcular varianza y covarianza
        for (reading in capillaryReadings) {
            val sensor = reading.sensorValue ?: continue
            if (sensor == 0 || !sensorFilter(sensor)) continue
            
            val x = sensor.toDouble()
            val y = (reading.value - sensor).toDouble()
            val dx = x - meanX
            
            varianceX += dx * dx
            covariance += dx * (y - meanY)
        }

        val slope = if (varianceX > 1e-9) covariance / varianceX else 0.0
        val intercept = meanY - slope * meanX

        return RangeOffsetEstimate(
            offset = intercept.roundToInt(),
            percentage = (slope * 100.0).roundToInt(),
            sampleCount = count
        )
    }

    /**
     * Formats the glucose display as original(calibrated).
     */
    fun formatDualValue(rawValue: Int, calibratedValue: Int): String {
        return "$rawValue($calibratedValue)"
    }

    /**
     * Maps the trend arrow integer to a string description or icon reference.
     */
    fun getTrendArrowSymbol(trend: Int?): String {
        return when (trend) {
            1 -> "↓"
            2 -> "↘"
            3 -> "→"
            4 -> "↗"
            5 -> "↑"
            else -> "→"
        }
    }
}