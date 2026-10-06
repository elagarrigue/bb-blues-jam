package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The key picker's keys and colours (`admin-set-key`, V1). Amber only on the current key, through
 * `key`, the one amber role this module may read.
 */
class SetKeyDefaultsTest {
    private val colors = BluesJamColors

    @Test
    fun `24 distinct keys, 12 major and 12 minor, chromatic from C in the approved spelling`() {
        assertEquals(
            listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"),
            SetKeyDefaults.MAJORS.map { it.value },
        )
        assertEquals(
            listOf("Cm", "C#m", "Dm", "Ebm", "Em", "Fm", "F#m", "Gm", "G#m", "Am", "Bbm", "Bm"),
            SetKeyDefaults.MINORS.map { it.value },
        )
        assertTrue(SetKeyDefaults.MAJORS.none { it.isMinor })
        assertTrue(SetKeyDefaults.MINORS.all { it.isMinor })
        assertEquals(24, (SetKeyDefaults.MAJORS + SetKeyDefaults.MINORS).toSet().size)
        assertEquals(4, SetKeyDefaults.COLUMNS)
    }

    @Test
    fun `a selectable cell is never amber, the current one is outlined with the key in key`() {
        val selectable = SetKeyDefaults.cell(isCurrent = false)
        assertEquals(colors.surfaceRaised, selectable.container)
        assertEquals(colors.text, selectable.content)
        assertNull(selectable.outline)

        val current = SetKeyDefaults.cell(isCurrent = true)
        assertEquals(colors.surface, current.container)
        assertEquals(colors.key, current.content)
        assertEquals(colors.border, current.outline)
        assertEquals(colors.textMuted, current.caption)

        val header = SetKeyDefaults.header()
        assertEquals(SetKeyDefaults.HeaderStyle(colors.text, colors.textMuted, colors.key), header)
    }
}
