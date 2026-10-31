package com.zorbi.gesture

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Calibration
import com.zorbi.core.GameProfile
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.MappingType
import com.zorbi.core.PoseFrame
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.Slash
import com.zorbi.core.TrackerId
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [SlashGestureInterpreter] per Task P2-4 and Test Gate T-P2-3/4.
 */
class SlashGestureInterpreterTest {

    private lateinit var interpreter: SlashGestureInterpreter
    private lateinit var profile: GameProfile
    private lateinit var calibration: Calibration

    @Before
    fun setUp() {
        interpreter = SlashGestureInterpreter(
            minConfidence = 0.5f,
            minLinearity = 0.70f,
            sampleWindowMs = 250L,
            maxTrackingGapMs = 500L,
            rearmSpeedFactor = 0.5f
        )

        profile = GameProfile(
            schemaVersion = 1,
            packageName = "com.halfbrick.fruitninja",
            displayName = "Fruit Ninja",
            orientation = ProfileOrientation.LANDSCAPE,
            tracker = TrackerId.HAND_SINGLE,
            mapping = MappingType.SLASH,
            targets = emptyMap(),
            params = ProfileParams(
                cooldownMs = 350L,
                swipeMinSpeed = 1.2f,
                slashMinLength = 0.15f
            )
        )

        calibration = Calibration(
            neutralAngleDeg = 0f,
            maxAngleDeg = 35f,
            reachScaleX = 1.0f,
            reachScaleY = 1.0f,
            dominantHand = Side.RIGHT,
            createdAtEpochMs = 1700000000000L
        )
    }

    private fun createHandFrame(
        tsNanos: Long,
        tipX: Float,
        tipY: Float,
        score: Float = 0.95f,
        side: Side = Side.RIGHT
    ): HandsFrame {
        val landmarks = MutableList(21) { Pt(0.5f, 0.5f, 0f) }
        landmarks[8] = Pt(tipX, tipY, 0f) // Landmark 8 = Index Fingertip
        val hand = Hand(side = side, score = score, landmarks = landmarks)
        return HandsFrame(tsNanos = tsNanos, hands = listOf(hand))
    }

    // 1. Valid Slash Recognition
    @Test
    fun test1_validSlashRecognition() {
        // Frame 1 at t=0ms: tip at (0.20, 0.20)
        val f1 = createHandFrame(tsNanos = 0L, tipX = 0.20f, tipY = 0.20f)
        assertThat(interpreter.onFrame(f1, profile, calibration)).isEmpty()

        // Frame 2 at t=30ms: tip at (0.26, 0.26) (length ~ 0.085 < slashMinLength 0.15)
        val f2 = createHandFrame(tsNanos = 30_000_000L, tipX = 0.26f, tipY = 0.26f)
        assertThat(interpreter.onFrame(f2, profile, calibration)).isEmpty()

        // Frame 3 at t=60ms: tip at (0.40, 0.40) (length ~ 0.283 > 0.15, speed ~ 4.71 > 1.2 units/s)
        val f3 = createHandFrame(tsNanos = 60_000_000L, tipX = 0.40f, tipY = 0.40f)
        val events = interpreter.onFrame(f3, profile, calibration)

        assertThat(events).hasSize(1)
        val slash = events[0] as Slash
        assertThat(slash.tsNanos).isEqualTo(60_000_000L)
        assertThat(slash.path).hasSize(3)
        assertThat(slash.path[0].x).isWithin(1e-5f).of(0.20f)
        assertThat(slash.path[0].y).isWithin(1e-5f).of(0.20f)
        assertThat(slash.path[1].x).isWithin(1e-5f).of(0.26f)
        assertThat(slash.path[1].y).isWithin(1e-5f).of(0.26f)
        assertThat(slash.path[2].x).isWithin(1e-5f).of(0.40f)
        assertThat(slash.path[2].y).isWithin(1e-5f).of(0.40f)
    }

    // 2. Direction and Movement Consistency (Linearity threshold)
    @Test
    fun test2_directionAndMovementConsistency() {
        // Erratic zig-zag movement: high cumulative distance but low net displacement
        val f1 = createHandFrame(tsNanos = 0L, tipX = 0.40f, tipY = 0.50f)
        interpreter.onFrame(f1, profile, calibration)

        // Rapid movement to the right (+0.20)
        val f2 = createHandFrame(tsNanos = 20_000_000L, tipX = 0.60f, tipY = 0.50f)
        interpreter.onFrame(f2, profile, calibration)

        // Rapid reversal back to near start (-0.18) -> Linearity = 0.02 / 0.38 ~ 0.05 < 0.70
        val f3 = createHandFrame(tsNanos = 40_000_000L, tipX = 0.42f, tipY = 0.50f)
        val events = interpreter.onFrame(f3, profile, calibration)

        assertThat(events).isEmpty()
    }

