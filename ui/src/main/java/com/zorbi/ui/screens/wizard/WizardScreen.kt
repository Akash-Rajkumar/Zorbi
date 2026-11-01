package com.zorbi.ui.screens.wizard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zorbi.core.AppGraph
import com.zorbi.ui.components.StatusLevel
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.components.ZorbiGlassCard
import com.zorbi.ui.components.ZorbiOutlinedButton
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiScaffold
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.components.ZorbiSectionHeader
import com.zorbi.ui.components.ZorbiStatusDot
import com.zorbi.ui.screens.settings.SettingsStorage
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiCardShape
import com.zorbi.ui.theme.ZorbiError
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextDisabled
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import com.zorbi.ui.theme.ZorbiWarning

/**
 * P3-2 Setup Wizard Screen:
 * - Accessibility Service activation with genuine Android settings intent
 * - Android 13+ "Restricted Settings" guidance dialog
 * - Camera runtime permission request
 * - Notification permission handling (Android 13+ / API 33)
 * - Vivo / OriginOS aggressive battery killing & autostart instructions
 * - Live status checks on resume
 * - True granted/denied states with zero mock/fakes in production paths
 */
@Composable
fun WizardScreen(
    appGraph: AppGraph,
    onNavigateBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val storage = remember {
        try {
            SettingsStorage.fromContext(context)
        } catch (e: Exception) {
            null
        }
    }

    // Live reactive injector state from AppGraph
    val isInjectorReady by appGraph.injector.isReady.collectAsState(initial = false)

    var wizardState by remember { mutableStateOf(WizardState()) }
    var showRestrictedDialog by remember { mutableStateOf(false) }
    var showVivoDialog by remember { mutableStateOf(false) }

    fun refreshStatus() {
        wizardState = WizardSystemHelper.checkStatus(context, isInjectorReady)
    }

    // Lifecycle observer to re-check on resume when returning from Android Settings
    DisposableEffect(lifecycleOwner, isInjectorReady) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshStatus()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshStatus()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshStatus()
    }

    // Modal Dialogs
    if (showRestrictedDialog) {
        RestrictedSettingsDialog(onDismiss = { showRestrictedDialog = false })
    }
    if (showVivoDialog) {
        VivoGuidanceDialog(onDismiss = { showVivoDialog = false })
    }

    ZorbiScaffold(
        title = "Setup Wizard",
        subtitle = "Permissions & System Readiness",
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        modifier = modifier
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall Progress Card
            WizardProgressCard(
                grantedCount = wizardState.grantedCount,
                totalSteps = wizardState.totalSteps,
                isAllGreen = wizardState.isAllGreen
            )

            // Step 1: Accessibility Service
            WizardStepCard(
                stepNumber = "1",
                title = "Accessibility Touch Injection",
                description = "Allows Zorbi to deliver touch and swipe controls into games without touching the screen.",
                isGranted = wizardState.isAccessibilityGranted,
                actionButtonText = if (wizardState.isAccessibilityGranted) "SERVICE CONNECTED" else "ENABLE IN SETTINGS",
                onActionClick = {
                    WizardSystemHelper.openAccessibilitySettings(context)
                },
                secondaryAction = {
                    if (!wizardState.isAccessibilityGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ZorbiOutlinedButton(
                            text = "Android 13+ Restricted Settings Help?",
                            leadingIcon = Icons.Default.Lock,
                            onClick = { showRestrictedDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )

            // Step 2: Camera Permission
            WizardStepCard(
                stepNumber = "2",
                title = "Front Camera Vision",
                description = "Tracks hand and upper-body movements in real-time. All vision runs on-device; no video ever leaves your phone.",
                isGranted = wizardState.isCameraGranted,
                actionButtonText = if (wizardState.isCameraGranted) "CAMERA GRANTED" else "GRANT CAMERA PERMISSION",
                onActionClick = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )

            // Step 3: Foreground Notification
            WizardStepCard(
                stepNumber = "3",
                title = "Foreground Service Notification",
                description = "Keeps the motion controller active in the foreground while your game is in front.",
                isGranted = wizardState.isNotificationGranted,
                actionButtonText = if (wizardState.isNotificationGranted) "NOTIFICATION GRANTED" else "ALLOW NOTIFICATIONS",
                onActionClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        refreshStatus()
                    }
                }
            )

            // Step 4: Battery Optimization & Power
            WizardStepCard(
                stepNumber = "4",
                title = "Background Power Management",
                description = "Exempts Zorbi from aggressive Android battery saving so gesture controls are never killed mid-game.",
                isGranted = wizardState.isBatteryOptimized,
                actionButtonText = if (wizardState.isBatteryOptimized) "BATTERY EXEMPTED" else "DISABLE BATTERY RESTRICTION",
                onActionClick = {
                    WizardSystemHelper.openBatteryOptimizationSettings(context)
                }
            )

            // Vivo / OriginOS Guidance Card
            VivoGuidanceBanner(
                isVivoDevice = WizardSystemHelper.isVivoDevice(),
                onClick = { showVivoDialog = true }
            )

            // Completion Card / Finish CTA
            AnimatedVisibility(
                visible = wizardState.isAllGreen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ZorbiGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = ZorbiSuccess
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ZorbiSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "All Systems Ready!",
                            style = ZorbiTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = ZorbiSuccess
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Every required permission and service is active. You can now play motion games or calibrate custom profiles.",
                        style = ZorbiTheme.typography.bodyMedium,
                        color = ZorbiTextPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ZorbiPrimaryButton(
                        text = "FINISH & RETURN TO HOME",
                        leadingIcon = Icons.Default.Check,
                        onClick = {
                            storage?.saveWizardDone(true)
                            onFinish()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Skip button: allows user to proceed to Home even if setup is not fully green
            if (!wizardState.isAllGreen) {
                ZorbiSecondaryButton(
                    text = "SKIP FOR NOW & CONTINUE TO HOME",
                    onClick = {
                        storage?.saveWizardDone(true)
                        onFinish()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WizardProgressCard(
    grantedCount: Int,
    totalSteps: Int,
    isAllGreen: Boolean
) {
    val progress = grantedCount.toFloat() / totalSteps.toFloat()
    val progressColor = if (isAllGreen) ZorbiSuccess else ZorbiPrimaryCyan

    ZorbiGlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isAllGreen) ZorbiSuccess.copy(alpha = 0.6f) else ZorbiBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isAllGreen) "Setup Complete" else "Setup in Progress",
                    style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiTextPrimary
                )
                Text(
                    text = "$grantedCount of $totalSteps requirements met",
                    style = ZorbiTheme.typography.bodySmall,
                    color = ZorbiTextMuted
                )
            }
            ZorbiBadge(
                text = if (isAllGreen) "ALL GREEN" else "$grantedCount / $totalSteps",
                accentColor = progressColor
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = progressColor,
            trackColor = ZorbiSurfaceElevated
        )
    }
}

@Composable
private fun WizardStepCard(
    stepNumber: String,
    title: String,
    description: String,
    isGranted: Boolean,
    actionButtonText: String,
    onActionClick: () -> Unit,
    secondaryAction: (@Composable () -> Unit)? = null
) {
    val statusColor = if (isGranted) ZorbiSuccess else ZorbiWarning

    ZorbiGlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isGranted) ZorbiSuccess.copy(alpha = 0.4f) else ZorbiBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isGranted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Granted",
                            tint = ZorbiSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = stepNumber,
                            style = ZorbiTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiTextPrimary
                )
            }
            ZorbiBadge(
                text = if (isGranted) "GRANTED" else "REQUIRED",
                accentColor = statusColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = ZorbiTheme.typography.bodySmall,
            color = ZorbiTextMuted
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (isGranted) {
            ZorbiOutlinedButton(
                text = actionButtonText,
                leadingIcon = Icons.Default.Check,
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            ZorbiPrimaryButton(
                text = actionButtonText,
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (secondaryAction != null) {
            secondaryAction()
        }
    }
}

@Composable
private fun VivoGuidanceBanner(
    isVivoDevice: Boolean,
    onClick: () -> Unit
) {
    ZorbiGlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isVivoDevice) ZorbiPrimaryCyan.copy(alpha = 0.6f) else ZorbiBorder,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = ZorbiPrimaryCyan.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, ZorbiPrimaryCyan.copy(alpha = 0.4f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ZorbiPrimaryCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isVivoDevice) "Vivo / OriginOS Detected" else "Vivo / OEM Battery Optimization",
                    style = ZorbiTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiTextPrimary
                )
                Text(
                    text = "Configure Autostart & High Background Power to avoid service kills.",
                    style = ZorbiTheme.typography.bodySmall,
                    color = ZorbiTextMuted
                )
            }
            ZorbiBadge(
                text = "GUIDE",
                accentColor = ZorbiPrimaryCyan
            )
        }
    }
}
