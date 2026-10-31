package com.zorbi.vision

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.PerfMode
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerStats
import com.zorbi.vision.internal.CoordinateTransforms
import com.zorbi.vision.perf.PerfController
import com.zorbi.vision.recorder.LandmarkJsonl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.atan2

@OptIn(ExperimentalCoroutinesApi::class)
class PoseLiteTest {

    private fun create33PoseLandmarks(
        leftShoulderX: Float = 0.4f,
        leftShoulderY: Float = 0.3f,
        rightShoulderX: Float = 0.6f,
        rightShoulderY: Float = 0.3f
    ): List<Pt> {
        return (0 until 33).map { index ->
            when (index) {
                11 -> Pt(x = leftShoulderX, y = leftShoulderY, z = 0f)
                12 -> Pt(x = rightShoulderX, y = rightShoulderY, z = 0f)
                else -> Pt(
                    x = (0.5f + (index - 16) * 0.01f).coerceIn(0f, 1f),
                    y = (0.5f + (index - 16) * 0.01f).coerceIn(0f, 1f),
                    z = 0f
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // 1. PoseFrame Contract & Structure Verification
    // -------------------------------------------------------------------------

    @Test
    fun poseFrame_supports33LandmarksAndScore() {
        val landmarks = create33PoseLandmarks()
        val frame = PoseFrame(
            tsNanos = 1_500_000_000L,
            score = 0.88f,
            landmarks = landmarks
        )

        assertThat(frame).isInstanceOf(TrackerFrame::class.java)
        assertThat(frame.tsNanos).isEqualTo(1_500_000_000L)
        assertThat(frame.score).isEqualTo(0.88f)
        assertThat(frame.landmarks).hasSize(33)
        assertThat(frame.landmarks[11].x).isEqualTo(0.4f)
        assertThat(frame.landmarks[12].x).isEqualTo(0.6f)
    }

    // -------------------------------------------------------------------------
    // 2. Section 5.3 JSONL Serialization & Deserialization for POSE_LITE
    // -------------------------------------------------------------------------

    @Test
    fun jsonl_serializesAndDeserializesPoseLiteRecord_Section53Format() {
        val landmarks = create33PoseLandmarks(leftShoulderX = 0.38f, rightShoulderX = 0.62f)
        val originalFrame = PoseFrame(123456790L, score = 0.88f, landmarks = landmarks)

        // Serialize according to Section 5.3:
        // {"t":123456790,"tracker":"POSE_LITE","pose":{"score":0.88,"lm":[[0.4,0.3,0.0],"...33 total"]}}
        val line = LandmarkJsonl.serialize(originalFrame, TrackerId.POSE_LITE)
        assertThat(line).contains("\"t\":123456790")
        assertThat(line).contains("\"tracker\":\"POSE_LITE\"")
        assertThat(line).contains("\"score\":0.88")
        assertThat(line).contains("\"lm\":")

        // Deserialize line
        val record = LandmarkJsonl.deserializeRecord(line)
        assertThat(record.trackerId).isEqualTo(TrackerId.POSE_LITE)
        assertThat(record.timestampNanos).isEqualTo(123456790L)

        val deserializedPose = record.poseFrame
        assertThat(deserializedPose).isNotNull()
        assertThat(deserializedPose!!.tsNanos).isEqualTo(123456790L)
        assertThat(deserializedPose.score).isEqualTo(0.88f)
        assertThat(deserializedPose.landmarks).hasSize(33)
        assertThat(deserializedPose.landmarks[11].x).isEqualTo(0.38f)
        assertThat(deserializedPose.landmarks[12].x).isEqualTo(0.62f)
    }

    // -------------------------------------------------------------------------
    // 3. ReplayTracker Emits POSE_LITE Frames with Exact Timestamps
    // -------------------------------------------------------------------------

    @Test
    fun replayTracker_replaysPoseLiteFramesAccurately() = runTest {
        val frame1 = PoseFrame(100_000_000L, 0.91f, create33PoseLandmarks(0.4f, 0.3f, 0.6f, 0.3f))
        val frame2 = PoseFrame(133_000_000L, 0.93f, create33PoseLandmarks(0.41f, 0.32f, 0.61f, 0.32f))

        val lines = listOf(
            LandmarkJsonl.serialize(frame1, TrackerId.POSE_LITE),
            LandmarkJsonl.serialize(frame2, TrackerId.POSE_LITE)
        )

        val replayTracker = ReplayTracker(
            lines = lines,
            id = TrackerId.POSE_LITE,
            replayMode = ReplayTracker.ReplayMode.INSTANT,
            coroutineScope = this
        )

        val receivedFrames = mutableListOf<TrackerFrame>()
        val collectJob = launch {
            replayTracker.frames.collect { frame ->
                receivedFrames.add(frame)
            }
        }

        replayTracker.start(PerfMode.BALANCED)
        replayTracker.awaitCompletion()
        advanceUntilIdle()

        assertThat(receivedFrames).hasSize(2)
        val p1 = receivedFrames[0] as PoseFrame
        val p2 = receivedFrames[1] as PoseFrame

        assertThat(p1.tsNanos).isEqualTo(100_000_000L)
        assertThat(p1.score).isEqualTo(0.91f)
        assertThat(p1.landmarks).hasSize(33)

        assertThat(p2.tsNanos).isEqualTo(133_000_000L)
        assertThat(p2.score).isEqualTo(0.93f)
        assertThat(p2.landmarks).hasSize(33)

        collectJob.cancel()
        replayTracker.stop()
    }

    // -------------------------------------------------------------------------
    // 4. Body Lean Calculation (Shoulder Tilt)
    // -------------------------------------------------------------------------

    @Test
    fun bodyLean_computesCorrectTiltDirection() {
        // Level shoulders
        val neutralPose = create33PoseLandmarks(leftShoulderX = 0.4f, leftShoulderY = 0.5f, rightShoulderX = 0.6f, rightShoulderY = 0.5f)
        val neutralAngle = Math.toDegrees(atan2(
            (neutralPose[12].y - neutralPose[11].y).toDouble(),
            (neutralPose[12].x - neutralPose[11].x).toDouble()
        ))
        assertThat(neutralAngle).isWithin(0.01).of(0.0)

        // Leaning right: right shoulder drops below left shoulder (y increases downwards)
        val leanRightPose = create33PoseLandmarks(leftShoulderX = 0.4f, leftShoulderY = 0.45f, rightShoulderX = 0.6f, rightShoulderY = 0.55f)
        val leanRightAngle = Math.toDegrees(atan2(
            (leanRightPose[12].y - leanRightPose[11].y).toDouble(),
            (leanRightPose[12].x - leanRightPose[11].x).toDouble()
        ))
        assertThat(leanRightAngle).isGreaterThan(5.0)

        // Leaning left: left shoulder drops below right shoulder
        val leanLeftPose = create33PoseLandmarks(leftShoulderX = 0.4f, leftShoulderY = 0.55f, rightShoulderX = 0.6f, rightShoulderY = 0.45f)
        val leanLeftAngle = Math.toDegrees(atan2(
            (leanLeftPose[12].y - leanLeftPose[11].y).toDouble(),
            (leanLeftPose[12].x - leanLeftPose[11].x).toDouble()
        ))
        assertThat(leanLeftAngle).isLessThan(-5.0)
    }

    // -------------------------------------------------------------------------
    // 5. PerfController Integration with POSE_LITE
    // -------------------------------------------------------------------------

    @Test
    fun perfController_dropsToPerformanceWhenPoseInferenceExceedsBudget() = runTest {
        val fakeTracker = FakePoseTracker()
        val controller = PerfController(
            initialMode = PerfMode.QUALITY,
            p95BudgetMs = 80.0f
        )
        val job = controller.attachToTracker(fakeTracker, this)

        // Normal samples under budget (e.g. 50ms)
        fakeTracker.emitStats(TrackerStats(fps = 20f, inferenceMsP50 = 40f, inferenceMsP95 = 50f, dropped = 0))
        advanceUntilIdle()
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)

        // Overload sample 1 (> 80ms)
        fakeTracker.emitStats(TrackerStats(fps = 10f, inferenceMsP50 = 90f, inferenceMsP95 = 110f, dropped = 0))
        advanceUntilIdle()
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.QUALITY)

        // Overload sample 2: triggers drop to PERFORMANCE
        fakeTracker.emitStats(TrackerStats(fps = 10f, inferenceMsP50 = 95f, inferenceMsP95 = 115f, dropped = 0))
        advanceUntilIdle()
        assertThat(controller.currentMode.value).isEqualTo(PerfMode.PERFORMANCE)
        assertThat(fakeTracker.activeMode).isEqualTo(PerfMode.PERFORMANCE)

        job.cancel()
    }

    // -------------------------------------------------------------------------
    // Helper Fake Tracker
    // -------------------------------------------------------------------------

    private class FakePoseTracker : Tracker {
        override val id: TrackerId = TrackerId.POSE_LITE
        private val _frames = MutableSharedFlow<TrackerFrame>()
        override val frames: SharedFlow<TrackerFrame> = _frames.asSharedFlow()

        private val _stats = MutableStateFlow(TrackerStats(0f, 0f, 0f, 0))
        override val stats: StateFlow<TrackerStats> = _stats.asStateFlow()

        var activeMode: PerfMode? = null

        override suspend fun start(perf: PerfMode) {
            activeMode = perf
        }

        override suspend fun stop() {
            activeMode = null
        }

        fun emitStats(stats: TrackerStats) {
            _stats.value = stats
        }
    }
}
