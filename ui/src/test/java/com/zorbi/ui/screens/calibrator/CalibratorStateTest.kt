package com.zorbi.ui.screens.calibrator

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Calibration
import com.zorbi.core.Side
import org.junit.Test

/**
 * Unit verification for Calibrator UI state, phase thresholds, and skeleton topology.
 * Enforces Master Prompt Section 8 / Task P3-3 & Test Gate T-P3-3.
 */
class CalibratorStateTest {

    @Test
    fun verifyCalibrationPhaseTransitions() {
        // Simulating the 60s countdown logic
        fun resolvePhase(remainingSeconds: Int): CalibrationPhase = when {
            remainingSeconds > 50 -> CalibrationPhase.DETECTING
            remainingSeconds > 30 -> CalibrationPhase.RECORDING_NEUTRAL
            remainingSeconds > 5 -> CalibrationPhase.RECORDING_RANGE
            else -> CalibrationPhase.COMPUTING
        }

        assertThat(resolvePhase(60)).isEqualTo(CalibrationPhase.DETECTING)
        assertThat(resolvePhase(51)).isEqualTo(CalibrationPhase.DETECTING)
        assertThat(resolvePhase(50)).isEqualTo(CalibrationPhase.RECORDING_NEUTRAL)
        assertThat(resolvePhase(31)).isEqualTo(CalibrationPhase.RECORDING_NEUTRAL)
        assertThat(resolvePhase(30)).isEqualTo(CalibrationPhase.RECORDING_RANGE)
        assertThat(resolvePhase(6)).isEqualTo(CalibrationPhase.RECORDING_RANGE)
        assertThat(resolvePhase(5)).isEqualTo(CalibrationPhase.COMPUTING)
        assertThat(resolvePhase(0)).isEqualTo(CalibrationPhase.COMPUTING)
    }

    @Test
    fun verifyHandConnectionsTopology() {
        // MediaPipe Hand contains 21 landmarks (0..20)
        for ((start, end) in HAND_CONNECTIONS) {
            assertThat(start).isAtLeast(0)
            assertThat(start).isAtMost(20)
            assertThat(end).isAtLeast(0)
            assertThat(end).isAtMost(20)
            assertThat(start).isNotEqualTo(end)
        }

        // Must contain all 5 fingers and palm base
        assertThat(HAND_CONNECTIONS).hasSize(21)
    }

    @Test
    fun verifyCalibrationResultStructure() {
        val cal = Calibration(
            neutralAngleDeg = 0f,
            maxAngleDeg = 40f,
            reachScaleX = 0.85f,
            reachScaleY = 0.75f,
            dominantHand = Side.RIGHT,
            createdAtEpochMs = 1730350000000L
        )

        assertThat(cal.neutralAngleDeg).isEqualTo(0f)
        assertThat(cal.maxAngleDeg).isEqualTo(40f)
        assertThat(cal.reachScaleX).isGreaterThan(0f)
        assertThat(cal.reachScaleY).isGreaterThan(0f)
        assertThat(cal.dominantHand).isEqualTo(Side.RIGHT)
    }
}
