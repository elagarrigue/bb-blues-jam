package com.bbbjam

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.bbbjam.core.assistant.AssistantStatus
import com.bbbjam.core.assistant.SetlistAssistant
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.navigation.AppNavHost
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: the system splash shows only the window color and then
        // applies postSplashScreenTheme (Theme.BBBluesJam); AppSplash draws the large logo.
        installSplashScreen()
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
            // null until the flag is read: the Asistente tab neither shows nor redirects before then.
            val isAdmin: Boolean? by remember { adminSession.observeIsAdmin() }.collectAsState(initial = null)
            val assistant = koinInject<SetlistAssistant>()
            // The Asistente tab shows only when the gateway answers at startup (or at login): one token-free
            // status read, made only for the admin. Saved so a rotation does not read again; the tab itself
            // reads the status again each time it is entered.
            var assistantAvailable: Boolean? by rememberSaveable { mutableStateOf(null) }
            LaunchedEffect(isAdmin) {
                when (isAdmin) {
                    true -> if (assistantAvailable == null) {
                        assistantAvailable = assistant.checkStatus() == AssistantStatus.Available
                    }

                    false -> assistantAvailable = null

                    null -> Unit
                }
            }
            // The splash plays once per cold start: a warm start (the activity finished with back, then
            // reopened while the process lives) skips it, and the saved value keeps a rotation or process
            // restore from replaying it.
            var splashDone by rememberSaveable { mutableStateOf(splashShown) }
            LaunchedEffect(Unit) {
                if (!splashDone) delay(SPLASH_DURATION_MS)
                splashDone = true
                splashShown = true
            }
            BluesJamTheme(admin = isAdmin == true) {
                if (splashDone) AppNavHost(admin = isAdmin, assistantAvailable = assistantAvailable) else AppSplash()
            }
        }
    }

    private companion object {
        /** True once this process has played the splash; a new process (a cold start) starts false. */
        var splashShown = false
    }
}
