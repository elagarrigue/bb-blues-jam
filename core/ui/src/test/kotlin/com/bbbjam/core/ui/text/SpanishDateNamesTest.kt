package com.bbbjam.core.ui.text

import java.time.DayOfWeek
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every day and month, written out here, never read from [SpanishDateNames]. */
class SpanishDateNamesTest {
    @Test
    fun `every day of the week`() {
        val expected = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        assertEquals(expected, DayOfWeek.entries.map { SpanishDateNames.day(it) })
    }

    @Test
    fun `every month`() {
        val expected = listOf(
            "enero",
            "febrero",
            "marzo",
            "abril",
            "mayo",
            "junio",
            "julio",
            "agosto",
            "septiembre",
            "octubre",
            "noviembre",
            "diciembre",
        )
        assertEquals(expected, Month.entries.map { SpanishDateNames.month(it) })
    }
}
