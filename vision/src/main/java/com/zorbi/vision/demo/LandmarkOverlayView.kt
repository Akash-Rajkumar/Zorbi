package com.zorbi.vision.demo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.zorbi.core.Hand

/**
 * Custom overlay view for rendering detected hand landmarks and skeleton connections
 * on top of the live camera preview.
 */
class LandmarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    @Volatile
    private var currentHands: List<Hand> = emptyList()

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676") // Bright neon green
        style = Paint.Style.FILL
    }

    private val pointBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B0FF") // Cyan connecting lines
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    // Standard MediaPipe 21 hand landmark connections
    private val connections = listOf(
        // Palm
        0 to 1, 0 to 5, 5 to 9, 9 to 13, 13 to 17, 0 to 17,
        // Thumb
        1 to 2, 2 to 3, 3 to 4,
        // Index finger
        5 to 6, 6 to 7, 7 to 8,
        // Middle finger
        9 to 10, 10 to 11, 11 to 12,
        // Ring finger
        13 to 14, 14 to 15, 15 to 16,
        // Pinky
        17 to 18, 18 to 19, 19 to 20
    )

    fun updateHands(hands: List<Hand>) {
        this.currentHands = hands
        postInvalidate()
    }

    fun clear() {
        this.currentHands = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        for (hand in currentHands) {
            val pts = hand.landmarks
            if (pts.size != 21) continue

            // 1. Draw connecting skeleton lines
            for ((fromIdx, toIdx) in connections) {
                if (fromIdx < pts.size && toIdx < pts.size) {
                    val p1 = pts[fromIdx]
                    val p2 = pts[toIdx]
                    canvas.drawLine(p1.x * w, p1.y * h, p2.x * w, p2.y * h, linePaint)
                }
            }

            // 2. Draw landmark points
            for (pt in pts) {
                val cx = pt.x * w
                val cy = pt.y * h
                canvas.drawCircle(cx, cy, 10f, pointPaint)
                canvas.drawCircle(cx, cy, 10f, pointBorderPaint)
            }
        }
    }
}

