package com.bbbjam.feature.pastjams

import com.bbbjam.core.ui.text.SpanishDateNames
import java.time.LocalDate

/**
 * The Anteriores copy, approved by the user on 4 October 2026 (`docs/specs/past-jams-list.md`, C1
 * and P1). Rioplatense Spanish with vos (D-12). Kept in Kotlin, not string resources, as in
 * `NextJamCopy`. The shared state messages, labels and ages live in `:core:ui` (`ListStateCopy`).
 */
internal object PastJamsCopy {
    const val TITLE = "Jams anteriores"
    const val LOADING = "Cargando las jams anteriores"
    const val LOAD_FAILED = "No pudimos cargar las jams anteriores"
    const val EMPTY_TITLE = "Todavía no hay jams anteriores"
    const val EMPTY_MESSAGE = "Después de cada jam, su lista queda guardada acá para que veas qué se tocó."

    /** A past draft (P1): the jam happened, its list was never published. */
    const val SETLIST_NOT_PUBLISHED = "La lista de esta jam no se publicó."
    const val SETLIST_UNAVAILABLE = "No se pudo leer la lista de esta jam."
    const val EMPTY_SETLIST = "Esta jam no tiene temas cargados."

    /** "1 tema", "13 temas"; [count] is at least 1 (no songs is [EMPTY_SETLIST], never "0 temas"). */
    fun songCount(count: Int): String = if (count == 1) "1 tema" else "$count temas"

    /** The hook's tail after the first titles: "y 10 más". */
    fun andMore(count: Int): String = "y $count más"
}

/** "Sábado 25 de julio de 2026": day, date and year, no time. */
internal fun pastJamDateLabel(date: LocalDate): String {
    val day = SpanishDateNames.day(date.dayOfWeek)
    val month = SpanishDateNames.month(date.month)
    return "$day ${date.dayOfMonth} de $month de ${date.year}"
}
