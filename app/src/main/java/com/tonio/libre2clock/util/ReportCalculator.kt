package com.tonio.libre2clock.util

import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.InsulinDose
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.ui.report.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

data class ProcessedGlucose(
    val instant: Instant,
    val rawValue: Double,
    val calibratedValue: Double
)

object ReportCalculator {

    fun calculateCvForPeriod(
        processed: List<ProcessedGlucose>,
        endInstant: Instant,
        days: Long,
        useOffset: Boolean = true
    ): Double {
        val cutoff = endInstant.minus(days, ChronoUnit.DAYS)
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (pg in processed) {
            if (!pg.instant.isBefore(cutoff) && !pg.instant.isAfter(endInstant)) {
                val v = if (useOffset) pg.calibratedValue else pg.rawValue
                sum += v
                sumSq += v * v
                count++
            }
        }

        if (count == 0) return 0.0
        val avg = sum / count
        val variance = (sumSq / count) - (avg * avg)
        val stdDev = if (variance > 0) sqrt(variance) else 0.0
        return if (avg > 0) (stdDev / avg) * 100.0 else 0.0
    }

    fun calculateMetrics(
        processed: List<ProcessedGlucose>,
        doses: List<InsulinDose>,
        daysCount: Int,
        useOffset: Boolean
    ): ReportMetrics {
        if (processed.isEmpty()) {
            return ReportMetrics(
                avgGlucose = 0.0, gmi = 0.0, cv = 0.0, stdDev = 0.0, activeSensorPercent = 0.0,
                tir = 0.0, tarHigh = 0.0, tarVHigh = 0.0, tbrLow = 0.0, tbrVLow = 0.0,
                timeInRangesHours = TimeInRangesHours(), targetsStatus = AgpTargetsStatus(),
                avgTdi = 0.0, basalPercentage = 0.0, bolusPercentage = 0.0, readingsCount = 0
            )
        }

        var sum = 0.0
        var sumSq = 0.0
        var tir = 0; var tarHigh = 0; var tarVHigh = 0; var tbrLow = 0; var tbrVLow = 0

        for (pg in processed) {
            val v = if (useOffset) pg.calibratedValue else pg.rawValue
            sum += v
            sumSq += v * v

            when {
                v in 70.0..180.0 -> tir++
                v in 181.0..250.0 -> tarHigh++
                v > 250.0 -> tarVHigh++
                v in 54.0..69.0 -> tbrLow++
                v < 54.0 -> tbrVLow++
            }
        }

        val count = processed.size.toDouble()
        val avg = sum / count
        val variance = (sumSq / count) - (avg * avg)
        val stdDev = if (variance > 0) sqrt(variance) else 0.0

        val cv = if (avg > 0) (stdDev / avg) * 100 else 0.0
        val gmi = if (avg > 0) (avg + 46.7) / 28.7 else 0.0

        val tirPct = (tir / count) * 100
        val tarHighPct = (tarHigh / count) * 100
        val tarVHighPct = (tarVHigh / count) * 100
        val tbrLowPct = (tbrLow / count) * 100
        val tbrVLowPct = (tbrVLow / count) * 100

        val tirHours = (tirPct / 100.0) * 24.0
        val tarHighHours = (tarHighPct / 100.0) * 24.0
        val tarVHighHours = (tarVHighPct / 100.0) * 24.0
        val tbrLowHours = (tbrLowPct / 100.0) * 24.0
        val tbrVLowHours = (tbrVLowPct / 100.0) * 24.0

        val timeInRangesHours = TimeInRangesHours(
            tirHours = tirHours,
            tarHighHours = tarHighHours,
            tarVHighHours = tarVHighHours,
            tbrLowHours = tbrLowHours,
            tbrVLowHours = tbrVLowHours
        )

        val targetsStatus = AgpTargetsStatus(
            isTirMet = tirPct >= 70.0,
            isTbrMet = (tbrLowPct + tbrVLowPct) <= 4.0,
            isTbrVLowMet = tbrVLowPct <= 1.0,
            isTarMet = (tarHighPct + tarVHighPct) <= 25.0,
            isTarVHighMet = tarVHighPct <= 5.0,
            isCvMet = cv <= 36.0
        )

        val safeDays = daysCount.coerceAtLeast(1)
        val maxExpected15Min = safeDays * 96.0
        val maxExpected1Min = safeDays * 1440.0
        val expectedReadings = if (count > maxExpected15Min * 1.2) maxExpected1Min else maxExpected15Min
        val activeSensorPct = ((count / expectedReadings) * 100.0).coerceAtMost(100.0)

        var totalInsulin = 0.0; var basal = 0.0; var bolus = 0.0
        for (d in doses) {
            totalInsulin += d.units
            if (d.type == InsulinType.SLOW) basal += d.units else bolus += d.units
        }

        val zone = ZoneId.systemDefault()
        var nightSum = 0.0; var nightCount = 0; var nightTir = 0; var nightTbr = 0
        for (pg in processed) {
            val hour = pg.instant.atZone(zone).hour
            if (hour in 0..5) {
                val v = if (useOffset) pg.calibratedValue else pg.rawValue
                nightSum += v
                nightCount++
                if (v in 70.0..180.0) nightTir++
                if (v < 70.0) nightTbr++
            }
        }
        val overnightAvg = if (nightCount > 0) nightSum / nightCount else 0.0
        val overnightTir = if (nightCount > 0) (nightTir.toDouble() / nightCount) * 100.0 else 0.0
        val overnightTbr = if (nightCount > 0) (nightTbr.toDouble() / nightCount) * 100.0 else 0.0

        return ReportMetrics(
            avgGlucose = avg,
            gmi = gmi,
            cv = cv,
            stdDev = stdDev,
            activeSensorPercent = activeSensorPct,
            tir = tirPct,
            tarHigh = tarHighPct,
            tarVHigh = tarVHighPct,
            tbrLow = tbrLowPct,
            tbrVLow = tbrVLowPct,
            timeInRangesHours = timeInRangesHours,
            targetsStatus = targetsStatus,
            avgTdi = totalInsulin / safeDays,
            basalPercentage = if (totalInsulin > 0) (basal / totalInsulin) * 100 else 0.0,
            bolusPercentage = if (totalInsulin > 0) (bolus / totalInsulin) * 100 else 0.0,
            readingsCount = processed.size,
            overnightAvg = overnightAvg,
            overnightTir = overnightTir,
            overnightTbr = overnightTbr
        )
    }

