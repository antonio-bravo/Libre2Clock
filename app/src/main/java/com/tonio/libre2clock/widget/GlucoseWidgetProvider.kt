package com.tonio.libre2clock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.tonio.libre2clock.MainActivity
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.InsulinType
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.repository.InsulinProcessor
import com.tonio.libre2clock.di.AppContainer
import com.tonio.libre2clock.util.GlucoseVoiceManager
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class GlucoseWidgetProvider : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private val syncDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.tonio.libre2clock.ACTION_UPDATE_WIDGET"
        const val ACTION_WIDGET_CLICK = "com.tonio.libre2clock.ACTION_WIDGET_CLICK"

        @Volatile
        private var lastWidgetClickTime: Long = 0L

        fun triggerUpdate(context: Context) {
            try {
                val intent = Intent(context, GlucoseWidgetProvider::class.java).apply {
                    action = ACTION_UPDATE_WIDGET
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WIDGET_CLICK) {
            val now = SystemClock.elapsedRealtime()
            val timeDiff = now - lastWidgetClickTime
            lastWidgetClickTime = now

            val pendingResult = goAsync()
            scope.launch {
                try {
                    val preferenceManager = AppContainer.providePreferenceManager(context)
                    val doubleTapSpeechEnabled = preferenceManager.widgetDoubleTapSpeech.first()
                    val doubleTapTimeoutMs = preferenceManager.widgetDoubleTapTimeoutMs.first().toLong()

                    if (timeDiff in 80L..doubleTapTimeoutMs && doubleTapSpeechEnabled) {
                        // Double tap detected -> Speak glucose aloud!
                        val repository = AppContainer.provideGlucoseRepository(context)
                        val rawMeasurement = repository.currentGlucose.first()
                            ?: withContext(Dispatchers.IO) {
                                com.tonio.libre2clock.data.local.GlucoseHistoryDatabaseHelper(context).readRecentHistory(1).firstOrNull()
                            }
                        if (rawMeasurement != null) {
                            val manualOffset = preferenceManager.glucoseOffset.first()
                            val userRanges = preferenceManager.glucoseOffsetRanges.first()
                            val autoAdjustEnabled = preferenceManager.autoAdjustEnabled.first()
                            val autoRangeOffsetMode = preferenceManager.autoRangeOffsetMode.first()
                            val capillaryReadings = preferenceManager.capillaryReadings.first()
                            val activeSensorSn = preferenceManager.activeSensorSerialNumber.first()

                            val measurement = GlucoseProcessor.process(
                                measurement = rawMeasurement,
                                manualOffset = manualOffset,
                                userRanges = userRanges,
                                autoAdjustEnabled = autoAdjustEnabled,
                                autoRangeOffsetMode = autoRangeOffsetMode,
                                capillaryReadings = capillaryReadings,
                                activeSensorSn = activeSensorSn
                            )

                            val insulinDoses = preferenceManager.insulinDoses.first()
                            val activeDoses = insulinDoses.filter { !it.isDeleted }
                            val rapidDoses = activeDoses.filter { it.type == InsulinType.RAPID }
                            val slowDoses = activeDoses.filter { it.type == InsulinType.SLOW }
                            val rapidIob = InsulinProcessor.calculateTotalIOB(rapidDoses)
                            val slowIob = InsulinProcessor.calculateTotalIOB(slowDoses)
                            val ttsDetailed = preferenceManager.ttsFormatDetailed.first()

                            val voiceManager = GlucoseVoiceManager.getInstance(context)
                            voiceManager.speakGlucose(measurement, rapidIob, slowIob, isDetailed = ttsDetailed)
                        }
                    } else {
                        // Single tap -> Launch MainActivity
                        val mainIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(mainIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (intent.action == ACTION_UPDATE_WIDGET) {
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
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Handled by onReceive
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

    private fun formatInsulinValue(iob: Double, decimalsMode: Int): String {
        return when (decimalsMode) {
            1 -> String.format(Locale.US, "%.1f", iob)
            2 -> String.format(Locale.US, "%.2f", iob)
            else -> { // 0 = Auto
                val secondDecimal = Math.round(iob * 100) % 10
                if (secondDecimal != 0L) {
                    String.format(Locale.US, "%.2f", iob)
                } else {
                    String.format(Locale.US, "%.1f", iob)
                }
            }
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
            val decimalsMode = preferenceManager.widgetInsulinDecimals.first()
            val staleIndicatorPref = preferenceManager.widgetStaleIndicator.first()
            val targetLow = preferenceManager.lowGlucoseThreshold.first()
            val targetHigh = preferenceManager.highGlucoseThreshold.first()

            // Dimensions from Widget options
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 110
            val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 250

            val isSmallLayout = compactModePref || minHeightDp < 90 || minWidthDp < 180

            // Fetch raw measurement and user calibration settings
            val rawMeasurement = repository.currentGlucose.first()
                ?: withContext(Dispatchers.IO) {
                    com.tonio.libre2clock.data.local.GlucoseHistoryDatabaseHelper(context).readRecentHistory(1).firstOrNull()
                }
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
            val activeDoses = insulinDoses.filter { !it.isDeleted }
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

            val applyStaleMode = isStale && staleIndicatorPref

            // Base RGB Color
            val baseRgb = when {
                applyStaleMode -> Color.parseColor("#2C2C2C")
                !useTargetColor -> Color.parseColor("#1E1E1E")
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
                
                val glucoseColor = if (applyStaleMode) Color.parseColor("#888888") else Color.parseColor("#FFFFFF")
                val trendColor = if (applyStaleMode) Color.parseColor("#666666") else getTrendColor(trend)
                val timeColor = if (applyStaleMode) Color.parseColor("#666666") else Color.parseColor("#DDDDDD")

                views.setTextColor(R.id.tv_glucose_value, glucoseColor)
                views.setTextColor(R.id.tv_glucose_trend, trendColor)
                views.setTextColor(R.id.tv_last_update, timeColor)

                val instant = TimestampParser.parseMeasurementInstant(measurement)
                views.setTextViewText(R.id.tv_last_update, syncDateFormatter.format(instant))
            } else {
                views.setTextViewText(R.id.tv_glucose_value, "---")
                views.setTextViewText(R.id.tv_glucose_trend, "")
                views.setTextViewText(R.id.tv_last_update, "----")
                views.setTextColor(R.id.tv_glucose_value, Color.parseColor("#888888"))
            }

            // Calculate Insulin & FS
            val rapidDoses = activeDoses.filter { it.type == InsulinType.RAPID }
            val slowDoses = activeDoses.filter { it.type == InsulinType.SLOW }
            
            val rapidIOB = InsulinProcessor.calculateTotalIOB(rapidDoses)
            val slowIOB = InsulinProcessor.calculateTotalIOB(slowDoses)

            val formattedRapidVal = formatInsulinValue(rapidIOB, decimalsMode)
            val formattedSlowVal = formatInsulinValue(slowIOB, decimalsMode)

            val calculatedTdi = manualTdi ?: InsulinProcessor.calculateAverageDaily(activeDoses, 30)
            val fsValue = InsulinProcessor.calculateISF(calculatedTdi, isfRuleC, manualIsf)
            val formattedFS = if (fsValue > 0) {
                if (fsValue % 1.0 == 0.0) String.format(Locale.US, "%.0f", fsValue) else String.format(Locale.US, "%.1f", fsValue)
            } else "-"

            // Localized labels for Rapid / Slow
            val rapidLabel = if (isSmallLayout) context.getString(R.string.widget_rapid_compact_label) else context.getString(R.string.widget_rapid_label)
            val slowLabel = if (isSmallLayout) context.getString(R.string.widget_slow_compact_label) else context.getString(R.string.widget_slow_label)

            val rapidText = if (showRapidPref) {
                if (isSmallLayout) context.getString(R.string.widget_rapid_compact_text, rapidLabel, formattedRapidVal)
                else context.getString(R.string.widget_rapid_text, rapidLabel, formattedRapidVal)
            } else ""

            val slowText = if (showSlowPref) {
                if (isSmallLayout) context.getString(R.string.widget_slow_compact_text, slowLabel, formattedSlowVal)
                else context.getString(R.string.widget_slow_text, slowLabel, formattedSlowVal)
            } else ""

            val subTextColor = if (applyStaleMode) Color.parseColor("#666666") else Color.parseColor("#82B1FF")
            val slowTextColor = if (applyStaleMode) Color.parseColor("#666666") else Color.parseColor("#FF8A80")
            val fsTextColor = if (applyStaleMode) Color.parseColor("#666666") else Color.parseColor("#C5E1A5")

            views.setTextViewText(R.id.tv_insulin_rapid, rapidText)
            views.setTextColor(R.id.tv_insulin_rapid, subTextColor)

            views.setTextViewText(R.id.tv_insulin_slow, slowText)
            views.setTextColor(R.id.tv_insulin_slow, slowTextColor)

            views.setTextViewText(R.id.tv_insulin_fs, if (showFsPref) "FS: $formattedFS" else "")
            views.setTextColor(R.id.tv_insulin_fs, fsTextColor)

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

            // Pending Intent to capture Click / Double Tap
            val clickIntent = Intent(context, GlucoseWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CLICK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
