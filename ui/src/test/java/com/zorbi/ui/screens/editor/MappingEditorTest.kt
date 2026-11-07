package com.zorbi.ui.screens.editor

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.TargetPoint
import com.zorbi.core.TrackerId
import com.zorbi.ui.navigation.NavRoutes
import com.zorbi.ui.screens.home.PreviewAppGraph
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Unit tests for the Custom Game Mapping Editor (Section 5.2 & Section 6).
 */
class MappingEditorTest {

    @Test
    fun mappingSelection_allFourEnumsSupportedAndMapped() {
        val testPkg = "com.kiloo.subwaysurf"

        val mappings = listOf(
            MappingType.STEER_BUTTONS,
            MappingType.STEER_DRAG,
            MappingType.LANE_SWIPE,
            MappingType.SLASH
        )

        mappings.forEach { mapping ->
            val state = MappingEditorUiState.canonicalDefaultsFor(testPkg, mapping)
            assertThat(state.selectedMapping).isEqualTo(mapping)

            val profile = state.toGameProfile()
            assertThat(profile.mapping).isEqualTo(mapping)
            assertThat(profile.schemaVersion).isEqualTo(1)
        }
    }

    @Test
    fun targetCoordinateValidation_validNormalizedCoordsAccepted() {
        val state = MappingEditorUiState(
            packageName = "com.kiloo.subwaysurf",
            displayName = "Subway Surfers",
            targetLeftX = 0.15f,
            targetLeftY = 0.85f,
            targetRightX = 0.85f,
            targetRightY = 0.85f,
            targetCenterX = 0.50f,
            targetCenterY = 0.50f
        )

        val errors = state.validate()
        assertThat(errors).isEmpty()
    }

    @Test
    fun targetCoordinateValidation_outOfBoundsCoordinatesRejected() {
        val negativeCoords = MappingEditorUiState(
            packageName = "com.kiloo.subwaysurf",
            targetLeftX = -0.1f,
            targetLeftY = 0.5f
        )
        assertThat(negativeCoords.validate()).isNotEmpty()
        assertThat(negativeCoords.validate().any { it.contains("Left target") }).isTrue()

        val excessCoords = MappingEditorUiState(
            packageName = "com.kiloo.subwaysurf",
            targetRightX = 1.25f,
            targetRightY = 0.5f
        )
        assertThat(excessCoords.validate()).isNotEmpty()
        assertThat(excessCoords.validate().any { it.contains("Right target") }).isTrue()
    }

    @Test
    fun parameterValidation_validRangesAccepted() {
        val validState = MappingEditorUiState(
            packageName = "com.fingersoft.hillclimb",
            deadzoneDeg = 12.0f,
            cooldownMs = 250L,
            swipeMinSpeed = 1.5f,
            slashMinLength = 0.20f,
            minCutoff = 1.2f,
            beta = 0.015f
        )

        val errors = validState.validate()
        assertThat(errors).isEmpty()
    }

    @Test
    fun parameterValidation_outOfBoundsRejected() {
        val invalidDeadzone = MappingEditorUiState(
            packageName = "com.test",
            deadzoneDeg = 55.0f // Max allowed is 45°
        )
        assertThat(invalidDeadzone.validate().any { it.contains("Deadzone") }).isTrue()

        val invalidCooldown = MappingEditorUiState(
            packageName = "com.test",
            cooldownMs = 20L // Min allowed is 50ms
        )
        assertThat(invalidCooldown.validate().any { it.contains("Cooldown") }).isTrue()

        val invalidMinCutoff = MappingEditorUiState(
            packageName = "com.test",
            minCutoff = 0.0f // Must be > 0
        )
        assertThat(invalidMinCutoff.validate().any { it.contains("minCutoff") }).isTrue()
    }

    @Test
    fun resetBehavior_restoresCanonicalDefaults() {
        val testPkg = "com.fingersoft.hillclimb"
        val customState = MappingEditorUiState(
            packageName = testPkg,
            displayName = "Custom Hill Climb",
            selectedMapping = MappingType.STEER_BUTTONS,
            targetLeftX = 0.05f,
            targetLeftY = 0.95f,
            deadzoneDeg = 20f
        )

        val defaults = MappingEditorUiState.canonicalDefaultsFor(testPkg, MappingType.STEER_BUTTONS)

        assertThat(defaults.targetLeftX).isEqualTo(0.18f)
        assertThat(defaults.targetLeftY).isEqualTo(0.82f)
        assertThat(defaults.deadzoneDeg).isEqualTo(8.0f)
        assertThat(defaults.selectedTracker).isEqualTo(TrackerId.HAND_SINGLE)
        assertThat(defaults.orientation).isEqualTo(ProfileOrientation.LANDSCAPE)
    }

    @Test
    fun savingBehavior_persistsToProfileRepository() = runBlocking {
        val appGraph = PreviewAppGraph()
        val testPkg = "com.halfbrick.fruitninjax"

        val originalProfile = GameProfile(
            schemaVersion = 1,
            packageName = testPkg,
            displayName = "Fruit Ninja Classic",
            orientation = ProfileOrientation.LANDSCAPE,
            tracker = TrackerId.HAND_SINGLE,
            mapping = MappingType.SLASH,
            targets = mapOf(
                "left" to TargetPoint(0.10f, 0.20f),
                "right" to TargetPoint(0.90f, 0.80f)
            ),
            params = ProfileParams(slashMinLength = 0.15f)
        )
        appGraph.profiles.saveLocal(originalProfile)

        // Verify initial state
        val loaded = appGraph.profiles.get(testPkg)
        assertThat(loaded).isNotNull()
        assertThat(loaded?.mapping).isEqualTo(MappingType.SLASH)

        // Modify in editor
        val editorState = MappingEditorUiState.fromProfile(loaded!!)
        val modifiedState = editorState.copy(
            slashMinLength = 0.35f,
            targetLeftX = 0.05f,
            targetRightX = 0.95f
        )

        // Save updated profile
        val updatedProfile = modifiedState.toGameProfile()
        appGraph.profiles.saveLocal(updatedProfile)

        // Verify updated state in repository
        val reloaded = appGraph.profiles.get(testPkg)
        assertThat(reloaded).isNotNull()
        assertThat(reloaded?.params?.slashMinLength).isEqualTo(0.35f)
        assertThat(reloaded?.targets?.get("left")?.x).isEqualTo(0.05f)
        assertThat(reloaded?.targets?.get("right")?.x).isEqualTo(0.95f)
    }

    @Test
    fun navigationRoute_editorRouteFormatMatchesContract() {
        val testPkg = "com.kiloo.subwaysurf"
        assertThat(NavRoutes.EDITOR).isEqualTo("editor/{packageName}")
        assertThat(NavRoutes.editor(testPkg)).isEqualTo("editor/com.kiloo.subwaysurf")
    }
}
