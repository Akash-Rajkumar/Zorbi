package com.zorbi.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android AccessibilityService implementation for Zorbi.
 * Dispatches injected touch gestures to third-party applications and games,
 * and hosts HUD overlay views using [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY].
 */
open class ZorbiAccessibilityService : AccessibilityService(), OverlayHost {

    private var overlayHost: RealOverlayHost? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var lastDispatchedGesture: GestureDescription? = null
        internal set

    val gestureDispatchHistory: MutableList<GestureDescription> = mutableListOf()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "ZorbiAccessibilityService connected")
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayHost = RealOverlayHost(wm)
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event processing placeholder per contract
    }

    override fun onInterrupt() {
        Log.w(TAG, "ZorbiAccessibilityService interrupted")
        _onInterrupted?.invoke()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "ZorbiAccessibilityService destroyed")
        overlayHost?.detachAll()
        overlayHost = null
        instance = null
        _isConnected.value = false
    }

    /**
     * Dispatches an asynchronous gesture description to the active screen.
     */
    open fun dispatch(
        gesture: GestureDescription,
        callback: GestureResultCallback? = null
    ): Boolean {
        lastDispatchedGesture = gesture
        synchronized(gestureDispatchHistory) {
            gestureDispatchHistory.add(gesture)
        }
        return try {
            val result = dispatchGesture(gesture, callback, mainHandler)
            if (!result) {
                Log.w(TAG, "dispatchGesture returned false: gesture rejected by system")
                callback?.onCancelled(gesture)
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception during dispatchGesture", e)
            callback?.onCancelled(gesture)
            false
        }
    }

    // OverlayHost implementation
    override fun attachOverlay(view: View, customParams: WindowManager.LayoutParams?): Boolean {
        return overlayHost?.attachOverlay(view, customParams) ?: false
    }

    override fun updateOverlay(view: View, params: WindowManager.LayoutParams): Boolean {
        return overlayHost?.updateOverlay(view, params) ?: false
    }

    override fun detachOverlay(view: View): Boolean {
        return overlayHost?.detachOverlay(view) ?: false
    }

    override fun detachAll() {
        overlayHost?.detachAll()
    }

    companion object {
        private const val TAG = "Zorbi/Accessibility"
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        var instance: ZorbiAccessibilityService? = null
            internal set

        var _onInterrupted: (() -> Unit)? = null

        fun setConnectedForTesting(service: ZorbiAccessibilityService?) {
            instance = service
            _isConnected.value = (service != null)
        }
    }
}
