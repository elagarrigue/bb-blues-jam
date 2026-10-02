package com.bbbjam.core.ui.strip

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.strip.InstrumentStripDefaults.Glyph
import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The style of each chip kind. Open and filled must differ in glyph (not only in colour), and an
 * extra must never borrow a slot's look: no amber, no `slotFilled`, no dot, no check.
 */
class InstrumentStripDefaultsTest {

    private val colors = BluesJamColors

    @Test
    fun `an open slot is amber text on a 15 percent amber fill with a dot`() {
        val style = InstrumentStripDefaults.style(InstrumentChipKind.OPEN_SLOT)
        assertEquals(colors.slotOpen.copy(alpha = 0.15f), style.fill)
        assertEquals(colors.slotOpen, style.textColor)
        assertEquals(colors.slotOpen, style.glyphColor)
        assertEquals(Glyph.DOT, style.glyph)
    }

    @Test
    fun `a filled slot is muted text on slotFilled with a check`() {
        val style = InstrumentStripDefaults.style(InstrumentChipKind.FILLED_SLOT)
        assertEquals(colors.slotFilled, style.fill)
        assertEquals(colors.textMuted, style.textColor)
        assertEquals(colors.textMuted, style.glyphColor)
        assertEquals(Glyph.CHECK, style.glyph)
    }

    @Test
    fun `an extra has no fill, muted text and no slot glyph`() {
        val style = InstrumentStripDefaults.style(InstrumentChipKind.EXTRA)
        assertEquals(Color.Transparent, style.fill)
        assertEquals(colors.textMuted, style.textColor)
        assertEquals(Glyph.NONE, style.glyph)
    }

    @Test
    fun `an extra never uses amber or the slot fills`() {
        val style = InstrumentStripDefaults.style(InstrumentChipKind.EXTRA)
        val amber = colors.slotOpen
        listOf(style.fill, style.textColor, style.glyphColor).forEach { color ->
            assertNotEquals(amber, color)
            assertNotEquals(amber.copy(alpha = InstrumentStripDefaults.OPEN_FILL_ALPHA), color)
            assertNotEquals(colors.slotFilled, color)
        }
    }

    @Test
    fun `open and filled differ in glyph, not only in colour`() {
        assertNotEquals(
            InstrumentStripDefaults.style(InstrumentChipKind.OPEN_SLOT).glyph,
            InstrumentStripDefaults.style(InstrumentChipKind.FILLED_SLOT).glyph,
        )
    }
}
