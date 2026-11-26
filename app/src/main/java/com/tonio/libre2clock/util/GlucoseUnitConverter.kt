package com.tonio.libre2clock.util

import com.tonio.libre2clock.data.model.GlucoseUnit
import java.util.Locale

object GlucoseUnitConverter {
    private const val MG_DL_PER_MMOL_L = 18.0182

    fun mgDlToMmol(mgDl: Number): Double = mgDl.toDouble() / MG_DL_PER_MMOL_L

    fun unitLabel(unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_DL -> "mg/dL"
        GlucoseUnit.MMOL_L -> "mmol/L"
    }

    fun formatValue(mgDl: Int, unit: GlucoseUnit): String {
        if (mgDl <= 0) return "---"
        return when (unit) {
            GlucoseUnit.MG_DL -> "$mgDl"
            GlucoseUnit.MMOL_L -> String.format(Locale.US, "%.1f", mgDlToMmol(mgDl))
        }
    }

    fun formatDualValue(rawMgDl: Int, calibratedMgDl: Int, unit: GlucoseUnit): String {
        if (rawMgDl <= 0) return "---"
        return if (rawMgDl == calibratedMgDl || calibratedMgDl <= 0) {
            formatValue(rawMgDl, unit)
        } else {
            "${formatValue(rawMgDl, unit)}(${formatValue(calibratedMgDl, unit)})"
        }
    }
}
