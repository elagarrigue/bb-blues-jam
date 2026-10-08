package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.SetlistDao
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Confirmed-only publish write, pending state and safe local diagnostics. */
internal class SetlistPublishes(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val scope: DataScope,
    private val ids: AtomicLong,
    private val order: Mutex,
    private val writes: Mutex,
    private val failureLog: WriteFailureLog,
) : SetlistPublishRepository {
    private val entries = MutableStateFlow<List<SetlistPublish>>(emptyList())

    override fun observePublishes(): Flow<List<SetlistPublish>> = entries.asStateFlow()

    override suspend fun publishSetlist(jamDate: LocalDate): PublishOutcome =
        scope.async(start = CoroutineStart.UNDISPATCHED) {
            order.withLock {
                val entry = SetlistPublish(ids.incrementAndGet(), jamDate, SetlistPublish.State.Sending)
                entries.update { it + entry }
                scope.async(start = CoroutineStart.UNDISPATCHED) {
                    writes.withLock { send(entry) }
                }
            }.await()
        }.await()

    override fun dismissPublish(id: Long) {
        entries.update { list -> list.filterNot { it.id == id && it.state is SetlistPublish.State.Failed } }
    }

    private suspend fun send(entry: SetlistPublish): PublishOutcome {
        val result = writer.send(PUBLISH_SETLIST, mapOf(DATE to JsonPrimitive(entry.jamDate.toString())))
        val outcome = when (result) {
            is AdminAnswer.Refused -> PublishOutcome.NotPublished(result.outcome)

            is AdminAnswer.Ok -> {
                val alreadyPublished = (result.body[ALREADY_PUBLISHED] as? JsonPrimitive)?.booleanOrNull
                if (alreadyPublished == null) {
                    PublishOutcome.NotPublished(WriteOutcome.Unavailable)
                } else {
                    mirror(entry.jamDate)
                    entries.update { list -> list.filterNot { it.id == entry.id } }
                    return PublishOutcome.Published(alreadyPublished)
                }
            }
        }
        val reason = (outcome as PublishOutcome.NotPublished).reason
        entries.update { list ->
            list.map { if (it.id == entry.id) it.copy(state = SetlistPublish.State.Failed(reason)) else it }
        }
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
        const val DATE = "date"
        const val ALREADY_PUBLISHED = "alreadyPublished"
    }
}
