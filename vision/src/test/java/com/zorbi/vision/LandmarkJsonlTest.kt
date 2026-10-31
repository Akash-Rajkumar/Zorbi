package com.zorbi.vision

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.TrackerId
import com.zorbi.vision.recorder.LandmarkJsonl
import com.zorbi.vision.recorder.LandmarkRecord
import org.junit.Assert.assertThrows
import org.junit.Test

class LandmarkJsonlTest {

    private fun create21Landmarks(startX: Float = 0.1f, startY: Float = 0.2f, startZ: Float = 0.0f): List<Pt> {
        return (0 until 21).map { i ->
            Pt(
                x = (startX + i * 0.01f),
                y = (startY + i * 0.01f),
                z = (startZ + i * 0.005f)
            )
        }
    }

    @Test
    fun serialize_matchesSection53Format() {
        val landmarks = create21Landmarks(startX = 0.1f, startY = 0.2f, startZ = 0.0f)
        val hand = Hand(side = Side.LEFT, score = 0.93f, landmarks = landmarks)
        val frame = HandsFrame(tsNanos = 123456789L, hands = listOf(hand))

        val json = LandmarkJsonl.serialize(frame, TrackerId.HAND_SINGLE)

        assertThat(json).contains("\"t\":123456789")
        assertThat(json).contains("\"tracker\":\"HAND_SINGLE\"")
        assertThat(json).contains("\"side\":\"LEFT\"")
        assertThat(json).contains("\"score\":0.93")
        assertThat(json).contains("\"lm\":[[0.1,0.2,0.0]")
    }

    @Test
    fun roundTrip_singleHand_preservesAllFieldsExactly() {
        val landmarks = create21Landmarks(startX = 0.15f, startY = 0.35f, startZ = 0.02f)
        val hand = Hand(side = Side.RIGHT, score = 0.97f, landmarks = landmarks)
        val frame = HandsFrame(tsNanos = 9876543210123L, hands = listOf(hand))

        val serialized = LandmarkJsonl.serialize(frame, TrackerId.HAND_SINGLE)
        val deserialized = LandmarkJsonl.deserialize(serialized)

        assertThat(deserialized.tsNanos).isEqualTo(frame.tsNanos)
        assertThat(deserialized.hands).hasSize(1)

        val deserializedHand = deserialized.hands[0]
        assertThat(deserializedHand.side).isEqualTo(Side.RIGHT)
        assertThat(deserializedHand.score).isEqualTo(0.97f)
        assertThat(deserializedHand.landmarks).hasSize(21)

        for (i in 0 until 21) {
            val expectedPt = landmarks[i]
            val actualPt = deserializedHand.landmarks[i]
            assertThat(actualPt.x).isEqualTo(expectedPt.x)
            assertThat(actualPt.y).isEqualTo(expectedPt.y)
            assertThat(actualPt.z).isEqualTo(expectedPt.z)
        }
    }

    @Test
    fun roundTrip_emptyHandsList_preservesEmptyFrame() {
        val frame = HandsFrame(tsNanos = 55555L, hands = emptyList())

        val serialized = LandmarkJsonl.serialize(frame, TrackerId.HAND_SINGLE)
        val record = LandmarkJsonl.deserializeRecord(serialized)

        assertThat(record.timestampNanos).isEqualTo(55555L)
        assertThat(record.trackerId).isEqualTo(TrackerId.HAND_SINGLE)
        assertThat(record.frame.tsNanos).isEqualTo(55555L)
        assertThat(record.frame.hands).isEmpty()
    }

    @Test
    fun roundTrip_dualHands_preservesBothHands() {
        val leftLandmarks = create21Landmarks(startX = 0.1f, startY = 0.2f, startZ = 0.01f)
        val rightLandmarks = create21Landmarks(startX = 0.6f, startY = 0.7f, startZ = -0.01f)

        val hand1 = Hand(side = Side.LEFT, score = 0.88f, landmarks = leftLandmarks)
        val hand2 = Hand(side = Side.RIGHT, score = 0.92f, landmarks = rightLandmarks)
        val frame = HandsFrame(tsNanos = 1000000L, hands = listOf(hand1, hand2))

        val serialized = LandmarkJsonl.serialize(frame, TrackerId.HAND_DUAL_WHEEL)
        val (trackerId, deserializedFrame) = LandmarkJsonl.deserializeWithTracker(serialized)

        assertThat(trackerId).isEqualTo(TrackerId.HAND_DUAL_WHEEL)
        assertThat(deserializedFrame.tsNanos).isEqualTo(1000000L)
        assertThat(deserializedFrame.hands).hasSize(2)
        assertThat(deserializedFrame.hands[0].side).isEqualTo(Side.LEFT)
        assertThat(deserializedFrame.hands[1].side).isEqualTo(Side.RIGHT)
    }

    @Test
    fun timestamp_preservesLargeMonotonicValues() {
        val largeTs = 1730368800000000000L
        val frame = HandsFrame(tsNanos = largeTs, hands = emptyList())

        val serialized = LandmarkJsonl.serialize(frame)
        val deserialized = LandmarkJsonl.deserialize(serialized)

        assertThat(deserialized.tsNanos).isEqualTo(largeTs)
    }

