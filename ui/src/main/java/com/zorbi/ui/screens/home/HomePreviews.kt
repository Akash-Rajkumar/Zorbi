package com.zorbi.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zorbi.core.AppGraph
import com.zorbi.core.Calibration
import com.zorbi.core.CalibrationRepository
import com.zorbi.core.EngineController
import com.zorbi.core.EngineState
import com.zorbi.core.GameProfile
import com.zorbi.core.HudFrame
import com.zorbi.core.InputInjector
import com.zorbi.core.MappingType
import com.zorbi.core.PerfMode
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.ProfileRepository
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.core.TelemetrySink
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.core.TrackerRegistry
import com.zorbi.core.TrackerStats
import com.zorbi.ui.theme.ZorbiTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Preview Fake AppGraph for UI previews and local testing without hardware.
 */
class PreviewAppGraph(
    initialProfiles: List<GameProfile> = sampleDemoProfiles()
) : AppGraph {
    override val trackers = object : TrackerRegistry {
        override fun get(id: TrackerId): Tracker = throw NotImplementedError()
    }
    override val engine = object : EngineController {
        override val state: StateFlow<EngineState> = MutableStateFlow(EngineState.IDLE)
        override val hud: SharedFlow<HudFrame> = MutableSharedFlow()
        override suspend fun start(packageName: String, perf: PerfMode) {}
        override fun pause() {}
        override fun resume() {}
        override suspend fun stop() {}
    }
    override val profiles = object : ProfileRepository {
        private val list = initialProfiles.toMutableList()
        override suspend fun get(packageName: String): GameProfile? = list.find { it.packageName == packageName }
        override suspend fun list(): List<GameProfile> = list
        override suspend fun importFromCloud(id: String): GameProfile? = null
        override suspend fun saveLocal(p: GameProfile) {
            list.removeAll { it.packageName == p.packageName }
            list.add(p)
        }
    }
    override val calibration = object : CalibrationRepository {
        override suspend fun load(packageName: String): Calibration? = null
        override suspend fun save(packageName: String, c: Calibration) {}
        override fun computeFromSamples(trackerId: TrackerId, samples: List<TrackerFrame>): Calibration =
            Calibration(0f, 45f, 0.5f, 0.5f, Side.RIGHT, System.currentTimeMillis())
    }
    override val telemetry = object : TelemetrySink {
        override fun session(started: Long, p: GameProfile, stats: TrackerStats, durationS: Int, gestures: Int, perf: PerfMode) {}
        override fun gestureEvent(sessionId: String, type: String, magnitude: Float) {}
    }
    override val injector = object : InputInjector {
        override val isReady: StateFlow<Boolean> = MutableStateFlow(true)
        override fun tap(p: Pt) {}
        override fun swipe(from: Pt, to: Pt, durationMs: Long) {}
        override fun path(points: List<Pt>, durationMs: Long) {}
        override fun holdAt(p: Pt) {}
        override fun dragTo(p: Pt) {}
        override fun release() {}
    }
}

fun sampleDemoProfiles(): List<GameProfile> = listOf(
    GameProfile(
        packageName = "com.kiloo.subwaysurf",
        displayName = "Subway Surfers",
        orientation = ProfileOrientation.PORTRAIT,
        tracker = TrackerId.HAND_SINGLE,
        mapping = MappingType.LANE_SWIPE,
        params = ProfileParams(cooldownMs = 350L, swipeMinSpeed = 1.2f)
    ),
    GameProfile(
        packageName = "com.halfbrick.fruitninja",
        displayName = "Fruit Ninja",
        orientation = ProfileOrientation.LANDSCAPE,
        tracker = TrackerId.HAND_SINGLE,
        mapping = MappingType.SLASH,
        params = ProfileParams(slashMinLength = 0.15f)
    ),
    GameProfile(
        packageName = "com.gameloft.android.ANMP.GloftA9HM",
        displayName = "Asphalt 9: Legends",
        orientation = ProfileOrientation.LANDSCAPE,
        tracker = TrackerId.HAND_DUAL_WHEEL,
        mapping = MappingType.STEER_BUTTONS,
        params = ProfileParams(deadzoneDeg = 8.0f)
    )
)

@Preview(name = "Home Screen - Phone (360dp)", widthDp = 360, showBackground = true)
@Composable
fun PreviewHomeScreenPhone() {
    ZorbiTheme {
        HomeScreen(
            appGraph = PreviewAppGraph(),
            onNavigateToLibrary = {},
            onNavigateToWizard = {},
            onNavigateToCalibrate = {},
            onNavigateToPlay = {},
            onNavigateToCommunity = {},
            onNavigateToSettings = {},
            onNavigateToAbout = {}
        )
    }
}

@Preview(name = "Home Screen - Tablet (720dp)", widthDp = 720, showBackground = true)
@Composable
fun PreviewHomeScreenTablet() {
    ZorbiTheme {
        HomeScreen(
            appGraph = PreviewAppGraph(),
            onNavigateToLibrary = {},
            onNavigateToWizard = {},
            onNavigateToCalibrate = {},
            onNavigateToPlay = {},
            onNavigateToCommunity = {},
            onNavigateToSettings = {},
            onNavigateToAbout = {}
        )
    }
}

@Preview(name = "Home Screen - Right Drawer Open", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun PreviewHomeRightDrawer() {
    ZorbiTheme {
        com.zorbi.ui.components.ZorbiRightDrawer(
            isOpen = true,
            onDismissRequest = {},
            onNavigateToLibrary = {},
            onNavigateToWizard = {},
            onNavigateToCalibrate = {},
            onNavigateToCommunity = {},
            onNavigateToSettings = {},
            onNavigateToAbout = {}
        )
    }
}
