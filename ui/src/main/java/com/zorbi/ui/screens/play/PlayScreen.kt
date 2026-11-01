package com.zorbi.ui.screens.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.AppGraph
import com.zorbi.core.EngineState
import com.zorbi.core.GameProfile
import com.zorbi.core.HudFrame
import com.zorbi.core.PerfMode
import com.zorbi.ui.components.ZorbiEngineStateBadge
import com.zorbi.ui.components.ZorbiIconButton
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.hud.GamingHudOverlay
import com.zorbi.ui.hud.HudOpacity
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import kotlinx.coroutines.launch

/**
 * In-App Gaming Session Screen for route play/{packageName}.
 * Embeds GamingHudOverlay and manages EngineController lifecycle.
 */
@Composable
fun PlayScreen(
    packageName: String,
    appGraph: AppGraph,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<GameProfile?>(null) }
    var currentFrame by remember { mutableStateOf<HudFrame?>(null) }
    val engineState by appGraph.engine.state.collectAsState(initial = EngineState.IDLE)
    val isPaused = engineState == EngineState.PAUSED

    // Load profile and start engine pipeline
    LaunchedEffect(packageName) {
        profile = appGraph.profiles.get(packageName)
        try {
            appGraph.engine.start(packageName, PerfMode.BALANCED)
        } catch (e: Exception) {
            // Handled via engine state
        }
    }

    // Stop engine on disposal
    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch {
                try {
                    appGraph.engine.stop()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    // Collect real-time HudFrame flow from EngineController
    LaunchedEffect(Unit) {
        appGraph.engine.hud.collect { frame ->
            currentFrame = frame
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF04060C)) // Deep transparent game canvas background
    ) {
        // Master Gaming HUD Overlay Layer
        GamingHudOverlay(
            frame = currentFrame,
            isPaused = isPaused,
            modifier = Modifier.fillMaxSize()
        )

        // Top Control Bar (Cleanly floating, avoids game controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZorbiIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Stop & Return",
                onClick = onNavigateBack
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.displayName ?: packageName,
                    style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiTextPrimary
                )
                Text(
                    text = "MOTION INJECTION ACTIVE",
                    style = ZorbiMetricTypography.copy(
                        fontSize = 10.sp,
                        color = ZorbiPrimaryCyan
                    )
                )
            }
            ZorbiEngineStateBadge(state = engineState)
        }

        // Bottom Gaming Control Strip
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            shape = ZorbiCardShape,
            color = Color(0xD9121830),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E2A4A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPaused) {
                    ZorbiPrimaryButton(
                        text = "RESUME ENGINE",
                        leadingIcon = Icons.Default.PlayArrow,
                        onClick = { appGraph.engine.resume() },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ZorbiSecondaryButton(
                        text = "PAUSE (FIST)",
                        onClick = { appGraph.engine.pause() },
                        modifier = Modifier.weight(1f)
                    )
                }

                ZorbiSecondaryButton(
                    text = "EXIT",
                    leadingIcon = Icons.Default.Close,
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