    @Test
    fun deserialize_supports2DLandmarks_defaultsZToZero() {
        val json2D = buildString {
            append("""{"t":123,"tracker":"HAND_SINGLE","hands":[{"side":"LEFT","score":0.9,"lm":[""")
            val points = (0 until 21).joinToString(",") { i -> "[${0.01f * i},${0.02f * i}]" }
            append(points)
            append("]}]}")
        }

        val frame = LandmarkJsonl.deserialize(json2D)

        assertThat(frame.hands).hasSize(1)
        assertThat(frame.hands[0].landmarks).hasSize(21)
        assertThat(frame.hands[0].landmarks[0].z).isEqualTo(0f)
        assertThat(frame.hands[0].landmarks[10].z).isEqualTo(0f)
    }

    @Test
    fun deserialize_toleratesWhitespaceAndNewlines() {
        val frame = HandsFrame(tsNanos = 42L, hands = emptyList())
        val serialized = "  \t " + LandmarkJsonl.serialize(frame) + " \r\n "

        val deserialized = LandmarkJsonl.deserialize(serialized)
        assertThat(deserialized.tsNanos).isEqualTo(42L)
    }

    @Test
    fun serializeRecord_preservesAllMetadata() {
        val frame = HandsFrame(tsNanos = 777L, hands = emptyList())
        val record = LandmarkRecord(
            timestampNanos = 777L,
            trackerId = TrackerId.HAND_SINGLE,
            frame = frame
        )

        val serialized = LandmarkJsonl.serializeRecord(record)
        val deserializedRecord = LandmarkJsonl.deserializeRecord(serialized)

        assertThat(deserializedRecord.timestampNanos).isEqualTo(record.timestampNanos)
        assertThat(deserializedRecord.trackerId).isEqualTo(record.trackerId)
        assertThat(deserializedRecord.frame.tsNanos).isEqualTo(record.frame.tsNanos)
    }

    @Test
    fun serialize_unsupportedTrackerFrame_throwsIllegalArgumentException() {
        val poseFrame = PoseFrame(tsNanos = 123L, score = 0.9f, landmarks = emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            LandmarkJsonl.serialize(poseFrame)
        }
    }

    @Test
    fun serialize_handWithWrongLandmarkCount_throwsIllegalArgumentException() {
        val invalidLandmarks = listOf(Pt(0.1f, 0.2f, 0f)) // only 1 landmark
        val frame = HandsFrame(
            tsNanos = 100L,
            hands = listOf(Hand(side = Side.LEFT, score = 0.9f, landmarks = invalidLandmarks))
        )

        assertThrows(IllegalArgumentException::class.java) {
            LandmarkJsonl.serialize(frame)
        }
    }

    @Test
    fun serialize_negativeTimestamp_throwsIllegalArgumentException() {
        val frame = HandsFrame(tsNanos = -1L, hands = emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            LandmarkJsonl.serialize(frame)
        }
    }

    @Test
    fun deserialize_blankLine_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            LandmarkJsonl.deserialize("   ")
        }
    }

    @Test
    fun deserialize_malformedJson_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            LandmarkJsonl.deserialize("{not valid json")
        }
    }

    @Test
    fun deserialize_missingTimestamp_throwsIllegalArgumentException() {
        val json = """{"tracker":"HAND_SINGLE","hands":[]}"""
        assertThrows(IllegalArgumentException::class.java) {
            LandmarkJsonl.deserialize(json)
        }
    }

    @Test
    fun deserialize_negativeTimestamp_throwsIllegalArgumentException() {
        val json = """{"t":-50,"tracker":"HAND_SINGLE","hands":[]}"""
        assertThrows(IllegalArgumentException::class.java) {
            LandmarkJsonl.deserialize(json)
        }
    }

    @Test
    fun deserialize_unknownTracker_throwsIllegalArgumentException() {
        val json = """{"t":100,"tracker":"UNKNOWN_ROBOT_TRACKER","hands":[]}"""
        assertThrows(IllegalArgumentException::class.java) {
            LandmarkJsonl.deserialize(json)
        }
    }

    @Test
    fun deserialize_invalidSide_throwsIllegalArgumentException() {
        val points = (0 until 21).joinToString(",") { "[0.1,0.2,0.0]" }
        val json = """{"t":100,"tracker":"HAND_SINGLE","hands":[{"side":"NOT_A_SIDE","score":0.9,"lm":[$points]}]}"""
        assertThrows(IllegalArgumentException::class.java) {
            LandmarkJsonl.deserialize(json)
        }
    }

    @Test
    fun deserialize_invalidLandmarkCount_throwsIllegalArgumentException() {
        // Hand with only 20 landmarks
        val points = (0 until 20).joinToString(",") { "[0.1,0.2,0.0]" }
        val json = """{"t":100,"tracker":"HAND_SINGLE","hands":[{"side":"LEFT","score":0.9,"lm":[$points]}]}"""
        assertThrows(IllegalArgumentException::class.java) {
            LandmarkJsonl.deserialize(json)
        }
    }

    @Test
    fun deserialize_invalidCoordinateDimension_throwsIllegalArgumentException() {
        // Landmark with 4 values
        val points = (0 until 20).map { "[0.1,0.2,0.0]" }.toMutableList()
        points.add("[0.1,0.2,0.3,0.4]")
        val json = """{"t":100,"tracker":"HAND_SINGLE","hands":[{"side":"LEFT","score":0.9,"lm":[${points.joinToString(",")}]}]}"""
        assertThrows(IllegalArgumentException::class.java) {
            LandmarkJsonl.deserialize(json)
        }
    }
}

