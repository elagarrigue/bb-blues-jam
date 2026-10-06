package com.bbbjam.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** `admin-add-song-to-setlist` (V1): text selection is never amber, in any text field of the app. */
class TextSelectionColorsTest {
    @Test
    fun `the selection handle is text and the selected background is text at 40 percent`() {
        val colors = BluesJamMaterial.textSelectionColors

        assertEquals(BluesJamPalette.Text, colors.handleColor)
        assertEquals(BluesJamPalette.Text.copy(alpha = 0.4f), colors.backgroundColor)
        assertNotEquals(BluesJamPalette.Amber, colors.handleColor)
        assertNotEquals(BluesJamPalette.Amber.copy(alpha = colors.backgroundColor.alpha), colors.backgroundColor)
    }
}
