package com.zorbi.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zorbi.core.AppGraph
import com.zorbi.core.EngineState
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.ProfileOrientation
import com.zorbi.core.ProfileParams
import com.zorbi.core.TargetPoint
import com.zorbi.core.TrackerId
import com.zorbi.ui.R
import com.zorbi.ui.animations.interactiveBounce
import com.zorbi.ui.animations.pulsingGlow
import com.zorbi.ui.components.StatusLevel
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.components.ZorbiEngineStateBadge
import com.zorbi.ui.components.ZorbiGlassCard
import com.zorbi.ui.components.ZorbiMappingBadge
import com.zorbi.ui.components.ZorbiOutlinedButton
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiRightDrawer
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.components.ZorbiSectionHeader
import com.zorbi.ui.components.ZorbiStatusChip
import com.zorbi.ui.components.ZorbiStatusDot
import com.zorbi.ui.components.ZorbiTrackerBadge
import com.zorbi.ui.haptics.rememberZorbiHaptics
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.theme.ZorbiError
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
import com.zorbi.ui.theme.ZorbiWarning
import com.zorbi.ui.theme.monoMedium
import com.zorbi.ui.util.GameLauncher
import kotlinx.coroutines.launch

/**
 * State representation for the Home screen system status.
 */
data class HomeSystemStatus(
    val isCameraGranted: Boolean = false,
    val isAccessibilityGranted: Boolean = false,
    val isBatteryOptimized: Boolean = false
) {
    val isAllGreen: Boolean
        get() = isCameraGranted && isAccessibilityGranted && isBatteryOptimized
}

/**
 * Preview mock game profile for quick carousel.
 */
data class QuickGamePreview(
    val title: String,
    val packageName: String,
    val mapping: String,
    val orientation: String,
    val fpsTarget: String,
    val isSimulated: Boolean = false
)

/**
 * Redesigned Premium Gaming Home Screen for ZORBI.
 *
 * Implements:
 * 1. Top Section: ZORBI cyberpunk branding, welcome message, status badge, notification icon, right menu button.
 * 2. Main Section: Large attractive PLAY NOW hero card with dual cyan/violet neon glow, selected game details, readiness indicator.
 * 3. Below: Active Games preview carousel, real-time activity/telemetry stats, calibration status card, quick access to community.
 * 4. Right-side slide-out menu drawer: full slide-out panel with Active Games, Quick Controls, Performance modes, Info & Navigation.
 */
