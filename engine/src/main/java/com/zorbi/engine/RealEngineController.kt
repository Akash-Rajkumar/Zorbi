package com.zorbi.engine

import com.zorbi.calib.fist.FistDetector
import com.zorbi.core.Calibration
import com.zorbi.core.CalibrationRepository
import com.zorbi.core.Dir
import com.zorbi.core.EngineController
import com.zorbi.core.EngineState
import com.zorbi.core.GameProfile
import com.zorbi.core.GestureEvent
import com.zorbi.core.GestureInterpreter
import com.zorbi.core.HandsFrame
import com.zorbi.core.HudFrame
import com.zorbi.core.InputInjector
import com.zorbi.core.MappingType
import com.zorbi.core.PauseToggle
import com.zorbi.core.PerfMode
import com.zorbi.core.PoseFrame
import com.zorbi.core.ProfileRepository
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.Slash
import com.zorbi.core.Steer
import com.zorbi.core.Swipe
import com.zorbi.core.TelemetrySink
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerRegistry
import com.zorbi.gesture.LaneSwipeMapper
import com.zorbi.gesture.LeanGestureInterpreter
import com.zorbi.gesture.ResettableInterpreter
import com.zorbi.gesture.SlashGestureInterpreter
import com.zorbi.gesture.SwipeGestureInterpreter
import com.zorbi.gesture.WheelGestureInterpreter
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Functional abstraction for frame smoothing/filtering (e.g., One Euro Filter).
 * Non-intrusive and injectable to keep Person 1 and Person 2 modules cleanly decoupled.
 */
fun interface FrameFilter {
    fun filter(frame: TrackerFrame): TrackerFrame
    fun configure(profile: GameProfile) {}
    fun reset() {}
}

object PassthroughFilter : FrameFilter {
    override fun filter(frame: TrackerFrame): TrackerFrame = frame
}

/**
 * Production implementation of [EngineController] per Task P2-5 and Gate T-P2-5.
 *
 * Coordinates the full vision-to-input gaming loop:
 * [Tracker] -> [FrameFilter] -> [GestureInterpreter] -> [LaneSwipeMapper] -> [InputInjector]
 * and publishes [HudFrame] metrics.
 */
