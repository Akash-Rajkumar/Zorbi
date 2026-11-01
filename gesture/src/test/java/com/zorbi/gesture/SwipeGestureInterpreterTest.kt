package com.zorbi.gesture

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Calibration
import com.zorbi.core.Dir
import com.zorbi.core.GameProfile
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.MappingType
import com.zorbi.core.PoseFrame
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.Swipe
import com.zorbi.core.TargetPoint
import com.zorbi.core.TrackerId
import org.junit.Before
import org.junit.Test

class SwipeGestureInterpreterTest {

    private lateinit var interpreter: SwipeGestureInterpreter
    private lateinit var profile: GameProfile
    private lateinit var calibration: Calibration

    @Before
    fun setUp() {
        interpreter = SwipeGestureInterpreter(
            minConfidence = 0.5f,
            minDistance = 0.08f,
            sampleWindowMs = 200L,
            maxTrackingGapMs = 500L,
            rearmSpeedFactor = 0.5f
        )

        profile = GameProfile(
            schemaVersion = 1,
            packageName = "com.subwaysurfers.runner",
            displayName = "Subway Surfers",
            orientation = ProfileOrientation.PORTRAIT,
            tracker = TrackerId.HAND_SINGLE,
            mapping = MappingType.LANE_SWIPE,
            targets = mapOf(
                "center" to TargetPoint(0.5f, 0.5f),
                "left" to TargetPoint(0.2f, 0.8f),
                "right" to TargetPoint(0.8f, 0.8f)
            ),
            params = ProfileParams(
                cooldownMs = 350L,
                swipeMinSpeed = 1.2f
            )
        )

        calibration = Calibration(
            neutralAngleDeg = 0f,
            maxAngleDeg = 35f,
            reachScaleX = 0.8f,
            reachScaleY = 0.8f,
            dominantHand = Side.RIGHT,
            createdAtEpochMs = 1700000000000L
        )
    }

    private fun createHandFrame(
        tsNanos: Long,
        x: Float,
        y: Float,
        score: Float = 0.95f,
        side: Side = Side.RIGHT
    ): HandsFrame {
        val landmarks = List(21) { idx -> Pt(x, y, 0f) }
        val hand = Hand(side = side, score = score, landmarks = landmarks)
        return HandsFrame(tsNanos = tsNanos, hands = listOf(hand))
    }

    @Test
    fun test1_validLeftSwipe() {
        // Frame 1 at t = 0ms: Hand at x=0.60, y=0.50
        val f1 = createHandFrame(tsNanos = 0L, x = 0.60f, y = 0.50f)
        val e1 = interpreter.onFrame(f1, profile, calibration)
        assertThat(e1).isEmpty()

        // Frame 2 at t = 50ms: Hand moves left to x=0.45, y=0.50 (deltaX = -0.15, dt = 0.05s -> speed = 3.0 units/s)
        val f2 = createHandFrame(tsNanos = 50_000_000L, x = 0.45f, y = 0.50f)
        val e2 = interpreter.onFrame(f2, profile, calibration)

        assertThat(e2).hasSize(1)
        val swipe = e2[0] as Swipe
        assertThat(swipe.dir).isEqualTo(Dir.LEFT)
        assertThat(swipe.speed).isAtLeast(profile.params.swipeMinSpeed)
        assertThat(swipe.tsNanos).isEqualTo(50_000_000L)
    }

    @Test
    fun test2_validRightSwipe() {
        // Frame 1 at t = 0ms: Hand at x=0.40, y=0.50
        val f1 = createHandFrame(tsNanos = 0L, x = 0.40f, y = 0.50f)
        val e1 = interpreter.onFrame(f1, profile, calibration)
        assertThat(e1).isEmpty()

        // Frame 2 at t = 50ms: Hand moves right to x=0.55, y=0.50 (deltaX = +0.15, dt = 0.05s -> speed = 3.0 units/s)
        val f2 = createHandFrame(tsNanos = 50_000_000L, x = 0.55f, y = 0.50f)
        val e2 = interpreter.onFrame(f2, profile, calibration)

        assertThat(e2).hasSize(1)
        val swipe = e2[0] as Swipe
        assertThat(swipe.dir).isEqualTo(Dir.RIGHT)
        assertThat(swipe.speed).isAtLeast(profile.params.swipeMinSpeed)
        assertThat(swipe.tsNanos).isEqualTo(50_000_000L)
    }