@Composable
fun HomeScreen(
    appGraph: AppGraph,
    onNavigateToLibrary: () -> Unit,
    onNavigateToWizard: () -> Unit,
    onNavigateToCalibrate: (String) -> Unit,
    onNavigateToPlay: (String) -> Unit,
    onNavigateToCommunity: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToEditor: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberZorbiHaptics()

    // Engine & Injector flows from AppGraph
    val engineState by appGraph.engine.state.collectAsState(initial = EngineState.IDLE)
    val isInjectorReady by appGraph.injector.isReady.collectAsState(initial = false)

    var systemStatus by remember { mutableStateOf(HomeSystemStatus()) }
    var loadedProfiles by remember { mutableStateOf<List<GameProfile>>(emptyList()) }
    var activePackageName by remember { mutableStateOf("com.kiloo.subwaysurf") }
    var isLoadingProfile by remember { mutableStateOf(true) }

    // Right-side slide-out drawer state
    var isDrawerOpen by remember { mutableStateOf(false) }

    // Notifications / Tips dialog state
    var showNotificationsDialog by remember { mutableStateOf(false) }

    // Game launch uninstalled warning dialog state
    var uninstalledGamePackage by remember { mutableStateOf<String?>(null) }

    // Helper to evaluate runtime permissions and injector state
    fun checkSystemStatus() {
        val hasCamera = try {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
        val isBatteryExempt = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (e: Exception) {
            false
        }
        systemStatus = HomeSystemStatus(
            isCameraGranted = hasCamera,
            isAccessibilityGranted = isInjectorReady,
            isBatteryOptimized = isBatteryExempt
        )
    }

    LaunchedEffect(isInjectorReady) {
        checkSystemStatus()
    }

    // Load available profiles from repository and initialize defaults if empty
    LaunchedEffect(Unit) {
        isLoadingProfile = true
        try {
            var profiles = appGraph.profiles.list()
            if (profiles.isEmpty()) {
                val defaults = listOf(
                    GameProfile(
                        packageName = "com.kiloo.subwaysurf",
                        displayName = "Subway Surfers",
                        orientation = ProfileOrientation.PORTRAIT,
                        tracker = TrackerId.HAND_SINGLE,
                        mapping = MappingType.LANE_SWIPE,
                        params = ProfileParams(cooldownMs = 350L, swipeMinSpeed = 1.2f)
                    ),
                    GameProfile(
                        packageName = "com.fingersoft.hillclimb",
                        displayName = "Hill Climb Racing",
                        orientation = ProfileOrientation.LANDSCAPE,
                        tracker = TrackerId.HAND_SINGLE,
                        mapping = MappingType.STEER_BUTTONS,
                        params = ProfileParams(deadzoneDeg = 8.0f)
                    ),
                    GameProfile(
                        packageName = "com.vectorunit.purple.googleplay",
                        displayName = "Beach Buggy Racing",
                        orientation = ProfileOrientation.LANDSCAPE,
                        tracker = TrackerId.HAND_DUAL_WHEEL,
                        mapping = MappingType.STEER_BUTTONS,
                        params = ProfileParams(deadzoneDeg = 8.0f),
                        targets = mapOf(
                            "left" to TargetPoint(0.15f, 0.75f),
                            "right" to TargetPoint(0.85f, 0.75f)
                        )
                    ),
                    GameProfile(
                        packageName = "com.halfbrick.fruitninja",
                        displayName = "Fruit Ninja",
                        orientation = ProfileOrientation.LANDSCAPE,
                        tracker = TrackerId.HAND_SINGLE,
                        mapping = MappingType.SLASH,
                        params = ProfileParams(slashMinLength = 0.15f)
                    ),
                    GameProfile(
                        packageName = "io.voodoo.paper2",
                        displayName = "Paper.io",
                        orientation = ProfileOrientation.PORTRAIT,
                        tracker = TrackerId.HAND_SINGLE,
                        mapping = MappingType.STEER_DRAG,
                        params = ProfileParams(deadzoneDeg = 5.0f, cooldownMs = 150L),
                        targets = mapOf(
                            "center" to TargetPoint(0.50f, 0.65f),
                            "left" to TargetPoint(0.20f, 0.65f),
                            "right" to TargetPoint(0.80f, 0.65f)
                        )
                    )
                )
                defaults.forEach { appGraph.profiles.saveLocal(it) }
                profiles = appGraph.profiles.list()
            }
            loadedProfiles = profiles
            val installedDefault = profiles.firstOrNull { GameLauncher.isGameInstalled(context, it.packageName) }
            if (installedDefault != null && !GameLauncher.isGameInstalled(context, activePackageName)) {
                activePackageName = installedDefault.packageName
            } else if (profiles.isNotEmpty() && profiles.none { it.packageName == activePackageName }) {
                activePackageName = profiles.first().packageName
            }
        } catch (e: Exception) {
            loadedProfiles = emptyList()
        } finally {
            isLoadingProfile = false
        }
    }

    // Refresh profiles whenever activePackageName changes
    LaunchedEffect(activePackageName) {
        try {
            val profiles = appGraph.profiles.list()
            if (profiles.isNotEmpty()) {
                loadedProfiles = profiles
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    // Current active game profile (matches ONLY activePackageName, never falls back to another game)
    val activeProfile = loadedProfiles.find { it.packageName == activePackageName }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ZorbiBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Top Section: Branding, Welcome, Readiness Pill, Notification & Right Menu
                HomeTopBar(
                    engineState = engineState,
                    isAllGreen = systemStatus.isAllGreen,
                    onNotificationClick = {
                        haptics.click()
                        showNotificationsDialog = true
                    },
                    onSettingsClick = {
                        haptics.click()
                        onNavigateToSettings()
                    },
                    onMenuClick = {
                        haptics.click()
                        isDrawerOpen = true
                    }
                )

                // 2. System Readiness Warning Banner (if setup incomplete)
                if (!systemStatus.isAllGreen) {
                    SystemReadinessBanner(
                        status = systemStatus,
                        onResolveClick = onNavigateToWizard
                    )
                }

                // 3. Compact System Status Chips (Camera, Service, Battery)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZorbiStatusChip(
                        label = "Camera",
                        isGranted = systemStatus.isCameraGranted,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToWizard
                    )
                    ZorbiStatusChip(
                        label = "Service",
                        isGranted = systemStatus.isAccessibilityGranted,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToWizard
                    )
                    ZorbiStatusChip(
                        label = "Battery",
                        isGranted = systemStatus.isBatteryOptimized,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToWizard
                    )
                }

                // 4. Main Section: Large Attractive "PLAY NOW" Card
                ZorbiSectionHeader(
                    title = "Active Game",
                    subtitle = "Selected motion control profile",
                    action = {
                        Text(
                            text = "Change",
                            style = ZorbiTheme.typography.labelMedium.copy(
                                color = ZorbiPrimaryCyan,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.click()
                                    onNavigateToLibrary()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                )

                HeroPlayCard(
                    profile = activeProfile,
                    fallbackPackageName = activePackageName,
                    isLoading = isLoadingProfile,
                    isReadyToPlay = systemStatus.isAllGreen,
                    onPlayClick = { pkg ->
                        haptics.confirm()
                        if (!GameLauncher.isGameInstalled(context, pkg)) {
                            uninstalledGamePackage = pkg
                            Toast.makeText(
                                context,
                                "Game '$pkg' is not installed on this device. Please install it from Google Play Store.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            onNavigateToPlay(pkg)
                        }
                    },
                    onCalibrateClick = { pkg ->
                        haptics.click()
                        onNavigateToCalibrate(pkg)
                    },
                    onEditMappingClick = { pkg ->
                        haptics.click()
                        onNavigateToEditor(pkg)
                    },
                    onSelectGameClick = onNavigateToLibrary
                )

                // 5. Active Games Preview Carousel
                ZorbiSectionHeader(
                    title = "Active Games Preview",
                    subtitle = "Fast switch supported gaming titles",
                    action = {
                        Text(
                            text = "View All",
                            style = ZorbiTheme.typography.labelMedium.copy(color = ZorbiAccentViolet),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.click()
                                    onNavigateToLibrary()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                )

                ActiveGamesPreviewRow(
                    activePackageName = activePackageName,
                    onSelectGame = { pkg ->
                        haptics.toggle()
                        activePackageName = pkg
                    }
                )

                // 6. Recent Game Activity & Real-Time Telemetry Stats
                RecentActivityCard(engineState = engineState)

                // 7. Calibration Status Card
                CalibrationStatusCard(
                    onCalibrateClick = {
                        haptics.click()
                        onNavigateToCalibrate(activePackageName)
                    }
                )

                // 8. Quick Access to Community Cloud
                CommunityHighlightCard(
                    onCommunityClick = {
                        haptics.click()
                        onNavigateToCommunity()
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Right-Side Slide-Out Menu Drawer
        ZorbiRightDrawer(
            isOpen = isDrawerOpen,
            onDismissRequest = { isDrawerOpen = false },
            onNavigateToLibrary = onNavigateToLibrary,
            onNavigateToWizard = onNavigateToWizard,
            onNavigateToCalibrate = onNavigateToCalibrate,
            onNavigateToCommunity = onNavigateToCommunity,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToAbout = onNavigateToAbout,
            onNavigateToEditor = onNavigateToEditor,
            activePackageName = activePackageName,
            onSelectActiveGame = { pkg ->
                activePackageName = pkg
            }
        )
    }

    // Notifications & Tips Dialog
    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = ZorbiPrimaryCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gaming Tips & Status",
                        style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiPrimaryCyan
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "• Pro-Tip: Prop your Vivo phone upright at a 75° angle for optimal hand tracking.",
                        style = ZorbiTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = ZorbiTextPrimary
                    )
                    Text(
                        text = "• High-Speed Response: ZORBI uses local MediaPipe CV delegates with sub-30ms input latency.",
                        style = ZorbiTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = ZorbiTextPrimary
                    )
                    Text(
                        text = "• Touchless Fist-Pause: Close either hand into a tight fist at any time to pause motion injection.",
                        style = ZorbiTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = ZorbiTextPrimary
                    )
                }
            },
            confirmButton = {
                Text(
                    text = "CLOSE",
                    style = ZorbiTheme.typography.labelLarge.copy(color = ZorbiPrimaryCyan),
                    modifier = Modifier
                        .clickable { showNotificationsDialog = false }
                        .padding(8.dp)
                )
            },
            containerColor = ZorbiSurfaceElevated
        )
    }

    // Game Not Installed Alert Dialog
    if (uninstalledGamePackage != null) {
        val missingPkg = uninstalledGamePackage ?: ""
        AlertDialog(
            onDismissRequest = { uninstalledGamePackage = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ZorbiWarning,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Game Not Installed",
                        style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiWarning
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The package \"$missingPkg\" is not installed on this device.",
                        style = ZorbiTheme.typography.bodyMedium,
                        color = ZorbiTextPrimary
                    )
                    Text(
                        text = "To play with ZORBI touchless controls, please install this game from Google Play Store or select an installed title like Hill Climb Racing.",
                        style = ZorbiTheme.typography.bodySmall,
                        color = ZorbiTextMuted
                    )
                }
            },
            confirmButton = {
                Text(
                    text = "DISMISS",
                    style = ZorbiTheme.typography.labelLarge.copy(color = ZorbiPrimaryCyan),
                    modifier = Modifier
                        .clickable { uninstalledGamePackage = null }
                        .padding(8.dp)
                )
            },
            dismissButton = {
                Text(
                    text = "VIEW ON PLAY STORE",
                    style = ZorbiTheme.typography.labelLarge.copy(color = ZorbiTextMuted),
                    modifier = Modifier
                        .clickable {
                            uninstalledGamePackage = null
                            try {
                                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$missingPkg")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(marketIntent)
                            } catch (e: Exception) {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$missingPkg")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(webIntent)
                            }
                        }
                        .padding(8.dp)
                )
            },
            containerColor = ZorbiSurfaceElevated
        )
    }
}

/**
 * Top branding bar with welcome message, live readiness status, notification bell, and right menu button.
 */
@Composable
private fun HomeTopBar(
    engineState: EngineState,
    isAllGreen: Boolean,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Branding & Welcome
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Official Zorbi Robot Mascot Logo Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                ZorbiPrimaryCyan.copy(alpha = 0.25f),
                                ZorbiAccentViolet.copy(alpha = 0.35f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.5.dp, ZorbiPrimaryCyan.copy(alpha = 0.7f)),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.zorbi_mascot_feathered),
                    contentDescription = "ZORBI Official Robot Mascot",
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ZORBI",
                        style = ZorbiTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = ZorbiPrimaryCyan
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ZorbiEngineStateBadge(state = engineState)
                }
                Spacer(modifier = Modifier.height(2.dp))
                // Readiness indicator pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isAllGreen) ZorbiSuccess else ZorbiWarning)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAllGreen) "ONLINE • READY TO PLAY" else "SETUP REQUIRED",
                        style = ZorbiMetricTypography.monoMedium.copy(
                            fontSize = 10.sp,
                            color = if (isAllGreen) ZorbiSuccess else ZorbiWarning
                        )
                    )
                }
            }
        }

        // Right Actions: Notification bell + Direct Settings Button + Right Slide-Out Menu Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Notification icon button
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNotificationClick),
                shape = CircleShape,
                color = ZorbiSurfaceElevated,
                border = BorderStroke(1.dp, ZorbiBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Tips & Notifications",
                        tint = ZorbiPrimaryCyan,
                        modifier = Modifier.size(19.dp)
                    )
                    // Notification dot
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .align(Alignment.TopEnd)
                            .padding(end = 2.dp, top = 2.dp)
                            .clip(CircleShape)
                            .background(ZorbiNeonMagenta)
                    )
                }
            }

            // Direct Settings icon button
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSettingsClick),
                shape = CircleShape,
                color = ZorbiSurfaceElevated,
                border = BorderStroke(1.dp, ZorbiBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "System Settings",
                        tint = ZorbiPrimaryCyan,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Right Menu Drawer Button (Prominent 48dp touch target)
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onMenuClick)
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(ZorbiPrimaryCyan.copy(alpha = 0.6f), ZorbiAccentViolet.copy(alpha = 0.4f))
                            )
                        ),
                        RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp),
                color = ZorbiSurfaceElevated
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Right Menu",
                        tint = ZorbiPrimaryCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Large attractive "PLAY NOW" Hero Card with dual-tone glow and action buttons.
 */
