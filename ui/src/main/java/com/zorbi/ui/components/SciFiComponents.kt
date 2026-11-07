package com.zorbi.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.ui.animations.interactiveBounce
import com.zorbi.ui.animations.pulsingGlow
import com.zorbi.ui.haptics.rememberZorbiHaptics
import com.zorbi.ui.theme.SciFiAngledTabShape
import com.zorbi.ui.theme.SciFiChamferedPanelShape
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.monoMedium
import com.zorbi.ui.theme.ZorbiNeonMagenta
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSurface
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextDisabled
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme

/**
 * Sci-Fi Chamfered HUD Panel Container (cut corners with multi-gradient neon border).
 * Includes the signature left neon accent notch seen in the reference gaming UI.
 */
/**
 * Sci-Fi Chamfered HUD Panel Container (cut corners with Home Screen cyan/violet border).
 */
@Composable
fun SciFiChamferedPanel(
    modifier: Modifier = Modifier,
    borderBrush: Brush = Brush.linearGradient(
        listOf(
            ZorbiPrimaryCyan,
            ZorbiAccentViolet,
            ZorbiPrimaryCyan
        )
    ),
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = ZorbiSurface,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(SciFiChamferedPanelShape)
            .background(backgroundColor)
            .border(borderWidth, borderBrush, SciFiChamferedPanelShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content
    )
}

/**
 * Sci-Fi Segmented Category Tab matching Home Screen colors.
 */
@Composable
fun SciFiAngledTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberZorbiHaptics()
    val interactionSource = remember { MutableInteractionSource() }

    val backgroundBrush = if (isSelected) {
        Brush.horizontalGradient(
            listOf(ZorbiPrimaryCyan.copy(alpha = 0.25f), ZorbiAccentViolet.copy(alpha = 0.35f))
        )
    } else {
        Brush.horizontalGradient(
            listOf(Color(0x33121830), Color(0x33182245))
        )
    }

    val borderBrush = if (isSelected) {
        Brush.horizontalGradient(listOf(ZorbiPrimaryCyan, ZorbiAccentViolet))
    } else {
        Brush.horizontalGradient(
            listOf(ZorbiBorder, ZorbiBorder)
        )
    }

    Surface(
        modifier = modifier
            .interactiveBounce(enabled = true)
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = ZorbiPrimaryCyan),
                role = Role.Tab,
                onClick = {
                    haptics.click()
                    onClick()
                }
            )
            .border(if (isSelected) 1.5.dp else 1.dp, borderBrush, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(backgroundBrush)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = ZorbiTheme.typography.labelSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = if (isSelected) Color.White else ZorbiTextMuted,
                maxLines = 1
            )
        }
    }
}

/**
 * Sci-Fi Pill Toggle Switch with glowing ON/OFF states and tactical haptics.
 */
@Composable
fun SciFiPillToggle(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 80.dp,
    height: Dp = 28.dp,
    labelOn: String = "ON",
    labelOff: String = "OFF"
) {
    val haptics = rememberZorbiHaptics()
    val interactionSource = remember { MutableInteractionSource() }

    val borderColor by animateColorAsState(
        targetValue = if (isOn) ZorbiPrimaryCyan else ZorbiBorder,
        animationSpec = tween(250),
        label = "pillBorder"
    )

    val backgroundBrush = if (isOn) {
        Brush.horizontalGradient(
            listOf(ZorbiPrimaryCyan.copy(alpha = 0.35f), ZorbiAccentViolet.copy(alpha = 0.6f))
        )
    } else {
        Brush.horizontalGradient(
            listOf(Color(0x33121830), Color(0x33121830))
        )
    }

    Surface(
        modifier = modifier
            .interactiveBounce(enabled = true)
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = ZorbiPrimaryCyan),
                role = Role.Switch,
                onClick = {
                    haptics.toggle()
                    onToggle(!isOn)
                }
            )
            .border(1.5.dp, borderColor, RoundedCornerShape(height / 2)),
        shape = RoundedCornerShape(height / 2),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(backgroundBrush)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isOn) labelOn else labelOff,
                style = ZorbiTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = if (isOn) Color.White else ZorbiTextDisabled,
                maxLines = 1
            )
        }
    }
}

