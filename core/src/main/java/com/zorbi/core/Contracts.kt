package com.zorbi.core

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

// ---- Conventions (FROZEN) ----
// * Time: nanoseconds, monotonic (SystemClock.elapsedRealtimeNanos domain).
// * Image-space points: x,y normalised 0..1, origin top-left, ALREADY rotated upright and
// MIRRORED for the front camera (user's right hand appears on the right of the frame).
// * Screen-space points: x,y normalised 0..1 of the CURRENT display orientation, origin top-left.
// * Angles: degrees, positive = clockwise from the user's view (turn right). Steer value -1..+1.

data class Pt(val x: Float, val y: Float, val z: Float = 0f)

enum class TrackerId { HAND_SINGLE, HAND_DUAL_WHEEL, POSE_LITE }

enum class PerfMode { QUALITY, BALANCED, PERFORMANCE } // PERFORMANCE: lower res, ~15 fps cap

enum class Side { LEFT, RIGHT, UNKNOWN }

sealed interface TrackerFrame { val tsNanos: Long }

data class Hand(val side: Side, val score: Float, val landmarks: List<Pt>) // 21 pts

data class HandsFrame(override val tsNanos: Long, val hands: List<Hand>) : TrackerFrame

data class PoseFrame(override val tsNanos: Long, val score: Float, val landmarks: List<Pt>) : TrackerFrame // 33 pts

data class TrackerStats(val fps: Float, val inferenceMsP50: Float, val inferenceMsP95: Float, val dropped: Int)

interface Tracker { // implemented by :vision (Person 1)
    val id: TrackerId
    val frames: SharedFlow<TrackerFrame>
    val stats: StateFlow<TrackerStats>
    suspend fun start(perf: PerfMode)
    suspend fun stop()
}

interface TrackerRegistry { fun get(id: TrackerId): Tracker }

enum class Dir { LEFT, RIGHT, UP, DOWN }

sealed interface GestureEvent { val tsNanos: Long }

data class Steer(override val tsNanos: Long, val value: Float, val angleDeg: Float) : GestureEvent

data class Swipe(override val tsNanos: Long, val dir: Dir, val speed: Float) : GestureEvent

data class Slash(override val tsNanos: Long, val path: List<Pt>) : GestureEvent // screen-space path

data class PauseToggle(override val tsNanos: Long) : GestureEvent // closed fist ~1 s

data class Calibration(
    val neutralAngleDeg: Float, // angle treated as "straight"
    val maxAngleDeg: Float, // angle treated as full lock (|value|=1)
    val reachScaleX: Float, // user's comfortable horizontal reach (image-space span)
    val reachScaleY: Float,
    val dominantHand: Side,
    val createdAtEpochMs: Long
)

interface GestureInterpreter { // implemented by :gesture (Person 2)
    val accepts: TrackerId
    fun onFrame(frame: TrackerFrame, profile: GameProfile, cal: Calibration): List<GestureEvent>
}

interface InputInjector { // implemented by :input (Person 2)
    val isReady: StateFlow<Boolean> // accessibility service connected
    fun tap(p: Pt)
    fun swipe(from: Pt, to: Pt, durationMs: Long)
    fun path(points: List<Pt>, durationMs: Long) // slash
    fun holdAt(p: Pt) // press and keep pressed
    fun dragTo(p: Pt) // move while holding (continued stroke)
    fun release()
}

enum class EngineState { IDLE, CALIBRATING, RUNNING, PAUSED, ERROR }

data class HudFrame( // what the overlay/UI draws; produced by engine
    val tsNanos: Long,
    val trackerId: TrackerId,
    val landmarks: List<Pt>,
    val lastGesture: GestureEvent?,
    val fps: Float,
    val e2eLatencyMs: Float
)

interface EngineController { // implemented by :engine (Person 2)
    val state: StateFlow<EngineState>
    val hud: SharedFlow<HudFrame>
    suspend fun start(packageName: String, perf: PerfMode)
    fun pause()
    fun resume()
    suspend fun stop()
}

interface CalibrationRepository { // implemented by :calib (Person 1)
    suspend fun load(packageName: String): Calibration?
    suspend fun save(packageName: String, c: Calibration)
    fun computeFromSamples(trackerId: TrackerId, samples: List<TrackerFrame>): Calibration // pure, unit-tested
}

interface ProfileRepository { // implemented by :engine (Person 2); remote source by :data (Person 4)
    suspend fun get(packageName: String): GameProfile?
    suspend fun list(): List<GameProfile>
    suspend fun importFromCloud(id: String): GameProfile?
    suspend fun saveLocal(p: GameProfile)
}

interface TelemetrySink { // implemented by :data (Person 4); no-op when opted out/offline
    fun session(started: Long, p: GameProfile, stats: TrackerStats, durationS: Int, gestures: Int, perf: PerfMode)
    fun gestureEvent(sessionId: String, type: String, magnitude: Float) // batched, <=5/s
}

interface AppGraph { // implemented by RealAppGraph (:app) and FakeAppGraph (:fakes)
    val trackers: TrackerRegistry
    val engine: EngineController
    val profiles: ProfileRepository
    val calibration: CalibrationRepository
    val telemetry: TelemetrySink
    val injector: InputInjector
}

