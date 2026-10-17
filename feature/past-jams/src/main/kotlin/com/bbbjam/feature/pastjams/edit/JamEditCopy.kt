package com.bbbjam.feature.pastjams.edit

import com.bbbjam.feature.pastjams.PastJamsCopy
import java.time.LocalDate

/** The copy of editing and deleting a future jam (`admin-edit-delete-jam`). Rioplatense Spanish with vos (D-12). */
internal object JamEditCopy {
    const val EDIT = "Editar la jam"
    const val DELETE = "Borrar la jam"
    const val SAVING = "Guardando los cambios…"
    const val DELETING = "Borrando la jam…"
    const val SAVE = "Guardar"
    const val DATE_MOVES_NOTE = "La lista y los músicos anotados pasan a la nueva fecha."
    const val VISIBLE_NOTE = "Los músicos ven el cambio cuando actualicen la app."
    const val UNDO_WARNING = "No se puede deshacer."
    const val EMPTY_LIST = "La lista está vacía."
    const val ONE_SONG_LOST = "Se pierde el tema de la lista y los músicos anotados."
    const val PUBLISHED_LOST =
        "Los músicos ya ven esta jam y su lista: la van a dejar de ver cuando actualicen la app."
    const val CREATED_LOST = "Los músicos ven la fecha: la van a dejar de ver cuando actualicen la app."
    const val NO_JAM_LEFT = "No va a quedar ninguna jam programada."
    const val UPDATE_FAILED = "No se pudo guardar la jam"
    const val DELETE_FAILED = "No se pudo borrar la jam"
    const val CONSEQUENCE = "La jam sigue como estaba."
    const val SETLIST_CHANGED = "La lista cambió mientras tanto. Revisala y probá de nuevo."
    const val SCRIPT_OUTDATED =
        "El script de la planilla todavía no sabe editar ni borrar jams. Actualizalo con la guía y probá de nuevo."

    fun editDescription(date: LocalDate): String = "$EDIT del ${PastJamsCopy.sentenceDate(date)}"

    fun deleteDescription(date: LocalDate): String = "$DELETE del ${PastJamsCopy.sentenceDate(date)}"

    fun deleteTitle(date: LocalDate): String = "¿Borrar la jam del ${PastJamsCopy.sentenceDate(date)}?"

    fun songsLost(count: Int): String = when (count) {
        0 -> EMPTY_LIST
        1 -> ONE_SONG_LOST
        else -> "Se pierden los $count temas de la lista y los músicos anotados."
    }

    fun nextJamBecomes(date: LocalDate): String = "La próxima jam pasa a ser la del ${PastJamsCopy.sentenceDate(date)}."

    fun nextJamStays(date: LocalDate): String = "La próxima jam sigue siendo la del ${PastJamsCopy.sentenceDate(date)}."

    fun laterError(firstHeldBack: LocalDate): String =
        "Tiene que ser antes de la jam del ${PastJamsCopy.sentenceDate(firstHeldBack)}: " +
            "si no, esa pasaría a ser la próxima."

    fun earlierThanUpcomingError(upcoming: LocalDate): String =
        "Tiene que ser después de la próxima jam (${PastJamsCopy.sentenceDate(upcoming)}): " +
            "si no, la dejaría en espera."
}
