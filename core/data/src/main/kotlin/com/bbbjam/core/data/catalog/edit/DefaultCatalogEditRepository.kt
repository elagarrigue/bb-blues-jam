package com.bbbjam.core.data.catalog.edit

import android.database.SQLException
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminAnswer
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogEditDao
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.remote.ServiceCodes
import com.bbbjam.core.model.SongId
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * [CatalogEditRepository] over the admin POST path. One fair [Mutex] serializes the writes; each runs in
 * [scope] from its first instruction, so leaving the screen never cancels one (only its result is lost).
 * Room follows only a valid echo, and the catalog refresh that follows repairs anything the mirror missed.
 */
internal class DefaultCatalogEditRepository(
    private val writer: AdminWriter,
    private val dao: CatalogEditDao,
    private val catalog: CatalogRepository,
    private val scope: DataScope,
) : CatalogEditRepository {
    private val mutex = Mutex()

    override suspend fun createSong(fields: SongFields): CreateSongOutcome = serialized {
        val answer = writer.send(CREATE_SONG, fields.toRequest())
        val outcome = when (answer) {
            is AdminAnswer.Refused ->
                if (answer.isOutdated()) {
                    CreateSongOutcome.ScriptOutdated
                } else {
                    CreateSongOutcome.NotCreated(
                        answer.outcome,
                    )
                }

            is AdminAnswer.Ok -> {
                val echo = songEchoOf(answer.body)
                if (echo == null) {
                    CreateSongOutcome.NotCreated(WriteOutcome.Unavailable)
                } else {
                    mirror { dao.insertCreated(echo.toEntity()) }
                    CreateSongOutcome.Created(SongId(echo.id))
                }
            }
        }
        refreshAfter(answer)
        outcome
    }

    override suspend fun updateSong(id: SongId, fields: SongFields): UpdateSongOutcome = serialized {
        val answer = writer.send(UPDATE_SONG, fields.toRequest() + (ID to JsonPrimitive(id.value)))
        val outcome = when (answer) {
            is AdminAnswer.Refused ->
                if (answer.isOutdated()) {
                    UpdateSongOutcome.ScriptOutdated
                } else {
                    UpdateSongOutcome.NotUpdated(
                        answer.outcome,
                    )
                }

            is AdminAnswer.Ok -> {
                val echo = songEchoOf(answer.body)
                val changed = (answer.body[CHANGED] as? JsonPrimitive)?.booleanOrNull
                if (echo != null && echo.id == id.value && changed != null) {
                    mirror { dao.updateFields(id.value, echo.title, echo.artist, echo.defaultKey) }
                    UpdateSongOutcome.Updated(changed)
                } else {
                    UpdateSongOutcome.NotUpdated(WriteOutcome.Unavailable)
                }
            }
        }
        refreshAfter(answer)
        outcome
    }

    override suspend fun deleteSong(id: SongId): DeleteSongOutcome = serialized {
        val answer = writer.send(DELETE_SONG, mapOf(ID to JsonPrimitive(id.value)))
        val outcome = when (answer) {
            is AdminAnswer.Refused ->
                if (answer.isOutdated()) {
                    DeleteSongOutcome.ScriptOutdated
                } else {
                    DeleteSongOutcome.NotDeleted(
                        answer.outcome,
                    )
                }

            is AdminAnswer.Ok ->
                if ((answer.body[ID] as? JsonPrimitive)?.contentOrNull == id.value) {
                    DeleteSongOutcome.Deleted
                } else {
                    DeleteSongOutcome.NotDeleted(WriteOutcome.Unavailable)
                }
        }
        if (outcome is DeleteSongOutcome.Deleted) mirror { dao.delete(id.value) }
        refreshAfter(answer)
        outcome
    }

    /** Runs [block] in [scope] from its first instruction, after the earlier writes; the caller only awaits. */
    private suspend fun <T> serialized(block: suspend () -> T): T =
        scope.async(start = CoroutineStart.UNDISPATCHED) { mutex.withLock { block() } }.await()

    private suspend fun mirror(block: suspend () -> Unit) {
        try {
            block()
        } catch (ignored: SQLException) {
            // The Sheet is authoritative; the refresh that follows repairs the cache.
        }
    }

    /** Whatever the answer was, a write that reached the server may have changed the Sheet. */
    private fun refreshAfter(answer: AdminAnswer) {
        if (answer is AdminAnswer.Ok) scope.launch { catalog.refresh() }
    }

    private fun AdminAnswer.Refused.isOutdated(): Boolean = failure == DataFailure.Service(ServiceCodes.UNKNOWN_ACTION)

    private companion object {
        const val CREATE_SONG = "createSong"
        const val UPDATE_SONG = "updateSong"
        const val DELETE_SONG = "deleteSong"
        const val ID = "id"
        const val CHANGED = "changed"
    }
}
