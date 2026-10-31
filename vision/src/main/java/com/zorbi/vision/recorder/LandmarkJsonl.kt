package com.zorbi.vision.recorder

import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Data container representing a parsed landmark record line from a JSONL file.
 */
data class LandmarkRecord(
    val timestampNanos: Long,
    val trackerId: TrackerId,
    val frame: HandsFrame
)

/**
 * Section 5.3 DTO for top-level JSONL record line:
 * {"t":123456789,"tracker":"HAND_SINGLE","hands":[{"side":"LEFT","score":0.93,"lm":[[0.1,0.2,0.0],...]}]}
 */
@Serializable
internal data class LandmarkRecordDto(
    @SerialName("t") val t: Long,
    @SerialName("tracker") val tracker: String,
    @SerialName("hands") val hands: List<HandDto> = emptyList()
)

/**
 * Section 5.3 DTO for an individual hand in the hands array.
 */
@Serializable
internal data class HandDto(
    @SerialName("side") val side: String,
    @SerialName("score") val score: Float,
    @SerialName("lm") val lm: List<List<Float>>
)

/**
 * Frozen Section 5.3 JSONL serialization and deserialization layer for ZORBI landmark logs.
 *
 * Line schema:
 * {"t":123456789,"tracker":"HAND_SINGLE","hands":[{"side":"LEFT","score":0.93,"lm":[[0.1,0.2,0.0],...]}]}
 */
object LandmarkJsonl {

    private const val EXPECTED_HAND_LANDMARK_COUNT = 21

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * Serializes a [HandsFrame] to a single-line JSON string per Section 5.3 specification.
     */
    fun serialize(frame: HandsFrame, trackerId: TrackerId = TrackerId.HAND_SINGLE): String {
        require(frame.tsNanos >= 0) {
            "Timestamp must be non-negative: ${frame.tsNanos}"
        }

        val handDtos = frame.hands.map { hand ->
            require(hand.landmarks.size == EXPECTED_HAND_LANDMARK_COUNT) {
                "Hand must contain exactly $EXPECTED_HAND_LANDMARK_COUNT landmarks, found ${hand.landmarks.size}"
            }
            require(!hand.score.isNaN() && !hand.score.isInfinite()) {
                "Hand score must be a finite number: ${hand.score}"
            }

            HandDto(
                side = hand.side.name,
                score = hand.score,
                lm = hand.landmarks.map { pt ->
                    require(!pt.x.isNaN() && !pt.y.isNaN() && !pt.z.isNaN()) {
                        "Landmark coordinate contains NaN: $pt"
                    }
                    require(!pt.x.isInfinite() && !pt.y.isInfinite() && !pt.z.isInfinite()) {
                        "Landmark coordinate contains Infinite: $pt"
                    }
                    listOf(pt.x, pt.y, pt.z)
                }
            )
        }

        val recordDto = LandmarkRecordDto(
            t = frame.tsNanos,
            tracker = trackerId.name,
            hands = handDtos
        )

        return json.encodeToString(recordDto)
    }

    /**
     * Convenience overload for generic [TrackerFrame]. Currently supports [HandsFrame].
     */
    fun serialize(frame: TrackerFrame, trackerId: TrackerId = TrackerId.HAND_SINGLE): String {
        return when (frame) {
            is HandsFrame -> serialize(frame, trackerId)
            else -> throw IllegalArgumentException("Unsupported TrackerFrame type: ${frame::class.java.name}")
        }
    }

    /**
     * Serializes a [LandmarkRecord] to a single-line JSON string.
     */
    fun serializeRecord(record: LandmarkRecord): String {
        return serialize(record.frame, record.trackerId)
    }

    /**
     * Deserializes a single JSONL line into a [LandmarkRecord].
     *
     * Validates:
     * - Line is non-blank and valid JSON.
     * - Timestamp 't' is present and non-negative.
     * - Tracker is a known [TrackerId].
     * - Each hand contains a valid [Side], finite score, and exactly 21 landmarks.
     * - Each landmark coordinate contains 2 or 3 finite numbers.
     *
     * @throws IllegalArgumentException on malformed JSON, missing fields, or invalid values.
     */
    fun deserializeRecord(line: String): LandmarkRecord {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("JSONL line cannot be blank")
        }

        val dto = try {
            json.decodeFromString<LandmarkRecordDto>(trimmed)
        } catch (ex: SerializationException) {
            throw IllegalArgumentException("Malformed JSONL line: ${ex.message}", ex)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("Malformed JSONL line: ${ex.message}", ex)
        }

        if (dto.t < 0) {
            throw IllegalArgumentException("Timestamp must be non-negative, found: ${dto.t}")
        }

        val trackerId = runCatching { TrackerId.valueOf(dto.tracker) }.getOrElse {
            throw IllegalArgumentException("Unknown or unsupported tracker '${dto.tracker}' in JSONL")
        }

        val hands = dto.hands.mapIndexed { handIdx, handDto ->
            val side = runCatching { Side.valueOf(handDto.side.uppercase()) }.getOrElse {
                throw IllegalArgumentException("Invalid hand side '${handDto.side}' at hand index $handIdx")
            }

            if (handDto.score.isNaN() || handDto.score.isInfinite()) {
                throw IllegalArgumentException("Hand score must be a finite number, found: ${handDto.score}")
            }

            if (handDto.lm.size != EXPECTED_HAND_LANDMARK_COUNT) {
                throw IllegalArgumentException(
                    "Hand at index $handIdx must contain exactly $EXPECTED_HAND_LANDMARK_COUNT landmarks, found ${handDto.lm.size}"
                )
            }

            val landmarks = handDto.lm.mapIndexed { lmIdx, coords ->
                if (coords.size < 2 || coords.size > 3) {
                    throw IllegalArgumentException(
                        "Landmark at index $lmIdx must contain 2 or 3 coordinates [x, y, z?], found ${coords.size}"
                    )
                }
                val x = coords[0]
                val y = coords[1]
                val z = if (coords.size >= 3) coords[2] else 0f

                if (x.isNaN() || y.isNaN() || z.isNaN() || x.isInfinite() || y.isInfinite() || z.isInfinite()) {
                    throw IllegalArgumentException("Landmark at index $lmIdx contains NaN or Infinite coordinate")
                }

                Pt(x = x, y = y, z = z)
            }

            Hand(side = side, score = handDto.score, landmarks = landmarks)
        }

        val frame = HandsFrame(tsNanos = dto.t, hands = hands)
        return LandmarkRecord(timestampNanos = dto.t, trackerId = trackerId, frame = frame)
    }

    /**
     * Deserializes a single JSONL line directly into a [HandsFrame].
     */
    fun deserialize(line: String): HandsFrame {
        return deserializeRecord(line).frame
    }

    /**
     * Deserializes a single JSONL line into a Pair of [TrackerId] and [HandsFrame].
     */
    fun deserializeWithTracker(line: String): Pair<TrackerId, HandsFrame> {
        val record = deserializeRecord(line)
        return record.trackerId to record.frame
    }
}

