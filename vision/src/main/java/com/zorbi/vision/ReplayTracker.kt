package com.zorbi.vision

import com.zorbi.core.PerfMode
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerStats
import com.zorbi.vision.internal.TrackerStatsCollector
import com.zorbi.vision.recorder.LandmarkJsonl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.Reader

/**
 * Replay implementation of [Tracker] (Person 1 / P1-2).
 *
 * Reads landmark records from Section 5.3 JSONL format using [LandmarkJsonl],
 * preserves exact recorded timestamps, and emits [TrackerFrame] instances over [frames].
 *
 * Supports two playback modes:
 * - [ReplayMode.REALTIME]: Paced playback according to recorded inter-frame timestamp deltas.
 * - [ReplayMode.INSTANT]: Immediate playback without delays for fast, deterministic unit testing.
 *
 * Exposes live [TrackerStats] through [stats] with computed FPS and zero dropped frames.
 */
class ReplayTracker(
    private val linesSupplier: () -> Sequence<String>,
    override val id: TrackerId = TrackerId.HAND_SINGLE,
    val replayMode: ReplayMode = ReplayMode.REALTIME,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    val loop: Boolean = false
) : Tracker {

    enum class ReplayMode {
        REALTIME,
        INSTANT
    }

    constructor(
        lines: List<String>,
        id: TrackerId = TrackerId.HAND_SINGLE,
        replayMode: ReplayMode = ReplayMode.REALTIME,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
        loop: Boolean = false
    ) : this(
        linesSupplier = { lines.asSequence() },
        id = id,
        replayMode = replayMode,
        coroutineScope = coroutineScope,
        loop = loop
    )

    constructor(
        jsonContent: String,
        id: TrackerId = TrackerId.HAND_SINGLE,
        replayMode: ReplayMode = ReplayMode.REALTIME,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
        loop: Boolean = false
    ) : this(
        lines = jsonContent.trim().lines().filter { it.isNotBlank() },
        id = id,
        replayMode = replayMode,
        coroutineScope = coroutineScope,
        loop = loop
    )

    private val _frames = MutableSharedFlow<TrackerFrame>(
        extraBufferCapacity = 64
    )
    override val frames: SharedFlow<TrackerFrame> = _frames.asSharedFlow()

    private val statsCollector = TrackerStatsCollector()
    override val stats: StateFlow<TrackerStats> = statsCollector.stats

    private val startStopMutex = Mutex()
    @Volatile private var isRunning = false
    private var playbackJob: Job? = null

    /**
     * Last error encountered during replay execution, if any.
     */
    @Volatile
    var playbackError: Throwable? = null
        private set

    /**
     * Starts replaying frames from the supplied JSONL source.
     * Safe to call repeatedly after [stop] to restart playback.
     */
    override suspend fun start(perf: PerfMode) = startStopMutex.withLock {
        if (isRunning) return@withLock
        isRunning = true
        playbackError = null
        statsCollector.reset()

        playbackJob = coroutineScope.launch {
            try {
                do {
                    playSession()
                } while (isRunning && loop)
            } catch (e: CancellationException) {
                // Clean cancellation on stop()
                throw e
            } catch (e: Throwable) {
                playbackError = e
            } finally {
                isRunning = false
            }
        }
    }

    private suspend fun playSession() {
        var lastTimestampNanos: Long? = null

        for (line in linesSupplier()) {
            if (!isRunning) break

            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val record = LandmarkJsonl.deserializeRecord(trimmed)
            val frame = record.frame

            if (replayMode == ReplayMode.REALTIME && lastTimestampNanos != null) {
                val deltaNanos = frame.tsNanos - lastTimestampNanos
                if (deltaNanos > 0) {
                    val delayMs = deltaNanos / 1_000_000L
                    if (delayMs > 0) {
                        delay(delayMs)
                    }
                }
            }
            lastTimestampNanos = frame.tsNanos

            _frames.emit(frame)
            statsCollector.recordFrame(arrivalNanos = System.nanoTime(), latencyMs = 0f)
        }
    }

    /**
     * Stops replay playback, cancels collection, and cleans up active resources.
     * Safe to call repeatedly.
     */
    override suspend fun stop() = startStopMutex.withLock {
        isRunning = false
        playbackJob?.cancelAndJoin()
        playbackJob = null
    }

    /**
     * Suspends until the active playback session finishes all lines.
     * Re-throws any parsing or replay error that occurred during playback.
     */
    suspend fun awaitCompletion() {
        playbackJob?.join()
        playbackError?.let { throw it }
    }

    companion object {
        /**
         * Creates a [ReplayTracker] from a [File] containing JSONL landmark records.
         */
        fun fromFile(
            file: File,
            id: TrackerId = TrackerId.HAND_SINGLE,
            replayMode: ReplayMode = ReplayMode.REALTIME,
            coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            loop: Boolean = false
        ): ReplayTracker {
            return ReplayTracker(
                linesSupplier = { file.bufferedReader().lineSequence() },
                id = id,
                replayMode = replayMode,
                coroutineScope = coroutineScope,
                loop = loop
            )
        }

        /**
         * Creates a [ReplayTracker] from a [Reader] factory.
         */
        fun fromReader(
            readerProvider: () -> Reader,
            id: TrackerId = TrackerId.HAND_SINGLE,
            replayMode: ReplayMode = ReplayMode.REALTIME,
            coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            loop: Boolean = false
        ): ReplayTracker {
            return ReplayTracker(
                linesSupplier = { readerProvider().buffered().lineSequence() },
                id = id,
                replayMode = replayMode,
                coroutineScope = coroutineScope,
                loop = loop
            )
        }
    }
}

