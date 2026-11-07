package com.zorbi.ui.screens.calibrator

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.Tracker
import com.zorbi.core.TrackerFrame
import com.zorbi.core.TrackerId
import com.zorbi.core.ZorbiGestureConfig
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
 * - Dynamic sensor landscape adaptation for landscape games (Hill Climb Racing, Bus Simulator)
 * - Live 3D vision feed with dual-hand steering wheel & pedals
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
    val context = LocalContext.current
    val activity = context as? Activity

    var profile by remember { mutableStateOf<GameProfile?>(null) }
    var tracker by remember { mutableStateOf<Tracker?>(null) }
    var uiState by remember { mutableStateOf(CalibratorUiState()) }
    val samples = remember { mutableStateListOf<TrackerFrame>() }
    var showDebugBones by remember { mutableStateOf(false) }
    var currentTrackingStatus by remember { mutableStateOf(HandTrackingStatus.NOT_CONNECTED) }
    var isLandscape3DView by remember { mutableStateOf(false) }
    var selectedGestureOption by remember { mutableStateOf("STEER") }
    var calibrationTrigger by remember { mutableIntStateOf(0) }
    var handMode by remember { mutableStateOf(ZorbiGestureConfig.handMode) }
    var fistAction by remember { mutableStateOf(ZorbiGestureConfig.fistAction) }
    var pinchAction by remember { mutableStateOf(ZorbiGestureConfig.pinchAction) }
    var steerLayout by remember { mutableStateOf(ZorbiGestureConfig.steerLayout) }

    // Helper for vocal coaching
    fun speakVoice(text: String) {
        com.zorbi.ui.util.ZorbiVoiceCoach.speak(text)
    }

    val guide = remember(packageName, profile) {
        com.zorbi.ui.util.GameGestureAdvisor.getGuide(packageName, profile)
    }

    // Auto-detect Landscape vs Portrait and set default modes
    LaunchedEffect(packageName) {
        val loaded = appGraph.profiles.get(packageName)
        profile = loaded
        ZorbiGestureConfig.adaptForGame(packageName)
        handMode = ZorbiGestureConfig.handMode
        fistAction = ZorbiGestureConfig.fistAction
        pinchAction = ZorbiGestureConfig.pinchAction
        steerLayout = ZorbiGestureConfig.steerLayout

        if (loaded?.orientation == ProfileOrientation.LANDSCAPE) {
            isLandscape3DView = true
        }
        if (loaded?.packageName == "com.maleo.bussimulatorindonesia" || loaded?.packageName == "com.maleo.bussimulatorid" || loaded?.packageName == "com.ansangha.drdriving") {
            selectedGestureOption = "GEAR"
            isLandscape3DView = true
        } else if (loaded?.tracker == TrackerId.HAND_DUAL_WHEEL || loaded?.orientation == ProfileOrientation.LANDSCAPE) {
            selectedGestureOption = "STEER"
            isLandscape3DView = true
        } else {
            selectedGestureOption = "SWIPE"
        }
        val targetTrackerId = if (handMode == ZorbiGestureConfig.HandMode.DUAL_HAND) {
            TrackerId.HAND_DUAL_WHEEL
        } else {
            TrackerId.HAND_SINGLE
        }
        try {
            tracker = appGraph.trackers.get(targetTrackerId)
        } catch (e: Exception) {
            uiState = uiState.copy(
                phase = CalibrationPhase.ERROR,
                errorMessage = "Failed to load tracker: ${e.message}"
            )
        }
    }

    // Adapt screen orientation to landscape when in landscape view
    DisposableEffect(isLandscape3DView) {
        if (isLandscape3DView) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
            if (hasLandmarks && (uiState.phase == CalibrationPhase.DETECTING || uiState.phase == CalibrationPhase.RECORDING_NEUTRAL || uiState.phase == CalibrationPhase.RECORDING_RANGE)) {
                samples.add(frame)
                uiState = uiState.copy(sampleCount = samples.size)
            }
        }
    }

    // 60-Second Guided Countdown Loop
    LaunchedEffect(calibrationTrigger) {
        samples.clear()
        uiState = uiState.copy(
            phase = CalibrationPhase.DETECTING,
            secondsRemaining = 60,
            sampleCount = 0,
            errorMessage = null
        )

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

    fun restartCalibration() {
        calibrationTrigger++
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
                    text = "${tracker?.id ?: profile?.tracker ?: TrackerId.HAND_SINGLE}",
                    style = ZorbiMetricTypography.copy(
                        fontSize = 11.sp,
                        color = ZorbiTextMuted
                    )
                )
            }

            // Identified Game Header Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0x990A0E1A),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ZorbiPrimaryCyan.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "IDENTIFIED: ${guide.gameTitle.uppercase()}",
                            style = ZorbiMetricTypography.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZorbiPrimaryCyan
                            )
                        )
                        Text(
                            text = guide.orientationLabel,
                            style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = ZorbiTextMuted
                        )
                    }

                    Surface(
                        modifier = Modifier.clickable {
                            speakVoice(guide.spokenVoiceScript)
                        },
                        color = ZorbiPrimaryCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ZorbiPrimaryCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "🔊", fontSize = 11.sp)
                            Text(
                                text = "VOICE GUIDE",
                                style = ZorbiMetricTypography.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ZorbiPrimaryCyan
                                )
                            )
                        }
                    }
                }
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
                    title = "Calibration Stopped",
                    message = uiState.errorMessage ?: "We could not see your hand clearly. Please try again in good light.",
                    fixButtonText = "Try Again",
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

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.clickable {
                                val script = when (uiState.phase) {
                                    CalibrationPhase.READY -> "Put your phone upright on a table facing you."
                                    CalibrationPhase.DETECTING -> "Show your open hand in front of the front camera."
                                    CalibrationPhase.RECORDING_NEUTRAL -> "Hand detected! Hold your hand still in the center of the screen."
                                    CalibrationPhase.RECORDING_RANGE -> "Great! Now slowly move your hand left, right, up, and down."
                                    CalibrationPhase.COMPUTING -> "Saving your motion controls. Almost done!"
                                    CalibrationPhase.SUCCESS -> "Calibration complete! Flick your hand up to jump, down to roll, and left or right to move."
                                    CalibrationPhase.ERROR -> "We lost sight of your hand. Please try again with good lighting."
                                }
                                speakVoice(script)
                            },
                            color = ZorbiPrimaryCyan.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ZorbiPrimaryCyan.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "🔊", fontSize = 13.sp)
                                Text(
                                    text = "VOICE COACH ACTIVE • TAP TO REPLAY",
                                    style = ZorbiMetricTypography.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ZorbiPrimaryCyan
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Hand Tracking Mode Selector (2-Hand Mode vs Single Hand Mode)
            ZorbiSectionHeader(
                title = "Hand Tracking Mode",
                subtitle = "Choose between 2-hand wheel or single-hand gesture control"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isDual = handMode == ZorbiGestureConfig.HandMode.DUAL_HAND
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            handMode = ZorbiGestureConfig.HandMode.DUAL_HAND
                            ZorbiGestureConfig.handMode = ZorbiGestureConfig.HandMode.DUAL_HAND
                            speakVoice("Two-hand steering wheel mode active. Push right hand forward for gas, left hand forward for brake.")
                            try {
                                tracker = appGraph.trackers.get(TrackerId.HAND_DUAL_WHEEL)
                            } catch (_: Exception) {}
                        },
                    color = if (isDual) ZorbiPrimaryCyan.copy(alpha = 0.22f) else ZorbiSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isDual) ZorbiPrimaryCyan else ZorbiBorder
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🙌 2-HAND MODE",
                            style = ZorbiMetricTypography.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDual) ZorbiPrimaryCyan else ZorbiTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Steering Wheel + Depth Pedals",
                            style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (isDual) ZorbiPrimaryCyan.copy(alpha = 0.85f) else ZorbiTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                val isSingle = handMode == ZorbiGestureConfig.HandMode.SINGLE_HAND
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            handMode = ZorbiGestureConfig.HandMode.SINGLE_HAND
                            ZorbiGestureConfig.handMode = ZorbiGestureConfig.HandMode.SINGLE_HAND
                            speakVoice("Single hand motion mode active. Tilt to steer or swipe to navigate.")
                            try {
                                tracker = appGraph.trackers.get(TrackerId.HAND_SINGLE)
                            } catch (_: Exception) {}
                        },
                    color = if (isSingle) ZorbiAccentViolet.copy(alpha = 0.22f) else ZorbiSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isSingle) ZorbiAccentViolet else ZorbiBorder
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "✋ SINGLE HAND",
                            style = ZorbiMetricTypography.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSingle) ZorbiAccentViolet else ZorbiTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "One Hand Swipes & Air Tilt",
                            style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (isSingle) ZorbiAccentViolet.copy(alpha = 0.85f) else ZorbiTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Steering Position Identification & Game Layout Adaptation
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0x990A0E1A),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ZorbiBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📍 IN-GAME STEERING POSITION",
                            style = ZorbiMetricTypography.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZorbiPrimaryCyan
                            )
                        )
                        Text(
                            text = "LATENCY: <3ms • ACCURACY: HIGH",
                            style = ZorbiMetricTypography.copy(fontSize = 9.sp, color = ZorbiSuccess)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when (steerLayout) {
                            ZorbiGestureConfig.SteerLayout.LEFT_WHEEL_RIGHT_PEDALS ->
                                "Identified: Virtual Steering Wheel on Left (0.18, 0.70) • Brake Pedal (0.76, 0.84) • Gas Pedal (0.90, 0.84) • Horn (0.18, 0.70)"
                            ZorbiGestureConfig.SteerLayout.SIDE_BUTTONS ->
                                "Identified: Touch Buttons on Screen Edges (Left 0.12, Right 0.88) • Forward Reach Pedals"
                            ZorbiGestureConfig.SteerLayout.CENTER_DRAG ->
                                "Identified: Center Drag Steering Wheel (0.50, 0.50)"
                        },
                        style = ZorbiTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        color = ZorbiTextMuted
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            ZorbiGestureConfig.SteerLayout.LEFT_WHEEL_RIGHT_PEDALS to "🎯 LEFT WHEEL",
                            ZorbiGestureConfig.SteerLayout.SIDE_BUTTONS to "🏎️ SIDE BUTTONS",
                            ZorbiGestureConfig.SteerLayout.CENTER_DRAG to "🎛️ CENTER WHEEL"
                        ).forEach { (layout, label) ->
                            val isSel = steerLayout == layout
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        steerLayout = layout
                                        ZorbiGestureConfig.steerLayout = layout
                                        speakVoice("Steering layout adapted to $label")
                                    },
                                color = if (isSel) ZorbiPrimaryCyan.copy(alpha = 0.2f) else ZorbiSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSel) ZorbiPrimaryCyan else ZorbiBorder
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = ZorbiMetricTypography.copy(
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) ZorbiPrimaryCyan else ZorbiTextMuted
                                    ),
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Gesture Customization Section (Fist Hold for Horn, Pinch for Headlight)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0x990A0E1A),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ZorbiBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚙️ CUSTOMIZE GESTURE SHORTCUTS",
                        style = ZorbiMetricTypography.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZorbiPrimaryCyan
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 1. Fist Hold Customization
                    Text(
                        text = "✊ FIST HOLD (>= 1s):",
                        style = ZorbiMetricTypography.copy(fontSize = 10.sp, color = ZorbiTextPrimary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ZorbiGestureConfig.FistAction.values().forEach { action ->
                            val isSel = fistAction == action
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        fistAction = action
                                        ZorbiGestureConfig.fistAction = action
                                        speakVoice("Fist hold mapped to ${action.label}")
                                    },
                                color = if (isSel) Color(0x33FFB300) else ZorbiSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSel) Color(0xFFFFB300) else ZorbiBorder
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${action.icon} ${action.name}",
                                    style = ZorbiMetricTypography.copy(
                                        fontSize = 9.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color(0xFFFFB300) else ZorbiTextMuted
                                    ),
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Pinch Customization
                    Text(
                        text = "🤏 PINCH (THUMB + INDEX):",
                        style = ZorbiMetricTypography.copy(fontSize = 10.sp, color = ZorbiTextPrimary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ZorbiGestureConfig.PinchAction.values().forEach { action ->
                            val isSel = pinchAction == action
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        pinchAction = action
                                        ZorbiGestureConfig.pinchAction = action
                                        speakVoice("Pinch gesture mapped to ${action.label}")
                                    },
                                color = if (isSel) ZorbiPrimaryCyan.copy(alpha = 0.22f) else ZorbiSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSel) ZorbiPrimaryCyan else ZorbiBorder
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${action.icon} ${action.name}",
                                    style = ZorbiMetricTypography.copy(
                                        fontSize = 9.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) ZorbiPrimaryCyan else ZorbiTextMuted
                                    ),
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Live 3D Vision Feed & Gesture Calibration Options
            ZorbiSectionHeader(
                title = "Live 3D Vision Feed",
                subtitle = "On-device MediaPipe 3D holographic hand tracking"
            )

            // Gesture Mode Options (Steering, Gear Changing, Swipe)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("STEER", "🏎️ STEER & PEDALS", "Hold hands like steering wheel to steer. Push right hand forward for gas, push left hand forward for brake, and pinch to pause."),
                    Triple("GEAR", "🕹️ GEAR SHIFT", "Gear changing mode: Quick flick up to shift into Drive, and quick flick down for Reverse."),
                    Triple("SWIPE", "⚡ SWIPE", "Swipe controls: Flick up to jump, flick down to roll, and flick left or right to change lanes.")
                ).forEach { (mode, label, script) ->
                    val isSelected = selectedGestureOption == mode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedGestureOption = mode
                                speakVoice(script)
                                val neededTracker = if (mode == "STEER" || mode == "GEAR") {
                                    TrackerId.HAND_DUAL_WHEEL
                                } else {
                                    TrackerId.HAND_SINGLE
                                }
                                if (tracker?.id != neededTracker) {
                                    try {
                                        tracker = appGraph.trackers.get(neededTracker)
                                    } catch (_: Exception) {}
                                }
                            },
                        color = if (isSelected) ZorbiPrimaryCyan.copy(alpha = 0.2f) else ZorbiSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) ZorbiPrimaryCyan else ZorbiBorder
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = label,
                            style = ZorbiMetricTypography.copy(
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ZorbiPrimaryCyan else ZorbiTextMuted
                            ),
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Live Tracking Indicator Controls (Status Chip, Landscape Toggle, Debug Bones Toggle)
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

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Landscape / Portrait 3D Toggle
                    Surface(
                        modifier = Modifier.clickable {
                            isLandscape3DView = !isLandscape3DView
                            val prompt = if (isLandscape3DView) {
                                "Landscape 3D cockpit feed active. Wide view for dual hand driving."
                            } else {
                                "Portrait 3D view active."
                            }
                            speakVoice(prompt)
                        },
                        color = if (isLandscape3DView) Color(0x3300FF88) else ZorbiSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isLandscape3DView) Color(0xFF00FF88) else ZorbiBorder
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isLandscape3DView) "🔄 3D LANDSCAPE" else "📱 3D PORTRAIT",
                            style = ZorbiMetricTypography.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLandscape3DView) Color(0xFF00FF88) else ZorbiTextMuted
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ZorbiStatusDot(
                                level = if (showDebugBones) StatusLevel.SUCCESS else StatusLevel.NEUTRAL,
                                animatePulse = false
                            )
                            Text(
                                text = if (showDebugBones) "3D MESH" else "CYBER BONES",
                                style = ZorbiMetricTypography.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (showDebugBones) ZorbiAccentViolet else ZorbiTextMuted
                                )
                            )
                        }
                    }
                }
            }

            // Mode Action Explanation Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0x990A0E1A),
                border = androidx.compose.foundation.BorderStroke(1.dp, ZorbiBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = when (selectedGestureOption) {
                        "STEER" -> "🏎️ DUAL-HAND: Push RIGHT hand forward = GAS • Push LEFT hand forward = BRAKE • Tilt together to STEER"
                        "GEAR" -> "🕹️ BUS SIMULATOR: Flick RIGHT hand UP = DRIVE (D) • Flick DOWN = REVERSE (R) • Hold neutral = N"
                        else -> "⚡ SWIPE: Flick UP = JUMP • Flick DOWN = SLIDE • Flick LEFT / RIGHT = SWITCH LANES"
                    },
                    style = ZorbiMetricTypography.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ZorbiPrimaryCyan
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    textAlign = TextAlign.Center
                )
            }

            SkeletonCanvas(
                frame = uiState.lastFrame,
                isTrackingActive = tracker != null,
                showFingertipMarker = false,
                showFadingTrail = true,
                showDebugBones = showDebugBones,
                showStatusOverlay = true,
                isLandscapeMode = isLandscape3DView,
                activeGestureMode = selectedGestureOption,
                onStatusChange = { currentTrackingStatus = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape3DView) 215.dp else 280.dp)
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
