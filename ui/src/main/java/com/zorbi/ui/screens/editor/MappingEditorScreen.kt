package com.zorbi.ui.screens.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.AppGraph
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.TrackerId
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.components.ZorbiErrorCard
import com.zorbi.ui.components.ZorbiIconButton
import com.zorbi.ui.components.ZorbiLoadingIndicator
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiScaffold
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.haptics.rememberZorbiHaptics
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =============================================================================
// Professional Gaming Utility Color Tokens
// Clean, low-glare dark dashboard palette
// =============================================================================
private val UtilityBg = Color(0xFF0A0E1A)
private val UtilityCard = Color(0xFF151C2E)
private val UtilitySurfaceSec = Color(0xFF1B2438)
private val UtilityPrimaryAccent = Color(0xFF00C8D9)
private val UtilitySecondaryAccent = Color(0xFF7986CB)
private val UtilityTextPrimary = Color(0xFFF1F5F9)
private val UtilityTextSecondary = Color(0xFF94A3B8)
private val UtilityBorder = Color(0xFF26324A)
private val UtilityBorderFocused = Color(0xFF00C8D9)

/**
 * Custom Game Mapping Editor Screen.
 * Professional gaming controller configuration dashboard for touch targets,
 * motion tracker selection, mapping modes, and One Euro filter smoothing.
 */
