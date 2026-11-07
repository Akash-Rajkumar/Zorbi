package com.zorbi.calib.internal

import com.zorbi.core.Calibration
import com.zorbi.core.Side
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Internal DTO for JSON serialization of [Calibration] data models.
 */
@Serializable
internal data class CalibrationDto(
    @SerialName("neutralAngleDeg") val neutralAngleDeg: Float,
    @SerialName("maxAngleDeg") val maxAngleDeg: Float,
    @SerialName("reachScaleX") val reachScaleX: Float,
    @SerialName("reachScaleY") val reachScaleY: Float,
    @SerialName("dominantHand") val dominantHand: String,
    @SerialName("createdAtEpochMs") val createdAtEpochMs: Long
)

/**
 * Robust JSON serializer and deserializer for [Calibration].
 */
internal object JsonCalibrationSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Serializes a [Calibration] instance into a JSON formatted string.
     */
    fun serialize(c: Calibration): String {
        require(!c.neutralAngleDeg.isNaN() && !c.neutralAngleDeg.isInfinite()) {
            "neutralAngleDeg must be finite: ${c.neutralAngleDeg}"
        }
        require(!c.maxAngleDeg.isNaN() && !c.maxAngleDeg.isInfinite()) {
            "maxAngleDeg must be finite: ${c.maxAngleDeg}"
        }
        require(!c.reachScaleX.isNaN() && !c.reachScaleX.isInfinite()) {
            "reachScaleX must be finite: ${c.reachScaleX}"
        }
        require(!c.reachScaleY.isNaN() && !c.reachScaleY.isInfinite()) {
            "reachScaleY must be finite: ${c.reachScaleY}"
        }

        val dto = CalibrationDto(
            neutralAngleDeg = c.neutralAngleDeg,
            maxAngleDeg = c.maxAngleDeg,
            reachScaleX = c.reachScaleX,
            reachScaleY = c.reachScaleY,
            dominantHand = c.dominantHand.name,
            createdAtEpochMs = c.createdAtEpochMs
        )
        return json.encodeToString(dto)
    }

    /**
     * Deserializes a JSON formatted string into a [Calibration] instance.
     * Returns `null` if JSON is invalid, corrupted, or contains NaN/Infinite values.
     */
    fun deserialize(jsonString: String): Calibration? {
        if (jsonString.isBlank()) return null
        return try {
            val dto = json.decodeFromString<CalibrationDto>(jsonString)
            if (dto.neutralAngleDeg.isNaN() || dto.neutralAngleDeg.isInfinite()) return null
            if (dto.maxAngleDeg.isNaN() || dto.maxAngleDeg.isInfinite()) return null
            if (dto.reachScaleX.isNaN() || dto.reachScaleX.isInfinite()) return null
            if (dto.reachScaleY.isNaN() || dto.reachScaleY.isInfinite()) return null

            val side = try {
                Side.valueOf(dto.dominantHand)
            } catch (e: IllegalArgumentException) {
                Side.UNKNOWN
            }

            Calibration(
                neutralAngleDeg = dto.neutralAngleDeg,
                maxAngleDeg = dto.maxAngleDeg,
                reachScaleX = dto.reachScaleX,
                reachScaleY = dto.reachScaleY,
                dominantHand = side,
                createdAtEpochMs = dto.createdAtEpochMs
            )
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
