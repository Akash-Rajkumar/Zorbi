package com.zorbi.core

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class GameProfileTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testDefaultGameProfileSerialization() {
        val profile = GameProfile(
            schemaVersion = 1,
            packageName = "com.example.game",
            displayName = "Example Game",
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

        val serialized = json.encodeToString(GameProfile.serializer(), profile)
        val deserialized = json.decodeFromString(GameProfile.serializer(), serialized)

        assertThat(deserialized.packageName).isEqualTo("com.example.game")
        assertThat(deserialized.tracker).isEqualTo(TrackerId.HAND_SINGLE)
        assertThat(deserialized.mapping).isEqualTo(MappingType.LANE_SWIPE)
        assertThat(deserialized.targets["left"]?.x).isEqualTo(0.20f)
    }
}

