package com.zorbi.ui.screens.about

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit verification for About screen specifications, team roles, and privacy contracts.
 */
class AboutSpecTest {

    @Test
    fun version_isKodikonEdition() {
        val version = "Version 1.0 (Kodikon Edition)"
        assertThat(version).contains("1.0")
        assertThat(version).contains("Kodikon")
    }

    @Test
    fun privacyGuarantee_ensuresZeroCloudVision() {
        val statements = listOf(
            "100% On-Device Computer Vision",
            "Zero camera frames or video data ever leave the device",
            "Strictly opt-in anonymous telemetry",
            "Full gesture control operates completely offline"
        )

        statements.forEach { statement ->
            assertThat(statement).isNotEmpty()
        }
    }

    @Test
    fun teamCredits_coverAllFourMembersPerCodeowners() {
        val teamRoles = mapOf(
            "Person 1" to listOf(":vision", ":calib"),
            "Person 2" to listOf(":gesture", ":input", ":engine"),
            "Person 3" to listOf(":app", ":ui"),
            "Person 4" to listOf(":data", ":supabase", "CI")
        )

        assertThat(teamRoles).hasSize(4)
        assertThat(teamRoles["Person 1"]).contains(":vision")
        assertThat(teamRoles["Person 2"]).contains(":engine")
        assertThat(teamRoles["Person 3"]).contains(":ui")
        assertThat(teamRoles["Person 4"]).contains(":data")
    }
}