    // 3. Movement Below Minimum Threshold (slashMinLength = 0.15)
    @Test
    fun test3_minimumMovementThreshold() {
        // Fast movement (speed ~ 3.5 units/s) but total length only ~0.07 < 0.15
        val f1 = createHandFrame(tsNanos = 0L, tipX = 0.50f, tipY = 0.50f)
        interpreter.onFrame(f1, profile, calibration)

        val f2 = createHandFrame(tsNanos = 20_000_000L, tipX = 0.55f, tipY = 0.55f)
        val events = interpreter.onFrame(f2, profile, calibration)

        assertThat(events).isEmpty()
    }

    // 4. Insufficient Speed Rejection (swipeMinSpeed = 1.2 units/s)
    @Test
    fun test4_insufficientSpeedRejection() {
        // Long movement (length ~ 0.212 > 0.15) but very slow (dt = 250ms -> speed ~ 0.85 < 1.2)
        val f1 = createHandFrame(tsNanos = 0L, tipX = 0.35f, tipY = 0.35f)
        interpreter.onFrame(f1, profile, calibration)

        val f2 = createHandFrame(tsNanos = 250_000_000L, tipX = 0.50f, tipY = 0.50f)
        val events = interpreter.onFrame(f2, profile, calibration)

        assertThat(events).isEmpty()
    }

    // 5. Stationary Noisy Landmark Rejection
    @Test
    fun test5_stationaryNoisyLandmarkRejection() {
        val jitterOffsets = listOf(0.001f, -0.002f, 0.0015f, -0.001f, 0.002f, -0.0015f, 0.001f)
        var ts = 0L

        for (offset in jitterOffsets) {
            val frame = createHandFrame(tsNanos = ts, tipX = 0.5f + offset, tipY = 0.5f - offset)
            val events = interpreter.onFrame(frame, profile, calibration)
            assertThat(events).isEmpty()
            ts += 33_333_333L // ~30 fps
        }
    }

    // 6. Cooldown Duplicate Prevention
    @Test
    fun test6_cooldownDuplicatePrevention() {
        // Trigger initial slash
        val f1 = createHandFrame(tsNanos = 0L, tipX = 0.20f, tipY = 0.20f)
        interpreter.onFrame(f1, profile, calibration)
        val f2 = createHandFrame(tsNanos = 40_000_000L, tipX = 0.50f, tipY = 0.50f)
        val e1 = interpreter.onFrame(f2, profile, calibration)
        assertThat(e1).hasSize(1)

        // Rapid subsequent movements during cooldown (profile cooldown = 350ms)
        val f3 = createHandFrame(tsNanos = 100_000_000L, tipX = 0.20f, tipY = 0.20f)
        val e2 = interpreter.onFrame(f3, profile, calibration)
        assertThat(e2).isEmpty()

        val f4 = createHandFrame(tsNanos = 150_000_000L, tipX = 0.60f, tipY = 0.60f)
        val e3 = interpreter.onFrame(f4, profile, calibration)
        assertThat(e3).isEmpty()
    }

    // 7. Re-arm After Cooldown and Motion Settling
    @Test
    fun test7_rearmAfterCooldownAndMotionSettling() {
        // 1. Initial valid slash
        val f1 = createHandFrame(tsNanos = 0L, tipX = 0.20f, tipY = 0.20f)
        interpreter.onFrame(f1, profile, calibration)
        val f2 = createHandFrame(tsNanos = 40_000_000L, tipX = 0.50f, tipY = 0.50f)
        val e1 = interpreter.onFrame(f2, profile, calibration)
        assertThat(e1).hasSize(1)

        // 2. High-speed continuation past cooldown (cooldown = 350ms; frame at 400ms moving at high speed)
        val f3 = createHandFrame(tsNanos = 380_000_000L, tipX = 0.50f, tipY = 0.50f)
        interpreter.onFrame(f3, profile, calibration)
        val f4 = createHandFrame(tsNanos = 410_000_000L, tipX = 0.80f, tipY = 0.80f)
        val e2 = interpreter.onFrame(f4, profile, calibration)
        // High speed continuation MUST NOT trigger duplicate slash
        assertThat(e2).isEmpty()

        // 3. Motion settles below threshold (speed < 0.5 * 1.2 = 0.6)
        val f5 = createHandFrame(tsNanos = 500_000_000L, tipX = 0.802f, tipY = 0.801f)
        interpreter.onFrame(f5, profile, calibration)
        val f6 = createHandFrame(tsNanos = 540_000_000L, tipX = 0.803f, tipY = 0.802f)
        interpreter.onFrame(f6, profile, calibration)

        // 4. Subsequent valid slash triggers cleanly
        val f7 = createHandFrame(tsNanos = 600_000_000L, tipX = 0.80f, tipY = 0.80f)
        interpreter.onFrame(f7, profile, calibration)
        val f8 = createHandFrame(tsNanos = 640_000_000L, tipX = 0.50f, tipY = 0.50f)
        val e3 = interpreter.onFrame(f8, profile, calibration)

        assertThat(e3).hasSize(1)
        val slash2 = e3[0] as Slash
        assertThat(slash2.path.size).isAtLeast(2)
        assertThat(slash2.path.last().x).isWithin(1e-5f).of(0.50f)
        assertThat(slash2.path.last().y).isWithin(1e-5f).of(0.50f)
    }

