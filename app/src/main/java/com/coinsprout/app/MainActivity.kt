package com.coinsprout.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.coinsprout.app.navigation.CoinSproutNavGraph
import com.coinsprout.app.state.AppState
import com.coinsprout.app.ui.theme.CoinSproutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoinSproutTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val appState = remember { AppState() }
                    CoinSproutNavGraph(navController = navController, appState = appState)
                }
            }
        }
    }
}
