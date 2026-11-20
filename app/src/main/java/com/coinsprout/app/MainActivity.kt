package com.coinsprout.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner as UiLocalLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner as LifecycleLocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.coinsprout.app.navigation.CoinSproutNavGraph
import com.coinsprout.app.ui.theme.CoinSproutTheme
import com.coinsprout.app.ui.theme.Cream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is light-only for now, so the system bars always use dark icons. (The old code forced the whole
        // app's night mode through UiModeManager, which overrode the user's system choice for every screen.)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        setContent {
            CoinSproutTheme {
                CompositionLocalProvider(
                    LifecycleLocalLifecycleOwner provides UiLocalLifecycleOwner.current
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Cream) {
                        CoinSproutNavGraph(rememberNavController())
                    }
                }
            }
        }
    }
}