@Composable
fun MappingEditorScreen(
    packageName: String,
    appGraph: AppGraph,
    onNavigateBack: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberZorbiHaptics()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var state by remember {
        mutableStateOf(
            MappingEditorUiState.canonicalDefaultsFor(
                packageName = packageName.ifBlank { "com.kiloo.subwaysurf" },
                mapping = MappingType.LANE_SWIPE
            ).copy(isLoading = true)
        )
    }

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Load existing profile from ProfileRepository
    LaunchedEffect(packageName) {
        state = state.copy(isLoading = true, errorMessage = null)
        try {
            val allProfiles = try {
                appGraph.profiles.list()
            } catch (e: Exception) {
                emptyList()
            }

            val targetPkg = packageName.ifBlank { "com.kiloo.subwaysurf" }
            val existing = appGraph.profiles.get(targetPkg)

            if (existing != null) {
                state = MappingEditorUiState.fromProfile(existing).copy(
                    isLoading = false,
                    availableProfiles = allProfiles
                )
            } else {
                state = MappingEditorUiState.canonicalDefaultsFor(
                    packageName = targetPkg,
                    mapping = MappingType.LANE_SWIPE
                ).copy(
                    isLoading = false,
                    availableProfiles = allProfiles
                )
            }
        } catch (e: Exception) {
            state = state.copy(
                isLoading = false,
                errorMessage = e.message ?: "Failed to load game profile"
            )
        }
    }

    fun handleSave() {
        val errors = state.validate()
        if (errors.isNotEmpty()) {
            haptics.warning()
            state = state.copy(validationErrors = errors)
            return
        }

        haptics.confirm()
        scope.launch {
            state = state.copy(isSaving = true, validationErrors = emptyList(), errorMessage = null)
            try {
                val updatedProfile = state.toGameProfile()
                appGraph.profiles.saveLocal(updatedProfile)
                state = state.copy(isSaving = false, saveSuccess = true)
                saveSuccessMessage = "Mapping for '${state.displayName}' saved successfully!"
                delay(2500L)
                saveSuccessMessage = null
            } catch (e: Exception) {
                state = state.copy(
                    isSaving = false,
                    errorMessage = "Failed to save mapping: ${e.message}"
                )
            }
        }
    }

    fun handleResetToDefault() {
        haptics.click()
        val defaults = MappingEditorUiState.canonicalDefaultsFor(state.packageName, state.selectedMapping)
        state = defaults.copy(availableProfiles = state.availableProfiles)
        showResetConfirmDialog = false
    }

    ZorbiScaffold(
        title = "Mapping Editor",
        subtitle = state.displayName.ifBlank { "Custom Controller" },
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        actions = {
            ZorbiIconButton(
                icon = Icons.Default.Check,
                contentDescription = "Save Mapping",
                onClick = { handleSave() }
            )
        },
        modifier = modifier
    ) { _ ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ZorbiLoadingIndicator(message = "Loading configuration...")
            }
            return@ZorbiScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success Feedback Banner
            AnimatedVisibility(
                visible = saveSuccessMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(UtilitySurfaceSec)
                        .border(1.dp, ZorbiSuccess, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = ZorbiSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = saveSuccessMessage.orEmpty(),
                        style = ZorbiTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = UtilityTextPrimary
                    )
                }
            }

            // Validation Errors Banner
            if (state.validationErrors.isNotEmpty()) {
                ZorbiErrorCard(
                    title = "Invalid Configuration",
                    message = state.validationErrors.joinToString("\n• ", prefix = "• "),
                    fixButtonText = "Dismiss",
                    onFixClick = { state = state.copy(validationErrors = emptyList()) }
                )
            }

            // Section A: Game Selection & Header
            EditorGameHeaderCard(
                displayName = state.displayName,
                packageName = state.packageName,
                orientation = state.orientation,
                availableProfiles = state.availableProfiles,
                onSelectProfile = { selected ->
                    state = MappingEditorUiState.fromProfile(selected).copy(
                        availableProfiles = state.availableProfiles
                    )
                },
                onReturnToLibrary = onNavigateToLibrary
            )

            // Section B: Tracker Selection
            UtilitySectionHeader(
                title = "MOTION TRACKER",
                subtitle = "Select computer vision tracking model"
            )

            TrackerSelectionRow(
                selectedTracker = state.selectedTracker,
                onSelectTracker = { tracker ->
                    haptics.click()
                    state = state.copy(selectedTracker = tracker)
                }
            )

            // Section C: Gesture Mapping Selection (4 Frozen Enums)
            UtilitySectionHeader(
                title = "MAPPING SELECTION",
                subtitle = "Choose gesture translation engine for in-game input"
            )

            MappingSelectionCards(
                selectedMapping = state.selectedMapping,
                onSelectMapping = { mapping ->
                    haptics.click()
                    val canonical = MappingEditorUiState.canonicalDefaultsFor(state.packageName, mapping)
                    state = state.copy(
                        selectedMapping = mapping,
                        selectedTracker = canonical.selectedTracker,
                        targetLeftX = canonical.targetLeftX,
                        targetLeftY = canonical.targetLeftY,
                        targetRightX = canonical.targetRightX,
                        targetRightY = canonical.targetRightY,
                        targetCenterX = canonical.targetCenterX,
                        targetCenterY = canonical.targetCenterY,
                        deadzoneDeg = canonical.deadzoneDeg,
                        cooldownMs = canonical.cooldownMs
                    )
                }
            )

            // Section D: Target Calibration Canvas
            UtilitySectionHeader(
                title = "TARGET CALIBRATION",
                subtitle = "Adjust normalized screen targets (0.00 to 1.00)"
            )

            UtilityCard {
                TargetPreviewCanvas(
                    orientation = state.orientation,
                    mapping = state.selectedMapping,
                    targetLeftX = state.targetLeftX,
                    targetLeftY = state.targetLeftY,
                    targetRightX = state.targetRightX,
                    targetRightY = state.targetRightY,
                    targetCenterX = state.targetCenterX,
                    targetCenterY = state.targetCenterY,
                    onTargetLeftMoved = { x, y ->
                        state = state.copy(targetLeftX = x, targetLeftY = y)
                    },
                    onTargetRightMoved = { x, y ->
                        state = state.copy(targetRightX = x, targetRightY = y)
                    },
                    onTargetCenterMoved = { x, y ->
                        state = state.copy(targetCenterX = x, targetCenterY = y)
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "FINE-TUNED COORDINATE ADJUSTMENTS",
                    style = ZorbiTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = UtilityTextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                CoordinateAdjusterRow(
                    label = "LEFT TARGET",
                    color = UtilityPrimaryAccent,
                    x = state.targetLeftX,
                    y = state.targetLeftY,
                    onXChange = { state = state.copy(targetLeftX = it) },
                    onYChange = { state = state.copy(targetLeftY = it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                CoordinateAdjusterRow(
                    label = "RIGHT TARGET",
                    color = UtilitySecondaryAccent,
                    x = state.targetRightX,
                    y = state.targetRightY,
                    onXChange = { state = state.copy(targetRightX = it) },
                    onYChange = { state = state.copy(targetRightY = it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                CoordinateAdjusterRow(
                    label = "CENTER TARGET",
                    color = ZorbiSuccess,
                    x = state.targetCenterX,
                    y = state.targetCenterY,
                    onXChange = { state = state.copy(targetCenterX = it) },
                    onYChange = { state = state.copy(targetCenterY = it) }
                )
            }

            // Section E: Parameters
            UtilitySectionHeader(
                title = "PARAMETERS",
                subtitle = "Tune thresholds and One Euro filter smoothing"
            )

            UtilityCard {
                // Group 1: Detection Thresholds
                Text(
                    text = "GESTURE DETECTION THRESHOLDS",
                    style = ZorbiTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = UtilityPrimaryAccent
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (state.selectedMapping == MappingType.STEER_BUTTONS || state.selectedMapping == MappingType.STEER_DRAG) {
                    UtilitySlider(
                        title = "STEERING DEADZONE",
                        value = state.deadzoneDeg,
                        onValueChange = { state = state.copy(deadzoneDeg = it) },
                        valueRange = 0f..25f,
                        displayFormatter = { "${it.toInt()}°" },
                        description = "Hand tilt within ±${state.deadzoneDeg.toInt()}° remains centered without firing touches."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (state.selectedMapping == MappingType.LANE_SWIPE) {
                    UtilitySlider(
                        title = "MINIMUM SWIPE SPEED",
                        value = state.swipeMinSpeed,
                        onValueChange = { state = state.copy(swipeMinSpeed = it) },
                        valueRange = 0.2f..3.0f,
                        displayFormatter = { "${"%.2f".format(it)} u/s" },
                        description = "Velocity threshold required to trigger directional swipe injection."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (state.selectedMapping == MappingType.SLASH) {
                    UtilitySlider(
                        title = "MINIMUM SLASH LENGTH",
                        value = state.slashMinLength,
                        onValueChange = { state = state.copy(slashMinLength = it) },
                        valueRange = 0.05f..0.50f,
                        displayFormatter = { "%.2f".format(it) },
                        description = "Minimum normalized distance required before triggering a slice stroke."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                UtilitySlider(
                    title = "GESTURE COOLDOWN",
                    value = state.cooldownMs.toFloat(),
                    onValueChange = { state = state.copy(cooldownMs = it.toLong()) },
                    valueRange = 50f..1000f,
                    displayFormatter = { "${it.toInt()} ms" },
                    description = "Lockout window between consecutive gesture firings."
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Group 2: One Euro Filter Smoothing
                Text(
                    text = "ONE EURO FILTER (SMOOTHING & JITTER)",
                    style = ZorbiTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = UtilityPrimaryAccent
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                UtilitySlider(
                    title = "MIN CUTOFF FREQUENCY (JITTER REDUCTION)",
                    value = state.minCutoff,
                    onValueChange = { state = state.copy(minCutoff = it) },
                    valueRange = 0.2f..5.0f,
                    displayFormatter = { "${"%.2f".format(it)} Hz" },
                    description = "Lower values reduce high-frequency trembling at rest."
                )

                Spacer(modifier = Modifier.height(10.dp))

                UtilitySlider(
                    title = "SPEED COEFFICIENT BETA (LAG REDUCTION)",
                    value = state.beta,
                    onValueChange = { state = state.copy(beta = it) },
                    valueRange = 0.001f..0.05f,
                    displayFormatter = { "%.4f".format(it) },
                    description = "Higher values increase responsiveness during fast hand movement."
                )
            }

            // Section F: Actions
            UtilitySectionHeader(
                title = "ACTIONS",
                subtitle = "Test or apply configuration to game profile"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Secondary Action: Preview Mapping
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, UtilityBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.click()
                            showPreviewDialog = true
                        },
                    color = UtilitySurfaceSec
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = UtilityPrimaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PREVIEW",
                            style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = UtilityTextPrimary
                        )
                    }
                }

                // Text/Outlined Action: Reset to Default
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, UtilityBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.click()
                            showResetConfirmDialog = true
                        },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = UtilityTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RESET DEFAULT",
                            style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = UtilityTextSecondary
                        )
                    }
                }
            }

            // Primary Action: Save Mapping (Solid professional button, no excessive glow)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !state.isSaving) { handleSave() },
                color = UtilityPrimaryAccent
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF0A0E1A),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isSaving) "SAVING PROFILE..." else "SAVE MAPPING",
                        style = ZorbiTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFF0A0E1A)
                    )
                }
            }
        }
    }

    // Modal Simulator Preview Dialog
    if (showPreviewDialog) {
        MappingPreviewDialog(
            state = state,
            onDismiss = { showPreviewDialog = false }
        )
    }

    // Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(
                    text = "Reset Mapping to Default?",
                    style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = UtilityTextPrimary
                )
            },
            text = {
                Text(
                    text = "This will restore standard target coordinates and detection parameters for ${state.displayName}.",
                    style = ZorbiTheme.typography.bodyMedium,
                    color = UtilityTextSecondary
                )
            },
            confirmButton = {
                ZorbiPrimaryButton(
                    text = "RESTORE DEFAULTS",
                    onClick = { handleResetToDefault() }
                )
            },
            dismissButton = {
                ZorbiSecondaryButton(
                    text = "CANCEL",
                    onClick = { showResetConfirmDialog = false }
                )
            },
            containerColor = UtilityCard,
            textContentColor = UtilityTextPrimary
        )
    }
}

