package com.bbbjam.core.data.setlist.jamcreate

import android.database.SQLException
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.remote.ServiceCodes
import com.bbbjam.core.data.setlist.engine.QueuedWrite
import com.bbbjam.core.data.setlist.engine.SetlistMutationQueue
import com.bbbjam.core.data.setlist.engine.SetlistRequestFields
import com.bbbjam.core.data.setlist.engine.WriteFailureLog
import com.bbbjam.core.data.setlist.engine.enqueue
import com.bbbjam.core.data.setlist.jamedit.logName
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Confirmed-only jam creation: the cache mirrors the row only from the server's echo. */
internal class SetlistJamCreations(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
    private val failureLog: WriteFailureLog,
) : SetlistJamCreationRepository {

    /** Runs in [scope] from the first instruction, so the caller's cancellation never cancels it. */
    override suspend fun createJam(date: LocalDate, startTime: LocalTime, venue: String): CreateJamOutcome =
        queue.enqueue(scope) { QueuedWrite.Send { send(date, startTime, venue) } }

    private suspend fun send(date: LocalDate, startTime: LocalTime, venue: String): CreateJamOutcome {
        val answer = writer.send(
            CREATE_JAM,
            mapOf(
                SetlistRequestFields.DATE to JsonPrimitive(date.toString()),
                TIME to JsonPrimitive(startTime.toString()),
                VENUE to JsonPrimitive(venue),
            ),
        )
        val outcome = when (answer) {
            is AdminAnswer.Refused ->
                if (answer.failure == DataFailure.Service(ServiceCodes.UNKNOWN_ACTION)) {
                    CreateJamOutcome.ScriptOutdated
                } else {
                    CreateJamOutcome.NotCreated(answer.outcome)
                }

            is AdminAnswer.Ok -> confirmed(answer, date, startTime)
        }
        if (outcome is CreateJamOutcome.Created) {
            mirror(outcome)
        } else {
            failureLog.write("createJam failed: date=$date outcome=${outcome.logName()}")
        }
        return outcome
    }

    private fun confirmed(answer: AdminAnswer.Ok, date: LocalDate, startTime: LocalTime): CreateJamOutcome {
        fun field(name: String) = (answer.body[name] as? JsonPrimitive)?.contentOrNull
        val venue = field(VENUE)
        val echoed = field(SetlistRequestFields.DATE) == date.toString() &&
            field(TIME) == startTime.toString() &&
            field(STATUS) == DRAFT_STATUS &&
            !venue.isNullOrBlank()
        return if (echoed) {
            CreateJamOutcome.Created(date, startTime, venue.orEmpty())
        } else {
            CreateJamOutcome.NotCreated(WriteOutcome.Unavailable)
        }
    }

    private suspend fun mirror(created: CreateJamOutcome.Created) {
        try {
            setlistDao.insertCreatedJam(created.date.toString(), created.startTime.toString(), created.venue)
        } catch (ignored: SQLException) {
            // The Sheet is authoritative; the refresh that follows repairs the cache.
        }
    }

    private fun CreateJamOutcome.logName(): String = when (this) {
        is CreateJamOutcome.Created -> "Created"

        CreateJamOutcome.ScriptOutdated -> "ScriptOutdated"

        is CreateJamOutcome.NotCreated -> when (val reason = reason) {
            WriteOutcome.AccessRefused -> "AccessRefused"
            WriteOutcome.Offline -> "Offline"
            WriteOutcome.Unavailable, WriteOutcome.Done -> "Unavailable"
            is WriteOutcome.Rejected -> "Rejected(${reason.code})"
        }
    }

    private companion object {
        const val CREATE_JAM = "createJam"
        const val TIME = "time"
        const val VENUE = "venue"
        const val STATUS = "status"
        const val DRAFT_STATUS = "BORRADOR"
    }
}
