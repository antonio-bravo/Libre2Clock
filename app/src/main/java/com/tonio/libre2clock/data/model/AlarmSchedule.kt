package com.tonio.libre2clock.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AlarmSchedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startTime: String, // HH:mm
    val endTime: String,   // HH:mm
    val daysOfWeek: List<Int>, // 1 (Monday) to 7 (Sunday)
    val isEnabled: Boolean = true,
    val intervalMinutes: Int? = null,
    val startMinute: Int? = null
)
