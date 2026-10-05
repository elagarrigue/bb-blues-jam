package com.bbbjam.core.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The bottom bar's visual rules (DESIGN.md "Bottom bar"), kept apart from the composable so a JVM
 * test can check them. No amber: the selected tab is a location, not an action, so it reads as
 * `text` on a `surfaceRaised` indicator, the others as `textMuted`, on a `surface` bar.
 */
internal object TabBarDefaults {
    data class BarColors(
        val container: Color,
        val selectedContent: Color,
        val indicator: Color,
        val unselectedContent: Color,
    )

    fun colors(colors: BluesJamColors = BluesJamColors): BarColors = BarColors(
        container = colors.surface,
        selectedContent = colors.text,
        indicator = colors.surfaceRaised,
        unselectedContent = colors.textMuted,
    )

    /** I1: icons from `material-icons-core`, no new dependency. The list mirrors in RTL. */
    fun icon(icon: TabIcon): ImageVector = when (icon) {
        TabIcon.SETLIST -> Icons.AutoMirrored.Filled.List
        TabIcon.ARCHIVE -> Icons.Filled.DateRange
        TabIcon.INFO -> Icons.Outlined.Info
    }
}
