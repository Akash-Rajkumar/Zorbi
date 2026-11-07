package com.zorbi.ui.components

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.HudFrame
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.TrackerId
import com.zorbi.ui.screens.calibrator.HAND_CONNECTIONS
import com.zorbi.ui.screens.calibrator.POSE_CONNECTIONS
import org.junit.Test

/**
 * Unit tests for the live hand-tracking indicator pipeline, coordinate extraction,
 * trail pruning, and state handling.
 */
class HandTrackingIndicatorTest {

    private fun create21Landmarks(indexTip: Pt = Pt(0.5f, 0.4f, 0.0f)): List<Pt> {
        return (0..20).map { i ->
            if (i == 8) indexTip else Pt(0.1f * i, 0.2f * i, 0.0f)
        }
    }

    @Test
    fun extractFromTrackerFrame_whenHandsFrameWithFingertip_returnsTrackingAndPt() {
        val expectedTip = Pt(0.42f, 0.38f, -0.05f)
        val frame = HandsFrame(
            tsNanos = 100_000_000L,
            hands = listOf(
                Hand(
                    side = Side.RIGHT,
                    score = 0.92f,
                    landmarks = create21Landmarks(expectedTip)
                )
            )
        )

        val result = HandTrackingExtractor.extractFromTrackerFrame(frame, isTrackerConnected = true)

        assertThat(result.status).isEqualTo(HandTrackingStatus.TRACKING)
        assertThat(result.fingertipPt).isEqualTo(expectedTip)
        assertThat(result.confidence).isEqualTo(0.92f)
        assertThat(result.allLandmarks).hasSize(21)
        assertThat(result.isPose).isFalse()
    }

    @Test
    fun extractFromTrackerFrame_whenHandsFrameEmpty_returnsNoHandDetected() {
        val frame = HandsFrame(
            tsNanos = 100_000_000L,
            hands = emptyList()
        )

        val result = HandTrackingExtractor.extractFromTrackerFrame(frame, isTrackerConnected = true)

        assertThat(result.status).isEqualTo(HandTrackingStatus.NO_HAND_DETECTED)
        assertThat(result.fingertipPt).isNull()
        assertThat(result.confidence).isNull()
        assertThat(result.allLandmarks).isEmpty()
    }

    @Test
    fun extractFromTrackerFrame_whenTrackerDisconnected_returnsNotConnected() {
        val frame = HandsFrame(
            tsNanos = 100_000_000L,
            hands = listOf(
                Hand(
                    side = Side.RIGHT,
                    score = 0.95f,
                    landmarks = create21Landmarks()
                )
            )
        )

        val result = HandTrackingExtractor.extractFromTrackerFrame(frame, isTrackerConnected = false)

        assertThat(result.status).isEqualTo(HandTrackingStatus.NOT_CONNECTED)
        assertThat(result.fingertipPt).isNull()
    }

    @Test
    fun extractFromTrackerFrame_whenFrameIsNull_returnsNotConnected() {
        val result = HandTrackingExtractor.extractFromTrackerFrame(null, isTrackerConnected = true)

        assertThat(result.status).isEqualTo(HandTrackingStatus.NOT_CONNECTED)
        assertThat(result.fingertipPt).isNull()
    }

    @Test
    fun extractFromTrackerFrame_whenPoseFrame_returnsRightOrLeftIndex() {
        val landmarks = (0..25).map { i ->
            if (i == 20) Pt(0.7f, 0.6f, 0.0f) else Pt(0.1f * i, 0.1f * i, 0.0f)
        }
        val frame = PoseFrame(
            tsNanos = 200_000_000L,
            score = 0.88f,
            landmarks = landmarks
        )

        val result = HandTrackingExtractor.extractFromTrackerFrame(frame, isTrackerConnected = true)

        assertThat(result.status).isEqualTo(HandTrackingStatus.TRACKING)
        assertThat(result.fingertipPt).isEqualTo(Pt(0.7f, 0.6f, 0.0f))
        assertThat(result.confidence).isEqualTo(0.88f)
        assertThat(result.isPose).isTrue()
    }

    @Test
    fun extractFromHudFrame_whenValidLandmarks_returnsTracking() {
        val tip = Pt(0.33f, 0.67f, 0.0f)
        val hudFrame = HudFrame(
            tsNanos = 300_000_000L,
            trackerId = TrackerId.HAND_SINGLE,
            landmarks = create21Landmarks(tip),
            lastGesture = null,
            fps = 60f,
            e2eLatencyMs = 12f
        )

        val result = HandTrackingExtractor.extractFromHudFrame(hudFrame, isEngineRunning = true)

        assertThat(result.status).isEqualTo(HandTrackingStatus.TRACKING)
        assertThat(result.fingertipPt).isEqualTo(tip)
        assertThat(result.allLandmarks).hasSize(21)
    }

    @Test
    fun extractFromHudFrame_whenDisconnectedOrEmpty_handlesGracefully() {
        val nullResult = HandTrackingExtractor.extractFromHudFrame(null, isEngineRunning = true)
        assertThat(nullResult.status).isEqualTo(HandTrackingStatus.NOT_CONNECTED)

        val emptyHudFrame = HudFrame(
            tsNanos = 300_000_000L,
            trackerId = TrackerId.HAND_SINGLE,
            landmarks = emptyList(),
            lastGesture = null,
            fps = 0f,
            e2eLatencyMs = 0f
        )
        val emptyResult = HandTrackingExtractor.extractFromHudFrame(emptyHudFrame, isEngineRunning = true)
        assertThat(emptyResult.status).isEqualTo(HandTrackingStatus.NO_HAND_DETECTED)
    }

    @Test
    fun pruneTrail_removesOldPointsBeyondMaxAge() {
        val now = 1_000_000_000L // 1.0s
        val recentPoint = FingertipTrailPoint(
            normalizedOffset = Offset(0.5f, 0.5f),
            timestampNanos = now - 100_000_000L // 100ms old -> keep
        )
        val stalePoint = FingertipTrailPoint(
            normalizedOffset = Offset(0.2f, 0.2f),
            timestampNanos = now - 500_000_000L // 500ms old -> prune (> 400ms)
        )

        val trail = mutableListOf(stalePoint, recentPoint)
        HandTrackingExtractor.pruneTrail(trail, nowNanos = now, maxAgeNanos = 400_000_000L)

        assertThat(trail).containsExactly(recentPoint)
    }

    @Test
    fun handConnections_containsIndexFingerChain() {
        // Index finger connects: 5 -> 6 -> 7 -> 8
        assertThat(HAND_CONNECTIONS).contains(5 to 6)
        assertThat(HAND_CONNECTIONS).contains(6 to 7)
        assertThat(HAND_CONNECTIONS).contains(7 to 8)
    }

    @Test
    fun poseConnections_containsTorsoAndArmLinks() {
        assertThat(POSE_CONNECTIONS).contains(11 to 12)
        assertThat(POSE_CONNECTIONS).contains(11 to 13)
        assertThat(POSE_CONNECTIONS).contains(12 to 14)
    }
}
