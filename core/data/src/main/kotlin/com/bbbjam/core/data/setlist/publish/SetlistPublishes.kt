package com.bbbjam.core.data.setlist.publish

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.setlist.engine.PendingEntries
import com.bbbjam.core.data.setlist.engine.QueuedWrite
import com.bbbjam.core.data.setlist.engine.SetlistMutationQueue
import com.bbbjam.core.data.setlist.engine.SetlistRequestFields
import com.bbbjam.core.data.setlist.engine.WriteFailureLog
import com.bbbjam.core.data.setlist.engine.enqueue
import com.bbbjam.core.data.setlist.jamedit.logName
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Confirmed-only publish write, pending state and safe local diagnostics. */
internal class SetlistPublishes(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
    private val failureLog: WriteFailureLog,
) : SetlistPublishRepository {
    private val entries = PendingEntries<SetlistPublish>({ it.id }) { it.state is SetlistPublish.State.Failed }

    override fun observePublishes(): Flow<List<SetlistPublish>> = entries.observe()

    override suspend fun publishSetlist(jamDate: LocalDate): PublishOutcome = queue.enqueue(scope) { id ->
        val entry = SetlistPublish(id, jamDate, SetlistPublish.State.Sending)
        entries.add(entry)
        QueuedWrite.Send { send(entry) }
    }

    override fun dismissPublish(id: Long) = entries.dismiss(id)

    private suspend fun send(entry: SetlistPublish): PublishOutcome {
        val result = writer.send(
            PUBLISH_SETLIST,
            mapOf(SetlistRequestFields.DATE to JsonPrimitive(entry.jamDate.toString())),
        )
        val outcome = when (result) {
            is AdminAnswer.Refused -> PublishOutcome.NotPublished(result.outcome)

            is AdminAnswer.Ok -> {
                val alreadyPublished = (result.body[ALREADY_PUBLISHED] as? JsonPrimitive)?.booleanOrNull
                if (alreadyPublished == null) {
                    PublishOutcome.NotPublished(WriteOutcome.Unavailable)
                } else {
                    mirror(entry.jamDate)
                    entries.remove(entry.id)
                    return PublishOutcome.Published(alreadyPublished)
                }
            }
        }
        val reason = (outcome as PublishOutcome.NotPublished).reason
        entries.fail(entry.id) { it.copy(state = SetlistPublish.State.Failed(reason)) }
        failureLog.write("publishSetlist failed: date=${entry.jamDate} outcome=${reason.logName()}")
        return outcome
    }

    private suspend fun mirror(date: LocalDate) {
        try {
            setlistDao.markPublished(date.toString())
        } catch (ignored: SQLException) {
            // The Sheet is authoritative; a later refresh restores the confirmed status.
        }
    }

    private fun WriteOutcome.logName(): String = when (this) {
        WriteOutcome.AccessRefused -> "AccessRefused"
        WriteOutcome.Offline -> "Offline"
        WriteOutcome.Unavailable -> "Unavailable"
        WriteOutcome.Done -> "Unavailable"
        is WriteOutcome.Rejected -> "Rejected($code)"
    }

    private companion object {
        const val PUBLISH_SETLIST = "publishSetlist"
        const val ALREADY_PUBLISHED = "alreadyPublished"
    }
}