    fun calculateAgp(
        processed: List<ProcessedGlucose>,
        zone: ZoneId,
        useOffset: Boolean
    ): List<AgpPoint> {
        val hourBuckets = Array(24) { mutableListOf<Double>() }

        for (pg in processed) {
            val hour = pg.instant.atZone(zone).hour
            hourBuckets[hour].add(if (useOffset) pg.calibratedValue else pg.rawValue)
        }

        return (0..23).map { hr ->
            val values = hourBuckets[hr]
            if (values.isEmpty()) {
                AgpPoint(hr, 0.0, 0.0, 0.0, 0.0, 0.0)
            } else {
                values.sort()
                AgpPoint(
                    hour = hr,
                    median = getPercentile(values, 0.5),
                    p25 = getPercentile(values, 0.25),
                    p75 = getPercentile(values, 0.75),
                    p10 = getPercentile(values, 0.10),
                    p90 = getPercentile(values, 0.90)
                )
            }
        }
    }

    fun calculateDailySummaries(
        processed: List<ProcessedGlucose>,
        doses: List<InsulinDose>,
        zone: ZoneId
    ): List<DailySummary> {
        val dailyMap = mutableMapOf<LocalDate, DailySummaryBuilder>()

        for (pg in processed) {
            val date = pg.instant.atZone(zone).toLocalDate()
            dailyMap.getOrPut(date) { DailySummaryBuilder(date) }.addGlucose(pg)
        }

        for (d in doses) {
            val date = TimestampParser.parseFlexibleInstant(d.timestamp)?.atZone(zone)?.toLocalDate() ?: continue
            dailyMap.getOrPut(date) { DailySummaryBuilder(date) }.addDose(d)
        }

        return dailyMap.values
            .map { it.build() }
            .sortedByDescending { it.date }
    }

    fun getPercentile(sortedValues: List<Double>, p: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        val index = p * (sortedValues.size - 1)
        val lower = index.toInt()
        val upper = lower + 1
        if (upper >= sortedValues.size) return sortedValues[lower]
        val weight = index - lower
        return sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight
    }

    private class DailySummaryBuilder(val date: LocalDate) {
        val glucoseList = mutableListOf<GlucoseMeasurement>()
        var totalInsulin = 0.0; var totalCarbs = 0.0; var basal = 0.0; var bolus = 0.0

        fun addGlucose(pg: ProcessedGlucose) {
            glucoseList.add(
                GlucoseMeasurement(
                    factoryTimestamp = pg.instant.toString(),
                    timestamp = pg.instant.toString(),
                    value = pg.rawValue.toInt(),
                    valueInMgPerDl = pg.rawValue.toInt(),
                    calibratedValue = pg.calibratedValue.toInt(),
                    epochSeconds = pg.instant.epochSecond
                )
            )
        }

        fun addDose(d: InsulinDose) {
            totalInsulin += d.units
            totalCarbs += d.carbs ?: 0.0
            if (d.type == InsulinType.SLOW) basal += d.units else bolus += d.units
        }

        fun build(): DailySummary {
            return DailySummary(
                date = date,
                glucose = glucoseList.sortedBy { it.epochSeconds ?: 0L },
                insulin = totalInsulin,
                carbs = totalCarbs,
                basal = basal,
                bolus = bolus
            )
        }
    }
}
