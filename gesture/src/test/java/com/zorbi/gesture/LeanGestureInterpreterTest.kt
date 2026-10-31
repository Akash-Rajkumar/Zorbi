package com.zorbi.gesture

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Calibration
import com.zorbi.core.GameProfile
import com.zorbi.core.HandsFrame
import com.zorbi.core.MappingType
import com.zorbi.core.PoseFrame
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.Steer
import com.zorbi.core.TrackerId
import kotlin.math.PI
import kotlin.math.tan
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [LeanGestureInterpreter] per Task P2-8 and Test Gate T-P2-7/8.
 *
 * Pass criteria:
 * "Steering fixtures: Deadzone respected, value clamps to [-1,1], neutral from calibration applied"
 */
class LeanGestureInterpreterTest {

    private lateinit var interpreter: LeanGestureInterpreter
    private lateinit var profile: GameProfile
    private lateinit var calibration: Calibration

    @Before
    fun setUp() {
        interpreter = LeanGestureInterpreter(
            minConfidence = 0.5f,
            minShoulderDistance = 0.04f,
            maxTrackingGapMs = 500L
        )

        profile = GameProfile(
            schemaVersion = 1,
            packageName = "com.vectorunit.purple.googleplay",
            displayName = "Beach Buggy Racing",
            orientation = ProfileOrientation.LANDSCAPE,
            tracker = TrackerId.POSE_LITE,
            mapping = MappingType.STEER_BUTTONS,
            targets = emptyMap(),
            params = ProfileParams(
                deadzoneDeg = 8.0f,
                cooldownMs = 200L
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

    private fun createPoseFrame(
        tsNanos: Long,
        shoulderL: Pt,
        shoulderR: Pt,
        score: Float = 0.9f
    ): PoseFrame {
        // MediaPipe Pose Lite has 33 landmarks:
        // Index 11 is LEFT_SHOULDER, Index 12 is RIGHT_SHOULDER
        val landmarks = ArrayList<Pt>(33)
        for (i in 0 until 33) {
            when (i) {
                11 -> landmarks.add(shoulderL)
                12 -> landmarks.add(shoulderR)
                else -> landmarks.add(Pt(0.5f, 0.5f))
            }
        }
        return PoseFrame(tsNanos = tsNanos, score = score, landmarks = landmarks)
    }

    private fun computeShoulderRForAngle(shoulderL: Pt, dx: Float, angleDeg: Double): Pt {
        val dy = dx * tan(angleDeg * PI / 180.0)
        return Pt(shoulderL.x + dx, (shoulderL.y + dy).toFloat())
    }

    @Test
    fun test1_neutralBodyLean_withinDeadzone_emitsZeroValue() {
        // Horizontal shoulders: dy = 0 -> angle = 0.0 deg (within 8 deg deadzone)
        val frame = createPoseFrame(
            tsNanos = 1_000_000L,
            shoulderL = Pt(0.35f, 0.40f),
            shoulderR = Pt(0.65f, 0.40f)
        )

        val events = interpreter.onFrame(frame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isEqualTo(0.0f)
        assertThat(steer.angleDeg).isWithin(0.01f).of(0.0f)
    }

    @Test
    fun test2_leanRightClockwise_beyondDeadzone_emitsPositiveValue() {
        // Clockwise lean (lean right): shoulderR goes down (dy > 0), producing +28.0 deg
        val shoulderL = Pt(0.35f, 0.40f)
        val shoulderR = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 28.0)
        val frame = createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR)

        val events = interpreter.onFrame(frame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        // 28.0 deg / 35.0 deg maxAngle = 0.8
        assertThat(steer.value).isWithin(0.02f).of(0.8f)
        assertThat(steer.angleDeg).isWithin(0.1f).of(28.0f)
    }

    @Test
    fun test3_leanLeftCounterClockwise_beyondDeadzone_emitsNegativeValue() {
        // Counter-clockwise lean (lean left): shoulderR goes up (dy < 0), producing -24.5 deg
        val shoulderL = Pt(0.35f, 0.40f)
        val shoulderR = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = -24.5)
        val frame = createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR)

        val events = interpreter.onFrame(frame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        // -24.5 deg / 35.0 deg maxAngle = -0.7
        assertThat(steer.value).isWithin(0.02f).of(-0.7f)
        assertThat(steer.angleDeg).isWithin(0.1f).of(-24.5f)
    }

    @Test
    fun test4_clampingToMaxAngle_clampsToPositiveOneAndNegativeOne() {
        val shoulderL = Pt(0.35f, 0.40f)

        // Extreme lean right (+50 deg > 35 deg max)
        val shoulderR50 = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 50.0)
        val frameRight = createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR50)
        val eventsRight = interpreter.onFrame(frameRight, profile, calibration)
        val steerRight = eventsRight[0] as Steer
        assertThat(steerRight.value).isEqualTo(1.0f)
        assertThat(steerRight.angleDeg).isWithin(0.1f).of(50.0f)

        // Extreme lean left (-50 deg < -35 deg max)
        val shoulderRNeg50 = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = -50.0)
        val frameLeft = createPoseFrame(tsNanos = 2_000_000L, shoulderL = shoulderL, shoulderR = shoulderRNeg50)
        val eventsLeft = interpreter.onFrame(frameLeft, profile, calibration)
        val steerLeft = eventsLeft[0] as Steer
        assertThat(steerLeft.value).isEqualTo(-1.0f)
        assertThat(steerLeft.angleDeg).isWithin(0.1f).of(-50.0f)
    }

    @Test
    fun test5_calibrationNeutralAngle_appliedCorrectly() {
        val calWithNeutral = calibration.copy(neutralAngleDeg = 10.0f)
        val shoulderL = Pt(0.35f, 0.40f)

        // Body held at raw 10 deg: calibrated delta = 10 - 10 = 0 deg -> value = 0.0
        val shoulderR10 = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 10.0)
        val frameNeutral = createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR10)
        val eventsNeutral = interpreter.onFrame(frameNeutral, profile, calWithNeutral)
        val steerNeutral = eventsNeutral[0] as Steer
        assertThat(steerNeutral.value).isEqualTo(0.0f)
        assertThat(steerNeutral.angleDeg).isWithin(0.01f).of(0.0f)

        // Body held at raw 38 deg: calibrated delta = 38 - 10 = 28 deg -> value = 28/35 = 0.8
        val shoulderR38 = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 38.0)
        val frameTurn = createPoseFrame(tsNanos = 2_000_000L, shoulderL = shoulderL, shoulderR = shoulderR38)
        val eventsTurn = interpreter.onFrame(frameTurn, profile, calWithNeutral)
        val steerTurn = eventsTurn[0] as Steer
        assertThat(steerTurn.value).isWithin(0.02f).of(0.8f)
        assertThat(steerTurn.angleDeg).isWithin(0.1f).of(28.0f)

        // Body held at raw 14 deg: calibrated delta = 14 - 10 = 4 deg (<= 8 deg deadzone) -> value = 0.0
        val shoulderR14 = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 14.0)
        val frameDeadzone = createPoseFrame(tsNanos = 3_000_000L, shoulderL = shoulderL, shoulderR = shoulderR14)
        val eventsDeadzone = interpreter.onFrame(frameDeadzone, profile, calWithNeutral)
        val steerDeadzone = eventsDeadzone[0] as Steer
        assertThat(steerDeadzone.value).isEqualTo(0.0f)
        assertThat(steerDeadzone.angleDeg).isWithin(0.1f).of(4.0f)
    }

    @Test
    fun test6_calibrationMaxAngleSensitivity_changesScaling() {
        val sensitiveCal = calibration.copy(maxAngleDeg = 20.0f)
        val shoulderL = Pt(0.35f, 0.40f)
        val shoulderR = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 15.0)
        val frame = createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR)

        val events = interpreter.onFrame(frame, profile, sensitiveCal)

        val steer = events[0] as Steer
        // 15.0 deg / 20.0 deg maxAngle = 0.75
        assertThat(steer.value).isWithin(0.02f).of(0.75f)
        assertThat(steer.angleDeg).isWithin(0.1f).of(15.0f)
    }

    @Test
    fun test7_deadzoneRespected_smallAnglesProduceZero() {
        val deadzoneProfile = profile.copy(
            params = profile.params.copy(deadzoneDeg = 10.0f)
        )
        val shoulderL = Pt(0.35f, 0.40f)

        // Within deadzone: +6 deg -> value 0.0
        val shoulderR6 = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 6.0)
        val frame6 = createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR6)
        val events6 = interpreter.onFrame(frame6, deadzoneProfile, calibration)
        assertThat((events6[0] as Steer).value).isEqualTo(0.0f)

        // Within deadzone: -9.5 deg -> value 0.0
        val shoulderRNeg9 = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = -9.5)
        val frameNeg9 = createPoseFrame(tsNanos = 2_000_000L, shoulderL = shoulderL, shoulderR = shoulderRNeg9)
        val eventsNeg9 = interpreter.onFrame(frameNeg9, deadzoneProfile, calibration)
        assertThat((eventsNeg9[0] as Steer).value).isEqualTo(0.0f)

        // Outside deadzone: +11 deg -> value > 0.0
        val shoulderR11 = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 11.0)
        val frame11 = createPoseFrame(tsNanos = 3_000_000L, shoulderL = shoulderL, shoulderR = shoulderR11)
        val events11 = interpreter.onFrame(frame11, deadzoneProfile, calibration)
        assertThat((events11[0] as Steer).value).isGreaterThan(0.0f)
    }

    @Test
    fun test8_shoulderLandmarkIndexInversion_handledSafely() {
        val shoulderL = Pt(0.35f, 0.40f)
        val shoulderR = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 28.0)

        // Swap indices: place shoulderR at index 11 and shoulderL at index 12
        val landmarks = ArrayList<Pt>(33)
        for (i in 0 until 33) {
            when (i) {
                11 -> landmarks.add(shoulderR)
                12 -> landmarks.add(shoulderL)
                else -> landmarks.add(Pt(0.5f, 0.5f))
            }
        }
        val invertedFrame = PoseFrame(tsNanos = 1_000_000L, score = 0.9f, landmarks = landmarks)

        val events = interpreter.onFrame(invertedFrame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isWithin(0.02f).of(0.8f)
        assertThat(steer.angleDeg).isWithin(0.1f).of(28.0f)
    }

    @Test
    fun test9_lowConfidencePoseFrame_rejectedAsTrackingLoss() {
        val shoulderL = Pt(0.35f, 0.40f)
        val shoulderR = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 28.0)

        // Start active
        interpreter.onFrame(createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR), profile, calibration)

        // Frame with score 0.4f < 0.5f
        val lowScoreFrame = createPoseFrame(
            tsNanos = 2_000_000L,
            shoulderL = shoulderL,
            shoulderR = shoulderR,
            score = 0.40f
        )
        val events = interpreter.onFrame(lowScoreFrame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isEqualTo(0.0f)
    }

    @Test
    fun test10_insufficientLandmarks_rejectedSafely() {
        val shoulderL = Pt(0.35f, 0.40f)
        val shoulderR = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 28.0)
        interpreter.onFrame(createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR), profile, calibration)

        // Frame with only 5 landmarks (< 13)
        val shortFrame = PoseFrame(tsNanos = 2_000_000L, score = 0.9f, landmarks = listOf(shoulderL, shoulderR))
        val events = interpreter.onFrame(shortFrame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isEqualTo(0.0f)
    }

    @Test
    fun test11_staleAndOutOfOrderFrames_rejected() {
        val shoulderL = Pt(0.35f, 0.40f)
        val shoulderR = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 28.0)

        val frame1 = createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR)
        val events1 = interpreter.onFrame(frame1, profile, calibration)
        assertThat(events1).hasSize(1)

        // Out-of-order frame
        val staleFrame = createPoseFrame(tsNanos = 900_000L, shoulderL = shoulderL, shoulderR = shoulderR)
        val eventsStale = interpreter.onFrame(staleFrame, profile, calibration)
        assertThat(eventsStale).isEmpty()

        // Duplicate timestamp frame
        val dupFrame = createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR)
        val eventsDup = interpreter.onFrame(dupFrame, profile, calibration)
        assertThat(eventsDup).isEmpty()
    }

    @Test
    fun test12_trackingGapExceedingMax_resetsState() {
        val shoulderL = Pt(0.35f, 0.40f)
        val shoulderR = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 28.0)

        // Frame at t = 100ms
        interpreter.onFrame(
            createPoseFrame(tsNanos = 100_000_000L, shoulderL = shoulderL, shoulderR = shoulderR),
            profile,
            calibration
        )

        // Frame at t = 800ms (700ms gap > 500ms maxTrackingGapMs)
        val frameAfterGap = createPoseFrame(tsNanos = 800_000_000L, shoulderL = shoulderL, shoulderR = shoulderR)
        val events = interpreter.onFrame(frameAfterGap, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isWithin(0.02f).of(0.8f)
    }

    @Test
    fun test13_shouldersTooClose_rejectedAsInvalid() {
        // Both shoulders collapsed to same point
        val frame = createPoseFrame(
            tsNanos = 1_000_000L,
            shoulderL = Pt(0.50f, 0.40f),
            shoulderR = Pt(0.51f, 0.40f)
        )

        val events = interpreter.onFrame(frame, profile, calibration)
        assertThat(events).isEmpty()
    }

    @Test
    fun test14_trackingLossAfterActiveLean_releasesSteeringOnce() {
        val shoulderL = Pt(0.35f, 0.40f)
        val shoulderR = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 28.0)

        // Frame 1: Valid lean
        val events1 = interpreter.onFrame(
            createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR),
            profile,
            calibration
        )
        assertThat(events1).hasSize(1)
        assertThat((events1[0] as Steer).value).isGreaterThan(0.5f)

        // Frame 2: Low score tracking loss -> emits neutral release
        val events2 = interpreter.onFrame(
            createPoseFrame(tsNanos = 2_000_000L, shoulderL = shoulderL, shoulderR = shoulderR, score = 0.2f),
            profile,
            calibration
        )
        assertThat(events2).hasSize(1)
        val steerLoss = events2[0] as Steer
        assertThat(steerLoss.value).isEqualTo(0.0f)

        // Frame 3: Continued loss -> does not spam redundant releases
        val events3 = interpreter.onFrame(
            createPoseFrame(tsNanos = 3_000_000L, shoulderL = shoulderL, shoulderR = shoulderR, score = 0.2f),
            profile,
            calibration
        )
        assertThat(events3).isEmpty()
    }

    @Test
    fun test15_nonPoseFrame_handledGracefully() {
        val shoulderL = Pt(0.35f, 0.40f)
        val shoulderR = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 28.0)
        interpreter.onFrame(createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR), profile, calibration)

        val handsFrame = HandsFrame(tsNanos = 2_000_000L, hands = emptyList())
        val events = interpreter.onFrame(handsFrame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isEqualTo(0.0f)
    }

    @Test
    fun test16_resetLifecycle_clearsHistoryAndTracking() {
        val shoulderL = Pt(0.35f, 0.40f)
        val shoulderR = computeShoulderRForAngle(shoulderL, dx = 0.30f, angleDeg = 28.0)

        val frame1 = createPoseFrame(tsNanos = 1_000_000L, shoulderL = shoulderL, shoulderR = shoulderR)
        interpreter.onFrame(frame1, profile, calibration)

        // Reset
        interpreter.reset()

        // Frame with earlier timestamp is accepted because state was reset
        val frame2 = createPoseFrame(tsNanos = 500_000L, shoulderL = shoulderL, shoulderR = shoulderR)
        val events = interpreter.onFrame(frame2, profile, calibration)
        assertThat(events).hasSize(1)
        assertThat((events[0] as Steer).value).isWithin(0.02f).of(0.8f)
    }
}

