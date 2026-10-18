package com.bbbjam.core.data.setlist.key

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
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonPrimitive

/**
 * The key change mutation (`admin-set-key`): its entries, its request and its cache mirror, queued
 * on the shared [SetlistMutationQueue].
 */
internal class SetlistKeyChanges(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
    private val scope: DataScope,
    private val queue: SetlistMutationQueue,
) : SetlistKeyChangeRepository {
    private val entries = PendingEntries<KeyChange>({ it.id }) { it.state is KeyChange.State.Failed }

    /**
     * Runs in [scope] from the first instruction, like the add, and queues behind earlier writes of
     * any kind: the entry is published under the order lock and the request sent under the write
     * lock.
     */
    override suspend fun setKey(jamDate: LocalDate, songId: SongId, key: Key): SetKeyOutcome =
        queue.enqueue(scope) { id ->
            val entry = start(id, jamDate, songId, key)
            QueuedWrite.Send { send(entry) }
        }

    override fun observeKeyChanges(): Flow<List<KeyChange>> = entries.observe()

    override fun dismissKeyChange(id: Long) = entries.dismiss(id)

    /** Publishes a [KeyChange.State.Sending] entry [id] with the song's displayed title. */
    private suspend fun start(id: Long, jamDate: LocalDate, songId: SongId, key: Key): KeyChange {
        val title = displayedTitle(catalogDao, setlistDao, jamDate, songId)
        val entry = KeyChange(id, jamDate, songId, title, key, KeyChange.State.Sending)
        entries.add(entry)
        return entry
    }

    /**
     * Sends `setKey` for [entry]. On success the cache is updated **first** and the entry removed
     * after, so the confirmed key is in place before the pending one goes. Every other answer leaves
     * the cache as it was and turns the entry failed, which is the revert.
     */
    private suspend fun send(entry: KeyChange): SetKeyOutcome {
        val fields = mapOf(
            SetlistRequestFields.DATE to JsonPrimitive(entry.jamDate.toString()),
            SetlistRequestFields.SONG_ID to JsonPrimitive(entry.songId.value),
            SetlistRequestFields.KEY to JsonPrimitive(entry.key.value),
        )
        val outcome = writer.write(SET_KEY, fields)
        return if (outcome == WriteOutcome.Done) {
            mirror(entry)
            entries.remove(entry.id)
            SetKeyOutcome.KeySet
        } else {
            entries.fail(entry.id) { it.copy(state = KeyChange.State.Failed(outcome)) }
            SetKeyOutcome.NotSet(outcome)
        }
    }

    /**
     * Mirrors the server's key change in the cache. A cache that refuses it changes nothing about the
     * outcome: the Sheet is the authority, and the next refresh fixes the local copy.
     */
    private suspend fun mirror(entry: KeyChange) {
        try {
            setlistDao.updateKey(entry.jamDate.toString(), entry.songId.value, entry.key.value)
        } catch (ignored: SQLException) {
            // The Sheet changed; only the local copy waits for the next refresh.
        }
    }

    private companion object {
        const val SET_KEY = "setKey"
    }
}
