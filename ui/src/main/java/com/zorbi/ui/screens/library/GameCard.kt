package com.zorbi.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.GameProfile
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.components.ZorbiGlassCard
import com.zorbi.ui.components.ZorbiMappingBadge
import com.zorbi.ui.components.ZorbiOutlinedButton
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.components.ZorbiTrackerBadge
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme

/**
 * Reusable Game Card displaying GameProfile metadata, badges, and quick action triggers.
 */
@Composable
fun GameCard(
    profile: GameProfile,
    onPlayClick: () -> Unit,
    onCalibrateClick: () -> Unit,
    onEditClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ZorbiGlassCard(
        modifier = modifier.fillMaxWidth()
    ) {
        // Title & Package Details
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = ZorbiTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = profile.packageName,
                    style = ZorbiMetricTypography.copy(
                        fontSize = 12.sp,
                        color = ZorbiTextMuted
                    )
                )
            }
            ZorbiBadge(
                text = profile.orientation.name,
                accentColor = ZorbiAccentViolet
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Profile capabilities badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ZorbiTrackerBadge(trackerId = profile.tracker)
            ZorbiMappingBadge(mapping = profile.mapping)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ZorbiPrimaryButton(
                text = "PLAY",
                leadingIcon = Icons.Default.PlayArrow,
                onClick = onPlayClick,
                modifier = Modifier.weight(1.2f)
            )
            ZorbiSecondaryButton(
                text = "CALIB",
                leadingIcon = Icons.Default.Build,
                onClick = onCalibrateClick,
                modifier = Modifier.weight(1.1f)
            )
            ZorbiOutlinedButton(
                text = "MAP",
                leadingIcon = Icons.Default.Edit,
                onClick = onEditClick,
                modifier = Modifier.weight(1.0f)
            )
        }
    }
}
