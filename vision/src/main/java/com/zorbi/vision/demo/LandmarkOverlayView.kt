package com.zorbi.vision.demo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.zorbi.core.Hand
import com.zorbi.core.Pt

/**
 * Custom overlay view for rendering detected hand landmarks (21 pts) or pose landmarks (33 pts)
 * and skeleton connections on top of the live camera preview.
 */
class LandmarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    @Volatile
    private var currentHands: List<Hand> = emptyList()

    @Volatile
    private var currentPose: List<Pt> = emptyList()

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676") // Bright neon green
        style = Paint.Style.FILL
    }

    private val posePointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B") // Amber / orange for pose points
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

    private val poseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8") // Sky blue for pose skeleton
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    // Standard MediaPipe 21 hand landmark connections
    private val handConnections = listOf(
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

    // Standard MediaPipe 33 pose landmark connections
    private val poseConnections = listOf(
        // Face & Head
        0 to 1, 1 to 2, 2 to 3, 3 to 7,
        0 to 4, 4 to 5, 5 to 6, 6 to 8,
        9 to 10,
        // Torso
        11 to 12, 11 to 23, 12 to 24, 23 to 24,
        // Left Arm
        11 to 13, 13 to 15,
        15 to 17, 15 to 19, 15 to 21, 17 to 19,
        // Right Arm
        12 to 14, 14 to 16,
        16 to 18, 16 to 20, 16 to 22, 18 to 20,
        // Left Leg
        23 to 25, 25 to 27,
        27 to 29, 27 to 31, 29 to 31,
        // Right Leg
        24 to 26, 26 to 28,
        28 to 30, 28 to 32, 30 to 32
    )

    fun updateHands(hands: List<Hand>) {
        this.currentHands = hands
        this.currentPose = emptyList()
        postInvalidate()
    }

    fun updatePose(pose: List<Pt>) {
        this.currentPose = pose
        this.currentHands = emptyList()
        postInvalidate()
    }

    fun clear() {
        this.currentHands = emptyList()
        this.currentPose = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Render Hands if present
        for (hand in currentHands) {
            val pts = hand.landmarks
            if (pts.size != 21) continue

            for ((fromIdx, toIdx) in handConnections) {
                if (fromIdx < pts.size && toIdx < pts.size) {
                    val p1 = pts[fromIdx]
                    val p2 = pts[toIdx]
                    canvas.drawLine(p1.x * w, p1.y * h, p2.x * w, p2.y * h, linePaint)
                }
            }

            for (pt in pts) {
                val cx = pt.x * w
                val cy = pt.y * h
                canvas.drawCircle(cx, cy, 10f, pointPaint)
                canvas.drawCircle(cx, cy, 10f, pointBorderPaint)
            }
        }

        // 2. Render Pose if present (33 landmarks)
        val posePts = currentPose
        if (posePts.size >= 33) {
            // Draw skeleton lines
            for ((fromIdx, toIdx) in poseConnections) {
                if (fromIdx < posePts.size && toIdx < posePts.size) {
                    val p1 = posePts[fromIdx]
                    val p2 = posePts[toIdx]
                    canvas.drawLine(p1.x * w, p1.y * h, p2.x * w, p2.y * h, poseLinePaint)
                }
            }

            // Draw landmark joints
            for (pt in posePts) {
                val cx = pt.x * w
                val cy = pt.y * h
                canvas.drawCircle(cx, cy, 8f, posePointPaint)
                canvas.drawCircle(cx, cy, 8f, pointBorderPaint)
            }
        }
    }
}
