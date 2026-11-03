package com.zorbi.calib

import com.zorbi.core.Calibration
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Pure, deterministic mathematical and statistical algorithms for computing [Calibration]
 * from [TrackerFrame] sample sequences across all [TrackerId] tracking modes.
 *
 * ## Epoch Timestamp Semantics:
 * [computeFromSamples] is purely mathematical and deterministic. It returns a documented
 * sentinel value `0L` for [Calibration.createdAtEpochMs] because [TrackerFrame.tsNanos] represents
 * monotonic elapsed time (device boot domain) rather than Unix epoch wall-clock time.
 *
 * The actual Unix epoch timestamp is assigned at the persistence boundary
 * in [com.zorbi.core.CalibrationRepository.save] using `System.currentTimeMillis()`.
 */
object CalibrationMath {

    const val MIN_HAND_LANDMARK_COUNT = 21
    const val MIN_POSE_LANDMARK_COUNT = 33

    // Safe operational clamps
    const val REACH_SCALE_MIN = 0.2f
    const val REACH_SCALE_MAX = 1.0f
    const val DEFAULT_REACH_SCALE = 0.8f

    const val WHEEL_MIN_ANGLE_DEG = 20.0f
    const val WHEEL_MAX_ANGLE_DEG = 60.0f
    const val DEFAULT_WHEEL_MAX_ANGLE_DEG = 35.0f
    const val WHEEL_STATIONARY_THRESHOLD_DEG = 3.0f

    const val LEAN_MIN_ANGLE_DEG = 15.0f
    const val LEAN_MAX_ANGLE_DEG = 35.0f
    const val DEFAULT_LEAN_MAX_ANGLE_DEG = 20.0f
    const val LEAN_STATIONARY_THRESHOLD_DEG = 2.5f

    const val DEFAULT_SINGLE_HAND_MAX_ANGLE_DEG = 35.0f

    // Pose landmark indices
    const val POSE_LEFT_SHOULDER = 11
    const val POSE_RIGHT_SHOULDER = 12
    const val POSE_LEFT_HIP = 23
    const val POSE_RIGHT_HIP = 24

    /**
     * Pure, deterministic entry point that computes [Calibration] from the provided [samples].
     */
    fun computeFromSamples(trackerId: TrackerId, samples: List<TrackerFrame>): Calibration {
        if (samples.isEmpty()) {
            return createDefaultFallback(trackerId, createdAtEpochMs = 0L)
        }

        // 1. Filter frames with monotonic, valid timestamps
        val monotonicSamples = filterMonotonicTimestamps(samples)
        if (monotonicSamples.isEmpty()) {
            return createDefaultFallback(trackerId, createdAtEpochMs = 0L)
        }

        // Return documented sentinel 0L: pure computation does not assign epoch timestamps.
        val sentinelEpochMs = 0L

        return when (trackerId) {
            TrackerId.HAND_SINGLE -> computeHandSingle(monotonicSamples, sentinelEpochMs)
            TrackerId.HAND_DUAL_WHEEL -> computeHandDualWheel(monotonicSamples, sentinelEpochMs)
            TrackerId.POSE_LITE -> computePoseLite(monotonicSamples, sentinelEpochMs)
        }
    }

