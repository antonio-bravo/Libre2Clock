package com.tonio.libre2clock

import com.tonio.libre2clock.util.AlertSnoozeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSnoozeHelperTest {

    @Test
    fun snoozeSilencesNonCriticalAlarmsUntilExpiry() {
        val now = 1_000_000L
        val snoozeUntil = now + 30 * 60_000L

        // Above critical threshold -> snoozed
        assertTrue(AlertSnoozeHelper.isAlarmSnoozed(now, snoozeUntil, valueMgDl = 65))
        assertTrue(AlertSnoozeHelper.isAlarmSnoozed(now, snoozeUntil, valueMgDl = 240))

        // Expired -> not snoozed
        assertFalse(AlertSnoozeHelper.isAlarmSnoozed(snoozeUntil + 1000L, snoozeUntil, valueMgDl = 65))
    }

    @Test
    fun criticalSevereHypoglycemiaNeverSnoozes() {
        val now = 1_000_000L
        val snoozeUntil = now + 30 * 60_000L

        assertFalse(AlertSnoozeHelper.isAlarmSnoozed(now, snoozeUntil, valueMgDl = 54))
        assertFalse(AlertSnoozeHelper.isAlarmSnoozed(now, snoozeUntil, valueMgDl = 42))
    }

    @Test
    fun remainingSecondsCalculatesCountdownCorrectly() {
        val now = 1_000_000L
        assertEquals(900L, AlertSnoozeHelper.getRemainingSeconds(now, now + 900_000L))
        assertEquals(0L, AlertSnoozeHelper.getRemainingSeconds(now, now - 1000L))
        assertEquals(0L, AlertSnoozeHelper.getRemainingSeconds(now, null))
    }
}
