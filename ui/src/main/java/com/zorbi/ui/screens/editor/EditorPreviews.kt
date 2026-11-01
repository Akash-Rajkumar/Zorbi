package com.zorbi.ui.screens.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.TrackerId
import com.zorbi.ui.screens.home.PreviewAppGraph
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiTheme

@Preview(name = "Mapping Editor - Portrait", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun MappingEditorPortraitPreview() {
    ZorbiTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = ZorbiBackground) {
            MappingEditorScreen(
                packageName = "com.kiloo.subwaysurf",
                appGraph = PreviewAppGraph(),
                onNavigateBack = {},
                onNavigateToLibrary = {}
            )
        }
    }
}

@Preview(name = "Target Canvas Preview", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
fun TargetPreviewCanvasPreview() {
    ZorbiTheme {
        Surface(color = ZorbiBackground) {
            TargetPreviewCanvas(
                orientation = ProfileOrientation.PORTRAIT,
                mapping = MappingType.LANE_SWIPE,
                targetLeftX = 0.20f,
                targetLeftY = 0.50f,
                targetRightX = 0.80f,
                targetRightY = 0.50f,
                targetCenterX = 0.50f,
                targetCenterY = 0.50f,
                onTargetLeftMoved = { _, _ -> },
                onTargetRightMoved = { _, _ -> },
                onTargetCenterMoved = { _, _ -> }
            )
        }
    }
}

@Preview(name = "Simulator Preview Dialog", showBackground = true, widthDp = 380, heightDp = 600)
@Composable
fun MappingPreviewDialogPreview() {
    ZorbiTheme {
        MappingPreviewDialog(
            state = MappingEditorUiState.canonicalDefaultsFor(
                packageName = "com.fingersoft.hillclimb",
                mapping = MappingType.STEER_BUTTONS
            ),
            onDismiss = {}
        )
    }
}
