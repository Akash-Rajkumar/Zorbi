package com.zorbi.ui.screens.calibrator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zorbi.core.AppGraph
import com.zorbi.core.Calibration
import com.zorbi.core.GameProfile
import com.zorbi.core.HandsFrame
import com.zorbi.core.PerfMode
import com.zorbi.core.PoseFrame
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
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
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import com.zorbi.ui.theme.ZorbiWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    val lifecycleOwner = LocalLifecycleOwner.current

    var profile by remember { mutableStateOf<GameProfile?>(null) }
    var tracker by remember { mutableStateOf<Tracker?>(null) }
    var uiState by remember { mutableStateOf(CalibratorUiState()) }
    val samples = remember { mutableStateListOf<TrackerFrame>() }

    var lastFrame by remember { mutableStateOf<TrackerFrame?>(null) }
    var isTrackingLocked by remember { mutableStateOf(false) }
    var calibrationSessionId by remember { mutableIntStateOf(0) }
    var isCalibrating by remember { mutableStateOf(false) }
    var savedResult by remember { mutableStateOf<Calibration?>(null) }
    var lastFrameTimestampMs by remember { mutableLongStateOf(0L) }

    fun restartCalibration() {
        samples.clear()
        lastFrameTimestampMs = 0L
        uiState = CalibratorUiState(
            phase = CalibrationPhase.DETECTING,
            secondsRemaining = 60,
            isTrackingLocked = isTrackingLocked,
            sampleCount = 0,
            result = savedResult
        )
        isCalibrating = true
        calibrationSessionId++

        val activeTracker = tracker
        if (activeTracker != null) {
            coroutineScope.launch {
                try {
                    activeTracker.start(PerfMode.BALANCED)
                } catch (e: Exception) {
                    // Ignore if already running
                }
            }
        }
    }

    // Load game profile and check for existing persisted calibration
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
            return@LaunchedEffect
        }

        // Read-back existing persisted calibration if available
        val existing = try {
            appGraph.calibration.load(packageName)
        } catch (e: Exception) {
            null
        }

        if (existing != null) {
            savedResult = existing
            uiState = uiState.copy(
                phase = CalibrationPhase.SUCCESS,
                result = existing,
                secondsRemaining = 0,
                errorMessage = null
            )
        } else {
            // No saved calibration yet: start initial guided calibration
            restartCalibration()
        }
    }

    // Lifecycle-aware tracker start and stop (rebind on ON_RESUME, unbind on ON_PAUSE/disposal)
    DisposableEffect(lifecycleOwner, tracker) {
        val activeTracker = tracker ?: return@DisposableEffect onDispose {}

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
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
                }
                Lifecycle.Event.ON_PAUSE -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            activeTracker.stop()
                        } catch (e: Exception) {
                            // Ignore on pause
                        }
                    }
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

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
            lifecycleOwner.lifecycle.removeObserver(observer)
            CoroutineScope(Dispatchers.IO).launch {
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
            lastFrameTimestampMs = System.currentTimeMillis()
            val hasLandmarks = when (frame) {
                is HandsFrame -> frame.hands.isNotEmpty() && frame.hands.first().landmarks.isNotEmpty()
                is PoseFrame -> frame.landmarks.isNotEmpty() && frame.score >= 0.5f
                else -> false
            }

            lastFrame = frame
            isTrackingLocked = hasLandmarks
            uiState = uiState.copy(
                lastFrame = frame,
                isTrackingLocked = hasLandmarks
            )

            // Accumulate samples during active calibration recording phases
            if (isCalibrating && hasLandmarks &&
                (uiState.phase == CalibrationPhase.RECORDING_NEUTRAL || uiState.phase == CalibrationPhase.RECORDING_RANGE)
            ) {
                samples.add(frame)
                uiState = uiState.copy(sampleCount = samples.size)
            }
        }
    }

    // 60-Second Guided Countdown Loop (Single session coroutine)
    LaunchedEffect(calibrationSessionId) {
        if (!isCalibrating) return@LaunchedEffect

        var consecutiveLostSeconds = 0
        var remaining = 60

        uiState = uiState.copy(
            phase = CalibrationPhase.DETECTING,
            secondsRemaining = remaining,
            sampleCount = 0,
            errorMessage = null
        )

        while (remaining > 5) {
            delay(1000L)
            remaining -= 1

            // Interruption check: during recording phases (remaining 6..50s), verify tracking
            val now = System.currentTimeMillis()
            val isCameraActive = lastFrameTimestampMs > 0L && (now - lastFrameTimestampMs) < 4000L
            val isTrackingActive = isTrackingLocked && isCameraActive

            if (remaining in 6..50) {
                if (!isTrackingActive) {
                    consecutiveLostSeconds++
                    if (consecutiveLostSeconds >= 10) {
                        uiState = uiState.copy(
                            phase = CalibrationPhase.ERROR,
                            errorMessage = if (!isCameraActive && lastFrameTimestampMs > 0L) {
                                "Camera feed stalled during calibration. Check camera permission."
                            } else {
                                "Tracking was lost for too long during calibration. Hold your hand clearly in front of the camera."
                            }
                        )
                        isCalibrating = false
                        return@LaunchedEffect
                    }
                } else {
                    consecutiveLostSeconds = 0
                }
            }

            val nextPhase = when {
                remaining > 50 -> CalibrationPhase.DETECTING
                remaining > 30 -> CalibrationPhase.RECORDING_NEUTRAL
                else -> CalibrationPhase.RECORDING_RANGE
            }

            uiState = uiState.copy(
                secondsRemaining = remaining,
                phase = nextPhase
            )
        }

        // Phase: COMPUTING (5s .. 0s)
        uiState = uiState.copy(
            secondsRemaining = remaining,
            phase = CalibrationPhase.COMPUTING
        )

        val targetTrackerId = profile?.tracker ?: TrackerId.HAND_SINGLE
        val collectedSamples = samples.toList()

        try {
            val computed = appGraph.calibration.computeFromSamples(targetTrackerId, collectedSamples)
            appGraph.calibration.save(packageName, computed)

            // Read-back verification from disk
            val persisted = appGraph.calibration.load(packageName) ?: computed
            savedResult = persisted
            uiState = uiState.copy(
                phase = CalibrationPhase.SUCCESS,
                result = persisted,
                secondsRemaining = 0,
                errorMessage = null
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            uiState = uiState.copy(
                phase = CalibrationPhase.ERROR,
                errorMessage = "Calibration calculation failed: ${e.message}"
            )
        } finally {
            isCalibrating = false
        }
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
                        level = if (isTrackingLocked) StatusLevel.SUCCESS else StatusLevel.WARNING,
                        animatePulse = isTrackingLocked
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTrackingLocked) "TRACKING LOCKED" else "SEARCHING FOR HAND...",
                        style = ZorbiMetricTypography.copy(
                            fontSize = 12.sp,
                            color = if (isTrackingLocked) ZorbiSuccess else ZorbiWarning
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
                if (savedResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ZorbiSecondaryButton(
                        text = "USE PREVIOUS CALIBRATION",
                        leadingIcon = Icons.Default.CheckCircle,
                        onClick = {
                            uiState = uiState.copy(
                                phase = CalibrationPhase.SUCCESS,
                                result = savedResult,
                                errorMessage = null
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Active 60s Countdown Ring & Instructions
                ZorbiGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (isTrackingLocked) ZorbiPrimaryCyan.copy(alpha = 0.5f) else ZorbiBorder
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

            SkeletonCanvas(
                frame = lastFrame,
                isTrackingActive = isTrackingLocked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
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
