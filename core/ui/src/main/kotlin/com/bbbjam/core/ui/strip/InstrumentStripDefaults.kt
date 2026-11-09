package com.bbbjam.core.ui.strip

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The strip's visual rules, kept apart from the composable so a JVM test can check them. Amber
 * ([BluesJamColors.slotOpen]) is read only here, inside `:core:ui`, so no feature needs it in its
 * amber allowlist.
 */
internal object InstrumentStripDefaults {
    /** The open chip's fill is `slotOpen` at this alpha (DESIGN.md: "a 15% amber fill"). */
    const val OPEN_FILL_ALPHA = 0.15f

    /** The open dot's diameter relative to the label's font size; the check is one em. */
    const val DOT_TO_EM = 0.5f

    /** The glyph before a chip's label. [NONE]: the label itself starts with the glyph ("+ saxo: Juan"). */
    enum class Glyph { DOT, CHECK, NONE }

    data class ChipStyle(val fill: Color, val textColor: Color, val glyphColor: Color, val glyph: Glyph)

    /**
     * Open and filled differ in glyph and wording, never in brightness alone (the two fills are
     * almost the same grey). An extra shares the filled slot's fill and text, has no slot glyph and
     * is never amber; its leading "+" and the missing check keep it from reading as a slot.
     */
    fun style(kind: InstrumentChipKind, colors: BluesJamColors = BluesJamColors): ChipStyle = when (kind) {
        InstrumentChipKind.OPEN_SLOT -> ChipStyle(
            fill = colors.slotOpen.copy(alpha = OPEN_FILL_ALPHA),
            textColor = colors.slotOpen,
            glyphColor = colors.slotOpen,
            glyph = Glyph.DOT,
        )

        InstrumentChipKind.FILLED_SLOT -> ChipStyle(
            fill = colors.slotFilled,
            textColor = colors.textMuted,
            glyphColor = colors.textMuted,
            glyph = Glyph.CHECK,
        )

        InstrumentChipKind.EXTRA -> ChipStyle(
            fill = colors.slotFilled,
            textColor = colors.textMuted,
            glyphColor = colors.textMuted,
            glyph = Glyph.NONE,
        )
    }

    /**
     * The strip of a played song (`song-played-state`, D-31): every kind on the `surfaceRaised` fill with
     * `archive` text (5.16:1, and 4.80:1 on the admin's raised surface; `slotFilled` would give 3.85:1), and
     * no amber, because a played song's open slot is not a place to play any more
     * (DESIGN.md "amber only for what you can act on"). The glyph is the kind's own, so an open slot is
     * still told from a filled one by its dot and its wording, never by colour alone.
     */
    fun mutedStyle(kind: InstrumentChipKind, colors: BluesJamColors = BluesJamColors): ChipStyle =
        style(kind, colors).copy(fill = colors.surfaceRaised, textColor = colors.archive, glyphColor = colors.archive)
}
