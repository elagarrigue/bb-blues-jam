package com.bbbjam

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark only (DESIGN.md), so the system bars always use the dark style (light
        // icons). The default SystemBarStyle.auto follows the system night mode and would draw
        // dark icons and a light navigation scrim over the dark background when the device is in
        // light mode.
        val darkBars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = darkBars, navigationBarStyle = darkBars)
        super.onCreate(savedInstanceState)
        // AppNavHost hosts the temporary tabs (Próxima jam and Info) and the song detail.
        setContent { BluesJamTheme { AppNavHost() } }
    }
}
