package com.zorbi.ui.screens.community

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zorbi.core.AppGraph
import com.zorbi.core.CalibrationRepository
import com.zorbi.core.EngineController
import com.zorbi.core.InputInjector
import com.zorbi.core.ProfileRepository
import com.zorbi.core.TelemetrySink
import com.zorbi.core.TrackerRegistry
import com.zorbi.ui.screens.home.PreviewAppGraph
import com.zorbi.ui.theme.ZorbiTheme

@Preview(name = "Community - Phone - Dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewCommunityPhone() {
    val fakeGraph = PreviewAppGraph()
    ZorbiTheme {
        CommunityScreen(
            appGraph = fakeGraph,
            onNavigateBack = {},
            onNavigateToLibrary = {}
        )
    }
}

@Preview(name = "Community - Tablet - Dark", showBackground = true, widthDp = 1024, heightDp = 600)
@Composable
fun PreviewCommunityTablet() {
    val fakeGraph = PreviewAppGraph()
    ZorbiTheme {
        CommunityScreen(
            appGraph = fakeGraph,
            onNavigateBack = {},
            onNavigateToLibrary = {}
        )
    }
}
