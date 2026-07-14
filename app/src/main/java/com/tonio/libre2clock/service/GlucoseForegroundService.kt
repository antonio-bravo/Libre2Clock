package com.tonio.libre2clock.service

import android.app.*
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import com.tonio.libre2clock.MainActivity
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.repository.GlucoseRepository
import com.tonio.libre2clock.data.repository.GlucoseRepositoryImpl
import com.tonio.libre2clock.data.repository.PreferenceManager
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest

class GlucoseForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: GlucoseRepository
    private lateinit var preferenceManager: PreferenceManager
    private var syncJob: Job? = null
    private var lastWatchAlertAtMillis: Long = 0L
    private var watchAlertsEnabledCached: Boolean = false
    private var watchAlertIntervalMinutesCached: Int = 60
    private var lastForegroundNotificationContent: String? = null

    companion object {
        const val CHANNEL_ID = "glucose_monitoring_channel"
        const val ALERT_CHANNEL_ID = "glucose_alerts_v2"
        const val NOTIFICATION_ID = 1
        const val TEST_ALERT_TIMEOUT_MS = 15 * 60 * 1000L
        const val WATCH_ALERT_TIMEOUT_MS = 10 * 60 * 1000L
    }

    override fun onCreate() {
        super.onCreate()
        preferenceManager = PreferenceManager(applicationContext)
        repository = GlucoseRepositoryImpl(preferenceManager)
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
            (repository as? GlucoseRepositoryImpl)?.initialize()
            
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
                preferenceManager.watchAlertIntervalMinutes.collect {
                    watchAlertIntervalMinutesCached = it.coerceIn(5, 180)
                }
            }

            while (isActive) {
                val fetchResult = repository.fetchLatestGlucose()
                val measurement = fetchResult.getOrNull()
                if (measurement != null) {
                    maybeSendWatchAlert(measurement)
                }
                delay(60000) // Sync every minute
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
        val trendStr = GlucoseProcessor.getTrendArrowSymbol(measurement.trendArrow)
        val dualValue = GlucoseProcessor.formatDualValue(measurement.value, measurement.calibratedValue)
        val content = "$dualValue mg/dL $trendStr"
        if (content == lastForegroundNotificationContent) return
        lastForegroundNotificationContent = content
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun triggerTestNotification() {
        val title = "145 mg/dL  ↗"
        val content = "Test alert for watch mirroring"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        // Unique ID so each test is treated as a new notification event.
        val testNotificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_HIGH) 
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText("Glucose test notification\n$title")
            )
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(TEST_ALERT_TIMEOUT_MS)
            .build()
            
        notificationManager.notify(testNotificationId, notification)
    }

                private suspend fun maybeSendWatchAlert(measurement: GlucoseMeasurement) {
                    if (!watchAlertsEnabledCached) return

                    val intervalMillis = watchAlertIntervalMinutesCached * 60_000L
                    val now = System.currentTimeMillis()

                    if (now - lastWatchAlertAtMillis < intervalMillis) return
                    sendWatchAlertNotification(measurement)
                    lastWatchAlertAtMillis = now
                }

                private fun sendWatchAlertNotification(measurement: GlucoseMeasurement) {
                    val trendStr = GlucoseProcessor.getTrendArrowSymbol(measurement.trendArrow)
                    val dualValue = GlucoseProcessor.formatDualValue(measurement.value, measurement.calibratedValue)
                    val plainTitle = "$dualValue mg/dL  $trendStr"
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
}