@Composable
private fun HeroPlayCard(
    profile: GameProfile?,
    fallbackPackageName: String,
    isLoading: Boolean,
    isReadyToPlay: Boolean,
    onPlayClick: (String) -> Unit,
    onCalibrateClick: (String) -> Unit,
    onEditMappingClick: (String) -> Unit = {},
    onSelectGameClick: () -> Unit
) {
    val haptics = rememberZorbiHaptics()
    val displayName = profile?.displayName ?: when (fallbackPackageName) {
        "com.fingersoft.hillclimb" -> "Hill Climb Racing"
        "com.vectorunit.purple.googleplay", "com.vectorunit.cobalt.googleplay" -> "Beach Buggy Racing"
        "io.voodoo.paper2", "io.voodoo.paperio" -> "Paper.io"
        "com.halfbrick.fruitninja" -> "Fruit Ninja"
        "com.mojang.minecraftpe" -> "Minecraft"
        "com.kiloo.subwaysurf" -> "Subway Surfers"
        "com.imangi.templerun2" -> "Temple Run 2"
        "com.pubg.imobile" -> "BGMI"
        "com.tencent.ig" -> "PUBG Mobile"
        "com.activision.callofduty.shooter" -> "Call of Duty Mobile"
        "com.dts.freefireth" -> "Free Fire"
        else -> fallbackPackageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
    val orientationName = profile?.orientation?.name ?: when (fallbackPackageName) {
        "com.fingersoft.hillclimb", "com.vectorunit.purple.googleplay", "com.vectorunit.cobalt.googleplay", "com.halfbrick.fruitninja", "com.mojang.minecraftpe" -> "LANDSCAPE"
        else -> "PORTRAIT"
    }
    val packageName = profile?.packageName ?: fallbackPackageName
    val hasConfiguredMapping = profile != null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                BorderStroke(
                    1.5.dp,
                    Brush.linearGradient(
                        listOf(
                            ZorbiPrimaryCyan.copy(alpha = 0.8f),
                            ZorbiAccentViolet.copy(alpha = 0.6f)
                        )
                    )
                ),
                RoundedCornerShape(18.dp)
            )
            .then(
                if (isReadyToPlay) {
                    Modifier.pulsingGlow(color = ZorbiPrimaryCyan, minAlpha = 0.05f, maxAlpha = 0.22f)
                } else Modifier
            ),
        shape = RoundedCornerShape(18.dp),
        color = ZorbiSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Top Tag & Orientation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ZorbiPrimaryCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ZorbiPrimaryCyan.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ZorbiPrimaryCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ACTIVE MOTION PROFILE",
                            style = ZorbiTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = ZorbiPrimaryCyan
                        )
                    }
                }

                ZorbiBadge(
                    text = orientationName,
                    accentColor = ZorbiAccentViolet
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Game artwork badge + Game Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Game Avatar / Artwork Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    ZorbiAccentViolet.copy(alpha = 0.35f),
                                    ZorbiPrimaryCyan.copy(alpha = 0.25f)
                                )
                            )
                        )
                        .border(1.dp, ZorbiPrimaryCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = ZorbiPrimaryCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = ZorbiTheme.typography.headlineMedium.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = ZorbiTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = packageName,
                        style = ZorbiMetricTypography.monoMedium.copy(
                            fontSize = 11.sp,
                            color = ZorbiTextMuted
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Capability Tags (Actual Tracker, Mapping, and FPS Target from active profile)
            if (profile != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZorbiTrackerBadge(trackerId = profile.tracker)
                    ZorbiMappingBadge(mapping = profile.mapping)
                    ZorbiBadge(text = "60 FPS Target", accentColor = ZorbiPrimaryCyan)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ZorbiSurfaceElevated,
                        border = BorderStroke(1.dp, ZorbiBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ZorbiWarning,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "No mapping configured",
                                style = ZorbiTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ZorbiWarning
                            )
                        }
                    }
                    ZorbiBadge(text = "Unmapped", accentColor = ZorbiTextMuted)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gesture readiness note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (!hasConfiguredMapping) ZorbiWarning.copy(alpha = 0.08f)
                        else if (isReadyToPlay) ZorbiSuccess.copy(alpha = 0.08f)
                        else ZorbiWarning.copy(alpha = 0.08f)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (!hasConfiguredMapping || !isReadyToPlay) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (!hasConfiguredMapping || !isReadyToPlay) ZorbiWarning else ZorbiSuccess,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (!hasConfiguredMapping) "Tap CUSTOM MAP below to configure motion controls for this game"
                    else if (isReadyToPlay) "Camera & Accessibility Ready • Zero Latency Injection"
                    else "Complete Setup Wizard to unlock in-game touchless control",
                    style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (!hasConfiguredMapping || !isReadyToPlay) ZorbiWarning else ZorbiSuccess
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main "PLAY NOW" Action Button
            val buttonText = when {
                !hasConfiguredMapping -> "CONFIGURE MAPPING TO PLAY"
                !isReadyToPlay -> "COMPLETE SETUP TO PLAY"
                else -> "LAUNCH & PLAY NOW"
            }
            ZorbiPrimaryButton(
                text = buttonText,
                leadingIcon = if (!hasConfiguredMapping) Icons.Default.Edit else Icons.Default.PlayArrow,
                enabled = isReadyToPlay || !hasConfiguredMapping,
                onClick = {
                    if (!hasConfiguredMapping) {
                        onEditMappingClick(packageName)
                    } else {
                        onPlayClick(packageName)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .interactiveBounce()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Calibrate & Custom Map Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZorbiSecondaryButton(
                    text = "CALIBRATE",
                    leadingIcon = Icons.Default.Refresh,
                    onClick = { onCalibrateClick(packageName) },
                    modifier = Modifier.weight(1.1f)
                )

                ZorbiOutlinedButton(
                    text = "CUSTOM MAP",
                    leadingIcon = Icons.Default.Edit,
                    onClick = { onEditMappingClick(packageName) },
                    modifier = Modifier.weight(1.0f)
                )
            }
        }
    }
}

