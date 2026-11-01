package com.zorbi.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.ui.R
import com.zorbi.ui.haptics.rememberZorbiHaptics
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.ZorbiNeonMagenta
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurface
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextDisabled
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import com.zorbi.ui.theme.monoMedium

/**
 * Model representing a game in the Active Games slide-out list.
 */
data class DrawerGame(
    val name: String,
    val packageName: String,
    val compatibility: String,
    val orientation: String,
    val isEnabled: Boolean = true,
    val initialSelected: Boolean = false
)

/**
 * Performance preset modes for the gaming engine.
 */
enum class PerformancePreset(val title: String, val description: String) {
    QUALITY("Quality", "60Hz • High Precision"),
    BALANCED("Balanced", "30Hz • Power Efficient"),
    PERFORMANCE("Performance", "Sub-20ms • Low Heat")
}

/**
 * Right-Side Slide-Out Navigation Drawer for ZORBI.
 *
 * Implements:
 * A. Active Games with toggle switches and selection indicators.
 * B. Quick Controls (Gesture, HUD, Haptics, Floating HUD, Perf Monitor).
 * C. Performance mode selector (Quality, Balanced, Performance).
 * D. Information entries (About, Privacy, Help, Version).
 * E. Other Navigation (Calibration, Community, Settings).
 */
