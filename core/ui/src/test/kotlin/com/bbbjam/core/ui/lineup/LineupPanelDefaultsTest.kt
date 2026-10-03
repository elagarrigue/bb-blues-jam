package com.bbbjam.core.ui.lineup

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.strip.InstrumentStripDefaults.Glyph
import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The panel line styles: open reads as available, filled as taken, an extra never as a slot. */
class LineupPanelDefaultsTest {

    private val colors = BluesJamColors

    @Test
    fun `an open line is slotOpen text on the 15 percent amber fill with a dot`() {
        val style = LineupPanelDefaults.style(InstrumentChipKind.OPEN_SLOT)
        assertEquals(colors.slotOpen.copy(alpha = 0.15f), style.fill)
        assertEquals(colors.slotOpen, style.textColor)
        assertEquals(Glyph.DOT, style.glyph)
        assertEquals(colors.slotOpen, LineupPanelDefaults.detailColor(InstrumentChipKind.OPEN_SLOT))
    }

    @Test
    fun `a filled line shows the name in full text on slotFilled with a check`() {
        val style = LineupPanelDefaults.style(InstrumentChipKind.FILLED_SLOT)
        assertEquals(colors.slotFilled, style.fill)
        assertEquals(colors.textMuted, style.textColor)
        assertEquals(Glyph.CHECK, style.glyph)
        assertEquals(colors.text, LineupPanelDefaults.detailColor(InstrumentChipKind.FILLED_SLOT))
    }

    @Test
    fun `an extra line is never amber nor slotFilled and has no slot glyph`() {
        val style = LineupPanelDefaults.style(InstrumentChipKind.EXTRA)
        assertEquals(Color.Transparent, style.fill)
        assertNotEquals(colors.slotFilled, style.fill)
        assertNotEquals(colors.slotOpen, style.textColor)
        assertEquals(Glyph.NONE, style.glyph)
        val detail = LineupPanelDefaults.detailColor(InstrumentChipKind.EXTRA)
        assertNotEquals(colors.slotOpen, detail)
        assertEquals(colors.textMuted, detail)
    }
}
