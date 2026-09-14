package com.tonio.libre2clock.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale


object TimestampParser {

    // Regex precompilada para evitar recompilación en cada llamada
    private val msAjaxDateRegex = Regex("^/Date\\((-?\\d+)(?:[+-]\\d{4})?\\)/$")

    // Formatters construidos con secciones opcionales para reducir el número total
    private val isoDateTimeFormat: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd")
        .appendOptional(DateTimeFormatter.ofPattern("['T'][ ]HH:mm[:ss][.SSS]", Locale.US))
        .toFormatter(Locale.US)

    private val isoDateTimeWithZoneFormat: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd['T']HH:mm[:ss][.SSS]")
        .appendOffsetId()
        .toFormatter(Locale.US)

    private val usDateFormat: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("[M][MM]/[d][dd]/yyyy")
        .appendPattern(" [H][h]:mm[:ss][.SSS]")
        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
        .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
        .appendOptional(DateTimeFormatter.ofPattern(" a", Locale.US))
        .toFormatter(Locale.US)

    private val localTimeFormat: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("[H][HH]:mm")
        .toFormatter(Locale.US)

    private val localDateTimeFormats = listOf(
        isoDateTimeFormat,
        isoDateTimeWithZoneFormat,
        usDateFormat
    )

    fun parseFlexibleInstant(timestamp: String, zoneId: ZoneId = ZoneId.systemDefault()): Instant? {
        val raw = timestamp.trim()
        if (raw.isEmpty()) return null

        // Intentar formatos numéricos primero (más rápido)
        parseEpochLike(raw)?.let { return it }

        // Intentos rápidos de formatos ISO estándar
        tryParseInstant(raw)?.let { return it }
        tryParseOffsetDateTime(raw)?.let { return it }
        tryParseZonedDateTime(raw)?.let { return it }

        // Intentar formatos locales personalizados
        localDateTimeFormats.forEach { formatter ->
            tryParseLocalDateTime(raw, formatter)?.let { 
                return it.atZone(zoneId).toInstant() 
            }
        }

        // Intentar formatos de solo hora
        tryParseLocalTime(raw)?.let {
            return it.atDate(LocalDate.now(zoneId)).atZone(zoneId).toInstant()
        }

        return null
    }

    // Funciones de parseo específicas que solo capturan DateTimeParseException
    private fun tryParseInstant(raw: String): Instant? {
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun tryParseOffsetDateTime(raw: String): Instant? {
        return try {
            OffsetDateTime.parse(raw).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun tryParseZonedDateTime(raw: String): Instant? {
        return try {
            ZonedDateTime.parse(raw).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun tryParseLocalDateTime(raw: String, formatter: DateTimeFormatter): LocalDateTime? {
        return try {
            LocalDateTime.parse(raw, formatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun tryParseLocalTime(raw: String): LocalTime? {
        return try {
            LocalTime.parse(raw, localTimeFormat)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseEpochLike(raw: String): Instant? {
        // Verificación rápida para epoch numérico puro
        if (raw.all { it.isDigit() }) {
            return raw.toLongOrNull()?.let(::instantFromEpochNumber)
        }

        // Formato Microsoft AJAX Date: /Date(1234567890000)/
        return msAjaxDateRegex.find(raw)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?.let(::instantFromEpochNumber)
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