/**
 * Horizontal preview carousel of popular supported games.
 */
@Composable
private fun ActiveGamesPreviewRow(
    activePackageName: String,
    onSelectGame: (String) -> Unit
) {
    val previewGames = listOf(
        QuickGamePreview("Subway Surfers", "com.kiloo.subwaysurf", "Lane Swipe", "PORTRAIT", "60 FPS"),
        QuickGamePreview("Hill Climb Racing", "com.fingersoft.hillclimb", "Steer Buttons", "LANDSCAPE", "60 FPS"),
        QuickGamePreview("Paper.io", "io.voodoo.paper2", "Steer Drag", "PORTRAIT", "60 FPS"),
        QuickGamePreview("Beach Buggy Racing", "com.vectorunit.purple.googleplay", "Steer Buttons", "LANDSCAPE", "60 FPS"),
        QuickGamePreview("Fruit Ninja", "com.halfbrick.fruitninja", "Air Slash", "LANDSCAPE", "60 FPS"),
        QuickGamePreview("Minecraft", "com.mojang.minecraftpe", "Hand Cursor", "LANDSCAPE", "60 FPS", true)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        previewGames.forEach { game ->
            val isSelected = game.packageName == activePackageName

            Surface(
                modifier = Modifier
                    .width(160.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectGame(game.packageName) }
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isSelected) ZorbiPrimaryCyan else ZorbiBorder.copy(alpha = 0.5f)
                        ),
                        RoundedCornerShape(14.dp)
                    ),
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) ZorbiPrimaryCyan.copy(alpha = 0.12f) else ZorbiSurfaceElevated
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) ZorbiPrimaryCyan.copy(alpha = 0.25f)
                                    else ZorbiBackground
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isSelected) ZorbiPrimaryCyan else ZorbiTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (isSelected) {
                            Surface(
                                shape = CircleShape,
                                color = ZorbiPrimaryCyan.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = ZorbiTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ZorbiPrimaryCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = game.title,
                        style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${game.mapping} • ${game.fpsTarget}",
                        style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isSelected) ZorbiPrimaryCyan else ZorbiTextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Real-Time Activity & Telemetry stats dashboard card.
 */
@Composable
private fun RecentActivityCard(engineState: EngineState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, ZorbiBorder), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = ZorbiSurface
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ZorbiPrimaryCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = ZorbiPrimaryCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pipeline Telemetry [DEMO]",
                        style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiTextPrimary
                    )
                }

                Text(
                    text = "STATUS: SIMULATED (${engineState.name})",
                    style = ZorbiMetricTypography.monoMedium.copy(fontSize = 10.sp),
                    color = ZorbiWarning
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Three stats pills: Latency, FPS, Gesture Confidence (clearly labeled as simulated targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricTile(
                    value = "150ms",
                    label = "Target < 50ms [DEMO]",
                    metricName = "Latency",
                    accentColor = ZorbiPrimaryCyan,
                    modifier = Modifier.weight(1f)
                )
                StatMetricTile(
                    value = "60 FPS",
                    label = "Target Rate [DEMO]",
                    metricName = "Pipeline",
                    accentColor = ZorbiSuccess,
                    modifier = Modifier.weight(1f)
                )
                StatMetricTile(
                    value = "99.2%",
                    label = "Filter Target [DEMO]",
                    metricName = "Smoothness",
                    accentColor = ZorbiAccentViolet,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Unconnected pipeline notice
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = ZorbiWarning,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Demo Mode: Live CameraX vision pipeline is not connected in this build • Stats are simulated targets",
                    style = ZorbiTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = ZorbiTextMuted
                )
            }
        }
    }
}

