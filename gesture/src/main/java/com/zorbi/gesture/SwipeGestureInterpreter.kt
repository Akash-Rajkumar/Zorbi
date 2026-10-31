package com.zorbi.gesture

import com.zorbi.core.Calibration
import com.zorbi.core.Dir
import com.zorbi.core.GameProfile
import com.zorbi.core.GestureEvent
import com.zorbi.core.GestureInterpreter
import com.zorbi.core.HandsFrame
import com.zorbi.core.MappingType
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.Swipe
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Interprets hand movements from [HandsFrame] into [Swipe] gesture events.
 *
 * Implements [GestureInterpreter] for [TrackerId.HAND_SINGLE].
 *
 * Features:
 * - 4-way direction classification (LEFT, RIGHT, UP, DOWN).
 * - Distance and speed validation using [GameProfile.params.swipeMinSpeed].
 * - Monotonic timestamp tracking with out-of-order and stale frame rejection.
 * - Tracking gap reset for temporal discontinuities.
 * - Low-confidence and empty frame rejection.
 * - Cooldown enforcement using [GameProfile.params.cooldownMs].
 * - Motion-settling re-arm logic preventing multiple triggers during a single continuous gesture stroke.
 * - Palm center tracking (midpoint between Wrist [0] and Middle MCP [9]) for high noise immunity.
 */
class SwipeGestureInterpreter(
    override val accepts: TrackerId = TrackerId.HAND_SINGLE,
    private val minConfidence: Float = 0.5f,
    private val minDistance: Float = 0.08f,
    private val sampleWindowMs: Long = 200L,
    private val maxTrackingGapMs: Long = 500L,
    private val rearmSpeedFactor: Float = 0.5f
) : ResettableInterpreter {

    private data class TimestampedPoint(val tsNanos: Long, val pt: Pt)

    private val sampleHistory = ArrayDeque<TimestampedPoint>()
    private var lastFrameTsNanos: Long = 0L
    private var lastSwipeTsNanos: Long = 0L
    private var isArmed: Boolean = true

    override fun reset() {
        sampleHistory.clear()
        lastFrameTsNanos = 0L
        lastSwipeTsNanos = 0L
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
                // Tracking interrupted: reset history to prevent artificial velocity spikes across gaps
                sampleHistory.clear()
                if (currentTs - lastSwipeTsNanos >= profile.params.cooldownMs * 1_000_000L) {
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

        // 4. Calculate stable tracking point (palm center = midpoint of wrist [0] and middle MCP [9])
        val wrist = hand.landmarks[0]
        val middleMcp = hand.landmarks[9]
        val currentPoint = Pt(
            x = (wrist.x + middleMcp.x) * 0.5f,
            y = (wrist.y + middleMcp.y) * 0.5f,
            z = (wrist.z + middleMcp.z) * 0.5f
        )

        // 5. Check cooldown and re-arm state
        val cooldownNanos = profile.params.cooldownMs * 1_000_000L
        val minSpeed = profile.params.swipeMinSpeed
        val rearmSpeedThreshold = minSpeed * rearmSpeedFactor
        val timeSinceLastSwipe = currentTs - lastSwipeTsNanos
        val isInCooldown = timeSinceLastSwipe < cooldownNanos

        val previousSample = sampleHistory.lastOrNull()
        val windowNanos = sampleWindowMs * 1_000_000L
        sampleHistory.addLast(TimestampedPoint(currentTs, currentPoint))
        while (sampleHistory.isNotEmpty() && (currentTs - sampleHistory.first().tsNanos) > windowNanos) {
            sampleHistory.removeFirst()
        }

        if (!isArmed) {
            if (!isInCooldown && previousSample != null) {
                val dt = (currentTs - previousSample.tsNanos) / 1_000_000_000f
                if (dt > 0f) {
                    val dx = currentPoint.x - previousSample.pt.x
                    val dy = currentPoint.y - previousSample.pt.y
                    val recentSpeed = sqrt(dx * dx + dy * dy) / dt
                    if (recentSpeed < rearmSpeedThreshold) {
                        // Hand motion has settled below the threshold: re-arm for next gesture
                        isArmed = true
                        sampleHistory.clear()
                        sampleHistory.addLast(TimestampedPoint(currentTs, currentPoint))
                    }
                }
            }
            // If still in cooldown or hand is still moving rapidly from previous gesture: do not trigger
            return emptyList()
        }

        if (sampleHistory.size < 2) {
            return emptyList()
        }

        // 6. Compute displacement, duration, and speed across the active window
        val startSample = sampleHistory.first()
        val deltaX = currentPoint.x - startSample.pt.x
        val deltaY = currentPoint.y - startSample.pt.y
        val deltaNanos = currentTs - startSample.tsNanos
        if (deltaNanos <= 0L) return emptyList()

        val deltaSec = deltaNanos / 1_000_000_000f
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
        val speed = distance / deltaSec

        // 8. Validate swipe criteria (speed and distance thresholds)
        if (distance < minDistance || speed < minSpeed) {
            return emptyList()
        }

        // 9. Determine primary direction
        // In image-space: x increases rightward, y increases downward
        val absDeltaX = abs(deltaX)
        val absDeltaY = abs(deltaY)

        val dir = if (absDeltaX > absDeltaY) {
            if (deltaX > 0f) Dir.RIGHT else Dir.LEFT
        } else {
            if (deltaY > 0f) Dir.DOWN else Dir.UP
        }

        // 10. Trigger swipe, disarm, and enter cooldown
        lastSwipeTsNanos = currentTs
        isArmed = false
        sampleHistory.clear()

        return listOf(Swipe(tsNanos = currentTs, dir = dir, speed = speed))
    }

    private fun selectHand(frame: HandsFrame, cal: Calibration) =
        frame.hands.firstOrNull { it.side == cal.dominantHand && it.score >= minConfidence }
            ?: frame.hands.firstOrNull { it.score >= minConfidence }
}
