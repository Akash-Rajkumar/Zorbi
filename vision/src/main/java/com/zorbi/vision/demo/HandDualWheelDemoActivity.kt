package com.zorbi.vision.demo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.zorbi.core.HandsFrame
import com.zorbi.core.PerfMode
import com.zorbi.core.Side
import com.zorbi.vision.HandDualWheelTracker
import com.zorbi.vision.perf.PerfController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.atan2

/**
 * Launchable Android Activity providing a live physical-device demo and performance
 * measurement runner for the Person 1 HAND_DUAL_WHEEL tracker pipeline (P1-5 / Gate T-P1-5).
 *
 * Capabilities:
 * - Requests CAMERA permission at runtime.
 * - Streams live front-camera video to a [SurfaceView].
 * - Runs [HandDualWheelTracker] configured for 2 simultaneous hands.
 * - Renders real-time 21-landmark skeletons for both hands on [LandmarkOverlayView].
 * - Displays a live HUD with mode selector, dual-hand status, left/right hand confidence,
 *   steering angle, FPS, P50, P95, and dropped frames.
 * - Supports dynamic mode selection (QUALITY, BALANCED, PERFORMANCE) via on-screen buttons
 *   or ADB launch extras (--es perf_mode <MODE>).
 * - Integrates [PerfController] to demonstrate automatic overload degradation when P95 > 80ms.
 * - Safely stops tracker on destroy.
 */
class HandDualWheelDemoActivity : Activity(), LifecycleOwner {

