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
import com.zorbi.core.Steer
import com.zorbi.core.TrackerId
import kotlin.math.PI
import kotlin.math.tan
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [WheelGestureInterpreter] per Task P2-7 and Test Gate T-P2-7/8.
 *
 * Pass criteria:
 * "Steering fixtures: Deadzone respected, value clamps to [-1,1], neutral from calibration applied"
 */
class WheelGestureInterpreterTest {

    private lateinit var interpreter: WheelGestureInterpreter
    private lateinit var profile: GameProfile
    private lateinit var calibration: Calibration

    @Before
    fun setUp() {
        interpreter = WheelGestureInterpreter(
            minConfidence = 0.5f,
            minWristDistance = 0.04f,
            maxTrackingGapMs = 500L
        )

        profile = GameProfile(
            schemaVersion = 1,
            packageName = "com.vectorunit.purple.googleplay",
            displayName = "Beach Buggy Racing",
            orientation = ProfileOrientation.LANDSCAPE,
            tracker = TrackerId.HAND_DUAL_WHEEL,
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

    private fun createHand(
        wrist: Pt,
        side: Side = Side.UNKNOWN,
        score: Float = 0.9f
    ): Hand {
        val landmarks = mutableListOf(wrist)
        for (i in 1 until 21) {
            landmarks.add(Pt(wrist.x + i * 0.005f, wrist.y + i * 0.005f))
        }
        return Hand(side = side, score = score, landmarks = landmarks)
    }

    private fun createHandsFrame(
        tsNanos: Long,
        wristL: Pt,
        wristR: Pt,
        sideL: Side = Side.LEFT,
        sideR: Side = Side.RIGHT,
        scoreL: Float = 0.9f,
        scoreR: Float = 0.9f
    ): HandsFrame {
        val handL = createHand(wrist = wristL, side = sideL, score = scoreL)
        val handR = createHand(wrist = wristR, side = sideR, score = scoreR)
        return HandsFrame(tsNanos = tsNanos, hands = listOf(handL, handR))
    }

    private fun computeWristRForAngle(wristL: Pt, dx: Float, angleDeg: Double): Pt {
        val dy = dx * tan(angleDeg * PI / 180.0)
        return Pt(wristL.x + dx, (wristL.y + dy).toFloat())
    }

    @Test
    fun test1_neutralWheelPosition_withinDeadzone_emitsZeroValue() {
        // Horizontal wrists: dy = 0 -> angle = 0.0 deg (within 8 deg deadzone)
        val frame = createHandsFrame(
            tsNanos = 1_000_000L,
            wristL = Pt(0.25f, 0.50f),
            wristR = Pt(0.75f, 0.50f)
        )

        val events = interpreter.onFrame(frame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isEqualTo(0.0f)
        assertThat(steer.angleDeg).isWithin(0.01f).of(0.0f)
    }

    @Test
    fun test2_turnRightClockwise_beyondDeadzone_emitsPositiveValue() {
        // Clockwise turn: wristR goes down (dy > 0), producing +28.0 deg
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 28.0)
        val frame = createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR)

        val events = interpreter.onFrame(frame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        // 28.0 deg / 35.0 deg maxAngle = 0.8
        assertThat(steer.value).isWithin(0.02f).of(0.8f)
        assertThat(steer.angleDeg).isWithin(0.1f).of(28.0f)
    }

    @Test
    fun test3_turnLeftCounterClockwise_beyondDeadzone_emitsNegativeValue() {
        // Counter-clockwise turn: wristR goes up (dy < 0), producing -24.5 deg
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = -24.5)
        val frame = createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR)

        val events = interpreter.onFrame(frame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        // -24.5 deg / 35.0 deg maxAngle = -0.7
        assertThat(steer.value).isWithin(0.02f).of(-0.7f)
        assertThat(steer.angleDeg).isWithin(0.1f).of(-24.5f)
    }

    @Test
    fun test4_clampingToMaxAngle_clampsToPositiveOneAndNegativeOne() {
        val wristL = Pt(0.25f, 0.50f)

        // Extreme turn right (+50 deg > 35 deg max)
        val wristR50 = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 50.0)
        val frameRight = createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR50)
        val eventsRight = interpreter.onFrame(frameRight, profile, calibration)
        val steerRight = eventsRight[0] as Steer
        assertThat(steerRight.value).isEqualTo(1.0f)
        assertThat(steerRight.angleDeg).isWithin(0.1f).of(50.0f)

