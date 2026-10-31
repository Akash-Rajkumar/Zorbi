package com.zorbi.engine

import com.zorbi.core.GameProfile

/**
 * Validates [GameProfile] instances against the frozen schemaVersion 1 specifications
 * and security constraints (package name validity, path traversal prevention, coordinate bounds).
 */
object ProfileValidator {

    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    /**
     * Checks whether [packageName] is a well-formed Android application ID and is safe
     * from directory traversal attacks (no '..', '/', '\' or illegal characters).
     */
    fun isValidPackageName(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName.contains("..") || packageName.contains('/') || packageName.contains('\\')) return false
        return PACKAGE_NAME_REGEX.matches(packageName)
    }

    /**
     * Validates that [profile] adheres to all frozen schemaVersion 1 rules and range constraints.
     */
    fun isValid(profile: GameProfile): Boolean {
        // 1. Schema version check: only schemaVersion 1 is supported
        if (profile.schemaVersion != 1) return false

        // 2. Package name validation
        if (!isValidPackageName(profile.packageName)) return false

        // 3. Display name check
        if (profile.displayName.isBlank()) return false

        // 4. Target points validation: all coordinates must be in normalized range 0..1
        for ((_, target) in profile.targets) {
            if (target.x.isNaN() || target.x.isInfinite() || target.x < 0.0f || target.x > 1.0f) return false
            if (target.y.isNaN() || target.y.isInfinite() || target.y < 0.0f || target.y > 1.0f) return false
        }

        // 5. Profile parameters validation
        val params = profile.params
        if (params.deadzoneDeg.isNaN() || params.deadzoneDeg.isInfinite() || params.deadzoneDeg < 0.0f || params.deadzoneDeg > 90.0f) return false
        if (params.cooldownMs < 0L) return false
        if (params.swipeMinSpeed.isNaN() || params.swipeMinSpeed.isInfinite() || params.swipeMinSpeed <= 0.0f) return false
        if (params.slashMinLength.isNaN() || params.slashMinLength.isInfinite() || params.slashMinLength < 0.0f || params.slashMinLength > 1.0f) return false

        // 6. Smoothing parameters validation
        val smoothing = params.smoothing
        if (smoothing.minCutoff.isNaN() || smoothing.minCutoff.isInfinite() || smoothing.minCutoff <= 0.0f) return false
        if (smoothing.beta.isNaN() || smoothing.beta.isInfinite() || smoothing.beta < 0.0f) return false

        return true
    }
}

