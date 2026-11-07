package com.zorbi.ui.screens.placeholders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zorbi.ui.components.ZorbiGlassCard
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiScaffold
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme

@Composable
fun WizardPlaceholderScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZorbiScaffold(
        title = "Setup Wizard",
        subtitle = "P3-2 Target",
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Setup Wizard (P3-2)",
                    style = ZorbiTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiPrimaryCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Step-by-step permissions flow: Accessibility with 'Allow restricted settings' help, Camera, Notifications, and Vivo/OriginOS battery autostart guidance.",
                    style = ZorbiTheme.typography.bodyMedium,
                    color = ZorbiTextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                ZorbiPrimaryButton(
                    text = "RETURN TO HOME",
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CalibratePlaceholderScreen(
    packageName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZorbiScaffold(
        title = "Calibrator",
        subtitle = packageName,
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Calibrator UI (P3-3)",
                    style = ZorbiTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiPrimaryCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "60-second guided flow, live skeleton landmark canvas, countdown ring, and calibration result card for $packageName.",
                    style = ZorbiTheme.typography.bodyMedium,
                    color = ZorbiTextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                ZorbiSecondaryButton(
                    text = "CANCEL CALIBRATION",
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PlayPlaceholderScreen(
    packageName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZorbiScaffold(
        title = "Motion Controller Active",
        subtitle = packageName,
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "HUD Overlay & Injection (P3-4)",
                    style = ZorbiTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiPrimaryCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Live front camera analysis active for $packageName. Accessibility touches are being dispatched over the game interface with neon skeleton HUD.",
                    style = ZorbiTheme.typography.bodyMedium,
                    color = ZorbiTextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                ZorbiPrimaryButton(
                    text = "STOP ENGINE & RETURN",
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CommunityPlaceholderScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZorbiScaffold(
        title = "Community Profiles",
        subtitle = "P3-5 Target",
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Supabase Community Library",
                    style = ZorbiTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiPrimaryCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Browse, search, and import verified game motion profiles from the community cloud repository (R1/R2/R3 PostgREST routes).",
                    style = ZorbiTheme.typography.bodyMedium,
                    color = ZorbiTextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                ZorbiSecondaryButton(
                    text = "BACK TO LIBRARY",
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SettingsPlaceholderScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZorbiScaffold(
        title = "Settings",
        subtitle = "P3-5 Target",
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "App & Engine Settings",
                    style = ZorbiTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiPrimaryCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Configure Performance Mode (QUALITY, BALANCED, PERFORMANCE), anonymous telemetry opt-in toggle, and DataStore key resets.",
                    style = ZorbiTheme.typography.bodyMedium,
                    color = ZorbiTextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                ZorbiPrimaryButton(
                    text = "SAVE & EXIT",
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun AboutPlaceholderScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZorbiScaffold(
        title = "About Zorbi",
        subtitle = "Version 1.0 (Kodikon)",
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "ZORBI",
                    style = ZorbiTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                    color = ZorbiPrimaryCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Turning front cameras into game motion controllers. All vision runs 100% on-device. No camera frames ever leave your device.",
                    style = ZorbiTheme.typography.bodyMedium,
                    color = ZorbiTextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                ZorbiSecondaryButton(
                    text = "CLOSE",
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
