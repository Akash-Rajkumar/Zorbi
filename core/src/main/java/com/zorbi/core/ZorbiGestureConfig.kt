package com.zorbi.core

/**
 * Global gesture configuration holding runtime user customizations for:
 * 1. Hand tracking mode (2-Hand Steering Wheel vs Single Hand)
 * 2. Custom gesture mappings (Fist Hold for Horn / Pause / Boost, Pinch for Headlight / Pause / Wipers)
 * 3. Steering layout identification and coordinates (Left Wheel + Right Pedals, Side Buttons, Center Wheel)
 * 4. High-accuracy, low-latency sensitivity settings
 */
object ZorbiGestureConfig {

    enum class HandMode {
        DUAL_HAND,   // TrackerId.HAND_DUAL_WHEEL
        SINGLE_HAND  // TrackerId.HAND_SINGLE
    }

    enum class FistAction(val label: String, val icon: String) {
        HORN("Honk Horn", "📢"),
        PAUSE("Pause Game", "⏸️"),
        BOOST("Nitro Boost", "🚀"),
        HANDBRAKE("Handbrake", "🛑")
    }

    enum class PinchAction(val label: String, val icon: String) {
        HEADLIGHT("Headlights", "💡"),
        PAUSE("Pause / Resume", "🤏"),
        WIPER("Windshield Wipers", "🌧️"),
        HAZARD("Hazard Lights", "⚠️")
    }

    enum class SteerLayout(val label: String) {
        LEFT_WHEEL_RIGHT_PEDALS("Left Wheel (0.18, 0.70) + Right Pedals"),
        SIDE_BUTTONS("Side Touch Buttons (Left / Right)"),
        CENTER_DRAG("Center Virtual Wheel (0.50, 0.50)")
    }

    // Active configurations
    var handMode: HandMode = HandMode.DUAL_HAND
    var fistAction: FistAction = FistAction.HORN
    var pinchAction: PinchAction = PinchAction.HEADLIGHT
    var steerLayout: SteerLayout = SteerLayout.LEFT_WHEEL_RIGHT_PEDALS

    // Dynamic targets for driving & in-game controls (Measured precisely from live game HUDs)
    var steerWheelCenter = TargetPoint(0.125f, 0.785f)
    var gasPedal = TargetPoint(0.95f, 0.88f)
    var brakePedal = TargetPoint(0.85f, 0.88f)
    var hornTarget = TargetPoint(0.10f, 0.72f)
    var headlightTarget = TargetPoint(0.027f, 0.38f)
    var wiperTarget = TargetPoint(0.908f, 0.38f)
    var hazardTarget = TargetPoint(0.076f, 0.38f)
    var boostTarget = TargetPoint(0.90f, 0.40f)
    var handbrakeTarget = TargetPoint(0.85f, 0.70f)
    var leftArrow = TargetPoint(0.04f, 0.72f)
    var rightArrow = TargetPoint(0.168f, 0.72f)
    var gearUpTarget = TargetPoint(0.91f, 0.70f)
    var gearDownTarget = TargetPoint(0.95f, 0.70f)

    /**
     * Adapts default layout and targets according to the specified game package name.
     */
    fun adaptForGame(packageName: String) {
        when (packageName) {
            "com.maleo.bussimulatorindonesia", "com.maleo.bussimulatorid" -> {
                handMode = HandMode.DUAL_HAND
                steerLayout = SteerLayout.LEFT_WHEEL_RIGHT_PEDALS
                steerWheelCenter = TargetPoint(0.125f, 0.785f)
                gasPedal = TargetPoint(0.95f, 0.88f)
                brakePedal = TargetPoint(0.85f, 0.88f)
                hornTarget = TargetPoint(0.10f, 0.72f)
                leftArrow = TargetPoint(0.04f, 0.72f)
                rightArrow = TargetPoint(0.168f, 0.72f)
                headlightTarget = TargetPoint(0.027f, 0.38f)
                wiperTarget = TargetPoint(0.908f, 0.38f)
                hazardTarget = TargetPoint(0.076f, 0.38f)
                gearUpTarget = TargetPoint(0.91f, 0.70f)
                gearDownTarget = TargetPoint(0.95f, 0.70f)
                fistAction = FistAction.HORN
                pinchAction = PinchAction.HEADLIGHT
            }
            "com.ansangha.drdriving" -> {
                handMode = HandMode.DUAL_HAND
                steerLayout = SteerLayout.LEFT_WHEEL_RIGHT_PEDALS
                steerWheelCenter = TargetPoint(0.20f, 0.70f)
                gasPedal = TargetPoint(0.90f, 0.80f)
                brakePedal = TargetPoint(0.76f, 0.80f)
                hornTarget = TargetPoint(0.20f, 0.70f)
                headlightTarget = TargetPoint(0.85f, 0.18f)
                gearUpTarget = TargetPoint(0.92f, 0.32f)
                gearDownTarget = TargetPoint(0.92f, 0.44f)
                fistAction = FistAction.HORN
                pinchAction = PinchAction.HEADLIGHT
            }
            "com.fingersoft.hillclimb" -> {
                handMode = HandMode.DUAL_HAND
                steerLayout = SteerLayout.SIDE_BUTTONS
                gasPedal = TargetPoint(0.88f, 0.84f)
                brakePedal = TargetPoint(0.14f, 0.84f)
                fistAction = FistAction.PAUSE
                pinchAction = PinchAction.PAUSE
            }
            "com.vectorunit.purple.googleplay" -> {
                handMode = HandMode.DUAL_HAND
                steerLayout = SteerLayout.SIDE_BUTTONS
                steerWheelCenter = TargetPoint(0.50f, 0.50f)
                gasPedal = TargetPoint(0.88f, 0.82f)
                brakePedal = TargetPoint(0.12f, 0.82f)
                boostTarget = TargetPoint(0.90f, 0.40f)
                fistAction = FistAction.BOOST
                pinchAction = PinchAction.PAUSE
            }
            "com.kiloo.subwaysurf" -> {
                handMode = HandMode.SINGLE_HAND
                fistAction = FistAction.PAUSE
                pinchAction = PinchAction.PAUSE
            }
            "com.halfbrick.fruitninjafree" -> {
                handMode = HandMode.SINGLE_HAND
                fistAction = FistAction.PAUSE
                pinchAction = PinchAction.PAUSE
            }
            else -> {
                // Keep current defaults
            }
        }
    }
}
