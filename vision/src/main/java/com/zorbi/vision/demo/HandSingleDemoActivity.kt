package com.zorbi.vision.demo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import com.zorbi.vision.HandSingleTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Launchable Android Activity providing a live, physical-device demo for the Person 1
 * HAND_SINGLE pipeline.
 *
 * Capabilities:
 * - Requests CAMERA permission at runtime.
 * - Streams live front-camera video to a [SurfaceView].
 * - Runs [HandSingleTracker] and renders real-time 21-landmark skeleton on [LandmarkOverlayView].
 * - Displays a live HUD with tracker status, hand detection, side (LEFT/RIGHT), score, FPS, and latency.
 * - Safely stops tracker on destroy.
 */
class HandSingleDemoActivity : Activity(), LifecycleOwner {

    companion object {
        private const val TAG = "HandSingleDemo"
        private const val PERMISSION_REQUEST_CAMERA = 1001
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var surfaceView: SurfaceView
    private lateinit var overlayView: LandmarkOverlayView
    private lateinit var statusTextView: TextView
    private lateinit var handTextView: TextView
    private lateinit var statsTextView: TextView

    private var tracker: HandSingleTracker? = null
    @Volatile private var isTrackerStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        buildLayout()

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

        // 2. Custom OverlayView for drawing hand skeleton
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
            setBackgroundColor(Color.parseColor("#CC111827")) // Translucent dark gray
            setPadding(40, 60, 40, 40)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
            }
        }

        val title = TextView(this).apply {
            text = "ZORBI HAND_SINGLE Demo"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        hud.addView(title)

        statusTextView = TextView(this).apply {
            text = "Status: Initializing..."
            setTextColor(Color.parseColor("#4ADE80")) // Green
            textSize = 15f
            setPadding(0, 0, 0, 8)
        }
        hud.addView(statusTextView)

        handTextView = TextView(this).apply {
            text = "Hand: Waiting for camera..."
            setTextColor(Color.parseColor("#FACC15")) // Yellow
            textSize = 15f
            setPadding(0, 0, 0, 8)
        }
        hud.addView(handTextView)

        statsTextView = TextView(this).apply {
            text = "FPS: -- | P50: -- | Dropped: 0"
            setTextColor(Color.LTGRAY)
            textSize = 14f
        }
        hud.addView(statsTextView)

        root.addView(hud)
        setContentView(root)
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
                        request.provideSurface(holder.surface, ContextCompat.getMainExecutor(this@HandSingleDemoActivity)) {
                            Log.d(TAG, "Camera surface created and provided")
                        }
                    }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) {}
                })
            }
        }

        val createdTracker = HandSingleTracker(
            context = applicationContext,
            externalLifecycleOwner = this,
            previewSurfaceProvider = surfaceProvider
        )
        tracker = createdTracker

        // Observe frame output
        activityScope.launch {
            createdTracker.frames.collect { frame ->
                if (frame is HandsFrame) {
                    if (frame.hands.isNotEmpty()) {
                        val hand = frame.hands[0]
                        val scorePct = (hand.score * 100).toInt()
                        handTextView.text = "Hand: DETECTED (${hand.side}) | Conf: $scorePct% | LMs: ${hand.landmarks.size}"
                        handTextView.setTextColor(Color.parseColor("#4ADE80")) // Green
                        overlayView.updateHands(frame.hands)
                    } else {
                        handTextView.text = "Hand: None detected (looking for hand...)"
                        handTextView.setTextColor(Color.parseColor("#FACC15")) // Yellow
                        overlayView.clear()
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
            }
        }

        // Start tracker
        activityScope.launch {
            try {
                statusTextView.text = "Status: Starting CameraX & MediaPipe..."
                createdTracker.start(PerfMode.QUALITY)
                isTrackerStarted = true
                statusTextView.text = "Status: RUNNING (Front Camera Active)"
                Log.i(TAG, "HandSingleTracker started successfully in demo activity")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting HandSingleTracker", e)
                statusTextView.text = "Status: ERROR (${e.message})"
                statusTextView.setTextColor(Color.RED)
            }
        }
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

        if (isTrackerStarted) {
            isTrackerStarted = false
            runBlocking {
                try {
                    tracker?.stop()
                    Log.i(TAG, "HandSingleTracker stopped cleanly on activity destroy")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping HandSingleTracker on destroy", e)
                }
            }
        }
    }
}

