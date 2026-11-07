package com.zorbi.core

import kotlinx.serialization.Serializable

// ---- GameProfile JSON (schemaVersion 1, FROZEN per Section 5.2) ----

@Serializable
enum class ProfileOrientation { PORTRAIT, LANDSCAPE }

@Serializable
enum class MappingType { STEER_BUTTONS, STEER_DRAG, LANE_SWIPE, SLASH }

@Serializable
data class TargetPoint(val x: Float, val y: Float)

@Serializable
data class ProfileSmoothing(val minCutoff: Float = 1.0f, val beta: Float = 0.01f)

@Serializable
data class ProfileParams(
    val deadzoneDeg: Float = 8.0f,
    val cooldownMs: Long = 350L,
    val swipeMinSpeed: Float = 1.2f,
    val slashMinLength: Float = 0.15f,
    val smoothing: ProfileSmoothing = ProfileSmoothing()
)

@Serializable
data class GameProfile(
    val schemaVersion: Int = 1,
    val packageName: String,
    val displayName: String,
    val orientation: ProfileOrientation,
    val tracker: TrackerId,
    val mapping: MappingType,
    val targets: Map<String, TargetPoint> = emptyMap(),
    val params: ProfileParams = ProfileParams()
)

