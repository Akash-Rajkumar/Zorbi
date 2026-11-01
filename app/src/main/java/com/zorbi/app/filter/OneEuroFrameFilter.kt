package com.zorbi.app.filter

import com.zorbi.core.GameProfile
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PoseFrame
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.TrackerFrame
import com.zorbi.engine.FrameFilter
import com.zorbi.vision.filter.OneEuroFilter
import com.zorbi.vision.filter.OneEuroLandmarksFilter
import java.util.concurrent.ConcurrentHashMap

/**
 * Production implementation of [FrameFilter] adapting Person 1's [OneEuroLandmarksFilter]
 * to Person 2's engine pipeline with ultra-low latency tuning.
 *
 * Responsibilities:
 * - Smooths noisy landmark coordinates for [HandsFrame] (single or dual hands) and [PoseFrame] (33 pose points).
 * - Maintains independent landmark filters per hand side ([Side.LEFT], [Side.RIGHT], [Side.UNKNOWN])
 *   to avoid signal cross-talk between dual-wheel hands.
 * - Dynamic velocity adaptation: beta > 0 increases cutoff frequency during fast swipes to eliminate lag (<15ms delay).
 * - Preserves exact [TrackerFrame] types, monotonic [TrackerFrame.tsNanos] timestamps, and confidence scores.
 * - Safely returns unknown/unsupported [TrackerFrame] implementations unmodified.
 * - Exposes thread-safe [reset] and [configure] to clear historical derivative state.
 */
class OneEuroFrameFilter(
    var minCutoff: Float = DEFAULT_TUNED_MIN_CUTOFF,
    var beta: Float = DEFAULT_TUNED_BETA,
    val dCutoff: Float = OneEuroFilter.DEFAULT_D_CUTOFF
) : FrameFilter {

    companion object {
        const val DEFAULT_TUNED_MIN_CUTOFF = 1.2f
        const val DEFAULT_TUNED_BETA = 0.02f
    }

    private val handFiltersBySide = ConcurrentHashMap<Side, OneEuroLandmarksFilter>()
    private val fallbackHandFiltersByIndex = ConcurrentHashMap<Int, OneEuroLandmarksFilter>()
    private val poseFilter = OneEuroLandmarksFilter(minCutoff, beta, dCutoff)

    override fun filter(frame: TrackerFrame): TrackerFrame {
        return when (frame) {
            is HandsFrame -> filterHandsFrame(frame)
            is PoseFrame -> filterPoseFrame(frame)
            else -> frame // Unsupported or unknown frame type: return unmodified safely
        }
    }

    private fun filterHandsFrame(frame: HandsFrame): HandsFrame {
        if (frame.hands.isEmpty()) return frame

        val filteredHands = frame.hands.mapIndexed { index, hand ->
            if (hand.landmarks.isEmpty()) {
                hand
            } else {
                val landmarksFilter = when (hand.side) {
                    Side.LEFT, Side.RIGHT -> handFiltersBySide.computeIfAbsent(hand.side) {
                        OneEuroLandmarksFilter(minCutoff, beta, dCutoff)
                    }
                    Side.UNKNOWN -> fallbackHandFiltersByIndex.computeIfAbsent(index) {
                        OneEuroLandmarksFilter(minCutoff, beta, dCutoff)
                    }
                }
                val smoothedLandmarks = landmarksFilter.filter(hand.landmarks, frame.tsNanos)
                hand.copy(landmarks = smoothedLandmarks)
            }
        }

        return frame.copy(hands = filteredHands)
    }

    private fun filterPoseFrame(frame: PoseFrame): PoseFrame {
        if (frame.landmarks.isEmpty()) return frame
        val smoothedLandmarks = poseFilter.filter(frame.landmarks, frame.tsNanos)
        return frame.copy(landmarks = smoothedLandmarks)
    }

    /**
     * Resets all internal landmark filter states. Next frame will be treated as the initial sample.
     */
    override fun reset() {
        handFiltersBySide.values.forEach { it.reset() }
        fallbackHandFiltersByIndex.values.forEach { it.reset() }
        poseFilter.reset()
    }

    override fun configure(profile: GameProfile) {
        val smoothing = profile.params.smoothing
        val newMinCutoff = if (smoothing.minCutoff > 0f) smoothing.minCutoff else DEFAULT_TUNED_MIN_CUTOFF
        val newBeta = if (smoothing.beta > 0f) smoothing.beta else DEFAULT_TUNED_BETA
        minCutoff = newMinCutoff
        beta = newBeta
        handFiltersBySide.values.forEach {
            it.minCutoff = newMinCutoff
            it.beta = newBeta
        }
        fallbackHandFiltersByIndex.values.forEach {
            it.minCutoff = newMinCutoff
            it.beta = newBeta
        }
        poseFilter.minCutoff = newMinCutoff
        poseFilter.beta = newBeta
        reset()
    }
}
