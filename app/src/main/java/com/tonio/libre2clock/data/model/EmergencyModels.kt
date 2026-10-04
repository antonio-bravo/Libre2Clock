package com.tonio.libre2clock.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class EmergencyContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String = "",
    val telegramChatId: String = "",
    val whatsAppApiKey: String = "",
    val sendViaWhatsApp: Boolean = true,
    val sendViaTelegram: Boolean = true,
    val sendViaSms: Boolean = false
)

@Serializable
data class EmergencyConfig(
    val enabled: Boolean = false,
    val thresholdMgDl: Int = 60,
    val cooldownMinutes: Int = 15,
    val includeLocation: Boolean = true,
    val useCalibratedValue: Boolean = true,
    val useSchedule: Boolean = false,
    val startTime: String = "00:00",
    val endTime: String = "23:59",
    val telegramBotToken: String = "",
    val contacts: List<EmergencyContact> = emptyList()
)
