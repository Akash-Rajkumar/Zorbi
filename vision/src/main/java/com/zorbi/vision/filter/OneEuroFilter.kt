package com.zorbi.vision.filter

import com.zorbi.core.Pt
import kotlin.math.abs

/**
 * 1€ (One Euro) filter implementation for smoothing noisy landmark coordinates with minimal lag.
 *
 * Algorithm reference:
 * Casiez, G., Roussel, N., & Vogel, D. (2012).
 * 1 € Filter: A Simple Speed-based Low-pass Filter for Noisy Input with Lag Reduction.
 * In Proceedings of the SIGCHI Conference on Human Factors in Computing Systems (CHI '12), pp. 2527-2530.
 *
 * @param minCutoff Minimum cutoff frequency in Hz (fc_min > 0). Controls jitter reduction at low speeds.
 *                  Lower values produce stronger smoothing when stationary. Default = 1.0 Hz.
 * @param beta Speed coefficient (beta >= 0). Controls responsiveness during fast movements.
 *             Higher values decrease lag when moving fast. Default = 0.0.
 * @param dCutoff Cutoff frequency for the derivative filter in Hz (fc_d > 0). Default = 1.0 Hz.
 */
class OneEuroFilter(
    minCutoff: Float = DEFAULT_MIN_CUTOFF,
    beta: Float = DEFAULT_BETA,
    dCutoff: Float = DEFAULT_D_CUTOFF
) {
    companion object {
        const val DEFAULT_MIN_CUTOFF = 1.0f
        const val DEFAULT_BETA = 0.0f
        const val DEFAULT_D_CUTOFF = 1.0f

        /**
         * Calculates exponential smoothing factor alpha:
         * alpha = 1 / (1 + tau / dt) where tau = 1 / (2 * pi * cutoff)
         * Equivalent to: alpha = 2 * pi * cutoff * dt / (1 + 2 * pi * cutoff * dt)
         */
        fun computeAlpha(cutoffHz: Float, dtSeconds: Double): Float {
            if (cutoffHz <= 0f || dtSeconds <= 0.0) return 0f
            val tau = 1.0 / (2.0 * Math.PI * cutoffHz)
            val alpha = 1.0 / (1.0 + tau / dtSeconds)
            return alpha.toFloat().coerceIn(0f, 1f)
        }
    }

    var minCutoff: Float = minCutoff
        set(value) {
            require(value > 0f) { "minCutoff must be positive, got $value" }
            field = value
        }

    var beta: Float = beta
        set(value) {
            require(value >= 0f) { "beta must be non-negative, got $value" }
            field = value
        }

    var dCutoff: Float = dCutoff
        set(value) {
            require(value > 0f) { "dCutoff must be positive, got $value" }
            field = value
        }

    init {
        require(minCutoff > 0f) { "minCutoff must be positive, got $minCutoff" }
        require(beta >= 0f) { "beta must be non-negative, got $beta" }
        require(dCutoff > 0f) { "dCutoff must be positive, got $dCutoff" }
    }

    private var prevSmoothed: Float? = null
    private var prevFilteredDerivative: Float = 0f
    private var prevTimestampNanos: Long? = null
    private var prevTimestampSeconds: Double? = null

    /**
     * True if at least one sample has been processed since creation or the last [reset].
     */
    val isInitialized: Boolean
        get() = prevSmoothed != null

    /**
     * The most recent smoothed output, or null if no samples have been processed.
     */
    val lastFilteredValue: Float?
        get() = prevSmoothed

    /**
     * Filters a raw scalar sample given a nanosecond timestamp.
     *
     * @param value The raw input coordinate value.
     * @param timestampNanos Monotonic timestamp in nanoseconds (e.g. from TrackerFrame.tsNanos).
     * @return The smoothed coordinate value.
     */
    fun filter(value: Float, timestampNanos: Long): Float {
        val lastTs = prevTimestampNanos
        val lastVal = prevSmoothed

        // First sample: initialize state safely and return value directly (no artificial movement)
        if (lastTs == null || lastVal == null) {
            prevSmoothed = value
            prevFilteredDerivative = 0f
            prevTimestampNanos = timestampNanos
            prevTimestampSeconds = timestampNanos / 1_000_000_000.0
            return value
        }

        val dtNanos = timestampNanos - lastTs
        // Guard against zero or non-monotonic timestamps: return previous smoothed value
        if (dtNanos <= 0L) {
            return lastVal
        }

        val dtSeconds = dtNanos / 1_000_000_000.0
        prevTimestampNanos = timestampNanos
        prevTimestampSeconds = timestampNanos / 1_000_000_000.0
        return step(value, dtSeconds, lastVal)
    }

    /**
     * Filters a raw scalar sample given a timestamp in seconds.
     *
     * @param value The raw input coordinate value.
     * @param timestampSeconds Monotonic timestamp in seconds.
     * @return The smoothed coordinate value.
     */
    fun filterSeconds(value: Float, timestampSeconds: Double): Float {
        val lastSec = prevTimestampSeconds
        val lastVal = prevSmoothed

        if (lastSec == null || lastVal == null) {
            prevSmoothed = value
            prevFilteredDerivative = 0f
            prevTimestampSeconds = timestampSeconds
            prevTimestampNanos = (timestampSeconds * 1_000_000_000.0).toLong()
            return value
        }

        val dtSeconds = timestampSeconds - lastSec
        if (dtSeconds <= 0.0) {
            return lastVal
        }

        prevTimestampSeconds = timestampSeconds
        prevTimestampNanos = (timestampSeconds * 1_000_000_000.0).toLong()
        return step(value, dtSeconds, lastVal)
    }

    private fun step(raw: Float, dtSeconds: Double, lastVal: Float): Float {
        // 1. Raw derivative dx = (raw - prevSmoothed) / dt
        val rawDerivative = ((raw - lastVal) / dtSeconds).toFloat()

        // 2. Filter derivative with dCutoff
        val alphaD = computeAlpha(dCutoff, dtSeconds)
        val filteredDerivative = alphaD * rawDerivative + (1f - alphaD) * prevFilteredDerivative
        prevFilteredDerivative = filteredDerivative

        // 3. Dynamic cutoff frequency: fc = minCutoff + beta * |filteredDerivative|
        val dynamicCutoff = minCutoff + beta * abs(filteredDerivative)

        // 4. Filter signal with dynamic cutoff
        val alpha = computeAlpha(dynamicCutoff, dtSeconds)
        val smoothed = alpha * raw + (1f - alpha) * lastVal
        prevSmoothed = smoothed
        return smoothed
    }

    /**
     * Resets internal state so that the next received sample is treated as the initial sample.
     */
    fun reset() {
        prevSmoothed = null
        prevFilteredDerivative = 0f
        prevTimestampNanos = null
        prevTimestampSeconds = null
    }
}

