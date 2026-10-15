package com.bbbjam.core.ui.status

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The status chip's visual rules (DESIGN.md `chip-status`), apart from the composable so a JVM test can
 * check them. Amber ([BluesJamColors.published]) is read only here, inside `:core:ui`, and only for the
 * selected PUBLICADA chip, so no feature needs it in its amber allowlist. Shape always differs (outline,
 * glyph), never brightness alone.
 */
internal object JamStatusChipDefaults {
    /** [outline] null means no outline; [showGlyph] draws the leading check. */
    data class Style(val fill: Color, val outline: Color?, val label: Color, val glyph: Color, val showGlyph: Boolean)

    fun style(selected: Boolean, published: Boolean, enabled: Boolean, colors: BluesJamColors = BluesJamColors): Style =
        when {
            selected && published -> Style(colors.published, null, colors.onPublished, colors.onPublished, true)
            selected -> Style(colors.surfaceRaised, colors.text, colors.text, colors.text, true)
            enabled -> Style(colors.surfaceRaised, colors.border, colors.textMuted, colors.textMuted, false)
            else -> Style(Color.Transparent, null, colors.textMuted, colors.textMuted, false)
        }
}
