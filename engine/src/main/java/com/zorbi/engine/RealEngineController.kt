package com.zorbi.engine

import android.content.Context
import android.util.Log
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
import com.zorbi.core.ZorbiGestureConfig
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
    private val telemetrySink: TelemetrySink? = null,
    private val context: Context? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : EngineController {

    companion object {
        private const val TAG = "RealEngineController"
    }

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
        context: Context? = null,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    ) : this(
        trackerRegistry = trackerRegistry,
        profileRepository = profileRepository,
        calibrationRepository = calibrationRepository,
        inputInjector = inputInjector,
        interpretersByMapping = emptyMap(),
        interpretersByTracker = interpreters,
        customInterpreterProvider = null,
        filter = filter,
        telemetrySink = telemetrySink,
        context = context,
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
    private var lastSteerState: String = "NEUTRAL"
    private var activeTracker: Tracker? = null
    private var activeProfile: GameProfile? = null
    private var activeCalibration: Calibration? = null
    private var activeInterpreter: GestureInterpreter? = null
    private var activeSessionId: String = UUID.randomUUID().toString()

    private var sessionStartTimeMs: Long = 0L
    private var gestureCount: Int = 0

    override suspend fun start(packageName: String, perf: PerfMode) {
        Log.i(TAG, "start() requested for packageName=$packageName, perf=$perf")
        // Idempotency: safely stop any active session first
        if (_state.value == EngineState.RUNNING || _state.value == EngineState.PAUSED || _state.value == EngineState.CALIBRATING) {
            stop()
        }

        context?.let { ZorbiEngineService.start(it, packageName) }

        val profile = profileRepository.get(packageName)
        if (profile == null) {
            Log.e(TAG, "GameProfile not found for $packageName; transitioning to ERROR")
            _state.value = EngineState.ERROR
            context?.let { ZorbiEngineService.stop(it) }
            safeReleaseTouch()
            return
        }
        activeProfile = profile
        activeSessionId = UUID.randomUUID().toString()
        sessionStartTimeMs = System.currentTimeMillis()
        gestureCount = 0

        val cal = calibrationRepository.load(packageName) ?: defaultCalibration()
        activeCalibration = cal

        val tracker = try {
            trackerRegistry.get(profile.tracker)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to resolve tracker ${profile.tracker}", e)
            _state.value = EngineState.ERROR
            context?.let { ZorbiEngineService.stop(it) }
            safeReleaseTouch()
            return
        }
        activeTracker = tracker

        val interpreter = resolveInterpreter(profile)
        activeInterpreter = interpreter
        resetInterpreter(interpreter)

        filter.configure(profile)
        filter.reset()

        try {
            tracker.start(perf)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start tracker ${profile.tracker}", e)
            _state.value = EngineState.ERROR
            context?.let { ZorbiEngineService.stop(it) }
            safeReleaseTouch()
            return
        }

        _state.value = EngineState.RUNNING
        Log.i(TAG, "Engine RUNNING: tracker=${profile.tracker}, mapping=${profile.mapping}, game=${profile.displayName}")
        showInGameFeedback("🎮 ZORBI: Motion Controls Active for ${profile.displayName}")
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
        sessionStartTimeMs = System.currentTimeMillis()
        gestureCount = 0

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
                            processPausedFrame(rawFrame, tracker, profile, cal, interpreter)
                        }
                        EngineState.CALIBRATING -> {
                            emitHud(rawFrame, tracker, lastGesture = null)
                        }
                        EngineState.IDLE, EngineState.ERROR -> {
                            // IDLE or ERROR: drop frame
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Exception in tracking pipeline flow collection", e)
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
        updateHandAlignmentOverlay(filteredFrame, tracker)

        val events = try {
            interpreter.onFrame(filteredFrame, profile, cal)
        } catch (e: Throwable) {
            Log.e(TAG, "Error evaluating interpreter.onFrame", e)
            emptyList()
        }

        var lastEvent: GestureEvent? = null

        for (event in events) {
            lastEvent = event
            gestureCount++
            Log.i(TAG, "Detected gesture event: $event for profile=${profile.packageName}")
            when (event) {
                is PauseToggle -> {
                    if (_state.value == EngineState.RUNNING) {
                        pause()
                        showInGameFeedback("⏸️ PAUSED")
                    } else if (_state.value == EngineState.PAUSED) {
                        resume()
                        showInGameFeedback("▶️ RESUMED")
                    }
                }
                is Swipe -> {
                    if (event.speed == 88.0f) {
                        // Custom Fist Action
                        when (ZorbiGestureConfig.fistAction) {
                            ZorbiGestureConfig.FistAction.HORN -> {
                                val target = profile.targets["horn"] ?: ZorbiGestureConfig.hornTarget
                                inputInjector.tap(Pt(target.x, target.y))
                                showInGameFeedback("📢 HORN HONKED!")
                            }
                            ZorbiGestureConfig.FistAction.BOOST -> {
                                val target = profile.targets["boost"] ?: ZorbiGestureConfig.boostTarget
                                inputInjector.tap(Pt(target.x, target.y))
                                showInGameFeedback("🚀 NITRO BOOST!")
                            }
                            ZorbiGestureConfig.FistAction.HANDBRAKE -> {
                                val target = profile.targets["handbrake"] ?: ZorbiGestureConfig.handbrakeTarget
                                inputInjector.tap(Pt(target.x, target.y))
                                showInGameFeedback("🛑 HANDBRAKE ENGAGED!")
                            }
                            ZorbiGestureConfig.FistAction.PAUSE -> {
                                if (_state.value == EngineState.RUNNING) pause() else resume()
                                showInGameFeedback("⏸️ FIST: PAUSE TOGGLED")
                            }
                        }
                    } else if (event.speed == 77.0f) {
                        // Custom Pinch Action
                        when (ZorbiGestureConfig.pinchAction) {
                            ZorbiGestureConfig.PinchAction.HEADLIGHT -> {
                                val target = profile.targets["headlight"] ?: ZorbiGestureConfig.headlightTarget
                                inputInjector.tap(Pt(target.x, target.y))
                                showInGameFeedback("💡 HEADLIGHTS ON/OFF")
                            }
                            ZorbiGestureConfig.PinchAction.WIPER -> {
                                val target = profile.targets["wiper"] ?: ZorbiGestureConfig.wiperTarget
                                inputInjector.tap(Pt(target.x, target.y))
                                showInGameFeedback("🌧️ WIPERS ON/OFF")
                            }
                            ZorbiGestureConfig.PinchAction.HAZARD -> {
                                val target = profile.targets["hazard"] ?: ZorbiGestureConfig.hazardTarget
                                inputInjector.tap(Pt(target.x, target.y))
                                showInGameFeedback("⚠️ HAZARDS TOGGLED")
                            }
                            ZorbiGestureConfig.PinchAction.PAUSE -> {
                                if (_state.value == EngineState.RUNNING) pause() else resume()
                                showInGameFeedback("🤏 PINCH: PAUSE TOGGLED")
                            }
                        }
                    } else if (profile.targets.containsKey("gear_up") && event.dir == Dir.UP) {
                        profile.targets["gear_up"]?.let { inputInjector.tap(Pt(it.x, it.y)) }
                        showInGameFeedback("🕹️ GEAR SHIFT: DRIVE [D]")
                    } else if (profile.targets.containsKey("gear_down") && event.dir == Dir.DOWN) {
                        profile.targets["gear_down"]?.let { inputInjector.tap(Pt(it.x, it.y)) }
                        showInGameFeedback("🕹️ GEAR SHIFT: REVERSE [R]")
                    } else if (profile.mapping == MappingType.LANE_SWIPE) {
                        val touchSwipe = LaneSwipeMapper.map(event, profile)
                        Log.i(TAG, "Injecting Swipe: ${touchSwipe.from} -> ${touchSwipe.to} (${touchSwipe.durationMs}ms)")
                        inputInjector.swipe(touchSwipe.from, touchSwipe.to, touchSwipe.durationMs)
                        showInGameFeedback("⚡ SWIPE ${event.dir}")
                        telemetrySink?.gestureEvent(
                            sessionId = activeSessionId,
                            type = "SWIPE",
                            magnitude = event.speed
                        )
                    }
                }
                is Slash -> {
                    if (profile.mapping == MappingType.SLASH) {
                        Log.i(TAG, "Injecting Slash: ${event.path.size} points")
                        inputInjector.path(event.path, durationMs = 150L)
                        telemetrySink?.gestureEvent(
                            sessionId = activeSessionId,
                            type = "SLASH",
                            magnitude = event.path.size.toFloat()
                        )
                    }
                }
                is Steer -> {
                    val isBusOrDrivingGame = profile.packageName.contains("bussimulator") ||
                            profile.packageName.contains("drdriving") ||
                            profile.targets.containsKey("gas")

                    if (isBusOrDrivingGame) {
                        val gasTarget = profile.targets["gas"] ?: ZorbiGestureConfig.gasPedal
                        val brakeTarget = profile.targets["brake"] ?: ZorbiGestureConfig.brakePedal
                        val steerCenter = profile.targets["steer_wheel"] ?: profile.targets["center"] ?: ZorbiGestureConfig.steerWheelCenter
                        val leftBtn = profile.targets["left"] ?: ZorbiGestureConfig.leftArrow
                        val rightBtn = profile.targets["right"] ?: ZorbiGestureConfig.rightArrow

                        if (event.value >= 0.90f) {
                            inputInjector.holdAt(Pt(gasTarget.x, gasTarget.y))
                            if (lastSteerState != "GAS") {
                                lastSteerState = "GAS"
                                showInGameFeedback("🏎️ GAS (ACCELERATING) • RIGHT HAND FORWARD")
                            }
                        } else if (event.value <= -0.90f) {
                            inputInjector.holdAt(Pt(brakeTarget.x, brakeTarget.y))
                            if (lastSteerState != "BRAKE") {
                                lastSteerState = "BRAKE"
                                showInGameFeedback("🛑 BRAKE (STOPPING) • LEFT HAND FORWARD")
                            }
                        } else {
                            val angle = event.angleDeg
                            val deadzone = profile.params.deadzoneDeg
                            if (kotlin.math.abs(angle) > deadzone) {
                                if (profile.targets.containsKey("steer_wheel") ||
                                    ZorbiGestureConfig.steerLayout == ZorbiGestureConfig.SteerLayout.LEFT_WHEEL_RIGHT_PEDALS) {
                                    val rad = Math.toRadians(angle.toDouble())
                                    val radius = 0.08f
                                    val touchX = (steerCenter.x + kotlin.math.sin(rad) * radius).toFloat()
                                    val touchY = (steerCenter.y - kotlin.math.cos(rad) * radius * 0.4f).toFloat()
                                    inputInjector.holdAt(Pt(touchX, touchY))
                                } else {
                                    if (angle < 0f) {
                                        inputInjector.holdAt(Pt(leftBtn.x, leftBtn.y))
                                    } else {
                                        inputInjector.holdAt(Pt(rightBtn.x, rightBtn.y))
                                    }
                                }
                                if (lastSteerState != "STEER") {
                                    lastSteerState = "STEER"
                                    showInGameFeedback("🔄 STEERING: ${angle.toInt()}°")
                                }
                            } else {
                                inputInjector.release()
                                if (lastSteerState != "NEUTRAL") {
                                    lastSteerState = "NEUTRAL"
                                    showInGameFeedback("🎮 COASTING • HANDS LEVEL")
                                }
                            }
                        }
                    } else if (profile.mapping == MappingType.STEER_BUTTONS) {
                        val deadzone = profile.params.deadzoneDeg / 35.0f
                        val leftTarget = profile.targets["left"] ?: profile.targets["brake"] ?: com.zorbi.core.TargetPoint(0.14f, 0.82f)
                        val rightTarget = profile.targets["right"] ?: profile.targets["gas"] ?: com.zorbi.core.TargetPoint(0.89f, 0.82f)
                        if (event.value < -deadzone) {
                            Log.d(TAG, "Injecting Steer Left: ${event.value}")
                            inputInjector.holdAt(Pt(leftTarget.x, leftTarget.y))
                            if (lastSteerState != "BRAKE") {
                                lastSteerState = "BRAKE"
                                showInGameFeedback("🛑 BRAKE ACTIVE (Left Hand Forward)")
                            }
                        } else if (event.value > deadzone) {
                            Log.d(TAG, "Injecting Steer Right: ${event.value}")
                            inputInjector.holdAt(Pt(rightTarget.x, rightTarget.y))
                            if (lastSteerState != "GAS") {
                                lastSteerState = "GAS"
                                showInGameFeedback("🏎️ GAS ACTIVE (Right Hand Forward)")
                            }
                        } else {
                            inputInjector.release()
                            if (lastSteerState != "NEUTRAL") {
                                lastSteerState = "NEUTRAL"
                                showInGameFeedback("🎮 COASTING (Hands Back)")
                            }
                        }
                    } else if (profile.mapping == MappingType.STEER_DRAG) {
                        if (kotlin.math.abs(event.value) <= 0.001f) {
                            inputInjector.release()
                        } else {
                            val centerX = profile.targets["center"]?.x ?: profile.targets["steer_center"]?.x ?: 0.5f
                            val centerY = profile.targets["center"]?.y ?: profile.targets["steer_center"]?.y ?: 0.5f
                            val leftX = profile.targets["left"]?.x ?: (centerX - 0.3f)
                            val leftY = profile.targets["left"]?.y ?: centerY
                            val rightX = profile.targets["right"]?.x ?: (centerX + 0.3f)
                            val rightY = profile.targets["right"]?.y ?: centerY
                            val targetPt = if (event.value < 0f) {
                                Pt(centerX + event.value * (centerX - leftX), centerY + event.value * (centerY - leftY))
                            } else {
                                Pt(centerX + event.value * (rightX - centerX), centerY + event.value * (rightY - centerY))
                            }
                            Log.d(TAG, "Injecting Steer Drag: $targetPt")
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

    private fun processPausedFrame(
        rawFrame: TrackerFrame,
        tracker: Tracker,
        profile: GameProfile,
        cal: Calibration,
        interpreter: GestureInterpreter
    ) {
        val filteredFrame = filter.filter(rawFrame)
        updateHandAlignmentOverlay(filteredFrame, tracker)
        val events = try {
            interpreter.onFrame(filteredFrame, profile, cal)
        } catch (e: Throwable) {
            emptyList()
        }
        val pauseEvent = events.firstOrNull { it is PauseToggle }
        if (pauseEvent != null) {
            Log.i(TAG, "Detected PauseToggle while paused -> resuming engine")
            resume()
            showInGameFeedback("🤏 PINCH: RESUMED")
            emitHud(filteredFrame, tracker, lastGesture = pauseEvent)
            return
        }
        emitHud(filteredFrame, tracker, lastGesture = null)
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
            Log.i(TAG, "Pausing engine")
            _state.value = EngineState.PAUSED
            safeReleaseTouch()
            filter.reset()
        }
    }

    override fun resume() {
        if (_state.value == EngineState.PAUSED) {
            Log.i(TAG, "Resuming engine")
            resetInterpreter(activeInterpreter)
            _state.value = EngineState.RUNNING
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "Stopping engine")
        activeProfile?.let { prof ->
            val durationS = ((System.currentTimeMillis() - sessionStartTimeMs) / 1000L).toInt().coerceAtLeast(1)
            val stats = activeTracker?.stats?.value ?: com.zorbi.core.TrackerStats(fps = 60f, inferenceMsP50 = 8f, inferenceMsP95 = 12f, dropped = 0)
            try {
                telemetrySink?.session(
                    started = sessionStartTimeMs,
                    p = prof,
                    stats = stats,
                    durationS = durationS,
                    gestures = gestureCount,
                    perf = PerfMode.QUALITY
                )
            } catch (_: Throwable) { }
        }
        context?.let { ZorbiEngineService.stop(it) }
        _state.value = EngineState.IDLE
        pipelineJob?.cancel()
        pipelineJob = null
        safeReleaseTouch()

        try {
            activeTracker?.stop()
        } catch (_: Throwable) { }

        resetInterpreter(activeInterpreter)
        filter.reset()
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

    private var lastAlignmentStatus: String = ""
    private var lastAlignmentUpdateMs: Long = 0L

    private fun updateHandAlignmentOverlay(frame: TrackerFrame, tracker: Tracker) {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastAlignmentUpdateMs < 140L) return
        lastAlignmentUpdateMs = nowMs

        val (statusText, strokeColor, bgFill) = when (frame) {
            is HandsFrame -> {
                val hands = frame.hands
                when {
                    hands.isEmpty() -> {
                        Triple("🔴 NO HANDS DETECTED • SHOW HANDS TO CAMERA", "#FF2255", "#E6330008")
                    }
                    tracker.id == TrackerId.HAND_DUAL_WHEEL && hands.size < 2 -> {
                        val visibleHand = hands.firstOrNull()
                        val isLeft = visibleHand?.side == Side.LEFT || (visibleHand != null && visibleHand.landmarks.isNotEmpty() && visibleHand.landmarks[0].x < 0.50f)
                        if (isLeft) {
                            Triple("🟡 LEFT HAND DETECTED ✋ • RAISE RIGHT HAND", "#FFB703", "#E6332200")
                        } else {
                            Triple("🟡 RIGHT HAND DETECTED ✋ • RAISE LEFT HAND", "#FFB703", "#E6332200")
                        }
                    }
                    tracker.id == TrackerId.HAND_DUAL_WHEEL && hands.size >= 2 -> {
                        val h0 = hands[0]
                        val h1 = hands[1]
                        val (leftHand, rightHand) = if (h0.landmarks[0].x <= h1.landmarks[0].x) h0 to h1 else h1 to h0
                        val wristL = leftHand.landmarks[0]
                        val wristR = rightHand.landmarks[0]
                        val edgeMarginX = 0.06f
                        val edgeMarginY = 0.08f
                        val isNearEdge = wristL.x < edgeMarginX || wristR.x > (1f - edgeMarginX) ||
                                wristL.y < edgeMarginY || wristR.y < edgeMarginY ||
                                wristL.y > (1f - edgeMarginY) || wristR.y > (1f - edgeMarginY)
                        val wristDistX = wristR.x - wristL.x
                        when {
                            isNearEdge -> Triple("🟡 ALIGN HANDS • MOVE TO CAMERA CENTER", "#FFB703", "#E6332200")
                            wristDistX < 0.12f -> Triple("🟡 SPREAD HANDS • HOLD VIRTUAL WHEEL", "#FFB703", "#E6332200")
                            else -> Triple("🟢 BOTH HANDS DETECTED (L + R) • READY TO DRIVE", "#00FF88", "#E6002612")
                        }
                    }
                    else -> {
                        val hand = hands.firstOrNull()
                        val isLeft = hand?.side == Side.LEFT || (hand != null && hand.landmarks.isNotEmpty() && hand.landmarks[0].x < 0.50f)
                        val handName = if (isLeft) "LEFT" else "RIGHT"
                        if (hand != null && hand.landmarks.isNotEmpty()) {
                            val wrist = hand.landmarks[0]
                            if (wrist.x < 0.06f || wrist.x > 0.94f || wrist.y < 0.08f || wrist.y > 0.92f) {
                                Triple("🟡 $handName HAND DETECTED • CENTER IN FRONT OF CAMERA", "#FFB703", "#E6332200")
                            } else {
                                Triple("🟢 $handName HAND ACTIVE ✋ • MOTION READY", "#00FF88", "#E6002612")
                            }
                        } else {
                            Triple("🔴 NO HANDS DETECTED • SHOW HANDS TO CAMERA", "#FF2255", "#E6330008")
                        }
                    }
                }
            }
            is PoseFrame -> {
                if (frame.landmarks.isNotEmpty() && frame.score >= 0.4f) {
                    Triple("🟢 BODY POSE ALIGNED & READY", "#00FF88", "#E6002612")
                } else {
                    Triple("🔴 NO POSE DETECTED • STEP INTO CAMERA VIEW", "#FF2255", "#E6330008")
                }
            }
            else -> {
                Triple("🔴 NO HANDS DETECTED • SHOW HANDS TO CAMERA", "#FF2255", "#E6330008")
            }
        }

        if (statusText != lastAlignmentStatus) {
            lastAlignmentStatus = statusText
            notifyAccessibilityAlignment(statusText, strokeColor, bgFill)
        }
    }

    private fun notifyAccessibilityAlignment(statusText: String, strokeColor: String, bgFill: String) {
        try {
            val clazz = Class.forName("com.zorbi.input.ZorbiAccessibilityService")
            val companionField = clazz.getDeclaredField("Companion")
            val companion = companionField.get(null)
            val getInstanceMethod = companion.javaClass.getMethod("getInstance")
            val service = getInstanceMethod.invoke(companion)
            if (service != null) {
                val updateMethod = service.javaClass.getMethod(
                    "updateAlignmentStatus",
                    String::class.java,
                    String::class.java,
                    String::class.java
                )
                updateMethod.invoke(service, statusText, strokeColor, bgFill)
            }
        } catch (_: Throwable) { }
    }

    private fun showInGameFeedback(message: String) {
        context?.let { ctx ->
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (_: Throwable) { }
        }
        try {
            val clazz = Class.forName("com.zorbi.input.ZorbiAccessibilityService")
            val companionField = clazz.getDeclaredField("Companion")
            val companion = companionField.get(null)
            val getInstanceMethod = companion.javaClass.getMethod("getInstance")
            val service = getInstanceMethod.invoke(companion)
            if (service != null) {
                val showMethod = service.javaClass.getMethod("showFeedback", String::class.java, Boolean::class.javaPrimitiveType)
                showMethod.invoke(service, message, true)
            }
        } catch (_: Throwable) { }
    }
}
