package com.zorbi.ui.util

import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.TrackerId

/**
 * Detailed guidance structure representing an identified game and its supported motion gestures.
 */
data class GameGestureGuide(
    val gameTitle: String,
    val packageName: String,
    val orientationLabel: String,
    val trackerBadge: String,
    val supportedGestures: List<String>,
    val quickInstruction: String,
    val spokenVoiceScript: String
)

/**
 * Intelligent game identifier that determines which gestures a game supports and synthesizes
 * step-by-step audible voice guidance.
 */
object GameGestureAdvisor {

    fun getGuide(packageName: String, profile: GameProfile? = null): GameGestureGuide {
        return when (packageName) {
            "com.maleo.bussimulatorindonesia", "com.maleo.bussimulatorid" -> GameGestureGuide(
                gameTitle = "Bus Simulator Indonesia",
                packageName = packageName,
                orientationLabel = "Landscape 3D Cockpit",
                trackerBadge = "Dual-Hand 3D Vision",
                supportedGestures = listOf(
                    "🏎️ Gas: Right Hand Forward",
                    "🛑 Brake: Left Hand Forward",
                    "🔄 Steering: Hold & Tilt Wheel",
                    "📢 Horn: Fist Hold (>=1s)",
                    "💡 Headlights: Pinch (Thumb + Index)",
                    "🕹️ Gear Shift: Flick Up (D) / Down (R)",
                    "🤏 Pinch: Headlights / Pause"
                ),
                quickInstruction = "Hold hands like steering wheel to Steer • Push RIGHT hand forward for Gas • Push LEFT hand forward for Brake • Fist Hold for Horn • Pinch for Headlights.",
                spokenVoiceScript = "Identified Bus Simulator Indonesia. Hold your hands like gripping a steering wheel to steer. Push your right hand forward for gas and your left hand forward for brake. Hold a closed fist to honk the horn, and pinch your fingers to toggle headlights."
            )

            "com.ansangha.drdriving" -> GameGestureGuide(
                gameTitle = "Dr. Driving",
                packageName = packageName,
                orientationLabel = "Landscape Urban Driving",
                trackerBadge = "Dual-Hand 3D Vision",
                supportedGestures = listOf(
                    "🏎️ Gas: Right Hand Forward",
                    "🛑 Brake: Left Hand Forward",
                    "🔄 Steering: Hold & Tilt Wheel",
                    "📢 Horn: Fist Hold (>=1s)",
                    "💡 Headlights: Pinch (Thumb + Index)",
                    "🕹️ Gear Shift: Flick Up (D) / Down (R)"
                ),
                quickInstruction = "Hold hands like steering wheel to Steer • Push RIGHT hand forward for Gas • Push LEFT hand forward for Brake • Fist Hold for Horn • Pinch for Headlights.",
                spokenVoiceScript = "Identified Doctor Driving. Hold your hands in steering position to turn the wheel. Push your right hand forward for the accelerator and your left hand forward for the brake. Hold a fist for horn, and pinch for headlights."
            )

            "com.fingersoft.hillclimb" -> GameGestureGuide(
                gameTitle = "Hill Climb Racing",
                packageName = packageName,
                orientationLabel = "Landscape 2D Physics",
                trackerBadge = "Dual-Hand Forward Reach",
                supportedGestures = listOf(
                    "🏎️ Gas: Right Hand Forward",
                    "🛑 Brake: Left Hand Forward",
                    "🎮 Coast: Pull Hands Back",
                    "✊ Fist Pause"
                ),
                quickInstruction = "Push RIGHT hand forward to accelerate • Push LEFT hand forward to brake • Bring hands back to coast.",
                spokenVoiceScript = "Identified Hill Climb Racing. This game supports dual hand pedal reach. Push your right hand forward to step on the gas, and push your left hand forward to hit the brake. Pull both hands back to coast."
            )

            "com.kiloo.subwaysurf" -> GameGestureGuide(
                gameTitle = "Subway Surfers",
                packageName = packageName,
                orientationLabel = "Portrait 3D Runner",
                trackerBadge = "Single-Hand 3D Vision",
                supportedGestures = listOf(
                    "⬆️ Jump: Flick Up",
                    "⬇️ Roll/Slide: Flick Down",
                    "⬅️ Move Left: Flick Left",
                    "➡️ Move Right: Flick Right",
                    "✊ Fist Pause"
                ),
                quickInstruction = "Flick UP to Jump • Flick DOWN to Roll • Flick LEFT / RIGHT to switch lanes • Closed fist to Pause.",
                spokenVoiceScript = "Identified Subway Surfers. This game supports four-way swipe controls. Flick your hand up to jump, flick down to roll under barriers, and flick left or right to switch train tracks."
            )

            "com.halfbrick.fruitninjafree" -> GameGestureGuide(
                gameTitle = "Fruit Ninja",
                packageName = packageName,
                orientationLabel = "Landscape Blade Arena",
                trackerBadge = "Air Blade Tracking",
                supportedGestures = listOf(
                    "⚔️ Blade Slice: Air Hand Slash",
                    "💣 Avoid Bombs",
                    "✊ Fist Pause"
                ),
                quickInstruction = "Slash your hand through the air to cut fruit • Avoid bombs • Closed fist to Pause.",
                spokenVoiceScript = "Identified Fruit Ninja. This game supports laser blade slashes. Slice your hand through the air like a sword to slice flying fruit, but watch out for bombs!"
            )

            "com.vectorunit.purple.googleplay" -> GameGestureGuide(
                gameTitle = "Beach Buggy Racing",
                packageName = packageName,
                orientationLabel = "Landscape Kart Racing",
                trackerBadge = "Dual-Hand Wheel",
                supportedGestures = listOf(
                    "🔄 Steer: Turn Wheel Angle",
                    "🏎️ Gas: Right Hand Forward",
                    "🛑 Drift: Left Hand Forward",
                    "✊ Fist Pause"
                ),
                quickInstruction = "Turn imaginary steering wheel to corner • Push right forward for gas • Left forward to drift.",
                spokenVoiceScript = "Identified Beach Buggy Racing. This game supports steering wheel tilt and power drifts. Tilt both hands to steer around turns, and push forward to accelerate."
            )

            else -> {
                // Dynamic inference from GameProfile
                val title = profile?.displayName ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                val orientationLabel = if (profile?.orientation == ProfileOrientation.LANDSCAPE) "Landscape 3D View" else "Portrait View"
                val trackerBadge = when (profile?.tracker) {
                    TrackerId.HAND_DUAL_WHEEL -> "Dual-Hand Vision"
                    TrackerId.POSE_LITE -> "Pose Lean Tracking"
                    else -> "Single-Hand Vision"
                }

                val gestures = mutableListOf<String>()
                val spokenParts = mutableListOf<String>()

                when (profile?.mapping) {
                    MappingType.STEER_BUTTONS -> {
                        gestures.add("🏎️ Gas: Right Hand Forward")
                        gestures.add("🛑 Brake: Left Hand Forward")
                        spokenParts.add("push your right hand forward for gas, and push your left hand forward for brake")
                        if (profile.targets.containsKey("gear_up") || profile.targets.containsKey("gear_down")) {
                            gestures.add("🕹️ Gear Shift: Flick Up/Down")
                            spokenParts.add("flick your hand up or down to change gears")
                        }
                    }
                    MappingType.STEER_DRAG -> {
                        gestures.add("🔄 Steer Wheel: Turn Hands")
                        gestures.add("🏎️ Gas: Right Hand Forward")
                        spokenParts.add("tilt your hands together to steer the wheel")
                    }
                    MappingType.LANE_SWIPE -> {
                        gestures.add("⬆️ Jump: Flick Up")
                        gestures.add("⬇️ Roll: Flick Down")
                        gestures.add("⬅️➡️ Lanes: Flick Left/Right")
                        spokenParts.add("flick your hand up, down, left, or right to dodge and move")
                    }
                    MappingType.SLASH -> {
                        gestures.add("⚔️ Laser Slash: Air Trajectory")
                        spokenParts.add("slash your hand across the air to interact")
                    }
                    null -> {
                        gestures.add("🎮 Motion Controls Active")
                        spokenParts.add("follow on-screen motion indicators to play")
                    }
                }
                gestures.add("✊ Fist Pause")

                val instructions = gestures.joinToString(" • ")
                val spoken = "Identified $title. This game supports " +
                        (if (spokenParts.isNotEmpty()) spokenParts.joinToString(", and ") else "motion controls") + "."

                GameGestureGuide(
                    gameTitle = title,
                    packageName = packageName,
                    orientationLabel = orientationLabel,
                    trackerBadge = trackerBadge,
                    supportedGestures = gestures,
                    quickInstruction = instructions,
                    spokenVoiceScript = spoken
                )
            }
        }
    }
}
