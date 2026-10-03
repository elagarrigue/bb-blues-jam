package com.bbbjam.feature.nextjam

import java.time.DayOfWeek
import java.time.Month

/**
 * The Próxima jam copy, approved by the user on 2 October 2026 (`docs/specs/next-jam-read-only-list.md`,
 * C1). Rioplatense Spanish with vos (D-12). Kept in Kotlin, not string resources, as in `InfoCopy`.
 *
 * Day and month names are written out here instead of coming from `DateTimeFormatter` and a
 * `Locale`: desugared java.time on API 24–25 and the JVM may render locale text differently, and the
 * copy must be identical on the device and in tests (Decision 5).
 */
internal object NextJamCopy {
    const val TONIGHT = "Esta noche"
    const val TODAY = "Hoy"
    const val TOMORROW = "Mañana"

    const val NO_UPCOMING_JAM =
        "La próxima jam todavía no tiene fecha. Cuando se confirme, la vas a ver acá."
    const val SETLIST_WITHHELD =
        "La lista de temas se está armando. Cuando se publique, la vas a ver acá."
    const val SETLIST_UNAVAILABLE =
        "No se pudo leer la lista de temas de esta jam. Avisale a la organización."

    /**
     * A row's state and its action for screen readers, approved on 3 October 2026
     * (`docs/specs/song-row-expansion.md`, C1). TalkBack reads the action as "Presioná dos veces
     * para ver los cupos".
     */
    const val ROW_EXPANDED = "expandido"
    const val ROW_COLLAPSED = "contraído"
    const val SHOW_SLOTS = "ver los cupos"
    const val HIDE_SLOTS = "ocultar los cupos"

    /** "En 29 días": always days, no weeks (exact and short). Only for two days or more. */
    fun inDays(days: Long): String = "En $days días"

    /** The note under a setlist that lost [count] invalid rows (P4); [count] is at least 1. */
    fun droppedRows(count: Int): String =
        if (count == 1) "Falta 1 tema: no se pudo leer." else "Faltan $count temas: no se pudieron leer."

    /** What a screen reader says for the key: "Tonalidad Bm". */
    fun keyDescription(key: String): String = "Tonalidad $key"

    fun dayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Lunes"
        DayOfWeek.TUESDAY -> "Martes"
        DayOfWeek.WEDNESDAY -> "Miércoles"
        DayOfWeek.THURSDAY -> "Jueves"
        DayOfWeek.FRIDAY -> "Viernes"
        DayOfWeek.SATURDAY -> "Sábado"
        DayOfWeek.SUNDAY -> "Domingo"
    }

    fun monthName(month: Month): String = when (month) {
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
