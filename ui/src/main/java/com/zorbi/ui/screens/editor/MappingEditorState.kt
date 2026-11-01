package com.zorbi.ui.screens.editor

import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.ProfileSmoothing
import com.zorbi.core.TargetPoint
import com.zorbi.core.TrackerId

/**
 * UI State for the Custom Game Mapping Editor.
 * Enforces the frozen GameProfile schema (schemaVersion 1, Section 5.2).
 */
data class MappingEditorUiState(
    val isLoading: Boolean = false,
    val packageName: String = "",
    val displayName: String = "",
    val orientation: ProfileOrientation = ProfileOrientation.PORTRAIT,
    val selectedTracker: TrackerId = TrackerId.HAND_SINGLE,
    val selectedMapping: MappingType = MappingType.LANE_SWIPE,
    // Normalized Screen-space Target Coordinates (0.0f .. 1.0f)
    val targetLeftX: Float = 0.20f,
    val targetLeftY: Float = 0.80f,
    val targetRightX: Float = 0.80f,
    val targetRightY: Float = 0.80f,
    val targetCenterX: Float = 0.50f,
    val targetCenterY: Float = 0.50f,
    // Detection & Filter Parameters (Section 5.2 ProfileParams)
    val deadzoneDeg: Float = 8.0f,
    val cooldownMs: Long = 350L,
    val swipeMinSpeed: Float = 1.2f,
    val slashMinLength: Float = 0.15f,
    val minCutoff: Float = 1.0f,
    val beta: Float = 0.01f,
    // Status & Interaction flags
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val validationErrors: List<String> = emptyList(),
    val showPreviewDialog: Boolean = false,
    val showResetConfirmDialog: Boolean = false,
    val availableProfiles: List<GameProfile> = emptyList()
) {
    /**
     * Converts the current editor state into a validated GameProfile per Section 5.2.
     */
    fun toGameProfile(): GameProfile {
        val targetsMap = mutableMapOf<String, TargetPoint>()
        targetsMap["left"] = TargetPoint(
            x = targetLeftX.coerceIn(0f, 1f),
            y = targetLeftY.coerceIn(0f, 1f)
        )
        targetsMap["right"] = TargetPoint(
            x = targetRightX.coerceIn(0f, 1f),
            y = targetRightY.coerceIn(0f, 1f)
        )
        targetsMap["center"] = TargetPoint(
            x = targetCenterX.coerceIn(0f, 1f),
            y = targetCenterY.coerceIn(0f, 1f)
        )

        return GameProfile(
            schemaVersion = 1,
            packageName = packageName,
            displayName = displayName.ifBlank { packageName },
            orientation = orientation,
            tracker = selectedTracker,
            mapping = selectedMapping,
            targets = targetsMap,
            params = ProfileParams(
                deadzoneDeg = deadzoneDeg.coerceIn(0f, 45f),
                cooldownMs = cooldownMs.coerceIn(50L, 2000L),
                swipeMinSpeed = swipeMinSpeed.coerceIn(0.1f, 10f),
                slashMinLength = slashMinLength.coerceIn(0.01f, 1.0f),
                smoothing = ProfileSmoothing(
                    minCutoff = minCutoff.coerceIn(0.1f, 10f),
                    beta = beta.coerceIn(0.0001f, 1.0f)
                )
            )
        )
    }

    /**
     * Validates all coordinates and parameters. Returns true if completely valid.
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (packageName.isBlank()) {
            errors.add("Package name cannot be empty.")
        }

        // Target coordinates validation (strictly 0.0 .. 1.0 normalized screen space)
        if (targetLeftX !in 0.0f..1.0f || targetLeftY !in 0.0f..1.0f) {
            errors.add("Left target coordinates must be between 0.0 and 1.0.")
        }
        if (targetRightX !in 0.0f..1.0f || targetRightY !in 0.0f..1.0f) {
            errors.add("Right target coordinates must be between 0.0 and 1.0.")
        }
        if (targetCenterX !in 0.0f..1.0f || targetCenterY !in 0.0f..1.0f) {
            errors.add("Center target coordinates must be between 0.0 and 1.0.")
        }

        // Parameter range validation
        if (deadzoneDeg < 0f || deadzoneDeg > 45f) {
            errors.add("Deadzone angle must be between 0° and 45°.")
        }
        if (cooldownMs < 50L || cooldownMs > 2000L) {
            errors.add("Cooldown duration must be between 50ms and 2000ms.")
        }
        if (swipeMinSpeed < 0.1f || swipeMinSpeed > 10.0f) {
            errors.add("Minimum swipe speed must be between 0.1 and 10.0.")
        }
        if (slashMinLength < 0.01f || slashMinLength > 1.0f) {
            errors.add("Minimum slash length must be between 0.01 and 1.0.")
        }
        if (minCutoff <= 0f) {
            errors.add("Smoothing minCutoff must be greater than 0.")
        }
        if (beta < 0f) {
            errors.add("Smoothing beta coefficient cannot be negative.")
        }

        return errors
    }

    companion object {
        /**
         * Creates a state populated from an existing GameProfile.
         */
        fun fromProfile(profile: GameProfile): MappingEditorUiState {
            val left = profile.targets["left"] ?: TargetPoint(0.20f, 0.80f)
            val right = profile.targets["right"] ?: TargetPoint(0.80f, 0.80f)
            val center = profile.targets["center"] ?: TargetPoint(0.50f, 0.50f)

            return MappingEditorUiState(
                packageName = profile.packageName,
                displayName = profile.displayName,
                orientation = profile.orientation,
                selectedTracker = profile.tracker,
                selectedMapping = profile.mapping,
                targetLeftX = left.x,
                targetLeftY = left.y,
                targetRightX = right.x,
                targetRightY = right.y,
                targetCenterX = center.x,
                targetCenterY = center.y,
                deadzoneDeg = profile.params.deadzoneDeg,
                cooldownMs = profile.params.cooldownMs,
                swipeMinSpeed = profile.params.swipeMinSpeed,
                slashMinLength = profile.params.slashMinLength,
                minCutoff = profile.params.smoothing.minCutoff,
                beta = profile.params.smoothing.beta
            )
        }

        /**
         * Returns canonical default targets and parameters for a specific game & mapping.
         */
        fun canonicalDefaultsFor(packageName: String, mapping: MappingType): MappingEditorUiState {
            val (left, right, center) = when (mapping) {
                MappingType.STEER_BUTTONS -> Triple(
                    TargetPoint(0.18f, 0.82f),
                    TargetPoint(0.82f, 0.82f),
                    TargetPoint(0.50f, 0.82f)
                )
                MappingType.STEER_DRAG -> Triple(
                    TargetPoint(0.15f, 0.85f),
                    TargetPoint(0.85f, 0.85f),
                    TargetPoint(0.50f, 0.85f)
                )
                MappingType.LANE_SWIPE -> Triple(
                    TargetPoint(0.20f, 0.50f),
                    TargetPoint(0.80f, 0.50f),
                    TargetPoint(0.50f, 0.50f)
                )
                MappingType.SLASH -> Triple(
                    TargetPoint(0.15f, 0.30f),
                    TargetPoint(0.85f, 0.70f),
                    TargetPoint(0.50f, 0.50f)
                )
            }

            val isLandscape = packageName.contains("hillclimb") ||
                    packageName.contains("fruit") ||
                    packageName.contains("asphalt")

            val tracker = when (mapping) {
                MappingType.STEER_BUTTONS -> TrackerId.HAND_SINGLE
                MappingType.STEER_DRAG -> TrackerId.HAND_DUAL_WHEEL
                MappingType.LANE_SWIPE -> TrackerId.HAND_SINGLE
                MappingType.SLASH -> TrackerId.HAND_SINGLE
            }

            return MappingEditorUiState(
                packageName = packageName,
                displayName = when {
                    packageName.contains("subwaysurf") -> "Subway Surfers"
                    packageName.contains("hillclimb") -> "Hill Climb Racing"
                    packageName.contains("templerun") -> "Temple Run 2"
                    packageName.contains("fruit") -> "Fruit Ninja Classic"
                    packageName.contains("asphalt") -> "Asphalt Legends Unite"
                    else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                },
                orientation = if (isLandscape) ProfileOrientation.LANDSCAPE else ProfileOrientation.PORTRAIT,
                selectedTracker = tracker,
                selectedMapping = mapping,
                targetLeftX = left.x,
                targetLeftY = left.y,
                targetRightX = right.x,
                targetRightY = right.y,
                targetCenterX = center.x,
                targetCenterY = center.y,
                deadzoneDeg = if (mapping == MappingType.STEER_BUTTONS || mapping == MappingType.STEER_DRAG) 8.0f else 0.0f,
                cooldownMs = when (mapping) {
                    MappingType.LANE_SWIPE -> 350L
                    MappingType.SLASH -> 120L
                    else -> 250L
                },
                swipeMinSpeed = 1.2f,
                slashMinLength = 0.15f,
                minCutoff = 1.0f,
                beta = 0.01f
            )
        }
    }
}
