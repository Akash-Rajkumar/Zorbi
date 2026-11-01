package com.zorbi.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.theme.ZorbiError
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme

/**
 * Futuristic radar/scanning circular loading indicator.
 */
@Composable
fun ZorbiLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRotate"
    )

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            // Rotating neon gradient ring
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .rotate(rotation)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                ZorbiPrimaryCyan.copy(alpha = 0.2f),
                                ZorbiPrimaryCyan
                            )
                        )
                    )
            )
            // Inner dark mask
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0E1A))
            )
            // Center pulse dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ZorbiPrimaryCyan)
            )
        }

        if (message != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = ZorbiTheme.typography.bodyMedium,
                color = ZorbiTextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * High quality Empty State with clear messaging and optional CTA button.
 */
@Composable
fun ZorbiEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    ctaText: String? = null,
    onCtaClick: (() -> Unit)? = null,
    actionButton: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = ZorbiSurfaceElevated,
                border = BorderStroke(1.dp, ZorbiBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ZorbiPrimaryCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Text(
            text = title,
            style = ZorbiTheme.typography.titleLarge,
            color = ZorbiTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = ZorbiTheme.typography.bodyMedium,
            color = ZorbiTextMuted,
            textAlign = TextAlign.Center
        )

        if (actionButton != null) {
            Spacer(modifier = Modifier.height(24.dp))
            actionButton()
        } else if (ctaText != null && onCtaClick != null) {
            Spacer(modifier = Modifier.height(24.dp))
            ZorbiPrimaryButton(
                text = ctaText,
                onClick = onCtaClick
            )
        }
    }
}

/**
 * Error card with direct "Fix it" CTA button to resolve system settings issues.
 * Aligns with Section 13 accessibility guideline: "clear error text with a 'Fix it' button".
 */
@Composable
fun ZorbiErrorCard(
    title: String,
    message: String,
    onFixClick: () -> Unit,
    modifier: Modifier = Modifier,
    fixButtonText: String = "Fix it"
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(ZorbiCardShape),
        shape = ZorbiCardShape,
        color = ZorbiError.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, ZorbiError.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZorbiStatusDot(level = StatusLevel.ERROR, animatePulse = false)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiError
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = ZorbiTheme.typography.bodyMedium,
                color = ZorbiTextPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))
            ZorbiPrimaryButton(
                text = fixButtonText,
                onClick = onFixClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
