package com.tonio.libre2clock.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import java.time.Instant

object XDripBroadcastManager {

    private const val TAG = "XDripBroadcastManager"

    // Actions & Extras recognized by xDrip+, AndroidAPS, and Wear OS apps
    private const val ACTION_XDRIP_BROADCAST = "com.eveningoutpost.dexdrip.cgm.broadcast"
    private const val ACTION_XDRIP_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate"

    private const val EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
    private const val EXTRA_BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
    private const val EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Timestamp"
    private const val EXTRA_RAW = "com.eveningoutpost.dexdrip.Extras.Raw"
    private const val EXTRA_SOURCE = "com.eveningoutpost.dexdrip.Extras.Source"

    fun sendBroadcast(context: Context, measurement: GlucoseMeasurement, useCalibrated: Boolean = true) {
        try {
            val evalVal = if (useCalibrated && measurement.calibratedValue > 0) {
                measurement.calibratedValue.toDouble()
            } else {
                measurement.value.toDouble()
            }

            val instant = TimestampParser.parseMeasurementInstant(measurement) ?: Instant.now()
            val timestamp = instant.toEpochMilli()
            val slopeName = getSlopeName(measurement.trendArrow)

            val intent = Intent(ACTION_XDRIP_BROADCAST).apply {
                putExtra(EXTRA_BG_ESTIMATE, evalVal)
                putExtra(EXTRA_BG_SLOPE_NAME, slopeName)
                putExtra(EXTRA_TIMESTAMP, timestamp)
                putExtra(EXTRA_RAW, measurement.value.toDouble())
                putExtra(EXTRA_SOURCE, "Libre2Clock")
            }

            context.sendBroadcast(intent)

            // Secondary broadcast for legacy xDrip consumers
            val legacyIntent = Intent(ACTION_XDRIP_ESTIMATE).apply {
                putExtra(EXTRA_BG_ESTIMATE, evalVal)
                putExtra(EXTRA_BG_SLOPE_NAME, slopeName)
                putExtra(EXTRA_TIMESTAMP, timestamp)
                putExtra(EXTRA_RAW, measurement.value.toDouble())
            }

            context.sendBroadcast(legacyIntent)
            Log.i(TAG, "Sent xDrip+ local broadcast for glucose $evalVal mg/dL ($slopeName)")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending xDrip+ local broadcast", e)
        }
    }

    private fun getSlopeName(trend: Int?): String {
        return when (trend) {
            1 -> "DoubleDown"
            2 -> "FortyFiveDown"
            3 -> "Flat"
            4 -> "FortyFiveUp"
            5 -> "DoubleUp"
            else -> "Flat"
        }
    }
}
