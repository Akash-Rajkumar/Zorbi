package com.zorbi.vision

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PerfMode
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerStats
import com.zorbi.vision.recorder.LandmarkJsonl
import com.zorbi.vision.recorder.LandmarkRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.StringWriter

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayTrackerTest {

    private fun create21Landmarks(startX: Float = 0.1f, startY: Float = 0.2f): List<Pt> {
        return (0 until 21).map { i ->
            Pt(x = startX + i * 0.01f, y = startY + i * 0.01f, z = 0.0f)
        }
    }

    private class FakeLiveTracker(
        override val id: TrackerId = TrackerId.HAND_SINGLE,
        override val frames: MutableSharedFlow<TrackerFrame> = MutableSharedFlow(replay = 0, extraBufferCapacity = 64),
        override val stats: StateFlow<TrackerStats> = MutableStateFlow(TrackerStats(0f, 0f, 0f, 0))
    ) : Tracker {
        override suspend fun start(perf: PerfMode) {}
        override suspend fun stop() {}
    }

    @Test
    fun replay_instantMode_preservesExactTimestampsAndCount() = runTest {
        val frame1 = HandsFrame(
            tsNanos = 100_000_000L,
            hands = listOf(Hand(side = Side.LEFT, score = 0.95f, landmarks = create21Landmarks(0.1f)))
        )
        val frame2 = HandsFrame(
            tsNanos = 200_000_000L,
            hands = listOf(Hand(side = Side.RIGHT, score = 0.92f, landmarks = create21Landmarks(0.2f)))
        )
        val frame3 = HandsFrame(
            tsNanos = 300_000_000L,
            hands = emptyList()
        )

        val jsonl = buildString {
            appendLine(LandmarkJsonl.serialize(frame1))
            appendLine(LandmarkJsonl.serialize(frame2))
            appendLine(LandmarkJsonl.serialize(frame3))
        }

        val tracker = ReplayTracker(
            jsonContent = jsonl,
            replayMode = ReplayTracker.ReplayMode.INSTANT,
            coroutineScope = this
        )

        val replayed = mutableListOf<TrackerFrame>()
        val collectJob = launch {
            tracker.frames.collect { replayed.add(it) }
        }
        advanceUntilIdle()

        tracker.start(PerfMode.QUALITY)
        tracker.awaitCompletion()
        advanceUntilIdle()
        tracker.stop()
        collectJob.cancel()

        assertThat(replayed).hasSize(3)
        assertThat(replayed[0].tsNanos).isEqualTo(100_000_000L)
        assertThat(replayed[1].tsNanos).isEqualTo(200_000_000L)
        assertThat(replayed[2].tsNanos).isEqualTo(300_000_000L)

        val hand1 = (replayed[0] as HandsFrame).hands[0]
        assertThat(hand1.side).isEqualTo(Side.LEFT)
        assertThat(hand1.score).isEqualTo(0.95f)

        val hand2 = (replayed[1] as HandsFrame).hands[0]
        assertThat(hand2.side).isEqualTo(Side.RIGHT)
        assertThat(hand2.score).isEqualTo(0.92f)

        val hand3 = (replayed[2] as HandsFrame).hands
        assertThat(hand3).isEmpty()
    }

    @Test
    fun replay_pacedMode_pacesEmissionsAccordingToTimestampDeltas() = runTest {
        // Frame 0 at 0ms, Frame 1 at 50ms, Frame 2 at 120ms (deltas: 50ms, 70ms)
        val frame0 = HandsFrame(tsNanos = 0L, hands = emptyList())
        val frame1 = HandsFrame(tsNanos = 50_000_000L, hands = emptyList())
        val frame2 = HandsFrame(tsNanos = 120_000_000L, hands = emptyList())

        val lines = listOf(
            LandmarkJsonl.serialize(frame0),
            LandmarkJsonl.serialize(frame1),
            LandmarkJsonl.serialize(frame2)
        )

        val tracker = ReplayTracker(
            lines = lines,
            replayMode = ReplayTracker.ReplayMode.REALTIME,
            coroutineScope = this
        )

        val replayed = mutableListOf<TrackerFrame>()
        val collectJob = launch {
            tracker.frames.collect { replayed.add(it) }
        }
        advanceUntilIdle()

        tracker.start(PerfMode.QUALITY)

        // At t = 0ms, frame0 should be emitted immediately
        advanceTimeBy(1)
        assertThat(replayed).hasSize(1)
        assertThat(replayed[0].tsNanos).isEqualTo(0L)

        // Advance to t = 49ms: frame1 should NOT be emitted yet
        advanceTimeBy(48)
        assertThat(replayed).hasSize(1)

        // Advance past 50ms: frame1 is emitted
        advanceTimeBy(2)
        assertThat(replayed).hasSize(2)
        assertThat(replayed[1].tsNanos).isEqualTo(50_000_000L)

        // Advance 69ms: frame2 should NOT be emitted yet
        advanceTimeBy(69)
        assertThat(replayed).hasSize(2)

        // Advance past 70ms: frame2 is emitted
        advanceTimeBy(2)
        assertThat(replayed).hasSize(3)
        assertThat(replayed[2].tsNanos).isEqualTo(120_000_000L)

        tracker.awaitCompletion()
        tracker.stop()
        collectJob.cancel()
    }

    @Test
    fun stop_cancelsPlaybackCleanlyAndPreventsFurtherEmissions() = runTest {
        // 5 frames with 100ms delta between each
        val frames = (0 until 5).map { i ->
            HandsFrame(tsNanos = i * 100_000_000L, hands = emptyList())
        }
        val lines = frames.map { LandmarkJsonl.serialize(it) }

        val tracker = ReplayTracker(
            lines = lines,
            replayMode = ReplayTracker.ReplayMode.REALTIME,
            coroutineScope = this
        )

        val replayed = mutableListOf<TrackerFrame>()
        val collectJob = launch {
            tracker.frames.collect { replayed.add(it) }
        }
        advanceUntilIdle()

        tracker.start(PerfMode.QUALITY)
        advanceTimeBy(105) // Emits frame 0 and frame 1 (at 100ms)
        assertThat(replayed).hasSize(2)

        // Stop replay
        tracker.stop()

        // Advance time significantly: no further frames should be emitted
        advanceTimeBy(1000)
        assertThat(replayed).hasSize(2)

        collectJob.cancel()
    }

    @Test
    fun stop_isSafeToCallRepeatedly() = runTest {
        val tracker = ReplayTracker(lines = emptyList(), coroutineScope = this)
        tracker.stop()
        tracker.stop()
        tracker.stop()
    }

    @Test
    fun replay_emptyInput_completesCleanlyWithZeroFrames() = runTest {
        val tracker = ReplayTracker(
            jsonContent = "",
            replayMode = ReplayTracker.ReplayMode.INSTANT,
            coroutineScope = this
        )

        val replayed = mutableListOf<TrackerFrame>()
        val collectJob = launch {
            tracker.frames.collect { replayed.add(it) }
        }
        advanceUntilIdle()

        tracker.start(PerfMode.QUALITY)
        tracker.awaitCompletion()
        tracker.stop()
        collectJob.cancel()

        assertThat(replayed).isEmpty()
        assertThat(tracker.stats.value.dropped).isEqualTo(0)
    }

    @Test
    fun replay_malformedJson_surfacesIllegalArgumentException() = runTest {
        val malformedJsonl = """{"invalid_json": true}"""
        val tracker = ReplayTracker(
            jsonContent = malformedJsonl,
            replayMode = ReplayTracker.ReplayMode.INSTANT,
            coroutineScope = this
        )

        tracker.start(PerfMode.QUALITY)

        var thrown: Throwable? = null
        try {
            tracker.awaitCompletion()
        } catch (t: Throwable) {
            thrown = t
        }

        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
        tracker.stop()
    }

    @Test
    fun replay_statsExposesZeroDroppedFramesAndComputesFps() = runTest {
        val frames = (0 until 5).map { i ->
            HandsFrame(tsNanos = i * 10_000_000L, hands = emptyList())
        }
        val lines = frames.map { LandmarkJsonl.serialize(it) }

        val tracker = ReplayTracker(
            lines = lines,
            replayMode = ReplayTracker.ReplayMode.INSTANT,
            coroutineScope = this
        )

        tracker.start(PerfMode.QUALITY)
        tracker.awaitCompletion()
        tracker.stop()

        assertThat(tracker.stats.value.dropped).isEqualTo(0)
    }

    @Test
    fun replay_restartAfterStop_replaysFromBeginning() = runTest {
        val frames = listOf(
            HandsFrame(tsNanos = 10L, hands = emptyList()),
            HandsFrame(tsNanos = 20L, hands = emptyList())
        )
        val lines = frames.map { LandmarkJsonl.serialize(it) }

        val tracker = ReplayTracker(
            lines = lines,
            replayMode = ReplayTracker.ReplayMode.INSTANT,
            coroutineScope = this
        )

        val replayed = mutableListOf<TrackerFrame>()
        val collectJob = launch {
            tracker.frames.collect { replayed.add(it) }
        }
        advanceUntilIdle()

        // Session 1
        tracker.start(PerfMode.QUALITY)
        tracker.awaitCompletion()
        tracker.stop()

        assertThat(replayed).hasSize(2)

        // Session 2 (restart)
        tracker.start(PerfMode.QUALITY)
        tracker.awaitCompletion()
        tracker.stop()

        assertThat(replayed).hasSize(4)
        collectJob.cancel()
    }

    /**
     * Official T-P1-2 Acceptance Test:
     * Record 10 simulated frames with LandmarkRecorder -> serialize to JSONL ->
     * replay through ReplayTracker -> verify output equals recorded frames in exact count and timestamps.
     */
    @Test
    fun gate_TP12_acceptanceTest_recordThenReplayMatchesExactCountAndTimestamps() = runTest {
        // 1. Generate 10 original test frames with realistic 30 FPS timestamp progression
        val originalFrames = (0 until 10).map { i ->
            val tsNanos = 1_000_000_000L + i * 33_333_333L // ~30 fps timestamps
            val landmarks = (0 until 21).map { lmIdx ->
                Pt(
                    x = (0.1f + lmIdx * 0.02f).coerceIn(0f, 1f),
                    y = (0.2f + i * 0.01f).coerceIn(0f, 1f),
                    z = 0.001f * lmIdx
                )
            }
            HandsFrame(
                tsNanos = tsNanos,
                hands = listOf(
                    Hand(
                        side = if (i % 2 == 0) Side.LEFT else Side.RIGHT,
                        score = 0.90f + i * 0.008f,
                        landmarks = landmarks
                    )
                )
            )
        }

        // 2. Record using LandmarkRecorder to a StringWriter sink
        val writer = StringWriter()
        val fakeLiveTracker = FakeLiveTracker(id = TrackerId.HAND_SINGLE)
        val recorder = LandmarkRecorder(defaultTracker = fakeLiveTracker, coroutineScope = this)

        recorder.startRecording(destination = writer)
        advanceUntilIdle()

        for (frame in originalFrames) {
            fakeLiveTracker.frames.emit(frame)
        }
        advanceUntilIdle()

        val recordedCount = recorder.stopRecording()
        assertThat(recordedCount).isEqualTo(10)

        val recordedJsonl = writer.toString()
        assertThat(recordedJsonl.trim().lines()).hasSize(10)

        // 3. Replay through ReplayTracker
        val replayTracker = ReplayTracker(
            jsonContent = recordedJsonl,
            id = TrackerId.HAND_SINGLE,
            replayMode = ReplayTracker.ReplayMode.INSTANT,
            coroutineScope = this
        )

        val replayedFrames = mutableListOf<TrackerFrame>()
        val collectJob = launch {
            replayTracker.frames.collect { replayedFrames.add(it) }
        }
        advanceUntilIdle()

        replayTracker.start(PerfMode.QUALITY)
        replayTracker.awaitCompletion()
        advanceUntilIdle()
        replayTracker.stop()
        collectJob.cancel()

        // 4. Verify T-P1-2 Acceptance Criteria: Exact count and exact timestamps
        assertThat(replayedFrames).hasSize(originalFrames.size)

        for (i in originalFrames.indices) {
            val original = originalFrames[i]
            val replayed = replayedFrames[i] as HandsFrame

            // Exact timestamp preservation
            assertThat(replayed.tsNanos).isEqualTo(original.tsNanos)

            // Exact hand count
            assertThat(replayed.hands).hasSize(original.hands.size)

            val origHand = original.hands[0]
            val replayHand = replayed.hands[0]

            // Exact side, score, and all 21 landmark coordinates
            assertThat(replayHand.side).isEqualTo(origHand.side)
            assertThat(replayHand.score).isEqualTo(origHand.score)
            assertThat(replayHand.landmarks).hasSize(21)

            for (lmIdx in 0 until 21) {
                assertThat(replayHand.landmarks[lmIdx].x).isEqualTo(origHand.landmarks[lmIdx].x)
                assertThat(replayHand.landmarks[lmIdx].y).isEqualTo(origHand.landmarks[lmIdx].y)
                assertThat(replayHand.landmarks[lmIdx].z).isEqualTo(origHand.landmarks[lmIdx].z)
            }
        }

        // Verify stats: 0 dropped frames
        assertThat(replayTracker.stats.value.dropped).isEqualTo(0)
    }
}

