package com.zorbi.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.ui.R
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Official ZORBI Animated Splash Screen.
 *
 * Implements the second official Zorbi mascot — the friendly robot holding a gaming controller
 * with neon blue and purple glow, dark navy background, ZORBI bold typography, and "Learn. Play. Grow." tagline.
 * Features fluid multi-stage entrance, breathing zero-gravity idle float, rotating neon progress arc,
 * and seamless crossfade transition into the Home screen.
 */
@Composable
fun SplashScreen(
    onTransitionToNext: () -> Unit,
    modifier: Modifier = Modifier,
    splashDurationMs: Long = 2600L
) {
    var hasTransitioned by remember { mutableStateOf(false) }

    fun triggerTransition() {
        if (!hasTransitioned) {
            hasTransitioned = true
            onTransitionToNext()
        }
    }

    // Animation drivers
    val mascotScale = remember { Animatable(0.45f) }
    val mascotAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(32f) }
    val taglineAlpha = remember { Animatable(0f) }
    val spinnerAlpha = remember { Animatable(0f) }
    val exitScale = remember { Animatable(1f) }
    val exitAlpha = remember { Animatable(1f) }

    // Infinite breathing, floating & spinner loops
    val infiniteTransition = rememberInfiniteTransition(label = "splashInfinite")

    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascotFloatingOffset"
    )

    val mascotTilt by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascotTilt"
    )

    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraGlowPulse"
    )

    val spinnerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerAngle"
    )

    // Sequence orchestrator
    LaunchedEffect(Unit) {
        // Stage 1: Robot mascot scales in with spring bounce
        launch {
            mascotAlpha.animateTo(1f, tween(450))
        }
        launch {
            mascotScale.animateTo(1.08f, tween(480, easing = FastOutSlowInEasing))
            mascotScale.animateTo(1.0f, tween(220, easing = FastOutSlowInEasing))
        }

        delay(320L)

        // Stage 2: ZORBI logo typography slides up
        launch {
            textAlpha.animateTo(1f, tween(420))
        }
        launch {
            textOffsetY.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        }

        delay(220L)

        // Stage 3: Tagline reveals
        taglineAlpha.animateTo(1f, tween(380))

        // Stage 4: Spinner and status text reveal
        spinnerAlpha.animateTo(1f, tween(300))

        // Enjoy animated mascot float
        val remainingMs = (splashDurationMs - 1400L).coerceAtLeast(800L)
        delay(remainingMs)

        // Stage 5: Exit animation with expansion & crossfade
        launch {
            exitScale.animateTo(1.06f, tween(260, easing = FastOutSlowInEasing))
        }
        exitAlpha.animateTo(0f, tween(240))

        triggerTransition()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ZorbiBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { triggerTransition() }
            )
            .scale(exitScale.value)
            .alpha(exitAlpha.value)
    ) {
        // Futuristic Cyber Background with Neon Arcs & Ambient Nebula Orbs
        SplashCyberBackground(auraPulse = auraPulse)

        // Main Center Content: Mascot + Typography + Tagline
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1.0f))

            // 1. Robot Mascot with Neon Glow Aura & Zero-G Float
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .offset(y = floatOffsetY.dp)
                    .rotate(mascotTilt)
                    .scale(mascotScale.value)
                    .alpha(mascotAlpha.value),
                contentAlignment = Alignment.Center
            ) {
                // Radial pulsating glow behind mascot
                Canvas(
                    modifier = Modifier
                        .size(230.dp)
                        .scale(auraPulse)
                ) {
                    val radius = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ZorbiPrimaryCyan.copy(alpha = 0.40f),
                                ZorbiAccentViolet.copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }

                // Friendly Robot Mascot holding gaming controller
                Image(
                    painter = painterResource(id = R.drawable.zorbi_mascot_feathered),
                    contentDescription = "ZORBI Robot Mascot",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Bold ZORBI Typography
            Box(
                modifier = Modifier
                    .offset(y = textOffsetY.value.dp)
                    .alpha(textAlpha.value),
                contentAlignment = Alignment.Center
            ) {
                // Subtle ambient glow behind text
                Text(
                    text = "ZORBI",
                    style = ZorbiTheme.typography.displayLarge.copy(
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    ),
                    color = ZorbiPrimaryCyan.copy(alpha = 0.35f),
                    modifier = Modifier.offset(y = 2.dp)
                )

                Text(
                    text = "ZORBI",
                    style = ZorbiTheme.typography.displayLarge.copy(
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    ),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Official Tagline: "Learn. Play. Grow."
            Text(
                text = "Learn. Play. Grow.",
                style = ZorbiTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                ),
                color = Color(0xFFB8D5FF),
                modifier = Modifier.alpha(taglineAlpha.value)
            )

            Spacer(modifier = Modifier.weight(1.0f))

            // 4. Glowing Neon Circular Progress Arc & Status
            Column(
                modifier = Modifier
                    .padding(bottom = 44.dp)
                    .alpha(spinnerAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Rotating neon gradient arc spinner
                Canvas(
                    modifier = Modifier
                        .size(34.dp)
                        .rotate(spinnerAngle)
                ) {
                    val strokeWidth = 3.5.dp.toPx()
                    val arcSize = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                ZorbiPrimaryCyan,
                                ZorbiAccentViolet,
                                ZorbiAccentViolet.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Text(
                    text = "Launching Zorbi...",
                    style = ZorbiTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        letterSpacing = 0.6.sp
                    ),
                    color = ZorbiTextMuted.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * Draws futuristic glowing cyan and violet curves and ambient nebulas matching the official Zorbi splash design.
 */
@Composable
private fun SplashCyberBackground(
    auraPulse: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Top-left purple/violet ambient nebula orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ZorbiAccentViolet.copy(alpha = 0.35f * auraPulse),
                    ZorbiAccentViolet.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset(w * 0.15f, h * 0.10f),
                radius = w * 0.70f
            ),
            center = Offset(w * 0.15f, h * 0.10f),
            radius = w * 0.70f
        )

        // Bottom-right electric cyan ambient nebula orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ZorbiPrimaryCyan.copy(alpha = 0.30f * auraPulse),
                    ZorbiAccentViolet.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(w * 0.85f, h * 0.90f),
                radius = w * 0.75f
            ),
            center = Offset(w * 0.85f, h * 0.90f),
            radius = w * 0.75f
        )

        // Top flowing cyber neon curve (Violet)
        val topCurve = Path().apply {
            moveTo(0f, h * 0.08f)
            cubicTo(
                w * 0.25f, h * 0.02f,
                w * 0.40f, h * 0.14f,
                w * 0.75f, h * 0.04f
            )
        }
        drawPath(
            path = topCurve,
            color = ZorbiAccentViolet.copy(alpha = 0.45f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Bottom flowing cyber neon ribbon (Cyan & Violet waves)
        val bottomWaveCyan = Path().apply {
            moveTo(w * 0.15f, h)
            cubicTo(
                w * 0.40f, h * 0.90f,
                w * 0.65f, h * 0.94f,
                w, h * 0.80f
            )
        }
        drawPath(
            path = bottomWaveCyan,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    ZorbiPrimaryCyan.copy(alpha = 0.55f),
                    ZorbiAccentViolet.copy(alpha = 0.50f)
                )
            ),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        val bottomWaveViolet = Path().apply {
            moveTo(0f, h * 0.93f)
            cubicTo(
                w * 0.30f, h * 0.98f,
                w * 0.55f, h * 0.88f,
                w * 0.90f, h
            )
        }
        drawPath(
            path = bottomWaveViolet,
            color = ZorbiAccentViolet.copy(alpha = 0.35f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
