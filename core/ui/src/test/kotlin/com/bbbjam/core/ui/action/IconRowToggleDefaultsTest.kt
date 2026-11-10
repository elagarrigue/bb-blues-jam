package com.bbbjam.core.ui.action

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IconRowToggleDefaultsTest {
    @Test
    fun `played is filled when on and outlined when off`() {
        assertEquals(Icons.Filled.CheckCircle, IconRowToggleDefaults.glyph(IconRowActionIcon.Played, checked = true))
        assertEquals(Icons.Outlined.CheckCircle, IconRowToggleDefaults.glyph(IconRowActionIcon.Played, checked = false))
        assertNotEquals(
            IconRowToggleDefaults.glyph(IconRowActionIcon.Played, checked = true),
            IconRowToggleDefaults.glyph(IconRowActionIcon.Played, checked = false),
        )
    }

    @Test
    fun `other icons draw the same glyph on and off`() {
        (IconRowActionIcon.entries - IconRowActionIcon.Played).forEach {
            assertEquals(
                IconRowToggleDefaults.glyph(it, checked = true),
                IconRowToggleDefaults.glyph(it, checked = false),
            )
        }
    }
}
