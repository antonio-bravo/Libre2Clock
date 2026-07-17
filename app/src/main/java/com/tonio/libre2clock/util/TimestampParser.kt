package com.tonio.libre2clock.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object TimestampParser {

    private val localDateTimeFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("M/d/yyyy H:mm"),
        DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss"),
        DateTimeFormatter.ofPattern("M/d/yyyy h:mm a"),
        DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a")
    )

    private val localTimeFormats = listOf(
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("H:mm")
    )

    fun parseFlexibleInstant(timestamp: String, zoneId: ZoneId = ZoneId.systemDefault()): Instant? {
        val raw = timestamp.trim()
        if (raw.isEmpty()) return null

        parseEpochLike(raw)?.let { return it }

        runCatching { Instant.parse(raw) }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()?.let { return it }
        runCatching { ZonedDateTime.parse(raw).toInstant() }.getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(raw).atZone(zoneId).toInstant() }.getOrNull()?.let { return it }

        localDateTimeFormats.forEach { formatter ->
            runCatching { LocalDateTime.parse(raw, formatter).atZone(zoneId).toInstant() }
                .getOrNull()
                ?.let { return it }
        }

        localTimeFormats.forEach { formatter ->
            runCatching { LocalTime.parse(raw, formatter).atDate(LocalDate.now()).atZone(zoneId).toInstant() }
                .getOrNull()
                ?.let { return it }
        }

        return null
    }

    private fun parseEpochLike(raw: String): Instant? {
        if (raw.all { it.isDigit() }) {
            return raw.toLongOrNull()?.let(::instantFromEpochNumber)
        }

        val dateMatch = Regex("^/Date\\(([-]?\\d+)(?:[+-]\\d{4})?\\)/$").find(raw)
            ?: return null
        val value = dateMatch.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
        return instantFromEpochNumber(value)
    }

    private fun instantFromEpochNumber(value: Long): Instant {
        val absValue = kotlin.math.abs(value)
        return if (absValue < 1_000_000_000_000L) {
            Instant.ofEpochSecond(value)
        } else {
            Instant.ofEpochMilli(value)
        }
    }
}
