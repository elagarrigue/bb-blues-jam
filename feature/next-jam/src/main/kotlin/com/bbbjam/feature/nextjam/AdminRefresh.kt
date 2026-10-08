package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.jams.JamsRepository

@Composable
internal fun rememberAdminRefresh(adminSession: AdminSession, jams: JamsRepository) {
    // Read only by the effect, never by the composition, so writing it recomposes nothing.
    var adminRefreshed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // The flag's real values only (not the composition's initial false), so coming back to
        // the tab with admin on does not count as a new login.
        adminSession.observeIsAdmin().collect { flag ->
            if (!flag) {
                adminRefreshed = false
            } else if (!adminRefreshed) {
                adminRefreshed = true
                jams.refresh()
            }
        }
    }
}
