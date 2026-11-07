package com.zorbi.ui.screens.community

import com.google.common.truth.Truth.assertThat
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.TrackerId
import com.zorbi.ui.screens.home.PreviewAppGraph
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Unit tests for Community cloud profile catalog, filtering, and repository contract.
 */
class CommunityStateTest {

    @Test
    fun catalog_containsCuratedVerifiedProfiles() {
        val catalog = CloudProfileItem.CATALOG

        assertThat(catalog).isNotEmpty()
        assertThat(catalog.size).isAtLeast(3)

        val subwaySurfers = catalog.find { it.packageName == "com.kiloo.subwaysurf" }
        assertThat(subwaySurfers).isNotNull()
        assertThat(subwaySurfers!!.mapping).isEqualTo(MappingType.LANE_SWIPE)
        assertThat(subwaySurfers.tracker).isEqualTo(TrackerId.HAND_SINGLE)
    }

    @Test
    fun toGameProfile_mapsToFrozenSchemaVersion1() {
        val cloudItem = CloudProfileItem(
            id = "test-cloud-id",
            packageName = "com.test.game",
            displayName = "Test Game",
            author = "@Tester",
            downloads = 100,
            rating = 5.0f,
            orientation = ProfileOrientation.LANDSCAPE,
            tracker = TrackerId.HAND_DUAL_WHEEL,
            mapping = MappingType.STEER_BUTTONS,
            description = "Test game description"
        )

        val gameProfile: GameProfile = cloudItem.toGameProfile()

        assertThat(gameProfile.schemaVersion).isEqualTo(1)
        assertThat(gameProfile.packageName).isEqualTo("com.test.game")
        assertThat(gameProfile.displayName).isEqualTo("Test Game")
        assertThat(gameProfile.orientation).isEqualTo(ProfileOrientation.LANDSCAPE)
        assertThat(gameProfile.tracker).isEqualTo(TrackerId.HAND_DUAL_WHEEL)
        assertThat(gameProfile.mapping).isEqualTo(MappingType.STEER_BUTTONS)
    }

    @Test
    fun fakeProfileRepository_returnsNullForUnknownCloudId_guaranteeingNoFakeSuccess() = runBlocking {
        val fakeGraph = PreviewAppGraph()
        val imported = fakeGraph.profiles.importFromCloud("non-existent-id")

        // Strictly verify that importFromCloud returns null when not found
        assertThat(imported).isNull()
    }

    @Test
    fun categoryFiltering_correctlyMatchesMappingTypes() {
        val steerItem = CloudProfileItem.CATALOG.find { it.mapping == MappingType.STEER_BUTTONS }
        val swipeItem = CloudProfileItem.CATALOG.find { it.mapping == MappingType.LANE_SWIPE }
        val slashItem = CloudProfileItem.CATALOG.find { it.mapping == MappingType.SLASH }

        assertThat(steerItem).isNotNull()
        assertThat(swipeItem).isNotNull()
        assertThat(slashItem).isNotNull()
    }
}
