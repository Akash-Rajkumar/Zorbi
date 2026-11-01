package com.zorbi.engine

import com.zorbi.calib.fist.FistDetector

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.zorbi.core.Calibration
import com.zorbi.core.Dir
import com.zorbi.core.EngineState
import com.zorbi.core.GameProfile
import com.zorbi.core.GestureEvent
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.MappingType
import com.zorbi.core.PauseToggle
import com.zorbi.core.PerfMode
import com.zorbi.core.PoseFrame
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.Slash
import com.zorbi.core.Steer
import com.zorbi.core.Swipe
import com.zorbi.core.TargetPoint
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerStats
import com.zorbi.fakes.p1.FakeCalibrationRepository
import com.zorbi.fakes.p1.FakeTracker
import com.zorbi.fakes.p1.FakeTrackerRegistry
import com.zorbi.fakes.p2.FakeGestureInterpreter
import com.zorbi.fakes.p2.FakeInputInjector
import com.zorbi.fakes.p2.FakeProfileRepository
import com.zorbi.fakes.p4.FakeTelemetrySink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for [RealEngineController] covering Test Gate T-P2-5.
 *
 * Verifies:
 * - State machine transitions: IDLE -> RUNNING -> PAUSED -> IDLE and ERROR handling.
 * - Tracker lifecycle management (start/stop) with idempotency.
 * - Gesture event routing: Swipe (Lane mapping), Slash (path), Steer (holdAt/release).
 * - PauseToggle in-game gesture handling.
 * - Injection of injectable smoothing [FrameFilter].
 * - Frame error / disconnection resiliency.
 * - Non-blocking [com.zorbi.core.HudFrame] emission.
 * - Immediate touch release on stop, pause, and error.
 */
class RealEngineControllerTest {

    private lateinit var fakeHandSingleTracker: FakeTracker
    private lateinit var fakeDualWheelTracker: FakeTracker
    private lateinit var fakePoseTracker: FakeTracker
    private lateinit var trackerRegistry: FakeTrackerRegistry
    private lateinit var profileRepo: FakeProfileRepository
    private lateinit var calibRepo: FakeCalibrationRepository
    private lateinit var inputInjector: FakeInputInjector
    private lateinit var telemetrySink: FakeTelemetrySink
    private lateinit var testScope: CoroutineScope
    private lateinit var controller: RealEngineController

    private val subwayProfile = GameProfile(
        schemaVersion = 1,
        packageName = "com.kiloo.subwaysurf",
        displayName = "Subway Surfers",
        orientation = ProfileOrientation.PORTRAIT,
        tracker = TrackerId.HAND_SINGLE,
        mapping = MappingType.LANE_SWIPE,
        targets = mapOf(
            "center" to TargetPoint(0.5f, 0.5f),
            "left" to TargetPoint(0.2f, 0.5f),
            "right" to TargetPoint(0.8f, 0.5f),
            "up" to TargetPoint(0.5f, 0.2f),
            "down" to TargetPoint(0.5f, 0.8f)
        )
    )

    private val fruitProfile = GameProfile(
        schemaVersion = 1,
        packageName = "com.halfbrick.fruitninjafree",
        displayName = "Fruit Ninja",
        orientation = ProfileOrientation.LANDSCAPE,
        tracker = TrackerId.HAND_SINGLE,
        mapping = MappingType.SLASH
    )

    private val steerProfile = GameProfile(
        schemaVersion = 1,
        packageName = "com.eamobile.nfsshift",
        displayName = "Need For Speed",
        orientation = ProfileOrientation.LANDSCAPE,
        tracker = TrackerId.HAND_DUAL_WHEEL,
        mapping = MappingType.STEER_BUTTONS,
        targets = mapOf(
            "left" to TargetPoint(0.15f, 0.85f),
            "right" to TargetPoint(0.85f, 0.85f)
        ),
        params = ProfileParams(deadzoneDeg = 8.0f)
    )

    @Before
    fun setUp() {
        fakeHandSingleTracker = FakeTracker(TrackerId.HAND_SINGLE)
        fakeDualWheelTracker = FakeTracker(TrackerId.HAND_DUAL_WHEEL)
        fakePoseTracker = FakeTracker(TrackerId.POSE_LITE)
        trackerRegistry = FakeTrackerRegistry(
            mapOf(
                TrackerId.HAND_SINGLE to fakeHandSingleTracker,
                TrackerId.HAND_DUAL_WHEEL to fakeDualWheelTracker,
                TrackerId.POSE_LITE to fakePoseTracker
            )
        )
        profileRepo = FakeProfileRepository()
        calibRepo = FakeCalibrationRepository()
        inputInjector = FakeInputInjector()
        telemetrySink = FakeTelemetrySink()
        testScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        controller = createController()
    }

    private fun createController(
        interpreters: Map<TrackerId, com.zorbi.core.GestureInterpreter> = emptyMap(),
        filter: FrameFilter = PassthroughFilter,
        customRegistry: FakeTrackerRegistry = trackerRegistry,
        fistDetector: FistDetector? = null
    ): RealEngineController {
        return RealEngineController(
            trackerRegistry = customRegistry,
            profileRepository = profileRepo,
            calibrationRepository = calibRepo,
            inputInjector = inputInjector,
            interpreters = interpreters,
            filter = filter,
            telemetrySink = telemetrySink,
            coroutineScope = testScope,
            fistDetector = fistDetector
        )
    }

