package com.zorbi.ui.screens.calibrator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zorbi.core.Calibration
import com.zorbi.core.Hand
import com.zorbi.core.HandsFrame
import com.zorbi.core.Pt
import com.zorbi.core.Side
import com.zorbi.ui.screens.home.PreviewAppGraph
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiTheme

fun sampleHandFrame(): HandsFrame {
    // Synthetic 21 normalized landmarks (wrist to finger tips)
    val pts = listOf(
        Pt(0.5f, 0.8f),   // 0: Wrist
        Pt(0.42f, 0.72f), // 1: Thumb CMC
        Pt(0.36f, 0.65f), // 2: Thumb MCP
        Pt(0.32f, 0.58f), // 3: Thumb IP
        Pt(0.28f, 0.52f), // 4: Thumb Tip
        Pt(0.44f, 0.55f), // 5: Index MCP
        Pt(0.42f, 0.45f), // 6: Index PIP
        Pt(0.40f, 0.38f), // 7: Index DIP
        Pt(0.38f, 0.30f), // 8: Index Tip
        Pt(0.50f, 0.53f), // 9: Middle MCP
        Pt(0.50f, 0.42f), // 10: Middle PIP
        Pt(0.50f, 0.33f), // 11: Middle DIP
        Pt(0.50f, 0.25f), // 12: Middle Tip
        Pt(0.56f, 0.55f), // 13: Ring MCP
        Pt(0.58f, 0.45f), // 14: Ring PIP
        Pt(0.60f, 0.38f), // 15: Ring DIP
        Pt(0.62f, 0.32f), // 16: Ring Tip
        Pt(0.62f, 0.60f), // 17: Pinky MCP
        Pt(0.66f, 0.52f), // 18: Pinky PIP
        Pt(0.70f, 0.46f), // 19: Pinky DIP
        Pt(0.72f, 0.40f)  // 20: Pinky Tip
    )
    return HandsFrame(
        tsNanos = System.nanoTime(),
        hands = listOf(Hand(Side.RIGHT, 0.95f, pts))
    )
}

@Preview(name = "Calibrator Active - Phone (360dp)", widthDp = 360, showBackground = true)
@Composable
fun PreviewCalibratorPhone() {
    ZorbiTheme {
        CalibrateScreen(
            packageName = "com.kiloo.subwaysurf",
            appGraph = PreviewAppGraph(),
            onNavigateBack = {},
            onNavigateToPlay = {}
        )
    }
}

@Preview(name = "Calibrator Active - Tablet (720dp)", widthDp = 720, showBackground = true)
@Composable
fun PreviewCalibratorTablet() {
    ZorbiTheme {
        CalibrateScreen(
            packageName = "com.gameloft.android.ANMP.GloftA9HM",
            appGraph = PreviewAppGraph(),
            onNavigateBack = {},
            onNavigateToPlay = {}
        )
    }
}

@Preview(name = "Skeleton Canvas with Hand", widthDp = 360, heightDp = 260)
@Composable
fun PreviewSkeletonCanvas() {
    ZorbiTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ZorbiBackground)
                .padding(16.dp)
        ) {
            SkeletonCanvas(
                frame = sampleHandFrame(),
                isTrackingActive = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(name = "Calibration Result Card", widthDp = 360, showBackground = true)
@Composable
fun PreviewCalibrationResultCard() {
    ZorbiTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZorbiBackground)
                .padding(16.dp)
        ) {
            CalibrationResultCard(
                calibration = Calibration(
                    neutralAngleDeg = 0.5f,
                    maxAngleDeg = 36.2f,
                    reachScaleX = 0.82f,
                    reachScaleY = 0.79f,
                    dominantHand = Side.RIGHT,
                    createdAtEpochMs = System.currentTimeMillis()
                ),
                packageName = "Subway Surfers",
                onSaveAndPlay = {},
                onRecalibrate = {}
            )
        }
    }
}
