package com.bbbjam.core.ui.filter

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Amber only on the chosen-name chip; the picker dialog is never amber. */
class MusicianFilterDefaultsTest {
    private val colors = BluesJamColors

    @Test
    fun `a chosen name is onActiveFilter on activeFilter, none is muted on the raised surface`() {
        assertEquals(
            MusicianFilterDefaults.ChipStyle(colors.activeFilter, colors.onActiveFilter),
            MusicianFilterDefaults.chipStyle(selected = true),
        )
        assertEquals(
            MusicianFilterDefaults.ChipStyle(colors.surfaceRaised, colors.textMuted),
            MusicianFilterDefaults.chipStyle(selected = false),
        )
    }

    @Test
    fun `the picker dialog never uses amber`() {
        val dialog = MusicianFilterDefaults.dialogStyle()
        listOf(dialog.container, dialog.title, dialog.option, dialog.radio, dialog.action).forEach {
            assertNotEquals(colors.activeFilter, it)
            assertNotEquals(colors.primaryAction, it)
            assertNotEquals(colors.slotOpen, it)
        }
    }
}
