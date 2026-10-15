package com.bbbjam.core.ui.status

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bbbjam.core.ui.state.LoadingDotsDefaults
import com.bbbjam.core.ui.state.loadingDotAlpha
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The status chip's visual rules (DESIGN.md `chip-status`), apart from the composable so a JVM test can
 * check them. Amber ([BluesJamColors.published]) is read only here, inside `:core:ui`, and only for the
 * selected PUBLICADA chip, so no feature needs it in its amber allowlist. Shape always differs (outline,
 * glyph), never brightness alone. The pending chip (`jam-status-chip-loading`) is muted, never amber (D-17).
 */
internal object JamStatusChipDefaults {
    /** The pending chip's outline: a hairline would be invisible while it pulses. */
    val PENDING_OUTLINE_WIDTH: Dp = 2.dp

    /** [outline] null means no outline; [showGlyph] draws the leading check. */
    data class Style(
        val fill: Color,
        val outline: Color?,
        val label: Color,
        val glyph: Color,
        val showGlyph: Boolean,
        val outlineWidth: Dp = Dp.Hairline,
    )

    fun style(
        selected: Boolean,
        published: Boolean,
        enabled: Boolean,
        pending: Boolean = false,
        colors: BluesJamColors = BluesJamColors,
    ): Style = when {
        pending -> Style(
            colors.surfaceRaised,
            colors.textMuted,
            colors.textMuted,
            colors.textMuted,
            false,
            PENDING_OUTLINE_WIDTH,
        )

        selected && published -> Style(colors.published, null, colors.onPublished, colors.onPublished, true)

        selected -> Style(colors.surfaceRaised, colors.text, colors.text, colors.text, true)

        enabled -> Style(colors.surfaceRaised, colors.border, colors.textMuted, colors.textMuted, false)

        else -> Style(Color.Transparent, null, colors.textMuted, colors.textMuted, false)
    }
}

/**
 * The pending outline's alpha [elapsedMillis] after the animation started: the loading dots' fade (index 0),
 * or a constant [LoadingDotsDefaults.STATIC_ALPHA] under [reduced] motion.
 */
internal fun pendingOutlineAlpha(elapsedMillis: Long, reduced: Boolean): Float =
    if (reduced) LoadingDotsDefaults.STATIC_ALPHA else loadingDotAlpha(elapsedMillis, index = 0)
