package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.model.SongId
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

/** Reorder mutation entries, request and confirmed cache mirror. */
internal class SetlistMoves(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
    private val ids: AtomicLong,
    private val order: Mutex,
    private val writes: Mutex,
) : SetlistMoveRepository {
    private val entries = MutableStateFlow<List<SetlistMove>>(emptyList())

    override fun observeMoves(): Flow<List<SetlistMove>> = entries.asStateFlow()

    override suspend fun moveSong(jamDate: LocalDate, songId: SongId, toPosition: Int): MoveSongOutcome =
        scope.async(start = CoroutineStart.UNDISPATCHED) {
            order.withLock {
                val entry = start(ids.incrementAndGet(), jamDate, songId, toPosition)
                scope.async(start = CoroutineStart.UNDISPATCHED) { writes.withLock { send(entry) } }
            }.await()
        }.await()

    suspend fun start(id: Long, jamDate: LocalDate, songId: SongId, toPosition: Int): SetlistMove {
        val title = displayedTitle(catalogDao, setlistDao, jamDate, songId)
        val state = if (toPosition < 1) {
            SetlistMove.State.Failed(WriteOutcome.Rejected("invalid_position"))
        } else {
            SetlistMove.State.Sending
        }
        val entry = SetlistMove(id, jamDate, songId, title, toPosition, state)
        entries.update { it + entry }
        return entry
    }

    suspend fun send(entry: SetlistMove): MoveSongOutcome {
        if (entry.state is SetlistMove.State.Failed) return MoveSongOutcome.NotMoved(entry.state.reason)
        val result = writer.write(
            MOVE_SONG,
            mapOf(
                DATE to JsonPrimitive(entry.jamDate.toString()),
                SONG_ID to JsonPrimitive(entry.songId.value),
                TO_POSITION to JsonPrimitive(entry.toPosition),
            ),
        )
        return if (result == WriteOutcome.Done) {
            try {
                setlistDao.moveSetlistSong(entry.jamDate.toString(), entry.songId.value, entry.toPosition)
            } catch (ignored: SQLException) {
                // The Sheet is authoritative; the next refresh restores the local copy.
            }
            entries.update { list -> list.filterNot { it.id == entry.id } }
            MoveSongOutcome.Moved
        } else {
            entries.update { list ->
                list.map { if (it.id == entry.id) it.copy(state = SetlistMove.State.Failed(result)) else it }
            }
            MoveSongOutcome.NotMoved(result)
        }
    }

    fun dismiss(id: Long) {
        entries.update { list -> list.filterNot { it.id == id && it.state is SetlistMove.State.Failed } }
    }

    private companion object {
        const val MOVE_SONG = "moveSong"
        const val DATE = "date"
        const val SONG_ID = "songId"
        const val TO_POSITION = "toPosition"
    }
}
