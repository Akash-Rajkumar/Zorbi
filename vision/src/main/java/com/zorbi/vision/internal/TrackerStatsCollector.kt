package com.zorbi.vision.internal

import com.zorbi.core.TrackerStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe collector for tracker performance statistics.
 * Computes:
 * - FPS: Sliding 1-second window of completed frames.
 * - Latency P50 and P95: Percentile latencies across a sliding window of recent inferences.
 * - Dropped frames: Count of rate-limited or dropped frames.
 */
class TrackerStatsCollector(
    private val maxLatencyWindow: Int = 50
) {
    private val _stats = MutableStateFlow(
        TrackerStats(fps = 0f, inferenceMsP50 = 0f, inferenceMsP95 = 0f, dropped = 0)
    )
    val stats: StateFlow<TrackerStats> = _stats.asStateFlow()

    private val droppedFrames = AtomicInteger(0)
    private val lock = Any()
    private val frameTimestampsNanos = ArrayDeque<Long>()
    private val latenciesMs = ArrayDeque<Float>()

    fun recordFrame(arrivalNanos: Long, latencyMs: Float) {
        synchronized(lock) {
            frameTimestampsNanos.addLast(arrivalNanos)
            trimOldTimestamps(arrivalNanos)

            if (latencyMs >= 0f) {
                latenciesMs.addLast(latencyMs)
                if (latenciesMs.size > maxLatencyWindow) {
                    latenciesMs.removeFirst()
                }
            }
            updateStatsLocked()
        }
    }

    fun recordDropped() {
        droppedFrames.incrementAndGet()
        synchronized(lock) {
            updateStatsLocked()
        }
    }

    private fun trimOldTimestamps(nowNanos: Long) {
        val oneSecondAgo = nowNanos - 1_000_000_000L
        while (frameTimestampsNanos.isNotEmpty() && frameTimestampsNanos.first() < oneSecondAgo) {
            frameTimestampsNanos.removeFirst()
        }
    }

    private fun updateStatsLocked() {
        val fps = frameTimestampsNanos.size.toFloat()
        var p50 = 0f
        var p95 = 0f

        if (latenciesMs.isNotEmpty()) {
            val sorted = latenciesMs.sorted()
            val size = sorted.size
            val p50Idx = Math.round((size - 1) * 0.50).toInt().coerceIn(0, size - 1)
            val p95Idx = Math.round((size - 1) * 0.95).toInt().coerceIn(0, size - 1)
            p50 = sorted[p50Idx]
            p95 = sorted[p95Idx]
        }

        _stats.value = TrackerStats(
            fps = fps,
            inferenceMsP50 = p50,
            inferenceMsP95 = p95,
            dropped = droppedFrames.get()
        )
    }

    fun reset() {
        droppedFrames.set(0)
        synchronized(lock) {
            frameTimestampsNanos.clear()
            latenciesMs.clear()
            _stats.value = TrackerStats(fps = 0f, inferenceMsP50 = 0f, inferenceMsP95 = 0f, dropped = 0)
        }
    }
}