    private fun computeHandSingle(samples: List<TrackerFrame>, deterministicEpochMs: Long): Calibration {
        val wristXs = ArrayList<Float>(samples.size)
        val wristYs = ArrayList<Float>(samples.size)
        var leftCount = 0
        var rightCount = 0

        for (frame in samples) {
            if (frame !is HandsFrame) continue
            if (frame.hands.isEmpty()) continue

            // For HAND_SINGLE, consider the highest scoring valid hand
            val hand = frame.hands.maxByOrNull { it.score } ?: continue
            if (hand.score <= 0f || hand.landmarks.size < MIN_HAND_LANDMARK_COUNT) continue

            val wrist = hand.landmarks[0]
            if (!isValidPt(wrist)) continue

            // Check remaining landmarks are valid numbers
            if (!areAllLandmarksValid(hand.landmarks)) continue

            wristXs.add(wrist.x)
            wristYs.add(wrist.y)

            when (hand.side) {
                Side.LEFT -> leftCount++
                Side.RIGHT -> rightCount++
                Side.UNKNOWN -> {}
            }
        }

        if (wristXs.isEmpty()) {
            return createDefaultFallback(TrackerId.HAND_SINGLE, deterministicEpochMs)
        }

        // Dominant hand: majority vote
        val dominantHand = if (leftCount > rightCount) Side.LEFT else Side.RIGHT

        // Reach scales
        val minX = wristXs.minOrNull() ?: 0f
        val maxX = wristXs.maxOrNull() ?: 0f
        val spanX = maxX - minX

        val minY = wristYs.minOrNull() ?: 0f
        val maxY = wristYs.maxOrNull() ?: 0f
        val spanY = maxY - minY

        val reachX = if (spanX < 0.05f) DEFAULT_REACH_SCALE else spanX.coerceIn(REACH_SCALE_MIN, REACH_SCALE_MAX)
        val reachY = if (spanY < 0.05f) DEFAULT_REACH_SCALE else spanY.coerceIn(REACH_SCALE_MIN, REACH_SCALE_MAX)

        return Calibration(
            neutralAngleDeg = 0.0f,
            maxAngleDeg = DEFAULT_SINGLE_HAND_MAX_ANGLE_DEG,
            reachScaleX = reachX,
            reachScaleY = reachY,
            dominantHand = dominantHand,
            createdAtEpochMs = deterministicEpochMs
        )
    }

    private fun computeHandDualWheel(samples: List<TrackerFrame>, deterministicEpochMs: Long): Calibration {
        val angles = ArrayList<Float>(samples.size)
        val horizontalSpans = ArrayList<Float>(samples.size)
        val verticalSpans = ArrayList<Float>(samples.size)

        for (frame in samples) {
            if (frame !is HandsFrame) continue
            // Reject frames that do not contain two hands
            if (frame.hands.size < 2) continue

            val handPair = resolveLeftAndRightHands(frame.hands) ?: continue
            val (leftHand, rightHand) = handPair

            if (leftHand.landmarks.size < MIN_HAND_LANDMARK_COUNT || rightHand.landmarks.size < MIN_HAND_LANDMARK_COUNT) {
                continue
            }

            val lw = leftHand.landmarks[0]
            val rw = rightHand.landmarks[0]

            if (!isValidPt(lw) || !isValidPt(rw)) continue
            if (!areAllLandmarksValid(leftHand.landmarks) || !areAllLandmarksValid(rightHand.landmarks)) continue

            val dx = rw.x - lw.x
            val dy = rw.y - lw.y

            if (dx.isNaN() || dx.isInfinite() || dy.isNaN() || dy.isInfinite()) continue

            // Angle in degrees: positive = clockwise from user's view (turning right lowers right wrist -> dy > 0)
            val angleRad = atan2(dy.toDouble(), dx.toDouble())
            val angleDeg = normalizeAngleDeg(Math.toDegrees(angleRad).toFloat())

            angles.add(angleDeg)
            horizontalSpans.add(abs(dx))
            verticalSpans.add(maxOf(rw.y, lw.y) - minOf(rw.y, lw.y))
        }

        if (angles.isEmpty()) {
            return createDefaultFallback(TrackerId.HAND_DUAL_WHEEL, deterministicEpochMs)
        }

        val neutralAngle = median(angles)

        // Calculate absolute deviations from neutral angle
        val deviations = angles.map { abs(angularDifferenceDeg(it, neutralAngle)) }
        val p95Deviation = percentile(deviations, 95.0)

        // Stationary sample fallback vs active dynamic range
        val maxAngle = if (p95Deviation < WHEEL_STATIONARY_THRESHOLD_DEG) {
            DEFAULT_WHEEL_MAX_ANGLE_DEG
        } else {
            p95Deviation.coerceIn(WHEEL_MIN_ANGLE_DEG, WHEEL_MAX_ANGLE_DEG)
        }

        val medianSpanX = median(horizontalSpans)
        val reachX = if (medianSpanX < 0.05f) DEFAULT_REACH_SCALE else medianSpanX.coerceIn(REACH_SCALE_MIN, REACH_SCALE_MAX)

        val medianSpanY = median(verticalSpans)
        val reachY = if (medianSpanY < 0.05f) DEFAULT_REACH_SCALE else medianSpanY.coerceIn(REACH_SCALE_MIN, REACH_SCALE_MAX)

        return Calibration(
            neutralAngleDeg = neutralAngle,
            maxAngleDeg = maxAngle,
            reachScaleX = reachX,
            reachScaleY = reachY,
            dominantHand = Side.RIGHT,
            createdAtEpochMs = deterministicEpochMs
        )
    }

