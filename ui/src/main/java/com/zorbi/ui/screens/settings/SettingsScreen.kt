package com.zorbi.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.PerfMode
import com.zorbi.ui.animations.interactiveBounce
import com.zorbi.ui.components.SciFiCheckbox
import com.zorbi.ui.components.SciFiDropdown
import com.zorbi.ui.components.SciFiNeonSlider
import com.zorbi.ui.components.SciFiPillToggle
import com.zorbi.ui.components.SciFiRadioOption
import com.zorbi.ui.components.SciFiStepper
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.components.ZorbiIconButton
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.haptics.rememberZorbiHaptics
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiError
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.monoMedium
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurface
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextDisabled
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import com.zorbi.ui.theme.ZorbiWarning

enum class SettingsTab(val label: String) {
    BASIC("BASIC"),
    OPERATION("OPERATION"),
    ADVANCED("ADVANCED"),
    SCREEN("SCREEN")
}

/**
 * Premium Gaming Console Settings Screen for ZORBI.
 * Unified with the Home Screen's Electric Cyan (#00E5FF) and Neon Violet (#7C4DFF) aesthetic,
 * featuring full-width aligned cards, seamless segmented navigation tabs, and DataStore key persistence.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWizard: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
    initialSettings: ZorbiSettings? = null
) {
    val context = LocalContext.current
    val haptics = rememberZorbiHaptics()
    val storage = remember {
        try {
            SettingsStorage.fromContext(context)
        } catch (e: Exception) {
            null
        }
    }

    val initial = remember { initialSettings ?: storage?.load() ?: ZorbiSettings.DEFAULT }
    var settings by remember { mutableStateOf(initial) }

    var currentTab by remember { mutableStateOf(SettingsTab.BASIC) }
    var showTelemetryConsentDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    var musicVolume by remember { mutableFloatStateOf(initial.musicVolume) }
    var sfxVolume by remember { mutableFloatStateOf(initial.sfxVolume) }
    var hapticsEnabled by remember { mutableStateOf(initial.vibrationEnabled) }
    var tipsEnabled by remember { mutableStateOf(initial.tipsEnabled) }
    var steeringDeadzone by remember { mutableFloatStateOf(initial.steeringDeadzone) }
    var gestureSensitivity by remember { mutableFloatStateOf(initial.swipeSensitivity) }

    val languages = remember { listOf("English", "Kannada", "Japanese", "Spanish") }
    var languageIndex by remember {
        mutableStateOf(languages.indexOf(initial.language).coerceAtLeast(0))
    }
    val fpsOptions = remember { listOf("60 FPS", "90 FPS", "120 FPS", "Auto") }
    var fpsIndex by remember {
        mutableStateOf(fpsOptions.indexOf(initial.targetFps).coerceAtLeast(0))
    }
    var selectedDifficulty by remember { mutableStateOf(initial.difficulty) }
    var selectedHudColor by remember { mutableStateOf(initial.hudSkeletonColor) }
    var showHudStats by remember { mutableStateOf(true) }
    var showSteeringGauge by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    LaunchedEffect(currentTab) {
        scrollState.scrollTo(0)
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = ZorbiBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar & Top Tabs (Fixed at top)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Header Bar: Aligned with Home Screen style (Back, Cyan Bar, Title, Badge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZorbiIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = onNavigateBack
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Brush.verticalGradient(listOf(ZorbiPrimaryCyan, ZorbiAccentViolet))
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SETTINGS",
                            style = ZorbiTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp
                            ),
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ZorbiBadge(
                            text = "ZORBI v1.0",
                            accentColor = ZorbiPrimaryCyan
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ZorbiSurfaceElevated)
                                .border(1.dp, ZorbiBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    haptics.click()
                                    showResetConfirmDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Defaults",
                                tint = ZorbiTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Top Segmented Category Tabs (Full width, centered, perfectly aligned)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ZorbiSurfaceElevated)
                        .border(1.dp, ZorbiBorder, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        val tabBackground = if (isSelected) {
                            Brush.horizontalGradient(
                                listOf(
                                    ZorbiPrimaryCyan.copy(alpha = 0.25f),
                                    ZorbiAccentViolet.copy(alpha = 0.35f)
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Transparent)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tabBackground)
                                .then(
                                    if (isSelected) Modifier.border(
                                        1.5.dp,
                                        ZorbiPrimaryCyan,
                                        RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .clickable {
                                    haptics.click()
                                    currentTab = tab
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                style = ZorbiTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = if (isSelected) Color.White else ZorbiTextMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Main Content Area: Full width, vertically scrollable, weight(1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "settingsTabContent"
                ) { activeTab ->
                    when (activeTab) {
                        SettingsTab.BASIC -> {
                            BasicSettingsContent(
                                settings = settings,
                                musicVolume = musicVolume,
                                onMusicChange = { musicVolume = it },
                                sfxVolume = sfxVolume,
                                onSfxChange = { sfxVolume = it },
                                tipsEnabled = tipsEnabled,
                                onTipsToggle = { tipsEnabled = it },
                                hapticsEnabled = hapticsEnabled,
                                onHapticsToggle = { hapticsEnabled = it },
                                language = languages[languageIndex],
                                onLanguagePrevious = {
                                    languageIndex = (languageIndex - 1 + languages.size) % languages.size
                                },
                                onLanguageNext = {
                                    languageIndex = (languageIndex + 1) % languages.size
                                },
                                fpsOption = fpsOptions[fpsIndex],
                                onFpsCycle = {
                                    fpsIndex = (fpsIndex + 1) % fpsOptions.size
                                },
                                selectedDifficulty = selectedDifficulty,
                                onDifficultySelect = { selectedDifficulty = it },
                                onPerfModeSelect = { newMode ->
                                    settings = settings.copy(perfMode = newMode)
                                    storage?.savePerfMode(newMode)
                                }
                            )
                        }

                        SettingsTab.OPERATION -> {
                            OperationSettingsContent(
                                deadzone = steeringDeadzone,
                                onDeadzoneChange = { steeringDeadzone = it },
                                sensitivity = gestureSensitivity,
                                onSensitivityChange = { gestureSensitivity = it },
                                onNavigateToWizard = onNavigateToWizard
                            )
                        }

                        SettingsTab.ADVANCED -> {
                            AdvancedSettingsContent(
                                settings = settings,
                                onTelemetryToggle = { optIn ->
                                    if (optIn) {
                                        showTelemetryConsentDialog = true
                                    } else {
                                        settings = settings.copy(telemetryOptIn = false)
                                        storage?.saveTelemetryOptIn(false)
                                    }
                                },
                                onNavigateToAbout = onNavigateToAbout
                            )
                        }

                        SettingsTab.SCREEN -> {
                            ScreenSettingsContent(
                                selectedColor = selectedHudColor,
                                onColorSelect = { selectedHudColor = it },
                                showHudStats = showHudStats,
                                onToggleHudStats = { showHudStats = it },
                                showSteeringGauge = showSteeringGauge,
                                onToggleSteeringGauge = { showSteeringGauge = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Fixed Bottom Action Bar: Always anchored at bottom with navigationBarsPadding
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = ZorbiSurface,
                border = BorderStroke(1.dp, ZorbiBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZorbiSecondaryButton(
                        text = "CANCEL",
                        onClick = {
                            haptics.click()
                            onNavigateBack()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    ZorbiPrimaryButton(
                        text = "APPLY SETTINGS",
                        onClick = {
                            val updated = settings.copy(
                                musicVolume = musicVolume,
                                sfxVolume = sfxVolume,
                                vibrationEnabled = hapticsEnabled,
                                tipsEnabled = tipsEnabled,
                                steeringDeadzone = steeringDeadzone,
                                swipeSensitivity = gestureSensitivity,
                                language = languages[languageIndex],
                                targetFps = fpsOptions[fpsIndex],
                                difficulty = selectedDifficulty,
                                hudSkeletonColor = selectedHudColor
                            )
                            settings = updated
                            storage?.saveAll(updated)
                            haptics.confirm()
                            onNavigateBack()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Consent Dialog for Telemetry Opt-In
    if (showTelemetryConsentDialog) {
        AlertDialog(
            onDismissRequest = { showTelemetryConsentDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = ZorbiPrimaryCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enable Anonymous Telemetry?",
                        style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiTextPrimary
                    )
                }
            },
            text = {
                Text(
                    text = "ZORBI values your privacy. If you opt in, we collect strictly anonymous, aggregate diagnostic metrics (FPS, inference ms, session duration). Zero camera frames or video data will EVER leave your device.",
                    style = ZorbiTheme.typography.bodySmall,
                    color = ZorbiTextMuted
                )
            },
            confirmButton = {
                ZorbiPrimaryButton(
                    text = "AGREE & OPT IN",
                    onClick = {
                        settings = settings.copy(telemetryOptIn = true)
                        storage?.saveTelemetryOptIn(true)
                        showTelemetryConsentDialog = false
                    }
                )
            },
            dismissButton = {
                ZorbiSecondaryButton(
                    text = "CANCEL",
                    onClick = { showTelemetryConsentDialog = false }
                )
            },
            containerColor = ZorbiSurface,
            textContentColor = ZorbiTextPrimary
        )
    }

    // Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = ZorbiError,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset All Settings?",
                        style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiTextPrimary
                    )
                }
            },
            text = {
                Text(
                    text = "This will restore performance mode to BALANCED and disable all telemetry. Your game profiles will remain intact.",
                    style = ZorbiTheme.typography.bodyMedium,
                    color = ZorbiTextMuted
                )
            },
            confirmButton = {
                ZorbiPrimaryButton(
                    text = "RESET SETTINGS",
                    onClick = {
                        val reset = storage?.resetToDefaults() ?: ZorbiSettings.DEFAULT
                        settings = reset
                        musicVolume = reset.musicVolume
                        sfxVolume = reset.sfxVolume
                        hapticsEnabled = reset.vibrationEnabled
                        tipsEnabled = reset.tipsEnabled
                        steeringDeadzone = reset.steeringDeadzone
                        gestureSensitivity = reset.swipeSensitivity
                        languageIndex = languages.indexOf(reset.language).coerceAtLeast(0)
                        fpsIndex = fpsOptions.indexOf(reset.targetFps).coerceAtLeast(0)
                        selectedDifficulty = reset.difficulty
                        selectedHudColor = reset.hudSkeletonColor
                        showResetConfirmDialog = false
                        haptics.warning()
                    }
                )
            },
            dismissButton = {
                ZorbiSecondaryButton(
                    text = "CANCEL",
                    onClick = { showResetConfirmDialog = false }
                )
            },
            containerColor = ZorbiSurface,
            textContentColor = ZorbiTextPrimary
        )
    }
}

/**
 * Basic Tab Content: Audio, System Toggles, Language, Graphics Preset, and Difficulty.
 * Structured into clean full-width cards using the Home Screen color scheme.
 */
