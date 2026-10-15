package com.bbbjam.feature.assistant

import java.time.LocalDate

/** The copy of replacing the upcoming jam's list (`assistant-proposal-targets`), with *vos* (D-12). */
internal object ReplaceCopy {
    const val REPLACE = "Reemplazar la lista de la próxima jam"
    const val REPLACE_RETRY = "Reintentar el reemplazo"
    const val DIALOG_TITLE = "¿Reemplazar la lista?"
    const val CONFIRM = "Reemplazar"

    const val NOTE_EQUAL = "La lista de la próxima jam ya es igual a esta."
    const val NOTE_REPEATED =
        "La lista de la próxima jam tiene temas repetidos; arreglala en la planilla para poder reemplazarla."

    const val NO_ASSIGNMENTS = "Ninguno de esos temas tiene músicos anotados."
    const val PUBLISHED = "La lista está publicada: los músicos ven cada cambio apenas se guarda."
    const val MISMATCH = "La lista de la próxima jam no quedó igual a la propuesta. Revisala en Próxima jam."
    const val UNORDERED = "La planilla tiene posiciones repetidas o vacías; ordená la lista a mano."

    const val REMOVING = "Quitando…"
    const val REMOVED = "Quitado"
    const val REMOVAL_NOT_ATTEMPTED = "Sin intentar"
    const val KEPT = "Ya estaba; conserva sus músicos"
    const val ORDERING = "Ordenando la lista…"
    const val ORDER_FAILED = "No se pudo ordenar la lista."
    const val ORDER_NOT_ATTEMPTED = "Orden sin intentar."

    fun intro(count: Int, date: LocalDate): String =
        "La lista de la jam del ${ApplyCopy.jamDate(date)} va a quedar igual a esta: " +
            (if (count == 1) "1 tema." else "$count temas, en este orden.")

    fun removed(titles: List<String>): String =
        (if (titles.size == 1) "Se quita 1 tema: " else "Se quitan ${titles.size} temas: ") +
            titles.joinToString(", ") + "."

    fun lost(count: Int): String = when (count) {
        0 -> NO_ASSIGNMENTS
        1 -> "Se pierde 1 músico anotado en esos temas."
        else -> "Se pierden $count músicos anotados en esos temas."
    }

    fun kept(count: Int): String = if (count == 1) {
        "1 ya estaba: conserva su tonalidad y sus músicos."
    } else {
        "$count ya estaban: conservan su tonalidad y sus músicos."
    }

    fun added(count: Int): String = (if (count == 1) "Se agrega 1 tema" else "Se agregan $count temas") +
        " con la tonalidad del catálogo."

    fun empty(count: Int): String = "La lista está vacía: se cargan ${ApplyCopy.themes(count)}."
}
