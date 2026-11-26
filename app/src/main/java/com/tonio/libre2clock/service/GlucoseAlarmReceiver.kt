package com.tonio.libre2clock.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tonio.libre2clock.MainActivity
import com.tonio.libre2clock.R
import com.tonio.libre2clock.di.AppContainer
import com.tonio.libre2clock.util.LogLevel
import com.tonio.libre2clock.widget.GlucoseWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GlucoseAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE_ALARM = "com.tonio.libre2clock.ACTION_SNOOZE_ALARM"
        const val ACTION_START_HYPO_15_RULE = "com.tonio.libre2clock.ACTION_START_HYPO_15_RULE"
        const val ACTION_HYPO_15_CHECK = "com.tonio.libre2clock.ACTION_HYPO_15_CHECK"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val HYPO_15_NOTIFICATION_ID = 4

        private const val FALLBACK_POLL_INTERVAL_MS = 300_000L
        private val fallbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun scheduleHypo15CheckAlarm(context: Context, triggerAtMs: Long) {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(appContext, GlucoseAlarmReceiver::class.java).apply {
                action = ACTION_HYPO_15_CHECK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                1515,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_SNOOZE_ALARM -> {
                val mins = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 30)
                val pending = goAsync()
                fallbackScope.launch {
                    try {
                        val pm = AppContainer.providePreferenceManager(appContext)
                        pm.saveAlarmSnoozeUntilMs(System.currentTimeMillis() + mins * 60_000L)
                        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        nm?.cancel(GlucoseForegroundService.GLUCOSE_ALARM_NOTIFICATION_ID)
                        AppContainer.provideEventLogManager(appContext).log(
                            LogLevel.INFO,
                            "AlarmSnooze",
                            "Alarmas silenciadas $mins min desde notificación"
                        )
                    } finally {
                        pending.finish()
                    }
                }
                return
            }

            ACTION_START_HYPO_15_RULE -> {
                val pending = goAsync()
                fallbackScope.launch {
                    try {
                        val now = System.currentTimeMillis()
                        val endMs = now + 15 * 60_000L
                        val pm = AppContainer.providePreferenceManager(appContext)
                        pm.saveHypo15RuleEndsAtMs(endMs)
                        pm.saveAlarmSnoozeUntilMs(endMs)
                        scheduleHypo15CheckAlarm(appContext, endMs)
                        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        nm?.cancel(GlucoseForegroundService.GLUCOSE_ALARM_NOTIFICATION_ID)
                        AppContainer.provideEventLogManager(appContext).log(
                            LogLevel.INFO,
                            "Hypo15Rule",
                            "Asistente Regla 15/15 iniciado (recordatorio en 15 min)"
                        )
                    } finally {
                        pending.finish()
                    }
                }
                return
            }

            ACTION_HYPO_15_CHECK -> {
                val pending = goAsync()
                fallbackScope.launch {
                    try {
                        val pm = AppContainer.providePreferenceManager(appContext)
                        pm.saveHypo15RuleEndsAtMs(null)
                        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        val mainIntent = PendingIntent.getActivity(
                            appContext,
                            1516,
                            Intent(appContext, MainActivity::class.java),
                            PendingIntent.FLAG_IMMUTABLE
                        )
                        val notification = NotificationCompat.Builder(appContext, GlucoseForegroundService.ALERT_CHANNEL_ID)
                            .setContentTitle(appContext.getString(R.string.hypo_15_check_notif_title))
                            .setContentText(appContext.getString(R.string.hypo_15_check_notif_body))
                            .setSmallIcon(android.R.drawable.stat_notify_sync)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_ALARM)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setDefaults(NotificationCompat.DEFAULT_ALL)
                            .setContentIntent(mainIntent)
                            .setAutoCancel(true)
                            .build()
                        nm?.notify(HYPO_15_NOTIFICATION_ID, notification)
                    } finally {
                        pending.finish()
                    }
                }
                return
            }
        }

        val serviceIntent = Intent(context, GlucoseForegroundService::class.java).apply {
            action = GlucoseForegroundService.ACTION_POLL_GLUCOSE
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            // Android 12+/15+ may restrict starting a dataSync FGS from background (e.g. 6h daily quota).
            // Fall back to a short-lived goAsync() poll so glucose, widget, and alarm rescheduling keep running.
            Log.w("GlucoseAlarmReceiver", "FGS start restricted (${e.message}); running goAsync fallback poll")
            val pendingResult = goAsync()
            fallbackScope.launch {
                try {
                    val eventLogger = AppContainer.provideEventLogManager(appContext)
                    eventLogger.log(
                        LogLevel.WARNING,
                        "AlarmReceiver",
                        "Servicio en primer plano restringido por el sistema; ejecutando sondeo alternativo",
                        e.message
                    )
                    val repository = AppContainer.provideGlucoseRepository(appContext)
                    repository.initialize()
                    repository.fetchLatestGlucose()
                } catch (pollError: Exception) {
                    Log.e("GlucoseAlarmReceiver", "Fallback poll failed: ${pollError.message}", pollError)
                } finally {
                    GlucoseWidgetProvider.triggerUpdate(appContext)
                    GlucoseForegroundService.scheduleNextPollAlarm(appContext, FALLBACK_POLL_INTERVAL_MS)
                    pendingResult.finish()
                }
            }
        }
    }
}
