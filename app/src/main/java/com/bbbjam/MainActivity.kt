package com.bbbjam

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.navigation.AppNavHost
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark only (DESIGN.md), so the system bars always use the dark style (light
        // icons). The default SystemBarStyle.auto follows the system night mode and would draw
        // dark icons and a light navigation scrim over the dark background when the device is in
        // light mode.
        val darkBars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = darkBars, navigationBarStyle = darkBars)
        super.onCreate(savedInstanceState)
        // AppNavHost hosts the tabs shell (bottom bar, one route per tab) and the song detail above it.
        // The admin flag is read once here (admin-mode-highlight), the same observeIsAdmin()
        // pattern every admin-aware presenter already uses (D-15), and passed down so the admin
        // background/banner apply app-wide with no per-screen `if (isAdmin)` branch.
        setContent {
            val adminSession = koinInject<AdminSession>()
            val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = false)
            BluesJamTheme(admin = isAdmin) { AppNavHost(admin = isAdmin) }
        }
    }
}
