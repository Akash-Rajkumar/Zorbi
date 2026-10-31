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
    // 3b. Strict Trailing-Content & Root Structure Regression Tests
    // -------------------------------------------------------------------------
    @Test
    fun parseJsonlLine_rejectsTrailingGarbage() {
        val json = "$validHandJson garbage"
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on trailing garbage")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unexpected trailing content") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsTrailingJsonObject() {
        val json = """$validHandJson {"extra": 123}"""
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on trailing JSON object")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unexpected trailing content") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsTrailingPunctuation() {
        val json = "$validHandJson ,"
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on trailing punctuation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unexpected trailing content") == true)
        }
    }

    @Test
    fun parseJsonlLine_acceptsTrailingWhitespace() {
        val json = "$validHandJson   \t \r\n  "
        val frame = ReplayTracker.parseJsonlLine(json, 1)
        assertNotNull(frame)
        assertEquals(1000000000L, frame.tsNanos)
    }

    @Test
    fun parseJsonlLine_rejectsNonObjectRoot() {
        val json = """["an", "array"]"""
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on non-object root")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Expected JSON object at root") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsUnclosedObject() {
        val json = """{"t":1000000000,"tracker":"HAND_SINGLE""""
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on unclosed JSON object")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Malformed JSON") == true)
        }
    }

    // -------------------------------------------------------------------------
    // 3c. Strict Timestamp Numeric & Range Regression Tests
    // -------------------------------------------------------------------------
    @Test
    fun parseJsonlLine_acceptsIntegerTimestamp() {
        val json = validHandJson.replace("\"t\":1000000000,", "\"t\":1700000000000000000,")
        val frame = ReplayTracker.parseJsonlLine(json, 1)
        assertEquals(1700000000000000000L, frame.tsNanos)
    }

    @Test
    fun parseJsonlLine_acceptsLongMaxTimestamp() {
        val json = validHandJson.replace("\"t\":1000000000,", "\"t\":${Long.MAX_VALUE},")
        val frame = ReplayTracker.parseJsonlLine(json, 1)
        assertEquals(Long.MAX_VALUE, frame.tsNanos)
    }

    @Test
    fun parseJsonlLine_acceptsZeroTimestamp() {
        val json = validHandJson.replace("\"t\":1000000000,", "\"t\":0,")
        val frame = ReplayTracker.parseJsonlLine(json, 1)
        assertEquals(0L, frame.tsNanos)
    }

    @Test
    fun parseJsonlLine_rejectsFractionalTimestamp() {
        val json = validHandJson.replace("\"t\":1000000000,", "\"t\":1234.5,")
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on fractional timestamp")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be a fractional number") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsNegativeTimestampMinusOne() {
        val json = validHandJson.replace("\"t\":1000000000,", "\"t\":-1,")
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on negative timestamp -1")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be negative") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsNegativeTimestamp() {
        val json = validHandJson.replace("\"t\":1000000000,", "\"t\":-100,")
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on negative timestamp")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be negative") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsOutOfRangeTimestamp() {
        // Exceeds Long.MAX_VALUE (9223372036854775807)
        val json = validHandJson.replace("\"t\":1000000000,", "\"t\":9223372036854775808,")
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on out-of-range timestamp")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("outside supported Long range") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsDecimalNotationTimestamp() {
        // Decimal representation with .0 must be rejected by integer-token contract
        val json = validHandJson.replace("\"t\":1000000000,", "\"t\":9223372036854775807.0,")
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on decimal-token timestamp")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must be an integer token") == true)
        }
    }

    @Test
    fun parseJsonlLine_rejectsExponentNotationTimestamp() {
        // Exponent notation must be rejected by integer-token contract
        val json = validHandJson.replace("\"t\":1000000000,", "\"t\":1e3,")
        try {
            ReplayTracker.parseJsonlLine(json, 1)
            fail("Expected exception on exponent-notation timestamp")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must be an integer token") == true)
        }
    }

    @Test
    fun parseJsonlLines_acceptsValidMonotonicSequence() {
        val line1 = validPoseJson
        val line2 = validPoseJson.replace("\"t\":1000000000", "\"t\":1033333333")
        val line3 = validPoseJson.replace("\"t\":1000000000", "\"t\":1066666666")
        val frames = ReplayTracker.parseJsonlLines(sequenceOf(line1, line2, line3))
        assertEquals(3, frames.size)
        assertEquals(1000000000L, frames[0].tsNanos)
        assertEquals(1033333333L, frames[1].tsNanos)
        assertEquals(1066666666L, frames[2].tsNanos)
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
