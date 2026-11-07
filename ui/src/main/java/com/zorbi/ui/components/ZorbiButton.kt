package com.zorbi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zorbi.ui.animations.interactiveBounce
import com.zorbi.ui.haptics.rememberZorbiHaptics
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiButtonShape
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiSurfaceVariant
import com.zorbi.ui.theme.ZorbiTextDisabled
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme

/**
 * Primary gaming button with neon cyan glow and high-contrast dark text.
 * Strictly satisfies minimum 48dp touch target per Master Prompt Section 13.
 */
@Composable
fun ZorbiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = rememberZorbiHaptics()
    val backgroundColor = if (enabled) ZorbiPrimaryCyan else ZorbiSurfaceVariant
    val contentColor = if (enabled) Color(0xFF001520) else ZorbiTextDisabled

    Surface(
        modifier = modifier
            .interactiveBounce(enabled = enabled && !loading)
            .defaultMinSize(minHeight = ZorbiTheme.spacing.minTouchTarget)
            .clip(ZorbiButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = {
                    haptics.click()
                    onClick()
                }
            ),
        shape = ZorbiButtonShape,
        color = backgroundColor,
        shadowElevation = if (enabled) 6.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = ZorbiTheme.spacing.minTouchTarget)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        color = contentColor,
                        style = ZorbiTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    if (trailingIcon != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = trailingIcon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Secondary futuristic button with violet border and glass accent styling.
 * Satisfies minimum 48dp touch target.
 */
@Composable
fun ZorbiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = rememberZorbiHaptics()
    val borderColor = if (enabled) ZorbiAccentViolet else ZorbiBorder
    val contentColor = if (enabled) ZorbiTextPrimary else ZorbiTextDisabled
    val iconTint = if (enabled) ZorbiAccentViolet else ZorbiTextDisabled

    Surface(
        modifier = modifier
            .interactiveBounce(enabled = enabled && !loading)
            .defaultMinSize(minHeight = ZorbiTheme.spacing.minTouchTarget)
            .clip(ZorbiButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = ZorbiAccentViolet),
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = {
                    haptics.click()
                    onClick()
                }
            ),
        shape = ZorbiButtonShape,
        color = ZorbiSurfaceElevated,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = ZorbiTheme.spacing.minTouchTarget)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = ZorbiAccentViolet,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        color = contentColor,
                        style = ZorbiTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (trailingIcon != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = trailingIcon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Outlined button for subtle secondary actions (e.g., secondary filters, cancellations).
 */
@Composable
fun ZorbiOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = rememberZorbiHaptics()
    val contentColor = if (enabled) ZorbiTextMuted else ZorbiTextDisabled

    Surface(
        modifier = modifier
            .interactiveBounce(enabled = enabled)
            .defaultMinSize(minHeight = ZorbiTheme.spacing.minTouchTarget)
            .clip(ZorbiButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = ZorbiPrimaryCyan),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.click()
                    onClick()
                }
            ),
        shape = ZorbiButtonShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, ZorbiBorder)
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = ZorbiTheme.spacing.minTouchTarget)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                style = ZorbiTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Accessible Icon Button ensuring >= 48dp interactive touch target.
 */
@Composable
fun ZorbiIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = ZorbiPrimaryCyan
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = rememberZorbiHaptics()

    Surface(
        modifier = modifier
            .interactiveBounce(enabled = enabled)
            .size(ZorbiTheme.spacing.minTouchTarget)
            .clip(ZorbiButtonShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = tint),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.click()
                    onClick()
                }
            ),
        shape = ZorbiButtonShape,
        color = ZorbiSurfaceElevated,
        border = BorderStroke(1.dp, ZorbiBorder)
    ) {
        Box(
            modifier = Modifier.size(ZorbiTheme.spacing.minTouchTarget),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else ZorbiTextDisabled,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
