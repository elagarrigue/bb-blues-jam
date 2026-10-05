package com.bbbjam.feature.info

import com.bbbjam.core.ui.theme.BluesJamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The login's colours: amber only on the enabled Entrar button (`button-primary`), none in the field. */
class AdminLoginDefaultsTest {
    private val amber = setOf(BluesJamColors.primaryAction, BluesJamColors.onPrimaryAction)

    @Test
    fun `the enabled button is the primary action, the disabled one is muted`() {
        assertEquals(
            AdminLoginDefaults.ButtonStyle(BluesJamColors.primaryAction, BluesJamColors.onPrimaryAction),
            AdminLoginDefaults.submitButton(enabled = true),
        )
        assertEquals(
            AdminLoginDefaults.ButtonStyle(BluesJamColors.surfaceRaised, BluesJamColors.textMuted),
            AdminLoginDefaults.submitButton(enabled = false),
        )
    }

    @Test
    fun `the field and the toggle use no amber`() {
        val field = AdminLoginDefaults.field()
        val used = listOf(
            field.container,
            field.text,
            field.label,
            field.unfocusedBorder,
            field.focusedBorder,
            field.cursor,
            field.error,
            AdminLoginDefaults.toggle(),
        )
        used.forEach { assertFalse(it.toString(), it in amber) }
        assertEquals(BluesJamColors.surface, field.container)
        assertEquals(BluesJamColors.error, field.error)
    }
}
