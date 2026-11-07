package com.zorbi.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.ui.navigation.ZorbiNavHost
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiError
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG_STARTUP, "MainActivity onCreate started [action=${intent?.action}]")

        val app = application as? ZorbiApp

        setContent {
            Log.i(TAG_STARTUP, "Compose setContent entered")
            ZorbiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ZorbiBackground
                ) {
                    var currentGraphState by remember {
                        mutableStateOf(app?.graphState ?: ZorbiApp.AppGraphState.Loading)
                    }

                    when (val state = currentGraphState) {
                        is ZorbiApp.AppGraphState.Ready -> {
                            ZorbiNavHost(appGraph = state.graph)
                        }
                        is ZorbiApp.AppGraphState.Error -> {
                            StartupErrorScreen(
                                cause = state.cause,
                                onRetry = {
                                    app?.retryInitialization()
                                    currentGraphState = app?.graphState ?: ZorbiApp.AppGraphState.Loading
                                }
                            )
                        }
                        is ZorbiApp.AppGraphState.Loading -> {
                            StartupLoadingScreen()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG_STARTUP = "ZORBI_STARTUP"
    }
}

@Composable
private fun StartupLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZorbiBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = ZorbiPrimaryCyan,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Starting Zorbi...",
                color = ZorbiTextMuted,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun StartupErrorScreen(
    cause: Throwable,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZorbiBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ZorbiSurfaceElevated, shape = RoundedCornerShape(16.dp))
                .border(1.dp, ZorbiError.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Startup Warning",
                tint = ZorbiError,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Startup Initialization Error",
                color = ZorbiTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = cause.localizedMessage ?: cause.javaClass.simpleName,
                color = ZorbiTextMuted,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = ZorbiPrimaryCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "RETRY INITIALIZATION",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
