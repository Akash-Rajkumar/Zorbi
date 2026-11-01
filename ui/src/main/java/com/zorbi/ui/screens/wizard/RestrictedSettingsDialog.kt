package com.zorbi.ui.screens.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSurface
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import com.zorbi.ui.theme.ZorbiWarning

/**
 * Explains how to unlock Android 13+ "Restricted Settings" for sideloaded APKs.
 * Enforces Master Prompt Section 8 / P3-2 & Section 16 Risk Mitigation.
 */
@Composable
fun RestrictedSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZorbiCardShape),
            shape = ZorbiCardShape,
            color = ZorbiSurface,
            border = BorderStroke(1.dp, ZorbiWarning.copy(alpha = 0.5f)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = ZorbiWarning.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ZorbiWarning.copy(alpha = 0.4f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = ZorbiWarning,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Restricted Settings",
                            style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ZorbiTextPrimary
                        )
                        Text(
                            text = "Android 13+ Security Requirement",
                            style = ZorbiTheme.typography.labelSmall,
                            color = ZorbiWarning
                        )
                    }
                }

                Text(
                    text = "If Android greyed out the Zorbi Accessibility switch with 'Restricted setting', unlock it in 3 quick steps:",
                    style = ZorbiTheme.typography.bodySmall,
                    color = ZorbiTextMuted
                )

                // Step-by-step guidance
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepItem(
                        number = "1",
                        title = "Open Zorbi App Info",
                        description = "Tap the button below to jump directly to Zorbi's system settings page."
                    )
                    StepItem(
                        number = "2",
                        title = "Tap Top-Right Menu (⋮)",
                        description = "Look for the 3 dots in the top-right corner of the App Info screen."
                    )
                    StepItem(
                        number = "3",
                        title = "Select 'Allow restricted settings'",
                        description = "Verify with your phone fingerprint or PIN to unlock accessibility access."
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Actions
                ZorbiPrimaryButton(
                    text = "OPEN APP INFO SETTINGS",
                    onClick = {
                        WizardSystemHelper.openAppDetailsSettings(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                ZorbiSecondaryButton(
                    text = "GOT IT, RETURN",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StepItem(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = ZorbiAccentViolet.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, ZorbiAccentViolet)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = ZorbiTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = ZorbiAccentViolet
                    )
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = ZorbiTextPrimary
            )
            Text(
                text = description,
                style = ZorbiTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = ZorbiTextMuted
            )
        }
    }
}
