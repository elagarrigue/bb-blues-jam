package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonPrimitive

/**
 * The key change half of [DefaultSetlistRepository] (`admin-set-key`): its entries, its request and
 * its cache mirror. Ordering, ids and the scope stay in the repository, which calls [start] under
 * its order lock and [send] under its write lock.
 */
internal class SetlistKeyChanges(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
) {
    private val entries = MutableStateFlow<List<KeyChange>>(emptyList())

    fun observe(): Flow<List<KeyChange>> = entries.asStateFlow()

    /** Publishes a [KeyChange.State.Sending] entry [id] with the song's displayed title. */
    suspend fun start(id: Long, jamDate: LocalDate, songId: SongId, key: Key): KeyChange {
        val title = displayedTitle(catalogDao, setlistDao, jamDate, songId)
        val entry = KeyChange(id, jamDate, songId, title, key, KeyChange.State.Sending)
        entries.update { it + entry }
        return entry
    }

    /**
     * Sends `setKey` for [entry]. On success the cache is updated **first** and the entry removed
     * after, so the confirmed key is in place before the pending one goes. Every other answer leaves
     * the cache as it was and turns the entry failed, which is the revert.
     */
    suspend fun send(entry: KeyChange): SetKeyOutcome {
        val fields = mapOf(
            DATE to JsonPrimitive(entry.jamDate.toString()),
            SONG_ID to JsonPrimitive(entry.songId.value),
            KEY to JsonPrimitive(entry.key.value),
        )
        val outcome = writer.write(SET_KEY, fields)
        return if (outcome == WriteOutcome.Done) {
            mirror(entry)
            entries.update { list -> list.filterNot { it.id == entry.id } }
            SetKeyOutcome.KeySet
        } else {
            entries.update { list ->
                list.map { if (it.id == entry.id) it.copy(state = KeyChange.State.Failed(outcome)) else it }
            }
            SetKeyOutcome.NotSet(outcome)
        }
    }

    /** Removes the failed entry [id]; a sending entry or an unknown id is left alone. */
    fun dismiss(id: Long) {
        entries.update { list -> list.filterNot { it.id == id && it.state is KeyChange.State.Failed } }
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
        const val DATE = "date"
        const val SONG_ID = "songId"
        const val KEY = "key"
    }
}