    @Test
    fun test3_validUpwardSwipe() {
        // Frame 1 at t = 0ms: Hand at x=0.50, y=0.60
        val f1 = createHandFrame(tsNanos = 0L, x = 0.50f, y = 0.60f)
        val e1 = interpreter.onFrame(f1, profile, calibration)
        assertThat(e1).isEmpty()

        // Frame 2 at t = 50ms: Hand moves up to x=0.50, y=0.45 (deltaY = -0.15, dt = 0.05s -> speed = 3.0 units/s)
        val f2 = createHandFrame(tsNanos = 50_000_000L, x = 0.50f, y = 0.45f)
        val e2 = interpreter.onFrame(f2, profile, calibration)

        assertThat(e2).hasSize(1)
        val swipe = e2[0] as Swipe
        assertThat(swipe.dir).isEqualTo(Dir.UP)
        assertThat(swipe.speed).isAtLeast(profile.params.swipeMinSpeed)
        assertThat(swipe.tsNanos).isEqualTo(50_000_000L)
    }

    @Test
    fun test4_validDownwardSwipe() {
        // Frame 1 at t = 0ms: Hand at x=0.50, y=0.40
        val f1 = createHandFrame(tsNanos = 0L, x = 0.50f, y = 0.40f)
        val e1 = interpreter.onFrame(f1, profile, calibration)
        assertThat(e1).isEmpty()

        // Frame 2 at t = 50ms: Hand moves down to x=0.50, y=0.55 (deltaY = +0.15, dt = 0.05s -> speed = 3.0 units/s)
        val f2 = createHandFrame(tsNanos = 50_000_000L, x = 0.50f, y = 0.55f)
        val e2 = interpreter.onFrame(f2, profile, calibration)

        assertThat(e2).hasSize(1)
        val swipe = e2[0] as Swipe
        assertThat(swipe.dir).isEqualTo(Dir.DOWN)
        assertThat(swipe.speed).isAtLeast(profile.params.swipeMinSpeed)
        assertThat(swipe.tsNanos).isEqualTo(50_000_000L)
    }

    @Test
    fun test5_movementBelowMinimumThreshold() {
        // Movement is fast (speed = 1.5 units/s) but distance is only 0.03 (< minDistance of 0.08)
        val f1 = createHandFrame(tsNanos = 0L, x = 0.50f, y = 0.50f)
        val e1 = interpreter.onFrame(f1, profile, calibration)
        assertThat(e1).isEmpty()

        val f2 = createHandFrame(tsNanos = 20_000_000L, x = 0.53f, y = 0.50f) // dx = 0.03, dt = 20ms -> speed = 1.5
        val e2 = interpreter.onFrame(f2, profile, calibration)

        assertThat(e2).isEmpty()
    }

    @Test
    fun test6_movementBelowSpeedRequirement() {
        // Hand moves a total distance of 0.20 over 500ms in slow steps (speed = 0.4 units/s < 1.2 units/s)
        var events = emptyList<com.zorbi.core.GestureEvent>()
        for (i in 0..10) {
            val ts = i * 50_000_000L
            val x = 0.40f + (i * 0.02f) // total delta = 0.20 over 500ms
            val frame = createHandFrame(tsNanos = ts, x = x, y = 0.50f)
            events = interpreter.onFrame(frame, profile, calibration)
            assertThat(events).isEmpty()
        }
    }

    @Test
    fun test7_cooldownPreventsDuplicateTriggers() {
        // First valid swipe at t = 50ms
        interpreter.onFrame(createHandFrame(tsNanos = 0L, x = 0.60f, y = 0.50f), profile, calibration)
        val swipe1 = interpreter.onFrame(createHandFrame(tsNanos = 50_000_000L, x = 0.45f, y = 0.50f), profile, calibration)
        assertThat(swipe1).hasSize(1)

        // Attempt another fast swipe at t = 100ms and t = 150ms (well within 350ms cooldown)
        val f3 = createHandFrame(tsNanos = 100_000_000L, x = 0.60f, y = 0.50f)
        val e3 = interpreter.onFrame(f3, profile, calibration)
        assertThat(e3).isEmpty()

        val f4 = createHandFrame(tsNanos = 150_000_000L, x = 0.40f, y = 0.50f)
        val e4 = interpreter.onFrame(f4, profile, calibration)
        assertThat(e4).isEmpty()
    }

