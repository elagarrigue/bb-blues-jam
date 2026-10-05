package com.bbbjam.feature.pastjams

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The archive treatment of a past jam's detail, kept apart from the composable so a JVM test can
 * check it (DESIGN.md "Past jam detail"). Muted, with one amber: the key, in its `key` role, the
 * only amber role this module may read (`AMBER_ROLE_ALLOWLIST`). The screen reads its colours only
 * through here.
 */
internal object PastJamDetailDefaults {
    data class HeaderStyle(val date: Color, val venue: Color, val count: Color, val message: Color)

    data class RowStyle(
        val fill: Color,
        val position: Color,
        val title: Color,
        val artist: Color,
        val key: Color,
        val droppedNote: Color,
    )

    /** `textMuted` date, venue, count and the line that replaces the songs. */
    fun headerStyle(colors: BluesJamColors = BluesJamColors): HeaderStyle = HeaderStyle(
        date = colors.textMuted,
        venue = colors.textMuted,
        count = colors.textMuted,
        message = colors.textMuted,
    )

    /** `surface` fill, `textMuted` position, title and note, `archive` artist (5.39:1 on `surface`), `key` key. */
    fun rowStyle(colors: BluesJamColors = BluesJamColors): RowStyle = RowStyle(
        fill = colors.surface,
        position = colors.textMuted,
        title = colors.textMuted,
        artist = colors.archive,
        key = colors.key,
        droppedNote = colors.textMuted,
    )
}
