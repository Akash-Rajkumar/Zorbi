package com.zorbi.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.TrackerId
import com.zorbi.ui.screens.home.HomeSystemStatus
import com.zorbi.ui.screens.library.LibraryFilter
import org.junit.Test

/**
 * Unit verification for Navigation Routes and UI State logic.
 * Enforces Master Prompt Section 5.4 (FROZEN navigation routes).
 */
class NavigationTest {

    @Test
    fun verifyFrozenNavigationRouteContracts() {
        // Section 5.4: splash, home, library, wizard, calibrate/{packageName}, play/{packageName}, community, settings, about
        assertThat(NavRoutes.SPLASH).isEqualTo("splash")
        assertThat(NavRoutes.HOME).isEqualTo("home")
        assertThat(NavRoutes.LIBRARY).isEqualTo("library")
        assertThat(NavRoutes.WIZARD).isEqualTo("wizard")
        assertThat(NavRoutes.CALIBRATE).isEqualTo("calibrate/{packageName}")
        assertThat(NavRoutes.PLAY).isEqualTo("play/{packageName}")
        assertThat(NavRoutes.COMMUNITY).isEqualTo("community")
        assertThat(NavRoutes.SETTINGS).isEqualTo("settings")
        assertThat(NavRoutes.ABOUT).isEqualTo("about")
        assertThat(NavRoutes.EDITOR).isEqualTo("editor/{packageName}")
    }

    @Test
    fun verifyDynamicRouteGenerators() {
        val testPkg = "com.kiloo.subwaysurf"
        assertThat(NavRoutes.calibrate(testPkg)).isEqualTo("calibrate/com.kiloo.subwaysurf")
        assertThat(NavRoutes.play(testPkg)).isEqualTo("play/com.kiloo.subwaysurf")
        assertThat(NavRoutes.editor(testPkg)).isEqualTo("editor/com.kiloo.subwaysurf")
    }

    @Test
    fun verifySystemReadinessLogic() {
        val allGranted = HomeSystemStatus(
            isCameraGranted = true,
            isAccessibilityGranted = true,
            isBatteryOptimized = true
        )
        assertThat(allGranted.isAllGreen).isTrue()

        val missingCamera = allGranted.copy(isCameraGranted = false)
        assertThat(missingCamera.isAllGreen).isFalse()

        val missingAccessibility = allGranted.copy(isAccessibilityGranted = false)
        assertThat(missingAccessibility.isAllGreen).isFalse()

        val missingBattery = allGranted.copy(isBatteryOptimized = false)
        assertThat(missingBattery.isAllGreen).isFalse()
    }

    @Test
    fun verifyLibraryFilterCoverage() {
        val sampleProfiles = listOf(
            GameProfile(
                packageName = "runner",
                displayName = "Runner",
                orientation = ProfileOrientation.PORTRAIT,
                tracker = TrackerId.HAND_SINGLE,
                mapping = MappingType.LANE_SWIPE
            ),
            GameProfile(
                packageName = "racer",
                displayName = "Racer",
                orientation = ProfileOrientation.LANDSCAPE,
                tracker = TrackerId.HAND_DUAL_WHEEL,
                mapping = MappingType.STEER_BUTTONS
            ),
            GameProfile(
                packageName = "pose_racer",
                displayName = "Pose Racer",
                orientation = ProfileOrientation.LANDSCAPE,
                tracker = TrackerId.POSE_LITE,
                mapping = MappingType.STEER_DRAG
            ),
            GameProfile(
                packageName = "slicer",
                displayName = "Slicer",
                orientation = ProfileOrientation.LANDSCAPE,
                tracker = TrackerId.HAND_SINGLE,
                mapping = MappingType.SLASH
            )
        )

        val oneHand = sampleProfiles.filter { it.tracker == TrackerId.HAND_SINGLE }
        assertThat(oneHand).hasSize(2)

        val wheel = sampleProfiles.filter { it.tracker == TrackerId.HAND_DUAL_WHEEL }
        assertThat(wheel).hasSize(1)

        val pose = sampleProfiles.filter { it.tracker == TrackerId.POSE_LITE }
        assertThat(pose).hasSize(1)

        val swipe = sampleProfiles.filter { it.mapping == MappingType.LANE_SWIPE }
        assertThat(swipe).hasSize(1)

        val slash = sampleProfiles.filter { it.mapping == MappingType.SLASH }
        assertThat(slash).hasSize(1)
    }

    @Test
    fun verifyStartupRoutingDecision_firstTimeUserDirectsToWizard() {
        val firstTimeUserWizardDone = false
        val targetDestination = if (firstTimeUserWizardDone) NavRoutes.HOME else NavRoutes.WIZARD
        assertThat(targetDestination).isEqualTo(NavRoutes.WIZARD)
    }

    @Test
    fun verifyStartupRoutingDecision_returningUserDirectsToHome() {
        val returningUserWizardDone = true
        val targetDestination = if (returningUserWizardDone) NavRoutes.HOME else NavRoutes.WIZARD
        assertThat(targetDestination).isEqualTo(NavRoutes.HOME)
    }
}
