package com.bbbjam.feature.assistant.create

import com.bbbjam.feature.assistant.apply.ApplyCopy
import java.time.LocalDate

/** The copy of creating a jam with the proposal's list (`assistant-proposal-targets`), with *vos* (D-12). */
internal object CreateCopy {
    const val CREATE = "Crear una jam nueva con esta lista"
    const val FORM_TITLE = "Nueva jam con esta lista"
    const val NOTE_NO_UPCOMING = "No hay una próxima jam: podés crear una con esta lista."
    const val CREATING = "Creando la jam…"
    const val NOT_LOADED = "No se cargó ningún tema."
    const val OUTDATED_HELD_BACK =
        "La jam se creó, pero el script de la planilla todavía no sabe cargar listas en una jam en espera. " +
            "Actualizalo con la guía y reintentá."

    fun formNote(count: Int): String = "Se crea como borrador y " +
        (if (count == 1) "se carga 1 tema." else "se cargan los $count temas en este orden.")

    fun waitsNote(upcoming: LocalDate): String =
        "Queda en espera detrás de la del ${ApplyCopy.jamDate(upcoming)}; la lista se carga en esta jam nueva " +
            "y la vas a ver en Próxima jam cuando pase esa."

    fun submit(count: Int): String = "Crear y cargar ${ApplyCopy.themes(count)}"

    fun loading(date: LocalDate, done: Int, total: Int): String =
        "Cargando temas en la jam del ${ApplyCopy.jamDate(date)}: $done de $total."

    fun success(date: LocalDate, count: Int, behind: LocalDate?): String {
        val head = "Listo: se creó la jam del ${ApplyCopy.jamDate(date)} como borrador"
        val wait = behind?.let { ", en espera detrás de la del ${ApplyCopy.jamDate(it)}," } ?: ""
        return "$head$wait y " + (if (count == 1) "se cargó 1 tema." else "se cargaron $count temas.")
    }

    fun notCreated(reason: String): String = "No se pudo crear la jam: $reason $NOT_LOADED"

    fun partial(date: LocalDate, added: Int, planned: Int, detail: String): String = "La jam del ${ApplyCopy.jamDate(
        date,
    )} se creó como borrador, pero se cargaron $added de ${ApplyCopy.themes(planned)}. " +
        detail

    fun failedCount(count: Int): String = if (count == 1) "1 no se agregó." else "$count no se agregaron."

    fun stopped(date: LocalDate, added: Int, planned: Int): String =
        "Detuviste la carga: la jam del ${ApplyCopy.jamDate(date)} se creó como borrador y " +
            "se cargaron $added de ${ApplyCopy.themes(planned)}."

    fun interrupted(date: LocalDate, added: Int, planned: Int): String =
        "Se interrumpió la carga: la jam del ${ApplyCopy.jamDate(date)} se creó como borrador y " +
            "se cargaron $added de ${ApplyCopy.themes(planned)}."
}
