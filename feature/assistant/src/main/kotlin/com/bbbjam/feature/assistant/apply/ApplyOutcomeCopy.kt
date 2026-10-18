package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.admin.WriteRejection
import com.bbbjam.core.ui.text.WriteFailureCopy
import java.time.LocalDate

/** The copy of a run's progress, summary and failures (`assistant-apply-proposal`), with *vos* (D-12). */
internal object ApplyOutcomeCopy {
    const val WRITE_OFFLINE = "No pudimos confirmar si se agregó: no hay conexión o el servidor tardó."

    private fun alreadyThere(count: Int) =
        if (count == 1) "1 ya estaba en la lista." else "$count ya estaban en la lista."

    fun running(done: Int, total: Int, date: LocalDate): String =
        "Agregando temas a la jam del ${ApplyCopy.jamDate(date)}: $done de $total."

    fun success(added: Int, skipped: Int, date: LocalDate): String {
        val jam = ApplyCopy.jamDate(date)
        val head = when (added) {
            0 -> "No se agregó ningún tema a la jam del $jam."
            1 -> "Listo: se agregó 1 tema a la jam del $jam."
            else -> "Listo: se agregaron $added temas a la jam del $jam."
        }
        return if (skipped > 0) "$head ${alreadyThere(skipped)}" else head
    }

    fun partial(added: Int, planned: Int, failed: Int): String =
        "Se agregaron $added de ${ApplyCopy.themes(planned)}. " +
            if (failed == 1) "1 no se agregó." else "$failed no se agregaron."

    fun aborted(added: Int, planned: Int, reason: String): String =
        "Se cortó la carga: se agregaron $added de ${ApplyCopy.themes(planned)}. $reason"

    fun stopped(added: Int, planned: Int): String =
        "Detuviste la carga: se agregaron $added de ${ApplyCopy.themes(planned)}."

    fun interrupted(added: Int, planned: Int): String =
        "Se interrumpió la carga: se agregaron $added de ${ApplyCopy.themes(planned)}."

    /** The reason a write did not happen, as a sentence. */
    fun writeFailure(reason: WriteOutcome): String = when (reason) {
        WriteOutcome.AccessRefused -> WriteFailureCopy.ACCESS_REFUSED

        WriteOutcome.Offline -> WRITE_OFFLINE

        WriteOutcome.Unavailable, WriteOutcome.Done -> WriteFailureCopy.UNAVAILABLE

        is WriteOutcome.Rejected -> when (reason.rejection) {
            WriteRejection.UNKNOWN_SONG -> WriteFailureCopy.NOT_IN_CATALOG
            WriteRejection.UNKNOWN_JAM, WriteRejection.JAM_NOT_EDITABLE -> WriteFailureCopy.JAM_CHANGED
            else -> WriteFailureCopy.SHEET_REFUSED
        }
    }

    /** A song's failure caption: the offline sentence already says nothing was confirmed. */
    fun songFailure(reason: WriteOutcome): String =
        if (reason == WriteOutcome.Offline) WRITE_OFFLINE else "No se agregó: ${writeFailure(reason)}"
}
