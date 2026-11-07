package com.zorbi.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zorbi.ui.screens.home.PreviewAppGraph
import com.zorbi.ui.theme.ZorbiTheme

@Preview(name = "Game Library - Phone (360dp)", widthDp = 360, showBackground = true)
@Composable
fun PreviewGameLibraryPhone() {
    ZorbiTheme {
        GameLibraryScreen(
            appGraph = PreviewAppGraph(),
            onNavigateBack = {},
            onNavigateToCalibrate = {},
            onNavigateToPlay = {},
            onNavigateToCommunity = {}
        )
    }
}

@Preview(name = "Game Library - Empty State (360dp)", widthDp = 360, showBackground = true)
@Composable
fun PreviewGameLibraryEmpty() {
    ZorbiTheme {
        GameLibraryScreen(
            appGraph = PreviewAppGraph(initialProfiles = emptyList()),
            onNavigateBack = {},
            onNavigateToCalibrate = {},
            onNavigateToPlay = {},
            onNavigateToCommunity = {}
        )
    }
}

@Preview(name = "Game Library - Tablet (720dp)", widthDp = 720, showBackground = true)
@Composable
fun PreviewGameLibraryTablet() {
    ZorbiTheme {
        GameLibraryScreen(
            appGraph = PreviewAppGraph(),
            onNavigateBack = {},
            onNavigateToCalibrate = {},
            onNavigateToPlay = {},
            onNavigateToCommunity = {}
        )
    }
}
