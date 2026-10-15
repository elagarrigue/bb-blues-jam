package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The status controls use neutral and error roles only: no amber (D-17). */
class JamStatusDefaultsTest {
    private val colors = BluesJamColors
    private val neutral = setOf(colors.surfaceRaised, colors.text, colors.textMuted, colors.error)

    @Test
    fun `the status action and the unpublish dialog use no amber`() {
        val action = JamStatusDefaults.action()
        val dialog = JamStatusDefaults.dialog()
        assertEquals(colors.text, action.action)
        assertEquals(colors.surfaceRaised, dialog.container)
        assertEquals(colors.text, dialog.title)
        assertEquals(colors.textMuted, dialog.body)
        assertEquals(colors.error, dialog.confirm)
        assertEquals(colors.text, dialog.cancel)
        val all = listOf(action.action, dialog.container, dialog.title, dialog.body, dialog.confirm, dialog.cancel)
        assertTrue(all.all { it in neutral })
    }
}
