package com.zorbi.vision.recorder

import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.Writer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe recording engine for ZORBI landmark streams.
 *
 * Collects [TrackerFrame] items asynchronously from a [Tracker] or [Flow],
 * serializes each frame into the Section 5.3 JSONL format using [LandmarkJsonl],
 * and writes line-delimited records to a caller-provided [Writer].
 *
 * Flushes output after each record without closing caller-owned destinations.
 */
class LandmarkRecorder(
    private val defaultTracker: Tracker? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val lock = Any()
    private val _isRecording = AtomicBoolean(false)
    private val _recordedFrameCount = AtomicInteger(0)

    @Volatile
    private var recordingJob: Job? = null

    @Volatile
    private var activeWriter: Writer? = null

    /**
     * Whether a recording session is currently active.
     */
    val isRecording: Boolean
        get() = _isRecording.get()

    /**
     * Total number of frames successfully written to the output in the current or most recent recording session.
     */
    val recordedFrameCount: Int
        get() = _recordedFrameCount.get()

    /**
     * Starts recording frames from [tracker] into [destination].
     *
     * @param destination Target [Writer] where JSONL records will be written. Caller owns and must close.
     * @param tracker Source [Tracker] providing [Tracker.frames]. Defaults to [defaultTracker].
     * @param trackerId Identifier written in the JSONL header. Defaults to [tracker.id].
     * @throws IllegalStateException if recording is already in progress.
     * @throws IllegalArgumentException if no tracker is provided or configured.
     */
    fun startRecording(
        destination: Writer,
        tracker: Tracker? = defaultTracker,
        trackerId: TrackerId? = tracker?.id
    ) {
        val targetTracker = tracker ?: defaultTracker ?: throw IllegalArgumentException(
            "Tracker must be provided either in the LandmarkRecorder constructor or in startRecording()"
        )
        val targetTrackerId = trackerId ?: targetTracker.id
        startRecording(
            frames = targetTracker.frames,
            destination = destination,
            trackerId = targetTrackerId
        )
    }

    /**
     * Starts recording frames from a generic [Flow] of [TrackerFrame] into [destination].
     *
     * @param frames Cold or hot stream of [TrackerFrame] items to record.
     * @param destination Target [Writer] where JSONL records will be written. Caller owns and must close.
     * @param trackerId Identifier written in each JSONL record line.
     * @throws IllegalStateException if recording is already in progress.
     */
    fun startRecording(
        frames: Flow<TrackerFrame>,
        destination: Writer,
        trackerId: TrackerId = TrackerId.HAND_SINGLE
    ) {
        synchronized(lock) {
            check(!_isRecording.get()) {
                "Recording is already in progress. Stop the current recording before starting again."
            }

            _recordedFrameCount.set(0)
            _isRecording.set(true)
            activeWriter = destination

            recordingJob = coroutineScope.launch {
                try {
                    frames.collect { frame ->
                        synchronized(lock) {
                            if (!_isRecording.get()) return@collect
                            val line = LandmarkJsonl.serialize(frame, trackerId)
                            destination.write(line)
                            destination.write("\n")
                            destination.flush()
                            _recordedFrameCount.incrementAndGet()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    synchronized(lock) {
                        _isRecording.set(false)
                    }
                }
            }
        }
    }

    /**
     * Stops the active recording session immediately, cancels collection, flushes pending output,
     * and prevents any further writes.
     *
     * Does NOT close the caller-provided [Writer]. Safe to call repeatedly.
     *
     * @return Total number of frames recorded during the stopped session.
     */
    fun stopRecording(): Int {
        val jobToCancel: Job?
        val writerToFlush: Writer?

        synchronized(lock) {
            if (!_isRecording.getAndSet(false)) {
                return _recordedFrameCount.get()
            }
            jobToCancel = recordingJob
            recordingJob = null
            writerToFlush = activeWriter
            activeWriter = null
        }

        jobToCancel?.cancel()
        try {
            writerToFlush?.flush()
        } catch (_: Exception) {
            // Destination writer may already be completed; ignore flush errors on shutdown.
        }

        return _recordedFrameCount.get()
    }

    /**
     * Suspending variant of [stopRecording] that also joins the cancelled collection job,
     * ensuring complete coroutine termination before returning.
     *
     * @return Total number of frames recorded during the stopped session.
     */
    suspend fun stopRecordingAndJoin(): Int {
        val jobToCancel: Job?
        val writerToFlush: Writer?

        synchronized(lock) {
            if (!_isRecording.getAndSet(false)) {
                return _recordedFrameCount.get()
            }
            jobToCancel = recordingJob
            recordingJob = null
            writerToFlush = activeWriter
            activeWriter = null
        }

        jobToCancel?.cancelAndJoin()
        try {
            writerToFlush?.flush()
        } catch (_: Exception) {
            // Ignore flush errors on shutdown.
        }

        return _recordedFrameCount.get()
    }
}

