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
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.PerfMode
import com.zorbi.core.Side
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerStats
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
 * Production implementation of [Tracker] for [TrackerId.HAND_DUAL_WHEEL] (Person 1 / P1-5).
 *
 * Responsibilities:
 * - Operates the CameraX front camera feed with zero buffer starvation.
 * - Runs MediaPipe [HandLandmarker] in [RunningMode.LIVE_STREAM] configured for two hands ([setNumHands(2)]).
 * - Delivers normalized, upright, and mirrored 21-landmark coordinate points for both hands in [HandsFrame].
 * - Correctly attributes left and right hands based on MediaPipe handedness analysis.
 * - Supports dynamic [PerfMode] throttling and resolution adaptation.
 * - Exposes real-time FPS, latency (P50/P95), and dropped frame metrics over [StateFlow].
 */
class HandDualWheelTracker(
    private val context: Context,
    private val externalLifecycleOwner: LifecycleOwner? = null,
    private val modelAssetPath: String = DEFAULT_MODEL_PATH,
    private val previewSurfaceProvider: Preview.SurfaceProvider? = null
) : Tracker {

    companion object {
        private const val TAG = "HandDualWheelTracker"
        const val DEFAULT_MODEL_PATH = "models/hand_landmarker.task"
        const val MAX_DETECTED_HANDS = 2
    }

    override val id: TrackerId = TrackerId.HAND_DUAL_WHEEL

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
        pendingSubmissions.clear()
        lastSubmittedTimestampMs = 0L
        nextAllowedArrivalMs = 0L

        // 1. Initialize MediaPipe HandLandmarker for LIVE_STREAM configured for 2 hands (dual wheel)
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .setDelegate(Delegate.CPU)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(MAX_DETECTED_HANDS)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ ->
                handleResult(result, sessionId)
            }
            .setErrorListener { error ->
                Log.e(TAG, "MediaPipe LIVE_STREAM error in dual-wheel session $sessionId: ${error.message}", error)
                isInferring.set(false)
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)

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

        // 3. Bind CameraX use cases on Android Main thread
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
            Log.i(TAG, "CameraX front camera bound for HAND_DUAL_WHEEL in mode $perf (session $sessionId)")
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
        Log.i(TAG, "HandDualWheelTracker stopped and resources released cleanly")
    }

    private fun analyzeFrame(imageProxy: ImageProxy, sessionId: Long, frameIntervalMs: Long) {
        val arrivalNanos = SystemClock.elapsedRealtimeNanos()
        val arrivalMs = arrivalNanos / 1_000_000L

        try {
            if (!isRunning || sessionCounter.get() != sessionId) {
                return
            }

            // Apply frame interval throttling if configured (e.g. PERFORMANCE mode)
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
            Log.e(TAG, "Exception during frame analysis in dual-wheel session $sessionId", e)
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
        val nowNanos = SystemClock.elapsedRealtimeNanos()

        if (!isRunning || sessionCounter.get() != sessionId || submission.sessionId != sessionId) {
            // Ignore stale results from previous sessions or after stop()
            return
        }

        val latencyMs = ((nowNanos - submission.arrivalNanos) / 1_000_000.0).toFloat()
        statsCollector.recordFrame(submission.arrivalNanos, latencyMs)

        val landmarks = result.landmarks()
        val handednesses = result.handedness()

        if (!landmarks.isNullOrEmpty()) {
            val detectedHands = mutableListOf<Hand>()

            for (i in landmarks.indices) {
                val handLandmarks = landmarks[i]
                if (handLandmarks.size < 21) continue

                val side = if (!handednesses.isNullOrEmpty() && i < handednesses.size && handednesses[i].isNotEmpty()) {
                    CoordinateTransforms.toSide(handednesses[i][0].categoryName())
                } else {
                    Side.UNKNOWN
                }

                val score = if (!handednesses.isNullOrEmpty() && i < handednesses.size && handednesses[i].isNotEmpty()) {
                    handednesses[i][0].score()
                } else {
                    1.0f
                }

                // Convert MediaPipe 21 landmarks into core Pt format (upright, normalized, mirrored)
                val pts = handLandmarks.map { lm ->
                    CoordinateTransforms.toPt(lm.x(), lm.y(), lm.z(), mirrorHorizontally = true)
                }

                detectedHands.add(Hand(side = side, score = score, landmarks = pts))
            }

            if (detectedHands.isNotEmpty()) {
                val frame = HandsFrame(tsNanos = submission.arrivalNanos, hands = detectedHands)
                _frames.tryEmit(frame)
            }
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

    private data class FrameSubmission(val arrivalNanos: Long, val sessionId: Long, val bitmap: android.graphics.Bitmap? = null)

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
