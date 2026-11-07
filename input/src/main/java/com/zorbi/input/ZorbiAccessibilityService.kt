package com.zorbi.input

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ZorbiAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null

    // Real-time alignment state
    private var currentAlignmentText: String = "🟢 ZORBI • MOTION READY"
    private var currentAlignmentStroke: String = "#00E5FF"
    private var currentAlignmentFill: String = "#E60A0E1A"
    private var isActionShowing: Boolean = false
    private var actionExpiryTimeMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        Log.i(TAG, "ZorbiAccessibilityService connected")
        initOverlay()
    }

    fun initOverlay() {
        mainHandler.post {
            try {
                if (windowManager == null) {
                    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                }
                if (overlayView == null && windowManager != null) {
                    val tv = TextView(this).apply {
                        text = currentAlignmentText
                        setTextColor(Color.parseColor(currentAlignmentStroke))
                        textSize = 12.5f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        setPadding(36, 14, 36, 14)

                        val bg = GradientDrawable().apply {
                            setColor(Color.parseColor(currentAlignmentFill))
                            cornerRadius = 28f
                            setStroke(3, Color.parseColor(currentAlignmentStroke))
                        }
                        background = bg
                        elevation = 30f
                        visibility = View.VISIBLE
                    }

                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        y = 48
                    }

                    windowManager?.addView(tv, params)
                    overlayView = tv
                    Log.i(TAG, "Zorbi overlay attached successfully")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create in-game overlay view", e)
            }
        }
    }

    /**
     * Updates real-time hand alignment & visibility banner at the top of the game screen.
     * Shows whether hands are visible, aligned in center, or if only 1 hand is present.
     */
    fun updateAlignmentStatus(
        statusText: String,
        strokeColorHex: String = "#00FF88",
        bgFillHex: String = "#E6002612"
    ) {
        mainHandler.post {
            currentAlignmentText = statusText
            currentAlignmentStroke = strokeColorHex
            currentAlignmentFill = bgFillHex

            val now = System.currentTimeMillis()
            if (!isActionShowing || now >= actionExpiryTimeMs) {
                isActionShowing = false
                renderOverlay(statusText, strokeColorHex, bgFillHex, isPulsing = false)
            }
        }
    }

    /**
     * Shows temporary high-visibility action feedback (Gas, Brake, Horn, Lights, Gear, Swipe).
     * Reverts to current alignment status once action completes.
     */
    fun showFeedback(message: String, isAction: Boolean = true) {
        mainHandler.post {
            try {
                if (overlayView == null) {
                    initOverlay()
                }
                val (bgFill, strokeColor, textColor) = when {
                    message.contains("GAS") -> Triple("#E6002612", "#00FF88", "#00FF88")
                    message.contains("BRAKE") -> Triple("#E62B050E", "#FF3366", "#FF3366")
                    message.contains("GEAR") -> Triple("#E60A1E33", "#00E5FF", "#00E5FF")
                    message.contains("HORN") || message.contains("SWIPE") -> Triple("#E62E2000", "#FFB703", "#FFB703")
                    message.contains("LIGHT") || message.contains("PINCH") -> Triple("#E6002333", "#00E5FF", "#00E5FF")
                    message.contains("NO HAND") -> Triple("#E6330008", "#FF2255", "#FF2255")
                    isAction -> Triple("#E6002612", "#00FF88", "#00FF88")
                    else -> Triple("#E60A0E1A", "#00E5FF", "#00E5FF")
                }

                isActionShowing = isAction
                actionExpiryTimeMs = System.currentTimeMillis() + (if (isAction) 1100L else 1800L)

                renderOverlay(message, strokeColor, bgFill, isPulsing = isAction)

                hideRunnable?.let { mainHandler.removeCallbacks(it) }
                hideRunnable = Runnable {
                    try {
                        isActionShowing = false
                        renderOverlay(
                            currentAlignmentText,
                            currentAlignmentStroke,
                            currentAlignmentFill,
                            isPulsing = false
                        )
                    } catch (_: Throwable) {}
                }
                mainHandler.postDelayed(hideRunnable!!, if (isAction) 1100L else 1800L)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to update feedback overlay", e)
            }
        }
    }

    private fun renderOverlay(
        text: String,
        strokeColorHex: String,
        bgFillHex: String,
        isPulsing: Boolean
    ) {
        if (overlayView == null) {
            initOverlay()
        }
        overlayView?.let { tv ->
            tv.text = text
            val strokeColor = Color.parseColor(strokeColorHex)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor(bgFillHex))
                cornerRadius = 28f
                setStroke(if (isPulsing) 4 else 3, strokeColor)
            }
            tv.background = bg
            tv.setTextColor(strokeColor)
            tv.visibility = View.VISIBLE
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg.contains("bussimulator") || pkg.contains("drdriving") ||
                pkg.contains("hillclimb") || pkg.contains("vectorunit") ||
                pkg.contains("subwaysurf") || pkg.contains("fruitninja")) {
                Log.i(TAG, "Supported game opened: $pkg")
                try {
                    com.zorbi.core.ZorbiGestureConfig.adaptForGame(pkg)
                } catch (_: Throwable) {}

                // Automatically start vision tracking engine for this game!
                autoStartEngine(pkg)

                // Prompt user to align hands if tracking has not initialized yet
                if (currentAlignmentText == "🟢 ZORBI • MOTION READY") {
                    updateAlignmentStatus(
                        "🔴 NO HANDS DETECTED • SHOW HANDS TO FRONT CAMERA",
                        "#FF2255",
                        "#E6330008"
                    )
                }
            }
        }
    }

    private fun autoStartEngine(pkg: String) {
        try {
            val clazz = Class.forName("com.zorbi.app.ZorbiApp")
            val companionField = clazz.getDeclaredField("Companion")
            val companion = companionField.get(null)
            val getInstanceMethod = companion.javaClass.getMethod("getInstance")
            val app = getInstanceMethod.invoke(companion)
            if (app != null) {
                val getAppGraphMethod = app.javaClass.getMethod("getAppGraph")
                val appGraph = getAppGraphMethod.invoke(app) as? com.zorbi.core.AppGraph
                val engine = appGraph?.engine
                if (engine != null && engine.state.value != com.zorbi.core.EngineState.RUNNING) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                        try {
                            engine.start(pkg, com.zorbi.core.PerfMode.BALANCED)
                            Log.i(TAG, "Zorbi engine auto-started successfully for: $pkg")
                        } catch (e: Throwable) {
                            Log.e(TAG, "Error auto-starting engine for $pkg", e)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed resolving ZorbiApp for auto-start", e)
        }
    }

    override fun onInterrupt() {
        // Interrupt placeholder
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.post {
            try {
                overlayView?.let { windowManager?.removeView(it) }
                overlayView = null
            } catch (_: Throwable) {}
        }
        instance = null
        _isConnected.value = false
    }

    companion object {
        private const val TAG = "ZorbiAccessibilityService"
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected

        var instance: ZorbiAccessibilityService? = null
            private set
    }
}
