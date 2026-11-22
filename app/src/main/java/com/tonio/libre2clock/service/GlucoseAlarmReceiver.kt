package com.tonio.libre2clock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.tonio.libre2clock.widget.GlucoseWidgetProvider

class GlucoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, GlucoseForegroundService::class.java).apply {
            action = GlucoseForegroundService.ACTION_POLL_GLUCOSE
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e("GlucoseAlarmReceiver", "Error starting GlucoseForegroundService: ${e.message}", e)
            // Refresh widget from available local DB readings even if service launch was restricted
            GlucoseWidgetProvider.triggerUpdate(context)
        }
    }
}