@Composable
private fun StatMetricTile(
    value: String,
    label: String,
    metricName: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, ZorbiBorder.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = ZorbiSurfaceElevated
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = ZorbiMetricTypography.monoMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = metricName,
                style = ZorbiTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = ZorbiTextPrimary
            )
            Text(
                text = label,
                style = ZorbiTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = ZorbiTextMuted
            )
        }
    }
}

/**
 * Dedicated Calibration status card.
 */
@Composable
private fun CalibrationStatusCard(
    onCalibrateClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onCalibrateClick)
            .border(BorderStroke(1.dp, ZorbiBorder), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = ZorbiSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ZorbiAccentViolet.copy(alpha = 0.2f))
                        .border(1.dp, ZorbiAccentViolet.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = ZorbiAccentViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Steering Calibration",
                        style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Quick 60s steering angle tuner for your device",
                        style = ZorbiTheme.typography.labelSmall,
                        color = ZorbiTextMuted
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCalibrateClick)
                    .border(BorderStroke(1.dp, ZorbiAccentViolet), RoundedCornerShape(8.dp)),
                color = ZorbiAccentViolet.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "CALIBRATE",
                    style = ZorbiTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = ZorbiAccentViolet,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Community cloud profile highlight card.
 */
@Composable
private fun CommunityHighlightCard(
    onCommunityClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onCommunityClick)
            .border(BorderStroke(1.dp, ZorbiBorder), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = ZorbiSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ZorbiSuccess.copy(alpha = 0.2f))
                        .border(1.dp, ZorbiSuccess.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = ZorbiSuccess,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Community Cloud Profiles",
                        style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Download 20+ verified motion profiles",
                        style = ZorbiTheme.typography.labelSmall,
                        color = ZorbiTextMuted
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCommunityClick)
                    .border(BorderStroke(1.dp, ZorbiSuccess), RoundedCornerShape(8.dp)),
                color = ZorbiSuccess.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "BROWSE",
                    style = ZorbiTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = ZorbiSuccess,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * System readiness banner when setup or permissions are required.
 */
@Composable
private fun SystemReadinessBanner(
    status: HomeSystemStatus,
    onResolveClick: () -> Unit
) {
    val isAllGreen = status.isAllGreen
    val bannerColor = if (isAllGreen) ZorbiSuccess else ZorbiWarning
    val bannerBg = if (isAllGreen) ZorbiSuccess.copy(alpha = 0.10f) else ZorbiWarning.copy(alpha = 0.10f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onResolveClick),
        shape = RoundedCornerShape(14.dp),
        color = bannerBg,
        border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = bannerColor.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isAllGreen) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = bannerColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAllGreen) "All Systems Operational" else "Setup Incomplete",
                    style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = bannerColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isAllGreen) "Front camera & accessibility service ready for gameplay."
                    else "Tap to open Setup Wizard and grant required permissions.",
                    style = ZorbiTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = ZorbiTextPrimary
                )
            }

            Text(
                text = "›",
                style = ZorbiTheme.typography.titleLarge,
                color = bannerColor
            )
        }
    }
}


