package com.zorbi.ui.hud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import com.zorbi.ui.components.StatusLevel
import com.zorbi.ui.theme.ZorbiChipShape
import com.zorbi.ui.components.ZorbiStatusDot
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import com.zorbi.ui.theme.ZorbiWarning

/**
 * Floating badge signaling fist-pause state (Master Prompt Section 5.1 / 13).
 */
@Composable
fun PauseIndicatorBadge(
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isPaused,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Surface(
            modifier = modifier.clip(ZorbiChipShape),
            shape = ZorbiChipShape,
            color = Color(0xE6121830),
            border = BorderStroke(1.5.dp, ZorbiWarning)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZorbiStatusDot(level = StatusLevel.WARNING, animatePulse = true)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CONTROLLER PAUSED",
                    style = ZorbiTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = ZorbiWarning,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "(HOLD FIST TO RESUME)",
                    style = ZorbiTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = ZorbiTextPrimary
                    )
                )
            }
        }
    }
}