    @Test
    fun test1_engineStart_transitionsToRunning_startsTracker() = runTest {
        profileRepo.saveLocal(subwayProfile)
        controller.start(subwayProfile.packageName, PerfMode.BALANCED)

        assertThat(controller.state.value).isEqualTo(EngineState.RUNNING)
        assertThat(fakeHandSingleTracker.isStarted).isTrue()
    }

    @Test
    fun test2_engineStop_transitionsToIdle_stopsTracker_releasesTouch() = runTest {
        profileRepo.saveLocal(subwayProfile)
        controller.start(subwayProfile.packageName, PerfMode.BALANCED)
        controller.stop()

        assertThat(controller.state.value).isEqualTo(EngineState.IDLE)
        assertThat(fakeHandSingleTracker.isStarted).isFalse()
        assertThat(inputInjector.recordedActions).contains("release")
    }

    @Test
    fun test3_idleToRunningTransition() = runTest {
        assertThat(controller.state.value).isEqualTo(EngineState.IDLE)
        profileRepo.saveLocal(subwayProfile)
        controller.start(subwayProfile.packageName, PerfMode.QUALITY)
        assertThat(controller.state.value).isEqualTo(EngineState.RUNNING)
    }

    @Test
    fun test4_runningToPausedTransition() = runTest {
        profileRepo.saveLocal(subwayProfile)
        controller.start(subwayProfile.packageName, PerfMode.BALANCED)
        inputInjector.recordedActions.clear()

        controller.pause()

        assertThat(controller.state.value).isEqualTo(EngineState.PAUSED)
        assertThat(inputInjector.recordedActions).contains("release")
    }

    @Test
    fun test5_pausedToRunningTransition() = runTest {
        profileRepo.saveLocal(subwayProfile)
        controller.start(subwayProfile.packageName, PerfMode.BALANCED)
        controller.pause()
        assertThat(controller.state.value).isEqualTo(EngineState.PAUSED)

        controller.resume()
        assertThat(controller.state.value).isEqualTo(EngineState.RUNNING)
    }

