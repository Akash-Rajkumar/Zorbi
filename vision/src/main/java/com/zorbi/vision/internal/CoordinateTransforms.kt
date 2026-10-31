package com.zorbi.vision.internal

import com.zorbi.core.Pt
import com.zorbi.core.Side

object CoordinateTransforms {

    /**
     * Maps MediaPipe handedness category label to core [Side] enum.
     */
    fun toSide(categoryName: String?): Side = when (categoryName?.trim()?.lowercase()) {
        "left" -> Side.LEFT
        "right" -> Side.RIGHT
        else -> Side.UNKNOWN
    }

    /**
     * Converts normalized coordinate to core [Pt] format following frozen conventions:
     * - Origin top-left
     * - Normalized 0..1
     * - Mirrored horizontally for front camera (user's right hand appears on right of frame)
     */
    fun toPt(x: Float, y: Float, z: Float = 0f, mirrorHorizontally: Boolean = true): Pt {
        val mx = if (mirrorHorizontally) (1.0f - x).coerceIn(0.0f, 1.0f) else x.coerceIn(0.0f, 1.0f)
        val my = y.coerceIn(0.0f, 1.0f)
        return Pt(x = mx, y = my, z = z)
    }

    /**
     * Transforms a list of points with front-camera mirroring.
     */
    fun toCorePoints(
        points: List<Pt>,
        mirrorHorizontally: Boolean = true
    ): List<Pt> = points.map { toPt(it.x, it.y, it.z, mirrorHorizontally) }
}

