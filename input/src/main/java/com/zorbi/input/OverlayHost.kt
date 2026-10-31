package com.zorbi.input

import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Manages overlay windows hosted by an AccessibilityService using
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY].
 *
 * Allows hosting HUD overlays directly over games without requiring the
 * SYSTEM_ALERT_WINDOW ("Draw over other apps") permission.
 */
interface OverlayHost {

    fun attachOverlay(view: View, customParams: WindowManager.LayoutParams? = null): Boolean

    fun updateOverlay(view: View, params: WindowManager.LayoutParams): Boolean

    fun detachOverlay(view: View): Boolean

    fun detachAll()

    companion object {
        fun createDefaultLayoutParams(): WindowManager.LayoutParams {
            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        }
    }
}

class RealOverlayHost(
    private val windowManager: WindowManager
) : OverlayHost {

    private val attachedViews = mutableSetOf<View>()

    override fun attachOverlay(view: View, customParams: WindowManager.LayoutParams?): Boolean {
        synchronized(attachedViews) {
            if (attachedViews.contains(view)) {
                Log.w(TAG, "View is already attached to overlay host: $view")
                return false
            }
            return try {
                val params = customParams ?: OverlayHost.createDefaultLayoutParams()
                windowManager.addView(view, params)
                attachedViews.add(view)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach overlay view", e)
                false
            }
        }
    }

    override fun updateOverlay(view: View, params: WindowManager.LayoutParams): Boolean {
        synchronized(attachedViews) {
            if (!attachedViews.contains(view)) {
                Log.w(TAG, "Cannot update view that is not attached: $view")
                return false
            }
            return try {
                windowManager.updateViewLayout(view, params)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update overlay view", e)
                false
            }
        }
    }

    override fun detachOverlay(view: View): Boolean {
        synchronized(attachedViews) {
            if (!attachedViews.contains(view)) {
                return false
            }
            return try {
                windowManager.removeViewImmediate(view)
                attachedViews.remove(view)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to detach overlay view", e)
                false
            }
        }
    }

    override fun detachAll() {
        synchronized(attachedViews) {
            val iterator = attachedViews.iterator()
            while (iterator.hasNext()) {
                val view = iterator.next()
                try {
                    windowManager.removeViewImmediate(view)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to detach overlay view during cleanup", e)
                }
                iterator.remove()
            }
        }
    }

    companion object {
        private const val TAG = "Zorbi/OverlayHost"
    }
}