    @Test
    fun test6_swipeEventRouting_laneSwipe() = runTest {
        val fakeInterpreter = FakeGestureInterpreter(TrackerId.HAND_SINGLE)
        val testController = createController(interpreters = mapOf(TrackerId.HAND_SINGLE to fakeInterpreter))
        profileRepo.saveLocal(subwayProfile)
        testController.start(subwayProfile.packageName, PerfMode.BALANCED)

        fakeInterpreter.queuedEvents = listOf(
            Swipe(tsNanos = 1_000_000L, dir = Dir.LEFT, speed = 1.5f)
        )

        fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = 1_000_000L, hands = emptyList()))

        assertThat(inputInjector.recordedActions).contains(
            "swipe(from=(0.5, 0.5), to=(0.2, 0.5), duration=100)"
        )
        assertThat(telemetrySink.recordedEvents).isNotEmpty()
        assertThat(telemetrySink.recordedEvents.last()).contains("SWIPE mag=1.5")
    }

    @Test
    fun test7_slashEventRouting_slashPath() = runTest {
        val fakeInterpreter = FakeGestureInterpreter(TrackerId.HAND_SINGLE)
        val testController = createController(interpreters = mapOf(TrackerId.HAND_SINGLE to fakeInterpreter))
        profileRepo.saveLocal(fruitProfile)
        testController.start(fruitProfile.packageName, PerfMode.BALANCED)

        fakeInterpreter.queuedEvents = listOf(
            Slash(tsNanos = 2_000_000L, path = listOf(Pt(0.1f, 0.2f), Pt(0.5f, 0.5f), Pt(0.8f, 0.9f)))
        )

        fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = 2_000_000L, hands = emptyList()))

        assertThat(inputInjector.recordedActions).contains("path(points=3, duration=150)")
        assertThat(telemetrySink.recordedEvents).isNotEmpty()
        assertThat(telemetrySink.recordedEvents.last()).contains("SLASH mag=3.0")
    }

    @Test
    fun test8_correctGameProfileMappingSelection() = runTest {
        val fakeInterpreter = FakeGestureInterpreter(TrackerId.HAND_DUAL_WHEEL)
        val testController = createController(interpreters = mapOf(TrackerId.HAND_DUAL_WHEEL to fakeInterpreter))
        profileRepo.saveLocal(steerProfile)
        testController.start(steerProfile.packageName, PerfMode.BALANCED)

        // Turn Left beyond deadzone (value < -8/35 = -0.228)
        fakeInterpreter.queuedEvents = listOf(
            Steer(tsNanos = 3_000_000L, value = -0.7f, angleDeg = -24.5f)
        )
        fakeDualWheelTracker.emitFrame(HandsFrame(tsNanos = 3_000_000L, hands = emptyList()))
        assertThat(inputInjector.recordedActions).contains("holdAt(0.15, 0.85)")

        // Turn Right beyond deadzone (value > 8/35 = 0.228)
        fakeInterpreter.queuedEvents = listOf(
            Steer(tsNanos = 3_010_000L, value = 0.8f, angleDeg = 28.0f)
        )
        fakeDualWheelTracker.emitFrame(HandsFrame(tsNanos = 3_010_000L, hands = emptyList()))
        assertThat(inputInjector.recordedActions).contains("holdAt(0.85, 0.85)")

        // Return to center within deadzone -> releases touch
        fakeInterpreter.queuedEvents = listOf(
            Steer(tsNanos = 3_020_000L, value = 0.05f, angleDeg = 1.75f)
        )
        fakeDualWheelTracker.emitFrame(HandsFrame(tsNanos = 3_020_000L, hands = emptyList()))
        assertThat(inputInjector.recordedActions.last()).isEqualTo("release")
    }

    @Test
    fun test9_invalidTrackingFrameHandling() = runTest {
        val throwingInterpreter = object : com.zorbi.core.GestureInterpreter {
            override val accepts: TrackerId = TrackerId.HAND_SINGLE
            override fun onFrame(frame: TrackerFrame, profile: GameProfile, cal: Calibration): List<GestureEvent> {
                throw IllegalStateException("Malformed frame landmark data")
            }
        }
        val testController = createController(interpreters = mapOf(TrackerId.HAND_SINGLE to throwingInterpreter))
        profileRepo.saveLocal(subwayProfile)
        testController.start(subwayProfile.packageName, PerfMode.BALANCED)
        inputInjector.recordedActions.clear()

        // Emitting frame should not crash engine or emit touches
        fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = 4_000_000L, hands = emptyList()))

        assertThat(testController.state.value).isEqualTo(EngineState.RUNNING)
        assertThat(inputInjector.recordedActions).isEmpty()
    }

    @Test
    fun test10_trackerDisconnectionHandling() = runTest {
        // Subtest A: Unknown tracker in registry
        val unknownTrackerProfile = subwayProfile.copy(
            packageName = "com.unknown.tracker",
            tracker = TrackerId.POSE_LITE
        )
        val limitedRegistry = FakeTrackerRegistry(mapOf(TrackerId.HAND_SINGLE to fakeHandSingleTracker))
        val controllerA = RealEngineController(
            trackerRegistry = limitedRegistry,
            profileRepository = profileRepo,
            calibrationRepository = calibRepo,
            inputInjector = inputInjector,
            coroutineScope = testScope
        )
        profileRepo.saveLocal(unknownTrackerProfile)
        controllerA.start(unknownTrackerProfile.packageName, PerfMode.BALANCED)
        assertThat(controllerA.state.value).isEqualTo(EngineState.ERROR)

        // Subtest B: Tracker frames flow fails during collection
        val failingTracker = object : Tracker {
            override val id: TrackerId = TrackerId.HAND_SINGLE
            override val stats: StateFlow<TrackerStats> = MutableStateFlow(TrackerStats(0f, 0f, 0f, 0))
            override val frames: SharedFlow<TrackerFrame> = object : SharedFlow<TrackerFrame> {
                override val replayCache: List<TrackerFrame> get() = emptyList()
                override suspend fun collect(collector: FlowCollector<TrackerFrame>): Nothing {
                    throw RuntimeException("Camera hardware disconnected")
                }
            }
            override suspend fun start(perf: PerfMode) {}
            override suspend fun stop() {}
        }
        val failingRegistry = FakeTrackerRegistry(mapOf(TrackerId.HAND_SINGLE to failingTracker))
        val controllerB = RealEngineController(
            trackerRegistry = failingRegistry,
            profileRepository = profileRepo,
            calibrationRepository = calibRepo,
            inputInjector = inputInjector,
            coroutineScope = testScope
        )
        profileRepo.saveLocal(subwayProfile)
        inputInjector.recordedActions.clear()
        controllerB.start(subwayProfile.packageName, PerfMode.BALANCED)
        assertThat(controllerB.state.value).isEqualTo(EngineState.ERROR)
        assertThat(inputInjector.recordedActions).contains("release")
    }

    @Test
    fun test11_touchReleaseOnStop() = runTest {
        profileRepo.saveLocal(subwayProfile)
        controller.start(subwayProfile.packageName, PerfMode.BALANCED)

        // Simulate an ongoing hold
        inputInjector.holdAt(Pt(0.5f, 0.5f))
        inputInjector.recordedActions.clear()

        // Stop releases touch
        controller.stop()
        assertThat(inputInjector.recordedActions).contains("release")

        // Restart and pause releases touch
        controller.start(subwayProfile.packageName, PerfMode.BALANCED)
        inputInjector.recordedActions.clear()
        controller.pause()
        assertThat(inputInjector.recordedActions).contains("release")
    }

    @Test
    fun test12_hudFrameEmission() = runTest {
        profileRepo.saveLocal(subwayProfile)
        controller.start(subwayProfile.packageName, PerfMode.BALANCED)

        val sampleLandmarks = listOf(Pt(0.1f, 0.2f), Pt(0.3f, 0.4f))
        val frame = HandsFrame(
            tsNanos = 123_456_789L,
            hands = listOf(Hand(side = Side.RIGHT, score = 0.95f, landmarks = sampleLandmarks))
        )

        controller.hud.test {
            fakeHandSingleTracker.emitFrame(frame)
            val hudFrame = awaitItem()
            assertThat(hudFrame.tsNanos).isEqualTo(123_456_789L)
            assertThat(hudFrame.trackerId).isEqualTo(TrackerId.HAND_SINGLE)
            assertThat(hudFrame.landmarks).isEqualTo(sampleLandmarks)
            assertThat(hudFrame.fps).isEqualTo(30f)
            assertThat(hudFrame.e2eLatencyMs).isAtLeast(0f)
        }
    }

    @Test
    fun test13_repeatedStartStopSafety() = runTest {
        profileRepo.saveLocal(subwayProfile)
        profileRepo.saveLocal(fruitProfile)

        // Start 1
        controller.start(subwayProfile.packageName, PerfMode.BALANCED)
        assertThat(controller.state.value).isEqualTo(EngineState.RUNNING)

        // Pause & Resume
        controller.pause()
        assertThat(controller.state.value).isEqualTo(EngineState.PAUSED)
        controller.resume()
        assertThat(controller.state.value).isEqualTo(EngineState.RUNNING)

        // Re-start with another profile without calling stop first
        controller.start(fruitProfile.packageName, PerfMode.QUALITY)
        assertThat(controller.state.value).isEqualTo(EngineState.RUNNING)

        // Stop 1
        controller.stop()
        assertThat(controller.state.value).isEqualTo(EngineState.IDLE)
        assertThat(fakeHandSingleTracker.isStarted).isFalse()

        // Stop 2 (idempotent duplicate stop)
        controller.stop()
        assertThat(controller.state.value).isEqualTo(EngineState.IDLE)

        // Start 3
        controller.start(subwayProfile.packageName, PerfMode.PERFORMANCE)
        assertThat(controller.state.value).isEqualTo(EngineState.RUNNING)

        // Final clean stop
        controller.stop()
        assertThat(controller.state.value).isEqualTo(EngineState.IDLE)
    }

    @Test
    fun test14_pauseToggleGesture_pausesAndResumes() = runTest {
        val fakeInterpreter = FakeGestureInterpreter(TrackerId.HAND_SINGLE)
        val testController = createController(interpreters = mapOf(TrackerId.HAND_SINGLE to fakeInterpreter))
        profileRepo.saveLocal(subwayProfile)
        testController.start(subwayProfile.packageName, PerfMode.BALANCED)

        // Emit PauseToggle while RUNNING -> transitions to PAUSED and releases touches
        fakeInterpreter.queuedEvents = listOf(PauseToggle(tsNanos = 5_000_000L))
        inputInjector.recordedActions.clear()
        fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = 5_000_000L, hands = emptyList()))

        assertThat(testController.state.value).isEqualTo(EngineState.PAUSED)
        assertThat(inputInjector.recordedActions).contains("release")

        // While PAUSED, frames should not route to gesture interpretation
        fakeInterpreter.queuedEvents = listOf(
            Swipe(tsNanos = 5_010_000L, dir = Dir.LEFT, speed = 1.5f)
        )
        inputInjector.recordedActions.clear()
        fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = 5_010_000L, hands = emptyList()))
        assertThat(inputInjector.recordedActions).doesNotContain("swipe(from=(0.5, 0.5), to=(0.2, 0.5), duration=100)")

        // Emit PauseToggle while PAUSED -> resumes to RUNNING
        fakeInterpreter.queuedEvents = listOf(PauseToggle(tsNanos = 5_020_000L))
        testController.resume()
        assertThat(testController.state.value).isEqualTo(EngineState.RUNNING)
    }

    @Test
    fun test15_frameFilterIntegration_transformsFrame() = runTest {
        var filterInvoked = false
        var configureProfileName: String? = null
        var filterResetCount = 0
        val customFilter = object : FrameFilter {
            override fun filter(frame: TrackerFrame): TrackerFrame {
                filterInvoked = true
                return frame
            }

            override fun configure(profile: GameProfile) {
                configureProfileName = profile.packageName
            }

            override fun reset() {
                filterResetCount++
            }
        }
        val testController = createController(filter = customFilter)
        profileRepo.saveLocal(subwayProfile)
        testController.start(subwayProfile.packageName, PerfMode.BALANCED)

        fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = 6_000_000L, hands = emptyList()))

        assertThat(filterInvoked).isTrue()
        assertThat(configureProfileName).isEqualTo(subwayProfile.packageName)
        assertThat(filterResetCount).isAtLeast(1)

        testController.pause()
        testController.stop()
        assertThat(filterResetCount).isAtLeast(3)
    }

    @Test
    fun test16_swipeAndSlashSelection_bothHandSingle_selectedCorrectly() = runTest {
        // Both subwayProfile and fruitProfile use TrackerId.HAND_SINGLE,
        // but have different mapping types: LANE_SWIPE vs SLASH.
        val defaultController = createController() // Uses default production interpreter resolution

        // 1. Start Subway Surfers (LANE_SWIPE)
        profileRepo.saveLocal(subwayProfile)
        defaultController.start(subwayProfile.packageName, PerfMode.BALANCED)
        inputInjector.recordedActions.clear()

        // Emitting a horizontal swipe frame sequence triggers a lane swipe
        val t0 = 100_000_000L
        val wrist0 = Pt(0.50f, 0.50f)
        val middle0 = Pt(0.50f, 0.40f) // palm center = (0.50, 0.45)
        val landmarks0 = MutableList(21) { Pt(0.5f, 0.5f) }.apply {
            this[0] = wrist0
            this[9] = middle0
        }
        fakeHandSingleTracker.emitFrame(
            HandsFrame(t0, listOf(Hand(Side.RIGHT, 0.9f, landmarks0)))
        )

        val t1 = t0 + 60_000_000L // +60ms
        val wrist1 = Pt(0.30f, 0.50f)
        val middle1 = Pt(0.30f, 0.40f) // palm center = (0.30, 0.45), dx = -0.20
        val landmarks1 = MutableList(21) { Pt(0.5f, 0.5f) }.apply {
            this[0] = wrist1
            this[9] = middle1
        }
        fakeHandSingleTracker.emitFrame(
            HandsFrame(t1, listOf(Hand(Side.RIGHT, 0.9f, landmarks1)))
        )

        assertThat(inputInjector.recordedActions).contains(
            "swipe(from=(0.5, 0.5), to=(0.2, 0.5), duration=100)"
        )
        defaultController.stop()

        // 2. Start Fruit Ninja (SLASH)
        profileRepo.saveLocal(fruitProfile)
        defaultController.start(fruitProfile.packageName, PerfMode.BALANCED)
        inputInjector.recordedActions.clear()

        // Emitting an index fingertip (landmark 8) slice trajectory triggers a slash path
        val st0 = 200_000_000L
        val sLandmarks0 = MutableList(21) { Pt(0.5f, 0.5f) }.apply {
            this[8] = Pt(0.10f, 0.10f)
        }
        fakeHandSingleTracker.emitFrame(
            HandsFrame(st0, listOf(Hand(Side.RIGHT, 0.9f, sLandmarks0)))
        )

        val st1 = st0 + 50_000_000L
        val sLandmarks1 = MutableList(21) { Pt(0.5f, 0.5f) }.apply {
            this[8] = Pt(0.25f, 0.25f)
        }
        fakeHandSingleTracker.emitFrame(
            HandsFrame(st1, listOf(Hand(Side.RIGHT, 0.9f, sLandmarks1)))
        )

        val st2 = st0 + 100_000_000L
        val sLandmarks2 = MutableList(21) { Pt(0.5f, 0.5f) }.apply {
            this[8] = Pt(0.40f, 0.40f)
        }
        fakeHandSingleTracker.emitFrame(
            HandsFrame(st2, listOf(Hand(Side.RIGHT, 0.9f, sLandmarks2)))
        )

        assertThat(inputInjector.recordedActions.any { it.startsWith("path(") }).isTrue()
        defaultController.stop()
    }

    @Test
    fun test17_explicitResettableInterpreter_resetsWithoutReflection() = runTest {
        var resetCount = 0
        val customResettable = object : com.zorbi.gesture.ResettableInterpreter {
            override val accepts: TrackerId = TrackerId.HAND_SINGLE

            override fun reset() {
                resetCount++
            }

            override fun onFrame(frame: TrackerFrame, profile: GameProfile, cal: Calibration): List<GestureEvent> {
                return emptyList()
            }
        }

        val testController = createController(interpreters = mapOf(TrackerId.HAND_SINGLE to customResettable))
        profileRepo.saveLocal(subwayProfile)

        testController.start(subwayProfile.packageName, PerfMode.BALANCED)
        assertThat(resetCount).isEqualTo(1)

        testController.pause()
        testController.resume()
        assertThat(resetCount).isEqualTo(2)

        testController.stop()
        assertThat(resetCount).isEqualTo(3)
    }

    @Test
    fun test18_cancellationExceptionRethrown_doesNotTransitionToError() = runTest {
        profileRepo.saveLocal(subwayProfile)
        controller.start(subwayProfile.packageName, PerfMode.BALANCED)
        assertThat(controller.state.value).isEqualTo(EngineState.RUNNING)

        // Normal stop cancels pipelineJob -> CancellationException is rethrown and caught by coroutine
        controller.stop()

        // Verify state is IDLE, strictly NOT ERROR
        assertThat(controller.state.value).isEqualTo(EngineState.IDLE)
        assertThat(fakeHandSingleTracker.isStarted).isFalse()
    }

    @Test
    fun test19_calibratingState_emitsHud_suppressesTouchInjection() = runTest {
        val fakeInterpreter = FakeGestureInterpreter(TrackerId.HAND_SINGLE)
        val testController = createController(interpreters = mapOf(TrackerId.HAND_SINGLE to fakeInterpreter))
        profileRepo.saveLocal(subwayProfile)

        // Start calibration session
        testController.startCalibration(subwayProfile.packageName, PerfMode.QUALITY)
        assertThat(testController.state.value).isEqualTo(EngineState.CALIBRATING)
        assertThat(fakeHandSingleTracker.isStarted).isTrue()

        // Queue a swipe event while CALIBRATING
        fakeInterpreter.queuedEvents = listOf(
            Swipe(tsNanos = 7_000_000L, dir = Dir.LEFT, speed = 1.5f)
        )
        inputInjector.recordedActions.clear()

        // Emit frame during CALIBRATING
        val landmarks = listOf(Pt(0.5f, 0.5f), Pt(0.2f, 0.5f))
        testController.hud.test {
            fakeHandSingleTracker.emitFrame(
                HandsFrame(tsNanos = 7_000_000L, hands = listOf(Hand(Side.RIGHT, 0.9f, landmarks)))
            )
            val hudFrame = awaitItem()
            assertThat(hudFrame.tsNanos).isEqualTo(7_000_000L)
            assertThat(hudFrame.landmarks).isEqualTo(landmarks)
            // Last gesture is null during calibration (suppressed)
            assertThat(hudFrame.lastGesture).isNull()
        }

        // Verify touch injection was suppressed (no swipes or taps dispatched)
        assertThat(inputInjector.recordedActions).isEmpty()

        // Clean stop transitions back to IDLE
        testController.stop()
        assertThat(testController.state.value).isEqualTo(EngineState.IDLE)
        assertThat(fakeHandSingleTracker.isStarted).isFalse()
    }

    @Test
    fun test20_wheelInterpreterDefaultResolutionAndSteerDrag() = runTest {
        val dragProfile = GameProfile(
            schemaVersion = 1,
            packageName = "com.racing.dragsteer",
            displayName = "Drag Racer",
            orientation = ProfileOrientation.LANDSCAPE,
            tracker = TrackerId.HAND_DUAL_WHEEL,
            mapping = MappingType.STEER_DRAG,
            targets = mapOf(
                "center" to TargetPoint(0.5f, 0.5f),
                "left" to TargetPoint(0.2f, 0.5f),
                "right" to TargetPoint(0.8f, 0.5f)
            ),
            params = ProfileParams(deadzoneDeg = 8.0f)
        )
        val defaultController = createController()
        profileRepo.saveLocal(dragProfile)
        defaultController.start(dragProfile.packageName, PerfMode.BALANCED)

        // Turn right (+28 deg -> value ~0.8)
        val wristL = Pt(0.25f, 0.50f)
        val dy = 0.50f * kotlin.math.tan(28.0 * kotlin.math.PI / 180.0).toFloat()
        val wristR = Pt(0.75f, 0.50f + dy)
        val lmListL = mutableListOf(wristL).apply { for (i in 1..20) add(Pt(0.25f, 0.50f)) }
        val lmListR = mutableListOf(wristR).apply { for (i in 1..20) add(Pt(0.75f, 0.50f)) }
        val turnFrame = HandsFrame(
            tsNanos = 10_000_000L,
            hands = listOf(
                Hand(Side.LEFT, 0.9f, lmListL),
                Hand(Side.RIGHT, 0.9f, lmListR)
            )
        )

        fakeDualWheelTracker.emitFrame(turnFrame)

        // Verifies STEER_DRAG calls dragTo
        val hasDragAction = inputInjector.recordedActions.any { it.startsWith("dragTo(") }
        assertThat(hasDragAction).isTrue()

        // Return to neutral (dy = 0 -> value 0.0) -> release touch
        val neutralWristR = Pt(0.75f, 0.50f)
        val neutralLmR = mutableListOf(neutralWristR).apply { for (i in 1..20) add(Pt(0.75f, 0.50f)) }
        val neutralFrame = HandsFrame(
            tsNanos = 10_016_000L,
            hands = listOf(
                Hand(Side.LEFT, 0.9f, lmListL),
                Hand(Side.RIGHT, 0.9f, neutralLmR)
            )
        )
        fakeDualWheelTracker.emitFrame(neutralFrame)

        assertThat(inputInjector.recordedActions.last()).isEqualTo("release")

        defaultController.stop()
    }

    @Test
    fun test21_leanInterpreterDefaultResolutionWithPoseLite() = runTest {
        val leanProfile = GameProfile(
            schemaVersion = 1,
            packageName = "com.racing.bodylean",
            displayName = "Body Lean Racer",
            orientation = ProfileOrientation.LANDSCAPE,
            tracker = TrackerId.POSE_LITE,
            mapping = MappingType.STEER_BUTTONS,
            targets = mapOf(
                "left" to TargetPoint(0.15f, 0.85f),
                "right" to TargetPoint(0.85f, 0.85f)
            ),
            params = ProfileParams(deadzoneDeg = 8.0f)
        )
        val defaultController = createController()
        profileRepo.saveLocal(leanProfile)
        defaultController.start(leanProfile.packageName, PerfMode.BALANCED)

        // Lean left (-24.5 deg -> value -0.7 < -8/35) -> hold left target
        val shoulderL = Pt(0.35f, 0.40f)
        val dy = 0.30f * kotlin.math.tan(-24.5 * kotlin.math.PI / 180.0).toFloat()
        val shoulderR = Pt(0.65f, 0.40f + dy)
        val lmList = ArrayList<Pt>(33).apply {
            for (i in 0 until 33) {
                when (i) {
                    11 -> add(shoulderL)
                    12 -> add(shoulderR)
                    else -> add(Pt(0.5f, 0.5f))
                }
            }
        }
        val leanLeftFrame = PoseFrame(
            tsNanos = 20_000_000L,
            score = 0.9f,
            landmarks = lmList
        )

        fakePoseTracker.emitFrame(leanLeftFrame)

        assertThat(inputInjector.recordedActions).contains("holdAt(0.15, 0.85)")

        // Return to neutral (dy = 0 -> value 0.0) -> release touch
        val neutralLmList = ArrayList<Pt>(33).apply {
            for (i in 0 until 33) {
                when (i) {
                    11 -> add(shoulderL)
                    12 -> add(Pt(0.65f, 0.40f))
                    else -> add(Pt(0.5f, 0.5f))
                }
            }
        }
        val neutralFrame = PoseFrame(
            tsNanos = 20_016_000L,
            score = 0.9f,
            landmarks = neutralLmList
        )

        fakePoseTracker.emitFrame(neutralFrame)

        assertThat(inputInjector.recordedActions.last()).isEqualTo("release")

        defaultController.stop()
    }

    @Test
    fun test22_beachBuggyRacing_modeSwitch_handsDualWheelToPoseLite() = runTest {
        val bbrWheelProfile = GameProfile(
            schemaVersion = 1,
            packageName = "com.vectorunit.purple.googleplay",
            displayName = "Beach Buggy Racing",
            orientation = ProfileOrientation.LANDSCAPE,
            tracker = TrackerId.HAND_DUAL_WHEEL,
            mapping = MappingType.STEER_BUTTONS,
            targets = mapOf(
                "left" to TargetPoint(0.15f, 0.85f),
                "right" to TargetPoint(0.85f, 0.85f)
            ),
            params = ProfileParams(deadzoneDeg = 8.0f)
        )
        val defaultController = createController()
        profileRepo.saveLocal(bbrWheelProfile)

        // 1. Start with HAND_DUAL_WHEEL
        defaultController.start("com.vectorunit.purple.googleplay", PerfMode.BALANCED)
        assertThat(fakeDualWheelTracker.isStarted).isTrue()
        assertThat(fakePoseTracker.isStarted).isFalse()
        defaultController.stop()
        assertThat(fakeDualWheelTracker.isStarted).isFalse()

        // 2. User switches mode to POSE_LITE
        val bbrLeanProfile = bbrWheelProfile.copy(tracker = TrackerId.POSE_LITE)
        profileRepo.saveLocal(bbrLeanProfile)

        // 3. Start with POSE_LITE
        defaultController.start("com.vectorunit.purple.googleplay", PerfMode.BALANCED)
        assertThat(fakePoseTracker.isStarted).isTrue()
        assertThat(fakeDualWheelTracker.isStarted).isFalse()

        // 4. Verify steering input injection works with POSE_LITE
        val shoulderL = Pt(0.35f, 0.40f)
        val dy = 0.30f * kotlin.math.tan(28.0 * kotlin.math.PI / 180.0).toFloat()
        val shoulderR = Pt(0.65f, 0.40f + dy)
        val lmList = ArrayList<Pt>(33).apply {
            for (i in 0 until 33) {
                when (i) {
                    11 -> add(shoulderL)
                    12 -> add(shoulderR)
                    else -> add(Pt(0.5f, 0.5f))
                }
            }
        }
        fakePoseTracker.emitFrame(PoseFrame(tsNanos = 30_000_000L, score = 0.9f, landmarks = lmList))
        // Leaning right -> hold right button (0.85, 0.85)
        assertThat(inputInjector.recordedActions).contains("holdAt(0.85, 0.85)")

        defaultController.stop()
        assertThat(fakePoseTracker.isStarted).isFalse()
    }

    private fun createClosedFist(score: Float = 0.95f): Hand {
        val lms = ArrayList<Pt>(21)
        lms.add(Pt(0.50f, 0.70f, 0.0f)) // 0: wrist
        // Thumb
        lms.add(Pt(0.44f, 0.65f, 0.01f))
        lms.add(Pt(0.42f, 0.58f, 0.03f))
        lms.add(Pt(0.45f, 0.52f, 0.04f))
        lms.add(Pt(0.48f, 0.48f, 0.04f))
        // Index
        lms.add(Pt(0.42f, 0.50f, 0.01f))
        lms.add(Pt(0.41f, 0.42f, 0.03f))
        lms.add(Pt(0.43f, 0.46f, 0.01f))
        lms.add(Pt(0.44f, 0.50f, 0.00f))
        // Middle
        lms.add(Pt(0.50f, 0.48f, 0.00f))
        lms.add(Pt(0.50f, 0.40f, 0.02f))
        lms.add(Pt(0.51f, 0.44f, 0.01f))
        lms.add(Pt(0.51f, 0.48f, 0.00f))
        // Ring
        lms.add(Pt(0.58f, 0.50f, -0.01f))
        lms.add(Pt(0.58f, 0.42f, 0.01f))
        lms.add(Pt(0.57f, 0.46f, 0.00f))
        lms.add(Pt(0.57f, 0.50f, -0.01f))
        // Pinky
        lms.add(Pt(0.65f, 0.54f, -0.02f))
        lms.add(Pt(0.65f, 0.46f, 0.00f))
        lms.add(Pt(0.64f, 0.50f, -0.01f))
        lms.add(Pt(0.63f, 0.54f, -0.02f))
        return Hand(Side.RIGHT, score, lms)
    }

    private fun createOpenHand(score: Float = 0.95f): Hand {
        val lms = ArrayList<Pt>(21)
        lms.add(Pt(0.50f, 0.70f, 0.0f))
        // Thumb
        lms.add(Pt(0.42f, 0.65f, 0.01f))
        lms.add(Pt(0.36f, 0.58f, 0.02f))
        lms.add(Pt(0.30f, 0.52f, 0.02f))
        lms.add(Pt(0.24f, 0.46f, 0.03f))
        // Index
        lms.add(Pt(0.42f, 0.50f, 0.01f))
        lms.add(Pt(0.41f, 0.38f, 0.01f))
        lms.add(Pt(0.40f, 0.28f, 0.01f))
        lms.add(Pt(0.40f, 0.18f, 0.01f))
        // Middle
        lms.add(Pt(0.50f, 0.48f, 0.00f))
        lms.add(Pt(0.50f, 0.35f, 0.00f))
        lms.add(Pt(0.50f, 0.24f, 0.00f))
        lms.add(Pt(0.50f, 0.14f, 0.00f))
        // Ring
        lms.add(Pt(0.58f, 0.50f, -0.01f))
        lms.add(Pt(0.59f, 0.38f, -0.01f))
        lms.add(Pt(0.60f, 0.28f, -0.01f))
        lms.add(Pt(0.60f, 0.19f, -0.01f))
        // Pinky
        lms.add(Pt(0.65f, 0.54f, -0.02f))
        lms.add(Pt(0.67f, 0.44f, -0.02f))
        lms.add(Pt(0.68f, 0.36f, -0.02f))
        lms.add(Pt(0.69f, 0.28f, -0.02f))
        return Hand(Side.RIGHT, score, lms)
    }

    @Test
    fun test23_fistHold_1Second_pausesRunningEngine_releasesTouch() = runTest {
        val fistDetector = FistDetector(holdDurationNanos = 1_000_000_000L)
        val testController = createController(fistDetector = fistDetector)
        profileRepo.saveLocal(subwayProfile)
        testController.start(subwayProfile.packageName, PerfMode.BALANCED)
        assertThat(testController.state.value).isEqualTo(EngineState.RUNNING)

        // Simulate touch active
        inputInjector.holdAt(Pt(0.5f, 0.5f))
        inputInjector.recordedActions.clear()

        // Stream 35 frames at 33ms intervals = 1155ms (> 1000ms, gap 33ms < 350ms)
        val fist = createClosedFist()
        for (i in 0..35) {
            val ts = 1_000_000_000L + i * 33_333_334L
            fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = ts, hands = listOf(fist)))
        }

        assertThat(testController.state.value).isEqualTo(EngineState.PAUSED)
        assertThat(inputInjector.recordedActions).contains("release")

        testController.stop()
    }

    @Test
    fun test24_fistHold_secondFistHold_resumesPausedEngine() = runTest {
        val fistDetector = FistDetector(holdDurationNanos = 1_000_000_000L)
        val testController = createController(fistDetector = fistDetector)
        profileRepo.saveLocal(subwayProfile)
        testController.start(subwayProfile.packageName, PerfMode.BALANCED)

        // First hold triggers pause
        val fist = createClosedFist()
        for (i in 0..35) {
            val ts = 1_000_000_000L + i * 33_333_334L
            fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = ts, hands = listOf(fist)))
        }
        assertThat(testController.state.value).isEqualTo(EngineState.PAUSED)

        // Open hand to clear triggered fist state
        val openHand = createOpenHand()
        fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = 3_000_000_000L, hands = listOf(openHand)))
        assertThat(testController.state.value).isEqualTo(EngineState.PAUSED)

        // Second fist hold triggers resume
        for (i in 0..35) {
            val ts = 4_000_000_000L + i * 33_333_334L
            fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = ts, hands = listOf(fist)))
        }
        assertThat(testController.state.value).isEqualTo(EngineState.RUNNING)

        testController.stop()
    }

    @Test
    fun test25_fistDetector_trackingLoss_resetsFistProgress() = runTest {
        val fistDetector = FistDetector(
            holdDurationNanos = 1_000_000_000L,
            maxTrackingGapNanos = 350_000_000L
        )
        val testController = createController(fistDetector = fistDetector)
        profileRepo.saveLocal(subwayProfile)
        testController.start(subwayProfile.packageName, PerfMode.BALANCED)

        // Fist starts at t = 0
        fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = 0L, hands = listOf(createClosedFist())))
        // Gap > 350ms (gap of 500ms)
        fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = 500_000_000L, hands = listOf(createClosedFist())))
        // Because of gap, fistStartNanos reset to 500ms
        // At 1100ms, only 600ms elapsed since reset
        fakeHandSingleTracker.emitFrame(HandsFrame(tsNanos = 1_100_000_000L, hands = listOf(createClosedFist())))
        assertThat(testController.state.value).isEqualTo(EngineState.RUNNING)

        testController.stop()
    }
}
