package com.zorbi.data.model

import com.zorbi.core.GameProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO matching `public.game_profiles` table schema (R1, R2, R3).
 *
 * Database columns:
 * - id: uuid primary key default gen_random_uuid()
 * - package_name: text not null
 * - display_name: text not null
 * - tracker: text not null check (tracker in ('HAND_SINGLE','HAND_DUAL_WHEEL','POSE_LITE'))
 * - mapping: text not null check (mapping in ('STEER_BUTTONS','STEER_DRAG','LANE_SWIPE','SLASH'))
 * - profile_json: jsonb not null (serialized [GameProfile])
 * - schema_version: int not null default 1
 * - author: text not null default 'zorbi-team'
 * - approved: boolean not null default false
 * - downloads: int not null default 0
 * - is_mock: boolean not null default false
 * - created_at: timestamptz not null default now()
 */
@Serializable
data class GameProfileRowDto(
    @SerialName("id")
    val id: String? = null,

    @SerialName("package_name")
    val packageName: String,

    @SerialName("display_name")
    val displayName: String,

    @SerialName("tracker")
    val tracker: String,

    @SerialName("mapping")
    val mapping: String,

    @SerialName("profile_json")
    val profileJson: GameProfile,

    @SerialName("schema_version")
    val schemaVersion: Int = 1,

    @SerialName("author")
    val author: String = "zorbi-team",

    @SerialName("approved")
    val approved: Boolean = false,

    @SerialName("downloads")
    val downloads: Int = 0,

    @SerialName("is_mock")
    val isMock: Boolean = false,

    @SerialName("created_at")
    val createdAt: String? = null
)

/**
 * DTO matching `public.increment_profile_downloads` RPC parameter (R4).
 * POST /rest/v1/rpc/increment_profile_downloads
 * Body: { "profile_id": "<uuid>" }
 */
@Serializable
data class ProfileDownloadRpcDto(
    @SerialName("profile_id")
    val profileId: String
)

/**
 * DTO matching `public.sessions` table schema (R5).
 *
 * Database columns:
 * - id: uuid primary key default gen_random_uuid()
 * - device_id: uuid not null
 * - device_model: text
 * - android_version: int
 * - game_package: text
 * - tracker: text not null
 * - perf_mode: text not null
 * - avg_fps: real
 * - p95_latency_ms: real
 * - duration_s: int
 * - gestures_count: int
 * - is_mock: boolean not null default false
 * - created_at: timestamptz not null default now()
 */
@Serializable
data class SessionRowDto(
    @SerialName("id")
    val id: String? = null,

    @SerialName("device_id")
    val deviceId: String,

    @SerialName("device_model")
    val deviceModel: String? = null,

    @SerialName("android_version")
    val androidVersion: Int? = null,

    @SerialName("game_package")
    val gamePackage: String? = null,

    @SerialName("tracker")
    val tracker: String,

    @SerialName("perf_mode")
    val perfMode: String,

    @SerialName("avg_fps")
    val avgFps: Float? = null,

    @SerialName("p95_latency_ms")
    val p95LatencyMs: Float? = null,

    @SerialName("duration_s")
    val durationS: Int? = null,

    @SerialName("gestures_count")
    val gesturesCount: Int? = null,

    @SerialName("is_mock")
    val isMock: Boolean = false,

    @SerialName("created_at")
    val createdAt: String? = null
)

/**
 * DTO matching `public.gesture_events` table schema (R6).
 *
 * Database columns:
 * - id: bigint generated always as identity primary key
 * - session_id: uuid not null references public.sessions(id) on delete cascade
 * - event_type: text not null check (event_type in ('STEER','SWIPE','SLASH','PAUSE'))
 * - magnitude: real
 * - is_mock: boolean not null default false
 * - created_at: timestamptz not null default now()
 */
@Serializable
data class GestureEventRowDto(
    @SerialName("id")
    val id: Long? = null,

    @SerialName("session_id")
    val sessionId: String,

    @SerialName("event_type")
    val eventType: String,

    @SerialName("magnitude")
    val magnitude: Float? = null,

    @SerialName("is_mock")
    val isMock: Boolean = false,

    @SerialName("created_at")
    val createdAt: String? = null
)

