package com.zorbi.vision

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PerfMode
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerStats
import com.zorbi.vision.internal.CoordinateTransforms
import com.zorbi.vision.perf.PerfController
import com.zorbi.vision.recorder.LandmarkJsonl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.atan2

@OptIn(ExperimentalCoroutinesApi::class)
class HandDualWheelTest {

    private fun create21Landmarks(wristX: Float, wristY: Float): List<Pt> {
        return (0 until 21).map { index ->
            Pt(x = (wristX + index * 0.005f).coerceIn(0f, 1f), y = (wristY + index * 0.005f).coerceIn(0f, 1f), z = 0f)
        }
    }

    // -------------------------------------------------------------------------
    // 1. Dual-Hand Contract & Structure Verification
    // -------------------------------------------------------------------------

    @Test
    fun dualHandFrame_supportsTwoSimultaneousHandsWith21LandmarksEach() {
        val leftWrist = Pt(0.25f, 0.5f, 0f)
        val rightWrist = Pt(0.75f, 0.5f, 0f)

        val leftHand = Hand(
            side = Side.LEFT,
            score = 0.95f,
            landmarks = create21Landmarks(leftWrist.x, leftWrist.y)
        )
        val rightHand = Hand(
            side = Side.RIGHT,
            score = 0.92f,
            landmarks = create21Landmarks(rightWrist.x, rightWrist.y)
        )

        val frame = HandsFrame(
            tsNanos = 1_000_000_000L,
            hands = listOf(leftHand, rightHand)
        )

        assertThat(frame.hands).hasSize(2)
        assertThat(frame.hands[0].side).isEqualTo(Side.LEFT)
        assertThat(frame.hands[0].landmarks).hasSize(21)
        assertThat(frame.hands[1].side).isEqualTo(Side.RIGHT)
        assertThat(frame.hands[1].landmarks).hasSize(21)
    }

    // -------------------------------------------------------------------------
    // 2. Section 5.3 JSONL Serialization & Replay of HAND_DUAL_WHEEL
    // -------------------------------------------------------------------------

    @Test
    fun jsonl_serializesAndDeserializesHandDualWheelRecord() {
        val leftHand = Hand(Side.LEFT, 0.94f, create21Landmarks(0.2f, 0.5f))
        val rightHand = Hand(Side.RIGHT, 0.91f, create21Landmarks(0.8f, 0.5f))
        val originalFrame = HandsFrame(123456789L, listOf(leftHand, rightHand))

        // Serialize line with tracker = "HAND_DUAL_WHEEL"
        val line = LandmarkJsonl.serialize(originalFrame, TrackerId.HAND_DUAL_WHEEL)
        assertThat(line).contains("\"tracker\":\"HAND_DUAL_WHEEL\"")
        assertThat(line).contains("\"side\":\"LEFT\"")
        assertThat(line).contains("\"side\":\"RIGHT\"")

        // Deserialize line
        val record = LandmarkJsonl.deserializeRecord(line)
        assertThat(record.trackerId).isEqualTo(TrackerId.HAND_DUAL_WHEEL)
        assertThat(record.timestampNanos).isEqualTo(123456789L)
        assertThat(record.frame.hands).hasSize(2)
        assertThat(record.frame.hands[0].side).isEqualTo(Side.LEFT)
        assertThat(record.frame.hands[1].side).isEqualTo(Side.RIGHT)
        assertThat(record.frame.hands[0].landmarks).hasSize(21)
        assertThat(record.frame.hands[1].landmarks).hasSize(21)
    }

    @Test
    fun replayTracker_replaysHandDualWheelFramesAccurately() = runTest {
        val leftHand = Hand(Side.LEFT, 0.95f, create21Landmarks(0.3f, 0.4f))
        val rightHand = Hand(Side.RIGHT, 0.93f, create21Landmarks(0.7f, 0.4f))
        val frame = HandsFrame(100_000_000L, listOf(leftHand, rightHand))

        val jsonlLine = LandmarkJsonl.serialize(frame, TrackerId.HAND_DUAL_WHEEL)

        val replayTracker = ReplayTracker(
            jsonContent = jsonlLine,
            id = TrackerId.HAND_DUAL_WHEEL,
            replayMode = ReplayTracker.ReplayMode.INSTANT,
            coroutineScope = this
        )

        assertThat(replayTracker.id).isEqualTo(TrackerId.HAND_DUAL_WHEEL)

        val replayed = mutableListOf<HandsFrame>()
        val collectJob = launch {
            replayTracker.frames.collect { if (it is HandsFrame) replayed.add(it) }
        }
        advanceUntilIdle()

        replayTracker.start(PerfMode.QUALITY)
        replayTracker.awaitCompletion()
        collectJob.cancel()

        assertThat(replayed).hasSize(1)
        val emitted = replayed.first()
        assertThat(emitted.tsNanos).isEqualTo(100_000_000L)
        assertThat(emitted.hands).hasSize(2)
        assertThat(emitted.hands[0].side).isEqualTo(Side.LEFT)
        assertThat(emitted.hands[1].side).isEqualTo(Side.RIGHT)
    }