    private fun computePoseLite(samples: List<TrackerFrame>, deterministicEpochMs: Long): Calibration {
        val angles = ArrayList<Float>(samples.size)
        val shoulderBreadths = ArrayList<Float>(samples.size)
        val torsoSpans = ArrayList<Float>(samples.size)

        for (frame in samples) {
            if (frame !is PoseFrame) continue
            if (frame.score <= 0f || frame.landmarks.size < MIN_POSE_LANDMARK_COUNT) continue

            val ls = frame.landmarks[POSE_LEFT_SHOULDER]
            val rs = frame.landmarks[POSE_RIGHT_SHOULDER]
            val lh = frame.landmarks[POSE_LEFT_HIP]
            val rh = frame.landmarks[POSE_RIGHT_HIP]

            if (!isValidPt(ls) || !isValidPt(rs) || !isValidPt(lh) || !isValidPt(rh)) continue
            if (!areAllLandmarksValid(frame.landmarks)) continue

            val dx = rs.x - ls.x
            val dy = rs.y - ls.y

            if (dx.isNaN() || dx.isInfinite() || dy.isNaN() || dy.isInfinite()) continue

            // Shoulder angle: positive = clockwise / right lean (right shoulder lowers -> dy > 0)
            val angleRad = atan2(dy.toDouble(), dx.toDouble())
            val angleDeg = normalizeAngleDeg(Math.toDegrees(angleRad).toFloat())

            angles.add(angleDeg)
            shoulderBreadths.add(abs(dx))

            val avgShoulderY = (ls.y + rs.y) / 2.0f
            val avgHipY = (lh.y + rh.y) / 2.0f
            torsoSpans.add(abs(avgHipY - avgShoulderY))
        }

        if (angles.isEmpty()) {
            return createDefaultFallback(TrackerId.POSE_LITE, deterministicEpochMs)
        }

        val neutralAngle = median(angles)

        val deviations = angles.map { abs(angularDifferenceDeg(it, neutralAngle)) }
        val p95Deviation = percentile(deviations, 95.0)

        // Stationary sample fallback vs active dynamic range
        val maxAngle = if (p95Deviation < LEAN_STATIONARY_THRESHOLD_DEG) {
            DEFAULT_LEAN_MAX_ANGLE_DEG
        } else {
            p95Deviation.coerceIn(LEAN_MIN_ANGLE_DEG, LEAN_MAX_ANGLE_DEG)
        }

        val medianBreadth = median(shoulderBreadths)
        val reachX = if (medianBreadth < 0.05f) DEFAULT_REACH_SCALE else medianBreadth.coerceIn(REACH_SCALE_MIN, REACH_SCALE_MAX)

        val medianTorso = median(torsoSpans)
        val reachY = if (medianTorso < 0.05f) DEFAULT_REACH_SCALE else medianTorso.coerceIn(REACH_SCALE_MIN, REACH_SCALE_MAX)

        return Calibration(
            neutralAngleDeg = neutralAngle,
            maxAngleDeg = maxAngle,
            reachScaleX = reachX,
            reachScaleY = reachY,
            dominantHand = Side.UNKNOWN,
            createdAtEpochMs = deterministicEpochMs
        )
    }

    /**
     * Resolves exactly one left hand and one right hand from a list of detected hands.
     * Uses [Hand.side] if available; falls back to horizontal position in mirrored view.
     */
    private fun resolveLeftAndRightHands(hands: List<Hand>): Pair<Hand, Hand>? {
        val leftHands = hands.filter { it.side == Side.LEFT }
        val rightHands = hands.filter { it.side == Side.RIGHT }

        if (leftHands.size == 1 && rightHands.size == 1) {
            return Pair(leftHands[0], rightHands[0])
        }

        // Fallback: take top 2 hands by confidence and sort by wrist x coordinate
        val candidates = hands.sortedByDescending { it.score }.take(2)
        if (candidates.size < 2) return null

        val h1 = candidates[0]
        val h2 = candidates[1]
        if (h1.landmarks.isEmpty() || h2.landmarks.isEmpty()) return null

        val x1 = h1.landmarks[0].x
        val x2 = h2.landmarks[0].x

        // In mirrored front view, user's left hand has smaller x coordinate
        return if (x1 <= x2) {
            Pair(h1, h2)
        } else {
            Pair(h2, h1)
        }
    }