        // Extreme turn left (-50 deg < -35 deg max)
        val wristRNeg50 = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = -50.0)
        val frameLeft = createHandsFrame(tsNanos = 2_000_000L, wristL = wristL, wristR = wristRNeg50)
        val eventsLeft = interpreter.onFrame(frameLeft, profile, calibration)
        val steerLeft = eventsLeft[0] as Steer
        assertThat(steerLeft.value).isEqualTo(-1.0f)
        assertThat(steerLeft.angleDeg).isWithin(0.1f).of(-50.0f)
    }

    @Test
    fun test5_calibrationNeutralAngle_appliedCorrectly() {
        val calWithNeutral = calibration.copy(neutralAngleDeg = 10.0f)
        val wristL = Pt(0.25f, 0.50f)

        // Wheel held at raw 10 deg: calibrated delta = 10 - 10 = 0 deg -> value = 0.0
        val wristR10 = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 10.0)
        val frameNeutral = createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR10)
        val eventsNeutral = interpreter.onFrame(frameNeutral, profile, calWithNeutral)
        val steerNeutral = eventsNeutral[0] as Steer
        assertThat(steerNeutral.value).isEqualTo(0.0f)
        assertThat(steerNeutral.angleDeg).isWithin(0.01f).of(0.0f)

        // Wheel held at raw 38 deg: calibrated delta = 38 - 10 = 28 deg -> value = 28/35 = 0.8
        val wristR38 = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 38.0)
        val frameTurn = createHandsFrame(tsNanos = 2_000_000L, wristL = wristL, wristR = wristR38)
        val eventsTurn = interpreter.onFrame(frameTurn, profile, calWithNeutral)
        val steerTurn = eventsTurn[0] as Steer
        assertThat(steerTurn.value).isWithin(0.02f).of(0.8f)
        assertThat(steerTurn.angleDeg).isWithin(0.1f).of(28.0f)

        // Wheel held at raw 14 deg: calibrated delta = 14 - 10 = 4 deg (<= 8 deg deadzone) -> value = 0.0
        val wristR14 = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 14.0)
        val frameDeadzone = createHandsFrame(tsNanos = 3_000_000L, wristL = wristL, wristR = wristR14)
        val eventsDeadzone = interpreter.onFrame(frameDeadzone, profile, calWithNeutral)
        val steerDeadzone = eventsDeadzone[0] as Steer
        assertThat(steerDeadzone.value).isEqualTo(0.0f)
        assertThat(steerDeadzone.angleDeg).isWithin(0.1f).of(4.0f)
    }

    @Test
    fun test6_calibrationMaxAngleSensitivity_changesScaling() {
        val sensitiveCal = calibration.copy(maxAngleDeg = 20.0f)
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 15.0)
        val frame = createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR)

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
        val wristL = Pt(0.25f, 0.50f)

        // Within deadzone: +6 deg -> value 0.0
        val wristR6 = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 6.0)
        val frame6 = createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR6)
        val events6 = interpreter.onFrame(frame6, deadzoneProfile, calibration)
        assertThat((events6[0] as Steer).value).isEqualTo(0.0f)

        // Within deadzone: -9.5 deg -> value 0.0
        val wristRNeg9 = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = -9.5)
        val frameNeg9 = createHandsFrame(tsNanos = 2_000_000L, wristL = wristL, wristR = wristRNeg9)
        val eventsNeg9 = interpreter.onFrame(frameNeg9, deadzoneProfile, calibration)
        assertThat((eventsNeg9[0] as Steer).value).isEqualTo(0.0f)

        // Outside deadzone: +11 deg -> value > 0.0
        val wristR11 = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 11.0)
        val frame11 = createHandsFrame(tsNanos = 3_000_000L, wristL = wristL, wristR = wristR11)
        val events11 = interpreter.onFrame(frame11, deadzoneProfile, calibration)
        assertThat((events11[0] as Steer).value).isGreaterThan(0.0f)
    }

    @Test
    fun test8_handOrderingAndSideLabels_correctlyIdentified() {
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 28.0)

        // Invert list order: Hand 0 is RIGHT, Hand 1 is LEFT
        val handR = createHand(wrist = wristR, side = Side.RIGHT)
        val handL = createHand(wrist = wristL, side = Side.LEFT)
        val frame = HandsFrame(tsNanos = 1_000_000L, hands = listOf(handR, handL))

        val events = interpreter.onFrame(frame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isWithin(0.02f).of(0.8f)
        assertThat(steer.angleDeg).isWithin(0.1f).of(28.0f)
    }

    @Test
    fun test9_handSideUnknown_fallsBackToXCoordinateSorting() {
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 28.0)

        // Both UNKNOWN, but wristL has smaller x (left of frame)
        val frame = createHandsFrame(
            tsNanos = 1_000_000L,
            wristL = wristL,
            wristR = wristR,
            sideL = Side.UNKNOWN,
            sideR = Side.UNKNOWN
        )

        val events = interpreter.onFrame(frame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isWithin(0.02f).of(0.8f)
        assertThat(steer.angleDeg).isWithin(0.1f).of(28.0f)
    }

    @Test
    fun test10_singleHand_trackingLossReleasesSteering() {
        // Frame 1: Valid turn
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 28.0)
        val validFrame = createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR)
        val events1 = interpreter.onFrame(validFrame, profile, calibration)
        assertThat(events1).hasSize(1)
        assertThat((events1[0] as Steer).value).isGreaterThan(0.5f)

        // Frame 2: Only 1 hand present -> emits neutral to release touch
        val singleHandFrame = HandsFrame(
            tsNanos = 2_000_000L,
            hands = listOf(createHand(wrist = wristL, side = Side.LEFT))
        )
        val events2 = interpreter.onFrame(singleHandFrame, profile, calibration)
        assertThat(events2).hasSize(1)
        val lossSteer = events2[0] as Steer
        assertThat(lossSteer.value).isEqualTo(0.0f)
        assertThat(lossSteer.angleDeg).isEqualTo(0.0f)

        // Frame 3: Still 1 hand -> does not spam redundant release
        val events3 = interpreter.onFrame(
            singleHandFrame.copy(tsNanos = 3_000_000L),
            profile,
            calibration
        )
        assertThat(events3).isEmpty()
    }

    @Test
    fun test11_lowConfidenceHand_rejectedAsTrackingLoss() {
        // Start active
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 28.0)
        interpreter.onFrame(createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR), profile, calibration)

        // Low confidence on right hand (0.4f < 0.5f)
        val lowScoreFrame = createHandsFrame(
            tsNanos = 2_000_000L,
            wristL = wristL,
            wristR = wristR,
            scoreR = 0.40f
        )
        val events = interpreter.onFrame(lowScoreFrame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isEqualTo(0.0f)
    }

    @Test
    fun test12_insufficientLandmarks_rejectedSafely() {
        // Start active
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 28.0)
        interpreter.onFrame(createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR), profile, calibration)

        // Hand has only 5 landmarks instead of required 21
        val invalidHand = Hand(side = Side.LEFT, score = 0.9f, landmarks = listOf(wristL))
        val validHand = createHand(wrist = wristR, side = Side.RIGHT)
        val frame = HandsFrame(tsNanos = 2_000_000L, hands = listOf(invalidHand, validHand))

        val events = interpreter.onFrame(frame, profile, calibration)
        assertThat(events).hasSize(1)
        assertThat((events[0] as Steer).value).isEqualTo(0.0f)
    }

    @Test
    fun test13_staleAndOutOfOrderFrames_rejected() {
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 28.0)

        val frame1 = createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR)
        val events1 = interpreter.onFrame(frame1, profile, calibration)
        assertThat(events1).hasSize(1)

        // Out-of-order frame (earlier timestamp)
        val staleFrame = createHandsFrame(tsNanos = 900_000L, wristL = wristL, wristR = wristR)
        val eventsStale = interpreter.onFrame(staleFrame, profile, calibration)
        assertThat(eventsStale).isEmpty()

        // Duplicate timestamp frame
        val dupFrame = createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR)
        val eventsDup = interpreter.onFrame(dupFrame, profile, calibration)
        assertThat(eventsDup).isEmpty()
    }

    @Test
    fun test14_trackingGapExceedingMax_resetsState() {
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 28.0)

        // Frame at t = 100ms
        interpreter.onFrame(
            createHandsFrame(tsNanos = 100_000_000L, wristL = wristL, wristR = wristR),
            profile,
            calibration
        )

        // Frame at t = 800ms (700ms gap > 500ms maxTrackingGapMs)
        val frameAfterGap = createHandsFrame(tsNanos = 800_000_000L, wristL = wristL, wristR = wristR)
        val events = interpreter.onFrame(frameAfterGap, profile, calibration)

        // Processes cleanly after gap reset
        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isWithin(0.02f).of(0.8f)
    }

    @Test
    fun test15_wristsTooClose_rejectedAsInvalid() {
        // Both wrists virtually overlapping (distance < 0.04)
        val frame = createHandsFrame(
            tsNanos = 1_000_000L,
            wristL = Pt(0.50f, 0.50f),
            wristR = Pt(0.51f, 0.50f)
        )

        val events = interpreter.onFrame(frame, profile, calibration)
        assertThat(events).isEmpty()
    }

    @Test
    fun test16_resetLifecycle_clearsHistoryAndTracking() {
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 28.0)

        val frame1 = createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR)
        interpreter.onFrame(frame1, profile, calibration)

        // Reset
        interpreter.reset()

        // After reset, a frame with same or earlier timestamp is accepted because state was reset
        val frame2 = createHandsFrame(tsNanos = 500_000L, wristL = wristL, wristR = wristR)
        val events = interpreter.onFrame(frame2, profile, calibration)
        assertThat(events).hasSize(1)
        assertThat((events[0] as Steer).value).isWithin(0.02f).of(0.8f)
    }

    @Test
    fun test17_nonHandsFrame_handlesGracefully() {
        val wristL = Pt(0.25f, 0.50f)
        val wristR = computeWristRForAngle(wristL, dx = 0.50f, angleDeg = 28.0)
        interpreter.onFrame(createHandsFrame(tsNanos = 1_000_000L, wristL = wristL, wristR = wristR), profile, calibration)

        val poseFrame = PoseFrame(tsNanos = 2_000_000L, score = 0.9f, landmarks = emptyList())
        val events = interpreter.onFrame(poseFrame, profile, calibration)

        assertThat(events).hasSize(1)
        val steer = events[0] as Steer
        assertThat(steer.value).isEqualTo(0.0f)
    }
}

