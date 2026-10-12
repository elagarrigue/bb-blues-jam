package com.bbbjam.core.ui.lineup

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.strip.InstrumentStripDefaults
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The panel's visual rules, kept apart from the composable so a JVM test can check them. A line
 * takes its fill, glyph and instrument colour from the strip's style for the same kind, so the two
 * components never disagree on what open looks like. Amber stays inside `:core:ui` (D-17).
 */
internal object LineupPanelDefaults {
    /**
     * The widest a line's instrument text may grow before it is cut with "…" (`song-detail-screen`,
     * from the `song-row-expansion` validation): an extra's instrument is free text.
     */
    val INSTRUMENT_MAX_WIDTH: Dp = 160.dp

    /**
     * Fill, glyph and instrument text colour of a line: the strip chip's style, except an extra,
     * which reads exactly like a filled slot here (`extras-dialog-and-lines`). The strip's own
     * extra chip is unchanged.
     */
    fun style(kind: InstrumentChipKind, colors: BluesJamColors = BluesJamColors): InstrumentStripDefaults.ChipStyle =
        when (kind) {
            InstrumentChipKind.EXTRA -> InstrumentStripDefaults.style(InstrumentChipKind.FILLED_SLOT, colors)
            else -> InstrumentStripDefaults.style(kind, colors)
        }

    /**
     * The detail's colour: "LIBRE" in `slotOpen`, a musician's or an extra's name in full
     * `text` (the information on that line), never amber.
     */
    fun detailColor(kind: InstrumentChipKind, colors: BluesJamColors = BluesJamColors): Color = when (kind) {
        InstrumentChipKind.OPEN_SLOT -> colors.slotOpen
        InstrumentChipKind.FILLED_SLOT, InstrumentChipKind.EXTRA -> colors.text
    }
}
