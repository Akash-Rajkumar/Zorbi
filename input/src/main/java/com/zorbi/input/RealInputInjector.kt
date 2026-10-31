package com.zorbi.input

import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.res.Resources
import android.util.Log
import com.zorbi.core.InputInjector
import com.zorbi.core.Pt
import kotlinx.coroutines.flow.StateFlow

/**
 * Production implementation of [InputInjector] using Android's AccessibilityService framework.
 * Dispatches touch events (taps, directional swipes, multi-point paths, and continued hold/drag strokes)
 * into third-party games and apps without requiring root or game modifications.
 *
 * Coordinates received are normalized screen-space points (0..1), which are converted dynamically
 * to current display pixel coordinates.
 */
class RealInputInjector(
    private val serviceProvider: () -> ZorbiAccessibilityService? = { ZorbiAccessibilityService.instance },
    private val displayBoundsProvider: () -> Pair<Int, Int> = {
        val service = ZorbiAccessibilityService.instance
        val dm = try {
            service?.resources?.displayMetrics ?: Resources.getSystem().displayMetrics
        } catch (e: Exception) {
            Resources.getSystem().displayMetrics
        }
        Pair(dm.widthPixels, dm.heightPixels)
    }
) : InputInjector {

    private val lock = Any()

    // Continued stroke tracking state
    private var activeStroke: GestureDescription.StrokeDescription? = null
    private var lastScreenPoint: ScreenPoint? = null
    private var isHolding: Boolean = false

    override val isReady: StateFlow<Boolean>
        get() = ZorbiAccessibilityService.isConnected

    init {
        // Register hook to clear continued strokes if service is interrupted
        ZorbiAccessibilityService._onInterrupted = {
            synchronized(lock) {
                resetStrokeStateLocked()
            }
        }
    }

    override fun tap(p: Pt) {
        synchronized(lock) {
            val service = getConnectedServiceOrWarn("tap") ?: return
            ensurePreviousStrokeReleasedLocked(service)

            val (width, height) = displayBoundsProvider()
            val point = GesturePathBuilder.toPixel(p, width, height)
            val path = GesturePathBuilder.createTapPath(point)

            val stroke = GestureDescription.StrokeDescription(
                path,
                0L,
                GesturePathBuilder.DEFAULT_TAP_DURATION_MS,
                false
            )

            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            service.dispatch(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.v(TAG, "tap completed at ($point.x, $point.y)")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "tap cancelled at ($point.x, $point.y)")
                }
            })
        }
    }

    override fun swipe(from: Pt, to: Pt, durationMs: Long) {
        synchronized(lock) {
            val service = getConnectedServiceOrWarn("swipe") ?: return
            ensurePreviousStrokeReleasedLocked(service)

            val (width, height) = displayBoundsProvider()
            val fromPoint = GesturePathBuilder.toPixel(from, width, height)
            val toPoint = GesturePathBuilder.toPixel(to, width, height)
            val path = GesturePathBuilder.createLinePath(fromPoint, toPoint)
            val clampedDuration = GesturePathBuilder.clampDuration(durationMs, 150L)

            val stroke = GestureDescription.StrokeDescription(
                path,
                0L,
                clampedDuration,
                false
            )

            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            service.dispatch(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.v(TAG, "swipe completed from $fromPoint to $toPoint in ${clampedDuration}ms")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "swipe cancelled from $fromPoint to $toPoint")
                }
            })
        }
    }

    override fun path(points: List<Pt>, durationMs: Long) {
        if (points.isEmpty()) {
            Log.w(TAG, "path called with empty points list; ignoring")
            return
        }

        synchronized(lock) {
            val service = getConnectedServiceOrWarn("path") ?: return
            ensurePreviousStrokeReleasedLocked(service)

            val (width, height) = displayBoundsProvider()
            val pixelPoints = points.map { GesturePathBuilder.toPixel(it, width, height) }
            val path = GesturePathBuilder.createMultiPointPath(pixelPoints)
            val clampedDuration = GesturePathBuilder.clampDuration(durationMs, 200L)

            val stroke = GestureDescription.StrokeDescription(
                path,
                0L,
                clampedDuration,
                false
            )

            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            service.dispatch(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.v(TAG, "multipoint path completed (${pixelPoints.size} points in ${clampedDuration}ms)")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "multipoint path cancelled")
                }
            })
        }
    }

    override fun holdAt(p: Pt) {
        synchronized(lock) {
            val service = getConnectedServiceOrWarn("holdAt") ?: return
            ensurePreviousStrokeReleasedLocked(service)

            val (width, height) = displayBoundsProvider()
            val point = GesturePathBuilder.toPixel(p, width, height)
            val path = GesturePathBuilder.createTapPath(point)

            // Start continued stroke with willContinue = true
            val stroke = GestureDescription.StrokeDescription(
                path,
                0L,
                GesturePathBuilder.DEFAULT_HOLD_INTERVAL_MS,
                true
            )

            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            activeStroke = stroke
            lastScreenPoint = point
            isHolding = true

            service.dispatch(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.v(TAG, "holdAt initial stroke completed at $point")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "holdAt stroke cancelled at $point")
                    synchronized(lock) {
                        resetStrokeStateLocked()
                    }
                }
            })
        }
    }

    override fun dragTo(p: Pt) {
        synchronized(lock) {
            val service = getConnectedServiceOrWarn("dragTo") ?: return

            val currentStroke = activeStroke
            val fromPoint = lastScreenPoint

            if (!isHolding || currentStroke == null || fromPoint == null) {
                Log.d(TAG, "dragTo called without active hold; starting new hold at point")
                holdAt(p)
                return
            }

            val (width, height) = displayBoundsProvider()
            val targetPoint = GesturePathBuilder.toPixel(p, width, height)
            val path = GesturePathBuilder.createLinePath(fromPoint, targetPoint)

            val nextStroke = currentStroke.continueStroke(
                path,
                0L,
                GesturePathBuilder.DEFAULT_DRAG_INTERVAL_MS,
                true
            )

            val gesture = GestureDescription.Builder()
                .addStroke(nextStroke)
                .build()

            activeStroke = nextStroke
            lastScreenPoint = targetPoint

            service.dispatch(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.v(TAG, "dragTo stroke completed from $fromPoint to $targetPoint")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "dragTo stroke cancelled")
                    synchronized(lock) {
                        resetStrokeStateLocked()
                    }
                }
            })
        }
    }

    override fun release() {
        synchronized(lock) {
            val service = serviceProvider()
            if (service == null || !isReady.value) {
                resetStrokeStateLocked()
                return
            }

            val currentStroke = activeStroke
            val point = lastScreenPoint

            if (!isHolding || currentStroke == null || point == null) {
                resetStrokeStateLocked()
                return
            }

            val path = GesturePathBuilder.createTapPath(point)

            // Terminate continued stroke with willContinue = false
            val finalStroke = currentStroke.continueStroke(
                path,
                0L,
                GesturePathBuilder.DEFAULT_RELEASE_INTERVAL_MS,
                false
            )

            val gesture = GestureDescription.Builder()
                .addStroke(finalStroke)
                .build()

            resetStrokeStateLocked()

            service.dispatch(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.v(TAG, "release completed at $point")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "release cancelled at $point")
                }
            })
        }
    }

    private fun ensurePreviousStrokeReleasedLocked(service: ZorbiAccessibilityService) {
        if (isHolding && activeStroke != null && lastScreenPoint != null) {
            val point = lastScreenPoint!!
            val path = GesturePathBuilder.createTapPath(point)
            try {
                val terminatingStroke = activeStroke!!.continueStroke(
                    path,
                    0L,
                    GesturePathBuilder.DEFAULT_RELEASE_INTERVAL_MS,
                    false
                )
                val gesture = GestureDescription.Builder()
                    .addStroke(terminatingStroke)
                    .build()
                service.dispatch(gesture, null)
            } catch (e: Exception) {
                Log.w(TAG, "Error cleanly finalizing previous continued stroke", e)
            } finally {
                resetStrokeStateLocked()
            }
        }
    }

    private fun resetStrokeStateLocked() {
        activeStroke = null
        lastScreenPoint = null
        isHolding = false
    }

    private fun getConnectedServiceOrWarn(action: String): ZorbiAccessibilityService? {
        val service = serviceProvider()
        if (service == null || !isReady.value) {
            Log.w(TAG, "Cannot perform $action: ZorbiAccessibilityService is not connected")
            return null
        }
        return service
    }

    companion object {
        private const val TAG = "Zorbi/InputInjector"
    }
}

