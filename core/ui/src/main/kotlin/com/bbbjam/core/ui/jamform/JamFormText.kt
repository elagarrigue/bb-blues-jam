package com.bbbjam.core.ui.jamform

import com.bbbjam.core.ui.text.SpanishDateNames
import java.time.LocalDate

/**
 * The copy every jam form writes the same way: the field labels and hints and the field errors, in
 * Rioplatense Spanish with *vos* (D-12).
 */
object JamFormText {
    const val DATE_LABEL = "Fecha"
    const val DATE_HINT = "DD/MM/AAAA"
    const val TIME_LABEL = "Hora"
    const val TIME_HINT = "HH:MM"
    const val VENUE_LABEL = "Lugar"
    const val CANCEL = "Cancelar"
    const val DATE_FORMAT_ERROR = "Escribí la fecha como DD/MM/AAAA."
    const val DATE_PAST_ERROR = "Esa fecha ya pasó."
    const val DATE_EXISTS_ERROR = "Ya hay una jam ese día."
    const val TIME_ERROR = "Escribí la hora como HH:MM, por ejemplo 21:00."
    const val VENUE_EMPTY_ERROR = "Escribí el lugar."
    const val VENUE_LONG_ERROR = "El lugar puede tener hasta 80 letras."
    const val VENUE_FORMULA_ERROR = "El lugar no puede empezar con =, +, - o @."

    /** "sábado 31 de octubre": the date inside a sentence, day name in lower case. */
    fun sentenceDate(date: LocalDate): String = "${SpanishDateNames.day(
        date.dayOfWeek,
    ).lowercase()} ${date.dayOfMonth} de ${SpanishDateNames.month(date.month)}"

    /** The error of a date before the upcoming jam, which would put that jam on hold. */
    fun earlierError(upcoming: LocalDate): String =
        "Es antes de la próxima jam (${sentenceDate(upcoming)}) y la dejaría en espera. " +
            "Si de verdad querés eso, agregala en la planilla."
}
