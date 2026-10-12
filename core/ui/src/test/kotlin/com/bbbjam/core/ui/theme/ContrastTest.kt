package com.bbbjam.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.bbbjam.core.ui.filter.InstrumentFilterDefaults
import com.bbbjam.core.ui.nav.TabBarDefaults
import com.bbbjam.core.ui.state.ListStateDefaults
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
    fun `content on the published badge`() = assertContrast(colors.onPublished, colors.published, expected = 9.52)

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

    /** The instrument strip's filled chip; an extra chip shares this pair, read through its style. */
    @Test
    fun `muted text on a filled chip`() {
        assertContrast(colors.textMuted, colors.slotFilled, expected = 7.22)
        val extra = InstrumentStripDefaults.style(InstrumentChipKind.EXTRA)
        assertContrast(extra.textColor, extra.fill, expected = 7.22)
    }

    /** The selected filter chip, read through the style the component draws. */
    @Test
    fun `content on the active filter`() {
        val style = InstrumentFilterDefaults.chipStyle(selected = true)
        assertContrast(style.content, style.fill, expected = 9.52)
    }

    /** An unselected filter chip. */
    @Test
    fun `muted text on a raised surface`() {
        val style = InstrumentFilterDefaults.chipStyle(selected = false)
        assertContrast(style.content, style.fill, expected = 9.66)
    }

    /** The error block's retry button, read through the style the component draws (`list-states`). */
    @Test
    fun `content on the retry button`() {
        val style = ListStateDefaults.retryButtonStyle()
        assertContrast(style.content, style.fill, expected = 9.52)
    }

    /** The staleness notice's detail ("Mostrando lo guardado hace 3 horas.") on the notice's fill. */
    @Test
    fun `muted text on the notice`() = assertContrast(colors.textMuted, ListStateDefaults.noticeFill(), expected = 9.66)

    /** A past jam's hook (the first titles, `body` size) on its row's fill (`past-jams-list`). */
    @Test
    fun `archive on a surface`() = assertContrast(colors.archive, colors.surface, expected = 5.39)

    /** The selected bottom-bar tab: icon and label in `text` on the `surfaceRaised` indicator (`bottom-navigation`). */
    @Test
    fun `text on a raised surface`() {
        val bar = TabBarDefaults.colors()
        assertContrast(bar.selectedContent, bar.indicator, expected = 12.75)
    }

    /**
     * The admin's destructive text on a row (`admin-remove-song-from-setlist`): "Quitar de la lista"
     * and "Quitar" in `error` on the row's `surface`, as the failure cards' titles.
     */
    @Test
    fun `error on a surface`() = assertContrast(colors.error, colors.surface, expected = 10.12)

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

/**
 * The same pairings re-measured on [BluesJamAdminColors] (`admin-mode-highlight`, Scenario 6): the
 * admin background/surface/surfaceRaised are a visibly different, clearly tinted deep-wine ramp
 * (orchestrator amendment A1), not a re-use of the musician values, so every ratio here is its own
 * recorded number, not copied from [ContrastTest]. Every pairing still reaches AA text (4.5:1).
 */
class AdminContrastTest {

    private val admin = BluesJamAdminColors
    private val colors = BluesJamColors

    @Test
    fun `key on the admin background`() = assertContrast(colors.key, admin.background, expected = 9.77)

    @Test
    fun `open slot on the admin surface`() = assertContrast(colors.slotOpen, admin.surface, expected = 8.93)

    @Test
    fun `muted text on the admin surface`() = assertContrast(colors.textMuted, admin.surface, expected = 9.42)

    @Test
    fun `muted text on the admin background`() = assertContrast(colors.textMuted, admin.background, expected = 10.31)

    @Test
    fun `text on the admin background`() = assertContrast(colors.text, admin.background, expected = 13.59)

    @Test
    fun `archive on the admin surface`() = assertContrast(colors.archive, admin.surface, expected = 5.03)

    @Test
    fun `error on the admin surface`() = assertContrast(colors.error, admin.surface, expected = 9.44)

    /** Backs the "MODO ADMIN" banner's own content colour (T3). */
    @Test
    fun `text on the admin raised surface`() = assertContrast(colors.text, admin.surfaceRaised, expected = 11.85)

    @Test
    fun `muted text on the admin raised surface`() =
        assertContrast(colors.textMuted, admin.surfaceRaised, expected = 8.98)

    /** The open-slot chip fill (15% amber), composited over the admin surface instead of the musician one. */
    @Test
    fun `open slot on its chip fill over the admin surface`() = assertContrast(
        colors.slotOpen,
        InstrumentStripDefaults.style(InstrumentChipKind.OPEN_SLOT).fill.compositeOver(admin.surface),
        expected = 6.48,
    )

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
