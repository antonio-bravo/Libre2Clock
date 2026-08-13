package com.tonio.libre2clock.util

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

object SectionPerfTelemetry {

    private const val TAG = "SectionPerf"
    private const val SUMMARY_EVERY_N_CALLS = 20
    private const val SLOW_CALL_MS = 80L

    private val sectionStats = ConcurrentHashMap<String, Stats>()

    data class Snapshot(
        val section: String,
        val calls: Int,
        val cacheHits: Int,
        val cacheMisses: Int,
        val avgDurationMs: Double,
        val maxDurationMs: Long,
        val hitRatePercent: Double
    )

    fun record(section: String, durationMs: Long, cacheHit: Boolean) {
        val stats = sectionStats.getOrPut(section) { Stats() }

        stats.calls.incrementAndGet()
        stats.totalDurationMs.addAndGet(durationMs)
        stats.maxDurationMs.updateAndGet { current -> max(current, durationMs) }

        if (cacheHit) {
            stats.cacheHits.incrementAndGet()
        } else {
            stats.cacheMisses.incrementAndGet()
        }

        if (!cacheHit || durationMs >= SLOW_CALL_MS) {
            Log.d(TAG, "section=$section durationMs=$durationMs cacheHit=$cacheHit")
        }

        val calls = stats.calls.get()
        if (calls % SUMMARY_EVERY_N_CALLS == 0) {
            val total = stats.totalDurationMs.get()
            val avg = if (calls > 0) total.toDouble() / calls else 0.0
            Log.d(
                TAG,
                "summary section=$section calls=$calls avgMs=${"%.1f".format(avg)} maxMs=${stats.maxDurationMs.get()} hits=${stats.cacheHits.get()} misses=${stats.cacheMisses.get()}"
            )
        }
    }

    fun snapshot(): List<Snapshot> {
        return sectionStats.entries
            .map { (section, stats) ->
                val calls = stats.calls.get()
                val hits = stats.cacheHits.get()
                val misses = stats.cacheMisses.get()
                val total = stats.totalDurationMs.get()
                val avg = if (calls > 0) total.toDouble() / calls else 0.0
                val hitRate = if (calls > 0) (hits.toDouble() / calls) * 100.0 else 0.0
                Snapshot(
                    section = section,
                    calls = calls,
                    cacheHits = hits,
                    cacheMisses = misses,
                    avgDurationMs = avg,
                    maxDurationMs = stats.maxDurationMs.get(),
                    hitRatePercent = hitRate
                )
            }
            .sortedByDescending { it.calls }
    }

    fun reset() {
        sectionStats.clear()
    }

    private class Stats {
        val calls = AtomicInteger(0)
        val cacheHits = AtomicInteger(0)
        val cacheMisses = AtomicInteger(0)
        val totalDurationMs = AtomicLong(0)
        val maxDurationMs = AtomicLong(0)
    }
}
