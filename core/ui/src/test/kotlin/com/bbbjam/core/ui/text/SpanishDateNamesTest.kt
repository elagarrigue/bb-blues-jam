package com.bbbjam.core.ui.text

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every day and month, written out here, resolved against the shipped `strings.xml`. */
class SpanishDateNamesTest {
    @Test
    fun `every day of the week`() {
        val expected = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        assertEquals(expected, DayOfWeek.entries.map { SpanishDateNames.day(it).text() })
        assertEquals(expected.map { it.lowercase() }, DayOfWeek.entries.map { SpanishDateNames.dayLower(it).text() })
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
        assertEquals(expected, Month.entries.map { SpanishDateNames.month(it).text() })
    }

    @Test
    fun `a date is written day, number, de, month with no year`() {
        val date = LocalDate.of(2026, 10, 31)
        assertEquals("Sábado 31 de octubre", SpanishDateNames.label(date).text())
        assertEquals("sábado 31 de octubre", SpanishDateNames.sentence(date).text())
    }
}
