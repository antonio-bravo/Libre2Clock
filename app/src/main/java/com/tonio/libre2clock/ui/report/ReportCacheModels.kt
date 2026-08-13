package com.tonio.libre2clock.ui.report

import com.tonio.libre2clock.data.model.GlucoseMeasurement
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class DailySummaryCacheItem(
    val dateEpochDay: Long,
    val glucose: List<GlucoseMeasurement>,
    val insulin: Double,
    val carbs: Double,
    val basal: Double,
    val bolus: Double
) {
    fun toDailySummary(): DailySummary {
        return DailySummary(
            date = LocalDate.ofEpochDay(dateEpochDay),
            glucose = glucose,
            insulin = insulin,
            carbs = carbs,
            basal = basal,
            bolus = bolus
        )
    }

    companion object {
        fun fromDailySummary(summary: DailySummary): DailySummaryCacheItem {
            return DailySummaryCacheItem(
                dateEpochDay = summary.date.toEpochDay(),
                glucose = summary.glucose,
                insulin = summary.insulin,
                carbs = summary.carbs,
                basal = summary.basal,
                bolus = summary.bolus
            )
        }
    }
}
