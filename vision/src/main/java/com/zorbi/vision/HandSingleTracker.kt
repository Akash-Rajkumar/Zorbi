package com.zorbi.vision

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.zorbi.core.*
import com.zorbi.vision.internal.CoordinateTransforms
import com.zorbi.vision.internal.TrackerStatsCollector
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production implementation of [Tracker] for [TrackerId.HAND_SINGLE] (Person 1 / P1-4).
 *
 * Responsibilities:
 * - Operates the CameraX front camera feed with zero buffer starvation.
 * - Runs MediaPipe [HandLandmarker] in [RunningMode.LIVE_STREAM] configured for single hand ([setNumHands(1)]).
 * - Converts 21 landmarks into core [Pt] format with upright rotation and horizontal front-camera mirroring.
 * - Dynamically adapts camera targetRotation using [OrientationEventListener] for seamless landscape driving.
 * - Exposes real-time FPS, latency (P50/P95), and dropped frame metrics over [StateFlow].
 */
class HandSingleTracker(
    private val context: Context,
    private val externalLifecycleOwner: LifecycleOwner? = null,
    private val modelAssetPath: String = DEFAULT_MODEL_PATH,
    private val previewSurfaceProvider: Preview.SurfaceProvider? = null
) : Tracker {

    companion object {
        private const val TAG = "HandSingleTracker"
        const val DEFAULT_MODEL_PATH = "models/hand_landmarker.task"
    }

    override val id: TrackerId = TrackerId.HAND_SINGLE

    private val _frames = MutableSharedFlow<TrackerFrame>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val frames: SharedFlow<TrackerFrame> = _frames.asSharedFlow()

    private val statsCollector = TrackerStatsCollector()
    override val stats: StateFlow<TrackerStats> = statsCollector.stats

    private val sessionCounter = AtomicLong(0L)
    @Volatile private var isRunning = false
    @Volatile private var currentPerfMode: PerfMode? = null
    private val startStopMutex = Mutex()

    val activePerfMode: PerfMode?
        get() = currentPerfMode

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var handLandmarker: HandLandmarker? = null
    private var internalLifecycleOwner: TrackerLifecycleOwner? = null
    private var orientationListener: OrientationEventListener? = null
    @Volatile private var currentAnalysis: ImageAnalysis? = null

    @Volatile private var nextAllowedArrivalMs: Long = 0L
    @Volatile private var lastSubmittedTimestampMs: Long = 0L
    @Volatile private var isAnalyzingFrame = false
    private val pendingSubmissions = ConcurrentHashMap<Long, FrameSubmission>()

    override suspend fun start(perf: PerfMode) = startStopMutex.withLock {
        if (isRunning && currentPerfMode == perf) {
            Log.d(TAG, "Already running in mode $perf; skipping redundant start")
            return@withLock
        }
        if (isRunning) {
            stopInternal()
        }
        isRunning = true
        currentPerfMode = perf
        val sessionId = sessionCounter.incrementAndGet()
        statsCollector.reset()
        pendingSubmissions.clear()
        lastSubmittedTimestampMs = 0L
        nextAllowedArrivalMs = 0L

        // 1. Initialize MediaPipe HandLandmarker for LIVE_STREAM with strictly 1 hand
        // Attempts GPU hardware acceleration for ultra-low latency; gracefully falls back to CPU
        handLandmarker = try {
            val gpuBase = BaseOptions.builder()
                .setModelAssetPath(modelAssetPath)
                .setDelegate(Delegate.GPU)
                .build()
            val gpuOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(gpuBase)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    handleResult(result, sessionId)
                }
                .setErrorListener { error ->
                    isAnalyzingFrame = false
                    Log.e(TAG, "MediaPipe LIVE_STREAM error (GPU) in session $sessionId: ${error.message}", error)
                }
                .build()
            HandLandmarker.createFromOptions(context, gpuOptions)
        } catch (e: Throwable) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU for single hand: ${e.message}")
            val cpuBase = BaseOptions.builder()
                .setModelAssetPath(modelAssetPath)
                .setDelegate(Delegate.CPU)
                .build()
            val cpuOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(cpuBase)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    handleResult(result, sessionId)
                }
                .setErrorListener { error ->
                    isAnalyzingFrame = false
                    Log.e(TAG, "MediaPipe LIVE_STREAM error (CPU) in session $sessionId: ${error.message}", error)
                }
                .build()
            HandLandmarker.createFromOptions(context, cpuOptions)
        }

        // 2. Configure CameraX resolution & throttling per PerfMode
        val executor = Executors.newSingleThreadExecutor()
        cameraExecutor = executor

        val targetSize = when (perf) {
            PerfMode.QUALITY -> Size(1280, 720)
            PerfMode.BALANCED, PerfMode.PERFORMANCE -> Size(640, 480)
        }

        val frameIntervalMs = when (perf) {
            PerfMode.PERFORMANCE -> 66L // ~15 FPS cap window
            PerfMode.QUALITY, PerfMode.BALANCED -> 0L
        }

        val initialRotation = try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        } catch (_: Throwable) {
            Surface.ROTATION_0
        }

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(targetSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(initialRotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        this.currentAnalysis = imageAnalysis

        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val newRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                currentAnalysis?.targetRotation = newRotation
            }
        }
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
        this.orientationListener = orientationEventListener

        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            analyzeFrame(imageProxy, sessionId, frameIntervalMs)
        }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        // 3. Bind CameraX use cases on the Android Main thread
        runOnMainThread(context) {
            val provider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider = provider
            provider.unbindAll()

            val lifecycleToBind: LifecycleOwner = externalLifecycleOwner ?: run {
                val internalOwner = TrackerLifecycleOwner()
                internalLifecycleOwner = internalOwner
                internalOwner.markResumed()
                internalOwner
            }

            val preview = previewSurfaceProvider?.let { providerCallback ->
                Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build().also {
                        it.setSurfaceProvider(providerCallback)
                    }
            }

            if (preview != null) {
                provider.bindToLifecycle(lifecycleToBind, cameraSelector, preview, imageAnalysis)
            } else {
                provider.bindToLifecycle(lifecycleToBind, cameraSelector, imageAnalysis)
            }
            Log.i(TAG, "CameraX front camera bound for HAND_SINGLE in mode $perf (session $sessionId)")
        }
    }

    override suspend fun stop() = startStopMutex.withLock {
        stopInternal()
    }

    private suspend fun stopInternal() {
        isRunning = false
        isAnalyzingFrame = false
        currentPerfMode = null
        orientationListener?.disable()
        orientationListener = null
        currentAnalysis = null
        sessionCounter.incrementAndGet() // Invalidate pending and in-flight callbacks
        pendingSubmissions.clear()

        // Unbind CameraX on main thread
        runOnMainThread(context) {
            try {
                cameraProvider?.unbindAll()
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding CameraX", e)
            }
            cameraProvider = null

            internalLifecycleOwner?.markDestroyed()
            internalLifecycleOwner = null
        }

        try {
            cameraExecutor?.shutdown()
            cameraExecutor?.awaitTermination(300, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down camera executor", e)
        }
        cameraExecutor = null

        try {
            handLandmarker?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing HandLandmarker", e)
        }
        handLandmarker = null

        statsCollector.reset()
        Log.i(TAG, "HandSingleTracker stopped and resources released")
    }

    private fun analyzeFrame(imageProxy: ImageProxy, sessionId: Long, frameIntervalMs: Long) {
        val arrivalNanos = SystemClock.elapsedRealtimeNanos()
        val arrivalMs = arrivalNanos / 1_000_000L

        try {
            if (!isRunning || sessionCounter.get() != sessionId) {
                return
            }

            // Drop frame if previous inference is still in-flight to prevent MediaPipe queue backlog
            if (isAnalyzingFrame) {
                statsCollector.recordDropped()
                return
            }

            if (frameIntervalMs > 0L) {
                if (arrivalMs < nextAllowedArrivalMs) {
                    statsCollector.recordDropped()
                    return
                }
                nextAllowedArrivalMs = arrivalMs + frameIntervalMs
            }

            val landmarker = handLandmarker ?: return

            val rawBitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Upright bitmap rotated before feeding to MediaPipe to avoid internal calculator skew
            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rot = android.graphics.Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                rawBitmap.recycle()
                rot
            } else {
                rawBitmap
            }

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()

            // Ensure strictly monotonic timestamp for MediaPipe LIVE_STREAM
            val timestampMs = synchronized(this) {
                val ts = if (arrivalMs <= lastSubmittedTimestampMs) {
                    lastSubmittedTimestampMs + 1L
                } else {
                    arrivalMs
                }
                lastSubmittedTimestampMs = ts
                ts
            }

            isAnalyzingFrame = true
            pendingSubmissions[timestampMs] = FrameSubmission(arrivalNanos, sessionId)
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            isAnalyzingFrame = false
            Log.e(TAG, "Exception during frame analysis in session $sessionId", e)
        } finally {
            // Buffer must ALWAYS be closed to prevent CameraX buffer starvation
            imageProxy.close()
        }
    }

    private fun handleResult(result: HandLandmarkerResult, sessionId: Long) {
        isAnalyzingFrame = false
        val timestampMs = result.timestampMs()
        val submission = pendingSubmissions.remove(timestampMs) ?: return
        val nowNanos = SystemClock.elapsedRealtimeNanos()

        if (!isRunning || sessionCounter.get() != sessionId || submission.sessionId != sessionId) {
            // Ignore stale results from previous sessions or after stop()
            return
        }

        val latencyMs = ((nowNanos - submission.arrivalNanos) / 1_000_000.0).toFloat()
        statsCollector.recordFrame(submission.arrivalNanos, latencyMs)

        val landmarks = result.landmarks()
        val handednesses = result.handedness()

        if (!landmarks.isNullOrEmpty() && landmarks[0].isNotEmpty()) {
            val firstHandLandmarks = landmarks[0]

            val side = if (!handednesses.isNullOrEmpty() && handednesses[0].isNotEmpty()) {
                CoordinateTransforms.toSide(handednesses[0][0].categoryName())
            } else {
                Side.UNKNOWN
            }

            val score = if (!handednesses.isNullOrEmpty() && handednesses[0].isNotEmpty()) {
                handednesses[0][0].score()
            } else {
                1.0f
            }

            // Convert MediaPipe 21 landmarks into core Pt format (upright, normalized, mirrored)
            val pts = firstHandLandmarks.map { lm ->
                CoordinateTransforms.toPt(lm.x(), lm.y(), lm.z(), mirrorHorizontally = true)
            }

            val hand = Hand(side = side, score = score, landmarks = pts)
            val frame = HandsFrame(tsNanos = submission.arrivalNanos, hands = listOf(hand))
            _frames.tryEmit(frame)
        }
    }

    private suspend fun runOnMainThread(context: Context, block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            suspendCancellableCoroutine { cont ->
                ContextCompat.getMainExecutor(context).execute {
                    try {
                        block()
                        cont.resume(Unit)
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    }
                }
            }
        }
    }

    private data class FrameSubmission(val arrivalNanos: Long, val sessionId: Long)

    private class TrackerLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override fun getLifecycle(): Lifecycle = registry

        fun markResumed() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun markDestroyed() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}
