package com.tonio.libre2clock

import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.util.TimestampParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TimestampPriorityTest {

    @Test
    fun factoryTimestampIsPrioritizedOverTimestamp() {
        // FactoryTimestamp represents 12:00 UTC
        val factoryTs = "2024-05-20T12:00:00Z"
        // Timestamp represents 13:00 (ambiguous, usually parsed as local)
        val plainTs = "2024-05-20 13:00:00"
        
        val measurement = GlucoseMeasurement(
            factoryTimestamp = factoryTs,
            timestamp = plainTs,
            value = 100
        )

        // The unified logic (now implemented in various places or manually tested here via Parser)
        // should ensure that FactoryTimestamp is used if present.
        val parsedInstant = TimestampParser.parseFlexibleInstant(measurement.factoryTimestamp)
            ?: TimestampParser.parseFlexibleInstant(measurement.timestamp)

        val expectedInstant = Instant.parse(factoryTs)
        assertEquals("Should prioritize FactoryTimestamp (UTC)", expectedInstant, parsedInstant)
    }

    @Test
    fun fallbackToTimestampIfFactoryIsEmpty() {
        val plainTs = "2024-05-20T13:00:00Z" // Use Z to make it unambiguous for test
        val measurement = GlucoseMeasurement(
            factoryTimestamp = "",
            timestamp = plainTs,
            value = 100
        )

        val parsedInstant = TimestampParser.parseFlexibleInstant(measurement.factoryTimestamp)
            ?: TimestampParser.parseFlexibleInstant(measurement.timestamp)

        val expectedInstant = Instant.parse(plainTs)
        assertEquals("Should fallback to Timestamp if Factory is empty", expectedInstant, parsedInstant)
    }
}