    @Test
    fun test8_gestureCanTriggerAgainAfterCooldownAndRearm() {
        // 1. Initial valid swipe at t = 50ms
        interpreter.onFrame(createHandFrame(tsNanos = 0L, x = 0.60f, y = 0.50f), profile, calibration)
        val swipe1 = interpreter.onFrame(createHandFrame(tsNanos = 50_000_000L, x = 0.45f, y = 0.50f), profile, calibration)
        assertThat(swipe1).hasSize(1)

        // 2. Continuous fast motion past cooldown window (t = 450ms, elapsed 400ms > 350ms cooldown)
        // Hand moves fast continuously (no settling): should NOT trigger duplicate swipe
        interpreter.onFrame(createHandFrame(tsNanos = 400_000_000L, x = 0.30f, y = 0.50f), profile, calibration)
        val continuousFast = interpreter.onFrame(createHandFrame(tsNanos = 450_000_000L, x = 0.15f, y = 0.50f), profile, calibration)
        assertThat(continuousFast).isEmpty()

        // 3. Hand slows down / settles (speed drops below re-arm threshold)
        interpreter.onFrame(createHandFrame(tsNanos = 500_000_000L, x = 0.15f, y = 0.50f), profile, calibration)
        interpreter.onFrame(createHandFrame(tsNanos = 550_000_000L, x = 0.15f, y = 0.50f), profile, calibration)

        // 4. New distinct swipe to the right
        interpreter.onFrame(createHandFrame(tsNanos = 600_000_000L, x = 0.15f, y = 0.50f), profile, calibration)
        val swipe2 = interpreter.onFrame(createHandFrame(tsNanos = 650_000_000L, x = 0.35f, y = 0.50f), profile, calibration)

        assertThat(swipe2).hasSize(1)
        assertThat((swipe2[0] as Swipe).dir).isEqualTo(Dir.RIGHT)
    }

    @Test
    fun test9_missingOrInvalidTrackingData() {
        // Frame with empty hands list
        val emptyFrame = HandsFrame(tsNanos = 10_000_000L, hands = emptyList())
        assertThat(interpreter.onFrame(emptyFrame, profile, calibration)).isEmpty()

        // Wrong frame type (PoseFrame)
        val poseFrame = PoseFrame(tsNanos = 20_000_000L, score = 0.9f, landmarks = List(33) { Pt(0f, 0f) })
        assertThat(interpreter.onFrame(poseFrame, profile, calibration)).isEmpty()

        // Low confidence hand score (0.2 < 0.5)
        val lowConfFrame = createHandFrame(tsNanos = 30_000_000L, x = 0.5f, y = 0.5f, score = 0.2f)
        assertThat(interpreter.onFrame(lowConfFrame, profile, calibration)).isEmpty()

        // Hand with insufficient landmarks (< 21)
        val badLandmarksHand = Hand(side = Side.RIGHT, score = 0.9f, landmarks = List(10) { Pt(0.5f, 0.5f) })
        val badHandFrame = HandsFrame(tsNanos = 40_000_000L, hands = listOf(badLandmarksHand))
        assertThat(interpreter.onFrame(badHandFrame, profile, calibration)).isEmpty()

        // Stale / out-of-order frame (ts <= lastFrameTs)
        val validF1 = createHandFrame(tsNanos = 100_000_000L, x = 0.5f, y = 0.5f)
        interpreter.onFrame(validF1, profile, calibration)

        val staleFrame = createHandFrame(tsNanos = 90_000_000L, x = 0.3f, y = 0.5f)
        assertThat(interpreter.onFrame(staleFrame, profile, calibration)).isEmpty()

        // Tracking gap > 500ms resets history safely without throwing
        val afterGapFrame = createHandFrame(tsNanos = 1_000_000_000L, x = 0.2f, y = 0.5f)
        assertThat(interpreter.onFrame(afterGapFrame, profile, calibration)).isEmpty()
    }

    @Test
    fun test10_noUnintendedTriggerFromStationaryOrNoisyLandmarks() {
        // Hand stays near (0.50, 0.50) with small sensor fluctuations (+- 0.005)
        val noises = listOf(0.002f, -0.003f, 0.005f, -0.004f, 0.001f, -0.002f, 0.003f, -0.005f)

        for ((idx, noise) in noises.withIndex()) {
            val ts = idx * 30_000_000L
            val frame = createHandFrame(tsNanos = ts, x = 0.50f + noise, y = 0.50f - noise)
            val events = interpreter.onFrame(frame, profile, calibration)
            assertThat(events).isEmpty()
        }
    }

