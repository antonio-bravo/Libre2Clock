package com.tonio.libre2clock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tonio.libre2clock.MainActivity
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.AlarmSchedule
import com.tonio.libre2clock.data.model.AutoRangeOffsetMode
import com.tonio.libre2clock.data.model.CapillaryMeasurement
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.GlucoseOffsetRange
import com.tonio.libre2clock.data.model.WatchNotificationMode
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.GlucoseRepositoryImpl
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class GlucoseForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: GlucoseRepository
    private lateinit var repositoryImpl: GlucoseRepositoryImpl
    private lateinit var preferenceManager: PreferenceManager
    
    private var syncJob: Job? = null
    private var lastWatchAlertEpochMinute: Long = -1L
    private var lastLowAlarmAtMillis: Long = 0L
    private var lastHighAlarmAtMillis: Long = 0L
    private var lastForegroundNotificationContent: String? = null

    // --- OPTIMIZACIÓN 1: Estado consolidado en una sola data class ---
    private data class ParsedSchedule(
        val original: AlarmSchedule,
        val start: LocalTime,
        val end: LocalTime
    )

    private data class ServiceConfig(
        val watchAlertsEnabled: Boolean,
        val watchNotificationMode: WatchNotificationMode,
        val watchAlertIntervalMinutes: Int,
        val watchAlertStartMinute: Int,
        val lowGlucoseAlarmEnabled: Boolean,
        val highGlucoseAlarmEnabled: Boolean,
        val useCalibratedForAlarms: Boolean,
        val parsedWatchSchedules: List<ParsedSchedule>,
        val parsedAlarmSchedules: List<ParsedSchedule>,
        val batteryLowThreshold: Int,
        val batteryCriticalThreshold: Int,
        val disableFastOnSlowCharge: Boolean,
        val glucoseOffset: Int,
        val glucoseOffsetRanges: List<GlucoseOffsetRange>,
        val autoAdjustEnabled: Boolean,
        val autoRangeOffsetMode: AutoRangeOffsetMode,
        val capillaryReadings: List<CapillaryMeasurement>
    ) {
        val hasActiveAlerts: Boolean
            get() = watchNotificationMode != WatchNotificationMode.OFF || 
                    lowGlucoseAlarmEnabled || highGlucoseAlarmEnabled || 
                    parsedWatchSchedules.isNotEmpty() || parsedAlarmSchedules.isNotEmpty()
    }

    private lateinit var configState: StateFlow<ServiceConfig>

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
        initializeConfigState()
    }

    private fun initializeConfigState() {
        // 1. Alert Config: Anidamos combines para mantener la seguridad de tipos (Type Safety)
        // y evitar el warning de "Unchecked cast" con Array<Any?>
        val alertConfigFlow = combine(
            combine(
                preferenceManager.watchAlertsEnabled,
                preferenceManager.watchNotificationMode,
                preferenceManager.watchAlertIntervalMinutes,
                preferenceManager.watchAlertStartMinute
            ) { enabled, mode, interval, startMinute ->
                AlertConfigPart1(enabled, mode, interval, startMinute)
            },
            combine(
                preferenceManager.lowGlucoseAlarmEnabled,
                preferenceManager.highGlucoseAlarmEnabled,
                preferenceManager.useCalibratedForAlarms
            ) { low, high, cal ->
                AlertConfigPart2(low, high, cal)
            }
        ) { part1, part2 ->
            AlertConfig(
                enabled = part1.enabled,
                mode = part1.mode,
                interval = part1.interval,
                startMinute = part1.startMinute,
                lowEnabled = part2.low,
                highEnabled = part2.high,
                useCalibrated = part2.cal
            )
        }

        // 2. Schedule Config (2 flujos: ya es type-safe)
        val scheduleConfigFlow = combine(
            preferenceManager.watchNotificationSchedules,
            preferenceManager.glucoseAlarmSchedules
        ) { watch, alarm -> 
            ScheduleConfig(parseSchedules(watch), parseSchedules(alarm)) 
        }

        // 3. Battery Config (3 flujos: ya es type-safe)
        val batteryConfigFlow = combine(
            preferenceManager.batteryLowThreshold,
            preferenceManager.batteryCriticalThreshold,
            preferenceManager.disableFastRefreshOnSlowCharge
        ) { low, crit, disable -> 
            BatteryConfig(low, crit, disable) 
        }

        // 4. Glucose Config: Usamos los parámetros tipados directamente (hasta 5 flujos es 100% seguro)
        val glucoseConfigFlow = combine(
            preferenceManager.glucoseOffset,
            preferenceManager.glucoseOffsetRanges,
            preferenceManager.autoAdjustEnabled,
            preferenceManager.autoRangeOffsetMode,
            preferenceManager.capillaryReadings
        ) { offset, ranges, autoAdjust, autoMode, capillaries -> 
            GlucoseConfig(
                offset = offset,
                ranges = ranges,
                autoAdjust = autoAdjust,
                autoMode = autoMode,
                capillaries = capillaries
            ) 
        }

        // 5. Estado final combinado
        configState = combine(
            alertConfigFlow, scheduleConfigFlow, batteryConfigFlow, glucoseConfigFlow
        ) { alert, schedule, battery, glucose ->
            ServiceConfig(
                watchAlertsEnabled = alert.enabled,
                watchNotificationMode = alert.mode,
                watchAlertIntervalMinutes = alert.interval.coerceIn(5, 180),
                watchAlertStartMinute = alert.startMinute.coerceIn(0, 59),
                lowGlucoseAlarmEnabled = alert.lowEnabled,
                highGlucoseAlarmEnabled = alert.highEnabled,
                useCalibratedForAlarms = alert.useCalibrated,
                parsedWatchSchedules = schedule.watch,
                parsedAlarmSchedules = schedule.alarm,
                batteryLowThreshold = battery.low,
                batteryCriticalThreshold = battery.critical,
                disableFastOnSlowCharge = battery.disableFast,
                glucoseOffset = glucose.offset,
                glucoseOffsetRanges = glucose.ranges,
                autoAdjustEnabled = glucose.autoAdjust,
                autoRangeOffsetMode = glucose.autoMode,
                capillaryReadings = glucose.capillaries
            )
        }.stateIn(serviceScope, SharingStarted.Eagerly, ServiceConfig(
            watchAlertsEnabled = false, WatchNotificationMode.OFF, 60, 0,
            false, false, true, emptyList(), emptyList(), 15, 5, true,
            0, emptyList(), false, AutoRangeOffsetMode.OFF, emptyList()
        ))
    }

    // --- OPTIMIZACIÓN 3: Parsing de horarios una sola vez ---
    private fun parseSchedules(schedules: List<AlarmSchedule>): List<ParsedSchedule> {
        return schedules.filter { it.isEnabled }.mapNotNull { schedule ->
            try {
                ParsedSchedule(
                    original = schedule,
                    start = LocalTime.parse(schedule.startTime),
                    end = LocalTime.parse(schedule.endTime)
                )
            } catch (e: Exception) {
                null // Ignorar horarios con formato inválido
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "TEST_NOTIFICATION") {
            triggerTestNotification()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Starting glucose monitoring..."))
        
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            repositoryImpl.initialize()
            
            // Tarea A: Actualizar notificación en tiempo real cuando la BD local cambia
            launch {
                repository.currentGlucose.collect { measurement ->
                    measurement?.let {
                        val config = configState.value
                        val processed = processMeasurement(it, config)
                        updateNotification(processed)
                    }
                }
            }

            // Tarea B: Bucle de sondeo (Polling) para obtener nuevos datos y evaluar alarmas
            launch {
                var firstPoll = true
                while (isActive) {
                    val config = configState.value
                    val fetchResult = repository.fetchLatestGlucose()
                    val measurement = fetchResult.getOrNull()
                    
                    if (measurement != null) {
                        val processed = processMeasurement(measurement, config)
                        maybeSendWatchAlert(processed, config)
                        maybeSendGlucoseAlarms(processed, config)
                    }

                    val batteryState = getDetailedBatteryStatus(config)
                    val pollingIntervalMs = calculatePollingInterval(firstPoll, batteryState, config)
                    
                    firstPoll = false
                    delay(pollingIntervalMs)
                }
            }
        }

        return START_STICKY
    }

    private fun calculatePollingInterval(firstPoll: Boolean, batteryState: BatteryState, config: ServiceConfig): Long {
        return when {
            firstPoll -> 60_000L
            batteryState == BatteryState.CRITICAL -> 900_000L // 15 min obligatorio
            batteryState == BatteryState.LOW && config.hasActiveAlerts -> 300_000L // 5 min máximo
            batteryState == BatteryState.LOW -> 900_000L // 15 min
            batteryState == BatteryState.SLOW_CHARGING && config.disableFastOnSlowCharge -> 300_000L // 5 min para cargar
            config.hasActiveAlerts -> 60_000L // Refresco rápido normal
            else -> 300_000L // Línea base normal
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            val channel = NotificationChannel(CHANNEL_ID, "Glucose Monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows current glucose readings"
            }
            notificationManager.createNotificationChannel(channel)

            val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "Glucose Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for glucose tests and alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Libre2Clock")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(measurement: GlucoseMeasurement) {
        val trendStr = GlucoseProcessor.getTrendArrowSymbol(measurement.trendArrow)
        val dualValue = GlucoseProcessor.formatDualValue(measurement.value, measurement.calibratedValue)
        val content = "$dualValue mg/dL $trendStr"
        
        if (content == lastForegroundNotificationContent) return
        
        lastForegroundNotificationContent = content
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    // --- OPTIMIZACIÓN 2: Receptor de batería compatible con Android 14+ ---
    private fun getDetailedBatteryStatus(config: ServiceConfig): BatteryState {
        val batteryIntent = ContextCompat.registerReceiver(
            this,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        ) ?: return BatteryState.NORMAL
        
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugType = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

        if (level < 0 || scale <= 0) return BatteryState.NORMAL
        val batteryPercent = (level * 100) / scale

        return when {
            batteryPercent <= config.batteryCriticalThreshold -> BatteryState.CRITICAL
            batteryPercent <= config.batteryLowThreshold -> BatteryState.LOW
            status == BatteryManager.BATTERY_STATUS_CHARGING && plugType == BatteryManager.BATTERY_PLUGGED_USB -> BatteryState.SLOW_CHARGING
            else -> BatteryState.NORMAL
        }
    }

    private enum class BatteryState { NORMAL, LOW, CRITICAL, SLOW_CHARGING }

    private fun processMeasurement(measurement: GlucoseMeasurement, config: ServiceConfig): GlucoseMeasurement {
        return GlucoseProcessor.process(
            measurement = measurement,
            manualOffset = config.glucoseOffset,
            userRanges = config.glucoseOffsetRanges,
            autoAdjustEnabled = config.autoAdjustEnabled,
            autoRangeOffsetMode = config.autoRangeOffsetMode,
            capillaryReadings = config.capillaryReadings
        )
    }

    private fun triggerTestNotification() {
        serviceScope.launch {
            repositoryImpl.initialize()
            val fetchResult = repository.fetchLatestGlucose()
            val measurement = fetchResult.getOrNull()
                ?: repository.currentGlucose.first()
                ?: repository.historicalGlucose.first().firstOrNull()
            
            val fetchErrorMessage = fetchResult.exceptionOrNull()?.message?.trim()?.takeIf { it.isNotEmpty() }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val testNotificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

            val notification = if (measurement != null) {
                val config = configState.value
                val processed = processMeasurement(measurement, config)
                val plainTitle = buildWatchPlainTitle(processed)
                val dualValue = GlucoseProcessor.formatDualValue(processed.value, processed.calibratedValue)
                val styledTitle = buildWatchStyledTitle(plainTitle, dualValue)

                NotificationCompat.Builder(this@GlucoseForegroundService, ALERT_CHANNEL_ID)
                    .setContentTitle(styledTitle)
                    .setContentText(plainTitle)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(styledTitle).bigText(styledTitle))
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

    private suspend fun maybeSendWatchAlert(measurement: GlucoseMeasurement, config: ServiceConfig) {
        if (!config.watchAlertsEnabled || config.watchNotificationMode == WatchNotificationMode.OFF) return
        
        val nowMillis = System.currentTimeMillis()
        val epochMinute = nowMillis / 60_000L
        if (epochMinute == lastWatchAlertEpochMinute) return
        
        val nowLocal = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())

        val periodicTrigger = isTriggerMinute(nowLocal, config.watchAlertIntervalMinutes, config.watchAlertStartMinute)

        val scheduleTrigger = config.parsedWatchSchedules.any { schedule ->
            isCurrentTimeInSchedule(schedule, nowLocal) && 
            isTriggerMinute(nowLocal, schedule.original.intervalMinutes ?: config.watchAlertIntervalMinutes, schedule.original.startMinute ?: config.watchAlertStartMinute)
        }
        
        val shouldTrigger = when (config.watchNotificationMode) {
            WatchNotificationMode.OFF -> false
            WatchNotificationMode.PERIODIC_ONLY -> periodicTrigger
            WatchNotificationMode.SCHEDULES_ONLY -> scheduleTrigger
            WatchNotificationMode.PERIODIC_AND_SCHEDULES -> {
                if (config.parsedWatchSchedules.isEmpty()) periodicTrigger else periodicTrigger || scheduleTrigger
            }
        }

        if (shouldTrigger) {
            sendWatchAlertNotification(measurement)
            lastWatchAlertEpochMinute = epochMinute
        }
    }

    private fun isTriggerMinute(nowLocal: ZonedDateTime, interval: Int, startMinute: Int): Boolean {
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
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(styledTitle).bigText(styledTitle))
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(WATCH_ALERT_TIMEOUT_MS)
            .build()

        notificationManager.notify(watchNotificationId, notification)
    }

    private fun maybeSendGlucoseAlarms(measurement: GlucoseMeasurement, config: ServiceConfig) {
        if (!isWithinAnyActiveSchedule(config.parsedAlarmSchedules)) return
        
        val now = System.currentTimeMillis()
        val valueToCheck = if (config.useCalibratedForAlarms) measurement.calibratedValue else measurement.value

        if (config.lowGlucoseAlarmEnabled && valueToCheck < LOW_GLUCOSE_THRESHOLD) {
            if (now - lastLowAlarmAtMillis >= GLUCOSE_ALARM_COOLDOWN_MS) {
                sendThresholdAlarmNotification(measurement, isLow = true)
                lastLowAlarmAtMillis = now
            }
        }

        if (config.highGlucoseAlarmEnabled && valueToCheck > HIGH_GLUCOSE_THRESHOLD) {
            if (now - lastHighAlarmAtMillis >= GLUCOSE_ALARM_COOLDOWN_MS) {
                sendThresholdAlarmNotification(measurement, isLow = false)
                lastHighAlarmAtMillis = now
            }
        }
    }

    private fun sendThresholdAlarmNotification(measurement: GlucoseMeasurement, isLow: Boolean) {
        val plainTitle = buildWatchPlainTitle(measurement)
        val dualValue = GlucoseProcessor.formatDualValue(measurement.value, measurement.calibratedValue)
        val styledTitle = buildWatchStyledTitle(plainTitle, dualValue)
        val alarmText = if (isLow) "Low glucose alarm" else "High glucose alarm"
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
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(styledTitle).bigText("$alarmText\n$plainTitle"))
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
            setSpan(RelativeSizeSpan(1.8f), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(2.0f), 0, dualValue.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, dualValue.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun isWithinAnyActiveSchedule(schedules: List<ParsedSchedule>): Boolean {
        if (schedules.isEmpty()) return true
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        return schedules.any { isCurrentTimeInSchedule(it, now) }
    }

    private fun isCurrentTimeInSchedule(schedule: ParsedSchedule, now: ZonedDateTime): Boolean {
        val dayOfWeek = now.dayOfWeek.value // 1 to 7
        if (dayOfWeek !in schedule.original.daysOfWeek) return false

        val currentTime = now.toLocalTime()
        return if (schedule.start.isBefore(schedule.end)) {
            currentTime >= schedule.start && currentTime < schedule.end
        } else {
            // Spans midnight (e.g. 22:00 to 06:00)
            currentTime >= schedule.start || currentTime < schedule.end
        }
    }

    // Clases auxiliares para el combine anidado (mejora la legibilidad y evita casts de Array<Any>)
    private data class AlertConfigPart1(val enabled: Boolean, val mode: WatchNotificationMode, val interval: Int, val startMinute: Int)
    private data class AlertConfigPart2(val low: Boolean, val high: Boolean, val cal: Boolean)
    
    private data class AlertConfig(val enabled: Boolean, val mode: WatchNotificationMode, val interval: Int, val startMinute: Int, val lowEnabled: Boolean, val highEnabled: Boolean, val useCalibrated: Boolean)
    private data class ScheduleConfig(val watch: List<ParsedSchedule>, val alarm: List<ParsedSchedule>)
    private data class BatteryConfig(val low: Int, val critical: Int, val disableFast: Boolean)
    private data class GlucoseConfig(val offset: Int, val ranges: List<GlucoseOffsetRange>, val autoAdjust: Boolean, val autoMode: AutoRangeOffsetMode, val capillaries: List<CapillaryMeasurement>)}