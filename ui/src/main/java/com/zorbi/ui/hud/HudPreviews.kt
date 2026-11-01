package com.zorbi.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zorbi.core.HudFrame
import com.zorbi.core.Pt
import com.zorbi.core.Steer
import com.zorbi.core.Swipe
import com.zorbi.core.Dir
import com.zorbi.core.TrackerId
import com.zorbi.ui.screens.calibrator.sampleHandFrame
import com.zorbi.ui.screens.home.PreviewAppGraph
import com.zorbi.ui.screens.play.PlayScreen
import com.zorbi.ui.theme.ZorbiTheme

fun sampleHudFrame(isSteering: Boolean = false): HudFrame {
    val hand = sampleHandFrame().hands.first()
    val gesture = if (isSteering) {
        Steer(tsNanos = System.nanoTime(), value = 0.65f, angleDeg = 24.5f)
    } else {
        Swipe(tsNanos = System.nanoTime(), dir = Dir.RIGHT, speed = 2.4f)
    }

    return HudFrame(
        tsNanos = System.nanoTime(),
        trackerId = if (isSteering) TrackerId.HAND_DUAL_WHEEL else TrackerId.HAND_SINGLE,
        landmarks = hand.landmarks,
        lastGesture = gesture,
        fps = 24.8f,
        e2eLatencyMs = 52.4f
    )
}

@Preview(name = "Gaming HUD Overlay - Phone (360dp)", widthDp = 360, heightDp = 640)
@Composable
fun PreviewGamingHudPhone() {
    ZorbiTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF04060C))
        ) {
            GamingHudOverlay(
                frame = sampleHudFrame(isSteering = false),
                isPaused = false
            )
        }
    }
}

@Preview(name = "Gaming HUD Overlay - Steering (Tablet 720dp)", widthDp = 720, heightDp = 480)
@Composable
fun PreviewGamingHudSteeringTablet() {
    ZorbiTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF04060C))
        ) {
            GamingHudOverlay(
                frame = sampleHudFrame(isSteering = true),
                isPaused = false
            )
        }
    }
}

@Preview(name = "Pause Badge Indicator", widthDp = 360, showBackground = true)
@Composable
fun PreviewPauseBadge() {
    ZorbiTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0E1A))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            PauseIndicatorBadge(isPaused = true)
        }
    }
}

@Preview(name = "Steering Gauge Preview", widthDp = 200, heightDp = 160)
@Composable
fun PreviewSteeringGauge() {
    ZorbiTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0E1A)),
            contentAlignment = Alignment.Center
        ) {
            SteeringGauge(
                steerValue = 0.55f,
                angleDeg = 22.0f
            )
        }
    }
}
