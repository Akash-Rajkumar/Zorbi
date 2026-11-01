package com.zorbi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zorbi.ui.navigation.AppNavigation
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ZorbiApp
        val appGraph = app.appGraph

        setContent {
            ZorbiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ZorbiBackground
                ) {
                    AppNavigation(appGraph = appGraph)
                }
            }
        }
    }
}
