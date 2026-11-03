package com.zorbi.calib

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Fixture validation test suite for Gate T-P1-7 (Person 1 / P1-7).
 *
 * ## Fixture Sourcing & Gate T-P1-7 Status:
 * - `neutral_hand.jsonl`: Real physical fixture recorded from CameraX -> MediaPipe pipeline.
 * - `wheel_neutral_synthetic.jsonl`: Local synthetic test fixture (ground truth 0.0 deg neutral).
 * - `wheel_steer_range_synthetic.jsonl`: Local synthetic test fixture (ground truth 30.0 deg max steer).
 * - `lean_neutral_synthetic.jsonl`: Local synthetic test fixture (ground truth 0.0 deg neutral lean).
 * - `lean_range_synthetic.jsonl`: Local synthetic test fixture (ground truth 18.0 deg max lean).
 *
 * **Note on Missing Fixture Dependency**:
 * The master prompt assigns synthetic fixture generation to Person 4 under task P4-3 (`tools/synth_landmarks.py`).
 * Until Person 4 delivers the official wheel and lean fixtures or real physical recordings are captured,
 * local synthetic test fixtures are used for mathematical verification against approved tolerances.
 */
class CalibrationFixtureTest {

    // Approved tolerances from Owner Agent
    companion object {
        const val TOLERANCE_SYNTHETIC_NEUTRAL_DEG = 1.0f
        const val TOLERANCE_SYNTHETIC_MAX_DEG = 2.0f
        const val TOLERANCE_PHYSICAL_NEUTRAL_DEG = 2.5f
        const val TOLERANCE_PHYSICAL_MAX_DEG = 4.0f
        const val TOLERANCE_REACH_SCALE = 0.05f
    }