    // 8. Missing or Invalid Tracking Data
    @Test
    fun test8_missingOrInvalidTrackingData() {
        // PoseFrame instead of HandsFrame
        val poseFrame = PoseFrame(tsNanos = 10_000_000L, score = 0.9f, landmarks = emptyList())
        assertThat(interpreter.onFrame(poseFrame, profile, calibration)).isEmpty()

        // Empty hands list
        val emptyHandsFrame = HandsFrame(tsNanos = 20_000_000L, hands = emptyList())
        assertThat(interpreter.onFrame(emptyHandsFrame, profile, calibration)).isEmpty()

        // Confidence below threshold (0.4 < 0.5)
        val lowConfFrame = createHandFrame(tsNanos = 30_000_000L, tipX = 0.5f, tipY = 0.5f, score = 0.40f)
        assertThat(interpreter.onFrame(lowConfFrame, profile, calibration)).isEmpty()

        // Truncated landmarks (< 21)
        val truncatedHand = Hand(side = Side.RIGHT, score = 0.9f, landmarks = List(15) { Pt(0.5f, 0.5f, 0f) })
        val truncatedFrame = HandsFrame(tsNanos = 40_000_000L, hands = listOf(truncatedHand))
        assertThat(interpreter.onFrame(truncatedFrame, profile, calibration)).isEmpty()
    }

    // 9. Stale or Out-of-Order Timestamps
    @Test
    fun test9_staleOrOutOfOrderTimestamps() {
        val f1 = createHandFrame(tsNanos = 100_000_000L, tipX = 0.50f, tipY = 0.50f)
        assertThat(interpreter.onFrame(f1, profile, calibration)).isEmpty()

        // Out-of-order frame (80ms < 100ms)
        val f2 = createHandFrame(tsNanos = 80_000_000L, tipX = 0.70f, tipY = 0.70f)
        assertThat(interpreter.onFrame(f2, profile, calibration)).isEmpty()

        // Identical timestamp (100ms == 100ms)
        val f3 = createHandFrame(tsNanos = 100_000_000L, tipX = 0.70f, tipY = 0.70f)
        assertThat(interpreter.onFrame(f3, profile, calibration)).isEmpty()

        // Forward valid frame
        val f4 = createHandFrame(tsNanos = 140_000_000L, tipX = 0.80f, tipY = 0.80f)
        val events = interpreter.onFrame(f4, profile, calibration)
        assertThat(events).hasSize(1)
    }

    // 10. Tracking Interruption and State Reset
    @Test
    fun test10_trackingInterruptionAndStateReset() {
        val f1 = createHandFrame(tsNanos = 0L, tipX = 0.20f, tipY = 0.20f)
        interpreter.onFrame(f1, profile, calibration)

        // 600ms gap exceeds maxTrackingGapMs (500ms)
        val f2 = createHandFrame(tsNanos = 600_000_000L, tipX = 0.50f, tipY = 0.50f)
        val e1 = interpreter.onFrame(f2, profile, calibration)
        // Discontinuity clears history; cannot trigger false slash across gap
        assertThat(e1).isEmpty()

        // Manual reset clears state
        interpreter.reset()
        val f3 = createHandFrame(tsNanos = 700_000_000L, tipX = 0.20f, tipY = 0.20f)
        interpreter.onFrame(f3, profile, calibration)
        val f4 = createHandFrame(tsNanos = 740_000_000L, tipX = 0.50f, tipY = 0.50f)
        val e2 = interpreter.onFrame(f4, profile, calibration)
        assertThat(e2).hasSize(1)
    }

    // 11. Coordinate Transformation Image to Screen Space
    @Test
    fun test11_coordinateTransformationImageToScreenSpace() {
        // 1. Identity reach scale (1.0, 1.0)
        val cal1 = calibration.copy(reachScaleX = 1.0f, reachScaleY = 1.0f)
        val p1 = interpreter.transformToScreenSpace(Pt(0.5f, 0.5f, 0f), cal1)
        assertThat(p1.x).isWithin(1e-5f).of(0.5f)
        assertThat(p1.y).isWithin(1e-5f).of(0.5f)

        // 2. Scaled reach (reachScaleX = 0.8, reachScaleY = 0.5)
        val cal2 = calibration.copy(reachScaleX = 0.8f, reachScaleY = 0.5f)
        // (0.9 - 0.5)/0.8 + 0.5 = 1.0; (0.75 - 0.5)/0.5 + 0.5 = 1.0
        val p2 = interpreter.transformToScreenSpace(Pt(0.9f, 0.75f, 0f), cal2)
        assertThat(p2.x).isWithin(1e-5f).of(1.0f)
        assertThat(p2.y).isWithin(1e-5f).of(1.0f)

        // 3. Out-of-bounds clipping to [0, 1]
        val p3 = interpreter.transformToScreenSpace(Pt(1.5f, -0.2f, 0f), cal1)
        assertThat(p3.x).isEqualTo(1.0f)
        assertThat(p3.y).isEqualTo(0.0f)
    }
}
