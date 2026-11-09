package com.bbbjam.feature.nextjam

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The colours of a song row and of the song detail for a played song (`song-played-state`, D-31), apart from
 * the composables so a JVM test can check them. A played song's position, title and key leave `textMuted`,
 * `text` and the amber `key` for `archive` (the colour of a past jam, 5.84:1 on the background), so the
 * amber role is read only for what is still live. Colour is never the only signal: the "Ya tocado" caption
 * and the header's semantics carry it too.
 */
internal object PlayedRowDefaults {
    fun position(played: Boolean, colors: BluesJamColors = BluesJamColors): Color =
        if (played) colors.archive else colors.textMuted

    fun title(played: Boolean, colors: BluesJamColors = BluesJamColors): Color =
        if (played) colors.archive else colors.text

    fun key(played: Boolean, colors: BluesJamColors = BluesJamColors): Color =
        if (played) colors.archive else colors.key

    /** The "Ya tocado" caption under a title: always `archive`. */
    fun caption(colors: BluesJamColors = BluesJamColors): Color = colors.archive

    /** The switch's tint: `text` when on, `textMuted` when off, never amber. */
    fun toggle(checked: Boolean, colors: BluesJamColors = BluesJamColors): Color =
        if (checked) colors.text else colors.textMuted
}
