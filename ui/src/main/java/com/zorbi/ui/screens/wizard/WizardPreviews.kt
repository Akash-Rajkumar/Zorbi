package com.zorbi.ui.screens.wizard

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zorbi.ui.screens.home.PreviewAppGraph
import com.zorbi.ui.theme.ZorbiTheme

@Preview(name = "Setup Wizard - Incomplete (Phone 360dp)", widthDp = 360, showBackground = true)
@Composable
fun PreviewWizardIncomplete() {
    ZorbiTheme {
        WizardScreen(
            appGraph = PreviewAppGraph(),
            onNavigateBack = {},
            onFinish = {}
        )
    }
}

@Preview(name = "Setup Wizard - Tablet (720dp)", widthDp = 720, showBackground = true)
@Composable
fun PreviewWizardTablet() {
    ZorbiTheme {
        WizardScreen(
            appGraph = PreviewAppGraph(),
            onNavigateBack = {},
            onFinish = {}
        )
    }
}

@Preview(name = "Restricted Settings Dialog", widthDp = 360, showBackground = true)
@Composable
fun PreviewRestrictedSettingsDialog() {
    ZorbiTheme {
        RestrictedSettingsDialog(onDismiss = {})
    }
}

@Preview(name = "Vivo Guidance Dialog", widthDp = 360, showBackground = true)
@Composable
fun PreviewVivoGuidanceDialog() {
    ZorbiTheme {
        VivoGuidanceDialog(onDismiss = {})
    }
}
