package com.zorbi.input

import android.graphics.Path
import com.zorbi.core.Pt

/**
 * Screen-space coordinate represented in actual display pixels.
 */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * Pure coordinate transformation and Path builder for touch injection.
 * Translates normalized screen-space points (0..1) into display pixel paths.
 */
object GesturePathBuilder {

    const val DEFAULT_TAP_DURATION_MS: Long = 50L
    const val MIN_GESTURE_DURATION_MS: Long = 10L
    const val MAX_GESTURE_DURATION_MS: Long = 5000L
    const val DEFAULT_HOLD_INTERVAL_MS: Long = 100L
    const val DEFAULT_DRAG_INTERVAL_MS: Long = 100L
    const val DEFAULT_RELEASE_INTERVAL_MS: Long = 40L

    /**
     * Converts a normalized screen-space point (0..1) into display pixel coordinates.
     * Coordinates are clamped to [0.0, 1.0] to prevent out-of-screen touch injection.
     */
    fun toPixel(p: Pt, displayWidth: Int, displayHeight: Int): ScreenPoint {
        require(displayWidth > 0) { "displayWidth must be positive: $displayWidth" }
        require(displayHeight > 0) { "displayHeight must be positive: $displayHeight" }

        val clampedX = p.x.coerceIn(0f, 1f)
        val clampedY = p.y.coerceIn(0f, 1f)

        return ScreenPoint(
            x = clampedX * displayWidth,
            y = clampedY * displayHeight
        )
    }

    /**
     * Clamps gesture duration to acceptable bounds for Android Accessibility dispatch.
     */
    fun clampDuration(durationMs: Long, defaultMs: Long = DEFAULT_TAP_DURATION_MS): Long {
        if (durationMs <= 0L) return defaultMs
        return durationMs.coerceIn(MIN_GESTURE_DURATION_MS, MAX_GESTURE_DURATION_MS)
    }

    /**
     * Creates an Android [Path] for a single tap at the given pixel point.
     * Android requires a path to have a non-zero length contour, so we connect the point to itself.
     */
    fun createTapPath(point: ScreenPoint): Path {
        return Path().apply {
            moveTo(point.x, point.y)
            lineTo(point.x, point.y)
        }
    }

    /**
     * Creates an Android [Path] for a directional line swipe between two pixel points.
     */
    fun createLinePath(from: ScreenPoint, to: ScreenPoint): Path {
        return Path().apply {
            moveTo(from.x, from.y)
            lineTo(to.x, to.y)
        }
    }

    /**
     * Creates an Android [Path] traversing a list of pixel points (e.g. for slashes).
     */
    fun createMultiPointPath(points: List<ScreenPoint>): Path {
        require(points.isNotEmpty()) { "Point list must not be empty" }
        return Path().apply {
            moveTo(points[0].x, points[0].y)
            if (points.size == 1) {
                lineTo(points[0].x, points[0].y)
            } else {
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
        }
    }
}

