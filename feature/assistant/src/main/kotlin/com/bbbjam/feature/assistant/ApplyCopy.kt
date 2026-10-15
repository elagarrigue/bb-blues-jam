package com.bbbjam.feature.assistant

import com.bbbjam.core.ui.text.SpanishDateNames
import java.time.LocalDate

/** The copy of loading a proposal into the upcoming jam (`assistant-apply-proposal`), with *vos* (D-12). */
internal object ApplyCopy {
    const val APPLY = "Agregar a la próxima jam"
    const val APPLY_RETRY = "Reintentar los que faltan"
    const val APPLY_DIALOG_TITLE = "¿Cargar esta lista?"
    const val APPLY_CANCEL = "Cancelar"
    const val APPLY_STOP = "Detener"
    const val APPLY_PUBLISHED =
        "La lista ya está publicada: los músicos van a ver los temas apenas se agreguen."
    const val BLOCKED_UNREADABLE =
        "No pude leer la lista de la próxima jam. Abrí Próxima jam para actualizarla y volvé."
    const val BLOCKED_ALL_PRESENT = "Todos estos temas ya están en la lista de la próxima jam."

    const val STATUS_ADDING = "Agregando…"
    const val STATUS_ADDED = "Agregado"
    const val STATUS_SKIPPED = "Ya estaba en la lista"
    const val STATUS_NOT_ATTEMPTED = "Sin intentar"

    internal fun themes(count: Int) = if (count == 1) "1 tema" else "$count temas"

    /** "sábado 31 de octubre": the day in lower case, as a sentence needs it. */
    fun jamDate(date: LocalDate): String =
        "${SpanishDateNames.day(date.dayOfWeek).lowercase()} ${date.dayOfMonth} de " +
            SpanishDateNames.month(date.month)

    fun applyIntro(count: Int, date: LocalDate): String =
        (if (count == 1) "Se va a agregar 1 tema" else "Se van a agregar $count temas") +
            " al final de la lista de la jam del ${jamDate(date)}, " +
            (if (count == 1) "" else "en este orden y ") +
            "con la tonalidad del catálogo. Después podés cambiar cualquier tonalidad."

    fun applyExisting(count: Int): String = "La lista ya tiene ${themes(count)}; los nuevos van después."

    fun applySkipped(titles: List<String>): String = (
        if (titles.size ==
            1
        ) {
            "1 ya está en la lista y no se agrega: "
        } else {
            "${titles.size} ya están en la lista y no se agregan: "
        }
        ) + titles.joinToString(", ") + "."

    fun applyConfirm(count: Int): String = "Agregar ${themes(count)}"
}
