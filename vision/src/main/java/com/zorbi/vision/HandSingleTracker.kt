package com.zorbi.vision

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
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
 * Production implementation of [Tracker] for [TrackerId.HAND_SINGLE] (Person 1 / P1-1).
 *
 * Responsibilities:
 * - Acquires front camera frames non-blockingly via CameraX ImageAnalysis (KEEP_ONLY_LATEST).
 * - Runs MediaPipe Hand Landmarker in LIVE_STREAM mode configured for maximum 1 hand.
 * - Converts 21 landmarks into core [Pt] format with upright rotation and horizontal front-camera mirroring.
 * - Emits successful detections as [HandsFrame] over [SharedFlow].
 * - Exposes real-time FPS, latency (P50/P95), and dropped frame metrics over [StateFlow].
 * - Manages explicit CameraX lifecycle binding and clean teardown.
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
    private val isInferring = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var isRunning = false
    @Volatile private var currentPerfMode: PerfMode? = null
    private val startStopMutex = Mutex()

    val activePerfMode: PerfMode?
        get() = currentPerfMode

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null
    private var handLandmarker: HandLandmarker? = null
    private var internalLifecycleOwner: TrackerLifecycleOwner? = null

    @Volatile private var nextAllowedArrivalMs: Long = 0L
    @Volatile private var lastSubmittedTimestampMs: Long = 0L
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
        pendingSubmissions.values.forEach { it.bitmap?.recycle() }
        pendingSubmissions.clear()
        lastSubmittedTimestampMs = 0L
        nextAllowedArrivalMs = 0L

        // 1. Initialize MediaPipe HandLandmarker for LIVE_STREAM with strictly 1 hand (GPU delegate with CPU fallback)
        var landmarkerInstance: HandLandmarker? = null
        try {
            val gpuBaseOptions = BaseOptions.builder()
                .setModelAssetPath(modelAssetPath)
                .setDelegate(Delegate.GPU)
                .build()

            val gpuOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(gpuBaseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    handleResult(result, sessionId)
                }
                .setErrorListener { error ->
                    Log.e(TAG, "MediaPipe LIVE_STREAM error in session $sessionId: ${error.message}", error)
                    isInferring.set(false)
                }
                .build()

            landmarkerInstance = HandLandmarker.createFromOptions(context, gpuOptions)
            Log.i(TAG, "Initialized MediaPipe HandLandmarker with Delegate.GPU")
        } catch (e: Throwable) {
            Log.w(TAG, "GPU delegate initialization failed or unavailable; falling back to Delegate.CPU: ${e.message}")
            val cpuBaseOptions = BaseOptions.builder()
                .setModelAssetPath(modelAssetPath)
                .setDelegate(Delegate.CPU)
                .build()

            val cpuOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(cpuBaseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    handleResult(result, sessionId)
                }
                .setErrorListener { error ->
                    Log.e(TAG, "MediaPipe LIVE_STREAM error in session $sessionId: ${error.message}", error)
                    isInferring.set(false)
                }
                .build()

            landmarkerInstance = HandLandmarker.createFromOptions(context, cpuOptions)
            Log.i(TAG, "Initialized MediaPipe HandLandmarker with Delegate.CPU")
        }
        handLandmarker = landmarkerInstance

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

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(targetSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

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
        isInferring.set(false)
        currentPerfMode = null
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

            if (frameIntervalMs > 0L) {
                if (arrivalMs < nextAllowedArrivalMs) {
                    statsCollector.recordDropped()
                    return
                }
                nextAllowedArrivalMs = arrivalMs + frameIntervalMs
            }

            val landmarker = handLandmarker ?: return

            if (!isInferring.compareAndSet(false, true)) {
                statsCollector.recordDropped()
                return
            }

            val rawBitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Upright bitmap rotated before feeding to MediaPipe to avoid internal calculator skew
            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = android.graphics.Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                rawBitmap.recycle()
                rotated
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

            pendingSubmissions[timestampMs] = FrameSubmission(arrivalNanos, sessionId, rotatedBitmap)
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during frame analysis in session $sessionId", e)
            isInferring.set(false)
        } finally {
            // Buffer must ALWAYS be closed to prevent CameraX buffer starvation
            imageProxy.close()
        }
    }

    private fun handleResult(result: HandLandmarkerResult, sessionId: Long) {
        val timestampMs = result.timestampMs()
        val submission = pendingSubmissions.remove(timestampMs)
        submission?.bitmap?.recycle()
        isInferring.set(false)
        if (submission == null) return
        submission.bitmap?.recycle()
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

    private data class FrameSubmission(
        val arrivalNanos: Long,
        val sessionId: Long,
        val bitmap: android.graphics.Bitmap? = null
    )

    /**
     * Dedicated [LifecycleOwner] managing CameraX binding when no external [LifecycleOwner]
     * (e.g. from an Android Service or Activity) is provided.
     *
     * Lifecycle contract:
     * - Owned exclusively by this [HandSingleTracker] instance.
     * - Created and transitioned to [Lifecycle.State.RESUMED] during [start].
     * - Explicitly transitioned to [Lifecycle.State.DESTROYED] during [stop] after unbinding use cases.
     */
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
