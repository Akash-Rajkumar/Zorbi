package com.zorbi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zorbi.ui.animations.gamingShimmer
import com.zorbi.ui.animations.pulsingBorder
import com.zorbi.ui.animations.pulsingGlow
import com.zorbi.ui.screens.calibrator.CountdownRing
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme

@Preview(name = "Polish - Pulsing Glow & Border", showBackground = true, widthDp = 390, heightDp = 600)
@Composable
fun PreviewPolishGlowAndBorder() {
    ZorbiTheme {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ZorbiGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pulsingGlow(color = ZorbiPrimaryCyan, minAlpha = 0.05f, maxAlpha = 0.25f)
            ) {
                Text(
                    text = "Hero Card with Neon Glow",
                    style = ZorbiTheme.typography.titleMedium,
                    color = ZorbiPrimaryCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Subtle ambient breathing cyan glow behind the card.",
                    style = ZorbiTheme.typography.bodySmall,
                    color = ZorbiTextMuted
                )
            }

            ZorbiGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .pulsingBorder(color = ZorbiAccentViolet, borderWidth = 1.5.dp)
            ) {
                Text(
                    text = "Card with Pulsing Violet Border",
                    style = ZorbiTheme.typography.titleMedium,
                    color = ZorbiAccentViolet
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Active border animation highlighting calibrator or wizard focus.",
                    style = ZorbiTheme.typography.bodySmall,
                    color = ZorbiTextMuted
                )
            }

            ZorbiGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .gamingShimmer(highlightColor = ZorbiPrimaryCyan.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "High-Tech Gaming Shimmer",
                    style = ZorbiTheme.typography.titleMedium,
                    color = ZorbiTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Smooth diagonal shimmer across loading elements.",
                    style = ZorbiTheme.typography.bodySmall,
                    color = ZorbiTextMuted
                )
            }

            ZorbiPrimaryButton(
                text = "PLAY NOW (HAPTIC READY)",
                onClick = {},
                leadingIcon = Icons.Default.PlayArrow,
                modifier = Modifier.fillMaxWidth()
            )

            ZorbiSecondaryButton(
                text = "RESET CALIBRATION",
                onClick = {},
                leadingIcon = Icons.Default.Refresh,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