// =============================================================================
// Clean Dashboard Card Containers & Section Headers
// =============================================================================

@Composable
private fun UtilityCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, UtilityBorder), RoundedCornerShape(12.dp)),
        color = UtilityCard
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun UtilitySectionHeader(
    title: String,
    subtitle: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        Text(
            text = title,
            style = ZorbiTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 11.sp
            ),
            color = UtilityPrimaryAccent
        )
        Text(
            text = subtitle,
            style = ZorbiTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = UtilityTextSecondary
        )
    }
}

// =============================================================================
// Section A: Clean Game Header Card
// =============================================================================

@Composable
private fun EditorGameHeaderCard(
    displayName: String,
    packageName: String,
    orientation: ProfileOrientation,
    availableProfiles: List<GameProfile>,
    onSelectProfile: (GameProfile) -> Unit,
    onReturnToLibrary: () -> Unit
) {
    UtilityCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clean game icon placeholder
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(UtilitySurfaceSec)
                    .border(1.dp, UtilityBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = UtilityPrimaryAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = ZorbiTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = UtilityTextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ZorbiBadge(
                        text = orientation.name,
                        accentColor = if (orientation == ProfileOrientation.LANDSCAPE) UtilitySecondaryAccent else UtilityPrimaryAccent
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = packageName,
                    style = ZorbiMetricTypography.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = UtilityTextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Return to Game Library",
                style = ZorbiTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = UtilityPrimaryAccent
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onReturnToLibrary() }
                    .padding(vertical = 4.dp)
            )

            Text(
                text = "Schema v1",
                style = ZorbiMetricTypography.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = UtilityTextSecondary.copy(alpha = 0.6f)
                )
            )
        }
    }
}

