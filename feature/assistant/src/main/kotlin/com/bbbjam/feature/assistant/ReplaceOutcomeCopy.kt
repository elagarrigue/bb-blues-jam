package com.bbbjam.feature.assistant

import com.bbbjam.core.data.admin.WriteOutcome
import java.time.LocalDate

/** The copy of a replace run's progress, summary and failures (`assistant-proposal-targets`), with *vos* (D-12). */
internal object ReplaceOutcomeCopy {
    private const val REMOVAL_OFFLINE = "No pudimos confirmar si se quitó: no hay conexión o el servidor tardó."

    fun running(step: Int, total: Int, date: LocalDate): String =
        "Reemplazando la lista de la jam del ${ApplyCopy.jamDate(date)}: paso $step de $total."

    fun success(date: LocalDate): String =
        "Listo: la lista de la jam del ${ApplyCopy.jamDate(date)} quedó igual a la propuesta."

    fun cutInAdds(added: Int, planned: Int, reason: String): String =
        "Se cortó el reemplazo antes de quitar temas: se agregaron $added de $planned y no se quitó ninguno. $reason"

    fun cutLater(added: Int, removed: Int, toRemove: Int, reason: String): String =
        "Se cortó el reemplazo: se agregaron $added y se quitaron $removed de $toRemove. $reason"

    fun notOrdered(reason: String): String = "Los temas quedaron cargados, pero no en el orden propuesto. $reason"

    fun stopped(added: Int, removed: Int): String =
        "Detuviste el reemplazo: se agregaron $added y se quitaron $removed."

    fun interrupted(added: Int, removed: Int): String =
        "Se interrumpió el reemplazo: se agregaron $added y se quitaron $removed."

    /** The reason a move did not happen: the sheet's own duplicate/empty positions get their own line. */
    fun moveFailure(reason: WriteOutcome): String =
        if (reason is WriteOutcome.Rejected && reason.code == "unordered_setlist") {
            ReplaceCopy.UNORDERED
        } else {
            ApplyOutcomeCopy.writeFailure(reason)
        }

    /** A removal's failure caption: the offline sentence already says nothing was confirmed. */
    fun removalFailure(reason: WriteOutcome): String =
        if (reason == WriteOutcome.Offline) REMOVAL_OFFLINE else "No se quitó: ${ApplyOutcomeCopy.writeFailure(reason)}"
}