/**
 * 3D point filter that smooths [Pt.x], [Pt.y], and [Pt.z] independently using [OneEuroFilter].
 */
class OneEuroPointFilter(
    minCutoff: Float = OneEuroFilter.DEFAULT_MIN_CUTOFF,
    beta: Float = OneEuroFilter.DEFAULT_BETA,
    dCutoff: Float = OneEuroFilter.DEFAULT_D_CUTOFF
) {
    val xFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    val yFilter = OneEuroFilter(minCutoff, beta, dCutoff)
    val zFilter = OneEuroFilter(minCutoff, beta, dCutoff)

    var minCutoff: Float = minCutoff
        set(value) {
            field = value
            xFilter.minCutoff = value
            yFilter.minCutoff = value
            zFilter.minCutoff = value
        }

    var beta: Float = beta
        set(value) {
            field = value
            xFilter.beta = value
            yFilter.beta = value
            zFilter.beta = value
        }

    var dCutoff: Float = dCutoff
        set(value) {
            field = value
            xFilter.dCutoff = value
            yFilter.dCutoff = value
            zFilter.dCutoff = value
        }

    val isInitialized: Boolean
        get() = xFilter.isInitialized && yFilter.isInitialized && zFilter.isInitialized

    /**
     * Filters a 3D point using a nanosecond timestamp.
     */
    fun filter(pt: Pt, timestampNanos: Long): Pt {
        return Pt(
            x = xFilter.filter(pt.x, timestampNanos),
            y = yFilter.filter(pt.y, timestampNanos),
            z = zFilter.filter(pt.z, timestampNanos)
        )
    }

    /**
     * Filters a 3D point using a seconds timestamp.
     */
    fun filterSeconds(pt: Pt, timestampSeconds: Double): Pt {
        return Pt(
            x = xFilter.filterSeconds(pt.x, timestampSeconds),
            y = yFilter.filterSeconds(pt.y, timestampSeconds),
            z = zFilter.filterSeconds(pt.z, timestampSeconds)
        )
    }

    /**
     * Resets all coordinate axis filters.
     */
    fun reset() {
        xFilter.reset()
        yFilter.reset()
        zFilter.reset()
    }
}

/**
 * Landmark list filter (e.g. 21 hand landmarks or 33 pose landmarks) that smooths each landmark
 * coordinate independently using [OneEuroPointFilter].
 */
class OneEuroLandmarksFilter(
    minCutoff: Float = OneEuroFilter.DEFAULT_MIN_CUTOFF,
    beta: Float = OneEuroFilter.DEFAULT_BETA,
    dCutoff: Float = OneEuroFilter.DEFAULT_D_CUTOFF
) {
    var minCutoff: Float = minCutoff
        set(value) {
            field = value
            pointFilters.forEach { it.minCutoff = value }
        }

    var beta: Float = beta
        set(value) {
            field = value
            pointFilters.forEach { it.beta = value }
        }

    var dCutoff: Float = dCutoff
        set(value) {
            field = value
            pointFilters.forEach { it.dCutoff = value }
        }

    private val pointFilters = mutableListOf<OneEuroPointFilter>()

    val filterCount: Int
        get() = pointFilters.size

    val isInitialized: Boolean
        get() = pointFilters.isNotEmpty() && pointFilters.all { it.isInitialized }

    /**
     * Filters an entire list of landmarks using a nanosecond timestamp.
     */
    fun filter(landmarks: List<Pt>, timestampNanos: Long): List<Pt> {
        ensureCapacity(landmarks.size)
        return landmarks.mapIndexed { index, pt ->
            pointFilters[index].filter(pt, timestampNanos)
        }
    }

    /**
     * Filters an entire list of landmarks using a seconds timestamp.
     */
    fun filterSeconds(landmarks: List<Pt>, timestampSeconds: Double): List<Pt> {
        ensureCapacity(landmarks.size)
        return landmarks.mapIndexed { index, pt ->
            pointFilters[index].filterSeconds(pt, timestampSeconds)
        }
    }

    private fun ensureCapacity(size: Int) {
        while (pointFilters.size < size) {
            pointFilters.add(OneEuroPointFilter(minCutoff, beta, dCutoff))
        }
    }

    /**
     * Resets all landmark point filters.
     */
    fun reset() {
        pointFilters.forEach { it.reset() }
    }
}