// =============================================================================
// Section B: Tracker Selection (Segmented / Clean Cards)
// =============================================================================

@Composable
private fun TrackerSelectionRow(
    selectedTracker: TrackerId,
    onSelectTracker: (TrackerId) -> Unit
) {
    val trackers = listOf(
        Triple(TrackerId.HAND_SINGLE, "1-Hand Air", "Single hand gestures (swipe/slash)"),
        Triple(TrackerId.HAND_DUAL_WHEEL, "2-Hand Wheel", "Two hands forming virtual wheel"),
        Triple(TrackerId.POSE_LITE, "Pose Lean", "Upper-body shoulder lean & tilt")
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        trackers.forEach { (tracker, title, desc) ->
            val isSelected = selectedTracker == tracker

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) UtilityPrimaryAccent else UtilityBorder
                        ),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectTracker(tracker) },
                color = if (isSelected) UtilitySurfaceSec else UtilityCard
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) UtilityTextPrimary else UtilityTextPrimary.copy(alpha = 0.85f)
                        )
                        Text(
                            text = desc,
                            style = ZorbiTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = UtilityTextSecondary
                        )
                    }

                    // Radio indicator
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) UtilityPrimaryAccent else Color.Transparent)
                            .border(
                                1.5.dp,
                                if (isSelected) UtilityPrimaryAccent else UtilityBorder,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF0A0E1A),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Section C: Mapping Selection (4 Clean Cards)
