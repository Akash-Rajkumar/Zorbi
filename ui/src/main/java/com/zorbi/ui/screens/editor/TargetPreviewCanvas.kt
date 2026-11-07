package com.zorbi.ui.screens.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiTheme
import kotlin.math.hypot

// Professional Technical Canvas Theme Tokens
private val CanvasBezelBorder = Color(0xFF26324A)
private val CanvasScreenBg = Color(0xFF0E1424)
private val CanvasSurfaceCard = Color(0xFF151C2E)
private val CanvasSurfaceSec = Color(0xFF1B2438)
private val CanvasLeftColor = Color(0xFF00C8D9)
private val CanvasRightColor = Color(0xFF7986CB)
private val CanvasCenterColor = Color(0xFF10B981)
private val CanvasTextPrimary = Color(0xFFF1F5F9)
private val CanvasTextSecondary = Color(0xFF94A3B8)

/**
 * Visual Phone-Shaped Canvas Preview for Touch Targets.
 * Clean, technical display for normalized screen-space coordinates (0.0 .. 1.0).
 * Supports drag-to-position without distracting animations, neon glows, or casino gradients.
 */
@Composable
fun TargetPreviewCanvas(
    orientation: ProfileOrientation,
    mapping: MappingType,
    targetLeftX: Float,
    targetLeftY: Float,
    targetRightX: Float,
    targetRightY: Float,
    targetCenterX: Float,
    targetCenterY: Float,
    onTargetLeftMoved: (Float, Float) -> Unit,
    onTargetRightMoved: (Float, Float) -> Unit,
    onTargetCenterMoved: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = orientation == ProfileOrientation.LANDSCAPE
    val aspectRatio = if (isLandscape) 16f / 9f else 9f / 16f

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Device Canvas Bezel
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.95f else 0.65f)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF070B14))
                .border(
                    BorderStroke(1.5.dp, CanvasBezelBorder),
                    RoundedCornerShape(16.dp)
                )
                .padding(3.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(13.dp))
                    .background(CanvasScreenBg)
            ) {
                val canvasWidth = constraints.maxWidth.toFloat()
                val canvasHeight = constraints.maxHeight.toFloat()

                // Drag gesture detection mapped to the closest target
                var activeDragTarget by remember { mutableStateOf<String?>(null) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(canvasWidth, canvasHeight, targetLeftX, targetLeftY, targetRightX, targetRightY, targetCenterX, targetCenterY) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val leftPx = Offset(targetLeftX * canvasWidth, targetLeftY * canvasHeight)
                                    val rightPx = Offset(targetRightX * canvasWidth, targetRightY * canvasHeight)
                                    val centerPx = Offset(targetCenterX * canvasWidth, targetCenterY * canvasHeight)

                                    val distLeft = hypot(offset.x - leftPx.x, offset.y - leftPx.y)
                                    val distRight = hypot(offset.x - rightPx.x, offset.y - rightPx.y)
                                    val distCenter = hypot(offset.x - centerPx.x, offset.y - centerPx.y)

                                    val threshold = 70f
                                    activeDragTarget = when {
                                        distLeft <= threshold && distLeft <= distRight && distLeft <= distCenter -> "left"
                                        distRight <= threshold && distRight <= distCenter -> "right"
                                        distCenter <= threshold -> "center"
                                        else -> null
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val currentTarget = activeDragTarget ?: return@detectDragGestures

                                    val deltaX = dragAmount.x / canvasWidth
                                    val deltaY = dragAmount.y / canvasHeight

                                    when (currentTarget) {
                                        "left" -> {
                                            val newX = (targetLeftX + deltaX).coerceIn(0.02f, 0.98f)
                                            val newY = (targetLeftY + deltaY).coerceIn(0.02f, 0.98f)
                                            onTargetLeftMoved(newX, newY)
                                        }
                                        "right" -> {
                                            val newX = (targetRightX + deltaX).coerceIn(0.02f, 0.98f)
                                            val newY = (targetRightY + deltaY).coerceIn(0.02f, 0.98f)
                                            onTargetRightMoved(newX, newY)
                                        }
                                        "center" -> {
                                            val newX = (targetCenterX + deltaX).coerceIn(0.02f, 0.98f)
                                            val newY = (targetCenterY + deltaY).coerceIn(0.02f, 0.98f)
                                            onTargetCenterMoved(newX, newY)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    activeDragTarget = null
                                },
                                onDragCancel = {
                                    activeDragTarget = null
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // 1. Subtle, Clean Technical Grid Lines
                        val subtleGridColor = Color(0x2294A3B8)
                        val centerGridColor = Color(0x4494A3B8)
                        val dashedStroke = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))

                        // Quarter guidelines
                        drawLine(
                            color = subtleGridColor,
                            start = Offset(w * 0.25f, 0f),
                            end = Offset(w * 0.25f, h),
                            strokeWidth = 1f,
                            pathEffect = dashedStroke.pathEffect
                        )
                        drawLine(
                            color = subtleGridColor,
                            start = Offset(w * 0.75f, 0f),
                            end = Offset(w * 0.75f, h),
                            strokeWidth = 1f,
                            pathEffect = dashedStroke.pathEffect
                        )
                        drawLine(
                            color = subtleGridColor,
                            start = Offset(0f, h * 0.25f),
                            end = Offset(w, h * 0.25f),
                            strokeWidth = 1f,
                            pathEffect = dashedStroke.pathEffect
                        )
                        drawLine(
                            color = subtleGridColor,
                            start = Offset(0f, h * 0.75f),
                            end = Offset(w, h * 0.75f),
                            strokeWidth = 1f,
                            pathEffect = dashedStroke.pathEffect
                        )

                        // Center axes
                        drawLine(
                            color = centerGridColor,
                            start = Offset(w / 2f, 0f),
                            end = Offset(w / 2f, h),
                            strokeWidth = 1f,
                            pathEffect = dashedStroke.pathEffect
                        )
                        drawLine(
                            color = centerGridColor,
                            start = Offset(0f, h / 2f),
                            end = Offset(w, h / 2f),
                            strokeWidth = 1f,
                            pathEffect = dashedStroke.pathEffect
                        )

                        // 2. Mapping-specific visual feedback guides (Clean & Non-Glow)
                        val leftPx = Offset(targetLeftX * w, targetLeftY * h)
                        val rightPx = Offset(targetRightX * w, targetRightY * h)
                        val centerPx = Offset(targetCenterX * w, targetCenterY * h)

                        when (mapping) {
                            MappingType.STEER_BUTTONS -> {
                                // Connect Left and Right with subtle baseline
                                drawLine(
                                    color = CanvasLeftColor.copy(alpha = 0.35f),
                                    start = leftPx,
                                    end = rightPx,
                                    strokeWidth = 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                                )
                            }
                            MappingType.STEER_DRAG -> {
                                // Continuous Drag Slider Track Arc
                                val trackPath = Path().apply {
                                    moveTo(leftPx.x, leftPx.y)
                                    quadraticTo(centerPx.x, centerPx.y, rightPx.x, rightPx.y)
                                }
                                drawPath(
                                    path = trackPath,
                                    color = CanvasRightColor.copy(alpha = 0.45f),
                                    style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                                )
                            }
                            MappingType.LANE_SWIPE -> {
                                // 4 Directional swipe cross radiating from center
                                val arrowLen = 32f
                                val dirs = listOf(
                                    Offset(0f, -arrowLen),
                                    Offset(0f, arrowLen),
                                    Offset(-arrowLen, 0f),
                                    Offset(arrowLen, 0f)
                                )
                                dirs.forEach { dir ->
                                    drawLine(
                                        color = CanvasCenterColor.copy(alpha = 0.45f),
                                        start = centerPx,
                                        end = centerPx + dir,
                                        strokeWidth = 2f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                            MappingType.SLASH -> {
                                // Clean static diagonal trajectory guideline (no animated rainbow laser)
                                drawLine(
                                    color = CanvasLeftColor.copy(alpha = 0.40f),
                                    start = leftPx,
                                    end = rightPx,
                                    strokeWidth = 2f,
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                                )
                            }
                        }

                        // 3. Render Target: LEFT (Clean marker: subtle fill, crisp ring, center dot)
                        drawCircle(
                            color = CanvasLeftColor.copy(alpha = 0.15f),
                            radius = 16f,
                            center = leftPx
                        )
                        drawCircle(
                            color = CanvasLeftColor,
                            radius = 12f,
                            center = leftPx,
                            style = Stroke(width = 1.5f)
                        )
                        drawCircle(
                            color = CanvasLeftColor,
                            radius = 3f,
                            center = leftPx
                        )

                        // 4. Render Target: RIGHT (Clean marker)
                        drawCircle(
                            color = CanvasRightColor.copy(alpha = 0.15f),
                            radius = 16f,
                            center = rightPx
                        )
                        drawCircle(
                            color = CanvasRightColor,
                            radius = 12f,
                            center = rightPx,
                            style = Stroke(width = 1.5f)
                        )
                        drawCircle(
                            color = CanvasRightColor,
                            radius = 3f,
                            center = rightPx
                        )

                        // 5. Render Target: CENTER (Clean marker)
                        drawCircle(
                            color = CanvasCenterColor.copy(alpha = 0.15f),
                            radius = 14f,
                            center = centerPx
                        )
                        drawCircle(
                            color = CanvasCenterColor,
                            radius = 10f,
                            center = centerPx,
                            style = Stroke(width = 1.5f)
                        )
                        drawCircle(
                            color = CanvasCenterColor,
                            radius = 3f,
                            center = centerPx
                        )
                    }

                    // Punch-hole camera dot on top bezel
                    Box(
                        modifier = Modifier
                            .align(if (isLandscape) Alignment.CenterStart else Alignment.TopCenter)
                            .padding(if (isLandscape) 8.dp else 5.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(1.dp, CanvasBezelBorder, CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Technical Legend & Normalized Coordinates Readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TargetLegendPill(
                label = "LEFT",
                color = CanvasLeftColor,
                coords = "x:${"%.2f".format(targetLeftX)} y:${"%.2f".format(targetLeftY)}"
            )
            TargetLegendPill(
                label = "CENTER",
                color = CanvasCenterColor,
                coords = "x:${"%.2f".format(targetCenterX)} y:${"%.2f".format(targetCenterY)}"
            )
            TargetLegendPill(
                label = "RIGHT",
                color = CanvasRightColor,
                coords = "x:${"%.2f".format(targetRightX)} y:${"%.2f".format(targetRightY)}"
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Drag targets directly on the preview or use numeric sliders below",
            style = ZorbiTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = CanvasTextSecondary
        )
    }
}

@Composable
private fun TargetLegendPill(
    label: String,
    color: Color,
    coords: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CanvasSurfaceSec)
            .border(1.dp, CanvasBezelBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = ZorbiTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = color
        )
        Text(
            text = coords,
            style = ZorbiMetricTypography.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                color = CanvasTextPrimary
            )
        )
    }
}
