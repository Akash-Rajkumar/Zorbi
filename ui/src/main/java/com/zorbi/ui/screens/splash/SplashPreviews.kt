package com.zorbi.ui.screens.splash

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zorbi.ui.theme.ZorbiTheme

@Preview(name = "Splash Screen - Phone Portrait", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun PreviewSplashScreenPhone() {
    ZorbiTheme {
        SplashScreen(
            onTransitionToNext = {}
        )
    }
}
