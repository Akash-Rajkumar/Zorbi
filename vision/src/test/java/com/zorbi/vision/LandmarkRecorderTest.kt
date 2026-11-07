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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.StringWriter
import java.io.Writer

@OptIn(ExperimentalCoroutinesApi::class)
class LandmarkRecorderTest {

    private fun create21Landmarks(startX: Float = 0.1f): List<Pt> {
        return (0 until 21).map { i ->
            Pt(x = startX + i * 0.01f, y = 0.2f + i * 0.01f, z = 0.0f)
        }
    }

    private class TestCloseTrackingWriter(private val delegate: StringWriter = StringWriter()) : Writer() {
        var isClosed: Boolean = false
            private set
        var flushCount: Int = 0
            private set

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            delegate.write(cbuf, off, len)
        }

        override fun flush() {
            flushCount++
            delegate.flush()
        }

        override fun close() {
            isClosed = true
            delegate.close()
        }

        override fun toString(): String = delegate.toString()
    }

    private class FakeTracker(
        override val id: TrackerId = TrackerId.HAND_SINGLE,
        override val frames: MutableSharedFlow<TrackerFrame> = MutableSharedFlow(replay = 0, extraBufferCapacity = 64),
        override val stats: StateFlow<TrackerStats> = MutableStateFlow(TrackerStats(0f, 0f, 0f, 0))
    ) : Tracker {
        var isStarted: Boolean = false
        override suspend fun start(perf: PerfMode) { isStarted = true }
        override suspend fun stop() { isStarted = false }
    }

    @Test
    fun record_multipleFrames_writtenAsJsonlWithCorrectCount() = runTest {
        val fakeTracker = FakeTracker()
        val writer = TestCloseTrackingWriter()
        val recorder = LandmarkRecorder(defaultTracker = fakeTracker, coroutineScope = this)

        recorder.startRecording(destination = writer)
        advanceUntilIdle()
        assertThat(recorder.isRecording).isTrue()

        val frame1 = HandsFrame(
            tsNanos = 1000L,
            hands = listOf(Hand(side = Side.LEFT, score = 0.95f, landmarks = create21Landmarks(0.1f)))
        )
        val frame2 = HandsFrame(
            tsNanos = 2000L,
            hands = listOf(Hand(side = Side.RIGHT, score = 0.91f, landmarks = create21Landmarks(0.2f)))
        )
        val frame3 = HandsFrame(
            tsNanos = 3000L,
            hands = emptyList()
        )

        fakeTracker.frames.emit(frame1)
        fakeTracker.frames.emit(frame2)
        fakeTracker.frames.emit(frame3)
        advanceUntilIdle()

        val count = recorder.stopRecording()

        assertThat(count).isEqualTo(3)
        assertThat(recorder.recordedFrameCount).isEqualTo(3)
        assertThat(recorder.isRecording).isFalse()

        val lines = writer.toString().trim().lines()
        assertThat(lines).hasSize(3)

        // Verify each line is valid JSONL and matches the emitted frame data exactly
        val record1 = LandmarkJsonl.deserializeRecord(lines[0])
        assertThat(record1.timestampNanos).isEqualTo(1000L)
        assertThat(record1.trackerId).isEqualTo(TrackerId.HAND_SINGLE)
        assertThat(record1.frame.hands).hasSize(1)
        assertThat(record1.frame.hands[0].side).isEqualTo(Side.LEFT)

        val record2 = LandmarkJsonl.deserializeRecord(lines[1])
        assertThat(record2.timestampNanos).isEqualTo(2000L)
        assertThat(record2.frame.hands[0].side).isEqualTo(Side.RIGHT)

        val record3 = LandmarkJsonl.deserializeRecord(lines[2])
        assertThat(record3.timestampNanos).isEqualTo(3000L)
        assertThat(record3.frame.hands).isEmpty()
    }

    @Test
    fun record_preservesTimestampsAndFrameDataExactly() = runTest {
        val fakeTracker = FakeTracker()
        val writer = StringWriter()
        val recorder = LandmarkRecorder(defaultTracker = fakeTracker, coroutineScope = this)

        recorder.startRecording(destination = writer)
        advanceUntilIdle()

        val landmarks = create21Landmarks(0.15f)
        val originalFrame = HandsFrame(
            tsNanos = 1730368800000000000L,
            hands = listOf(Hand(side = Side.LEFT, score = 0.89f, landmarks = landmarks))
        )

        fakeTracker.frames.emit(originalFrame)
        advanceUntilIdle()
        recorder.stopRecording()

        val lines = writer.toString().trim().lines()
        assertThat(lines).hasSize(1)

        val deserialized = LandmarkJsonl.deserialize(lines[0])
        assertThat(deserialized.tsNanos).isEqualTo(originalFrame.tsNanos)
        assertThat(deserialized.hands).hasSize(1)
        assertThat(deserialized.hands[0].side).isEqualTo(Side.LEFT)
        assertThat(deserialized.hands[0].score).isEqualTo(0.89f)

        for (i in 0 until 21) {
            assertThat(deserialized.hands[0].landmarks[i].x).isEqualTo(landmarks[i].x)
            assertThat(deserialized.hands[0].landmarks[i].y).isEqualTo(landmarks[i].y)
            assertThat(deserialized.hands[0].landmarks[i].z).isEqualTo(landmarks[i].z)
        }
    }

    @Test
    fun record_noFramesWrittenAfterStop() = runTest {
        val fakeTracker = FakeTracker()
        val writer = StringWriter()
        val recorder = LandmarkRecorder(defaultTracker = fakeTracker, coroutineScope = this)

        recorder.startRecording(destination = writer)
        advanceUntilIdle()

        val frame1 = HandsFrame(tsNanos = 100L, hands = emptyList())
        fakeTracker.frames.emit(frame1)
        advanceUntilIdle()

        val stoppedCount = recorder.stopRecording()
        assertThat(stoppedCount).isEqualTo(1)

        val frameAfterStop1 = HandsFrame(tsNanos = 200L, hands = emptyList())
        val frameAfterStop2 = HandsFrame(tsNanos = 300L, hands = emptyList())
        fakeTracker.frames.emit(frameAfterStop1)
        fakeTracker.frames.emit(frameAfterStop2)
        advanceUntilIdle()

        val lines = writer.toString().trim().lines()
        assertThat(lines).hasSize(1)
        assertThat(recorder.recordedFrameCount).isEqualTo(1)
    }

    @Test
    fun record_repeatedStartStopUsage_handledSafely() = runTest {
        val fakeTracker = FakeTracker()
        val recorder = LandmarkRecorder(defaultTracker = fakeTracker, coroutineScope = this)

        // Session 1
        val writer1 = StringWriter()
        recorder.startRecording(destination = writer1)
        advanceUntilIdle()

        fakeTracker.frames.emit(HandsFrame(tsNanos = 10L, hands = emptyList()))
        fakeTracker.frames.emit(HandsFrame(tsNanos = 20L, hands = emptyList()))
        advanceUntilIdle()

        val count1 = recorder.stopRecording()
        assertThat(count1).isEqualTo(2)
        assertThat(writer1.toString().trim().lines()).hasSize(2)

        // Session 2
        val writer2 = StringWriter()
        recorder.startRecording(destination = writer2)
        advanceUntilIdle()
        assertThat(recorder.recordedFrameCount).isEqualTo(0) // Reset for new session

        fakeTracker.frames.emit(HandsFrame(tsNanos = 30L, hands = emptyList()))
        fakeTracker.frames.emit(HandsFrame(tsNanos = 40L, hands = emptyList()))
        fakeTracker.frames.emit(HandsFrame(tsNanos = 50L, hands = emptyList()))
        advanceUntilIdle()

        val count2 = recorder.stopRecording()
        assertThat(count2).isEqualTo(3)
        assertThat(writer2.toString().trim().lines()).hasSize(3)
    }

    @Test
    fun record_flushesOutputWithoutClosingDestinationWriter() = runTest {
        val fakeTracker = FakeTracker()
        val writer = TestCloseTrackingWriter()
        val recorder = LandmarkRecorder(defaultTracker = fakeTracker, coroutineScope = this)

        recorder.startRecording(destination = writer)
        advanceUntilIdle()

        fakeTracker.frames.emit(HandsFrame(tsNanos = 1L, hands = emptyList()))
        advanceUntilIdle()

        assertThat(writer.flushCount).isAtLeast(1)
        assertThat(writer.isClosed).isFalse()

        recorder.stopRecording()

        assertThat(writer.flushCount).isAtLeast(2)
        assertThat(writer.isClosed).isFalse() // Destination must NOT be closed by recorder
    }

    @Test
    fun startRecording_whenAlreadyRecording_throwsIllegalStateException() = runTest {
        val fakeTracker = FakeTracker()
        val writer = StringWriter()
        val recorder = LandmarkRecorder(defaultTracker = fakeTracker, coroutineScope = this)

        recorder.startRecording(destination = writer)
        advanceUntilIdle()

        assertThrows(IllegalStateException::class.java) {
            recorder.startRecording(destination = StringWriter())
        }

        recorder.stopRecording()
    }

    @Test
    fun stopRecording_whenNotRecording_isSafeNoOp() {
        val recorder = LandmarkRecorder()
        val count = recorder.stopRecording()
        assertThat(count).isEqualTo(0)
        assertThat(recorder.isRecording).isFalse()
    }

    @Test
    fun startRecording_withoutTracker_throwsIllegalArgumentException() {
        val recorder = LandmarkRecorder(defaultTracker = null)
        assertThrows(IllegalArgumentException::class.java) {
            recorder.startRecording(destination = StringWriter())
        }
    }
}
