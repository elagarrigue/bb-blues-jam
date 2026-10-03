package com.bbbjam.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.strip.InstrumentStripDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.x contrast of the pairs used for text, measured from the tokens. Each must reach 4.5:1 (AA
 * text) and match the value recorded in `docs/specs/design-tokens-theme.md`, so a token edit that
 * changes contrast is noticed even when it still passes AA.
 */
class ContrastTest {

    private val colors = BluesJamColors

    @Test
    fun `key on the background`() = assertContrast(colors.key, colors.background, expected = 10.35)

    @Test
    fun `open slot on a surface`() = assertContrast(colors.slotOpen, colors.surface, expected = 9.57)

    @Test
    fun `muted text on a surface`() = assertContrast(colors.textMuted, colors.surface, expected = 10.10)

    @Test
    fun `muted text on the background`() = assertContrast(colors.textMuted, colors.background, expected = 10.93)

    @Test
    fun `content on the primary action`() =
        assertContrast(colors.onPrimaryAction, colors.primaryAction, expected = 9.52)

    @Test
    fun `text on the background`() = assertContrast(colors.text, colors.background, expected = 14.41)

    /**
     * The open chip and the open panel line: amber on the fill the component actually draws
     * (`InstrumentStripDefaults.style`), composited over the row surface.
     */
    @Test
    fun `open slot on its chip fill`() = assertContrast(
        colors.slotOpen,
        InstrumentStripDefaults.style(InstrumentChipKind.OPEN_SLOT).fill.compositeOver(colors.surface),
        expected = 7.03,
    )

    /** A filled panel line: the musician's name in full `text` on `slotFilled`. */
    @Test
    fun `text on a filled slot`() = assertContrast(colors.text, colors.slotFilled, expected = 9.52)

    /** The instrument strip's filled chip. An extra chip has no fill: `muted text on a surface` covers it. */
    @Test
    fun `muted text on a filled chip`() = assertContrast(colors.textMuted, colors.slotFilled, expected = 7.22)

    private fun assertContrast(foreground: Color, background: Color, expected: Double) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + WCAG_FLARE) / (darker + WCAG_FLARE).toDouble()
        assertTrue("contrast $ratio is below AA text ($AA_TEXT)", ratio >= AA_TEXT)
        assertEquals("contrast differs from the recorded value", expected, ratio, TOLERANCE)
    }

    private companion object {
        const val WCAG_FLARE = 0.05f
        const val AA_TEXT = 4.5
        const val TOLERANCE = 0.01
    }
}
