package com.zorbi.gesture

import com.zorbi.core.Calibration
import com.zorbi.core.GameProfile
import com.zorbi.core.GestureEvent
import com.zorbi.core.GestureInterpreter
import com.zorbi.core.HandsFrame
import com.zorbi.core.Pt
import com.zorbi.core.Slash
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import kotlin.math.sqrt

/**
 * Interprets rapid cutting strokes from [HandsFrame] into [Slash] gesture events.
 *
 * Implements [GestureInterpreter] for [TrackerId.HAND_SINGLE].
 *
 * Detection & Trajectory Model:
 * - Blade Tip Tracking: Tracks the Index Fingertip (landmark 8) as the cutting point.
 *   Note on landmark confidence: Per the frozen contracts, confidence is provided at the
 *   hand level ([com.zorbi.core.Hand.score]); individual landmark confidence is not defined in [Pt].
 *   If hand confidence is below [minConfidence], the frame is dropped.
 * - Coordinate Transformation: Converts incoming image-space coordinates into normalized screen-space
 *   coordinates using [Calibration.reachScaleX] and [Calibration.reachScaleY] relative to center (0.5, 0.5).
 * - Path Preservation: Preserves the entire ordered trajectory curve in screen-space, not merely endpoints.
 * - Directional Linearity: Enforces net displacement over cumulative length ratio (D / L >= [minLinearity])
 *   to differentiate intentional straight/curved cuts from erratic shaking or back-and-forth noise.
 * - Thresholds: Enforces [GameProfile.params.slashMinLength] and [GameProfile.params.swipeMinSpeed].
 * - Cooldown & Re-Arm: Disarms for [GameProfile.params.cooldownMs] and requires motion to decelerate
 *   below [rearmSpeedFactor] * minSpeed before re-arming, preventing duplicate slashes during one continuous stroke.
 */
