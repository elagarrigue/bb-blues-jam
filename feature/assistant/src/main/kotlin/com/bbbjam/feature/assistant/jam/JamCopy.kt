package com.bbbjam.feature.assistant.jam

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.data.assistant.JamRejection
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.WriteFailureCopy
import com.bbbjam.feature.assistant.apply.ApplyCopy
import com.bbbjam.feature.assistant.apply.ApplyOutcomeCopy
import com.bbbjam.feature.assistant.run.JamFailure
import java.time.LocalDate
import java.time.LocalTime

/** The copy of creating the next jam (`assistant-create-jam`), with *vos* (D-12). */
internal object JamCopy {
    const val HEADING = "Nueva jam"
    const val CREATE = "Crear la jam"
    const val CREATE_RETRY = "Reintentar"
    const val DIALOG_TITLE = "¿Crear la jam?"
    const val CANCEL = "Cancelar"
    const val DRAFT_LINE =
        "Se crea como borrador: los músicos no la ven hasta que la pases a creada o publiques la lista."

    const val CREATING = "Creando la jam…"
    const val CREATED = "Jam creada como borrador."
    const val FAILURE_PREFIX = "No se pudo crear: "
    const val OFFLINE = "no pudimos confirmar si se creó: no hay conexión o el servidor tardó."
    const val INTERRUPTED = "se interrumpió la creación; revisá la planilla antes de reintentar."
    const val JAM_EXISTS = "ya hay una jam con esa fecha en la planilla."
    const val TAB_EXISTS = "ya hay una pestaña con esa fecha en la planilla; revisala antes de crear la jam."
    const val PAST_DATE = "esa fecha ya pasó."
    const val INVALID_DATA = "la planilla rechazó los datos de la jam."
    const val SCRIPT_OUTDATED =
        "El script de la planilla todavía no sabe crear jams. Actualizalo con la guía y probá de nuevo."

    const val BLOCKED_PAST = "Esa fecha ya pasó; pedime otra."
    const val BLOCKED_EXISTS = "Ya hay una jam con esa fecha. Pedime otra fecha."
    const val LIST_AFTER_CREATE = "Primero creá la jam; después podés cargar la lista."

    const val REJECTED_DATE = "La fecha propuesta no era válida; pedile otra."
    const val REJECTED_PAST = "La fecha propuesta ya pasó; pedile otra."
    const val REJECTED_TIME = "La hora propuesta no era válida; pedile otra."
    const val REJECTED_VENUE = "El lugar propuesto no era válido; pedile otro."

    /** "Sábado 28 de noviembre de 2026". */
    fun dateInWords(date: LocalDate): String =
        "${SpanishDateNames.day(date.dayOfWeek)} ${date.dayOfMonth} de ${SpanishDateNames.month(date.month)} " +
            "de ${date.year}"

    fun time(time: LocalTime): String =
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

    fun detail(time: LocalTime, venue: String): String = "${time(time)} · $venue"

    fun heldBackNote(upcoming: LocalDate): String =
        "Queda en espera: la próxima jam sigue siendo la del ${ApplyCopy.jamDate(upcoming)}."

    fun blockedEarlier(upcoming: LocalDate): String =
        "Esa fecha es anterior a la próxima jam (${ApplyCopy.jamDate(upcoming)}). " +
            "Si querés adelantarla, cambiá la fecha de esa jam en la pestaña Jams."

    fun heldBackLine(upcoming: LocalDate): String =
        "La próxima jam sigue siendo la del ${ApplyCopy.jamDate(upcoming)}; esta queda en espera hasta que pase."

    fun listHeldBack(upcoming: LocalDate): String =
        "La lista iría a la jam del ${ApplyCopy.jamDate(upcoming)}, no a la nueva: esta queda en espera. " +
            "Cargala cuando sea la próxima."

    fun rejection(reason: JamRejection): String = when (reason) {
        JamRejection.INVALID_DATE -> REJECTED_DATE
        JamRejection.PAST_DATE -> REJECTED_PAST
        JamRejection.INVALID_TIME -> REJECTED_TIME
        JamRejection.INVALID_VENUE -> REJECTED_VENUE
    }

    fun failure(failure: JamFailure): String =
        if (failure == JamFailure.ScriptOutdated) SCRIPT_OUTDATED else FAILURE_PREFIX + reason(failure)

    /** Why a jam was not created, as a sentence that follows a colon (the outdated script is a full line). */
    fun reason(failure: JamFailure): String = when (failure) {
        JamFailure.ScriptOutdated -> SCRIPT_OUTDATED
        JamFailure.Interrupted -> INTERRUPTED
        is JamFailure.Write -> writeReason(failure.reason)
    }

    private fun writeReason(reason: WriteOutcome): String = when (reason) {
        WriteOutcome.AccessRefused -> WriteFailureCopy.ACCESS_REFUSED

        WriteOutcome.Offline -> OFFLINE

        WriteOutcome.Unavailable, WriteOutcome.Done -> WriteFailureCopy.UNAVAILABLE

        is WriteOutcome.Rejected -> when (reason.rejection) {
            WriteRejection.JAM_EXISTS -> JAM_EXISTS
            WriteRejection.TAB_EXISTS -> TAB_EXISTS
            WriteRejection.PAST_DATE -> PAST_DATE
            WriteRejection.INVALID_DATE, WriteRejection.INVALID_TIME, WriteRejection.INVALID_VENUE -> INVALID_DATA
            else -> WriteFailureCopy.SHEET_REFUSED
        }
    }
}