@Composable
fun ZorbiRightDrawer(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToWizard: () -> Unit,
    onNavigateToCalibrate: (String) -> Unit,
    onNavigateToCommunity: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToEditor: (String) -> Unit = {},
    activePackageName: String = "com.kiloo.subwaysurf",
    onSelectActiveGame: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = rememberZorbiHaptics()

    // Back handler: Close drawer on Android hardware back press
    BackHandler(enabled = isOpen) {
        haptics.click()
        onDismissRequest()
    }

    // Quick control states
    var gestureControlsEnabled by remember { mutableStateOf(true) }
    var hudOverlayEnabled by remember { mutableStateOf(true) }
    var hapticFeedbackEnabled by remember { mutableStateOf(true) }
    var floatingHudEnabled by remember { mutableStateOf(false) }
    var perfMonitoringEnabled by remember { mutableStateOf(true) }

    // Performance mode selection
    var selectedPerfMode by remember { mutableStateOf(PerformancePreset.BALANCED) }

    // Dialog states for Help and Privacy
    var showHelpDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // Active games catalog with toggle state
    val gamesList = remember {
        mutableStateListOf(
            DrawerGame("Subway Surfers", "com.kiloo.subwaysurf", "Target 60 FPS (Verified)", "PORTRAIT", true, true),
            DrawerGame("Hill Climb Racing", "com.fingersoft.hillclimb", "Target 60 FPS (Verified)", "LANDSCAPE", true, false),
            DrawerGame("Fruit Ninja", "com.halfbrick.fruitninja", "Target 60 FPS (Verified)", "LANDSCAPE", true, false),
            DrawerGame("Minecraft", "com.mojang.minecraftpe", "Target 60 FPS (Simulated)", "LANDSCAPE", false, false)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Semi-transparent backdrop scrim with fade animation
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptics.click()
                            onDismissRequest()
                        }
                    )
            )
        }

        // Slide-out panel from the right edge
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 300.dp, max = 340.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0D1326), Color(0xFF080C17))
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    ZorbiPrimaryCyan.copy(alpha = 0.45f),
                                    ZorbiAccentViolet.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                    ),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    // Top Bar with Drawer Header & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                ZorbiPrimaryCyan.copy(alpha = 0.25f),
                                                ZorbiAccentViolet.copy(alpha = 0.25f)
                                            )
                                        )
                                    )
                                    .border(1.dp, ZorbiPrimaryCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.zorbi_mascot_feathered),
                                    contentDescription = "ZORBI Mascot",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "CONTROL CENTER",
                                    style = ZorbiTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    ),
                                    color = ZorbiTextPrimary
                                )
                                Text(
                                    text = "ZORBI GAMING PANEL",
                                    style = ZorbiTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                                    color = ZorbiPrimaryCyan
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptics.click()
                                onDismissRequest()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ZorbiSurfaceElevated)
                                .border(1.dp, ZorbiBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Menu",
                                tint = ZorbiTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = ZorbiBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // SECTION A: ACTIVE GAMES
                    DrawerSectionHeader(
                        title = "ACTIVE GAMES",
                        subtitle = "Select game or toggle support"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    gamesList.forEachIndexed { index, game ->
                        val isSelected = game.packageName == activePackageName

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptics.toggle()
                                    onSelectActiveGame(game.packageName)
                                }
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) ZorbiPrimaryCyan else ZorbiBorder.copy(alpha = 0.5f)
                                    ),
                                    RoundedCornerShape(12.dp)
                                ),
                            color = if (isSelected) ZorbiPrimaryCyan.copy(alpha = 0.08f) else ZorbiSurface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Game Icon badge
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) ZorbiPrimaryCyan.copy(alpha = 0.2f)
                                                else ZorbiSurfaceElevated
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (isSelected) ZorbiPrimaryCyan else ZorbiTextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = game.name,
                                                style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = ZorbiTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(ZorbiPrimaryCyan)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${game.compatibility} • ${game.orientation}",
                                            style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = if (isSelected) ZorbiPrimaryCyan else ZorbiTextMuted,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Interactive toggle switch
                                Switch(
                                    checked = game.isEnabled,
                                    onCheckedChange = { checked ->
                                        haptics.toggle()
                                        gamesList[index] = game.copy(isEnabled = checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ZorbiPrimaryCyan,
                                        checkedTrackColor = ZorbiPrimaryCyan.copy(alpha = 0.3f),
                                        uncheckedThumbColor = ZorbiTextMuted,
                                        uncheckedTrackColor = ZorbiSurfaceElevated
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = ZorbiBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // SECTION B: QUICK CONTROLS
                    DrawerSectionHeader(
                        title = "QUICK CONTROLS",
                        subtitle = "Hardware overlay & haptics"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    QuickToggleRow(
                        title = "Gesture Controls",
                        subtitle = "Front camera hand tracking",
                        checked = gestureControlsEnabled,
                        onCheckedChange = {
                            haptics.toggle()
                            gestureControlsEnabled = it
                        }
                    )
                    QuickToggleRow(
                        title = "HUD Overlay",
                        subtitle = "In-game steering & crosshair",
                        checked = hudOverlayEnabled,
                        onCheckedChange = {
                            haptics.toggle()
                            hudOverlayEnabled = it
                        }
                    )
                    QuickToggleRow(
                        title = "Haptic Feedback",
                        subtitle = "Vibration on gesture trigger",
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = {
                            haptics.toggle()
                            hapticFeedbackEnabled = it
                        }
                    )
                    QuickToggleRow(
                        title = "Floating HUD",
                        subtitle = "Picture-in-picture mini badge",
                        checked = floatingHudEnabled,
                        onCheckedChange = {
                            haptics.toggle()
                            floatingHudEnabled = it
                        }
                    )
                    QuickToggleRow(
                        title = "Performance Monitor",
                        subtitle = "Live FPS & latency stats",
                        checked = perfMonitoringEnabled,
                        onCheckedChange = {
                            haptics.toggle()
                            perfMonitoringEnabled = it
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = ZorbiBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // SECTION C: PERFORMANCE
                    DrawerSectionHeader(
                        title = "PERFORMANCE MODE",
                        subtitle = "Tune CV processing speed"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PerformancePreset.values().forEach { mode ->
                        val isSelected = selectedPerfMode == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptics.click()
                                    selectedPerfMode = mode
                                }
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) ZorbiPrimaryCyan else ZorbiBorder.copy(alpha = 0.4f)
                                    ),
                                    RoundedCornerShape(10.dp)
                                ),
                            color = if (isSelected) ZorbiPrimaryCyan.copy(alpha = 0.12f) else ZorbiSurface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = mode.title,
                                        style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) ZorbiPrimaryCyan else ZorbiTextPrimary
                                    )
                                    Text(
                                        text = mode.description,
                                        style = ZorbiTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = ZorbiTextMuted
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = ZorbiPrimaryCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = ZorbiBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // SECTION E: OTHER NAVIGATION
                    DrawerSectionHeader(
                        title = "NAVIGATION",
                        subtitle = "Direct screen shortcuts"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    DrawerNavRow(
                        title = "Game Library",
                        subtitle = "Browse & configure profiles",
                        icon = Icons.Default.PlayArrow,
                        onClick = {
                            haptics.click()
                            onDismissRequest()
                            onNavigateToLibrary()
                        }
                    )
                    DrawerNavRow(
                        title = "Calibrate Profile",
                        subtitle = "60-second steering tuner",
                        icon = Icons.Default.Refresh,
                        onClick = {
                            haptics.click()
                            onDismissRequest()
                            onNavigateToCalibrate(activePackageName)
                        }
                    )
                    DrawerNavRow(
                        title = "Custom Mapping Editor",
                        subtitle = "Drag targets & tune gestures",
                        icon = Icons.Default.Edit,
                        onClick = {
                            haptics.click()
                            onDismissRequest()
                            onNavigateToEditor(activePackageName)
                        }
                    )
                    DrawerNavRow(
                        title = "Community Cloud",
                        subtitle = "Browse community configs",
                        icon = Icons.Default.Share,
                        onClick = {
                            haptics.click()
                            onDismissRequest()
                            onNavigateToCommunity()
                        }
                    )
                    DrawerNavRow(
                        title = "System Settings",
                        subtitle = "Audio, haptics & telemetry",
                        icon = Icons.Default.Settings,
                        onClick = {
                            haptics.click()
                            onDismissRequest()
                            onNavigateToSettings()
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = ZorbiBorder.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // SECTION D: INFORMATION
                    DrawerSectionHeader(
                        title = "INFORMATION",
                        subtitle = "Privacy & system version"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    DrawerNavRow(
                        title = "About ZORBI",
                        subtitle = "Team credits & architecture",
                        icon = Icons.Default.Info,
                        onClick = {
                            haptics.click()
                            onDismissRequest()
                            onNavigateToAbout()
                        }
                    )
                    DrawerNavRow(
                        title = "Privacy & Security",
                        subtitle = "100% on-device CV guarantee",
                        icon = Icons.Default.Lock,
                        onClick = {
                            haptics.click()
                            showPrivacyDialog = true
                        }
                    )
                    DrawerNavRow(
                        title = "Help & Support",
                        subtitle = "Setup guidance & gestures",
                        icon = Icons.Default.Star,
                        onClick = {
                            haptics.click()
                            showHelpDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Version badge
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        color = ZorbiSurfaceElevated,
                        border = BorderStroke(1.dp, ZorbiBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "App Version",
                                style = ZorbiTheme.typography.labelSmall,
                                color = ZorbiTextMuted
                            )
                            Text(
                                text = "0.1.0-alpha",
                                style = ZorbiMetricTypography.monoMedium.copy(fontSize = 11.sp),
                                color = ZorbiPrimaryCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = "ZORBI Gesture Help",
                    style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiPrimaryCyan
                )
            },
            text = {
                Text(
                    text = "• Place phone on a stable surface 30-60cm from hands.\n" +
                            "• Ensure good front-facing lighting without strong backlights.\n" +
                            "• Steer with 1 or 2 hands depending on game profile.\n" +
                            "• Make a tight fist to pause motion injection anytime.",
                    style = ZorbiTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                    color = ZorbiTextPrimary
                )
            },
            confirmButton = {
                Text(
                    text = "GOT IT",
                    style = ZorbiTheme.typography.labelLarge.copy(color = ZorbiPrimaryCyan),
                    modifier = Modifier
                        .clickable { showHelpDialog = false }
                        .padding(8.dp)
                )
            },
            containerColor = ZorbiSurfaceElevated
        )
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Text(
                    text = "100% On-Device Privacy",
                    style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiSuccess
                )
            },
            text = {
                Text(
                    text = "• All camera frames are processed directly inside device RAM.\n" +
                            "• Zero camera frames or photos are saved to storage or sent over the internet.\n" +
                            "• Gesture injection operates entirely offline without an account.\n" +
                            "• Optional anonymous telemetry is disabled by default.",
                    style = ZorbiTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                    color = ZorbiTextPrimary
                )
            },
            confirmButton = {
                Text(
                    text = "UNDERSTOOD",
                    style = ZorbiTheme.typography.labelLarge.copy(color = ZorbiSuccess),
                    modifier = Modifier
                        .clickable { showPrivacyDialog = false }
                        .padding(8.dp)
                )
            },
            containerColor = ZorbiSurfaceElevated
        )
    }
}

@Composable
private fun DrawerSectionHeader(
    title: String,
    subtitle: String
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ZorbiPrimaryCyan)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = ZorbiTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = ZorbiTextPrimary
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = ZorbiTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = ZorbiTextMuted
        )
    }
}

@Composable
private fun QuickToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ZorbiTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = ZorbiTextPrimary
            )
            Text(
                text = subtitle,
                style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = ZorbiTextMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ZorbiPrimaryCyan,
                checkedTrackColor = ZorbiPrimaryCyan.copy(alpha = 0.35f),
                uncheckedThumbColor = ZorbiTextMuted,
                uncheckedTrackColor = ZorbiSurfaceElevated
            )
        )
    }
}

@Composable
private fun DrawerNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .border(BorderStroke(1.dp, ZorbiBorder.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
        color = ZorbiSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ZorbiSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ZorbiPrimaryCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ZorbiTextPrimary
                )
                Text(
                    text = subtitle,
                    style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = ZorbiTextMuted
                )
            }
            Text(
                text = "›",
                style = ZorbiTheme.typography.titleMedium,
                color = ZorbiTextMuted
            )
        }
    }
}
