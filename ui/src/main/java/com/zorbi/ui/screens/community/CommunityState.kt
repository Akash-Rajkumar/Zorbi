package com.zorbi.ui.screens.community

import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.TargetPoint
import com.zorbi.core.TrackerId

/**
 * Community cloud profile model for browsing and importing.
 */
data class CloudProfileItem(
    val id: String,
    val packageName: String,
    val displayName: String,
    val author: String,
    val downloads: Int,
    val rating: Float,
    val orientation: ProfileOrientation,
    val tracker: TrackerId,
    val mapping: MappingType,
    val description: String,
    val targets: Map<String, TargetPoint> = emptyMap(),
    val params: ProfileParams = ProfileParams(),
    val isImported: Boolean = false
) {
    fun toGameProfile(): GameProfile {
        return GameProfile(
            schemaVersion = 1,
            packageName = packageName,
            displayName = displayName,
            orientation = orientation,
            tracker = tracker,
            mapping = mapping,
            targets = targets,
            params = params
        )
    }

    companion object {
        /**
         * Curated cloud profiles available in the community catalog.
         */
        val CATALOG: List<CloudProfileItem> = listOf(
            CloudProfileItem(
                id = "cloud-subway-surfers",
                packageName = "com.kiloo.subwaysurf",
                displayName = "Subway Surfers",
                author = "@ZorbiVerified",
                downloads = 4820,
                rating = 4.9f,
                orientation = ProfileOrientation.PORTRAIT,
                tracker = TrackerId.HAND_SINGLE,
                mapping = MappingType.LANE_SWIPE,
                description = "Rapid 4-direction hand swipes for lane dodging, jumping, and rolling."
            ),
            CloudProfileItem(
                id = "cloud-hill-climb-2",
                packageName = "com.fingersoft.hillclimb",
                displayName = "Hill Climb Racing",
                author = "@GravityKing",
                downloads = 3210,
                rating = 4.8f,
                orientation = ProfileOrientation.LANDSCAPE,
                tracker = TrackerId.HAND_SINGLE,
                mapping = MappingType.STEER_BUTTONS,
                description = "Single-hand air gestures for responsive throttle acceleration, brake control, and mid-air balance."
            ),
            CloudProfileItem(
                id = "cloud-beach-buggy",
                packageName = "com.vectorunit.purple.googleplay",
                displayName = "Beach Buggy Racing",
                author = "@TurboDrift",
                downloads = 5120,
                rating = 4.8f,
                orientation = ProfileOrientation.LANDSCAPE,
                tracker = TrackerId.HAND_DUAL_WHEEL,
                mapping = MappingType.STEER_BUTTONS,
                description = "Dual-hand steering wheel air gestures with powerup triggers for Beach Buggy Racing."
            ),
            CloudProfileItem(
                id = "cloud-paper-io",
                packageName = "io.voodoo.paper2",
                displayName = "Paper.io",
                author = "@TerritoryMaster",
                downloads = 4390,
                rating = 4.8f,
                orientation = ProfileOrientation.PORTRAIT,
                tracker = TrackerId.HAND_SINGLE,
                mapping = MappingType.STEER_DRAG,
                description = "Fluid continuous 360-degree air steering drag to capture territory and cut opponent trails."
            ),
            CloudProfileItem(
                id = "cloud-temple-run-2",
                packageName = "com.imangi.templerun2",
                displayName = "Temple Run 2",
                author = "@MotionPro",
                downloads = 2740,
                rating = 4.7f,
                orientation = ProfileOrientation.PORTRAIT,
                tracker = TrackerId.POSE_LITE,
                mapping = MappingType.LANE_SWIPE,
                description = "Body-tilt steering and air gestures for turning tight temple corners."
            ),
            CloudProfileItem(
                id = "cloud-fruit-ninja",
                packageName = "com.halfbrick.fruitninjax",
                displayName = "Fruit Ninja Classic",
                author = "@BladeMaster",
                downloads = 1950,
                rating = 4.9f,
                orientation = ProfileOrientation.LANDSCAPE,
                tracker = TrackerId.HAND_SINGLE,
                mapping = MappingType.SLASH,
                description = "Laser air slash strokes to slice juicy fruits with sub-30ms latency."
            ),
            CloudProfileItem(
                id = "cloud-asphalt-9",
                packageName = "com.gameloft.android.ANMP.GloftA9HM",
                displayName = "Asphalt Legends Unite",
                author = "@ApexRacer",
                downloads = 1580,
                rating = 4.6f,
                orientation = ProfileOrientation.LANDSCAPE,
                tracker = TrackerId.HAND_DUAL_WHEEL,
                mapping = MappingType.STEER_DRAG,
                description = "Continuous steering drag arc with nitrous boost tap triggers."
            ),
            CloudProfileItem(
                id = "cloud-pubg-mobile",
                packageName = "com.tencent.ig",
                displayName = "PUBG Mobile",
                author = "@ProSniper",
                downloads = 8420,
                rating = 4.8f,
                orientation = ProfileOrientation.LANDSCAPE,
                tracker = TrackerId.HAND_SINGLE,
                mapping = MappingType.STEER_DRAG,
                description = "Motion aiming and continuous drag recoil smoothing for PUBG Mobile."
            ),
            CloudProfileItem(
                id = "cloud-bgmi",
                packageName = "com.pubg.imobile",
                displayName = "BGMI: Battlegrounds",
                author = "@DeltaForce",
                downloads = 9150,
                rating = 4.9f,
                orientation = ProfileOrientation.LANDSCAPE,
                tracker = TrackerId.HAND_SINGLE,
                mapping = MappingType.STEER_DRAG,
                description = "Competitive gyroscope and air drag mapping tailored for BGMI."
            ),
            CloudProfileItem(
                id = "cloud-cod-mobile",
                packageName = "com.activision.callofduty.shooter",
                displayName = "Call of Duty: Mobile",
                author = "@GhostOps",
                downloads = 7640,
                rating = 4.7f,
                orientation = ProfileOrientation.LANDSCAPE,
                tracker = TrackerId.HAND_SINGLE,
                mapping = MappingType.STEER_BUTTONS,
                description = "Fast dual-trigger ADS and hipfire air touch mapping."
            ),
            CloudProfileItem(
                id = "cloud-free-fire",
                packageName = "com.dts.freefireth",
                displayName = "Free Fire MAX",
                author = "@BooyahMaster",
                downloads = 6580,
                rating = 4.6f,
                orientation = ProfileOrientation.LANDSCAPE,
                tracker = TrackerId.HAND_SINGLE,
                mapping = MappingType.LANE_SWIPE,
                description = "Rapid flick gesture mapping for fast directional movement and wall placement."
            )
        )
    }
}

/**
 * Filter categories for community browsing.
 */
enum class CommunityCategory(val label: String) {
    ALL("All Profiles"),
    STEER("Steering"),
    SWIPE("Lane Swipe"),
    SLASH("Air Slash"),
    POSE("Body Lean")
}
