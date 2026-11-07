package com.zorbi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zorbi.core.EngineState
import com.zorbi.core.MappingType
import com.zorbi.core.TrackerId
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme

/**
 * Preview showcase of all Zorbi design foundation components.
 * Satisfies Test Gate T-P3-0:
 * "All component states render in dark theme on small and large widths".
 */
@Composable
fun ZorbiComponentKitShowcase() {
    ZorbiTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ZorbiBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Section Headers
            ZorbiSectionHeader(
                title = "UI Component Kit",
                subtitle = "Dark-only premium gaming design tokens"
            )

            // Section 2: Buttons in all states
            ZorbiSectionHeader(title = "Interactive Buttons (min 48dp)")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZorbiPrimaryButton(
                    text = "START CALIBRATION (ACTIVE)",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
                ZorbiPrimaryButton(
                    text = "PROCESSING FRAMES...",
                    onClick = {},
                    loading = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ZorbiPrimaryButton(
                    text = "PERMISSION REQUIRED (DISABLED)",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
                ZorbiSecondaryButton(
                    text = "BROWSE COMMUNITY PROFILES",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
                ZorbiSecondaryButton(
                    text = "CONNECTING...",
                    onClick = {},
                    loading = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ZorbiOutlinedButton(
                        text = "Reset Defaults",
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                    ZorbiOutlinedButton(
                        text = "Advanced",
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Section 3: Elevated Glass Cards & Metrics
            ZorbiSectionHeader(title = "Glass Cards & Diagnostic Metrics")
            ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Gesture Pipeline Engine",
                    style = ZorbiTheme.typography.titleMedium,
                    color = ZorbiTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "On-device MediaPipe Hand Landmarker with One Euro smoothing and Accessibility gesture injection.",
                    style = ZorbiTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZorbiMetricCard(
                        label = "Inference",
                        value = "18.4",
                        unit = "ms",
                        modifier = Modifier.weight(1f)
                    )
                    ZorbiMetricCard(
                        label = "E2E Latency",
                        value = "52",
                        unit = "ms",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Section 4: Status Indicators & Diagnostics
            ZorbiSectionHeader(title = "Status Indicators & Chips")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZorbiStatusChip(label = "Camera", isGranted = true)
                ZorbiStatusChip(label = "Accessibility", isGranted = true)
                ZorbiStatusChip(label = "Battery", isGranted = false)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZorbiFpsChip(fps = 24.5f, latencyMs = 58.2f)
                ZorbiEngineStateBadge(state = EngineState.RUNNING)
                ZorbiEngineStateBadge(state = EngineState.PAUSED)
            }

            // Section 5: Badges & Tags
            ZorbiSectionHeader(title = "Profile Badges & Filter Chips")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZorbiTrackerBadge(trackerId = TrackerId.HAND_SINGLE)
                ZorbiTrackerBadge(trackerId = TrackerId.HAND_DUAL_WHEEL)
                ZorbiTrackerBadge(trackerId = TrackerId.POSE_LITE)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZorbiMappingBadge(mapping = MappingType.LANE_SWIPE)
                ZorbiMappingBadge(mapping = MappingType.SLASH)
                ZorbiMappingBadge(mapping = MappingType.STEER_BUTTONS)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZorbiFilterChip(label = "All Games", selected = true, onClick = {}, count = 8)
                ZorbiFilterChip(label = "Runners", selected = false, onClick = {}, count = 3)
                ZorbiFilterChip(label = "Racing", selected = false, onClick = {}, count = 2)
            }

            // Section 6: States (Loading, Empty, Error)
            ZorbiSectionHeader(title = "Feedback & Error States")
            ZorbiLoadingIndicator(message = "Calibrating neutral wrist angle...")
            ZorbiErrorCard(
                title = "Accessibility Permission Restricted",
                message = "Android restricted settings require explicit user authorization before Zorbi can dispatch gesture inputs.",
                onFixClick = {},
                modifier = Modifier.fillMaxWidth()
            )
            ZorbiEmptyState(
                title = "No Custom Profiles Yet",
                description = "Download tested profiles from the Supabase community library or calibrate your own.",
                actionButton = {
                    ZorbiSecondaryButton(
                        text = "Explore Community",
                        onClick = {}
                    )
                }
            )
        }
    }
}

@Preview(name = "Small Screen (Phone - 360dp)", widthDp = 360, showBackground = true)
@Composable
fun PreviewSmallWidth() {
    ZorbiComponentKitShowcase()
}

@Preview(name = "Large Screen (Tablet/Foldable - 720dp)", widthDp = 720, showBackground = true)
@Composable
fun PreviewLargeWidth() {
    ZorbiComponentKitShowcase()
}
