package com.zorbi.vision.filter

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Pt
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class OneEuroFilterTest {

    // -------------------------------------------------------------------------
    // 1. First Sample Behavior
    // -------------------------------------------------------------------------

    @Test
    fun firstSample_returnsExactValueWithoutDelayOrMovement() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.005f)
        assertThat(filter.isInitialized).isFalse()
        assertThat(filter.lastFilteredValue).isNull()

        val sampleValue = 0.742f
        val timestampNanos = 1_000_000_000L // 1.0s

        val filtered = filter.filter(sampleValue, timestampNanos)

        // First sample must return the raw value exactly with zero artificial shift
        assertThat(filtered).isEqualTo(sampleValue)
        assertThat(filter.isInitialized).isTrue()
        assertThat(filter.lastFilteredValue).isEqualTo(sampleValue)
    }

    @Test
    fun firstSample_pointFilter_returnsExactPt() {
        val filter = OneEuroPointFilter(minCutoff = 1.0f, beta = 0.005f)
        val samplePt = Pt(x = 0.25f, y = 0.65f, z = -0.15f)

        val filtered = filter.filter(samplePt, 1_000_000_000L)

        assertThat(filtered.x).isEqualTo(samplePt.x)
        assertThat(filtered.y).isEqualTo(samplePt.y)
        assertThat(filtered.z).isEqualTo(samplePt.z)
        assertThat(filter.isInitialized).isTrue()
    }

    @Test
    fun firstSample_landmarksFilter_returnsExactList() {
        val filter = OneEuroLandmarksFilter()
        val dummyLandmarks = (0 until 21).map { i ->
            Pt(x = i * 0.04f, y = i * 0.03f, z = 0f)
        }

        val filtered = filter.filter(dummyLandmarks, 1_000_000_000L)

        assertThat(filtered).hasSize(21)
        dummyLandmarks.indices.forEach { i ->
            assertThat(filtered[i].x).isEqualTo(dummyLandmarks[i].x)
            assertThat(filtered[i].y).isEqualTo(dummyLandmarks[i].y)
            assertThat(filtered[i].z).isEqualTo(dummyLandmarks[i].z)
        }
    }

    // -------------------------------------------------------------------------
    // 2. Constant Input Remains Stable
    // -------------------------------------------------------------------------

    @Test
    fun constantInput_remainsConstantOverTime() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.1f)
        val constantValue = 0.5f
        val startNanos = 1_000_000_000L
        val intervalNanos = 33_333_333L // ~30 fps

        for (i in 0 until 100) {
            val ts = startNanos + i * intervalNanos
            val result = filter.filter(constantValue, ts)
            assertThat(result).isWithin(1e-6f).of(constantValue)
        }
    }

    @Test
    fun constantInput_multiDimensionalPointRemainsStable() {
        val filter = OneEuroPointFilter()
        val constantPt = Pt(0.123f, 0.456f, 0.789f)
        val startNanos = 10_000_000_000L

        for (i in 0 until 60) {
            val ts = startNanos + i * 33_333_333L
            val result = filter.filter(constantPt, ts)
            assertThat(result.x).isWithin(1e-6f).of(constantPt.x)
            assertThat(result.y).isWithin(1e-6f).of(constantPt.y)
            assertThat(result.z).isWithin(1e-6f).of(constantPt.z)
        }
    }

    // -------------------------------------------------------------------------
    // 3. Noisy Input is Smoothed (Jitter Reduction & Lag Reduction)
    // -------------------------------------------------------------------------

    @Test
    fun noisyInput_reducesVarianceWhenStationary() {
        // Stationary hand with high-frequency noise jitter around center = 0.5f
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.0f)
        val center = 0.5f
        val count = 100
        val intervalNanos = 33_333_333L // 30 fps

        val rawSamples = mutableListOf<Float>()
        val filteredSamples = mutableListOf<Float>()

        for (i in 0 until count) {
            val ts = 1_000_000_000L + i * intervalNanos
            // Deterministic synthetic high frequency jitter (+/- 0.03)
            val jitter = 0.03f * sin(i * 1.7f)
            val raw = center + jitter
            rawSamples.add(raw)

            val filtered = filter.filter(raw, ts)
            // Skip the first few warmup frames for variance calculation
            if (i >= 5) {
                filteredSamples.add(filtered)
            }
        }

        val rawSublist = rawSamples.subList(5, count)
        val rawVariance = calculateVariance(rawSublist)
        val filteredVariance = calculateVariance(filteredSamples)

        // Filtered variance should be significantly reduced compared to raw noise variance
        assertThat(filteredVariance).isLessThan(rawVariance * 0.4f)
        // Center mean must be preserved
        val meanFiltered = filteredSamples.average().toFloat()
        assertThat(meanFiltered).isWithin(0.01f).of(center)
    }

    @Test
    fun fastMovement_betaReducesLag() {
        // Compare filter with beta=0 (no speed adaptation) vs filter with beta=1.0 (high speed adaptation)
        val slowFilter = OneEuroFilter(minCutoff = 1.0f, beta = 0.0f)
        val fastFilter = OneEuroFilter(minCutoff = 1.0f, beta = 2.0f)

        val intervalNanos = 33_333_333L // 30 fps
        var ts = 1_000_000_000L

        // Initialize both at 0.0f
        slowFilter.filter(0.0f, ts)
        fastFilter.filter(0.0f, ts)

        // Sudden rapid movement from 0.0 to 1.0 over 3 frames
        val values = listOf(0.33f, 0.66f, 1.0f)
        var lastSlow = 0.0f
        var lastFast = 0.0f

        for (v in values) {
            ts += intervalNanos
            lastSlow = slowFilter.filter(v, ts)
            lastFast = fastFilter.filter(v, ts)
        }

        // Fast filter (higher beta) should have adjusted cutoff frequency and be closer to 1.0f
        assertThat(lastFast).isGreaterThan(lastSlow)
        assertThat(lastFast).isGreaterThan(0.75f)
    }

    // -------------------------------------------------------------------------
    // 4. Timestamp and Frame Interval Handling
    // -------------------------------------------------------------------------

    @Test
    fun zeroOrNegativeInterval_returnsPreviousSmoothedValueSafely() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.1f)
        val initialTs = 1_000_000_000L

        val first = filter.filter(0.2f, initialTs)
        assertThat(first).isEqualTo(0.2f)

        // Duplicate timestamp: dt = 0
        val zeroDt = filter.filter(0.9f, initialTs)
        assertThat(zeroDt).isEqualTo(first)

        // Monotonic regression (past timestamp): dt < 0
        val negativeDt = filter.filter(0.9f, initialTs - 100_000L)
        assertThat(negativeDt).isEqualTo(first)
    }

    @Test
    fun varyingFrameIntervals_maintainsSmoothOutput() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.05f)
        var ts = 1_000_000_000L

        filter.filter(0.0f, ts)

        // Irregular intervals: 16ms (60fps), 33ms (30fps), 66ms (15fps), 25ms (40fps)
        val intervalsMs = listOf(16L, 33L, 66L, 25L, 33L, 50L, 16L)
        for (intervalMs in intervalsMs) {
            ts += intervalMs * 1_000_000L
            val result = filter.filter(0.5f, ts)
            // Output must be finite and progressing smoothly toward 0.5
            assertThat(result).isGreaterThan(0.0f)
            assertThat(result).isAtMost(0.5f)
            assertThat(result.isNaN()).isFalse()
        }
    }

    @Test
    fun largeTimestampGap_recoversCleanlyWithoutArtifacts() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.1f)
        var ts = 1_000_000_000L

        filter.filter(0.1f, ts)
        ts += 33_333_333L
        filter.filter(0.1f, ts)

        // 2-second tracking gap (e.g. camera occlusion or paused)
        ts += 2_000_000_000L
        val resultAfterGap = filter.filter(0.9f, ts)

        // Should adapt cleanly to new position after 2 seconds without NaN or overflow
        assertThat(resultAfterGap).isGreaterThan(0.8f)
        assertThat(resultAfterGap.isNaN()).isFalse()
    }

    @Test
    fun filterSeconds_producesEquivalentResultsToFilterNanos() {
        val filterA = OneEuroFilter(minCutoff = 1.5f, beta = 0.02f)
        val filterB = OneEuroFilter(minCutoff = 1.5f, beta = 0.02f)

        val dtSec = 0.033333333
        val dtNanos = (dtSec * 1_000_000_000.0).toLong()

        var tsSec = 1.0
        var tsNanos = 1_000_000_000L

        val inputs = listOf(0.1f, 0.12f, 0.15f, 0.35f, 0.60f, 0.62f, 0.61f)
        for (raw in inputs) {
            val outA = filterA.filter(raw, tsNanos)
            val outB = filterB.filterSeconds(raw, tsSec)
            assertThat(outB).isWithin(1e-4f).of(outA)

            tsSec += dtSec
            tsNanos += dtNanos
        }
    }

    // -------------------------------------------------------------------------
    // 5. Parameter Behavior and Validation
    // -------------------------------------------------------------------------

    @Test
    fun parameterValidation_rejectsInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) {
            OneEuroFilter(minCutoff = 0.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OneEuroFilter(minCutoff = -1.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OneEuroFilter(beta = -0.01f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OneEuroFilter(dCutoff = 0.0f)
        }
    }

    @Test
    fun parameterMutation_updatesDynamically() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.0f, dCutoff = 1.0f)
        filter.minCutoff = 2.5f
        filter.beta = 0.5f
        filter.dCutoff = 1.2f

        assertThat(filter.minCutoff).isEqualTo(2.5f)
        assertThat(filter.beta).isEqualTo(0.5f)
        assertThat(filter.dCutoff).isEqualTo(1.2f)

        assertThrows(IllegalArgumentException::class.java) {
            filter.minCutoff = -0.5f
        }
    }

    @Test
    fun minCutoff_higherCutoffYieldsLessSmoothing() {
        val lowCutoffFilter = OneEuroFilter(minCutoff = 0.1f, beta = 0.0f)
        val highCutoffFilter = OneEuroFilter(minCutoff = 10.0f, beta = 0.0f)

        var ts = 1_000_000_000L
        lowCutoffFilter.filter(0.0f, ts)
        highCutoffFilter.filter(0.0f, ts)

        ts += 33_333_333L // 33ms step
        val lowResult = lowCutoffFilter.filter(1.0f, ts)
        val highResult = highCutoffFilter.filter(1.0f, ts)

        // Higher cutoff allows more high frequency pass-through (less lag, closer to raw 1.0f)
        assertThat(highResult).isGreaterThan(lowResult)
        assertThat(lowResult).isLessThan(0.1f) // very heavy smoothing
        assertThat(highResult).isGreaterThan(0.6f) // light smoothing
    }

    @Test
    fun reset_clearsStateAndTreatsNextSampleAsFirstSample() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.05f)
        filter.filter(0.5f, 1_000_000_000L)
        filter.filter(0.8f, 1_033_333_333L)
        assertThat(filter.isInitialized).isTrue()

        filter.reset()
        assertThat(filter.isInitialized).isFalse()
        assertThat(filter.lastFilteredValue).isNull()

        // After reset, next sample must act as first sample (return exact value)
        val next = filter.filter(0.2f, 2_000_000_000L)
        assertThat(next).isEqualTo(0.2f)
        assertThat(filter.isInitialized).isTrue()
    }

    // -------------------------------------------------------------------------
    // 6. Point and Landmark Filtering
    // -------------------------------------------------------------------------

    @Test
    fun pointFilter_smoothsXYZIndependently() {
        val filter = OneEuroPointFilter(minCutoff = 1.0f, beta = 0.0f)
        val t0 = 1_000_000_000L
        filter.filter(Pt(0.0f, 0.0f, 0.0f), t0)

        val t1 = t0 + 33_333_333L
        val smoothed = filter.filter(Pt(1.0f, 0.5f, -0.2f), t1)

        // All axes should be smoothed towards target without overshoot
        assertThat(smoothed.x).isGreaterThan(0.0f)
        assertThat(smoothed.x).isLessThan(1.0f)
        assertThat(smoothed.y).isGreaterThan(0.0f)
        assertThat(smoothed.y).isLessThan(0.5f)
        assertThat(smoothed.z).isLessThan(0.0f)
        assertThat(smoothed.z).isGreaterThan(-0.2f)
    }

    @Test
    fun landmarksFilter_smooths21LandmarksIndependently() {
        val filter = OneEuroLandmarksFilter(minCutoff = 1.0f, beta = 0.01f)
        val t0 = 1_000_000_000L

        val initialLandmarks = (0 until 21).map { i -> Pt(i * 0.01f, i * 0.01f, 0f) }
        val out0 = filter.filter(initialLandmarks, t0)
        assertThat(out0).isEqualTo(initialLandmarks)
        assertThat(filter.filterCount).isEqualTo(21)

        val t1 = t0 + 33_333_333L
        val perturbedLandmarks = initialLandmarks.map { pt ->
            Pt(pt.x + 0.05f, pt.y + 0.05f, pt.z + 0.01f)
        }
        val out1 = filter.filter(perturbedLandmarks, t1)

        assertThat(out1).hasSize(21)
        for (i in 0 until 21) {
            assertThat(out1[i].x).isGreaterThan(initialLandmarks[i].x)
            assertThat(out1[i].x).isLessThan(perturbedLandmarks[i].x)
        }

        // Verify reset
        filter.reset()
        val outAfterReset = filter.filter(perturbedLandmarks, 2_000_000_000L)
        // Treated as initial sample
        assertThat(outAfterReset[0].x).isEqualTo(perturbedLandmarks[0].x)
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun calculateVariance(values: List<Float>): Float {
        if (values.size <= 1) return 0f
        val mean = values.average()
        val sumSquares = values.sumOf { (it - mean).pow(2) }
        return (sumSquares / (values.size - 1)).toFloat()
    }
}
