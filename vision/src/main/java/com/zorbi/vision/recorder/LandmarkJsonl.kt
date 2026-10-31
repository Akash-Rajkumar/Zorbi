package com.zorbi.vision.recorder

import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PoseFrame
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
 *
 * For hand tracking ([TrackerId.HAND_SINGLE], [TrackerId.HAND_DUAL_WHEEL]), [frame] holds the
 * [HandsFrame] with 21 landmarks per hand.
 * For pose tracking ([TrackerId.POSE_LITE]), [poseFrame] holds the [PoseFrame] with 33 landmarks.
 */
data class LandmarkRecord(
    val timestampNanos: Long,
    val trackerId: TrackerId,
    val frame: HandsFrame,
    val poseFrame: PoseFrame? = null
)

/**
 * Section 5.3 DTO for top-level JSONL record line:
 * - Hands: {"t":123456789,"tracker":"HAND_SINGLE","hands":[{"side":"LEFT","score":0.93,"lm":[[0.1,0.2,0.0],...]}]}
 * - Pose:  {"t":123456790,"tracker":"POSE_LITE","pose":{"score":0.88,"lm":[[0.4,0.3,0.0],...]}}
 */
@Serializable
internal data class LandmarkRecordDto(
    @SerialName("t") val t: Long,
    @SerialName("tracker") val tracker: String,
    @SerialName("hands") val hands: List<HandDto>? = null,
    @SerialName("pose") val pose: PoseDto? = null
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
 * Section 5.3 DTO for a pose record.
 */
@Serializable
internal data class PoseDto(
    @SerialName("score") val score: Float,
    @SerialName("lm") val lm: List<List<Float>>
)

/**
 * Frozen Section 5.3 JSONL serialization and deserialization layer for ZORBI landmark logs.
 *
 * Schemas:
 * - Hands: {"t":123456789,"tracker":"HAND_DUAL_WHEEL","hands":[{"side":"LEFT","score":0.93,"lm":[[0.1,0.2,0.0],...]}]}
 * - Pose:  {"t":123456790,"tracker":"POSE_LITE","pose":{"score":0.88,"lm":[[0.4,0.3,0.0],...]}}
 */
object LandmarkJsonl {

    const val EXPECTED_HAND_LANDMARK_COUNT = 21
    const val EXPECTED_POSE_LANDMARK_COUNT = 33

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        prettyPrint = false
        encodeDefaults = false
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
     * Serializes a [PoseFrame] to a single-line JSON string per Section 5.3 specification.
     */
    fun serialize(frame: PoseFrame, trackerId: TrackerId = TrackerId.POSE_LITE): String {
        require(frame.tsNanos >= 0) {
            "Timestamp must be non-negative: ${frame.tsNanos}"
        }
        require(frame.landmarks.size == EXPECTED_POSE_LANDMARK_COUNT) {
            "Pose must contain exactly $EXPECTED_POSE_LANDMARK_COUNT landmarks, found ${frame.landmarks.size}"
        }
        require(!frame.score.isNaN() && !frame.score.isInfinite()) {
            "Pose score must be a finite number: ${frame.score}"
        }

        val poseDto = PoseDto(
            score = frame.score,
            lm = frame.landmarks.map { pt ->
                require(!pt.x.isNaN() && !pt.y.isNaN() && !pt.z.isNaN()) {
                    "Landmark coordinate contains NaN: $pt"
                }
                require(!pt.x.isInfinite() && !pt.y.isInfinite() && !pt.z.isInfinite()) {
                    "Landmark coordinate contains Infinite: $pt"
                }
                listOf(pt.x, pt.y, pt.z)
            }
        )

        val recordDto = LandmarkRecordDto(
            t = frame.tsNanos,
            tracker = trackerId.name,
            pose = poseDto
        )

        return json.encodeToString(recordDto)
    }

    /**
     * Convenience overload for generic [TrackerFrame]. Supports [HandsFrame] and [PoseFrame].
     */
    fun serialize(frame: TrackerFrame, trackerId: TrackerId = TrackerId.HAND_SINGLE): String {
        return when (frame) {
            is HandsFrame -> serialize(frame, trackerId)
            is PoseFrame -> serialize(frame, if (trackerId == TrackerId.HAND_SINGLE) TrackerId.POSE_LITE else trackerId)
            else -> throw IllegalArgumentException("Unsupported TrackerFrame type: ${frame::class.java.name}")
        }
    }

    /**
     * Serializes a [LandmarkRecord] to a single-line JSON string.
     */
    fun serializeRecord(record: LandmarkRecord): String {
        return if (record.poseFrame != null) {
            serialize(record.poseFrame, record.trackerId)
        } else {
            serialize(record.frame, record.trackerId)
        }
    }

    /**
     * Deserializes a single JSONL line into a [LandmarkRecord].
     *
     * Validates:
     * - Line is non-blank and valid JSON.
     * - Timestamp 't' is present and non-negative.
     * - Tracker is a known [TrackerId].
     * - If hands: each hand contains a valid [Side], finite score, and exactly 21 landmarks.
     * - If pose: contains finite score and exactly 33 landmarks.
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

        var parsedHandsFrame: HandsFrame? = null
        var parsedPoseFrame: PoseFrame? = null

        if (dto.pose != null) {
            val poseDto = dto.pose
            if (poseDto.score.isNaN() || poseDto.score.isInfinite()) {
                throw IllegalArgumentException("Pose score must be a finite number, found: ${poseDto.score}")
            }
            if (poseDto.lm.size != EXPECTED_POSE_LANDMARK_COUNT) {
                throw IllegalArgumentException(
                    "Pose must contain exactly $EXPECTED_POSE_LANDMARK_COUNT landmarks, found ${poseDto.lm.size}"
                )
            }

            val landmarks = poseDto.lm.mapIndexed { lmIdx, coords ->
                if (coords.size < 2 || coords.size > 3) {
                    throw IllegalArgumentException(
                        "Pose landmark at index $lmIdx must contain 2 or 3 coordinates [x, y, z?], found ${coords.size}"
                    )
                }
                val x = coords[0]
                val y = coords[1]
                val z = if (coords.size >= 3) coords[2] else 0f

                if (x.isNaN() || y.isNaN() || z.isNaN() || x.isInfinite() || y.isInfinite() || z.isInfinite()) {
                    throw IllegalArgumentException("Pose landmark at index $lmIdx contains NaN or Infinite coordinate")
                }

                Pt(x = x, y = y, z = z)
            }

            parsedPoseFrame = PoseFrame(tsNanos = dto.t, score = poseDto.score, landmarks = landmarks)
        } else if (dto.hands != null) {
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

            parsedHandsFrame = HandsFrame(tsNanos = dto.t, hands = hands)
        }

        val finalHandsFrame = parsedHandsFrame ?: HandsFrame(tsNanos = dto.t, hands = emptyList())

        return LandmarkRecord(
            timestampNanos = dto.t,
            trackerId = trackerId,
            frame = finalHandsFrame,
            poseFrame = parsedPoseFrame
        )
    }

    /**
     * Deserializes a single JSONL line directly into a [HandsFrame].
     */
    fun deserialize(line: String): HandsFrame {
        return deserializeRecord(line).frame
    }

    /**
     * Deserializes a single JSONL line directly into a [PoseFrame].
     */
    fun deserializePose(line: String): PoseFrame {
        return deserializeRecord(line).poseFrame
            ?: throw IllegalArgumentException("Record is not a PoseFrame")
    }

    /**
     * Deserializes a single JSONL line into a Pair of [TrackerId] and [HandsFrame].
     */
    fun deserializeWithTracker(line: String): Pair<TrackerId, HandsFrame> {
        val record = deserializeRecord(line)
        return record.trackerId to record.frame
    }
}
