package com.zorbi.data

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.ProfileSmoothing
import com.zorbi.core.TargetPoint
import com.zorbi.core.TrackerId
import com.zorbi.data.model.GameProfileRowDto
import com.zorbi.data.model.GestureEventRowDto
import com.zorbi.data.model.ProfileDownloadRpcDto
import com.zorbi.data.model.SessionRowDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Test

class SupabaseDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun testGameProfileRowDtoRoundTrip() {
        val gameProfile = GameProfile(
            schemaVersion = 1,
            packageName = "com.kiloo.subwaysurf",
            displayName = "Subway Surfers",
            orientation = ProfileOrientation.PORTRAIT,
            tracker = TrackerId.HAND_SINGLE,
            mapping = MappingType.LANE_SWIPE,
            targets = mapOf(
                "left" to TargetPoint(0.20f, 0.80f),
                "right" to TargetPoint(0.80f, 0.80f),
                "center" to TargetPoint(0.50f, 0.50f)
            ),
            params = ProfileParams(
                deadzoneDeg = 8.0f,
                cooldownMs = 350L,
                swipeMinSpeed = 1.2f,
                slashMinLength = 0.15f,
                smoothing = ProfileSmoothing(minCutoff = 1.0f, beta = 0.01f)
            )
        )

        val dto = GameProfileRowDto(
            id = "10000000-0000-0000-0000-000000000001",
            packageName = "com.kiloo.subwaysurf",
            displayName = "Subway Surfers",
            tracker = "HAND_SINGLE",
            mapping = "LANE_SWIPE",
            profileJson = gameProfile,
            schemaVersion = 1,
            author = "zorbi-team",
            approved = true,
            downloads = 142,
            isMock = false
        )

        val encoded = json.encodeToString(GameProfileRowDto.serializer(), dto)
        assertThat(encoded).contains("\"package_name\":\"com.kiloo.subwaysurf\"")
        assertThat(encoded).contains("\"display_name\":\"Subway Surfers\"")
        assertThat(encoded).contains("\"approved\":true")
        assertThat(encoded).contains("\"downloads\":142")

