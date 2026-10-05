package com.bbbjam.core.ui.text

import java.time.DayOfWeek
import java.time.Month

/**
 * Spanish day and month names shared by every screen that writes a date (Próxima jam, Anteriores).
 * Written out here instead of coming from `DateTimeFormatter` and a `Locale`: desugared java.time on
 * API 24–25 and the JVM may render locale text differently, and the copy must be identical on the
 * device and in tests. Days are capitalized, because a date label starts with one ("Sábado 25 de
 * julio"); months are lower case, as Spanish writes them mid-sentence.
 */
object SpanishDateNames {
    fun day(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Lunes"
        DayOfWeek.TUESDAY -> "Martes"
        DayOfWeek.WEDNESDAY -> "Miércoles"
        DayOfWeek.THURSDAY -> "Jueves"
        DayOfWeek.FRIDAY -> "Viernes"
        DayOfWeek.SATURDAY -> "Sábado"
        DayOfWeek.SUNDAY -> "Domingo"
    }

    fun month(month: Month): String = when (month) {
        Month.JANUARY -> "enero"
        Month.FEBRUARY -> "febrero"
        Month.MARCH -> "marzo"
        Month.APRIL -> "abril"
        Month.MAY -> "mayo"
        Month.JUNE -> "junio"
        Month.JULY -> "julio"
        Month.AUGUST -> "agosto"
        Month.SEPTEMBER -> "septiembre"
        Month.OCTOBER -> "octubre"
        Month.NOVEMBER -> "noviembre"
        Month.DECEMBER -> "diciembre"
    }
}
