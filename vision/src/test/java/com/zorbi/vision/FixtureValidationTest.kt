package com.zorbi.vision

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.HandsFrame
import com.zorbi.core.PerfMode
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.vision.recorder.LandmarkJsonl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

/**
 * Validation test suite for the 3 real physical-device landmark fixtures (P1-2).
 *
 * Verifies:
 * - All 3 fixture files exist on disk in the assets directory.
 * - Every line adheres to the frozen Section 5.3 JSONL format.
 * - Monotonic nanosecond timestamps, HAND_SINGLE tracker ID, and exactly 21 landmarks per hand.
 * - All 3 fixtures replay cleanly through [ReplayTracker] with exact frame counts and 0 dropped frames.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FixtureValidationTest {

    private val fixturesDir = File("src/main/assets/fixtures")

    private fun getFixtureFile(fileName: String): File {
        val file = File(fixturesDir, fileName)
        if (file.exists()) return file

        // Fallback for different working directory roots
        val rootFile = File("vision/src/main/assets/fixtures", fileName)
        if (rootFile.exists()) return rootFile

        throw AssertionError("Fixture file '$fileName' not found at ${file.absolutePath} or ${rootFile.absolutePath}")
    }

    private fun validateFixtureFile(file: File, expectedMinFrames: Int): List<HandsFrame> {
        assertThat(file.exists()).isTrue()
        assertThat(file.length()).isGreaterThan(0L)

        val lines = file.readLines().filter { it.isNotBlank() }
        assertThat(lines.size).isAtLeast(expectedMinFrames)

        var lastTimestamp = -1L
        val parsedFrames = mutableListOf<HandsFrame>()

        for ((index, line) in lines.withIndex()) {
            val record = LandmarkJsonl.deserializeRecord(line)

            assertThat(record.trackerId).isEqualTo(TrackerId.HAND_SINGLE)
            assertThat(record.timestampNanos).isGreaterThan(0L)

            // Monotonic timestamp check
            if (lastTimestamp >= 0L) {
                assertThat(record.timestampNanos).isAtLeast(lastTimestamp)
            }
            lastTimestamp = record.timestampNanos

            for (hand in record.frame.hands) {
                assertThat(hand.score).isAtLeast(0.0f)
                assertThat(hand.score).isAtMost(1.0f)
                assertThat(hand.landmarks).hasSize(21)

                for (pt in hand.landmarks) {
                    assertThat(pt.x.isNaN()).isFalse()
                    assertThat(pt.y.isNaN()).isFalse()
                    assertThat(pt.z.isNaN()).isFalse()
                }
            }

            parsedFrames.add(record.frame)
        }

        return parsedFrames
    }

    @Test
    fun validateNeutralHandFixture_fileFormatAndReplay() = runTest {
        val file = getFixtureFile("neutral_hand.jsonl")
        val parsedFrames = validateFixtureFile(file, expectedMinFrames = 20)

        assertThat(parsedFrames).hasSize(53)

        // Verify replay through ReplayTracker
        val tracker = ReplayTracker.fromFile(
            file = file,
            id = TrackerId.HAND_SINGLE,
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

        assertThat(replayed).hasSize(53)
        assertThat(tracker.stats.value.dropped).isEqualTo(0)
    }

    @Test
    fun validateSwipeLeftFixture_fileFormatAndReplay() = runTest {
        val file = getFixtureFile("swipe_left.jsonl")
        val parsedFrames = validateFixtureFile(file, expectedMinFrames = 15)

        assertThat(parsedFrames).hasSize(60)

        // Verify replay through ReplayTracker
        val tracker = ReplayTracker.fromFile(
            file = file,
            id = TrackerId.HAND_SINGLE,
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

        assertThat(replayed).hasSize(60)
        assertThat(tracker.stats.value.dropped).isEqualTo(0)
    }

    @Test
    fun validateSwipeRightFixture_fileFormatAndReplay() = runTest {
        val file = getFixtureFile("swipe_right.jsonl")
        val parsedFrames = validateFixtureFile(file, expectedMinFrames = 15)

        assertThat(parsedFrames).hasSize(38)

        // Verify replay through ReplayTracker
        val tracker = ReplayTracker.fromFile(
            file = file,
            id = TrackerId.HAND_SINGLE,
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

        assertThat(replayed).hasSize(38)
        assertThat(tracker.stats.value.dropped).isEqualTo(0)
    }
}