/**
 * Sci-Fi Neon Slider with glowing track and illuminated thumb (Home Screen Cyan/Violet palette).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SciFiNeonSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "",
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    displayFormatter: (Float) -> String = { "${it.toInt()}%" }
) {
    val haptics = rememberZorbiHaptics()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = title.uppercase(),
                    style = ZorbiTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = ZorbiTextPrimary,
                    maxLines = 1
                )
                if (icon.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = icon, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = displayFormatter(value),
                style = ZorbiMetricTypography.monoMedium.copy(fontSize = 12.sp),
                color = ZorbiPrimaryCyan,
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Slider(
            value = value,
            onValueChange = {
                onValueChange(it)
                haptics.tick()
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = ZorbiPrimaryCyan,
                inactiveTrackColor = ZorbiBorder,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Sci-Fi Radio Selection Option (e.g. GRAPHICS LOW / MED / HIGH).
 */
@Composable
fun SciFiRadioOption(
    title: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberZorbiHaptics()
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.click()
                    onSelect()
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(
                    BorderStroke(
                        1.5.dp,
                        if (isSelected) ZorbiPrimaryCyan else ZorbiTextDisabled
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ZorbiPrimaryCyan)
                )
            }
        }

        Text(
            text = title,
            style = ZorbiTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) Color.White else ZorbiTextMuted,
            maxLines = 1
        )
    }
}

/**
 * Sci-Fi Checkbox Option (e.g. DIFFICULTY EASY / NORMAL / HARD).
 */
@Composable
fun SciFiCheckbox(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberZorbiHaptics()
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.click()
                    onCheckedChange(!isChecked)
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .border(
                    BorderStroke(
                        1.5.dp,
                        if (isChecked) ZorbiPrimaryCyan else ZorbiBorder
                    ),
                    RoundedCornerShape(3.dp)
                )
                .background(
                    if (isChecked) ZorbiPrimaryCyan.copy(alpha = 0.25f) else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                Text(
                    text = "✓",
                    color = ZorbiPrimaryCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Text(
            text = title,
            style = ZorbiTheme.typography.labelSmall.copy(
                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isChecked) Color.White else ZorbiTextMuted,
            maxLines = 1
        )
    }
}

/**
 * Sci-Fi Stepper Selector (e.g. LANGUAGE < English >).
 */
@Composable
fun SciFiStepper(
    label: String,
    value: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberZorbiHaptics()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label.uppercase(),
            style = ZorbiTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = ZorbiTextPrimary,
            maxLines = 1
        )

        Row(
            modifier = Modifier
                .border(1.dp, ZorbiBorder, RoundedCornerShape(14.dp))
                .background(Color(0x33121830), RoundedCornerShape(14.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "‹",
                color = ZorbiPrimaryCyan,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        haptics.click()
                        onPrevious()
                    }
                    .padding(horizontal = 4.dp)
            )

            Text(
                text = value,
                style = ZorbiTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1
            )

            Text(
                text = "›",
                color = ZorbiPrimaryCyan,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        haptics.click()
                        onNext()
                    }
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

/**
 * Sci-Fi Dropdown Selector (e.g. DROPDOWN [ Option A ▾ ]).
 */
@Composable
fun SciFiDropdown(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberZorbiHaptics()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label.uppercase(),
            style = ZorbiTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = ZorbiTextPrimary,
            maxLines = 1
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, ZorbiBorder, RoundedCornerShape(14.dp))
                .background(Color(0x33121830))
                .clickable {
                    haptics.click()
                    onClick()
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                style = ZorbiTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = "▾",
                color = ZorbiNeonMagenta,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Sci-Fi HUD Header Frame with futuristic angular brackets and bookmark tag.
 */
@Composable
fun SciFiHeader(
    title: String,
    onBookmarkClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Outer decorative bracket lines
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, ZorbiAccentViolet, ZorbiNeonMagenta)
                        )
                    )
            )
            Spacer(modifier = Modifier.width(130.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(ZorbiNeonMagenta, ZorbiAccentViolet, Color.Transparent)
                        )
                    )
            )
        }

        // Title text with sci-fi gaming bracket
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(Color(0xF00A0E1A))
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Text(
                text = "◤",
                color = ZorbiPrimaryCyan,
                fontSize = 11.sp
            )
            Text(
                text = title.uppercase(),
                style = ZorbiTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.5.sp
                ),
                color = Color.White
            )
            Text(
                text = "◢",
                color = ZorbiNeonMagenta,
                fontSize = 11.sp
            )
        }

        // Bookmark tag on top-right
        Text(
            text = "🔖",
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .clickable { onBookmarkClick() }
        )
    }
}