// =============================================================================

@Composable
private fun MappingSelectionCards(
    selectedMapping: MappingType,
    onSelectMapping: (MappingType) -> Unit
) {
    data class MappingItem(
        val type: MappingType,
        val title: String,
        val explanation: String,
        val icon: ImageVector
    )

    val mappings = listOf(
        MappingItem(
            MappingType.STEER_BUTTONS,
            "Steering Buttons",
            "Translates hand tilt into discrete left/right button taps.",
            Icons.Default.PlayArrow
        ),
        MappingItem(
            MappingType.STEER_DRAG,
            "Steering Drag",
            "Translates tilt angle into continuous horizontal touch drag.",
            Icons.Default.Refresh
        ),
        MappingItem(
            MappingType.LANE_SWIPE,
            "Lane Swipe",
            "Monitors velocity to detect rapid 4-way flick gestures.",
            Icons.Default.Share
        ),
        MappingItem(
            MappingType.SLASH,
            "Air Slash",
            "Reconstructs continuous motion trajectories into rapid screen slices.",
            Icons.Default.Edit
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        mappings.forEach { item ->
            val isSelected = selectedMapping == item.type

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) UtilityPrimaryAccent else UtilityBorder
                        ),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectMapping(item.type) },
                color = if (isSelected) UtilitySurfaceSec else UtilityCard
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) UtilityPrimaryAccent.copy(alpha = 0.15f) else UtilitySurfaceSec)
                            .border(1.dp, if (isSelected) UtilityPrimaryAccent else UtilityBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isSelected) UtilityPrimaryAccent else UtilityTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = UtilityTextPrimary
                            )
                            ZorbiBadge(
                                text = item.type.name,
                                accentColor = if (isSelected) UtilityPrimaryAccent else UtilityTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = item.explanation,
                            style = ZorbiTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = UtilityTextSecondary
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// Section D: Coordinate Adjuster Row
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoordinateAdjusterRow(
    label: String,
    color: Color,
    x: Float,
    y: Float,
    onXChange: (Float) -> Unit,
    onYChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(UtilitySurfaceSec)
            .border(1.dp, UtilityBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = ZorbiTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
            }
            Text(
                text = "x=${"%.2f".format(x)}  y=${"%.2f".format(y)}",
                style = ZorbiMetricTypography.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, color = UtilityTextPrimary)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // X Slider
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "X Axis (${(x * 100).toInt()}%)",
                    style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = UtilityTextSecondary
                )
                Slider(
                    value = x,
                    onValueChange = onXChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = color,
                        activeTrackColor = color,
                        inactiveTrackColor = UtilityBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Y Slider
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Y Axis (${(y * 100).toInt()}%)",
                    style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = UtilityTextSecondary
                )
                Slider(
                    value = y,
                    onValueChange = onYChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = color,
                        activeTrackColor = color,
                        inactiveTrackColor = UtilityBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// =============================================================================
// Clean Material 3 Utility Slider
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UtilitySlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayFormatter: (Float) -> String,
    description: String
) {
    val haptics = rememberZorbiHaptics()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = ZorbiTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = UtilityTextPrimary
            )
            Text(
                text = displayFormatter(value),
                style = ZorbiMetricTypography.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                color = UtilityPrimaryAccent
            )
        }

        Slider(
            value = value,
            onValueChange = {
                onValueChange(it)
                haptics.tick()
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = UtilityPrimaryAccent,
                activeTrackColor = UtilityPrimaryAccent,
                inactiveTrackColor = UtilityBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = description,
            style = ZorbiTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = UtilityTextSecondary
        )
    }
}
