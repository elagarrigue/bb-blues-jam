package com.bbbjam.core.data.setlist.remove

import android.database.SQLException
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.remote.ServiceCodes
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

/**
 * The removal mutation (`admin-remove-song-from-setlist`): its entries, its request and its cache
 * mirror, queued on the shared [SetlistMutationQueue].
 */
internal class SetlistRemovals(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
) : SetlistSongRemoveRepository {
    private val entries = PendingEntries<SetlistRemove>({ it.id }) { it.state is SetlistRemove.State.Failed }

    /**
     * Runs in [scope] from the first instruction, like the add, and queues behind earlier writes of
     * either kind: the entry is published under the order lock and the request sent under the write
     * lock.
     */
    override suspend fun removeSong(jamDate: LocalDate, songId: SongId): RemoveSongOutcome =
        queue.enqueue(scope) { id ->
            val entry = start(id, jamDate, songId)
            QueuedWrite.Send { send(entry) }
        }

    override fun observeRemoves(): Flow<List<SetlistRemove>> = entries.observe()

    override fun dismissRemove(id: Long) = entries.dismiss(id)

    /** Publishes a [SetlistRemove.State.Sending] entry [id] with the song's displayed title. */
    private suspend fun start(id: Long, jamDate: LocalDate, songId: SongId): SetlistRemove {
        val title = displayedTitle(catalogDao, setlistDao, jamDate, songId)
        val entry = SetlistRemove(id, jamDate, songId, title, SetlistRemove.State.Sending)
        entries.add(entry)
        return entry
    }

    /**
     * Sends `removeSong` for [entry]. The cache changes only when the server removed the song, or
     * answered `song_not_in_setlist` (the Sheet no longer has it); every other answer leaves it as
     * it was and turns the entry failed.
     */
    private suspend fun send(entry: SetlistRemove): RemoveSongOutcome {
        val fields = mapOf(
            SetlistRequestFields.DATE to JsonPrimitive(entry.jamDate.toString()),
            SetlistRequestFields.SONG_ID to JsonPrimitive(entry.songId.value),
        )
        val outcome = writer.write(REMOVE_SONG, fields)
        if (outcome == WriteOutcome.Done || outcome == WriteOutcome.Rejected(ServiceCodes.SONG_NOT_IN_SETLIST)) {
            mirror(entry)
        }
        return if (outcome == WriteOutcome.Done) {
            entries.remove(entry.id)
            RemoveSongOutcome.Removed
        } else {
            entries.fail(entry.id) { it.copy(state = SetlistRemove.State.Failed(outcome)) }
            RemoveSongOutcome.NotRemoved(outcome)
        }
    }

    /**
     * Mirrors the server's removal in the cache. A cache that refuses it changes nothing about the
     * outcome: the Sheet is the authority, and the next refresh fixes the local copy.
     */
    private suspend fun mirror(entry: SetlistRemove) {
        try {
            setlistDao.removeSetlistSong(entry.jamDate.toString(), entry.songId.value)
        } catch (ignored: SQLException) {
            // The Sheet changed; only the local copy waits for the next refresh.
        }
    }

    private companion object {
        const val REMOVE_SONG = "removeSong"
    }
}
