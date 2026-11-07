package com.zorbi.calib

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import org.junit.Test

/**
 * Unit tests for [CalibrationMath] pure algorithms (Person 1 / P1-7).
 */
class CalibrationMathTest {

    private fun create21Landmarks(wrist: Pt, defaultOffset: Float = 0.01f): List<Pt> {
        val list = ArrayList<Pt>(21)
        list.add(wrist) // Landmark 0: Wrist
        for (i in 1..20) {
            list.add(Pt(wrist.x + i * defaultOffset, wrist.y + i * defaultOffset, wrist.z))
        }
        return list
    }

    private fun create33PoseLandmarks(
        leftShoulder: Pt,
        rightShoulder: Pt,
        leftHip: Pt,
        rightHip: Pt
    ): List<Pt> {
        val list = ArrayList<Pt>(33)
        for (i in 0..32) {
            when (i) {
                11 -> list.add(leftShoulder)
                12 -> list.add(rightShoulder)
                23 -> list.add(leftHip)
                24 -> list.add(rightHip)
                else -> list.add(Pt(0.5f, 0.5f, 0f))
            }
        }
        return list
    }

    // 1. HAND_SINGLE neutral calibration
    @Test
    fun handSingle_neutralCalibration_returnsZeroNeutralAngle() {
        val samples = (0 until 10).map { i ->
            val wrist = Pt(0.5f, 0.5f, 0f)
            HandsFrame(
                tsNanos = 1000L * i,
                hands = listOf(Hand(Side.RIGHT, score = 0.95f, landmarks = create21Landmarks(wrist)))
            )
        }

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_SINGLE, samples)
        assertThat(cal.neutralAngleDeg).isEqualTo(0.0f)
        assertThat(cal.maxAngleDeg).isEqualTo(35.0f)
    }

    // 2. HAND_SINGLE dominant-hand detection
    @Test
    fun handSingle_dominantHand_majorityVoteDeterminesDominantHand() {
        // 7 left frames, 3 right frames -> LEFT
        val samples = (0 until 10).map { i ->
            val side = if (i < 7) Side.LEFT else Side.RIGHT
            HandsFrame(
                tsNanos = 1000L * i,
                hands = listOf(Hand(side, score = 0.9f, landmarks = create21Landmarks(Pt(0.5f, 0.5f))))
            )
        }

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_SINGLE, samples)
        assertThat(cal.dominantHand).isEqualTo(Side.LEFT)
    }

    // 3. HAND_SINGLE reach scales
    @Test
    fun handSingle_reachScales_measuresMovementSpan() {
        // Movement spanning x from 0.2 to 0.7 (span = 0.5), y from 0.3 to 0.8 (span = 0.5)
        val samples = (0..5).map { i ->
            val x = 0.2f + i * 0.1f
            val y = 0.3f + i * 0.1f
            HandsFrame(
                tsNanos = 1000L * i,
                hands = listOf(Hand(Side.RIGHT, score = 0.95f, landmarks = create21Landmarks(Pt(x, y))))
            )
        }

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_SINGLE, samples)
        assertThat(cal.reachScaleX).isWithin(0.01f).of(0.5f)
        assertThat(cal.reachScaleY).isWithin(0.01f).of(0.5f)
    }

    // 4. HAND_DUAL_WHEEL neutral angle
    @Test
    fun handDualWheel_neutralAngle_computesHorizontalWristAngle() {
        // Left wrist at (0.3, 0.5), Right wrist at (0.7, 0.5) -> dy = 0, dx = 0.4 -> 0.0 deg
        val samples = (0 until 10).map { i ->
            val left = Hand(Side.LEFT, score = 0.9f, landmarks = create21Landmarks(Pt(0.3f, 0.5f)))
            val right = Hand(Side.RIGHT, score = 0.9f, landmarks = create21Landmarks(Pt(0.7f, 0.5f)))
            HandsFrame(tsNanos = 1000L * i, hands = listOf(left, right))
        }

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_DUAL_WHEEL, samples)
        assertThat(cal.neutralAngleDeg).isWithin(0.1f).of(0.0f)
        assertThat(cal.dominantHand).isEqualTo(Side.RIGHT)
    }

    // 5. HAND_DUAL_WHEEL maximum steering angle
    @Test
    fun handDualWheel_maxAngle_computes95thPercentileDeviation() {
        // User rotates from -30 deg to +30 deg
        // dx = 0.4. For angle = 30 deg: dy = 0.4 * tan(30 deg) = 0.4 * 0.57735 = 0.2309
        val samples = (-10..10).mapIndexed { idx, step ->
            val angleDeg = step * 3.0f // from -30 to +30 deg
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val dx = 0.4
            val dy = dx * kotlin.math.tan(angleRad)
            val left = Hand(Side.LEFT, score = 0.9f, landmarks = create21Landmarks(Pt(0.3f, 0.5f)))
            val right = Hand(Side.RIGHT, score = 0.9f, landmarks = create21Landmarks(Pt(0.7f, (0.5 + dy).toFloat())))
            HandsFrame(tsNanos = 1000L * idx, hands = listOf(left, right))
        }

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_DUAL_WHEEL, samples)
        assertThat(cal.neutralAngleDeg).isWithin(1.0f).of(0.0f)
        // 95th percentile of |step * 3| up to 30 deg should be around 28-30 deg
        assertThat(cal.maxAngleDeg).isWithin(2.0f).of(30.0f)
    }

    // 6. POSE_LITE neutral angle
    @Test
    fun poseLite_neutralAngle_computesShoulderLineAngle() {
        // Left shoulder at (0.4, 0.3), Right shoulder at (0.6, 0.3) -> angle = 0.0 deg
        val samples = (0 until 10).map { i ->
            val landmarks = create33PoseLandmarks(
                leftShoulder = Pt(0.4f, 0.3f),
                rightShoulder = Pt(0.6f, 0.3f),
                leftHip = Pt(0.45f, 0.7f),
                rightHip = Pt(0.55f, 0.7f)
            )
            PoseFrame(tsNanos = 1000L * i, score = 0.95f, landmarks = landmarks)
        }

        val cal = CalibrationMath.computeFromSamples(TrackerId.POSE_LITE, samples)
        assertThat(cal.neutralAngleDeg).isWithin(0.1f).of(0.0f)
        assertThat(cal.dominantHand).isEqualTo(Side.UNKNOWN)
    }

    // 7. POSE_LITE maximum lean angle
    @Test
    fun poseLite_maxLeanAngle_computesLeanExtents() {
        // Leans from -20 deg to +20 deg
        val samples = (-10..10).mapIndexed { idx, step ->
            val angleDeg = step * 2.0f // -20 to +20 deg
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val dx = 0.2
            val dy = dx * kotlin.math.tan(angleRad)
            val landmarks = create33PoseLandmarks(
                leftShoulder = Pt(0.4f, 0.3f),
                rightShoulder = Pt(0.6f, (0.3 + dy).toFloat()),
                leftHip = Pt(0.45f, 0.7f),
                rightHip = Pt(0.55f, 0.7f)
            )
            PoseFrame(tsNanos = 1000L * idx, score = 0.95f, landmarks = landmarks)
        }

        val cal = CalibrationMath.computeFromSamples(TrackerId.POSE_LITE, samples)
        assertThat(cal.neutralAngleDeg).isWithin(1.0f).of(0.0f)
        assertThat(cal.maxAngleDeg).isWithin(2.0f).of(20.0f)
    }

    // 8. Stationary sample fallback
    @Test
    fun stationarySamples_fallbacksToSafeDefaults() {
        // Dual wheel stationary (jitter < 0.5 deg)
        val wheelSamples = (0 until 10).map { i ->
            val left = Hand(Side.LEFT, score = 0.9f, landmarks = create21Landmarks(Pt(0.3f, 0.5f)))
            val right = Hand(Side.RIGHT, score = 0.9f, landmarks = create21Landmarks(Pt(0.7f, 0.5f)))
            HandsFrame(tsNanos = 1000L * i, hands = listOf(left, right))
        }
        val wheelCal = CalibrationMath.computeFromSamples(TrackerId.HAND_DUAL_WHEEL, wheelSamples)
        assertThat(wheelCal.maxAngleDeg).isEqualTo(35.0f)

        // Pose stationary
        val poseSamples = (0 until 10).map { i ->
            val landmarks = create33PoseLandmarks(
                leftShoulder = Pt(0.4f, 0.3f),
                rightShoulder = Pt(0.6f, 0.3f),
                leftHip = Pt(0.45f, 0.7f),
                rightHip = Pt(0.55f, 0.7f)
            )
            PoseFrame(tsNanos = 1000L * i, score = 0.95f, landmarks = landmarks)
        }
        val poseCal = CalibrationMath.computeFromSamples(TrackerId.POSE_LITE, poseSamples)
        assertThat(poseCal.maxAngleDeg).isEqualTo(20.0f)
    }

    // 9. Outlier rejection
    @Test
    fun outlierRejection_extremeGlitchFrameIgnoredByMedian() {
        // 9 steady frames at 0 deg, 1 glitch frame at +85 deg
        val samples = (0 until 10).map { i ->
            val rightY = if (i == 5) 0.9f else 0.5f // Glitch frame
            val left = Hand(Side.LEFT, score = 0.9f, landmarks = create21Landmarks(Pt(0.3f, 0.5f)))
            val right = Hand(Side.RIGHT, score = 0.9f, landmarks = create21Landmarks(Pt(0.7f, rightY)))
            HandsFrame(tsNanos = 1000L * i, hands = listOf(left, right))
        }

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_DUAL_WHEEL, samples)
        // Median should still be 0.0 deg
        assertThat(cal.neutralAngleDeg).isWithin(0.1f).of(0.0f)
    }

    // 10. Invalid landmark count
    @Test
    fun invalidLandmarkCount_frameIsSafelySkipped() {
        // Frame with only 10 landmarks instead of 21
        val invalidHand = Hand(Side.RIGHT, score = 0.9f, landmarks = (0 until 10).map { Pt(0.5f, 0.5f) })
        val validHand = Hand(Side.RIGHT, score = 0.9f, landmarks = create21Landmarks(Pt(0.5f, 0.5f)))

        val frame1 = HandsFrame(tsNanos = 1_000_000L, hands = listOf(invalidHand))
        val frame2 = HandsFrame(tsNanos = 2_000_000L, hands = listOf(validHand))

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_SINGLE, listOf(frame1, frame2))
        assertThat(cal.dominantHand).isEqualTo(Side.RIGHT)
        assertThat(cal.createdAtEpochMs).isEqualTo(0L) // Pure calculation returns sentinel 0L
    }

    // 11. Missing hands
    @Test
    fun dualWheel_missingHand_frameIsSkipped() {
        val singleHandFrame = HandsFrame(
            tsNanos = 1000L,
            hands = listOf(Hand(Side.LEFT, score = 0.9f, landmarks = create21Landmarks(Pt(0.3f, 0.5f))))
        )
        val validDualFrame = HandsFrame(
            tsNanos = 2000L,
            hands = listOf(
                Hand(Side.LEFT, score = 0.9f, landmarks = create21Landmarks(Pt(0.3f, 0.5f))),
                Hand(Side.RIGHT, score = 0.9f, landmarks = create21Landmarks(Pt(0.7f, 0.5f)))
            )
        )

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_DUAL_WHEEL, listOf(singleHandFrame, validDualFrame))
        assertThat(cal.neutralAngleDeg).isWithin(0.1f).of(0.0f)
    }

    // 12. NaN and Infinity handling
    @Test
    fun nanAndInfinityCoordinates_frameIsSkipped() {
        val nanWrist = Pt(Float.NaN, 0.5f)
        val infWrist = Pt(0.5f, Float.POSITIVE_INFINITY)
        val validWrist = Pt(0.5f, 0.5f)

        val frame1 = HandsFrame(tsNanos = 1000L, hands = listOf(Hand(Side.RIGHT, 0.9f, create21Landmarks(nanWrist))))
        val frame2 = HandsFrame(tsNanos = 2000L, hands = listOf(Hand(Side.RIGHT, 0.9f, create21Landmarks(infWrist))))
        val frame3 = HandsFrame(tsNanos = 3000L, hands = listOf(Hand(Side.RIGHT, 0.9f, create21Landmarks(validWrist))))

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_SINGLE, listOf(frame1, frame2, frame3))
        assertThat(cal.dominantHand).isEqualTo(Side.RIGHT)
        assertThat(cal.reachScaleX).isEqualTo(0.8f) // Default fallback due to 1 valid sample
    }

    // 13. Angular wraparound
    @Test
    fun angularWraparound_normalizedCorrectly() {
        assertThat(CalibrationMath.normalizeAngleDeg(190f)).isWithin(0.01f).of(-170f)
        assertThat(CalibrationMath.normalizeAngleDeg(-200f)).isWithin(0.01f).of(160f)
        assertThat(CalibrationMath.normalizeAngleDeg(360f)).isWithin(0.01f).of(0f)
        assertThat(CalibrationMath.angularDifferenceDeg(170f, -170f)).isWithin(0.01f).of(-20f)
    }

    // 14. Invalid timestamp ordering
    @Test
    fun invalidTimestampOrdering_nonMonotonicFrameSkipped() {
        val f1 = HandsFrame(tsNanos = 10_000_000L, hands = listOf(Hand(Side.RIGHT, 0.9f, create21Landmarks(Pt(0.4f, 0.4f)))))
        val f2 = HandsFrame(tsNanos = 5_000_000L, hands = listOf(Hand(Side.LEFT, 0.9f, create21Landmarks(Pt(0.2f, 0.2f))))) // backwards in time!
        val f3 = HandsFrame(tsNanos = 20_000_000L, hands = listOf(Hand(Side.RIGHT, 0.9f, create21Landmarks(Pt(0.6f, 0.6f)))))

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_SINGLE, listOf(f1, f2, f3))
        // f2 must be rejected, so dominant hand is RIGHT from f1 and f3
        assertThat(cal.dominantHand).isEqualTo(Side.RIGHT)
        assertThat(cal.createdAtEpochMs).isEqualTo(0L) // Pure calculation returns sentinel 0L
    }

    // 15. Empty and insufficient samples
    @Test
    fun emptyAndInsufficientSamples_returnsDefaultFallback() {
        val emptyCal = CalibrationMath.computeFromSamples(TrackerId.HAND_DUAL_WHEEL, emptyList())
        assertThat(emptyCal.neutralAngleDeg).isEqualTo(0.0f)
        assertThat(emptyCal.maxAngleDeg).isEqualTo(35.0f)
        assertThat(emptyCal.reachScaleX).isEqualTo(0.8f)
        assertThat(emptyCal.createdAtEpochMs).isEqualTo(0L)
    }

    // 16. Deterministic output
    @Test
    fun deterministicOutput_repeatedExecutionProducesIdenticalCalibration() {
        val samples = (0 until 20).map { i ->
            val left = Hand(Side.LEFT, score = 0.9f, landmarks = create21Landmarks(Pt(0.3f, 0.5f)))
            val right = Hand(Side.RIGHT, score = 0.9f, landmarks = create21Landmarks(Pt(0.7f, 0.52f)))
            HandsFrame(tsNanos = 1_000_000L * i, hands = listOf(left, right))
        }

        val cal1 = CalibrationMath.computeFromSamples(TrackerId.HAND_DUAL_WHEEL, samples)
        val cal2 = CalibrationMath.computeFromSamples(TrackerId.HAND_DUAL_WHEEL, samples)

        assertThat(cal1).isEqualTo(cal2)
        assertThat(cal1.createdAtEpochMs).isEqualTo(0L)
        assertThat(cal2.createdAtEpochMs).isEqualTo(0L)
    }

    // 17. Monotonic timestamps do not leak into createdAtEpochMs
    @Test
    fun computeFromSamples_monotonicTimestampsDoNotLeakIntoCreatedAtEpochMs_returnsSentinelZero() {
        // Real-world monotonic timestamps from SystemClock.elapsedRealtimeNanos() domain
        val samples = (0 until 10).map { i ->
            val tsNanos = 311685245800729L + i * 33_333_333L
            HandsFrame(
                tsNanos = tsNanos,
                hands = listOf(Hand(Side.RIGHT, score = 0.95f, landmarks = create21Landmarks(Pt(0.5f, 0.5f))))
            )
        }

        val cal = CalibrationMath.computeFromSamples(TrackerId.HAND_SINGLE, samples)

        // Must NOT mistake monotonic nanoseconds (e.g. 311685245800ms) for Unix epoch time (~1.7e12 ms)
        // Must return sentinel 0L
        assertThat(cal.createdAtEpochMs).isEqualTo(0L)
    }
}
