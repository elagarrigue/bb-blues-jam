package com.bbbjam.core.ui.jamform

import com.bbbjam.core.ui.R
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.text
import com.bbbjam.core.ui.theme.BluesJamColors
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The jam form's colours (moved from the Jams tab): neutral and error roles only, never amber (D-17). */
class JamFormDefaultsTest {
    private val colors = BluesJamColors
    private val neutral = setOf(colors.surface, colors.surfaceRaised, colors.text, colors.textMuted, colors.error)

    @Test
    fun `the form uses neutral and error roles only, never amber`() {
        val form = JamFormDefaults.form()
        val field = form.field
        assertEquals(colors.surfaceRaised, form.container)
        assertEquals(colors.error, form.error)
        assertEquals(colors.textMuted, form.disabled)
        val all = listOf(
            form.container, form.title, form.confirm, form.disabled, form.cancel, form.note, form.error,
            field.container, field.text, field.label, field.unfocusedBorder, field.focusedBorder, field.cursor,
        )
        assertTrue(all.all { it in neutral || it == colors.border })
    }

    @Test
    fun `the copy is written with vos and names the upcoming jam in a sentence`() {
        assertEquals("sábado 31 de octubre", SpanishDateNames.sentence(LocalDate.of(2026, 10, 31)).text())
        val earlier = jamFormEarlierError(LocalDate.of(2026, 10, 31)).text()
        assertTrue(earlier.contains("sábado 31 de octubre"))
        assertFalse(earlier.contains("planilla"))
        assertEquals("Escribí la fecha como DD/MM/AAAA.", UiText.res(R.string.jam_form_date_format_error).text())
    }
}
