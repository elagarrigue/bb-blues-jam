package com.bbbjam.feature.nextjam

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The expected strings are written out from the approved copy table in
 * `docs/specs/next-jam-read-only-list.md`, not read from [NextJamCopy], so any change to the shipped
 * copy fails here.
 */
class JamDateTextTest {
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val nine = LocalTime.of(21, 0)

    @Test
    fun `on its own date a jam starting at 18 00 or later is tonight, earlier is today`() {
        assertEquals("Esta noche", timeRemaining(jamDate, jamDate, nine))
        assertEquals("Esta noche", timeRemaining(jamDate, jamDate, LocalTime.of(18, 0)))
        assertEquals("Hoy", timeRemaining(jamDate, jamDate, LocalTime.of(17, 59)))
        assertEquals("Hoy", timeRemaining(jamDate, jamDate, LocalTime.of(11, 0)))
    }

    @Test
    fun `the day before is tomorrow and later days are counted`() {
        assertEquals("Mañana", timeRemaining(jamDate.minusDays(1), jamDate, nine))
        assertEquals("Mañana", timeRemaining(jamDate.minusDays(1), jamDate, LocalTime.of(10, 0)))
        assertEquals("En 2 días", timeRemaining(jamDate.minusDays(2), jamDate, nine))
        assertEquals("En 29 días", timeRemaining(LocalDate.of(2026, 10, 2), jamDate, nine))
        assertEquals("En 45 días", timeRemaining(LocalDate.of(2026, 9, 16), jamDate, nine))
    }

    @Test
    fun `days are calendar days across a year change`() {
        assertEquals("En 2 días", timeRemaining(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 2), nine))
        assertEquals("Mañana", timeRemaining(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1), nine))
    }

    @Test
    fun `a jam already behind today clamps to its own date`() {
        assertEquals("Esta noche", timeRemaining(jamDate.plusDays(1), jamDate, nine))
        assertEquals("Hoy", timeRemaining(jamDate.plusDays(1), jamDate, LocalTime.of(12, 0)))
    }

    @Test
    fun `the date label names the day and month in Spanish with a padded start time`() {
        assertEquals("Sábado 31 de octubre · 21:00", jamDateLabel(jamDate, nine))
        assertEquals("Lunes 1 de febrero · 09:05", jamDateLabel(LocalDate.of(2027, 2, 1), LocalTime.of(9, 5)))
    }

    @Test
    fun `every day of the week has its Spanish name`() {
        // 2026-10-19 is a Monday.
        val expected = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        expected.forEachIndexed { offset, day ->
            val date = LocalDate.of(2026, 10, 19).plusDays(offset.toLong())
            assertEquals("$day ${date.dayOfMonth} de octubre · 21:00", jamDateLabel(date, nine))
        }
    }

    @Test
    fun `every month has its lowercase Spanish name`() {
        val expected = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
        )
        expected.forEachIndexed { index, month ->
            val date = LocalDate.of(2027, index + 1, 10)
            val label = jamDateLabel(date, nine)
            assertEquals("${label.substringBefore(' ')} 10 de $month · 21:00", label)
        }
    }
}
