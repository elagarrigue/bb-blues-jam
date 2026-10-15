package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.remote.ServiceCodes
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Confirmed-only edits and deletions of future jams: the cache follows only the server's echo. */
internal class SetlistJamEdits(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
    private val failureLog: WriteFailureLog,
) : SetlistJamEditRepository {
    private val entries = MutableStateFlow<List<JamEdit>>(emptyList())

    override fun observeJamEdits(): Flow<List<JamEdit>> = entries.asStateFlow()

    override fun dismissJamEdit(id: Long) {
        entries.update { list -> list.filterNot { it.id == id && it.state !is JamEdit.State.Sending } }
    }

    /** Runs in [scope] from the first instruction, so the caller's cancellation never cancels it. */
    override suspend fun updateJam(
        jamDate: LocalDate,
        newDate: LocalDate,
        startTime: LocalTime,
        venue: String,
    ): UpdateJamOutcome = queued(jamDate, JamEdit.Change.Update(newDate, startTime, venue)) { sendUpdate(it) }

    /** Runs in [scope] from the first instruction, so the caller's cancellation never cancels it. */
    override suspend fun deleteJam(jamDate: LocalDate, expectedSongs: Int): DeleteJamOutcome =
        queued(jamDate, JamEdit.Change.Delete(expectedSongs)) { sendDelete(it) }

    private suspend fun <T> queued(jamDate: LocalDate, change: JamEdit.Change, send: suspend (JamEdit) -> T): T =
        scope.async(start = CoroutineStart.UNDISPATCHED) {
            queue.order.withLock {
                val entry = JamEdit(queue.ids.incrementAndGet(), jamDate, change, JamEdit.State.Sending)
                entries.update { it + entry }
                scope.async(start = CoroutineStart.UNDISPATCHED) {
                    queue.writes.withLock { send(entry) }
                }
            }.await()
        }.await()

    private suspend fun sendUpdate(entry: JamEdit): UpdateJamOutcome {
        val change = entry.change as JamEdit.Change.Update
        val answer = writer.send(
            UPDATE_JAM,
            mapOf(
                SetlistRequestFields.DATE to JsonPrimitive(entry.jamDate.toString()),
                NEW_DATE to JsonPrimitive(change.newDate.toString()),
                TIME to JsonPrimitive(change.startTime.toString()),
                VENUE to JsonPrimitive(change.venue),
            ),
        )
        val outcome = when (answer) {
            is AdminAnswer.Refused ->
                if (answer.failure == DataFailure.Service(ServiceCodes.UNKNOWN_ACTION)) {
                    UpdateJamOutcome.ScriptOutdated
                } else {
                    UpdateJamOutcome.NotUpdated(answer.outcome)
                }

            is AdminAnswer.Ok -> confirmedUpdate(answer, entry.jamDate, change)
        }
        if (outcome is UpdateJamOutcome.Updated) {
            mirrorUpdate(entry.jamDate, outcome)
            entries.update { list -> list.filterNot { it.id == entry.id } }
        } else {
            val state = when (outcome) {
                is UpdateJamOutcome.NotUpdated -> JamEdit.State.Failed(outcome.reason)
                else -> JamEdit.State.Outdated
            }
            entries.update { list -> list.map { if (it.id == entry.id) it.copy(state = state) else it } }
            failureLog.write(
                "updateJam failed: date=${entry.jamDate} newDate=${change.newDate} outcome=${outcome.logName()}",
            )
        }
        return outcome
    }

    private fun confirmedUpdate(
        answer: AdminAnswer.Ok,
        jamDate: LocalDate,
        change: JamEdit.Change.Update,
    ): UpdateJamOutcome {
        fun field(name: String) = (answer.body[name] as? JsonPrimitive)?.contentOrNull
        val venue = field(VENUE)
        val changed = (answer.body[CHANGED] as? JsonPrimitive)?.booleanOrNull
        val echoed = field(SetlistRequestFields.DATE) == change.newDate.toString() &&
            field(PREVIOUS_DATE) == jamDate.toString() &&
            field(TIME) == change.startTime.toString() &&
            !venue.isNullOrBlank() &&
            changed != null
        return if (echoed) {
            UpdateJamOutcome.Updated(change.newDate, change.startTime, venue.orEmpty(), changed == true)
        } else {
            UpdateJamOutcome.NotUpdated(WriteOutcome.Unavailable)
        }
    }

    private suspend fun mirrorUpdate(jamDate: LocalDate, updated: UpdateJamOutcome.Updated) {
        try {
            if (updated.date == jamDate) {
                setlistDao.updateJamDetails(jamDate.toString(), updated.startTime.toString(), updated.venue)
            } else {
                setlistDao.moveJam(
                    jamDate.toString(),
                    updated.date.toString(),
                    updated.startTime.toString(),
                    updated.venue,
                )
            }
        } catch (ignored: SQLException) {
            // The Sheet is authoritative; the refresh that follows repairs the cache.
        }
    }

    private suspend fun sendDelete(entry: JamEdit): DeleteJamOutcome {
        val change = entry.change as JamEdit.Change.Delete
        val answer = writer.send(
            DELETE_JAM,
            mapOf(
                SetlistRequestFields.DATE to JsonPrimitive(entry.jamDate.toString()),
                EXPECTED_SONGS to JsonPrimitive(change.expectedSongs),
            ),
        )
        val outcome = when (answer) {
            is AdminAnswer.Refused ->
                if (answer.failure == DataFailure.Service(ServiceCodes.UNKNOWN_ACTION)) {
                    DeleteJamOutcome.ScriptOutdated
                } else {
                    DeleteJamOutcome.NotDeleted(answer.outcome)
                }

            is AdminAnswer.Ok -> confirmedDelete(answer, entry.jamDate)
        }
        if (outcome is DeleteJamOutcome.Deleted) {
            mirrorDelete(entry.jamDate)
            entries.update { list -> list.filterNot { it.id == entry.id } }
        } else {
            val state = when (outcome) {
                is DeleteJamOutcome.NotDeleted -> JamEdit.State.Failed(outcome.reason)
                else -> JamEdit.State.Outdated
            }
            entries.update { list -> list.map { if (it.id == entry.id) it.copy(state = state) else it } }
            failureLog.write("deleteJam failed: date=${entry.jamDate} outcome=${outcome.logName()}")
        }
        return outcome
    }

    private fun confirmedDelete(answer: AdminAnswer.Ok, jamDate: LocalDate): DeleteJamOutcome {
        val echoedDate = (answer.body[SetlistRequestFields.DATE] as? JsonPrimitive)?.contentOrNull
        val songs = (answer.body[DELETED_SONGS] as? JsonPrimitive)?.intOrNull
        return if (echoedDate == jamDate.toString() && songs != null && songs >= 0) {
            DeleteJamOutcome.Deleted(songs)
        } else {
            DeleteJamOutcome.NotDeleted(WriteOutcome.Unavailable)
        }
    }

    private suspend fun mirrorDelete(jamDate: LocalDate) {
        try {
            setlistDao.deleteJamRow(jamDate.toString())
        } catch (ignored: SQLException) {
            // The Sheet is authoritative; the refresh that follows repairs the cache.
        }
    }

    private companion object {
        const val UPDATE_JAM = "updateJam"
        const val DELETE_JAM = "deleteJam"
        const val NEW_DATE = "newDate"
        const val PREVIOUS_DATE = "previousDate"
        const val TIME = "time"
        const val VENUE = "venue"
        const val CHANGED = "changed"
        const val EXPECTED_SONGS = "expectedSongs"
        const val DELETED_SONGS = "deletedSongs"
    }
}
