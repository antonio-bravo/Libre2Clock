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

    private val eventLogger = com.tonio.libre2clock.di.AppContainer.provideEventLogManager(context)

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
        config: EmergencyConfig,
        launchWhatsAppDirectly: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        if (config.contacts.isEmpty()) {
            eventLogger.log(LogLevel.WARNING, "EmergencyDispatch", "Despacho cancelado: Lista de contactos está vacía.")
            return@withContext 0
        }

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
        val smsContacts = config.contacts.filter { it.sendViaSms && it.phoneNumber.isNotBlank() }
        if (smsContacts.isNotEmpty()) {
            if (hasSmsPermission()) {
                for (contact in smsContacts) {
                    val success = sendSmsMessage(contact.phoneNumber, message)
                    if (success) dispatchedCount++
                }
            } else {
                eventLogger.log(
                    LogLevel.ERROR,
                    "EmergencyDispatch",
                    "❌ SMS cancelado para ${smsContacts.size} contacto(s): Falta el permiso SEND_SMS en la aplicación."
                )
            }
        }

        // 3. WhatsApp (CallMeBot HTTP API automático de fondo o Intent directo local)
        val whatsAppContacts = config.contacts.filter { 
            it.sendViaWhatsApp && (it.phoneNumber.isNotBlank() || it.whatsAppGroupId.isNotBlank()) 
        }
        for (contact in whatsAppContacts) {
            val target = contact.whatsAppGroupId.ifBlank { contact.phoneNumber }
            if (contact.whatsAppApiKey.isNotBlank()) {
                val success = sendCallMeBotWhatsAppMessage(target, contact.whatsAppApiKey, message)
                if (success) dispatchedCount++
            } else {
                withContext(Dispatchers.Main) {
                    launchWhatsApp(target, message)
                }
                dispatchedCount++
            }
        }

        // 4. Mostrar Notificación de Máxima Prioridad con accesos directos a WhatsApp e Intents
        showEmergencyNotification(glucoseMgDl, message, config.contacts)

        // 5. Enviar vía Webhook Personalizado (WASender API, IFTTT, Make, servidor propio)
        if (config.customWebhookUrl.isNotBlank()) {
            val success = sendCustomWebhook(config.customWebhookUrl, glucoseMgDl, location, message, config.contacts)
            if (success) dispatchedCount++
        }

        return@withContext dispatchedCount
    }

    suspend fun sendCallMeBotWhatsAppMessage(phoneNumber: String, apiKey: String, message: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val phoneDigits = phoneNumber.replace("[^0-9]".toRegex(), "")
            if (phoneDigits.isBlank()) {
                eventLogger.log(LogLevel.WARNING, "EmergencyDispatch", "CallMeBot cancelado: Número de teléfono inválido ($phoneNumber)")
                return@withContext false
            }

            val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
            val url = "https://api.callmebot.com/whatsapp.php?phone=$phoneDigits&text=$encodedMessage&apikey=${apiKey.trim()}"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            val isSuccess = response.isSuccessful && !bodyStr.contains("ERROR:", ignoreCase = true)
            response.close()

            if (!isSuccess) {
                eventLogger.log(LogLevel.ERROR, "EmergencyDispatch", "❌ CallMeBot WhatsApp error para $phoneDigits: $bodyStr")
            } else {
                eventLogger.log(LogLevel.INFO, "EmergencyDispatch", "✅ CallMeBot WhatsApp enviado automáticamente a $phoneDigits")
            }

            isSuccess
        } catch (e: Exception) {
            eventLogger.log(LogLevel.ERROR, "EmergencyDispatch", "❌ CallMeBot WhatsApp excepción para $phoneNumber: ${e.message}")
            false
        }
    }

    suspend fun sendCustomWebhook(
        url: String,
        glucoseMgDl: Int,
        location: EmergencyLocation?,
        message: String,
        contacts: List<EmergencyContact> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val primaryGroupId = contacts.firstOrNull { it.whatsAppGroupId.isNotBlank() }?.whatsAppGroupId ?: ""
            val jsonBody = JSONObject().apply {
                put("glucose", glucoseMgDl)
                put("message", message)
                put("group_id", primaryGroupId)
                put("timestamp", System.currentTimeMillis())
                put("latitude", location?.latitude ?: 0.0)
                put("longitude", location?.longitude ?: 0.0)
                put("map_url", location?.mapUrl ?: "")
                put("contacts", org.json.JSONArray().apply {
                    contacts.forEach { c ->
                        put(JSONObject().apply {
                            put("name", c.name)
                            put("phone", c.phoneNumber)
                            put("telegram_chat_id", c.telegramChatId)
                            put("whatsapp_api_key", c.whatsAppApiKey)
                            put("whatsapp_group_id", c.whatsAppGroupId)
                        })
                    }
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url.trim())
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val isSuccess = response.isSuccessful
            response.close()

            if (!isSuccess) {
                eventLogger.log(LogLevel.ERROR, "EmergencyDispatch", "❌ Webhook error HTTP ${response.code} para $url")
            } else {
                eventLogger.log(LogLevel.INFO, "EmergencyDispatch", "✅ Webhook personalizado enviado a $url")
            }

            isSuccess
        } catch (e: Exception) {
            eventLogger.log(LogLevel.ERROR, "EmergencyDispatch", "❌ Webhook excepción para $url: ${e.message}")
            false
        }
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
            val responseBodyStr = response.body?.string()
            val isSuccess = response.isSuccessful
            response.close()

            if (!isSuccess) {
                eventLogger.log(LogLevel.ERROR, "EmergencyDispatch", "❌ Telegram error HTTP ${response.code} para Chat ID $chatId: $responseBodyStr")
            }

            isSuccess
        } catch (e: Exception) {
            eventLogger.log(LogLevel.ERROR, "EmergencyDispatch", "❌ Telegram excepción para Chat ID $chatId: ${e.message}")
            false
        }
    }

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    fun sendSmsMessage(phoneNumber: String, message: String): Boolean {
        if (!hasSmsPermission()) {
            eventLogger.log(LogLevel.ERROR, "EmergencyDispatch", "❌ SMS cancelado: Permiso SEND_SMS no concedido.")
            return false
        }

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val cleanPhone = phoneNumber.replace("[^0-9+]".toRegex(), "")
            if (cleanPhone.isBlank()) {
                eventLogger.log(LogLevel.WARNING, "EmergencyDispatch", "SMS cancelado: Número de teléfono inválido ($phoneNumber)")
                return false
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(cleanPhone, null, message, null, null)
            }
            eventLogger.log(LogLevel.INFO, "EmergencyDispatch", "✅ SMS enviado correctamente a $cleanPhone")
            true
        } catch (e: Exception) {
            eventLogger.log(LogLevel.ERROR, "EmergencyDispatch", "❌ SMS error para $phoneNumber: ${e.message}")
            false
        }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
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
        for ((index, contact) in whatsAppContacts.take(3).withIndex()) {
            val waIntent = createWhatsAppIntent(contact.phoneNumber, message)
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

    fun createWhatsAppIntent(target: String, message: String): Intent {
        val trimmedTarget = target.trim()
        val paquetesWhatsApp = listOf("com.whatsapp", "com.whatsapp.w4b", "com.whatsappdual")
        val availablePackage = paquetesWhatsApp.firstOrNull { isPackageInstalled(it) }

        if (trimmedTarget.contains("chat.whatsapp.com") || trimmedTarget.contains("http") || trimmedTarget.contains("@g.us")) {
            val jid = if (trimmedTarget.contains("@g.us")) trimmedTarget else "$trimmedTarget@g.us"
            return Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra("jid", jid)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                availablePackage?.let { setPackage(it) }
            }
        }

        val cleanDigits = trimmedTarget.replace("[^0-9]".toRegex(), "")
        val jid = if (cleanDigits.isNotBlank()) "$cleanDigits@s.whatsapp.net" else ""
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanDigits&text=${Uri.encode(message)}")
        return Intent(Intent.ACTION_SEND, uri).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            if (jid.isNotBlank()) putExtra("jid", jid)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            availablePackage?.let { setPackage(it) }
        }
    }

    fun launchWhatsApp(target: String, message: String): Boolean {
        val trimmedTarget = target.trim()
        if (trimmedTarget.isBlank()) return false

        val paquetesWhatsApp = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.whatsappdual"
        )

        // Si es un enlace HTTP de grupo o JID de grupo
        if (trimmedTarget.contains("chat.whatsapp.com") || trimmedTarget.contains("http") || trimmedTarget.contains("@g.us")) {
            val jid = if (trimmedTarget.contains("@g.us")) trimmedTarget else "$trimmedTarget@g.us"
            var enviado = false
            for (pkg in paquetesWhatsApp) {
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                        putExtra("jid", jid)
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    enviado = true
                    break
                } catch (_: Exception) {}
            }

            if (!enviado) {
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                        putExtra("jid", jid)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    enviado = true
                } catch (e: Exception) {
                    eventLogger.log(LogLevel.ERROR, "EmergencyDispatch", "❌ Error al abrir grupo: ${e.message}")
                }
            }
            return enviado
        }

        // Determinar número directo (Usuario)
        val cleanDigits = trimmedTarget.replace("[^0-9]".toRegex(), "")
        val jid = if (cleanDigits.isNotBlank()) "$cleanDigits@s.whatsapp.net" else ""
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanDigits&text=${Uri.encode(message)}")
        
        var enviado = false
        for (pkg in paquetesWhatsApp) {
            try {
                val intent = Intent(Intent.ACTION_SEND, uri).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    if (jid.isNotBlank()) putExtra("jid", jid)
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                enviado = true
                break
            } catch (_: Exception) {}
        }

        if (!enviado) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    if (jid.isNotBlank()) putExtra("jid", jid)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                enviado = true
            } catch (e: Exception) {
                eventLogger.log(LogLevel.ERROR, "EmergencyDispatch", "❌ Error al enviar mensaje: ${e.message}")
            }
        }

        return enviado
    }

    fun createTelegramIntent(message: String): Intent {
        val uri = Uri.parse("https://t.me/share/url?url=${Uri.encode(message)}")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
