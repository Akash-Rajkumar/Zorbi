package com.zorbi.ui.screens.calibrator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.AppGraph
import com.zorbi.core.Calibration
import com.zorbi.core.GameProfile
import com.zorbi.core.HandsFrame
import com.zorbi.core.PerfMode
import com.zorbi.core.PoseFrame
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.ui.components.HandTrackingStatus
import com.zorbi.ui.components.HandTrackingStatusChip
import com.zorbi.ui.components.StatusLevel
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.components.ZorbiErrorCard
import com.zorbi.ui.components.ZorbiGlassCard
import com.zorbi.ui.components.ZorbiOutlinedButton
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiScaffold
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.components.ZorbiSectionHeader
import com.zorbi.ui.components.ZorbiStatusChip
import com.zorbi.ui.components.ZorbiStatusDot
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import com.zorbi.ui.theme.ZorbiWarning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * P3-3 Calibration Screen:
 * - 60-second guided flow with countdown ring
 * - Live skeleton canvas driven by actual TrackerFrame data
 * - Real-time tracking status indicator
 * - Connection to CalibrationRepository (computeFromSamples, save)
 * - Result card with computed angles and reach bounds
 * - Lifecycle-aware tracker start and stop
 */
@Composable
fun CalibrateScreen(
    packageName: String,
    appGraph: AppGraph,
    onNavigateBack: () -> Unit,
    onNavigateToPlay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<GameProfile?>(null) }
    var tracker by remember { mutableStateOf<Tracker?>(null) }
    var uiState by remember { mutableStateOf(CalibratorUiState()) }
    val samples = remember { mutableStateListOf<TrackerFrame>() }
    var showDebugBones by remember { mutableStateOf(false) }
    var currentTrackingStatus by remember { mutableStateOf(HandTrackingStatus.NOT_CONNECTED) }

    // Load game profile to identify active tracker
    LaunchedEffect(packageName) {
        val loaded = appGraph.profiles.get(packageName)
        profile = loaded
        val targetTrackerId = loaded?.tracker ?: TrackerId.HAND_SINGLE
        try {
            tracker = appGraph.trackers.get(targetTrackerId)
        } catch (e: Exception) {
            uiState = uiState.copy(
                phase = CalibrationPhase.ERROR,
                errorMessage = "Failed to load tracker: ${e.message}"
            )
        }
    }

    // Start/Stop tracker flow lifecycle
    DisposableEffect(tracker) {
        val activeTracker = tracker ?: return@DisposableEffect onDispose {}
        coroutineScope.launch {
            try {
                activeTracker.start(PerfMode.BALANCED)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    phase = CalibrationPhase.ERROR,
                    errorMessage = "Camera error: ${e.message}"
                )
            }
        }
        onDispose {
            coroutineScope.launch {
                try {
                    activeTracker.stop()
                } catch (e: Exception) {
                    // Ignore on disposal
                }
            }
        }
    }

    // Collect live TrackerFrames
    LaunchedEffect(tracker) {
        tracker?.frames?.collect { frame ->
            val hasLandmarks = when (frame) {
                is HandsFrame -> frame.hands.isNotEmpty() && frame.hands.first().landmarks.isNotEmpty()
                is PoseFrame -> frame.landmarks.isNotEmpty() && frame.score >= 0.5f
                else -> false
            }

            uiState = uiState.copy(
                lastFrame = frame,
                isTrackingLocked = hasLandmarks
            )

            // Accumulate samples during active calibration phases
            if (hasLandmarks && (uiState.phase == CalibrationPhase.RECORDING_NEUTRAL || uiState.phase == CalibrationPhase.RECORDING_RANGE)) {
                samples.add(frame)
                uiState = uiState.copy(sampleCount = samples.size)
            }
        }
    }

    // 60-Second Guided Countdown Loop
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == CalibrationPhase.READY) {
            uiState = uiState.copy(phase = CalibrationPhase.DETECTING, secondsRemaining = 60)
        }

        if (uiState.phase in listOf(
                CalibrationPhase.DETECTING,
                CalibrationPhase.RECORDING_NEUTRAL,
                CalibrationPhase.RECORDING_RANGE
            )
        ) {
            while (uiState.secondsRemaining > 0) {
                delay(1000L)
                val remaining = uiState.secondsRemaining - 1

                // Phase Transitions across 60 seconds
                val nextPhase = when {
                    remaining > 50 -> CalibrationPhase.DETECTING          // 0 - 10s: Alignment
                    remaining > 30 -> CalibrationPhase.RECORDING_NEUTRAL   // 10 - 30s: Neutral posture
                    remaining > 5 -> CalibrationPhase.RECORDING_RANGE      // 30 - 55s: Range of motion
                    else -> CalibrationPhase.COMPUTING                    // 55 - 60s: Calculating
                }

                uiState = uiState.copy(
                    secondsRemaining = remaining,
                    phase = nextPhase
                )

                if (nextPhase == CalibrationPhase.COMPUTING) {
                    break
                }
            }

            // Compute calibration from collected samples
            if (uiState.phase == CalibrationPhase.COMPUTING) {
                val targetTrackerId = profile?.tracker ?: TrackerId.HAND_SINGLE
                try {
                    val computed = appGraph.calibration.computeFromSamples(targetTrackerId, samples.toList())
                    appGraph.calibration.save(packageName, computed)
                    uiState = uiState.copy(
                        phase = CalibrationPhase.SUCCESS,
                        result = computed
                    )
                } catch (e: Exception) {
                    uiState = uiState.copy(
                        phase = CalibrationPhase.ERROR,
                        errorMessage = "Calibration calculation failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun restartCalibration() {
        samples.clear()
        uiState = CalibratorUiState(
            phase = CalibrationPhase.DETECTING,
            secondsRemaining = 60,
            isTrackingLocked = false,
            sampleCount = 0
        )
    }

    ZorbiScaffold(
        title = "Motion Calibrator",
        subtitle = profile?.displayName ?: packageName,
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        actions = {
            ZorbiBadge(
                text = "${uiState.sampleCount} SAMPLES",
                accentColor = ZorbiPrimaryCyan
            )
        },
        modifier = modifier
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Tracking Status Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZorbiStatusDot(
                        level = if (uiState.isTrackingLocked) StatusLevel.SUCCESS else StatusLevel.WARNING,
                        animatePulse = uiState.isTrackingLocked
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isTrackingLocked) "TRACKING LOCKED" else "SEARCHING FOR HAND...",
                        style = ZorbiMetricTypography.copy(
                            fontSize = 12.sp,
                            color = if (uiState.isTrackingLocked) ZorbiSuccess else ZorbiWarning
                        )
                    )
                }

                Text(
                    text = "${profile?.tracker ?: TrackerId.HAND_SINGLE}",
                    style = ZorbiMetricTypography.copy(
                        fontSize = 11.sp,
                        color = ZorbiTextMuted
                    )
                )
            }

            // Central Area: Countdown Ring during active calibration, or Result Card on success
            if (uiState.phase == CalibrationPhase.SUCCESS && uiState.result != null) {
                CalibrationResultCard(
                    calibration = uiState.result!!,
                    packageName = profile?.displayName ?: packageName,
                    onSaveAndPlay = { onNavigateToPlay(packageName) },
                    onRecalibrate = { restartCalibration() }
                )
            } else if (uiState.phase == CalibrationPhase.ERROR) {
                ZorbiErrorCard(
                    title = "Calibration Interrupted",
                    message = uiState.errorMessage ?: "Tracking was lost for too long during calibration.",
                    fixButtonText = "Restart 60s Calibration",
                    onFixClick = { restartCalibration() }
                )
            } else {
                // Active 60s Countdown Ring & Instructions
                ZorbiGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (uiState.isTrackingLocked) ZorbiPrimaryCyan.copy(alpha = 0.5f) else ZorbiBorder
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CountdownRing(
                            secondsRemaining = uiState.secondsRemaining,
                            totalSeconds = 60,
                            currentPhaseTitle = uiState.phase.title
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = uiState.phase.title,
                            style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ZorbiTextPrimary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = uiState.phase.instruction,
                            style = ZorbiTheme.typography.bodyMedium,
                            color = ZorbiTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Live Neon Skeleton Canvas
            ZorbiSectionHeader(
                title = "Live Vision Feed",
                subtitle = "Mirrored front camera landmarks (on-device only)"
            )

            // Live Tracking Indicator Controls (Status Chip & Debug Bones Toggle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tracking Status Chip with upstream confidence
                val confidence = when (val f = uiState.lastFrame) {
                    is HandsFrame -> f.hands.firstOrNull()?.score
                    is PoseFrame -> f.score
                    else -> null
                }
                HandTrackingStatusChip(
                    status = currentTrackingStatus,
                    confidence = confidence
                )

                // Debug Bones Toggle
                Surface(
                    modifier = Modifier.clickable { showDebugBones = !showDebugBones },
                    color = if (showDebugBones) ZorbiAccentViolet.copy(alpha = 0.2f) else ZorbiSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (showDebugBones) ZorbiAccentViolet else ZorbiBorder
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ZorbiStatusDot(
                            level = if (showDebugBones) StatusLevel.SUCCESS else StatusLevel.NEUTRAL,
                            animatePulse = false
                        )
                        Text(
                            text = if (showDebugBones) "DEBUG BONES" else "FINGERTIP ONLY",
                            style = ZorbiMetricTypography.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (showDebugBones) ZorbiAccentViolet else ZorbiTextMuted
                            )
                        )
                    }
                }
            }

            SkeletonCanvas(
                frame = uiState.lastFrame,
                isTrackingActive = tracker != null,
                showFingertipMarker = true,
                showFadingTrail = true,
                showDebugBones = showDebugBones,
                showStatusOverlay = true,
                onStatusChange = { currentTrackingStatus = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            )

            // Cancel button
            if (uiState.phase != CalibrationPhase.SUCCESS) {
                ZorbiOutlinedButton(
                    text = "CANCEL CALIBRATION",
                    leadingIcon = Icons.Default.Close,
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
