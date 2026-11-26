package com.tonio.libre2clock.util

object AlertSnoozeHelper {
    const val CRITICAL_HYPO_BYPASS_MG_DL = 55

    /**
     * Returns true if alarms are currently snoozed.
     * Clinical safety: severe hypoglycemia (< 55 mg/dL) NEVER gets snoozed.
     */
    fun isAlarmSnoozed(
        nowMs: Long,
        snoozeUntilMs: Long?,
        valueMgDl: Int,
        criticalLowThreshold: Int = CRITICAL_HYPO_BYPASS_MG_DL
    ): Boolean {
        if (valueMgDl in 1 until criticalLowThreshold) return false
        return snoozeUntilMs != null && nowMs < snoozeUntilMs
    }

    fun getRemainingSeconds(nowMs: Long, targetMs: Long?): Long {
        if (targetMs == null || targetMs <= nowMs) return 0L
        return (targetMs - nowMs) / 1000L
    }
}
