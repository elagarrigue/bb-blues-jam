package com.bbbjam.feature.nextjam

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The catalog picker's colours (`admin-add-song-to-setlist`, V1), apart from the composables so a
 * JVM test can check them. Amber only through the `key` role, on the key of a song that can be
 * added (this module's allowlist is `{key}`); a song already in the list is muted throughout. The
 * search field is set explicitly from tokens, as the admin login's, because Material's defaults
 * (amber `primary` border and cursor) are not design decisions.
 */
internal object AddSongDefaults {
    data class RowStyle(val container: Color, val title: Color, val artist: Color, val key: Color, val note: Color)

    data class FieldStyle(
        val container: Color,
        val text: Color,
        val label: Color,
        val unfocusedBorder: Color,
        val focusedBorder: Color,
        val cursor: Color,
    )

    /** A catalog row: `surface`; title `text`, artist `textMuted`, key `key`; listed rows all muted. */
    fun row(isListed: Boolean, colors: BluesJamColors = BluesJamColors): RowStyle = if (isListed) {
        RowStyle(
            container = colors.surface,
            title = colors.textMuted,
            artist = colors.textMuted,
            key = colors.textMuted,
            note = colors.textMuted,
        )
    } else {
        RowStyle(
            container = colors.surface,
            title = colors.text,
            artist = colors.textMuted,
            key = colors.key,
            note = colors.textMuted,
        )
    }

    /** The search field: no amber anywhere. */
    fun field(colors: BluesJamColors = BluesJamColors): FieldStyle = FieldStyle(
        container = colors.surface,
        text = colors.text,
        label = colors.textMuted,
        unfocusedBorder = colors.border,
        focusedBorder = colors.text,
        cursor = colors.text,
    )
}