@Composable
private fun BasicSettingsContent(
    settings: ZorbiSettings,
    musicVolume: Float,
    onMusicChange: (Float) -> Unit,
    sfxVolume: Float,
    onSfxChange: (Float) -> Unit,
    tipsEnabled: Boolean,
    onTipsToggle: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsToggle: (Boolean) -> Unit,
    language: String,
    onLanguagePrevious: () -> Unit,
    onLanguageNext: () -> Unit,
    fpsOption: String,
    onFpsCycle: () -> Unit,
    selectedDifficulty: String,
    onDifficultySelect: (String) -> Unit,
    onPerfModeSelect: (PerfMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Card 1: Audio Controls
        SettingsCard(title = "AUDIO & SOUND EFFECTS") {
            SciFiNeonSlider(
                title = "MUSIC",
                icon = "🎵",
                value = musicVolume,
                onValueChange = onMusicChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            SciFiNeonSlider(
                title = "SFX",
                icon = "🔊",
                value = sfxVolume,
                onValueChange = onSfxChange,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Card 2: System Toggles
        SettingsCard(title = "FEEDBACK & GUIDANCE") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GAMEPLAY TIPS",
                        style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiTextPrimary
                    )
                    Text(
                        text = "Display in-game hand alignment hints and gesture suggestions",
                        style = ZorbiTheme.typography.bodySmall,
                        color = ZorbiTextMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                SciFiPillToggle(
                    isOn = tipsEnabled,
                    onToggle = onTipsToggle,
                    width = 72.dp,
                    height = 28.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TACTILE VIBRATION",
                        style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiTextPrimary
                    )
                    Text(
                        text = "Haptic pulse upon steering zero-lock, swipe injection, or fist pause",
                        style = ZorbiTheme.typography.bodySmall,
                        color = ZorbiTextMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                SciFiPillToggle(
                    isOn = hapticsEnabled,
                    onToggle = onHapticsToggle,
                    width = 72.dp,
                    height = 28.dp
                )
            }
        }

        // Card 3: Language & Refresh Rate
        SettingsCard(title = "DISPLAY & REGIONAL") {
            SciFiStepper(
                label = "INTERFACE LANGUAGE",
                value = language,
                onPrevious = onLanguagePrevious,
                onNext = onLanguageNext,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            SciFiDropdown(
                label = "TARGET REFRESH RATE",
                value = fpsOption,
                onClick = onFpsCycle,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Card 4: Performance Preset (mapped to PerfMode)
        SettingsCard(title = "PERFORMANCE MODE") {
            Text(
                text = "Balances front-camera processing resolution against thermal throttling",
                style = ZorbiTheme.typography.bodySmall,
                color = ZorbiTextMuted
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PerformanceOptionTile(
                    title = "PERF",
                    subtitle = "15 FPS Cap",
                    isSelected = settings.perfMode == PerfMode.PERFORMANCE,
                    onClick = { onPerfModeSelect(PerfMode.PERFORMANCE) },
                    modifier = Modifier.weight(1f)
                )

                PerformanceOptionTile(
                    title = "BALANCED",
                    subtitle = "60 FPS Target",
                    isSelected = settings.perfMode == PerfMode.BALANCED,
                    onClick = { onPerfModeSelect(PerfMode.BALANCED) },
                    modifier = Modifier.weight(1f)
                )

                PerformanceOptionTile(
                    title = "QUALITY",
                    subtitle = "Full Resolution",
                    isSelected = settings.perfMode == PerfMode.QUALITY,
                    onClick = { onPerfModeSelect(PerfMode.QUALITY) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Card 5: Input Filtering Difficulty
        SettingsCard(title = "GESTURE DIFFICULTY & DEADZONE") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DifficultyOptionTile(
                    title = "EASY",
                    description = "Forgiving deadzone",
                    isSelected = selectedDifficulty == "EASY",
                    onClick = { onDifficultySelect("EASY") },
                    modifier = Modifier.weight(1f)
                )

                DifficultyOptionTile(
                    title = "NORMAL",
                    description = "Standard balance",
                    isSelected = selectedDifficulty == "NORMAL",
                    onClick = { onDifficultySelect("NORMAL") },
                    modifier = Modifier.weight(1f)
                )

                DifficultyOptionTile(
                    title = "HARD",
                    description = "Pro raw precision",
                    isSelected = selectedDifficulty == "HARD",
                    onClick = { onDifficultySelect("HARD") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OperationSettingsContent(
    deadzone: Float,
    onDeadzoneChange: (Float) -> Unit,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    onNavigateToWizard: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard(title = "MOTION SENSITIVITY CALIBRATION") {
            SciFiNeonSlider(
                title = "STEERING DEADZONE",
                icon = "🎯",
                value = deadzone,
                onValueChange = onDeadzoneChange,
                valueRange = 0f..25f,
                displayFormatter = { "${it.toInt()}°" },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            SciFiNeonSlider(
                title = "SWIPE SPEED MULTIPLIER",
                icon = "⚡",
                value = sensitivity * 10f,
                onValueChange = { onSensitivityChange(it / 10f) },
                valueRange = 5f..30f,
                displayFormatter = { String.format("%.1fx", it / 10f) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard(title = "SYSTEM SETUP RE-CALIBRATION") {
            Text(
                text = "Re-run the initial hardware permission checks, Vivo autostart configuration, or front-camera alignment wizard.",
                style = ZorbiTheme.typography.bodySmall,
                color = ZorbiTextMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            ZorbiSecondaryButton(
                text = "RE-RUN SETUP WIZARD",
                onClick = onNavigateToWizard,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AdvancedSettingsContent(
    settings: ZorbiSettings,
    onTelemetryToggle: (Boolean) -> Unit,
    onNavigateToAbout: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard(title = "ANONYMOUS DIAGNOSTIC TELEMETRY") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ANONYMOUS TELEMETRY",
                        style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiTextPrimary
                    )
                    Text(
                        text = "Collects anonymous aggregate FPS & inference latency. Zero camera frames leave RAM.",
                        style = ZorbiTheme.typography.bodySmall,
                        color = ZorbiTextMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                SciFiPillToggle(
                    isOn = settings.telemetryOptIn,
                    onToggle = onTelemetryToggle,
                    width = 72.dp,
                    height = 28.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEVICE IDENTIFIER",
                    style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiTextPrimary
                )
                Text(
                    text = if (settings.deviceId.length > 8) {
                        "${settings.deviceId.take(8)}...${settings.deviceId.takeLast(4)}"
                    } else {
                        settings.deviceId.ifEmpty { "Generated on launch" }
                    },
                    style = ZorbiMetricTypography.monoMedium.copy(fontSize = 12.sp),
                    color = ZorbiPrimaryCyan
                )
            }
        }

        SettingsCard(title = "ABOUT ZORBI APPLICATION") {
            Text(
                text = "Learn about the on-device MediaPipe vision engine, open JSON profile specifications, and zero-telemetry architecture.",
                style = ZorbiTheme.typography.bodySmall,
                color = ZorbiTextMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            ZorbiSecondaryButton(
                text = "ABOUT ZORBI & PRIVACY",
                onClick = onNavigateToAbout,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ScreenSettingsContent(
    selectedColor: String,
    onColorSelect: (String) -> Unit,
    showHudStats: Boolean,
    onToggleHudStats: (Boolean) -> Unit,
    showSteeringGauge: Boolean,
    onToggleSteeringGauge: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsCard(title = "HUD SKELETON DISPLAY COLOR") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectableChip(
                    title = "CYAN NEON",
                    isSelected = selectedColor == "CYAN NEON",
                    onClick = { onColorSelect("CYAN NEON") },
                    modifier = Modifier.weight(1f)
                )
                SelectableChip(
                    title = "VIOLET",
                    isSelected = selectedColor == "VIOLET",
                    onClick = { onColorSelect("VIOLET") },
                    modifier = Modifier.weight(1f)
                )
                SelectableChip(
                    title = "MINIMAL",
                    isSelected = selectedColor == "MINIMAL",
                    onClick = { onColorSelect("MINIMAL") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SettingsCard(title = "IN-GAME OVERLAYS") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "REAL-TIME FPS & LATENCY HUD",
                        style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiTextPrimary
                    )
                    Text(
                        text = "Displays live frame capture FPS, inference latency, and tracking state",
                        style = ZorbiTheme.typography.bodySmall,
                        color = ZorbiTextMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                SciFiPillToggle(
                    isOn = showHudStats,
                    onToggle = onToggleHudStats,
                    width = 72.dp,
                    height = 28.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STEERING ANGLE GAUGE",
                        style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiTextPrimary
                    )
                    Text(
                        text = "Shows dynamic cockpit tachometer arc with deadzone zero-lock feedback",
                        style = ZorbiTheme.typography.bodySmall,
                        color = ZorbiTextMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                SciFiPillToggle(
                    isOn = showSteeringGauge,
                    onToggle = onToggleSteeringGauge,
                    width = 72.dp,
                    height = 28.dp
                )
            }
        }
    }
}

/**
 * Reusable Settings Section Card with Home Screen surface styling and cyan indicator bar.
 */
@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, ZorbiBorder), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = ZorbiSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ZorbiPrimaryCyan)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = ZorbiTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = ZorbiPrimaryCyan
                )
            }

            content()
        }
    }
}

@Composable
private fun PerformanceOptionTile(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberZorbiHaptics()
    Surface(
        modifier = modifier
            .interactiveBounce(enabled = true)
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.5.dp,
                if (isSelected) ZorbiPrimaryCyan else ZorbiBorder,
                RoundedCornerShape(10.dp)
            )
            .background(
                if (isSelected) ZorbiPrimaryCyan.copy(alpha = 0.12f) else ZorbiSurfaceElevated
            )
            .clickable {
                haptics.click()
                onClick()
            },
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = ZorbiTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                ),
                color = if (isSelected) Color.White else ZorbiTextMuted,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (isSelected) ZorbiPrimaryCyan else ZorbiTextDisabled,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DifficultyOptionTile(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberZorbiHaptics()
    Surface(
        modifier = modifier
            .interactiveBounce(enabled = true)
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.5.dp,
                if (isSelected) ZorbiAccentViolet else ZorbiBorder,
                RoundedCornerShape(10.dp)
            )
            .background(
                if (isSelected) ZorbiAccentViolet.copy(alpha = 0.15f) else ZorbiSurfaceElevated
            )
            .clickable {
                haptics.click()
                onClick()
            },
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = ZorbiTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                ),
                color = if (isSelected) Color.White else ZorbiTextMuted,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = ZorbiTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (isSelected) ZorbiAccentViolet else ZorbiTextDisabled,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SelectableChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (isSelected) ZorbiPrimaryCyan else ZorbiBorder,
                RoundedCornerShape(8.dp)
            )
            .background(
                if (isSelected) ZorbiPrimaryCyan.copy(alpha = 0.15f) else ZorbiSurfaceElevated
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = ZorbiTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) Color.White else ZorbiTextMuted
            )
        }
    }
}
