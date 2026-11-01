package com.zorbi.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zorbi.core.PerfMode
import com.zorbi.ui.theme.ZorbiTheme

@Preview(name = "Settings - Phone - Default Balanced", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewSettingsPhoneBalanced() {
    ZorbiTheme {
        SettingsScreen(
            onNavigateBack = {},
            initialSettings = ZorbiSettings(
                perfMode = PerfMode.BALANCED,
                telemetryOptIn = false,
                deviceId = "d3b07384-d113-46fb-a0b2-113f99a5c812",
                wizardDone = true,
                lastGame = "com.kiloo.subwaysurf"
            )
        )
    }
}

@Preview(name = "Settings - Phone - Quality Opted In", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewSettingsPhoneQualityOptedIn() {
    ZorbiTheme {
        SettingsScreen(
            onNavigateBack = {},
            initialSettings = ZorbiSettings(
                perfMode = PerfMode.QUALITY,
                telemetryOptIn = true,
                deviceId = "e5a1b2c3-4d5e-6f7a-8b9c-0d1e2f3a4b5c",
                wizardDone = true,
                lastGame = "com.fingersoft.hillclimb"
            )
        )
    }
}

@Preview(name = "Settings - Tablet - Landscape", showBackground = true, widthDp = 1024, heightDp = 600)
@Composable
fun PreviewSettingsTablet() {
    ZorbiTheme {
        SettingsScreen(
            onNavigateBack = {},
            initialSettings = ZorbiSettings(
                perfMode = PerfMode.PERFORMANCE,
                telemetryOptIn = false,
                deviceId = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
                wizardDone = false,
                lastGame = ""
            )
        )
    }
}
