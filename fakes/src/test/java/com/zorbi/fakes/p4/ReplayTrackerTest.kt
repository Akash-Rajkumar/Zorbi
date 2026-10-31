package com.zorbi.fakes.p4

import com.zorbi.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException

class ReplayTrackerTest {

    private fun resolveFixtureFile(filename: String): File {
        val candidates = listOf(
            File("fixtures", filename),
            File("../fixtures", filename),
            File("../../fixtures", filename)
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: throw FileNotFoundException("Could not find fixture '$filename' in: ${candidates.map { it.absolutePath }}")
    }

    private val validHandJson = """
        {"t":1000000000,"tracker":"HAND_SINGLE","hands":[{"side":"RIGHT","score":0.99,"lm":[
            {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
            {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
            {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
            {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
            {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
            {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
            {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0}
        ]}]}
    """.trimIndent()

    private val validDualWheelJson = """
        {"t":1000000000,"tracker":"HAND_DUAL_WHEEL","hands":[
            {"side":"LEFT","score":0.98,"lm":[
                {"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},
                {"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},
                {"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},
                {"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},
                {"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},
                {"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},
                {"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0},{"x":0.3,"y":0.6,"z":0.0}
            ]},
            {"side":"RIGHT","score":0.98,"lm":[
                {"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},
                {"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},
                {"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},
                {"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},
                {"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},
                {"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},
                {"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0},{"x":0.7,"y":0.6,"z":0.0}
            ]}
        ]}
    """.trimIndent()

    private val validPoseJson = """
        {"t":1000000000,"tracker":"POSE_LITE","score":0.95,"lm":[
            {"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},
            {"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},
            {"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},
            {"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},
            {"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},
            {"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},
            {"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},
            {"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},
            {"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},
            {"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},
            {"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0},{"x":0.5,"y":0.2,"z":0.0}
        ]}
    """.trimIndent()

    // -------------------------------------------------------------------------
    // 1. Positive Parsing Tests
    // -------------------------------------------------------------------------
    @Test
    fun parseJsonlLine_parsesValidHandFrame() {
        val frame = ReplayTracker.parseJsonlLine(validHandJson, 1)
        assertTrue(frame is HandsFrame)
        val handsFrame = frame as HandsFrame
        assertEquals(1000000000L, handsFrame.tsNanos)
        assertEquals(1, handsFrame.hands.size)
        val hand = handsFrame.hands[0]
        assertEquals(Side.RIGHT, hand.side)
        assertEquals(0.99f, hand.score, 0.001f)
        assertEquals(21, hand.landmarks.size)
    }

    @Test
    fun parseJsonlLine_parsesValidDualWheelFrame() {
        val frame = ReplayTracker.parseJsonlLine(validDualWheelJson, 1)
        assertTrue(frame is HandsFrame)
        val handsFrame = frame as HandsFrame
        assertEquals(1000000000L, handsFrame.tsNanos)
        assertEquals(2, handsFrame.hands.size)
        assertEquals(Side.LEFT, handsFrame.hands[0].side)
        assertEquals(Side.RIGHT, handsFrame.hands[1].side)
        assertEquals(21, handsFrame.hands[0].landmarks.size)
        assertEquals(21, handsFrame.hands[1].landmarks.size)
    }

    @Test
    fun parseJsonlLine_parsesValidPoseFrame() {
        val frame = ReplayTracker.parseJsonlLine(validPoseJson, 1)
        assertTrue(frame is PoseFrame)
        val poseFrame = frame as PoseFrame
        assertEquals(1000000000L, poseFrame.tsNanos)
        assertEquals(0.95f, poseFrame.score, 0.001f)
        assertEquals(33, poseFrame.landmarks.size)
    }

    // -------------------------------------------------------------------------
    // 2. Actual Fixture Integration Tests (All 5 Files)
    // -------------------------------------------------------------------------
    @Test
    fun loadFixture_swipe_verifiesContractAndCounts() {
        val file = resolveFixtureFile("swipe.jsonl")
        val tracker = ReplayTracker(TrackerId.HAND_SINGLE)
        val frames = tracker.loadJsonl(file.reader(Charsets.UTF_8))

        assertEquals(30, frames.size)
        var prevT = -1L
        for (f in frames) {
            assertTrue(f is HandsFrame)
            val hf = f as HandsFrame
            assertTrue(hf.tsNanos > prevT)
            prevT = hf.tsNanos
            assertEquals(1, hf.hands.size)
            assertEquals(Side.RIGHT, hf.hands[0].side)
            assertEquals(21, hf.hands[0].landmarks.size)
        }
    }

    @Test
    fun loadFixture_slash_verifiesContractAndCounts() {
        val file = resolveFixtureFile("slash.jsonl")
        val tracker = ReplayTracker(TrackerId.HAND_SINGLE)
        val frames = tracker.loadJsonl(file.reader(Charsets.UTF_8))

        assertEquals(30, frames.size)
        var prevT = -1L
        for (f in frames) {
            assertTrue(f is HandsFrame)
            val hf = f as HandsFrame
            assertTrue(hf.tsNanos > prevT)
            prevT = hf.tsNanos
            assertEquals(1, hf.hands.size)
            assertEquals(Side.RIGHT, hf.hands[0].side)
            assertEquals(21, hf.hands[0].landmarks.size)
        }
    }

    @Test
    fun loadFixture_wheel_verifiesContractAndCounts() {
        val file = resolveFixtureFile("wheel.jsonl")
        val tracker = ReplayTracker(TrackerId.HAND_DUAL_WHEEL)
        val frames = tracker.loadJsonl(file.reader(Charsets.UTF_8))

        assertEquals(60, frames.size)
        var prevT = -1L
        for (f in frames) {
            assertTrue(f is HandsFrame)
            val hf = f as HandsFrame
            assertTrue(hf.tsNanos > prevT)
            prevT = hf.tsNanos
            assertEquals(2, hf.hands.size)
            assertEquals(Side.LEFT, hf.hands[0].side)
            assertEquals(Side.RIGHT, hf.hands[1].side)
            assertEquals(21, hf.hands[0].landmarks.size)
            assertEquals(21, hf.hands[1].landmarks.size)
        }
    }

    @Test
    fun loadFixture_lean_verifiesContractAndCounts() {
        val file = resolveFixtureFile("lean.jsonl")
        val tracker = ReplayTracker(TrackerId.POSE_LITE)
        val frames = tracker.loadJsonl(file.reader(Charsets.UTF_8))

        assertEquals(60, frames.size)
        var prevT = -1L
        for (f in frames) {
            assertTrue(f is PoseFrame)
            val pf = f as PoseFrame
            assertTrue(pf.tsNanos > prevT)
            prevT = pf.tsNanos
            assertEquals(33, pf.landmarks.size)
        }
    }

    @Test
    fun loadFixture_fist_verifiesContractAndCounts() {
        val file = resolveFixtureFile("fist.jsonl")
        val tracker = ReplayTracker(TrackerId.HAND_SINGLE)
        val frames = tracker.loadJsonl(file.reader(Charsets.UTF_8))

        assertEquals(50, frames.size)
        var prevT = -1L
        for (f in frames) {
            assertTrue(f is HandsFrame)
            val hf = f as HandsFrame
            assertTrue(hf.tsNanos > prevT)
            prevT = hf.tsNanos
            assertEquals(1, hf.hands.size)
            assertEquals(Side.RIGHT, hf.hands[0].side)
            assertEquals(21, hf.hands[0].landmarks.size)
        }
    }

    // -------------------------------------------------------------------------
    // 3. Negative Parser Tests
    // -------------------------------------------------------------------------
    @Test
    fun parseJsonlLine_rejectsMissingTimestamp() {
        val json = validHandJson.replace("\"t\":1000000000,", "")
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on missing timestamp")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing or invalid timestamp") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsMissingTracker() {
        val json = validHandJson.replace("\"tracker\":\"HAND_SINGLE\",", "")
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on missing tracker")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing or invalid 'tracker'") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsMissingHands() {
        val json = """{"t":1000000000,"tracker":"HAND_SINGLE"}"""
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on missing hands")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Missing or invalid 'hands'") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsInvalidSide() {
        val json = validHandJson.replace("\"side\":\"RIGHT\"", "\"side\":\"UPPER\"")
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on invalid side")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("invalid side 'UPPER'") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsInvalidConfidenceScore() {
        val json = validHandJson.replace("\"score\":0.99", "\"score\":\"high\"")
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on invalid score type")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("missing 'score'") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsIncorrectDualWheelHandCount() {
        // HAND_DUAL_WHEEL with only 1 hand
        val json = validHandJson.replace("\"tracker\":\"HAND_SINGLE\"", "\"tracker\":\"HAND_DUAL_WHEEL\"")
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on dual wheel with 1 hand")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("HAND_DUAL_WHEEL requires exactly 2 hands") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsMalformedJson() {
        val json = """{"t":1000000000,"tracker":"HAND_SINGLE","hands":"""
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on malformed JSON")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Malformed JSON") == true)
        }
    }

    @Test
    fun parseJsonlLines_rejectsDecreasingTimestamps() {
        val line1 = """{"t":2000000000,"tracker":"POSE_LITE","score":0.95,"lm":${validPoseJson.substringAfter("\"lm\":")}}"""
        val line2 = """{"t":1000000000,"tracker":"POSE_LITE","score":0.95,"lm":${validPoseJson.substringAfter("\"lm\":")}}"""
        try {
            ReplayTracker.parseJsonlLines(sequenceOf(line1, line2))
            fail("Expected exception on decreasing timestamp")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Non-monotonic timestamp") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsIncorrectHandPointCount() {
        val badHandJson = """
            {"t":1000000000,"tracker":"HAND_SINGLE","hands":[{"side":"RIGHT","score":0.99,"lm":[
                {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
                {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
                {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
                {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
                {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
                {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0},
                {"x":0.5,"y":0.5,"z":0.0},{"x":0.5,"y":0.5,"z":0.0}
            ]}]}
        """.trimIndent()

        try {
            ReplayTracker.parseJsonlLine(badHandJson, 1)
            fail("Expected IllegalArgumentException on 20 hand landmarks")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("landmark count must be exactly 21") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsOutOfBoundsCoordinates() {
        val oobJson = validHandJson.replace("\"x\":0.5", "\"x\":1.42")
        try {
            ReplayTracker.parseJsonlLine(oobJson, 1)
            fail("Expected IllegalArgumentException on out of bounds coordinate")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("out of [0, 1] bounds") == true)
        }
    }

    // -------------------------------------------------------------------------
    // 4. Thread-Safety & Lifecycle Tests
    // -------------------------------------------------------------------------
    @Test
    fun replayTracker_threadSafeLoadAndAccess() {
        val tracker = ReplayTracker(TrackerId.HAND_SINGLE)
        val threads = (1..5).map {
            Thread {
                tracker.loadJsonl(validHandJson)
                val snapshot = tracker.loadedFrames
                assertEquals(1, snapshot.size)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(1, tracker.loadedFrames.size)
    }
}
