package com.tonio.libre2clock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.tonio.libre2clock.MainActivity
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
    
    private val syncDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateAppWidgetSuspend(context, appWidgetManager, appWidgetId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun getTrendColor(trend: Int?): Int {
        return when (trend) {
            1 -> Color.parseColor("#FF5252") // ↓ Rapidly falling (Red)
            2 -> Color.parseColor("#FF9800") // ↘ Falling (Orange)
            3 -> Color.parseColor("#FFFFFF") // → Stable (White)
            4 -> Color.parseColor("#FFD54F") // ↗ Rising (Yellow)
            5 -> Color.parseColor("#FF5252") // ↑ Rapidly rising (Red)
            else -> Color.parseColor("#FFFFFF")
        }
    }

    private suspend fun updateAppWidgetSuspend(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_glucose)

        try {
            val repository = AppContainer.provideGlucoseRepository(context)
            val preferenceManager = AppContainer.providePreferenceManager(context)

            // Preferences
            val compactModePref = preferenceManager.widgetCompactMode.first()
            val showRapidPref = preferenceManager.widgetShowRapidInsulin.first()
            val showSlowPref = preferenceManager.widgetShowSlowInsulin.first()
            val showFsPref = preferenceManager.widgetShowFS.first()
            val showLastUpdatePref = preferenceManager.widgetShowLastUpdate.first()
            val opacityPercent = preferenceManager.widgetBackgroundOpacity.first()
            val useTargetColor = preferenceManager.widgetUseTargetColorBg.first()
            val targetLow = preferenceManager.lowGlucoseThreshold.first()
            val targetHigh = preferenceManager.highGlucoseThreshold.first()

            // Dimensions from Widget options
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 110
            val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 250

            val isSmallLayout = compactModePref || minHeightDp < 90 || minWidthDp < 180

            // Fetch raw measurement and user calibration settings
            val rawMeasurement = repository.currentGlucose.first()
            val manualOffset = preferenceManager.glucoseOffset.first()
            val userRanges = preferenceManager.glucoseOffsetRanges.first()
            val autoAdjustEnabled = preferenceManager.autoAdjustEnabled.first()
            val autoRangeOffsetMode = preferenceManager.autoRangeOffsetMode.first()
            val capillaryReadings = preferenceManager.capillaryReadings.first()
            val activeSensorSn = preferenceManager.activeSensorSerialNumber.first()

            // Process calibration
            val measurement = if (rawMeasurement != null) {
                GlucoseProcessor.process(
                    measurement = rawMeasurement,
                    manualOffset = manualOffset,
                    userRanges = userRanges,
                    autoAdjustEnabled = autoAdjustEnabled,
                    autoRangeOffsetMode = autoRangeOffsetMode,
                    capillaryReadings = capillaryReadings,
                    activeSensorSn = activeSensorSn
                )
            } else null

            val insulinDoses = preferenceManager.insulinDoses.first()
            val manualIsf = preferenceManager.manualIsf.first()
            val isfRuleC = preferenceManager.isfRuleConstant.first()
            val manualTdi = preferenceManager.manualTdi.first()

            val evalVal = measurement?.calibratedValue ?: measurement?.value ?: 0
            val rawVal = measurement?.value ?: 0
            val dualValueStr = if (measurement != null) {
                GlucoseProcessor.formatDualValue(rawVal, evalVal)
            } else {
                "---"
            }
            
            val trend = measurement?.trendArrow

            val isStale = if (measurement != null) {
                val instant = TimestampParser.parseMeasurementInstant(measurement)
                java.time.Duration.between(instant, Instant.now()).toMinutes() > 15
            } else true

            // Base RGB Color
            val baseRgb = when {
                !useTargetColor -> Color.parseColor("#1E1E1E")
                isStale -> Color.parseColor("#444444")
                evalVal < targetLow -> Color.parseColor("#C83B34")
                evalVal > targetHigh -> Color.parseColor("#E89940")
                else -> Color.parseColor("#9DCA44")
            }

            // Alpha calculation
            val alpha = (opacityPercent * 255) / 100
            val finalBgColor = (alpha shl 24) or (baseRgb and 0x00FFFFFF)

            // Update Glucose and Trend text
            if (measurement != null) {
                views.setTextViewText(R.id.tv_glucose_value, dualValueStr)
                views.setTextViewText(R.id.tv_glucose_trend, GlucoseProcessor.getTrendArrowSymbol(trend ?: 0))
                views.setTextColor(R.id.tv_glucose_trend, getTrendColor(trend))
                
                val instant = TimestampParser.parseMeasurementInstant(measurement)
                views.setTextViewText(R.id.tv_last_update, syncDateFormatter.format(instant))
            } else {
                views.setTextViewText(R.id.tv_glucose_value, "---")
                views.setTextViewText(R.id.tv_glucose_trend, "")
                views.setTextViewText(R.id.tv_last_update, "----")
            }

            // Calculate Insulin & FS
            val rapidDoses = insulinDoses.filter { it.type == InsulinType.RAPID }
            val slowDoses = insulinDoses.filter { it.type == InsulinType.SLOW }
            
            val rapidIOB = InsulinProcessor.calculateTotalIOB(rapidDoses)
            val slowIOB = InsulinProcessor.calculateTotalIOB(slowDoses)

            val calculatedTdi = manualTdi ?: InsulinProcessor.calculateAverageDaily(insulinDoses, 30)
            val fsValue = InsulinProcessor.calculateISF(calculatedTdi, isfRuleC, manualIsf)
            val formattedFS = if (fsValue > 0) {
                if (fsValue % 1.0 == 0.0) String.format(Locale.US, "%.0f", fsValue) else String.format(Locale.US, "%.1f", fsValue)
            } else "-"

            // Configure Insulin & FS text fields
            if (isSmallLayout) {
                views.setTextViewText(R.id.tv_insulin_rapid, if (showRapidPref) String.format(Locale.getDefault(), "R: %.1fU", rapidIOB) else "")
                views.setTextViewText(R.id.tv_insulin_slow, if (showSlowPref) String.format(Locale.getDefault(), "L: %.1fU", slowIOB) else "")
                views.setTextViewText(R.id.tv_insulin_fs, if (showFsPref) "FS: $formattedFS" else "")
            } else {
                views.setTextViewText(R.id.tv_insulin_rapid, if (showRapidPref) String.format(Locale.getDefault(), "Rápida: %.1f U", rapidIOB) else "")
                views.setTextViewText(R.id.tv_insulin_slow, if (showSlowPref) String.format(Locale.getDefault(), "Lenta: %.1f U", slowIOB) else "")
                views.setTextViewText(R.id.tv_insulin_fs, if (showFsPref) "FS: $formattedFS" else "")
            }

            // Visibility based on user settings
            val showAnyInsulin = showRapidPref || showSlowPref || showFsPref
            views.setViewVisibility(R.id.tv_last_update, if (showLastUpdatePref) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.ll_insulin_container, if (showAnyInsulin) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.divider_line, if (showAnyInsulin && !isSmallLayout) View.VISIBLE else View.GONE)

            // Responsive Text Sizes for small tiles
            if (isSmallLayout) {
                if (dualValueStr.length > 5) {
                    views.setTextViewTextSize(R.id.tv_glucose_value, TypedValue.COMPLEX_UNIT_SP, 22f)
                    views.setTextViewTextSize(R.id.tv_glucose_trend, TypedValue.COMPLEX_UNIT_SP, 20f)
                } else {
                    views.setTextViewTextSize(R.id.tv_glucose_value, TypedValue.COMPLEX_UNIT_SP, 28f)
                    views.setTextViewTextSize(R.id.tv_glucose_trend, TypedValue.COMPLEX_UNIT_SP, 24f)
                }
            } else {
                views.setTextViewTextSize(R.id.tv_glucose_value, TypedValue.COMPLEX_UNIT_SP, 40f)
                views.setTextViewTextSize(R.id.tv_glucose_trend, TypedValue.COMPLEX_UNIT_SP, 32f)
            }

            views.setInt(R.id.widget_root, "setBackgroundColor", finalBgColor)

            // Pending Intent to launch App on click
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