    // -------------------------------------------------------------------------
    // 3. Coordinate Orientation & Mirroring Verification
    // -------------------------------------------------------------------------

    @Test
    fun coordinateTransforms_mirrorsBothHandsCorrectlyForFrontCamera() {
        // MediaPipe raw x: user's right hand in camera image is on the left (x = 0.2)
        // With front-camera horizontal mirroring: (1.0 - 0.2) = 0.8 -> appears on the right
        val rightHandCamX = 0.2f
        val leftHandCamX = 0.8f

        val rightPtMirrored = CoordinateTransforms.toPt(rightHandCamX, 0.5f, mirrorHorizontally = true)
        val leftPtMirrored = CoordinateTransforms.toPt(leftHandCamX, 0.5f, mirrorHorizontally = true)

        assertThat(rightPtMirrored.x).isWithin(1e-5f).of(0.8f)
        assertThat(leftPtMirrored.x).isWithin(1e-5f).of(0.2f)

        // User's left hand is on the left (x ~ 0.2), user's right hand is on the right (x ~ 0.8)
        assertThat(leftPtMirrored.x).isLessThan(rightPtMirrored.x)
    }

    // -------------------------------------------------------------------------
    // 4. Two-Hand Steering Wheel Geometry Verification
    // -------------------------------------------------------------------------

    @Test
    fun dualWheelGeometry_computesWristAngleAccurately() {
        // Wrist 0 (index 0) of left hand and right hand
        // Neutral horizontal hands: Left wrist at (0.2, 0.5), Right wrist at (0.8, 0.5)
        val neutralLeftWrist = Pt(0.2f, 0.5f)
        val neutralRightWrist = Pt(0.8f, 0.5f)

        val neutralAngleRad = atan2(
            (neutralRightWrist.y - neutralLeftWrist.y).toDouble(),
            (neutralRightWrist.x - neutralLeftWrist.x).toDouble()
        )
        val neutralAngleDeg = Math.toDegrees(neutralAngleRad)
        assertThat(neutralAngleDeg).isWithin(0.01).of(0.0)

        // Steer Right (clockwise): Right hand moves down (y=0.7), Left hand moves up (y=0.3)
        val rightTurnLeftWrist = Pt(0.2f, 0.3f)
        val rightTurnRightWrist = Pt(0.8f, 0.7f)

        val turnRightAngleRad = atan2(
            (rightTurnRightWrist.y - rightTurnLeftWrist.y).toDouble(),
            (rightTurnRightWrist.x - rightTurnLeftWrist.x).toDouble()
        )
        val turnRightAngleDeg = Math.toDegrees(turnRightAngleRad)
        // Clockwise turn produces positive angle per Section 5 conventions
        assertThat(turnRightAngleDeg).isGreaterThan(10.0)
    }

    // -------------------------------------------------------------------------
    // 5. Dual-Hand Workload & PerfController Handling
    // -------------------------------------------------------------------------

    @Test
    fun dualHandWorkload_triggersPerformanceWhenExceedingBudget() {
        // As measured in Master Prompt Page 28, two-hand inference latency is ~119ms p50.
        // PerfController should safely drop to PERFORMANCE mode when dual hand inference latency exceeds budget.
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f,
            dropConsecutiveSamples = 2
        )

        val heavyDualHandStats = TrackerStats(
            fps = 11f,
            inferenceMsP50 = 119f,
            inferenceMsP95 = 135f,
            dropped = 2
        )

        controller.onStatsUpdated(heavyDualHandStats)
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)

        controller.onStatsUpdated(heavyDualHandStats)
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)
        assertThat(controller.isOverloaded).isTrue()
    }
}
