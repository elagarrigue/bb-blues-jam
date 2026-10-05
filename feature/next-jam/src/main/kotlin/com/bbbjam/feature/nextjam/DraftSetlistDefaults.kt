package com.bbbjam.feature.nextjam

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The draft card's colours (`unpublished-setlist-state`, V1; DESIGN.md `badge-draft` and "Status
 * badge": draft is muted), kept apart from the composable so a JVM test can check them. Never
 * amber: this module may read only `key` (`AMBER_ROLE_ALLOWLIST`), and a draft is not a highlight.
 * The card has a `surface` fill, which the empty block lacks, so the two never look alike. The
 * screen reads the card's colours only through here.
 */
internal object DraftSetlistDefaults {
    data class DraftStyle(
        val cardFill: Color,
        val badgeFill: Color,
        val badgeText: Color,
        val title: Color,
        val message: Color,
    )

    /** `surface` card, `textMuted` on `surfaceRaised` badge, `text` title, `textMuted` message. */
    fun style(colors: BluesJamColors = BluesJamColors): DraftStyle = DraftStyle(
        cardFill = colors.surface,
        badgeFill = colors.surfaceRaised,
        badgeText = colors.textMuted,
        title = colors.text,
        message = colors.textMuted,
    )
}
