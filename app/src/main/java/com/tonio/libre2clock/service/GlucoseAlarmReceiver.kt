package com.tonio.libre2clock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class GlucoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, GlucoseForegroundService::class.java).apply {
            action = GlucoseForegroundService.ACTION_POLL_GLUCOSE
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            // Log or handle background start failures gracefully
        }
    }
}
