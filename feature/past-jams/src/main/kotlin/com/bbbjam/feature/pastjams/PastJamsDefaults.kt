package com.bbbjam.feature.pastjams

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The archive treatment of a past jam row, kept apart from the composable so a JVM test can check
 * it (DESIGN.md "Past jams list"). Muted, and never amber: the list shows no keys. The module's
 * only amber role, `key`, is read by the detail (`PastJamDetailDefaults`). The screen reads row
 * colours only through here.
 */
internal object PastJamsDefaults {
    /** [message] is the line a past jam shows instead of count and hook (P1). */
    data class RowStyle(
        val fill: Color,
        val date: Color,
        val venue: Color,
        val count: Color,
        val hook: Color,
        val message: Color,
    )

    /** `surface` fill, `textMuted` date, venue, count and message, `archive` hook (5.39:1 on `surface`). */
    fun rowStyle(colors: BluesJamColors = BluesJamColors): RowStyle = RowStyle(
        fill = colors.surface,
        date = colors.textMuted,
        venue = colors.textMuted,
        count = colors.textMuted,
        hook = colors.archive,
        message = colors.textMuted,
    )
}
