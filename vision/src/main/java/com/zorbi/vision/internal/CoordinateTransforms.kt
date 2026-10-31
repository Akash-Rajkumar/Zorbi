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

    /**
     * Clockwise rotation mapping for normalized coordinates [0..1].
     */
    fun rotateNormalizedPoint(x: Float, y: Float, z: Float = 0f, rotationDegrees: Int): Pt {
        val normRot = ((rotationDegrees % 360) + 360) % 360
        return when (normRot) {
            0 -> Pt(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), z)
            90 -> Pt((1.0f - y).coerceIn(0f, 1f), x.coerceIn(0f, 1f), z)
            180 -> Pt((1.0f - x).coerceIn(0f, 1f), (1.0f - y).coerceIn(0f, 1f), z)
            270 -> Pt(y.coerceIn(0f, 1f), (1.0f - x).coerceIn(0f, 1f), z)
            else -> throw IllegalArgumentException("Unsupported rotationDegrees: $rotationDegrees; must be 0, 90, 180, or 270")
        }
    }

    enum class ScaleType {
        FILL,       // Stretch to fill dst bounds
        CENTER_CROP,// Scale uniformly so dst is filled, cropping excess
        FIT_CENTER  // Scale uniformly with letterbox / pillarbox
    }

    /**
     * Maps a normalized coordinate [0..1] in source aspect ratio into destination view space.
     *
     * @return Transformed pixel coordinate (X in [0..dstWidth], Y in [0..dstHeight])
     */
    fun mapToViewSpace(
        pt: Pt,
        srcWidth: Float,
        srcHeight: Float,
        dstWidth: Float,
        dstHeight: Float,
        scaleType: ScaleType = ScaleType.FILL
    ): Pt {
        require(srcWidth > 0f && srcHeight > 0f && dstWidth > 0f && dstHeight > 0f) {
            "Dimensions must be positive"
        }
        val clampedX = pt.x.coerceIn(0f, 1f)
        val clampedY = pt.y.coerceIn(0f, 1f)

        return when (scaleType) {
            ScaleType.FILL -> {
                Pt(x = clampedX * dstWidth, y = clampedY * dstHeight, z = pt.z)
            }
            ScaleType.CENTER_CROP -> {
                val scale = maxOf(dstWidth / srcWidth, dstHeight / srcHeight)
                val scaledW = srcWidth * scale
                val scaledH = srcHeight * scale
                val offsetX = (dstWidth - scaledW) / 2f
                val offsetY = (dstHeight - scaledH) / 2f
                Pt(
                    x = clampedX * scaledW + offsetX,
                    y = clampedY * scaledH + offsetY,
                    z = pt.z
                )
            }
            ScaleType.FIT_CENTER -> {
                val scale = minOf(dstWidth / srcWidth, dstHeight / srcHeight)
                val scaledW = srcWidth * scale
                val scaledH = srcHeight * scale
                val offsetX = (dstWidth - scaledW) / 2f
                val offsetY = (dstHeight - scaledH) / 2f
                Pt(
                    x = clampedX * scaledW + offsetX,
                    y = clampedY * scaledH + offsetY,
                    z = pt.z
                )
            }
        }
    }
}
