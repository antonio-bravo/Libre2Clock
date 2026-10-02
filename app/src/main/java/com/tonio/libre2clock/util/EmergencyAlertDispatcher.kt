package com.tonio.libre2clock.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tonio.libre2clock.MainActivity
import com.tonio.libre2clock.R
import com.tonio.libre2clock.data.model.EmergencyConfig
import com.tonio.libre2clock.data.model.EmergencyContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class EmergencyAlertDispatcher(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        const val EMERGENCY_CHANNEL_ID = "emergency_sos_channel"
        const val EMERGENCY_NOTIFICATION_ID = 888
    }

    init {
        createEmergencyNotificationChannel()
    }

    private fun createEmergencyNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.emergency_screen_title)
            val descriptionText = context.getString(R.string.emergency_enable_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(EMERGENCY_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun formatSosMessage(glucoseMgDl: Int, location: EmergencyLocation?): String {
        val now = LocalDateTime.now()
        val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

        val dateStr = now.format(dateFormatter)
        val timeStr = now.format(timeFormatter)

        val locStr = if (location != null) {
            context.getString(R.string.emergency_msg_body_location, location.mapUrl)
        } else {
            context.getString(R.string.emergency_msg_body_no_location)
        }

        return context.getString(R.string.emergency_msg_text, glucoseMgDl, timeStr, dateStr, locStr)
    }

    suspend fun dispatchSosAlert(
        glucoseMgDl: Int,
        location: EmergencyLocation?,
        config: EmergencyConfig
    ): Int = withContext(Dispatchers.IO) {
        if (config.contacts.isEmpty()) return@withContext 0

        val message = formatSosMessage(glucoseMgDl, location)
        var dispatchedCount = 0

        // 1. Enviar vía Telegram Bot API
        if (config.telegramBotToken.isNotBlank()) {
            val telegramContacts = config.contacts.filter { it.sendViaTelegram && it.telegramChatId.isNotBlank() }
            for (contact in telegramContacts) {
                val success = sendTelegramBotMessage(config.telegramBotToken, contact.telegramChatId, message)
                if (success) dispatchedCount++
            }
        }

        // 2. Enviar vía SMS directo si hay permiso
        if (hasSmsPermission()) {
            val smsContacts = config.contacts.filter { it.sendViaSms && it.phoneNumber.isNotBlank() }
            for (contact in smsContacts) {
                val success = sendSmsMessage(contact.phoneNumber, message)
                if (success) dispatchedCount++
            }
        }

        // 3. Mostrar Notificación de Máxima Prioridad para WhatsApp e Intents
        showEmergencyNotification(glucoseMgDl, message, config.contacts)

        return@withContext dispatchedCount
    }

    suspend fun sendTelegramBotMessage(botToken: String, chatId: String, message: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "https://api.telegram.org/bot${botToken.trim()}/sendMessage"
            val jsonBody = JSONObject().apply {
                put("chat_id", chatId.trim())
                put("text", message)
                put("disable_web_page_preview", false)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val isSuccess = response.isSuccessful
            response.close()
            isSuccess
        } catch (_: Exception) {
            false
        }
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun sendSmsMessage(phoneNumber: String, message: String): Boolean {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val cleanPhone = phoneNumber.replace("[^0-9+]".toRegex(), "")
            if (cleanPhone.isBlank()) return false

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanPhone, null, message, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun showEmergencyNotification(glucoseMgDl: Int, message: String, contacts: List<EmergencyContact>) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, EMERGENCY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⚠️ ALERTA SOS HIPOGLUCEMIA ($glucoseMgDl mg/dL)")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)

        // Añadir acciones de WhatsApp si hay contactos habilitados
        val whatsAppContacts = contacts.filter { it.sendViaWhatsApp && it.phoneNumber.isNotBlank() }
        for ((index, contact) in whatsAppContacts.take(2).withIndex()) {
            val cleanPhone = contact.phoneNumber.replace("[^0-9+]".toRegex(), "")
            val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
            val waIntent = Intent(Intent.ACTION_VIEW, waUri)
            val waPendingIntent = PendingIntent.getActivity(
                context,
                100 + index,
                waIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(
                R.mipmap.ic_launcher,
                "WhatsApp: ${contact.name}",
                waPendingIntent
            )
        }

        notificationManager.notify(EMERGENCY_NOTIFICATION_ID, builder.build())
    }

    fun createWhatsAppIntent(phoneNumber: String, message: String): Intent {
        val cleanPhone = phoneNumber.replace("[^0-9+]".toRegex(), "")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun createTelegramIntent(message: String): Intent {
        val uri = Uri.parse("https://t.me/share/url?url=${Uri.encode(message)}")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
