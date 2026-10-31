package com.zorbi.gesture

import com.zorbi.core.Dir
import com.zorbi.core.GameProfile
import com.zorbi.core.Pt
import com.zorbi.core.Swipe

/**
 * Normalized screen-space touch coordinates corresponding to a gesture swipe.
 *
 * @property from Touchdown starting point in normalized screen coordinates (0..1).
 * @property to Liftoff ending point in normalized screen coordinates (0..1).
 * @property durationMs Duration of touch injection stroke in milliseconds.
 */
data class TouchSwipe(
    val from: Pt,
    val to: Pt,
    val durationMs: Long
)

/**
 * Maps high-level [Swipe] gesture events to normalized screen-space touch coordinates
 * for [com.zorbi.core.InputInjector.swipe] based on target points configured in [GameProfile.targets].
 *
 * Adheres strictly to the frozen [GameProfile] schema without inventing any external fields.
 */
object LaneSwipeMapper {
    const val DEFAULT_SWIPE_DURATION_MS: Long = 100L
    const val DEFAULT_OFFSET: Float = 0.3f

    fun map(swipe: Swipe, profile: GameProfile): TouchSwipe {
        val centerTarget = profile.targets["center"]
        val center = if (centerTarget != null) {
            Pt(centerTarget.x, centerTarget.y)
        } else {
            Pt(0.5f, 0.5f)
        }

        val destination = when (swipe.dir) {
            Dir.LEFT -> {
                profile.targets["left"]?.let { Pt(it.x, it.y) }
                    ?: Pt((center.x - DEFAULT_OFFSET).coerceAtLeast(0f), center.y)
            }
            Dir.RIGHT -> {
                profile.targets["right"]?.let { Pt(it.x, it.y) }
                    ?: Pt((center.x + DEFAULT_OFFSET).coerceAtMost(1f), center.y)
            }
            Dir.UP -> {
                profile.targets["up"]?.let { Pt(it.x, it.y) }
                    ?: Pt(center.x, (center.y - DEFAULT_OFFSET).coerceAtLeast(0f))
            }
            Dir.DOWN -> {
                profile.targets["down"]?.let { Pt(it.x, it.y) }
                    ?: Pt(center.x, (center.y + DEFAULT_OFFSET).coerceAtMost(1f))
            }
        }

        return TouchSwipe(
            from = center,
            to = destination,
            durationMs = DEFAULT_SWIPE_DURATION_MS
        )
    }
}