    companion object {
        private const val TAG = "DualWheelDemo"
        private const val PERMISSION_REQUEST_CAMERA = 1002
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var surfaceView: SurfaceView
    private lateinit var overlayView: LandmarkOverlayView
    private lateinit var modeTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var handsTextView: TextView
    private lateinit var steerTextView: TextView
    private lateinit var statsTextView: TextView

    private lateinit var btnQuality: Button
    private lateinit var btnBalanced: Button
    private lateinit var btnPerformance: Button
    private lateinit var btnAuto: Button

    private var tracker: HandDualWheelTracker? = null
    private var perfController: PerfController? = null
    @Volatile private var isTrackerStarted = false
    @Volatile private var currentMode: PerfMode = PerfMode.BALANCED
    @Volatile private var isAutoMode: Boolean = false
    @Volatile private var lastHandCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        currentMode = parseInitialMode(intent)
        isAutoMode = parseInitialAuto(intent)

        buildLayout()
        updateModeHud()
        updateButtonsUI()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupTracker()
        } else {
            statusTextView.text = "Camera: Requesting permission..."
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CAMERA
            )
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val requestedMode = parseInitialMode(intent)
        val requestedAuto = parseInitialAuto(intent)
        switchMode(requestedMode, requestedAuto)
    }

    private fun parseInitialMode(srcIntent: Intent?): PerfMode {
        val modeStr = srcIntent?.getStringExtra("perf_mode")
            ?: srcIntent?.getStringExtra("mode")
            ?: srcIntent?.getStringExtra("PERF_MODE")
        return when (modeStr?.uppercase()) {
            "QUALITY" -> PerfMode.QUALITY
            "PERFORMANCE" -> PerfMode.PERFORMANCE
            "BALANCED" -> PerfMode.BALANCED
            else -> PerfMode.BALANCED
        }
    }

    private fun parseInitialAuto(srcIntent: Intent?): Boolean {
        return srcIntent?.getBooleanExtra("auto_perf", false) ?: false
    }

    private fun buildLayout() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // 1. SurfaceView for live front camera feed
        surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(surfaceView)

        // 2. Custom OverlayView for drawing hand skeletons (handles multiple hands)
        overlayView = LandmarkOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(overlayView)

        // 3. Top HUD Card
        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#D90F172A")) // Translucent slate
            setPadding(36, 48, 36, 36)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
            }
        }

        val title = TextView(this).apply {
            text = "ZORBI Dual Hand Demo (P1-5)"
            setTextColor(Color.WHITE)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 10)
        }
        hud.addView(title)

        // Mode control button bar
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }

        btnQuality = createModeButton("QUALITY").apply {
            setOnClickListener { switchMode(PerfMode.QUALITY, enableAuto = false) }
        }
        buttonsRow.addView(btnQuality)

        btnBalanced = createModeButton("BALANCED").apply {
            setOnClickListener { switchMode(PerfMode.BALANCED, enableAuto = false) }
        }
        buttonsRow.addView(btnBalanced)

        btnPerformance = createModeButton("PERF").apply {
            setOnClickListener { switchMode(PerfMode.PERFORMANCE, enableAuto = false) }
        }
        buttonsRow.addView(btnPerformance)

        btnAuto = createModeButton("AUTO").apply {
            setOnClickListener { switchMode(PerfMode.QUALITY, enableAuto = !isAutoMode) }
        }
        buttonsRow.addView(btnAuto)

        hud.addView(buttonsRow)

        modeTextView = TextView(this).apply {
            setTextColor(Color.parseColor("#38BDF8")) // Cyan
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 6)
        }
        hud.addView(modeTextView)

        statusTextView = TextView(this).apply {
            text = "Status: Initializing..."
            setTextColor(Color.LTGRAY)
            textSize = 14f
            setPadding(0, 0, 0, 6)
        }
        hud.addView(statusTextView)

        handsTextView = TextView(this).apply {
            text = "Hands: Waiting for camera..."
            setTextColor(Color.parseColor("#FACC15")) // Yellow
            textSize = 14f
            setPadding(0, 0, 0, 6)
        }
        hud.addView(handsTextView)

        steerTextView = TextView(this).apply {
            text = "Steering: Neutral (0.0°)"
            setTextColor(Color.parseColor("#A78BFA")) // Light Purple
            textSize = 14f
            setPadding(0, 0, 0, 6)
        }
        hud.addView(steerTextView)

        statsTextView = TextView(this).apply {
            text = "FPS: -- | P50: -- | P95: -- | Dropped: 0"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.MONOSPACE
        }
        hud.addView(statsTextView)

        root.addView(hud)
        setContentView(root)
    }

    private fun createModeButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#334155")) // Slate 700
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            ).apply {
                setMargins(4, 0, 4, 0)
            }
        }
    }

    private fun updateButtonsUI() {
        runOnUiThread {
            btnQuality.setBackgroundColor(
                if (!isAutoMode && currentMode == PerfMode.QUALITY) Color.parseColor("#0284C7") else Color.parseColor("#334155")
            )
            btnBalanced.setBackgroundColor(
                if (!isAutoMode && currentMode == PerfMode.BALANCED) Color.parseColor("#0284C7") else Color.parseColor("#334155")
            )
            btnPerformance.setBackgroundColor(
                if (!isAutoMode && currentMode == PerfMode.PERFORMANCE) Color.parseColor("#0284C7") else Color.parseColor("#334155")
            )
            btnAuto.setBackgroundColor(
                if (isAutoMode) Color.parseColor("#16A34A") else Color.parseColor("#334155")
            )
        }
    }

    private fun updateModeHud() {
        runOnUiThread {
            val autoTag = if (isAutoMode) {
                if (perfController?.isOverloaded == true) " [AUTO: Overloaded -> Dropped to PERF]"
                else " [AUTO: Monitoring (Budget: 80ms)]"
            } else {
                " [MANUAL]"
            }
            modeTextView.text = "Mode: $currentMode$autoTag"
        }
    }

    private fun switchMode(newMode: PerfMode, enableAuto: Boolean) {
        val targetTracker = tracker ?: return
        activityScope.launch {
            try {
                currentMode = newMode
                isAutoMode = enableAuto
                updateModeHud()
                updateButtonsUI()
                statusTextView.text = "Status: Switching to $newMode (Auto=$isAutoMode)..."

                if (enableAuto) {
                    val controller = PerfController(
                        initialMode = newMode,
                        p95BudgetMs = PerfController.DEFAULT_P95_BUDGET_MS,
                        onModeChanged = { next, prev ->
                            Log.i(TAG, "PerfController auto-transitioned: $prev -> $next")
                            runOnUiThread {
                                currentMode = next
                                updateModeHud()
                                updateButtonsUI()
                            }
                        }
                    )
                    perfController?.detach()
                    perfController = controller
                    controller.attachToTracker(targetTracker, activityScope)
                    // Start in target mode
                    targetTracker.start(newMode)
                } else {
                    perfController?.detach()
                    perfController = null
                    targetTracker.start(newMode)
                }
                statusTextView.text = "Status: RUNNING ($currentMode)"
                Log.i(TAG, "Switched successfully to $currentMode (Auto=$isAutoMode)")
            } catch (e: Exception) {
                Log.e(TAG, "Error switching mode to $newMode", e)
                statusTextView.text = "Status: ERROR (${e.message})"
                statusTextView.setTextColor(Color.RED)
            }
        }
    }

    private fun setupTracker() {
        val surfaceProvider = Preview.SurfaceProvider { request ->
            val surface = surfaceView.holder.surface
            if (surface != null && surface.isValid) {
                request.provideSurface(surface, ContextCompat.getMainExecutor(this)) {
                    Log.d(TAG, "Camera surface provided")
                }
            } else {
                surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        request.provideSurface(holder.surface, ContextCompat.getMainExecutor(this@HandDualWheelDemoActivity)) {
                            Log.d(TAG, "Camera surface created and provided")
                        }
                    }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) {}
                })
            }
        }

        val createdTracker = HandDualWheelTracker(
            context = applicationContext,
            externalLifecycleOwner = this,
            previewSurfaceProvider = surfaceProvider
        )
        tracker = createdTracker

        // Observe frame output
        activityScope.launch {
            createdTracker.frames.collect { frame ->
                if (frame is HandsFrame) {
                    lastHandCount = frame.hands.size
                    when (frame.hands.size) {
                        2 -> {
                            val handA = frame.hands[0]
                            val handB = frame.hands[1]
                            val left = if (handA.side == Side.LEFT) handA else handB
                            val right = if (handA.side == Side.RIGHT) handA else handB

                            val scoreL = (left.score * 100).toInt()
                            val scoreR = (right.score * 100).toInt()
                            handsTextView.text = "Hands: 2 DETECTED | Left: $scoreL% | Right: $scoreR%"
                            handsTextView.setTextColor(Color.parseColor("#4ADE80")) // Green

                            // Compute dual-hand steering angle from wrists (index 0)
                            if (left.landmarks.isNotEmpty() && right.landmarks.isNotEmpty()) {
                                val lw = left.landmarks[0]
                                val rw = right.landmarks[0]
                                val angleRad = atan2((rw.y - lw.y).toDouble(), (rw.x - lw.x).toDouble())
                                val angleDeg = Math.toDegrees(angleRad)
                                val angleStr = String.format("%.1f", angleDeg)
                                val direction = if (angleDeg > 5.0) "RIGHT" else if (angleDeg < -5.0) "LEFT" else "CENTER"
                                steerTextView.text = "Steering: $direction ($angleStr°)"
                            }

                            overlayView.updateHands(frame.hands)
                        }
                        1 -> {
                            val hand = frame.hands[0]
                            val scorePct = (hand.score * 100).toInt()
                            handsTextView.text = "Hands: 1 DETECTED (${hand.side}) | Conf: $scorePct% (Hold 2nd hand)"
                            handsTextView.setTextColor(Color.parseColor("#F59E0B")) // Orange
                            steerTextView.text = "Steering: Waiting for 2nd hand"
                            overlayView.updateHands(frame.hands)
                        }
                        else -> {
                            handsTextView.text = "Hands: None (Hold both hands like a steering wheel)"
                            handsTextView.setTextColor(Color.parseColor("#FACC15")) // Yellow
                            steerTextView.text = "Steering: Inactive"
                            overlayView.clear()
                        }
                    }
                }
            }
        }

        // Observe live performance statistics
        activityScope.launch {
            createdTracker.stats.collect { stats ->
                val fpsStr = String.format("%.1f", stats.fps)
                val p50Str = String.format("%.1f", stats.inferenceMsP50)
                val p95Str = String.format("%.1f", stats.inferenceMsP95)
                statsTextView.text = "FPS: $fpsStr | P50: ${p50Str}ms | P95: ${p95Str}ms | Dropped: ${stats.dropped}"

                val autoTag = if (isAutoMode) "[AUTO]" else "[MANUAL]"
                Log.i(TAG, "PERF_METRICS: mode=$currentMode $autoTag | fps=$fpsStr | p50=${p50Str}ms | p95=${p95Str}ms | dropped=${stats.dropped} | hands=$lastHandCount")
            }
        }

        // Start tracker in selected initial mode
        switchMode(currentMode, isAutoMode)
        isTrackerStarted = true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupTracker()
            } else {
                statusTextView.text = "Status: CAMERA PERMISSION DENIED"
                statusTextView.setTextColor(Color.RED)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
        activityScope.cancel()

        perfController?.detach()
        perfController = null

        if (isTrackerStarted) {
            isTrackerStarted = false
            runBlocking {
                try {
                    tracker?.stop()
                    Log.i(TAG, "HandDualWheelTracker stopped cleanly on activity destroy")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping HandDualWheelTracker on destroy", e)
                }
            }
        }
    }
}
