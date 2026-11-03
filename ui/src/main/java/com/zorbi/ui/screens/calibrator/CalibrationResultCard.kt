package com.zorbi.ui.screens.calibrator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.Calibration
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.components.ZorbiGlassCard
import com.zorbi.ui.components.ZorbiMetricCard
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme

/**
 * Result Card displaying calculated motion calibration metrics.
 * Per Master Prompt Section 5.1 (Calibration data contract).
 */
@Composable
fun CalibrationResultCard(
    calibration: Calibration,
    packageName: String,
    onSaveAndPlay: () -> Unit,
    onRecalibrate: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZorbiGlassCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = ZorbiSuccess
    ) {
        // Success Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = ZorbiSuccess,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Calibration Complete",
                    style = ZorbiTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiSuccess
                )
                Text(
                    text = "Profile tuned for $packageName",
                    style = ZorbiTheme.typography.bodySmall,
                    color = ZorbiTextMuted
                )
            }
            ZorbiBadge(
                text = "${calibration.dominantHand.name} HAND",
                accentColor = ZorbiAccentViolet
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ZorbiMetricCard(
                label = "Neutral Angle",
                value = String.format("%.1f", calibration.neutralAngleDeg),
                unit = "°",
                accentColor = ZorbiPrimaryCyan,
                modifier = Modifier.weight(1f)
            )
            ZorbiMetricCard(
                label = "Max Lock Angle",
                value = String.format("%.1f", calibration.maxAngleDeg),
                unit = "°",
                accentColor = ZorbiAccentViolet,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ZorbiMetricCard(
                label = "Reach Span X",
                value = String.format("%.2f", calibration.reachScaleX),
                accentColor = ZorbiPrimaryCyan,
                modifier = Modifier.weight(1f)
            )
            ZorbiMetricCard(
                label = "Reach Span Y",
                value = String.format("%.2f", calibration.reachScaleY),
                accentColor = ZorbiAccentViolet,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step-by-step Game Operation Guide Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1424), RoundedCornerShape(12.dp))
                .border(1.dp, ZorbiPrimaryCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = ZorbiPrimaryCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HOW TO PLAY WITH MOTION GESTURES",
                    style = ZorbiMetricTypography.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZorbiPrimaryCyan
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Motion Action Rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GestureGuidePill(
                    icon = "⬆️",
                    action = "JUMP",
                    flick = "Flick Hand UP",
                    modifier = Modifier.weight(1f)
                )
                GestureGuidePill(
                    icon = "⬇️",
                    action = "ROLL",
                    flick = "Flick Hand DOWN",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GestureGuidePill(
                    icon = "⬅️",
                    action = "LEFT",
                    flick = "Flick Hand LEFT",
                    modifier = Modifier.weight(1f)
                )
                GestureGuidePill(
                    icon = "➡️",
                    action = "RIGHT",
                    flick = "Flick Hand RIGHT",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "💡 Keep phone upright on a table. Wave hand 1 to 2 feet in front of camera. The floating pill [ 🎮 ZORBI ACTIVE ] at the top flashes every time a swipe is triggered!",
                style = ZorbiTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = ZorbiTextMuted
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        ZorbiPrimaryButton(
            text = "SAVE & LAUNCH GAME",
            leadingIcon = Icons.Default.PlayArrow,
            onClick = onSaveAndPlay,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        ZorbiSecondaryButton(
            text = "RECALIBRATE",
            leadingIcon = Icons.Default.Refresh,
            onClick = onRecalibrate,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GestureGuidePill(
    icon: String,
    action: String,
    flick: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0xFF070B14), RoundedCornerShape(8.dp))
            .border(1.dp, ZorbiBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = action,
                style = ZorbiMetricTypography.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZorbiTextPrimary
                )
            )
            Text(
                text = flick,
                style = ZorbiTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = ZorbiPrimaryCyan
            )
        }
    }
}