class SlashGestureInterpreter(
    override val accepts: TrackerId = TrackerId.HAND_SINGLE,
    private val minConfidence: Float = 0.5f,
    private val minLinearity: Float = 0.70f,
    private val sampleWindowMs: Long = 250L,
    private val maxTrackingGapMs: Long = 500L,
    private val rearmSpeedFactor: Float = 0.5f
) : ResettableInterpreter {

    private data class TimestampedPoint(
        val tsNanos: Long,
        val imagePt: Pt,
        val screenPt: Pt
    )

    private val sampleHistory = ArrayDeque<TimestampedPoint>()
    private var lastFrameTsNanos: Long = 0L
    private var lastSlashTsNanos: Long = 0L
    private var isArmed: Boolean = true

    override fun reset() {
        sampleHistory.clear()
        lastFrameTsNanos = 0L
        lastSlashTsNanos = 0L
        isArmed = true
    }

    override fun onFrame(
        frame: TrackerFrame,
        profile: GameProfile,
        cal: Calibration
    ): List<GestureEvent> {
        // 1. Validate frame type
        if (frame !is HandsFrame) {
            return emptyList()
        }

        // 2. Monotonicity and timestamp validation
        val currentTs = frame.tsNanos
        if (lastFrameTsNanos > 0L) {
            if (currentTs <= lastFrameTsNanos) {
                // Stale or out-of-order frame: drop safely
                return emptyList()
            }
            val gapNanos = currentTs - lastFrameTsNanos
            if (gapNanos > maxTrackingGapMs * 1_000_000L) {
                // Tracking interrupted: reset history to avoid artificial velocity spikes
                sampleHistory.clear()
                if (currentTs - lastSlashTsNanos >= profile.params.cooldownMs * 1_000_000L) {
                    isArmed = true
                }
            }
        }
        lastFrameTsNanos = currentTs

        // 3. Extract and validate hand tracking
        if (frame.hands.isEmpty()) {
            sampleHistory.clear()
            return emptyList()
        }

        val hand = selectHand(frame, cal)
        if (hand == null || hand.score < minConfidence || hand.landmarks.size < 21) {
            sampleHistory.clear()
            return emptyList()
        }

        // 4. Extract cutting blade point (Index Fingertip = landmark 8)
        val imagePt = hand.landmarks[8]
        val screenPt = transformToScreenSpace(imagePt, cal)

        // 5. Check cooldown and re-arm state
        val cooldownNanos = profile.params.cooldownMs * 1_000_000L
        val minSpeed = profile.params.swipeMinSpeed
        val rearmSpeedThreshold = minSpeed * rearmSpeedFactor
        val timeSinceLastSlash = currentTs - lastSlashTsNanos
        val isInCooldown = timeSinceLastSlash < cooldownNanos

        val previousSample = sampleHistory.lastOrNull()
        val windowNanos = sampleWindowMs * 1_000_000L
        sampleHistory.addLast(TimestampedPoint(currentTs, imagePt, screenPt))
        while (sampleHistory.isNotEmpty() && (currentTs - sampleHistory.first().tsNanos) > windowNanos) {
            sampleHistory.removeFirst()
        }

        if (!isArmed) {
            if (!isInCooldown && previousSample != null) {
                val dt = (currentTs - previousSample.tsNanos) / 1_000_000_000f
                if (dt > 0f) {
                    val dx = screenPt.x - previousSample.screenPt.x
                    val dy = screenPt.y - previousSample.screenPt.y
                    val recentSpeed = sqrt(dx * dx + dy * dy) / dt
                    if (recentSpeed < rearmSpeedThreshold) {
                        // Hand motion has settled below threshold: re-arm
                        isArmed = true
                        sampleHistory.clear()
                        sampleHistory.addLast(TimestampedPoint(currentTs, imagePt, screenPt))
                    }
                }
            }
            return emptyList()
        }

        if (sampleHistory.size < 2) {
            return emptyList()
        }

        // 6. Compute cumulative path length and net displacement in screen-space
        var cumulativeLength = 0f
        for (i in 1 until sampleHistory.size) {
            val p0 = sampleHistory[i - 1].screenPt
            val p1 = sampleHistory[i].screenPt
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            cumulativeLength += sqrt(dx * dx + dy * dy)
        }

        val startSample = sampleHistory.first()
        val netDx = screenPt.x - startSample.screenPt.x
        val netDy = screenPt.y - startSample.screenPt.y
        val netDisplacement = sqrt(netDx * netDx + netDy * netDy)

        val deltaNanos = currentTs - startSample.tsNanos
        if (deltaNanos <= 0L) return emptyList()

        val deltaSec = deltaNanos / 1_000_000_000f
        val speed = cumulativeLength / deltaSec
        val linearity = if (cumulativeLength > 0f) netDisplacement / cumulativeLength else 0f

        // 7. Validate slash criteria
        val minLength = profile.params.slashMinLength
        if (cumulativeLength < minLength || speed < minSpeed || linearity < minLinearity) {
            return emptyList()
        }

        // 8. Construct ordered trajectory path in screen-space
        val slashPath = sampleHistory.map { it.screenPt }

        // 9. Trigger slash, disarm, and enter cooldown
        lastSlashTsNanos = currentTs
        isArmed = false
        sampleHistory.clear()

        return listOf(Slash(tsNanos = currentTs, path = slashPath))
    }

    /**
     * Transforms normalized image-space coordinates to normalized screen-space coordinates
     * using [Calibration.reachScaleX] and [Calibration.reachScaleY] relative to center (0.5, 0.5).
     */
    fun transformToScreenSpace(p: Pt, cal: Calibration): Pt {
        val scaleX = if (cal.reachScaleX > 0.01f) cal.reachScaleX else 1.0f
        val scaleY = if (cal.reachScaleY > 0.01f) cal.reachScaleY else 1.0f

        val screenX = ((p.x - 0.5f) / scaleX + 0.5f).coerceIn(0f, 1f)
        val screenY = ((p.y - 0.5f) / scaleY + 0.5f).coerceIn(0f, 1f)
        return Pt(screenX, screenY, p.z)
    }

    private fun selectHand(frame: HandsFrame, cal: Calibration) =
        frame.hands.firstOrNull { it.side == cal.dominantHand && it.score >= minConfidence }
            ?: frame.hands.firstOrNull { it.score >= minConfidence }
}

