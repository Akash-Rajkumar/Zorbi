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
    private data class CandidateStroke(val startSample: TimestampedPoint, val deltaX: Float, val deltaY: Float, val distance: Float, val speed: Float, val windowMs: Long, val dir: Dir)

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

    private fun diagLog(msg: String) {
        try {
            android.util.Log.i("Zorbi/Diagnostic", msg)
        } catch (_: Throwable) {}
    }

    override fun onFrame(
        frame: TrackerFrame,
        profile: GameProfile,
        cal: Calibration
    ): List<GestureEvent> {
        // 1. Validate frame type
        if (frame !is HandsFrame) {
            diagLog("Drop: Frame is not HandsFrame: ${frame::class.simpleName}")
            return emptyList()
        }

        // 2. Monotonicity and timestamp validation
        val currentTs = frame.tsNanos
        if (lastFrameTsNanos > 0L) {
            if (currentTs <= lastFrameTsNanos) {
                diagLog("Drop: Stale frame currentTs=$currentTs <= last=$lastFrameTsNanos")
                return emptyList()
            }
            val gapNanos = currentTs - lastFrameTsNanos
            if (gapNanos > maxTrackingGapMs * 1_000_000L) {
                diagLog("Gap detected: ${gapNanos / 1_000_000}ms > ${maxTrackingGapMs}ms; resetting history")
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
            GestureDiagnostics.update { it.copy(handDetected = false, status = "No hands in frame") }
            return emptyList()
        }

        val hand = selectHand(frame, cal)
        if (hand == null || hand.score < minConfidence || hand.landmarks.size < 21) {
            sampleHistory.clear()
            val reason = if (hand == null) "No matching hand (cal=${cal.dominantHand})" else "score=${hand.score} < $minConfidence or lms=${hand.landmarks.size}"
            diagLog("Drop: $reason")
            GestureDiagnostics.update { it.copy(handDetected = false, status = "Hand rejected: $reason") }
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
        // Calibrate effective parameters for Subway Surfers profile without altering global defaults
        val effectiveMinDistance = if (minDistance == 0.08f && profile.packageName == "com.kiloo.subwaysurf") 0.035f else minDistance
        val effectiveSampleWindowMs = if (sampleWindowMs == 200L && profile.packageName == "com.kiloo.subwaysurf") 300L else sampleWindowMs

        val windowNanos = effectiveSampleWindowMs * 1_000_000L
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
                        isArmed = true
                        sampleHistory.clear()
                        sampleHistory.addLast(TimestampedPoint(currentTs, currentPoint))
                        diagLog("Re-armed: speed settled to $recentSpeed < $rearmSpeedThreshold")
                    }
                }
            }
            val statusStr = if (isInCooldown) "Cooldown: ${(cooldownNanos - timeSinceLastSwipe) / 1_000_000}ms left" else "Disarmed: settling motion"
            updateDiag(hand, wrist, middleMcp, currentPoint, 0f, 0f, 0f, 0f, effectiveMinDistance, minSpeed, 0L, isArmed, "None", statusStr)
            return emptyList()
        }

        if (sampleHistory.size < 2) {
            updateDiag(hand, wrist, middleMcp, currentPoint, 0f, 0f, 0f, 0f, effectiveMinDistance, minSpeed, 0L, isArmed, "None", "Buffering (1 sample)")
            return emptyList()
        }

        // 6. Adaptive Early Gesture Evaluation
        val fullStartSample = sampleHistory.first()
        val fullDeltaNanos = currentTs - fullStartSample.tsNanos
        val fullWindowMs = fullDeltaNanos / 1_000_000L
        val fullDeltaX = currentPoint.x - fullStartSample.pt.x
        val fullDeltaY = currentPoint.y - fullStartSample.pt.y
        val fullDistance = sqrt(fullDeltaX * fullDeltaX + fullDeltaY * fullDeltaY)
        val fullSpeed = if (fullDeltaNanos > 0L) fullDistance / (fullDeltaNanos / 1_000_000_000f) else 0f
        val fullAbsX = abs(fullDeltaX)
        val fullAbsY = abs(fullDeltaY)
        val fullCandDir = if (fullAbsX > fullAbsY) (if (fullDeltaX > 0f) Dir.RIGHT else Dir.LEFT) else (if (fullDeltaY > 0f) Dir.DOWN else Dir.UP)

        var matchedCandidate: CandidateStroke? = null

        // Scan candidate onset points from oldest to newest (minimum 40ms sub-window)
        for (i in 0 until (sampleHistory.size - 1)) {
            val s = sampleHistory[i]
            val dtNanos = currentTs - s.tsNanos
            val dtMs = dtNanos / 1_000_000L
            if (dtMs < 40L) continue

            val dx = currentPoint.x - s.pt.x
            val dy = currentPoint.y - s.pt.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < effectiveMinDistance) continue

            val dtSec = dtNanos / 1_000_000_000f
            val spd = dist / dtSec
            if (spd < minSpeed) continue

            val absDx = abs(dx)
            val absDy = abs(dy)
            val candDir = if (absDx > absDy) (if (dx > 0f) Dir.RIGHT else Dir.LEFT) else (if (dy > 0f) Dir.DOWN else Dir.UP)

            // Directional dominance: primary axis >= 1.15x secondary axis
            val isAxisDominant = if (candDir == Dir.LEFT || candDir == Dir.RIGHT) {
                absDx >= 1.15f * absDy
            } else {
                absDy >= 1.15f * absDx
            }
            if (!isAxisDominant) continue

            // Directional consistency: intermediate points must not reverse > 0.015
            var hasReversal = false
            for (k in (i + 1) until sampleHistory.size) {
                val ptK = sampleHistory[k].pt
                when (candDir) {
                    Dir.LEFT -> if (ptK.x > s.pt.x + 0.015f) hasReversal = true
                    Dir.RIGHT -> if (ptK.x < s.pt.x - 0.015f) hasReversal = true
                    Dir.UP -> if (ptK.y > s.pt.y + 0.015f) hasReversal = true
                    Dir.DOWN -> if (ptK.y < s.pt.y - 0.015f) hasReversal = true
                }
                if (hasReversal) break
            }
            if (hasReversal) continue

            matchedCandidate = CandidateStroke(
                startSample = s,
                deltaX = dx,
                deltaY = dy,
                distance = dist,
                speed = spd,
                windowMs = dtMs,
                dir = candDir
            )
            break
        }

        diagLog("Frame: palm=(${String.format(java.util.Locale.US, "%.2f,%.2f", currentPoint.x, currentPoint.y)}) wrist=(${String.format(java.util.Locale.US, "%.2f,%.2f", wrist.x, wrist.y)}) | dX=${String.format(java.util.Locale.US, "%.2f", fullDeltaX)} dY=${String.format(java.util.Locale.US, "%.2f", fullDeltaY)} dist=${String.format(java.util.Locale.US, "%.3f", fullDistance)} spd=${String.format(java.util.Locale.US, "%.2f", fullSpeed)} win=${fullWindowMs}ms n=${sampleHistory.size} dir=$fullCandDir early=${matchedCandidate != null} armed=$isArmed")

        if (matchedCandidate == null) {
            val reason = when {
                fullDistance < effectiveMinDistance -> "dist ${String.format(java.util.Locale.US, "%.3f", fullDistance)} < $effectiveMinDistance"
                else -> "speed ${String.format(java.util.Locale.US, "%.2f", fullSpeed)} < $minSpeed"
            }
            updateDiag(hand, wrist, middleMcp, currentPoint, fullDeltaX, fullDeltaY, fullDistance, fullSpeed, effectiveMinDistance, minSpeed, fullWindowMs, isArmed, fullCandDir.name, "Evaluating: $reason")
            return emptyList()
        }

        val stroke = matchedCandidate
        val candDir = stroke.dir
        val distance = stroke.distance
        val speed = stroke.speed
        val windowMs = stroke.windowMs
        val deltaX = stroke.deltaX
        val deltaY = stroke.deltaY
        val onsetLatencyMs = (currentTs - stroke.startSample.tsNanos) / 1_000_000L

        // 10. Trigger swipe, disarm, and enter cooldown
        lastSwipeTsNanos = currentTs
        isArmed = false
        sampleHistory.clear()

        diagLog(">>> SWIPE TRIGGERED (ADAPTIVE EARLY): $candDir | dist=${String.format(java.util.Locale.US, "%.3f", distance)} spd=${String.format(java.util.Locale.US, "%.2f", speed)} win=${windowMs}ms onsetLatency=${onsetLatencyMs}ms <<<")

        val swipeEvent = Swipe(tsNanos = currentTs, dir = candDir, speed = speed)
        GestureDiagnostics.update {
            it.copy(
                handDetected = true,
                handSide = hand.side.name,
                handScore = hand.score,
                wristX = wrist.x, wristY = wrist.y,
                middleMcpX = middleMcp.x, middleMcpY = middleMcp.y,
                palmX = currentPoint.x, palmY = currentPoint.y,
                deltaX = deltaX, deltaY = deltaY,
                distance = distance, speed = speed,
                minDistance = effectiveMinDistance, minSpeed = minSpeed,
                windowDurationMs = windowMs,
                sampleCount = 0,
                isArmed = false,
                candidateDir = candDir.name,
                status = "TRIGGERED SWIPE ${candDir.name}",
                lastRecognizedGesture = "SWIPE ${candDir.name}",
                lastRecognizedGestureTs = currentTs
            )
        }

        return listOf(swipeEvent)
    }

    private fun updateDiag(
        hand: com.zorbi.core.Hand,
        wrist: Pt,
        middleMcp: Pt,
        palm: Pt,
        deltaX: Float,
        deltaY: Float,
        distance: Float,
        speed: Float,
        minDistanceVal: Float,
        minSpeed: Float,
        windowMs: Long,
        isArmed: Boolean,
        candidateDir: String,
        status: String
    ) {
        GestureDiagnostics.update {
            it.copy(
                handDetected = true,
                handSide = hand.side.name,
                handScore = hand.score,
                wristX = wrist.x, wristY = wrist.y,
                middleMcpX = middleMcp.x, middleMcpY = middleMcp.y,
                palmX = palm.x, palmY = palm.y,
                deltaX = deltaX, deltaY = deltaY,
                distance = distance, speed = speed,
                minDistance = minDistanceVal, minSpeed = minSpeed,
                windowDurationMs = windowMs,
                sampleCount = sampleHistory.size,
                isArmed = isArmed,
                candidateDir = candidateDir,
                status = status
            )
        }
    }

    private fun selectHand(frame: HandsFrame, cal: Calibration) =
        frame.hands.firstOrNull { it.side == cal.dominantHand && it.score >= minConfidence }
            ?: frame.hands.firstOrNull { it.score >= minConfidence }
}
