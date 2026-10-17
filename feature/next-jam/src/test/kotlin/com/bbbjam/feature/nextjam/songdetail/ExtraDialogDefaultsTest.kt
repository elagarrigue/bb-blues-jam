package com.bbbjam.feature.nextjam.songdetail

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The "Agregar a Otros" dialog uses neutral tokens only: not destructive, never amber. */
class ExtraDialogDefaultsTest {
    private val colors = BluesJamColors

    @Test
    fun `the dialog roles are the specified ones`() {
        assertEquals(
            ExtraDialogDefaults.Style(
                container = colors.surfaceRaised,
                title = colors.text,
                confirm = colors.text,
                disabled = colors.textMuted,
                cancel = colors.text,
            ),
            ExtraDialogDefaults.style(),
        )
    }

    @Test
    fun `the dialog never uses amber nor the destructive role`() {
        val style = ExtraDialogDefaults.style()
        listOf(style.container, style.title, style.confirm, style.disabled, style.cancel).forEach {
            assertNotEquals(colors.key, it)
            assertNotEquals(colors.error, it)
            assertNotEquals(colors.primaryAction, it)
        }
    }
}
