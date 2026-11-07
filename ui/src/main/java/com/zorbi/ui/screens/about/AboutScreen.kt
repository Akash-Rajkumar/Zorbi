package com.zorbi.ui.screens.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.components.ZorbiGlassCard
import com.zorbi.ui.components.ZorbiOutlinedButton
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiScaffold
import com.zorbi.ui.components.ZorbiSectionHeader
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.monoMedium
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurface
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextDisabled
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import com.zorbi.ui.theme.ZorbiWarning

/**
 * About Screen for ZORBI.
 * Displays authoritative project background, problem statement (Master Prompt Appendix A),
 * on-device privacy guarantees, architectural specs, and team credits.
 */
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWizard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    ZorbiScaffold(
        title = "About Zorbi",
        subtitle = "Version 1.0 (Kodikon Edition)",
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Brand Card
            HeroBrandCard()

            // Problem Statement & Mission (Appendix A)
            ZorbiSectionHeader(
                title = "MISSION & PROBLEM STATEMENT",
                subtitle = "Breaking the mobile touch barrier"
            )

            ProblemStatementCard()

            // 100% On-Device Privacy Guarantee
            ZorbiSectionHeader(
                title = "ON-DEVICE PRIVACY GUARANTEE",
                subtitle = "Strict zero-cloud vision architecture"
            )

            PrivacyGuaranteeCard()

            // Core Technical Highlights
            ZorbiSectionHeader(
                title = "ENGINEERING HIGHLIGHTS",
                subtitle = "Sub-30ms touchless mobile motion stack"
            )

            EngineeringHighlightsCard()

            // Team Credits
            ZorbiSectionHeader(
                title = "PROJECT TEAM CREDITS",
                subtitle = "Kodikon Hackathon Team"
            )

            TeamCreditsCard()

            // Return to App CTA
            Spacer(modifier = Modifier.height(8.dp))
            ZorbiPrimaryButton(
                text = "RETURN TO APPLICATION",
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeroBrandCard() {
    ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ZORBI",
                    style = ZorbiTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = ZorbiPrimaryCyan
                )
                Text(
                    text = "Touchless Mobile Motion Controller",
                    style = ZorbiTheme.typography.titleSmall,
                    color = ZorbiTextPrimary
                )
            }

            ZorbiBadge(
                text = "v1.0 Kodikon",
                accentColor = ZorbiAccentViolet
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Transforming budget and flagship Android front-facing cameras into responsive, touchless game controllers through on-device MediaPipe landmark tracking and native Accessibility gesture injection.",
            style = ZorbiTheme.typography.bodyMedium,
            color = ZorbiTextMuted
        )
    }
}

@Composable
private fun ProblemStatementCard() {
    ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "The Touch Gaming Barrier",
            style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = ZorbiPrimaryCyan
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Mobile touch gaming creates an artificial barrier. Screen controls obscure visual gameplay, induce thumb fatigue, and restrict players with motor or dexterity impairments.\n\n" +
                    "ZORBI transforms any budget or flagship Android device's front-facing camera into an ultra-low-latency, contactless motion controller. Powered by on-device MediaPipe landmark detection and native AccessibilityService touch injection, ZORBI enables touchless steering, slashing, swiping, and tap gestures without modifying game binaries or requiring external peripherals.\n\n" +
                    "With a strict 100% on-device vision pipeline, zero camera frames or video data ever leave the device. Designed specifically for low-end chipsets, ZORBI delivers responsive gameplay through sub-30ms end-to-end latency, a 60-second personalized calibration sequence, and an open JSON game profile specification.",
            style = ZorbiTheme.typography.bodySmall.copy(lineHeight = 20.sp),
            color = ZorbiTextPrimary
        )
    }
}

@Composable
private fun PrivacyGuaranteeCard() {
    Surface(
        shape = ZorbiCardShape,
        color = ZorbiSuccess.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, ZorbiSuccess.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = ZorbiSuccess,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "100% On-Device Computer Vision",
                    style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiSuccess
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• Camera processing occurs entirely in local RAM via MediaPipe Tasks Vision CPU/GPU delegates.\n" +
                        "• Not a single pixel or camera frame is saved to disk or transmitted over the network.\n" +
                        "• Telemetry is strictly opt-in, disabled by default, and limited to anonymous aggregate metrics (FPS, latency ms).\n" +
                        "• Full gesture control operates completely offline.",
                style = ZorbiTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = ZorbiTextPrimary
            )
        }
    }
}

@Composable
private fun EngineeringHighlightsCard() {
    ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
        HighlightRow(
            icon = Icons.Default.PlayArrow,
            title = "Zero-Binary Game Compatibility",
            desc = "Dispatches native Accessibility gestures over untouched Play Store titles."
        )

        HorizontalDivider(
            color = ZorbiBorder,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        HighlightRow(
            icon = Icons.Default.Refresh,
            title = "Sub-30ms End-to-End Latency",
            desc = "Optimized One-Euro adaptive filter smoothing for instant steering response."
        )

        HorizontalDivider(
            color = ZorbiBorder,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        HighlightRow(
            icon = Icons.Default.Share,
            title = "Open JSON Game Profiles",
            desc = "Community-driven touch target layouts and sensitivity deadzones."
        )
    }
}

@Composable
private fun HighlightRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ZorbiPrimaryCyan.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ZorbiPrimaryCyan,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = ZorbiTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = ZorbiTheme.typography.bodySmall,
                color = ZorbiTextMuted
            )
        }
    }
}

@Composable
private fun TeamCreditsCard() {
    ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
        TeamMemberItem(
            role = "Person 1 — Vision & Calibration",
            modules = ":vision, :calib",
            responsibility = "MediaPipe Hand/Pose landmarker models, CameraX pipeline, and 60-second arm-reach calibration algorithms."
        )

        HorizontalDivider(
            color = ZorbiBorder,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        TeamMemberItem(
            role = "Person 2 — Gesture, Input & Engine",
            modules = ":gesture, :input, :engine",
            responsibility = "One-Euro filter smoothing, steering interpreters, AccessibilityService stroke injection, and engine controller."
        )

        HorizontalDivider(
            color = ZorbiBorder,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        TeamMemberItem(
            role = "Person 3 — App Shell, UI & Setup Wizard",
            modules = ":app, :ui",
            responsibility = "Dark gaming design tokens, Setup Wizard, Calibrator UI, Gaming HUD overlay, Community, Settings, and navigation."
        )

        HorizontalDivider(
            color = ZorbiBorder,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        TeamMemberItem(
            role = "Person 4 — Backend, Data, Tools & CI",
            modules = ":data, :supabase, CI",
            responsibility = "Supabase PostgREST cloud repository, DataStore persistence, telemetry sink, and web dashboard."
        )
    }
}

@Composable
private fun TeamMemberItem(
    role: String,
    modules: String,
    responsibility: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = role,
                style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = ZorbiPrimaryCyan
            )
            Text(
                text = modules,
                style = ZorbiMetricTypography.monoMedium.copy(fontSize = 11.sp),
                color = ZorbiAccentViolet
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = responsibility,
            style = ZorbiTheme.typography.bodySmall,
            color = ZorbiTextMuted
        )
    }
}
