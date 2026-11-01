package com.zorbi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zorbi.core.AppGraph
import com.zorbi.core.EngineState
import com.zorbi.core.HudFrame
import com.zorbi.core.PerfMode
import com.zorbi.engine.ZorbiEngineService
import com.zorbi.gesture.GestureDiagnostics
import com.zorbi.gesture.LaneSwipeMapper
import com.zorbi.ui.theme.ZorbiTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val zorbiApp = application as ZorbiApp
        val appGraph = zorbiApp.appGraph

        setContent {
            ZorbiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        appGraph = appGraph,
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    appGraph: AppGraph,
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val engineState by appGraph.engine.state.collectAsState()
    val isAccessibilityReady by appGraph.injector.isReady.collectAsState()
    val hudFrame by appGraph.engine.hud.collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ZORBI Pipeline",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Camera Permission Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (hasCameraPermission) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Camera Permission",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (hasCameraPermission) "Granted" else "Required for Vision Pipeline",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!hasCameraPermission) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant")
                    }
                }
            }
        }

        // Accessibility Service Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAccessibilityReady) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Input Injector Service",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isAccessibilityReady) "Connected & Ready" else "Disabled in Settings",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!isAccessibilityReady) {
                    Button(onClick = onOpenAccessibilitySettings) {
                        Text("Enable")
                    }
                }
            }
        }

        // Engine State Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Engine State: $engineState",
                    style = MaterialTheme.typography.titleMedium,
                    color = when (engineState) {
                        EngineState.RUNNING -> MaterialTheme.colorScheme.primary
                        EngineState.PAUSED -> MaterialTheme.colorScheme.tertiary
                        EngineState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = engineState == EngineState.IDLE && hasCameraPermission,
                        onClick = {
                            coroutineScope.launch {
                                ZorbiEngineService.start(context)
                                LaneSwipeMapper.activeSwipeDurationMs = LaneSwipeMapper.OPTIMIZED_SWIPE_DURATION_MS
                                appGraph.engine.start("com.kiloo.subwaysurf", PerfMode.BALANCED)
                            }
                        }
                    ) {
                        Text("Start Engine")
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = engineState == EngineState.RUNNING || engineState == EngineState.PAUSED,
                        onClick = {
                            coroutineScope.launch {
                                appGraph.engine.stop()
                                ZorbiEngineService.stop(context)
                            }
                        }
                    ) {
                        Text("Stop Engine")
                    }
                }

                if (engineState == EngineState.RUNNING) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.kiloo.subwaysurf")
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        }
                    ) {
                        Text("Launch Subway Surfers")
                    }
                }
            }
        }

        // Diagnostic Gesture HUD Card (Gate Diagnostic HUD)
        val diag by GestureDiagnostics.data.collectAsState()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (diag.handDetected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Live Diagnostic Tracker",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (diag.handDetected) "Hand: DETECTED (${diag.handSide}, conf=${"%.2f".format(diag.handScore)})" else "Hand: NONE DETECTED",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Palm: (${"%.2f".format(diag.palmX)}, ${"%.2f".format(diag.palmY)}) | Wrist: (${"%.2f".format(diag.wristX)}, ${"%.2f".format(diag.wristY)})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Motion: dist=${"%.3f".format(diag.distance)}/${diag.minDistance} | spd=${"%.2f".format(diag.speed)}/${diag.minSpeed} (${diag.windowDurationMs}ms)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Direction: ${diag.candidateDir} | Status: ${diag.status}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Last Recognized: ${diag.lastRecognizedGesture}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Last Injection: ${diag.lastInjectionResult}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Live HUD Telemetry Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Live HUD Feed",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tracker: ${hudFrame?.trackerId?.name ?: "None"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "FPS: ${"%.1f".format(hudFrame?.fps ?: 0f)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Latency: ${"%.1f".format(hudFrame?.e2eLatencyMs ?: 0f)} ms",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Landmarks: ${hudFrame?.landmarks?.size ?: 0}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Last Gesture: ${hudFrame?.lastGesture?.let { it::class.simpleName } ?: "None"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
