package com.zorbi.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.WindowManager
import com.zorbi.core.InputInjector
import com.zorbi.core.Pt
import kotlinx.coroutines.flow.StateFlow

/**
 * Production implementation of [InputInjector] using Android's [AccessibilityService.dispatchGesture].
 *
 * Implements:
 * 1. Dynamic screen coordinate resolution from active WindowMetrics/DisplayMetrics (no hardcoded dimensions).
 * 2. Full touch event lifecycle: tap, swipe, path (slash), holdAt, dragTo, release.
 * 3. Safe error handling when AccessibilityService is disconnected or dispatching fails.
 * 4. Continuing strokes via [GestureDescription.StrokeDescription.continueStroke] for hold/drag motions.
 */
class RealInputInjector(
    private val serviceProvider: () -> AccessibilityService? = { ZorbiAccessibilityService.instance },
    private val dimensionProvider: (() -> Pair<Float, Float>)? = null
) : InputInjector {

    companion object {
        private const val TAG = "RealInputInjector"
        const val DEFAULT_TAP_DURATION_MS = 35L
        const val DEFAULT_HOLD_INTERVAL_MS = 30L
        const val MIN_GESTURE_DURATION_MS = 15L
    }

    override val isReady: StateFlow<Boolean> = ZorbiAccessibilityService.isConnected

    private var activeStroke: GestureDescription.StrokeDescription? = null
    private var lastPt: Pt? = null
    @Volatile private var isGestureInProgress = false
    @Volatile private var isHolding = false
    @Volatile private var pendingPt: Pt? = null
    @Volatile private var pendingIsDrag = false

    /**
     * Resolves the current screen dimensions dynamically based on active display configuration.
     * Accurately adapts when rotating between Portrait and Landscape (e.g. Hill Climb Racing).
     */
    fun getScreenBounds(): Pair<Float, Float> {
        if (dimensionProvider != null) {
            return dimensionProvider.invoke()
        }
        val service = serviceProvider() ?: return Pair(1080f, 2400f)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wm = service.getSystemService(WindowManager::class.java)
                val bounds = wm?.currentWindowMetrics?.bounds
                if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    Pair(bounds.width().toFloat(), bounds.height().toFloat())
                } else {
                    val dm = service.resources.displayMetrics
                    Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
                }
            } else {
                val dm = service.resources.displayMetrics
                Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
            }
        } catch (e: Exception) {
            val dm = service.resources.displayMetrics
            Pair(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
        }
    }

    /**
     * Converts normalized coordinates (0..1) into current physical display pixel coordinates.
     */
    fun mapToPixels(normalized: Pt): Pair<Float, Float> {
        val (w, h) = getScreenBounds()
        val px = (normalized.x * w).coerceIn(0f, w)
        val py = (normalized.y * h).coerceIn(0f, h)
        return Pair(px, py)
    }

    override fun tap(p: Pt) {
        val service = serviceProvider()
        if (service == null) {
            Log.w(TAG, "Cannot tap: ZorbiAccessibilityService is not connected")
            return
        }
        val (x, y) = mapToPixels(p)
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, DEFAULT_TAP_DURATION_MS)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch tap gesture at ($x, $y)", e)
        }
    }

    override fun swipe(from: Pt, to: Pt, durationMs: Long) {
        val service = serviceProvider()
        if (service == null) {
            Log.w(TAG, "Cannot swipe: ZorbiAccessibilityService is not connected")
            return
        }
        val (startX, startY) = mapToPixels(from)
        val (endX, endY) = mapToPixels(to)
        try {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val dur = durationMs.coerceIn(MIN_GESTURE_DURATION_MS, 1500L)
            val stroke = GestureDescription.StrokeDescription(path, 0L, dur)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val dx = endX - startX
            val dy = endY - startY
            val dirStr = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                if (dx > 0) "RIGHT" else "LEFT"
            } else {
                if (dy > 0) "DOWN" else "UP"
            }
            ZorbiAccessibilityService.instance?.showFeedback("⚡ SWIPE $dirStr")

            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Touch stroke injected successfully: ($startX, $startY) -> ($endX, $endY)")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Touch stroke cancelled by system: ($startX, $startY) -> ($endX, $endY)")
                }
            }
            val dispatched = service.dispatchGesture(gesture, callback, null)
            Log.i(TAG, "Dispatched swipe to accessibility service: $dispatched")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch swipe gesture from ($startX, $startY) to ($endX, $endY)", e)
        }
    }

    override fun path(points: List<Pt>, durationMs: Long) {
        val service = serviceProvider()
        if (service == null || points.isEmpty()) return
        if (points.size == 1) {
            tap(points.first())
            return
        }
        try {
            val path = Path().apply {
                val (firstX, firstY) = mapToPixels(points.first())
                moveTo(firstX, firstY)
                for (i in 1 until points.size) {
                    val (px, py) = mapToPixels(points[i])
                    lineTo(px, py)
                }
            }
            val dur = durationMs.coerceIn(MIN_GESTURE_DURATION_MS, 1500L)
            val stroke = GestureDescription.StrokeDescription(path, 0L, dur)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch path gesture (${points.size} pts)", e)
        }
    }

    override fun holdAt(p: Pt) {
        val service = serviceProvider() ?: return
        isHolding = true
        val (x, y) = mapToPixels(p)
        val target = Pt(x, y)

        if (isGestureInProgress) {
            // Coalesce: save freshest target; do NOT flood Android's gesture queue
            pendingPt = target
            pendingIsDrag = false
            return
        }

        dispatchHoldOrDrag(service, target, isDrag = false)
    }

    override fun dragTo(p: Pt) {
        val service = serviceProvider() ?: return
        isHolding = true
        val (newX, newY) = mapToPixels(p)
        val target = Pt(newX, newY)

        if (isGestureInProgress) {
            // Coalesce: save freshest target; do NOT flood Android's gesture queue
            pendingPt = target
            pendingIsDrag = true
            return
        }

        dispatchHoldOrDrag(service, target, isDrag = true)
    }

    private fun dispatchHoldOrDrag(service: AccessibilityService, target: Pt, isDrag: Boolean) {
        try {
            val fromX = lastPt?.x ?: target.x
            val fromY = lastPt?.y ?: target.y
            val path = Path().apply {
                moveTo(fromX, fromY)
                if (isDrag || fromX != target.x || fromY != target.y) {
                    lineTo(target.x, target.y)
                } else {
                    // Infinitesimal micro-step to ensure non-empty path across OEM frameworks
                    lineTo(target.x, target.y + 0.05f)
                }
            }

            val stroke = if (activeStroke != null) {
                try {
                    activeStroke?.continueStroke(path, 0L, DEFAULT_HOLD_INTERVAL_MS, true)
                } catch (e: Exception) {
                    GestureDescription.StrokeDescription(path, 0L, DEFAULT_HOLD_INTERVAL_MS, true)
                }
            } else {
                GestureDescription.StrokeDescription(path, 0L, DEFAULT_HOLD_INTERVAL_MS, true)
            }

            if (stroke != null) {
                activeStroke = stroke
                lastPt = target
                isGestureInProgress = true

                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        onGestureFinished(service, success = true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        onGestureFinished(service, success = false)
                    }
                }
                val dispatched = service.dispatchGesture(gesture, callback, null)
                if (!dispatched) {
                    isGestureInProgress = false
                    activeStroke = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch gesture to (${target.x}, ${target.y})", e)
            activeStroke = null
            isGestureInProgress = false
        }
    }

    private fun onGestureFinished(service: AccessibilityService, success: Boolean) {
        isGestureInProgress = false
        if (!success) {
            activeStroke = null
        }
        if (!isHolding) {
            performFinalRelease(service)
            return
        }
        val nextPt = pendingPt
        if (nextPt != null) {
            pendingPt = null
            val isDrag = pendingIsDrag
            dispatchHoldOrDrag(service, nextPt, isDrag)
        }
    }

    override fun release() {
        isHolding = false
        pendingPt = null
        val service = serviceProvider()
        if (service != null && !isGestureInProgress) {
            performFinalRelease(service)
        }
    }

    private fun performFinalRelease(service: AccessibilityService) {
        if (activeStroke != null && lastPt != null) {
            try {
                val endPath = Path().apply {
                    moveTo(lastPt!!.x, lastPt!!.y)
                    lineTo(lastPt!!.x, lastPt!!.y + 0.05f)
                }
                val finishStroke = activeStroke?.continueStroke(endPath, 0L, MIN_GESTURE_DURATION_MS, false)
                if (finishStroke != null) {
                    val gesture = GestureDescription.Builder().addStroke(finishStroke).build()
                    service.dispatchGesture(gesture, null, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispatch release stroke", e)
            }
        }
        activeStroke = null
        lastPt = null
        isGestureInProgress = false
    }
}
