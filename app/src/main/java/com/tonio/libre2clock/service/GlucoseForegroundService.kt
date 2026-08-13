package com.tonio.libre2clock.service

import android.app.*
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.os.BatteryManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import com.tonio.libre2clock.MainActivity
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.WatchNotificationMode
import com.tonio.libre2clock.di.AppContainer
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.GlucoseRepositoryImpl
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

class GlucoseForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: GlucoseRepository
    private lateinit var repositoryImpl: GlucoseRepositoryImpl
    private lateinit var preferenceManager: PreferenceManager
    private var syncJob: Job? = null
    private var lastWatchAlertEpochMinute: Long = -1L
    
    // Calibration Cache
    private var watchAlertsEnabledCached: Boolean = false
    private var watchNotificationModeCached: WatchNotificationMode = WatchNotificationMode.OFF
    private var watchAlertIntervalMinutesCached: Int = 60
    private var watchAlertStartMinuteCached: Int = 0
    private var lowGlucoseAlarmEnabledCached: Boolean = false
    private var highGlucoseAlarmEnabledCached: Boolean = false
    private var useCalibratedForAlarmsCached: Boolean = true
    private var watchSchedulesCached: List<com.tonio.libre2clock.data.model.AlarmSchedule> = emptyList()
    private var alarmSchedulesCached: List<com.tonio.libre2clock.data.model.AlarmSchedule> = emptyList()
    private var batteryLowThresholdCached: Int = 15
    private var batteryCriticalThresholdCached: Int = 5
    private var disableFastOnSlowChargeCached: Boolean = true
    private var glucoseOffsetCached: Int = 0
    private var glucoseOffsetRangesCached: List<com.tonio.libre2clock.data.model.GlucoseOffsetRange> = emptyList()
    private var autoAdjustEnabledCached: Boolean = false
    private var autoRangeOffsetModeCached: AutoRangeOffsetMode = AutoRangeOffsetMode.OFF
    private var capillaryReadingsCached: List<com.tonio.libre2clock.data.model.CapillaryMeasurement> = emptyList()

    private var lastLowAlarmAtMillis: Long = 0L
    private var lastHighAlarmAtMillis: Long = 0L
    private var lastForegroundNotificationContent: String? = null

    companion object {
        const val CHANNEL_ID = "glucose_monitoring_channel"
        const val ALERT_CHANNEL_ID = "glucose_alerts_v2"
        const val NOTIFICATION_ID = 1
        const val TEST_ALERT_TIMEOUT_MS = 15 * 60 * 1000L
        const val WATCH_ALERT_TIMEOUT_MS = 10 * 60 * 1000L
        const val GLUCOSE_ALARM_COOLDOWN_MS = 15 * 60 * 1000L
        const val LOW_GLUCOSE_THRESHOLD = 70
        const val HIGH_GLUCOSE_THRESHOLD = 180
    }

    override fun onCreate() {
        super.onCreate()
        preferenceManager = AppContainer.providePreferenceManager(applicationContext)
        repositoryImpl = AppContainer.provideGlucoseRepository(applicationContext)
        repository = repositoryImpl
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "TEST_NOTIFICATION") {
            triggerTestNotification()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Starting glucose monitoring..."))
        
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            // Re-initialize repository with stored credentials
            repositoryImpl.initialize()
            
            launch {
                repository.currentGlucose.collectLatest { measurement ->
                    measurement?.let {
                        updateNotification(it)
                    }
                }
            }

            launch {
                preferenceManager.watchAlertsEnabled.collect {
                    watchAlertsEnabledCached = it
                }
            }

            launch {
                preferenceManager.watchNotificationMode.collect {
                    watchNotificationModeCached = it
                }
            }

            launch {
                preferenceManager.watchAlertIntervalMinutes.collect {
                    watchAlertIntervalMinutesCached = it.coerceIn(5, 180)
                }
            }

            launch {
                preferenceManager.watchAlertStartMinute.collect {
                    watchAlertStartMinuteCached = it.coerceIn(0, 59)
                }
            }

            launch {
                preferenceManager.lowGlucoseAlarmEnabled.collect {
                    lowGlucoseAlarmEnabledCached = it
                }
            }

            launch {
                preferenceManager.highGlucoseAlarmEnabled.collect {
                    highGlucoseAlarmEnabledCached = it
                }
            }

            launch {
                preferenceManager.useCalibratedForAlarms.collect {
                    useCalibratedForAlarmsCached = it
                }
            }

            launch {
                preferenceManager.watchNotificationSchedules.collect {
                    watchSchedulesCached = it
                }
            }

            launch {
                preferenceManager.glucoseAlarmSchedules.collect {
                    alarmSchedulesCached = it
                }
            }

            launch {
                preferenceManager.batteryLowThreshold.collect {
                    batteryLowThresholdCached = it
                }
            }

            launch {
                preferenceManager.batteryCriticalThreshold.collect {
                    batteryCriticalThresholdCached = it
                }
            }

            launch {
                preferenceManager.disableFastRefreshOnSlowCharge.collect {
                    disableFastOnSlowChargeCached = it
                }
            }

            launch {
                preferenceManager.glucoseOffset.collect {
                    glucoseOffsetCached = it
                    repository.currentGlucose.first()?.let { updateNotification(it) }
                }
            }

            launch {
                preferenceManager.glucoseOffsetRanges.collect {
                    glucoseOffsetRangesCached = it
                    repository.currentGlucose.first()?.let { updateNotification(it) }
                }
            }

            launch {
                preferenceManager.autoAdjustEnabled.collect {
                    autoAdjustEnabledCached = it
                    repository.currentGlucose.first()?.let { updateNotification(it) }
                }
            }

            launch {
                preferenceManager.autoRangeOffsetMode.collect {
                    autoRangeOffsetModeCached = it
                    repository.currentGlucose.first()?.let { updateNotification(it) }
                }
            }

            launch {
                preferenceManager.capillaryReadings.collect {
                    capillaryReadingsCached = it
                    repository.currentGlucose.first()?.let { updateNotification(it) }
                }
            }

            var firstPoll = true
            while (isActive) {
                val fetchResult = repository.fetchLatestGlucose()
                val measurement = fetchResult.getOrNull()
                if (measurement != null) {
                    maybeSendWatchAlert(measurement)
                    maybeSendGlucoseAlarms(measurement)
                }

                val batteryStatus = getDetailedBatteryStatus()
                val hasActiveAlerts = (watchNotificationModeCached != WatchNotificationMode.OFF) || lowGlucoseAlarmEnabledCached || highGlucoseAlarmEnabledCached || watchSchedulesCached.isNotEmpty() || alarmSchedulesCached.isNotEmpty()
                
                val pollingIntervalMs = when {
                    firstPoll -> 60_000L
                    batteryStatus == BatteryState.CRITICAL -> 900_000L // 15 min mandatory
                    batteryStatus == BatteryState.LOW && hasActiveAlerts -> 300_000L // 5 min max
                    batteryStatus == BatteryState.LOW -> 900_000L // 15 min
                    batteryStatus == BatteryState.SLOW_CHARGING && disableFastOnSlowChargeCached -> 300_000L // 5 min to charge faster
                    hasActiveAlerts -> 60_000L // Normal fast refresh
                    else -> 300_000L // Normal baseline
                }

                firstPoll = false
                delay(pollingIntervalMs)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel for persistent notification (Low priority)
            val name = "Glucose Monitoring"
            val descriptionText = "Shows current glucose readings"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)

            // Channel for Test Alerts (High priority for watch mirroring)
            val alertName = "Glucose Alerts"
            val alertDescription = "Notifications for glucose tests and alerts"
            val alertImportance = NotificationManager.IMPORTANCE_HIGH
            val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, alertName, alertImportance).apply {
                description = alertDescription
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Libre2Clock")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync) // Better than generic info
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(measurement: GlucoseMeasurement) {
        val processed = processMeasurement(measurement)
        val trendStr = GlucoseProcessor.getTrendArrowSymbol(processed.trendArrow)
        val dualValue = GlucoseProcessor.formatDualValue(processed.value, processed.calibratedValue)
        val content = "$dualValue mg/dL $trendStr"
        if (content == lastForegroundNotificationContent) return
        lastForegroundNotificationContent = content
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun getDetailedBatteryStatus(): BatteryState {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return BatteryState.NORMAL
        
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugType = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

        if (level < 0 || scale <= 0) return BatteryState.NORMAL
        val batteryPercent = (level * 100) / scale

        return when {
            batteryPercent <= batteryCriticalThresholdCached -> BatteryState.CRITICAL
            batteryPercent <= batteryLowThresholdCached -> BatteryState.LOW
            status == BatteryManager.BATTERY_STATUS_CHARGING && plugType == BatteryManager.BATTERY_PLUGGED_USB -> BatteryState.SLOW_CHARGING
            else -> BatteryState.NORMAL
        }
    }

    private enum class BatteryState {
        NORMAL, LOW, CRITICAL, SLOW_CHARGING
    }

    private fun processMeasurement(measurement: GlucoseMeasurement): GlucoseMeasurement {
        return GlucoseProcessor.process(
            measurement = measurement,
            manualOffset = glucoseOffsetCached,
            userRanges = glucoseOffsetRangesCached,
            autoAdjustEnabled = autoAdjustEnabledCached,
            autoRangeOffsetMode = autoRangeOffsetModeCached,
            capillaryReadings = capillaryReadingsCached
        )
    }

    private fun triggerTestNotification() {
        serviceScope.launch {
            repositoryImpl.initialize()
            val fetchResult = repository.fetchLatestGlucose()
            val measurement = fetchResult.getOrNull()
                ?: repository.currentGlucose.first()
                ?: repository.historicalGlucose.first().firstOrNull()
            val fetchErrorMessage = fetchResult.exceptionOrNull()?.message
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val testNotificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

            val notification = if (measurement != null) {
                val processed = processMeasurement(measurement)
                val title = buildWatchPlainTitle(processed)
                val dualValue = GlucoseProcessor.formatDualValue(processed.value, processed.calibratedValue)
                val styledTitle = buildWatchStyledTitle(title, dualValue)

                NotificationCompat.Builder(this@GlucoseForegroundService, ALERT_CHANNEL_ID)
                    .setContentTitle(styledTitle)
                    .setContentText(title)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .setBigContentTitle(styledTitle)
                            .bigText(styledTitle)
                    )
                    .setVibrate(longArrayOf(0, 500, 200, 500))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setTimeoutAfter(TEST_ALERT_TIMEOUT_MS)
                    .build()
            } else {
                NotificationCompat.Builder(this@GlucoseForegroundService, ALERT_CHANNEL_ID)
                    .setContentTitle("No glucose data")
                    .setContentText(fetchErrorMessage ?: "No se pudo obtener lectura actual")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(longArrayOf(0, 500, 200, 500))
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setTimeoutAfter(TEST_ALERT_TIMEOUT_MS)
                    .build()
            }

            notificationManager.notify(testNotificationId, notification)
        }
    }

                private suspend fun maybeSendWatchAlert(measurement: GlucoseMeasurement) {
                    val mode = watchNotificationModeCached
                    if (mode == WatchNotificationMode.OFF) return
                    
                    val nowMillis = System.currentTimeMillis()
                    val epochMinute = nowMillis / 60_000L
                    if (epochMinute == lastWatchAlertEpochMinute) return
                    
                    val nowLocal = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
                    val enabledSchedules = watchSchedulesCached.filter { it.isEnabled }

                    val periodicTrigger = isTriggerMinute(
                        nowLocal = nowLocal,
                        interval = watchAlertIntervalMinutesCached,
                        startMinute = watchAlertStartMinuteCached
                    )

                    val scheduleTrigger = enabledSchedules
                        .filter { isCurrentTimeInSchedule(it, nowLocal) }
                        .any { schedule ->
                            isTriggerMinute(
                                nowLocal = nowLocal,
                                interval = schedule.intervalMinutes ?: watchAlertIntervalMinutesCached,
                                startMinute = schedule.startMinute ?: watchAlertStartMinuteCached
                            )
                        }
                    
                    val shouldTrigger = when (mode) {
                        WatchNotificationMode.OFF -> false
                        WatchNotificationMode.PERIODIC_ONLY -> periodicTrigger
                        WatchNotificationMode.SCHEDULES_ONLY -> scheduleTrigger
                        WatchNotificationMode.PERIODIC_AND_SCHEDULES -> {
                            if (enabledSchedules.isEmpty()) periodicTrigger else periodicTrigger || scheduleTrigger
                        }
                    }

                    if (shouldTrigger) {
                        val processed = processMeasurement(measurement)
                        sendWatchAlertNotification(processed)
                        lastWatchAlertEpochMinute = epochMinute
                    }
                }

                private fun isTriggerMinute(nowLocal: java.time.ZonedDateTime, interval: Int, startMinute: Int): Boolean {
                    val minuteOfDay = (nowLocal.hour * 60) + nowLocal.minute
                    val offsetFromStart = (minuteOfDay - startMinute).mod(24 * 60)
                    return offsetFromStart % interval.coerceAtLeast(1) == 0
                }

                private fun sendWatchAlertNotification(measurement: GlucoseMeasurement) {
                    val dualValue = GlucoseProcessor.formatDualValue(measurement.value, measurement.calibratedValue)
                    val plainTitle = buildWatchPlainTitle(measurement)
                    val styledTitle = buildWatchStyledTitle(plainTitle, dualValue)
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                    val watchNotificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                    val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                        .setContentTitle(styledTitle)
                        .setContentText(plainTitle)
                        .setSmallIcon(android.R.drawable.stat_notify_sync)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .setBigContentTitle(styledTitle)
                                .bigText(styledTitle)
                        )
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setTimeoutAfter(WATCH_ALERT_TIMEOUT_MS)
                        .build()

                    notificationManager.notify(watchNotificationId, notification)
                }

                private fun maybeSendGlucoseAlarms(measurement: GlucoseMeasurement) {
                    if (!isWithinAnyActiveSchedule(alarmSchedulesCached)) return
                    
                    val now = System.currentTimeMillis()
                    val processed = processMeasurement(measurement)
                    val valueToCheck = if (useCalibratedForAlarmsCached) processed.calibratedValue else processed.value

                    if (lowGlucoseAlarmEnabledCached && valueToCheck < LOW_GLUCOSE_THRESHOLD) {
                        if (now - lastLowAlarmAtMillis >= GLUCOSE_ALARM_COOLDOWN_MS) {
                            sendThresholdAlarmNotification(processed, isLow = true)
                            lastLowAlarmAtMillis = now
                        }
                    }

                    if (highGlucoseAlarmEnabledCached && valueToCheck > HIGH_GLUCOSE_THRESHOLD) {
                        if (now - lastHighAlarmAtMillis >= GLUCOSE_ALARM_COOLDOWN_MS) {
                            sendThresholdAlarmNotification(processed, isLow = false)
                            lastHighAlarmAtMillis = now
                        }
                    }
                }

                private fun sendThresholdAlarmNotification(measurement: GlucoseMeasurement, isLow: Boolean) {
                    val plainTitle = buildWatchPlainTitle(measurement)
                    val dualValue = GlucoseProcessor.formatDualValue(measurement.value, measurement.calibratedValue)
                    val styledTitle = buildWatchStyledTitle(plainTitle, dualValue)
                    val alarmText = if (isLow) {
                        "Low glucose alarm"
                    } else {
                        "High glucose alarm"
                    }
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                    val alarmNotificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                    val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                        .setContentTitle(styledTitle)
                        .setContentText(alarmText)
                        .setSmallIcon(android.R.drawable.stat_notify_sync)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .setBigContentTitle(styledTitle)
                                .bigText("$alarmText\n$plainTitle")
                        )
                        .setVibrate(longArrayOf(0, 500, 200, 500))
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .setTimeoutAfter(TEST_ALERT_TIMEOUT_MS)
                        .build()

                    notificationManager.notify(alarmNotificationId, notification)
                }

                private fun buildWatchPlainTitle(measurement: GlucoseMeasurement): String {
                    val trendStr = GlucoseProcessor.getTrendArrowSymbol(measurement.trendArrow)
                    val dualValue = GlucoseProcessor.formatDualValue(measurement.value, measurement.calibratedValue)
                    return "$dualValue mg/dL  $trendStr"
                }

                private fun buildWatchStyledTitle(title: String, dualValue: String): CharSequence {
                    return SpannableString(title).apply {
                        setSpan(
                            RelativeSizeSpan(1.8f),
                            0,
                            title.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        setSpan(
                            RelativeSizeSpan(2.0f),
                            0,
                            dualValue.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        setSpan(
                            StyleSpan(Typeface.BOLD),
                            0,
                            dualValue.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }

    private fun isWithinAnyActiveSchedule(schedules: List<com.tonio.libre2clock.data.model.AlarmSchedule>): Boolean {
        if (schedules.isEmpty()) return true
        val enabledSchedules = schedules.filter { it.isEnabled }
        if (enabledSchedules.isEmpty()) return false

        val now = java.time.ZonedDateTime.now(ZoneId.systemDefault())
        return enabledSchedules.any { isCurrentTimeInSchedule(it, now) }
    }

    private fun isCurrentTimeInSchedule(schedule: com.tonio.libre2clock.data.model.AlarmSchedule, now: java.time.ZonedDateTime): Boolean {
        val dayOfWeek = now.dayOfWeek.value // 1 to 7
        if (dayOfWeek !in schedule.daysOfWeek) return false

        val currentTime = now.toLocalTime()
        val start = java.time.LocalTime.parse(schedule.startTime)
        val end = java.time.LocalTime.parse(schedule.endTime)

        return if (start.isBefore(end)) {
            currentTime >= start && currentTime < end
        } else {
            // Spans midnight (e.g. 22:00 to 06:00)
            currentTime >= start || currentTime < end
        }
    }
}
