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
     * almost the same grey). An extra has no fill, muted text and no slot glyph, so it can never
     * read as a slot: never amber, never `slotFilled`.
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
            fill = Color.Transparent,
            textColor = colors.textMuted,
            glyphColor = colors.textMuted,
            glyph = Glyph.NONE,
        )
    }
}
