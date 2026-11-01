package com.zorbi.ui.screens.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zorbi.ui.theme.ZorbiTheme

@Preview(name = "About - Phone - Dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewAboutPhone() {
    ZorbiTheme {
        AboutScreen(
            onNavigateBack = {},
            onNavigateToWizard = {}
        )
    }
}

@Preview(name = "About - Tablet - Dark", showBackground = true, widthDp = 1024, heightDp = 600)
@Composable
fun PreviewAboutTablet() {
    ZorbiTheme {
        AboutScreen(
            onNavigateBack = {},
            onNavigateToWizard = {}
        )
    }
}
