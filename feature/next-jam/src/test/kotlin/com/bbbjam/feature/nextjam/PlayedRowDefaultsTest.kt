package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A played row reads the amber `key` role nowhere (D-17: amber only for what is live). */
class PlayedRowDefaultsTest {
    private val colors = BluesJamColors

    @Test
    fun `a played row is archive for position, title, key and caption`() {
        assertEquals(colors.archive, PlayedRowDefaults.position(played = true))
        assertEquals(colors.archive, PlayedRowDefaults.title(played = true))
        assertEquals(colors.archive, PlayedRowDefaults.key(played = true))
        assertEquals(colors.archive, PlayedRowDefaults.caption())
    }

    @Test
    fun `an unplayed row keeps its own colours`() {
        assertEquals(colors.textMuted, PlayedRowDefaults.position(played = false))
        assertEquals(colors.text, PlayedRowDefaults.title(played = false))
        assertEquals(colors.key, PlayedRowDefaults.key(played = false))
    }

    @Test
    fun `the switch is never amber`() {
        listOf(true, false).forEach { checked ->
            val tint = PlayedRowDefaults.toggle(checked)
            assertNotEquals(colors.key, tint)
        }
        assertEquals(colors.text, PlayedRowDefaults.toggle(checked = true))
        assertEquals(colors.textMuted, PlayedRowDefaults.toggle(checked = false))
    }
}
