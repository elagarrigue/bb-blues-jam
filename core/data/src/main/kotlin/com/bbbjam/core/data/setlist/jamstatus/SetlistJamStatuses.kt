package com.bbbjam.core.data.setlist.jamstatus

import android.database.SQLException
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.remote.ServiceCodes
import com.bbbjam.core.data.setlist.engine.PendingEntries
import com.bbbjam.core.data.setlist.engine.QueuedWrite
import com.bbbjam.core.data.setlist.engine.SetlistMutationQueue
import com.bbbjam.core.data.setlist.engine.SetlistRequestFields
import com.bbbjam.core.data.setlist.engine.WriteFailureLog
import com.bbbjam.core.data.setlist.engine.enqueue
import com.bbbjam.core.data.setlist.jamedit.logName
import com.bbbjam.core.model.JamStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** Confirmed-only status changes: the cache mirrors the status only from the server's echo. */
internal class SetlistJamStatuses(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
    private val failureLog: WriteFailureLog,
) : SetlistJamStatusRepository {
    private val entries = PendingEntries<JamStatusChange>({ it.id }) { it.state !is JamStatusChange.State.Sending }

    override fun observeJamStatusChanges(): Flow<List<JamStatusChange>> = entries.observe()

    /** Runs in [scope] from the first instruction, so the caller's cancellation never cancels it. */
    override suspend fun setJamStatus(jamDate: LocalDate, status: JamStatus): SetJamStatusOutcome =
        queue.enqueue(scope) { id ->
            val entry = JamStatusChange(id, jamDate, status, JamStatusChange.State.Sending)
            entries.add(entry)
            QueuedWrite.Send { send(entry) }
        }

    override fun dismissJamStatusChange(id: Long) = entries.dismiss(id)

    private suspend fun send(entry: JamStatusChange): SetJamStatusOutcome {
        val answer = writer.send(
            SET_JAM_STATUS,
            mapOf(
                SetlistRequestFields.DATE to JsonPrimitive(entry.jamDate.toString()),
                STATUS to JsonPrimitive(entry.target.sheetText()),
            ),
        )
        val outcome = when (answer) {
            is AdminAnswer.Refused ->
                if (answer.failure == DataFailure.Service(ServiceCodes.UNKNOWN_ACTION)) {
                    SetJamStatusOutcome.ScriptOutdated
                } else {
                    SetJamStatusOutcome.NotSet(answer.outcome)
                }

            is AdminAnswer.Ok -> confirmed(answer, entry)
        }
        if (outcome is SetJamStatusOutcome.StatusSet) {
            mirror(entry)
            entries.remove(entry.id)
        } else {
            val state = when (outcome) {
                is SetJamStatusOutcome.NotSet -> JamStatusChange.State.Failed(outcome.reason)
                else -> JamStatusChange.State.Outdated
            }
            entries.fail(entry.id) { it.copy(state = state) }
            failureLog.write(
                "setJamStatus failed: date=${entry.jamDate} target=${entry.target.sheetText()} " +
                    "outcome=${outcome.logName()}",
            )
        }
        return outcome
    }

    private fun confirmed(answer: AdminAnswer.Ok, entry: JamStatusChange): SetJamStatusOutcome {
        val echoedDate = (answer.body[SetlistRequestFields.DATE] as? JsonPrimitive)?.contentOrNull
        val echoedStatus = (answer.body[STATUS] as? JsonPrimitive)?.contentOrNull
        val changed = (answer.body[CHANGED] as? JsonPrimitive)?.booleanOrNull
        return if (
            echoedDate == entry.jamDate.toString() && echoedStatus == entry.target.sheetText() && changed != null
        ) {
            SetJamStatusOutcome.StatusSet(entry.target, changed)
        } else {
            SetJamStatusOutcome.NotSet(WriteOutcome.Unavailable)
        }
    }

    private suspend fun mirror(entry: JamStatusChange) {
        try {
            when (entry.target) {
                JamStatus.PUBLISHED -> setlistDao.markPublished(entry.jamDate.toString())
                JamStatus.DRAFT -> setlistDao.updateJamStatus(entry.jamDate.toString(), "DRAFT")
                JamStatus.CREATED -> setlistDao.updateJamStatus(entry.jamDate.toString(), "CREATED")
            }
        } catch (ignored: SQLException) {
            // The Sheet is authoritative; the refresh that follows repairs the cache.
        }
    }

    private fun JamStatus.sheetText(): String = when (this) {
        JamStatus.DRAFT -> "BORRADOR"
        JamStatus.CREATED -> "CREADA"
        JamStatus.PUBLISHED -> "PUBLICADA"
    }

    private fun SetJamStatusOutcome.logName(): String = when (this) {
        is SetJamStatusOutcome.StatusSet -> "StatusSet"

        SetJamStatusOutcome.ScriptOutdated -> "ScriptOutdated"

        is SetJamStatusOutcome.NotSet -> when (val reason = reason) {
            WriteOutcome.AccessRefused -> "AccessRefused"
            WriteOutcome.Offline -> "Offline"
            WriteOutcome.Unavailable, WriteOutcome.Done -> "Unavailable"
            is WriteOutcome.Rejected -> "Rejected(${reason.code})"
        }
    }

    private companion object {
        const val SET_JAM_STATUS = "setJamStatus"
        const val STATUS = "status"
        const val CHANGED = "changed"
    }
}
