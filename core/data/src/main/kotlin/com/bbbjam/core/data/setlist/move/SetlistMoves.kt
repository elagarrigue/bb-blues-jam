package com.bbbjam.core.data.setlist.move

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.setlist.engine.PendingEntries
import com.bbbjam.core.data.setlist.engine.QueuedWrite
import com.bbbjam.core.data.setlist.engine.SetlistMutationQueue
import com.bbbjam.core.data.setlist.engine.SetlistRequestFields
import com.bbbjam.core.data.setlist.engine.displayedTitle
import com.bbbjam.core.data.setlist.engine.enqueue
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonPrimitive

/** Reorder mutation entries, request and confirmed cache mirror. */
internal class SetlistMoves(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
) : SetlistMoveRepository {
    private val entries = PendingEntries<SetlistMove>({ it.id }) { it.state is SetlistMove.State.Failed }

    override fun observeMoves(): Flow<List<SetlistMove>> = entries.observe()

    override suspend fun moveSong(jamDate: LocalDate, songId: SongId, toPosition: Int): MoveSongOutcome =
        queue.enqueue(scope) { id ->
            val entry = start(id, jamDate, songId, toPosition)
            QueuedWrite.Send { send(entry) }
        }

    private suspend fun start(id: Long, jamDate: LocalDate, songId: SongId, toPosition: Int): SetlistMove {
        val title = displayedTitle(catalogDao, setlistDao, jamDate, songId)
        val state = if (toPosition < 1) {
            SetlistMove.State.Failed(WriteOutcome.Rejected("invalid_position"))
        } else {
            SetlistMove.State.Sending
        }
        val entry = SetlistMove(id, jamDate, songId, title, toPosition, state)
        entries.add(entry)
        return entry
    }

    private suspend fun send(entry: SetlistMove): MoveSongOutcome {
        if (entry.state is SetlistMove.State.Failed) return MoveSongOutcome.NotMoved(entry.state.reason)
        val result = writer.write(
            MOVE_SONG,
            mapOf(
                SetlistRequestFields.DATE to JsonPrimitive(entry.jamDate.toString()),
                SetlistRequestFields.SONG_ID to JsonPrimitive(entry.songId.value),
                TO_POSITION to JsonPrimitive(entry.toPosition),
            ),
        )
        return if (result == WriteOutcome.Done) {
            try {
                setlistDao.moveSetlistSong(entry.jamDate.toString(), entry.songId.value, entry.toPosition)
            } catch (ignored: SQLException) {
                // The Sheet is authoritative; the next refresh restores the local copy.
            }
            entries.remove(entry.id)
            MoveSongOutcome.Moved
        } else {
            entries.fail(entry.id) { it.copy(state = SetlistMove.State.Failed(result)) }
            MoveSongOutcome.NotMoved(result)
        }
    }

    override fun dismissMove(id: Long) = entries.dismiss(id)

    private companion object {
        const val MOVE_SONG = "moveSong"
        const val TO_POSITION = "toPosition"
    }
}