    /**
     * Filters out non-monotonic or negative timestamp frames to ensure clean sequence processing.
     */
    private fun filterMonotonicTimestamps(samples: List<TrackerFrame>): List<TrackerFrame> {
        val result = ArrayList<TrackerFrame>(samples.size)
        var lastTs = -1L

        for (frame in samples) {
            if (frame.tsNanos < 0L) continue
            if (lastTs != -1L && frame.tsNanos < lastTs) {
                // Reject out-of-order frame
                continue
            }
            lastTs = frame.tsNanos
            result.add(frame)
        }
        return result
    }

    /**
     * Returns true if all 3D coordinates in [landmarks] are valid finite numbers.
     */
    fun areAllLandmarksValid(landmarks: List<Pt>): Boolean {
        for (i in landmarks.indices) {
            if (!isValidPt(landmarks[i])) return false
        }
        return true
    }

    /**
     * Returns true if point coordinates are finite non-NaN values.
     */
    fun isValidPt(pt: Pt): Boolean {
        return !pt.x.isNaN() && !pt.x.isInfinite() &&
                !pt.y.isNaN() && !pt.y.isInfinite() &&
                !pt.z.isNaN() && !pt.z.isInfinite()
    }

    /**
     * Normalizes an angle into the [-180, 180] degree interval.
     */
    fun normalizeAngleDeg(deg: Float): Float {
        var a = deg % 360f
        if (a > 180f) a -= 360f
        if (a <= -180f) a += 360f
        return a
    }

    /**
     * Returns the shortest signed angular difference between [angle] and [reference] in degrees.
     */
    fun angularDifferenceDeg(angle: Float, reference: Float): Float {
        return normalizeAngleDeg(angle - reference)
    }

    /**
     * Computes the robust median of a list of floats.
     */
    fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0.0f
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) {
            sorted[n / 2]
        } else {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0f
        }
    }

    /**
     * Computes the specified percentile [p] in [0, 100] using linear interpolation.
     */
    fun percentile(values: List<Float>, p: Double): Float {
        if (values.isEmpty()) return 0.0f
        if (values.size == 1) return values[0]
        val sorted = values.sorted()
        val clampedP = p.coerceIn(0.0, 100.0)
        val rank = (clampedP / 100.0) * (sorted.size - 1)
        val lower = floor(rank).toInt()
        val upper = ceil(rank).toInt()
        val weight = (rank - lower).toFloat()
        return sorted[lower] + weight * (sorted[upper] - sorted[lower])
    }

    /**
     * Creates safe fallback [Calibration] parameters when sample data is empty or invalid.
     */
    fun createDefaultFallback(trackerId: TrackerId, createdAtEpochMs: Long = 0L): Calibration {
        return when (trackerId) {
            TrackerId.HAND_SINGLE -> Calibration(
                neutralAngleDeg = 0.0f,
                maxAngleDeg = DEFAULT_SINGLE_HAND_MAX_ANGLE_DEG,
                reachScaleX = DEFAULT_REACH_SCALE,
                reachScaleY = DEFAULT_REACH_SCALE,
                dominantHand = Side.RIGHT,
                createdAtEpochMs = createdAtEpochMs
            )
            TrackerId.HAND_DUAL_WHEEL -> Calibration(
                neutralAngleDeg = 0.0f,
                maxAngleDeg = DEFAULT_WHEEL_MAX_ANGLE_DEG,
                reachScaleX = DEFAULT_REACH_SCALE,
                reachScaleY = DEFAULT_REACH_SCALE,
                dominantHand = Side.RIGHT,
                createdAtEpochMs = createdAtEpochMs
            )
            TrackerId.POSE_LITE -> Calibration(
                neutralAngleDeg = 0.0f,
                maxAngleDeg = DEFAULT_LEAN_MAX_ANGLE_DEG,
                reachScaleX = DEFAULT_REACH_SCALE,
                reachScaleY = DEFAULT_REACH_SCALE,
                dominantHand = Side.UNKNOWN,
                createdAtEpochMs = createdAtEpochMs
            )
        }
    }
}
