package com.bbbjam

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.info.InfoScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark only (DESIGN.md), so the system bars always use the dark style (light
        // icons). The default SystemBarStyle.auto follows the system night mode and would draw
        // dark icons and a light navigation scrim over the dark background when the device is in
        // light mode.
        val darkBars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = darkBars, navigationBarStyle = darkBars)
        super.onCreate(savedInstanceState)
        // Info is the only screen until bottom-navigation hosts the tabs and passes its inner padding.
        setContent { BluesJamTheme { InfoScreen(contentPadding = WindowInsets.systemBars.asPaddingValues()) } }
    }
}
