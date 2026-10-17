package com.tonio.libre2clock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.graphics.Color
import android.content.ComponentName
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.repository.InsulinProcessor
import com.tonio.libre2clock.di.AppContainer
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class GlucoseWidgetProvider : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private val SyncDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.tonio.libre2clock.ACTION_UPDATE_WIDGET" || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val pendingResult = goAsync()
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, GlucoseWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            scope.launch {
                try {
                    for (appWidgetId in appWidgetIds) {
                        updateAppWidgetSuspend(context, appWidgetManager, appWidgetId)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Handled by onReceive to ensure goAsync() works correctly with coroutines
    }

    private suspend fun updateAppWidgetSuspend(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_glucose)

        try {
            val repository = AppContainer.provideGlucoseRepository(context)
            val preferenceManager = AppContainer.providePreferenceManager(context)

            // Fetch latest data
            val measurement = repository.currentGlucose.first()
            val insulinDoses = preferenceManager.insulinDoses.first()

            val evalVal = measurement?.calibratedValue ?: measurement?.value ?: 0
            val rawVal = measurement?.value ?: 0
            val dualValueStr = if (measurement != null) {
                GlucoseProcessor.formatDualValue(rawVal, evalVal)
            } else {
                "---"
            }
            
            val trend = measurement?.trendArrow

            // Determine color
            val targetLow = 70
            val targetHigh = 180
            
            val isStale = if (measurement != null) {
                val instant = TimestampParser.parseMeasurementInstant(measurement)
                java.time.Duration.between(instant, Instant.now()).toMinutes() > 15
            } else true
            
            val bgColor = when {
                isStale -> Color.parseColor("#444444") // Dark grey if stale
                evalVal < targetLow -> Color.parseColor("#C83B34") // Red
                evalVal > targetHigh -> Color.parseColor("#E89940") // Orange
                else -> Color.parseColor("#9DCA44") // Green
            }
            
            // Update UI elements
            if (measurement != null) {
                views.setTextViewText(R.id.tv_glucose_value, dualValueStr)
                views.setTextViewText(R.id.tv_glucose_trend, GlucoseProcessor.getTrendArrowSymbol(trend ?: 0))
                
                val instant = TimestampParser.parseMeasurementInstant(measurement)
                views.setTextViewText(R.id.tv_last_update, SyncDateFormatter.format(instant))
            } else {
                views.setTextViewText(R.id.tv_glucose_value, "---")
                views.setTextViewText(R.id.tv_glucose_trend, "")
                views.setTextViewText(R.id.tv_last_update, "----")
            }

            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)
            
            // Calculate Insulin
            val rapidDoses = insulinDoses.filter { it.type == InsulinType.RAPID }
            val slowDoses = insulinDoses.filter { it.type == InsulinType.SLOW }
            
            val rapidIOB = InsulinProcessor.calculateTotalIOB(rapidDoses)
            val slowIOB = InsulinProcessor.calculateTotalIOB(slowDoses)

            views.setTextViewText(R.id.tv_insulin_rapid, String.format(Locale.getDefault(), "Rápida: %.1f U", rapidIOB))
            views.setTextViewText(R.id.tv_insulin_slow, String.format(Locale.getDefault(), "Lenta: %.1f U", slowIOB))

            appWidgetManager.updateAppWidget(appWidgetId, views)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
