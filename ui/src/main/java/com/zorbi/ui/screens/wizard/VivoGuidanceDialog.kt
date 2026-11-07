package com.zorbi.ui.screens.wizard

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurface
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import com.zorbi.ui.theme.ZorbiWarning

/**
 * Detailed guidance dialog for Vivo / OriginOS and custom Android skins.
 * Enforces Master Prompt Section 10 & Test Gate T-P3-2.
 */
@Composable
fun VivoGuidanceDialog(
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
            border = BorderStroke(1.dp, ZorbiPrimaryCyan.copy(alpha = 0.5f)),
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
                        color = ZorbiPrimaryCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ZorbiPrimaryCyan.copy(alpha = 0.4f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ZorbiPrimaryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Vivo / OriginOS Setup",
                            style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ZorbiTextPrimary
                        )
                        Text(
                            text = "Prevent Background Service Kills",
                            style = ZorbiTheme.typography.labelSmall,
                            color = ZorbiPrimaryCyan
                        )
                    }
                }

                Text(
                    text = "OriginOS aggressively suspends background camera and accessibility services unless these 3 settings are configured:",
                    style = ZorbiTheme.typography.bodySmall,
                    color = ZorbiTextMuted
                )

                // 3 Essential steps for Vivo / OriginOS
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VivoStepItem(
                        number = "1",
                        title = "Enable Autostart",
                        description = "Open iManager or Settings -> App Manager -> Autostart -> Toggle Zorbi ON."
                    )
                    VivoStepItem(
                        number = "2",
                        title = "High Background Power Consumption",
                        description = "In App Info -> Battery -> Select 'Allow high background power consumption' (do not use Smart Control)."
                    )
                    VivoStepItem(
                        number = "3",
                        title = "Lock Zorbi in Recents",
                        description = "Open your recent apps switcher, swipe down on Zorbi or tap the padlock icon to lock it in RAM."
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Actions
                ZorbiPrimaryButton(
                    text = "OPEN APP INFO TO CONFIGURE",
                    onClick = {
                        WizardSystemHelper.openAppDetailsSettings(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                ZorbiSecondaryButton(
                    text = "I'VE CONFIGURED THIS",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun VivoStepItem(
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
            color = ZorbiSuccess.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, ZorbiSuccess)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = ZorbiTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = ZorbiSuccess
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
