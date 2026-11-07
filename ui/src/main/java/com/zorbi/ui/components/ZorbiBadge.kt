package com.zorbi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.MappingType
import com.zorbi.core.TrackerId
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiChipShape
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiSurfaceVariant
import com.zorbi.ui.theme.ZorbiTextDisabled
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme

/**
 * Clean gaming badge for profile tags, tracker types, and download counts.
 */
@Composable
fun ZorbiBadge(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = ZorbiPrimaryCyan,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier.clip(ZorbiChipShape),
        shape = ZorbiChipShape,
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = ZorbiTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = accentColor
                )
            )
        }
    }
}

/**
 * Specialized badge for TrackerId (HAND_SINGLE, HAND_DUAL_WHEEL, POSE_LITE).
 */
@Composable
fun ZorbiTrackerBadge(
    trackerId: TrackerId,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (trackerId) {
        TrackerId.HAND_SINGLE -> "1-Hand" to ZorbiPrimaryCyan
        TrackerId.HAND_DUAL_WHEEL -> "2-Hand Wheel" to ZorbiAccentViolet
        TrackerId.POSE_LITE -> "Pose Lean" to ZorbiSuccess
    }
    ZorbiBadge(text = label, modifier = modifier, accentColor = color)
}

/**
 * Specialized badge for MappingType (STEER_BUTTONS, STEER_DRAG, LANE_SWIPE, SLASH).
 */
@Composable
fun ZorbiMappingBadge(
    mapping: MappingType,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (mapping) {
        MappingType.LANE_SWIPE -> "Lane Swipe" to ZorbiPrimaryCyan
        MappingType.SLASH -> "Air Slash" to ZorbiSuccess
        MappingType.STEER_BUTTONS -> "Steer Buttons" to ZorbiAccentViolet
        MappingType.STEER_DRAG -> "Steer Drag" to ZorbiAccentViolet
    }
    ZorbiBadge(text = label, modifier = modifier, accentColor = color)
}

/**
 * Selectable filter chip for the Game Library and Community screens.
 */
@Composable
fun ZorbiFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val borderColor = if (selected) ZorbiPrimaryCyan else ZorbiBorder
    val backgroundColor = if (selected) ZorbiPrimaryCyan.copy(alpha = 0.15f) else ZorbiSurfaceElevated
    val textColor = if (selected) ZorbiPrimaryCyan else ZorbiTextMuted

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(ZorbiChipShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = ZorbiPrimaryCyan),
                role = Role.Checkbox,
                onClick = onClick
            ),
        shape = ZorbiChipShape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = ZorbiTheme.typography.labelMedium.copy(color = textColor)
            )
            if (count != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$count",
                    style = ZorbiTheme.typography.labelSmall.copy(
                        color = if (selected) ZorbiPrimaryCyan else ZorbiTextDisabled
                    )
                )
            }
        }
    }
}
