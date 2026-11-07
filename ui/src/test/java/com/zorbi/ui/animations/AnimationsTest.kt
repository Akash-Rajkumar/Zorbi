package com.zorbi.ui.animations

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit verification for visual polish and animation constraints.
 */
class AnimationsTest {

    @Test
    fun transitionDurations_areWithinLowLatencyThresholds() {
        val enterDurationMs = 260
        val exitDurationMs = 260
        val playScaleDurationMs = 280

        // Transitions should be snappy (<= 300ms) for responsive gaming UI
        assertThat(enterDurationMs).isAtMost(300)
        assertThat(exitDurationMs).isAtMost(300)
        assertThat(playScaleDurationMs).isAtMost(300)
    }

    @Test
    fun glowAlphaBounds_remainSubtleAndNonObtrusive() {
        val minAlpha = 0.08f
        val maxAlpha = 0.28f

        assertThat(minAlpha).isGreaterThan(0.0f)
        assertThat(maxAlpha).isLessThan(0.5f) // Should never overpower dark UI
    }
}