        val decoded = json.decodeFromString(GameProfileRowDto.serializer(), encoded)
        assertThat(decoded.id).isEqualTo("10000000-0000-0000-0000-000000000001")
        assertThat(decoded.packageName).isEqualTo("com.kiloo.subwaysurf")
        assertThat(decoded.profileJson.displayName).isEqualTo("Subway Surfers")
        assertThat(decoded.profileJson.targets["left"]?.x).isEqualTo(0.20f)
    }

    @Test
    fun testGameProfileRowDtoFromSeedJson() {
        val seedJson = """
            {
                "id": "10000000-0000-0000-0000-000000000001",
                "package_name": "com.kiloo.subwaysurf",
                "display_name": "Subway Surfers",
                "tracker": "HAND_SINGLE",
                "mapping": "LANE_SWIPE",
                "profile_json": {
                    "schemaVersion": 1,
                    "packageName": "com.kiloo.subwaysurf",
                    "displayName": "Subway Surfers",
                    "orientation": "PORTRAIT",
                    "tracker": "HAND_SINGLE",
                    "mapping": "LANE_SWIPE",
                    "targets": {
                        "left": { "x": 0.20, "y": 0.80 },
                        "right": { "x": 0.80, "y": 0.80 },
                        "center": { "x": 0.50, "y": 0.50 }
                    },
                    "params": {
                        "deadzoneDeg": 8.0,
                        "cooldownMs": 350,
                        "swipeMinSpeed": 1.2,
                        "slashMinLength": 0.15,
                        "smoothing": { "minCutoff": 1.0, "beta": 0.01 }
                    }
                },
                "schema_version": 1,
                "author": "zorbi-team",
                "approved": true,
                "downloads": 142,
                "is_mock": false,
                "created_at": "2026-10-31T00:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(GameProfileRowDto.serializer(), seedJson)
        assertThat(decoded.packageName).isEqualTo("com.kiloo.subwaysurf")
        assertThat(decoded.approved).isTrue()
        assertThat(decoded.downloads).isEqualTo(142)
        assertThat(decoded.profileJson.mapping).isEqualTo(MappingType.LANE_SWIPE)
        assertThat(decoded.profileJson.orientation).isEqualTo(ProfileOrientation.PORTRAIT)
    }

    @Test
    fun testProfileDownloadRpcDtoSerialization() {
        val rpcDto = ProfileDownloadRpcDto(profileId = "10000000-0000-0000-0000-000000000001")
        val encoded = json.encodeToString(ProfileDownloadRpcDto.serializer(), rpcDto)

        assertThat(encoded).contains("\"profile_id\":\"10000000-0000-0000-0000-000000000001\"")

        val decoded = json.decodeFromString(ProfileDownloadRpcDto.serializer(), encoded)
        assertThat(decoded.profileId).isEqualTo("10000000-0000-0000-0000-000000000001")
    }

    @Test
    fun testSessionRowDtoRoundTrip() {
        val sessionDto = SessionRowDto(
            id = "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f",
            deviceId = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
            deviceModel = "iQOO Z9x",
            androidVersion = 34,
            gamePackage = "com.kiloo.subwaysurf",
            tracker = "HAND_SINGLE",
            perfMode = "BALANCED",
            avgFps = 29.8f,
            p95LatencyMs = 38.4f,
            durationS = 120,
            gesturesCount = 45,
            isMock = false
        )

        val encoded = json.encodeToString(SessionRowDto.serializer(), sessionDto)
        assertThat(encoded).contains("\"device_id\":\"a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d\"")
        assertThat(encoded).contains("\"android_version\":34")
        assertThat(encoded).contains("\"perf_mode\":\"BALANCED\"")

        val decoded = json.decodeFromString(SessionRowDto.serializer(), encoded)
        assertThat(decoded.deviceId).isEqualTo("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        assertThat(decoded.androidVersion).isEqualTo(34)
        assertThat(decoded.avgFps).isEqualTo(29.8f)
    }

    @Test
    fun testGestureEventRowDtoBatchSerialization() {
        val events = listOf(
            GestureEventRowDto(
                sessionId = "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f",
                eventType = "SWIPE",
                magnitude = 1.4f,
                isMock = false
            ),
            GestureEventRowDto(
                sessionId = "c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f",
                eventType = "PAUSE",
                magnitude = 1.0f,
                isMock = false
            )
        )

        val encoded = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(GestureEventRowDto.serializer()), events)
        assertThat(encoded).contains("\"event_type\":\"SWIPE\"")
        assertThat(encoded).contains("\"event_type\":\"PAUSE\"")
        assertThat(encoded).contains("\"session_id\":\"c3d4e5f6-a7b8-9c0d-1e2f-3a4b5c6d7e8f\"")

        val decoded = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GestureEventRowDto.serializer()), encoded)
        assertThat(decoded).hasSize(2)
        assertThat(decoded[0].eventType).isEqualTo("SWIPE")
        assertThat(decoded[1].eventType).isEqualTo("PAUSE")
    }

    @Test(expected = SerializationException::class)
    fun testMalformedJsonThrowsSerializationException() {
        val malformedJson = "{ \"package_name\": \"test\", \"broken_json\": "
        json.decodeFromString(GameProfileRowDto.serializer(), malformedJson)
    }

    @Test(expected = SerializationException::class)
    fun testMissingRequiredFieldsThrowsSerializationException() {
        // Missing required 'package_name' or other non-defaulted field
        val partialJson = "{ \"author\": \"zorbi-team\" }"
        json.decodeFromString(GameProfileRowDto.serializer(), partialJson)
    }

    @Test
    fun testUnknownKeysIgnoredGracefully() {
        val jsonWithExtras = """
            {
                "profile_id": "10000000-0000-0000-0000-000000000001",
                "future_column_added_by_migration": "extra_data"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(ProfileDownloadRpcDto.serializer(), jsonWithExtras)
        assertThat(decoded.profileId).isEqualTo("10000000-0000-0000-0000-000000000001")
    }
}