    @Test
    fun test11_resetClearsInterpreterState() {
        interpreter.onFrame(createHandFrame(tsNanos = 0L, x = 0.60f, y = 0.50f), profile, calibration)
        val swipe = interpreter.onFrame(createHandFrame(tsNanos = 50_000_000L, x = 0.45f, y = 0.50f), profile, calibration)
        assertThat(swipe).hasSize(1)

        // Reset clears cooldown and active history
        interpreter.reset()

        // Immediately following reset, a new swipe can be detected without waiting for cooldown
        interpreter.onFrame(createHandFrame(tsNanos = 60_000_000L, x = 0.40f, y = 0.50f), profile, calibration)
        val swipeAfterReset = interpreter.onFrame(createHandFrame(tsNanos = 110_000_000L, x = 0.60f, y = 0.50f), profile, calibration)

        assertThat(swipeAfterReset).hasSize(1)
        assertThat((swipeAfterReset[0] as Swipe).dir).isEqualTo(Dir.RIGHT)
    }

    @Test
    fun test12_subwaySurfersProfileBoundaryValidLeftSwipe() {
        val subwayProfile = profile.copy(
            packageName = "com.kiloo.subwaysurf",
            params = ProfileParams(cooldownMs = 350L, swipeMinSpeed = 0.18f)
        )
        val f1 = createHandFrame(tsNanos = 0L, x = 0.50f, y = 0.50f)
        assertThat(interpreter.onFrame(f1, subwayProfile, calibration)).isEmpty()

        val f2 = createHandFrame(tsNanos = 200_000_000L, x = 0.46f, y = 0.50f)
        val events = interpreter.onFrame(f2, subwayProfile, calibration)
        assertThat(events).hasSize(1)
        val swipe = events[0] as Swipe
        assertThat(swipe.dir).isEqualTo(Dir.LEFT)
        assertThat(swipe.speed).isAtLeast(0.18f)
    }

    @Test
    fun test13_subwaySurfersProfileBoundaryDistanceRejection() {
        val subwayProfile = profile.copy(
            packageName = "com.kiloo.subwaysurf",
            params = ProfileParams(cooldownMs = 350L, swipeMinSpeed = 0.18f)
        )
        val f1 = createHandFrame(tsNanos = 0L, x = 0.50f, y = 0.50f)
        interpreter.onFrame(f1, subwayProfile, calibration)

        val f2 = createHandFrame(tsNanos = 100_000_000L, x = 0.47f, y = 0.50f)
        val events = interpreter.onFrame(f2, subwayProfile, calibration)
        assertThat(events).isEmpty()
    }

    @Test
    fun test14_subwaySurfersProfileBoundarySpeedRejection() {
        val subwayProfile = profile.copy(
            packageName = "com.kiloo.subwaysurf",
            params = ProfileParams(cooldownMs = 350L, swipeMinSpeed = 0.18f)
        )
        val f1 = createHandFrame(tsNanos = 0L, x = 0.50f, y = 0.50f)
        interpreter.onFrame(f1, subwayProfile, calibration)

        val f2 = createHandFrame(tsNanos = 280_000_000L, x = 0.46f, y = 0.50f)
        val events = interpreter.onFrame(f2, subwayProfile, calibration)
        assertThat(events).isEmpty()
    }

    @Test
    fun test15_stationaryIdleHandNoiseRejection() {
        val subwayProfile = profile.copy(
            packageName = "com.kiloo.subwaysurf",
            params = ProfileParams(cooldownMs = 350L, swipeMinSpeed = 0.18f)
        )
        val basePos = 0.50f
        val jitterOffsets = listOf(0.001f, -0.002f, 0.003f, -0.001f, 0.004f, -0.003f, 0.000f, 0.002f)
        for (i in 0 until 60) {
            val ts = i * 66_666_666L
            val jitterX = basePos + jitterOffsets[i % jitterOffsets.size]
            val jitterY = basePos + jitterOffsets[(i + 3) % jitterOffsets.size]
            val frame = createHandFrame(tsNanos = ts, x = jitterX, y = jitterY)
            val events = interpreter.onFrame(frame, subwayProfile, calibration)
            assertThat(events).isEmpty()
        }
    }

    @Test
    fun test16_subwaySurfers300msWindowCapturesDisplacementAt15Fps() {
        val subwayProfile = profile.copy(
            packageName = "com.kiloo.subwaysurf",
            params = ProfileParams(cooldownMs = 350L, swipeMinSpeed = 0.18f)
        )
        var events = emptyList<com.zorbi.core.GestureEvent>()
        for (i in 0..3) {
            val ts = i * 66_666_666L
            val x = 0.50f + (i * 0.0167f)
            val frame = createHandFrame(tsNanos = ts, x = x, y = 0.50f)
            val currentEvents = interpreter.onFrame(frame, subwayProfile, calibration)
            if (currentEvents.isNotEmpty()) {
                events = currentEvents
            }
        }
        assertThat(events).hasSize(1)
        assertThat((events[0] as Swipe).dir).isEqualTo(Dir.RIGHT)
    }
}

