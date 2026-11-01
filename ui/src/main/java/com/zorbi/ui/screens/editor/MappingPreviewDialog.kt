package com.zorbi.ui.screens.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.MappingType
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.components.ZorbiIconButton
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.haptics.rememberZorbiHaptics
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiTheme
import kotlin.math.abs

// Technical Utility Color Tokens
private val DialogBg = Color(0xFF151C2E)
private val DialogSurfaceSec = Color(0xFF1B2438)
private val DialogBorder = Color(0xFF26324A)
private val DialogPrimaryAccent = Color(0xFF00C8D9)
private val DialogSecondaryAccent = Color(0xFF7986CB)
private val DialogTextPrimary = Color(0xFFF1F5F9)
private val DialogTextSecondary = Color(0xFF94A3B8)
private val DialogSuccess = Color(0xFF10B981)

/**
 * Interactive Mapping Simulator Preview Dialog.
 * Clean, technical simulation interface to test touch injection behavior and thresholds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingPreviewDialog(
    state: MappingEditorUiState,
    onDismiss: () -> Unit
) {
    val haptics = rememberZorbiHaptics()
    var simSteerAngle by remember { mutableFloatStateOf(0f) }
    var lastSimulatedAction by remember { mutableStateOf("IDLE — Awaiting input") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(DialogPrimaryAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MAPPING SIMULATOR",
                        style = ZorbiTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = DialogTextPrimary
                    )
                }
                ZorbiIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Close Preview",
                    onClick = onDismiss
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Info Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DialogSurfaceSec)
                        .border(1.dp, DialogBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = state.displayName,
                            style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = DialogTextPrimary
                        )
                        Text(
                            text = state.selectedMapping.name,
                            style = ZorbiMetricTypography.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                color = DialogPrimaryAccent
                            )
                        )
                    }
                    ZorbiBadge(
                        text = state.selectedTracker.name,
                        accentColor = DialogSecondaryAccent
                    )
                }

                // Interactive Simulator according to Mapping Type
                when (state.selectedMapping) {
                    MappingType.STEER_BUTTONS -> {
                        Text(
                            text = "Tilt Hand Steering Angle (-30° to +30°):",
                            style = ZorbiTheme.typography.bodySmall,
                            color = DialogTextSecondary
                        )

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "STEER ANGLE",
                                    style = ZorbiTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = DialogTextPrimary
                                )
                                Text(
                                    text = "${simSteerAngle.toInt()}°",
                                    style = ZorbiMetricTypography.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                                    color = DialogPrimaryAccent
                                )
                            }
                            Slider(
                                value = simSteerAngle,
                                onValueChange = { angle ->
                                    simSteerAngle = angle
                                    val inDeadzone = abs(angle) < state.deadzoneDeg
                                    lastSimulatedAction = when {
                                        inDeadzone -> "Deadzone [Neutral — No Tap]"
                                        angle < -state.deadzoneDeg -> {
                                            haptics.click()
                                            "LEFT TAP: (${state.targetLeftX.format2()}, ${state.targetLeftY.format2()})"
                                        }
                                        else -> {
                                            haptics.click()
                                            "RIGHT TAP: (${state.targetRightX.format2()}, ${state.targetRightY.format2()})"
                                        }
                                    }
                                },
                                valueRange = -30f..30f,
                                colors = SliderDefaults.colors(
                                    thumbColor = DialogPrimaryAccent,
                                    activeTrackColor = DialogPrimaryAccent,
                                    inactiveTrackColor = DialogBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            text = "Deadzone: ±${state.deadzoneDeg.toInt()}° (Angle within deadzone remains neutral)",
                            style = ZorbiTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = DialogTextSecondary
                        )
                    }

                    MappingType.STEER_DRAG -> {
                        Text(
                            text = "Drag Wheel Simulation (-1.0 to +1.0):",
                            style = ZorbiTheme.typography.bodySmall,
                            color = DialogTextSecondary
                        )

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "DRAG POSITION",
                                    style = ZorbiTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = DialogTextPrimary
                                )
                                Text(
                                    text = "%.2f".format(simSteerAngle),
                                    style = ZorbiMetricTypography.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                                    color = DialogPrimaryAccent
                                )
                            }
                            Slider(
                                value = simSteerAngle,
                                onValueChange = { pos ->
                                    simSteerAngle = pos
                                    val normalizedPos = (pos + 1f) / 2f
                                    val interpX = state.targetLeftX + (state.targetRightX - state.targetLeftX) * normalizedPos
                                    val interpY = state.targetLeftY + (state.targetRightY - state.targetLeftY) * normalizedPos
                                    lastSimulatedAction = "DRAG TO: (${interpX.format2()}, ${interpY.format2()})"
                                },
                                valueRange = -1f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = DialogPrimaryAccent,
                                    activeTrackColor = DialogPrimaryAccent,
                                    inactiveTrackColor = DialogBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    MappingType.LANE_SWIPE -> {
                        Text(
                            text = "Test 4-Way Lane Swipe Dispatches:",
                            style = ZorbiTheme.typography.bodySmall,
                            color = DialogTextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ZorbiSecondaryButton(
                                text = "SWIPE LEFT",
                                onClick = {
                                    haptics.confirm()
                                    lastSimulatedAction = "FLICK LEFT: origin=(${state.targetCenterX.format2()}, ${state.targetCenterY.format2()}) → (${state.targetLeftX.format2()}, ${state.targetLeftY.format2()})"
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ZorbiSecondaryButton(
                                text = "SWIPE RIGHT",
                                onClick = {
                                    haptics.confirm()
                                    lastSimulatedAction = "FLICK RIGHT: origin=(${state.targetCenterX.format2()}, ${state.targetCenterY.format2()}) → (${state.targetRightX.format2()}, ${state.targetRightY.format2()})"
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ZorbiSecondaryButton(
                                text = "JUMP UP",
                                onClick = {
                                    haptics.confirm()
                                    lastSimulatedAction = "FLICK UP: origin=(${state.targetCenterX.format2()}, ${state.targetCenterY.format2()}) → y=0.15"
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ZorbiSecondaryButton(
                                text = "ROLL DOWN",
                                onClick = {
                                    haptics.confirm()
                                    lastSimulatedAction = "FLICK DOWN: origin=(${state.targetCenterX.format2()}, ${state.targetCenterY.format2()}) → y=0.85"
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    MappingType.SLASH -> {
                        Text(
                            text = "Test Freeform Slash Stroke:",
                            style = ZorbiTheme.typography.bodySmall,
                            color = DialogTextSecondary
                        )

                        ZorbiPrimaryButton(
                            text = "SIMULATE SLASH STROKE",
                            leadingIcon = Icons.Default.PlayArrow,
                            onClick = {
                                haptics.confirm()
                                lastSimulatedAction = "SLASH PATH: (${state.targetLeftX.format2()}, ${state.targetLeftY.format2()}) → (${state.targetRightX.format2()}, ${state.targetRightY.format2()}) [cooldown=${state.cooldownMs}ms]"
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Status Action Readout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DialogSurfaceSec)
                        .border(1.dp, DialogBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "LAST INJECTED ACTION",
                        style = ZorbiTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DialogTextSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = lastSimulatedAction,
                        style = ZorbiMetricTypography.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = DialogSuccess
                        )
                    )
                }
            }
        },
        confirmButton = {
            ZorbiPrimaryButton(
                text = "DONE",
                onClick = onDismiss
            )
        },
        containerColor = DialogBg,
        textContentColor = DialogTextPrimary
    )
}

private fun Float.format2(): String = "%.2f".format(this)