class RealEngineController(
    private val trackerRegistry: TrackerRegistry,
    private val profileRepository: ProfileRepository,
    private val calibrationRepository: CalibrationRepository,
    private val inputInjector: InputInjector,
    private val interpretersByMapping: Map<MappingType, GestureInterpreter> = emptyMap(),
    private val interpretersByTracker: Map<TrackerId, GestureInterpreter> = emptyMap(),
    private val customInterpreterProvider: ((GameProfile) -> GestureInterpreter?)? = null,
    private val filter: FrameFilter = PassthroughFilter,
    private val fistDetector: FistDetector? = null,
    private val telemetrySink: TelemetrySink? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : EngineController {

    /**
     * Backward-compatible convenience constructor for tracker-keyed interpreter mappings.
     */
    constructor(
        trackerRegistry: TrackerRegistry,
        profileRepository: ProfileRepository,
        calibrationRepository: CalibrationRepository,
        inputInjector: InputInjector,
        interpreters: Map<TrackerId, GestureInterpreter>,
        filter: FrameFilter = PassthroughFilter,
        telemetrySink: TelemetrySink? = null,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
        fistDetector: FistDetector? = null
    ) : this(
        trackerRegistry = trackerRegistry,
        profileRepository = profileRepository,
        calibrationRepository = calibrationRepository,
        inputInjector = inputInjector,
        interpretersByMapping = emptyMap(),
        interpretersByTracker = interpreters,
        customInterpreterProvider = null,
        filter = filter,
        fistDetector = fistDetector,
        telemetrySink = telemetrySink,
        coroutineScope = coroutineScope
    )

    private val _state = MutableStateFlow(EngineState.IDLE)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _hud = MutableSharedFlow<HudFrame>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val hud: SharedFlow<HudFrame> = _hud.asSharedFlow()

    private var pipelineJob: Job? = null
    private var activeTracker: Tracker? = null
    private var activeProfile: GameProfile? = null
    private var activeCalibration: Calibration? = null
    private var activeInterpreter: GestureInterpreter? = null
    private var activeSessionId: String = UUID.randomUUID().toString()

    override suspend fun start(packageName: String, perf: PerfMode) {
        // Idempotency: safely stop any active session first
        if (_state.value == EngineState.RUNNING || _state.value == EngineState.PAUSED || _state.value == EngineState.CALIBRATING) {
            stop()
        }

        val profile = profileRepository.get(packageName)
        if (profile == null) {
            _state.value = EngineState.ERROR
            safeReleaseTouch()
            return
        }
        activeProfile = profile
        activeSessionId = UUID.randomUUID().toString()

        val cal = calibrationRepository.load(packageName) ?: defaultCalibration()
        activeCalibration = cal

        val tracker = try {
            trackerRegistry.get(profile.tracker)
        } catch (e: Throwable) {
            _state.value = EngineState.ERROR
            safeReleaseTouch()
            return
        }
        activeTracker = tracker

        val interpreter = resolveInterpreter(profile)
        activeInterpreter = interpreter
        resetInterpreter(interpreter)

        filter.configure(profile)
        filter.reset()
        fistDetector?.reset()

        try {
            tracker.start(perf)
        } catch (e: Throwable) {
            _state.value = EngineState.ERROR
            safeReleaseTouch()
            return
        }

        _state.value = EngineState.RUNNING
        startPipeline(tracker, profile, cal, interpreter)
    }

    /**
     * Starts calibration session for [packageName], placing the engine into [EngineState.CALIBRATING].
     *
     * In this state, frames are collected and published to [hud] with live landmarks and metrics
     * so that the UI and [CalibrationRepository] can sample user poses/reach, while all game touch
     * injection is strictly suppressed.
     */
    suspend fun startCalibration(packageName: String, perf: PerfMode = PerfMode.QUALITY) {
        if (_state.value == EngineState.RUNNING || _state.value == EngineState.PAUSED || _state.value == EngineState.CALIBRATING) {
            stop()
        }

        val profile = profileRepository.get(packageName)
        if (profile == null) {
            _state.value = EngineState.ERROR
            safeReleaseTouch()
            return
        }
        activeProfile = profile
        activeSessionId = UUID.randomUUID().toString()

        val cal = calibrationRepository.load(packageName) ?: defaultCalibration()
        activeCalibration = cal

        val tracker = try {
            trackerRegistry.get(profile.tracker)
        } catch (e: Throwable) {
            _state.value = EngineState.ERROR
            safeReleaseTouch()
            return
        }
        activeTracker = tracker

        val interpreter = resolveInterpreter(profile)
        activeInterpreter = interpreter
        resetInterpreter(interpreter)

        filter.configure(profile)
        filter.reset()
        fistDetector?.reset()

        try {
            tracker.start(perf)
        } catch (e: Throwable) {
            _state.value = EngineState.ERROR
            safeReleaseTouch()
            return
        }

        _state.value = EngineState.CALIBRATING
        startPipeline(tracker, profile, cal, interpreter)
    }

    private fun startPipeline(
        tracker: Tracker,
        profile: GameProfile,
        cal: Calibration,
        interpreter: GestureInterpreter
    ) {
        pipelineJob?.cancel()
        pipelineJob = coroutineScope.launch {
            try {
                tracker.frames.collect { rawFrame ->
                    when (_state.value) {
                        EngineState.RUNNING -> {
                            processFrame(rawFrame, tracker, profile, cal, interpreter)
                        }
                        EngineState.PAUSED -> {
                            processPausedFrame(rawFrame, tracker)
                        }
                        EngineState.CALIBRATING -> {
                            val filteredFrame = filter.filter(rawFrame)
                            emitHud(filteredFrame, tracker, lastGesture = null)
                        }
                        EngineState.IDLE, EngineState.ERROR -> {
                            // IDLE or ERROR: drop frame
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = EngineState.ERROR
                safeReleaseTouch()
            } finally {
                safeReleaseTouch()
            }
        }
    }

    private fun processFrame(
        rawFrame: TrackerFrame,
        tracker: Tracker,
        profile: GameProfile,
        cal: Calibration,
        interpreter: GestureInterpreter
    ) {
        val filteredFrame = filter.filter(rawFrame)

        val fistEvents = try {
            fistDetector?.onFrame(filteredFrame) ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }

        val interpreterEvents = try {
            interpreter.onFrame(filteredFrame, profile, cal)
        } catch (_: Throwable) {
            emptyList()
        }

        val events = fistEvents + interpreterEvents
        var lastEvent: GestureEvent? = null

        for (event in events) {
            lastEvent = event
            when (event) {
                is PauseToggle -> {
                    if (_state.value == EngineState.RUNNING) {
                        pause()
                        break
                    } else if (_state.value == EngineState.PAUSED) {
                        resume()
                    }
                }
                is Swipe -> {
                    if (profile.mapping == MappingType.LANE_SWIPE) {
                        val touchSwipe = LaneSwipeMapper.map(event, profile)
                        try {
                            android.util.Log.i("Zorbi/Diagnostic", "Engine: Received SWIPE ${event.dir} (speed=${event.speed}), mapped to (${touchSwipe.from} -> ${touchSwipe.to})")
                            android.util.Log.i("Zorbi/Engine", "Gesture detected: SWIPE ${event.dir} (speed=${event.speed}) -> mapped to (${touchSwipe.from} -> ${touchSwipe.to})")
                        } catch (_: Throwable) {
                            // Ignored in headless JVM unit tests
                        }
                        inputInjector.swipe(touchSwipe.from, touchSwipe.to, touchSwipe.durationMs)
                        telemetrySink?.gestureEvent(
                            sessionId = activeSessionId,
                            type = "SWIPE",
                            magnitude = event.speed
                        )
                    }
                }
                is Slash -> {
                    if (profile.mapping == MappingType.SLASH) {
                        inputInjector.path(event.path, durationMs = 150L)
                        telemetrySink?.gestureEvent(
                            sessionId = activeSessionId,
                            type = "SLASH",
                            magnitude = event.path.size.toFloat()
                        )
                    }
                }
                is Steer -> {
                    if (profile.mapping == MappingType.STEER_BUTTONS) {
                        val deadzone = profile.params.deadzoneDeg / 35.0f
                        if (event.value < -deadzone) {
                            profile.targets["left"]?.let { inputInjector.holdAt(Pt(it.x, it.y)) }
                        } else if (event.value > deadzone) {
                            profile.targets["right"]?.let { inputInjector.holdAt(Pt(it.x, it.y)) }
                        } else {
                            inputInjector.release()
                        }
                    } else if (profile.mapping == MappingType.STEER_DRAG) {
                        if (kotlin.math.abs(event.value) <= 0.001f) {
                            inputInjector.release()
                        } else {
                            val centerX = profile.targets["center"]?.x ?: 0.5f
                            val centerY = profile.targets["center"]?.y ?: 0.5f
                            val leftX = profile.targets["left"]?.x ?: (centerX - 0.3f)
                            val leftY = profile.targets["left"]?.y ?: centerY
                            val rightX = profile.targets["right"]?.x ?: (centerX + 0.3f)
                            val rightY = profile.targets["right"]?.y ?: centerY
                            val targetPt = if (event.value < 0f) {
                                Pt(centerX + event.value * (centerX - leftX), centerY + event.value * (centerY - leftY))
                            } else {
                                Pt(centerX + event.value * (rightX - centerX), centerY + event.value * (rightY - centerY))
                            }
                            inputInjector.dragTo(targetPt)
                        }
                    }
                    telemetrySink?.gestureEvent(
                        sessionId = activeSessionId,
                        type = "STEER",
                        magnitude = event.value
                    )
                }
            }
        }

        emitHud(filteredFrame, tracker, lastEvent)
    }

    private fun processPausedFrame(rawFrame: TrackerFrame, tracker: Tracker) {
        val filteredFrame = filter.filter(rawFrame)
        val fistEvents = try {
            fistDetector?.onFrame(filteredFrame) ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }

        var lastEvent: GestureEvent? = null
        for (event in fistEvents) {
            if (event is PauseToggle) {
                lastEvent = event
                if (_state.value == EngineState.PAUSED) {
                    resume()
                    break
                }
            }
        }

        emitHud(filteredFrame, tracker, lastGesture = lastEvent)
    }

    private fun emitHud(
        frame: TrackerFrame,
        tracker: Tracker,
        lastGesture: GestureEvent?
    ) {
        val nowNanos = currentMonotonicNanos()
        val latencyMs = if (frame.tsNanos > 0L) {
            ((nowNanos - frame.tsNanos) / 1_000_000f).coerceAtLeast(0f)
        } else {
            0f
        }

        val landmarks = when (frame) {
            is HandsFrame -> frame.hands.flatMap { it.landmarks }
            is PoseFrame -> frame.landmarks
            else -> emptyList()
        }

        val stats = tracker.stats.value
        val hudFrame = HudFrame(
            tsNanos = frame.tsNanos,
            trackerId = tracker.id,
            landmarks = landmarks,
            lastGesture = lastGesture,
            fps = stats.fps,
            e2eLatencyMs = latencyMs
        )
        _hud.tryEmit(hudFrame)
    }

    private fun currentMonotonicNanos(): Long {
        return try {
            android.os.SystemClock.elapsedRealtimeNanos()
        } catch (_: Throwable) {
            System.nanoTime()
        }
    }

    override fun pause() {
        if (_state.value == EngineState.RUNNING) {
            _state.value = EngineState.PAUSED
            safeReleaseTouch()
            filter.reset()
            fistDetector?.reset()
        }
    }

    override fun resume() {
        if (_state.value == EngineState.PAUSED) {
            resetInterpreter(activeInterpreter)
            filter.reset()
            fistDetector?.reset()
            _state.value = EngineState.RUNNING
        }
    }

    override suspend fun stop() {
        _state.value = EngineState.IDLE
        pipelineJob?.cancel()
        pipelineJob = null
        safeReleaseTouch()

        try {
            activeTracker?.stop()
        } catch (_: Throwable) { }

        resetInterpreter(activeInterpreter)
        filter.reset()
        fistDetector?.reset()
        activeTracker = null
        activeProfile = null
        activeInterpreter = null
    }

    private fun safeReleaseTouch() {
        try {
            inputInjector.release()
        } catch (_: Throwable) { }
    }

    private fun resetInterpreter(interpreter: GestureInterpreter?) {
        (interpreter as? ResettableInterpreter)?.reset()
    }

    private fun resolveInterpreter(profile: GameProfile): GestureInterpreter {
        customInterpreterProvider?.invoke(profile)?.let { return it }
        interpretersByMapping[profile.mapping]?.let { return it }
        interpretersByTracker[profile.tracker]?.let { trackerInterpreter ->
            val matches = when (profile.mapping) {
                MappingType.SLASH -> trackerInterpreter is SlashGestureInterpreter || trackerInterpreter !is SwipeGestureInterpreter
                MappingType.LANE_SWIPE -> trackerInterpreter is SwipeGestureInterpreter || trackerInterpreter !is SlashGestureInterpreter
                MappingType.STEER_BUTTONS, MappingType.STEER_DRAG -> trackerInterpreter is WheelGestureInterpreter || trackerInterpreter is LeanGestureInterpreter || trackerInterpreter.accepts == profile.tracker
                else -> true
            }
            if (matches) return trackerInterpreter
        }
        return createDefaultInterpreter(profile)
    }

    private fun createDefaultInterpreter(profile: GameProfile): GestureInterpreter {
        return when (profile.mapping) {
            MappingType.SLASH -> SlashGestureInterpreter()
            MappingType.LANE_SWIPE -> SwipeGestureInterpreter()
            MappingType.STEER_BUTTONS, MappingType.STEER_DRAG -> when (profile.tracker) {
                TrackerId.POSE_LITE -> LeanGestureInterpreter()
                else -> WheelGestureInterpreter()
            }
            else -> when (profile.tracker) {
                TrackerId.POSE_LITE -> LeanGestureInterpreter()
                TrackerId.HAND_DUAL_WHEEL -> WheelGestureInterpreter()
                else -> SwipeGestureInterpreter()
            }
        }
    }

    private fun defaultCalibration(): Calibration {
        return Calibration(
            neutralAngleDeg = 0f,
            maxAngleDeg = 35f,
            reachScaleX = 1.0f,
            reachScaleY = 1.0f,
            dominantHand = Side.RIGHT,
            createdAtEpochMs = System.currentTimeMillis()
        )
    }
}
