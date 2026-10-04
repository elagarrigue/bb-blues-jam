package com.bbbjam.core.ui.filter

import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.strip.InstrumentStripDefaults
import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The filter chip's look (DESIGN.md `chip-filter`): amber only for a selected chip, never the open slot's look. */
class InstrumentFilterDefaultsTest {

    private val colors = BluesJamColors

    @Test
    fun `a selected chip is onActiveFilter on activeFilter`() {
        assertEquals(
            InstrumentFilterDefaults.ChipStyle(fill = colors.activeFilter, content = colors.onActiveFilter),
            InstrumentFilterDefaults.chipStyle(selected = true),
        )
    }

    @Test
    fun `an unselected chip is muted text on the raised surface`() {
        assertEquals(
            InstrumentFilterDefaults.ChipStyle(fill = colors.surfaceRaised, content = colors.textMuted),
            InstrumentFilterDefaults.chipStyle(selected = false),
        )
    }

    @Test
    fun `no filter chip looks like an open slot chip`() {
        val open = InstrumentStripDefaults.style(InstrumentChipKind.OPEN_SLOT)
        listOf(true, false).forEach { selected ->
            val style = InstrumentFilterDefaults.chipStyle(selected)
            assertNotEquals(open.fill, style.fill)
            assertNotEquals(open.textColor, style.content)
        }
    }
}
