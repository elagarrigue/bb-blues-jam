package com.bbbjam.feature.nextjam.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.bbbjam.core.ui.status.JamStatusBadge
import com.bbbjam.core.ui.text.resolve
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.nextjam.AdminStatusUiModel

/**
 * The admin's read-only status directly below the jam header (`admin-jams-tab`): the shared badge, the
 * note of what musicians see and the hint that the status is changed in the Jams tab.
 */
@Composable
internal fun AdminStatusBlock(model: AdminStatusUiModel) {
    val style = AdminControlsDefaults.status()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs),
    ) {
        JamStatusBadge(model.badge)
        Text(text = model.note.resolve(), style = BluesJamTheme.typography.caption, color = style.note)
        Text(text = model.hint.resolve(), style = BluesJamTheme.typography.caption, color = style.note)
    }
}
