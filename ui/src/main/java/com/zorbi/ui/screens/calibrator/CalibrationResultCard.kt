package com.zorbi.ui.screens.calibrator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zorbi.core.Calibration
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.components.ZorbiGlassCard
import com.zorbi.ui.components.ZorbiMetricCard
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
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