    private fun loadFramesFromResource(resourcePath: String): List<TrackerFrame> {
        val stream = javaClass.classLoader?.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Fixture resource not found: $resourcePath")

        val frames = mutableListOf<TrackerFrame>()
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            for (line in reader.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val frame = parseJsonlLine(trimmed)
                if (frame != null) {
                    frames.add(frame)
                }
            }
        }
        return frames
    }

    private fun parseJsonlLine(line: String): TrackerFrame? {
        val root = Json.parseToJsonElement(line).jsonObject
        val ts = root["t"]!!.jsonPrimitive.long

        if (root.containsKey("hands") && root["hands"] != null) {
            val handsArray = root["hands"]!!.jsonArray
            val hands = handsArray.map { handElement ->
                val handObj = handElement.jsonObject
                val sideStr = handObj["side"]!!.jsonPrimitive.content
                val score = handObj["score"]!!.jsonPrimitive.float
                val lms = handObj["lm"]!!.jsonArray.map { ptElement ->
                    val coords = ptElement.jsonArray
                    Pt(
                        x = coords[0].jsonPrimitive.float,
                        y = coords[1].jsonPrimitive.float,
                        z = if (coords.size > 2) coords[2].jsonPrimitive.float else 0f
                    )
                }
                Hand(side = Side.valueOf(sideStr), score = score, landmarks = lms)
            }
            return HandsFrame(tsNanos = ts, hands = hands)
        } else if (root.containsKey("pose") && root["pose"] != null) {
            val poseObj = root["pose"]!!.jsonObject
            val score = poseObj["score"]!!.jsonPrimitive.float
            val lms = poseObj["lm"]!!.jsonArray.map { ptElement ->
                val coords = ptElement.jsonArray
                Pt(
                    x = coords[0].jsonPrimitive.float,
                    y = coords[1].jsonPrimitive.float,
                    z = if (coords.size > 2) coords[2].jsonPrimitive.float else 0f
                )
            }
            return PoseFrame(tsNanos = ts, score = score, landmarks = lms)
        }
        return null
    }

    // 1. Physical HAND_SINGLE neutral fixture
    @Test
    fun physicalFixture_neutralHand_validatesWithinTolerance() {
        val frames = loadFramesFromResource("fixtures/neutral_hand.jsonl")
        assertThat(frames.size).isEqualTo(53)

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_SINGLE, frames)

        // Physical neutral angle tolerance: +-2.5 deg around 0.0
        assertThat(cal.neutralAngleDeg).isWithin(TOLERANCE_PHYSICAL_NEUTRAL_DEG).of(0.0f)
        assertThat(cal.maxAngleDeg).isEqualTo(35.0f)

        // Recorded hand was left hand
        assertThat(cal.dominantHand).isEqualTo(Side.LEFT)

        // Reach scales must be in valid range [0.2, 1.0]
        assertThat(cal.reachScaleX).isAtLeast(0.2f)
        assertThat(cal.reachScaleX).isAtMost(1.0f)
        assertThat(cal.reachScaleY).isAtLeast(0.2f)
        assertThat(cal.reachScaleY).isAtMost(1.0f)
    }

    // 2. Synthetic HAND_DUAL_WHEEL neutral fixture
    @Test
    fun syntheticFixture_wheelNeutral_validatesWithinTolerance() {
        val frames = loadFramesFromResource("fixtures/wheel_neutral_synthetic.jsonl")
        assertThat(frames).isNotEmpty()

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_DUAL_WHEEL, frames)

        // Neutral angle within +-1.0 deg of 0.0
        assertWithMessage("Wheel neutral angle must be within synthetic tolerance")
            .that(cal.neutralAngleDeg)
            .isWithin(TOLERANCE_SYNTHETIC_NEUTRAL_DEG)
            .of(0.0f)

        // Stationary fallback is 35 deg
        assertThat(cal.maxAngleDeg).isWithin(TOLERANCE_SYNTHETIC_MAX_DEG).of(35.0f)

        // Inter-wrist distance in fixture is 0.70 - 0.30 = 0.40
        assertThat(cal.reachScaleX).isWithin(TOLERANCE_REACH_SCALE).of(0.40f)
        assertThat(cal.dominantHand).isEqualTo(Side.RIGHT)
    }

    // 3. Synthetic HAND_DUAL_WHEEL steer range fixture
    @Test
    fun syntheticFixture_wheelSteerRange_validatesWithinTolerance() {
        val frames = loadFramesFromResource("fixtures/wheel_steer_range_synthetic.jsonl")
        assertThat(frames).isNotEmpty()

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_DUAL_WHEEL, frames)

        assertWithMessage("Wheel range neutral angle must be within synthetic tolerance")
            .that(cal.neutralAngleDeg)
            .isWithin(TOLERANCE_SYNTHETIC_NEUTRAL_DEG)
            .of(0.0f)

        // Max steering angle in fixture reaches +-30.0 deg (95th percentile ~28.5-30.0 deg)
        assertWithMessage("Wheel max steering angle must match fixture range")
            .that(cal.maxAngleDeg)
            .isWithin(TOLERANCE_SYNTHETIC_MAX_DEG)
            .of(29.0f)

        assertThat(cal.reachScaleX).isWithin(TOLERANCE_REACH_SCALE).of(0.40f)
    }

    // 4. Synthetic POSE_LITE neutral fixture
    @Test
    fun syntheticFixture_leanNeutral_validatesWithinTolerance() {
        val frames = loadFramesFromResource("fixtures/lean_neutral_synthetic.jsonl")
        assertThat(frames).isNotEmpty()

        val cal = CalibrationMath.computeFromSamples(TrackerId.POSE_LITE, frames)

        assertWithMessage("Lean neutral angle must be within synthetic tolerance")
            .that(cal.neutralAngleDeg)
            .isWithin(TOLERANCE_SYNTHETIC_NEUTRAL_DEG)
            .of(0.0f)

        // Stationary fallback is 20 deg
        assertThat(cal.maxAngleDeg).isWithin(TOLERANCE_SYNTHETIC_MAX_DEG).of(20.0f)

        // Shoulder breadth in fixture is 0.60 - 0.40 = 0.20
        assertThat(cal.reachScaleX).isWithin(TOLERANCE_REACH_SCALE).of(0.20f)
        assertThat(cal.dominantHand).isEqualTo(Side.UNKNOWN)
    }

    // 5. Synthetic POSE_LITE lean range fixture
    @Test
    fun syntheticFixture_leanRange_validatesWithinTolerance() {
        val frames = loadFramesFromResource("fixtures/lean_range_synthetic.jsonl")
        assertThat(frames).isNotEmpty()

        val cal = CalibrationMath.computeFromSamples(TrackerId.POSE_LITE, frames)

        assertWithMessage("Lean range neutral angle must be within synthetic tolerance")
            .that(cal.neutralAngleDeg)
            .isWithin(TOLERANCE_SYNTHETIC_NEUTRAL_DEG)
            .of(0.0f)

        // Max lean angle in fixture reaches +-18.0 deg (95th percentile ~17.1-18.0 deg)
        assertWithMessage("Pose max lean angle must match fixture range")
            .that(cal.maxAngleDeg)
            .isWithin(TOLERANCE_SYNTHETIC_MAX_DEG)
            .of(17.5f)

        assertThat(cal.reachScaleX).isWithin(TOLERANCE_REACH_SCALE).of(0.20f)
    }
}
