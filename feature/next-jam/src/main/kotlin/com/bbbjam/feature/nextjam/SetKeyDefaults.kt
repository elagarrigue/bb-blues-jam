package com.bbbjam.feature.nextjam

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.model.Key
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The key picker's keys and colours (`admin-set-key`, V1), apart from the composables so a JVM test
 * can check them. Amber only through the `key` role, and only on the current key (this module's
 * allowlist is `{key}`): every selectable cell is `surfaceRaised` with `text`, so amber never marks
 * something that is not yet the song's key.
 */
internal object SetKeyDefaults {
    /** Cells per grid row: 12 keys make 3 rows of 4. */
    const val COLUMNS = 4

    /** The 12 major keys, chromatic from C, in the conventional spelling (F# on the tie). */
    val MAJORS: List<Key> = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B").map(::Key)

    /** The 12 minor keys, chromatic from C, in the conventional spelling (Ebm on the tie). */
    val MINORS: List<Key> =
        listOf("Cm", "C#m", "Dm", "Ebm", "Em", "Fm", "F#m", "Gm", "G#m", "Am", "Bbm", "Bm").map(::Key)

    data class CellStyle(val container: Color, val content: Color, val outline: Color?, val caption: Color)

    /**
     * A selectable cell: `surfaceRaised` with `text`, no outline. The current key's cell: `surface`
     * with a `border` outline, the key in the amber `key` role and the caption `actual` in
     * `textMuted`.
     */
    fun cell(isCurrent: Boolean, colors: BluesJamColors = BluesJamColors): CellStyle = if (isCurrent) {
        CellStyle(container = colors.surface, content = colors.key, outline = colors.border, caption = colors.textMuted)
    } else {
        CellStyle(container = colors.surfaceRaised, content = colors.text, outline = null, caption = colors.textMuted)
    }

    data class HeaderStyle(val songTitle: Color, val label: Color, val currentKey: Color)

    /** The song title in `text`, the `TONALIDAD ACTUAL`/section labels in `textMuted`, the key in `key`. */
    fun header(colors: BluesJamColors = BluesJamColors): HeaderStyle =
        HeaderStyle(songTitle = colors.text, label = colors.textMuted, currentKey = colors.key)
}
