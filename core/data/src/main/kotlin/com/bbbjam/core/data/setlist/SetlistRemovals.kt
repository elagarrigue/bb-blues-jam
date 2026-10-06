package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonPrimitive

/**
 * The removal half of [DefaultSetlistRepository] (`admin-remove-song-from-setlist`): its entries,
 * its request and its cache mirror. Ordering, ids and the scope stay in the repository, which calls
 * [start] under its order lock and [send] under its write lock.
 */
internal class SetlistRemovals(
    private val writer: AdminWriter,
    private val setlistDao: SetlistDao,
    private val catalogDao: CatalogDao,
) {
    private val entries = MutableStateFlow<List<SetlistRemove>>(emptyList())

    fun observe(): Flow<List<SetlistRemove>> = entries.asStateFlow()

    /** Publishes a [SetlistRemove.State.Sending] entry [id] with the song's displayed title. */
    suspend fun start(id: Long, jamDate: LocalDate, songId: SongId): SetlistRemove {
        val title = displayedTitle(catalogDao, setlistDao, jamDate, songId)
        val entry = SetlistRemove(id, jamDate, songId, title, SetlistRemove.State.Sending)
        entries.update { it + entry }
        return entry
    }

    /**
     * Sends `removeSong` for [entry]. The cache changes only when the server removed the song, or
     * answered `song_not_in_setlist` (the Sheet no longer has it); every other answer leaves it as
     * it was and turns the entry failed.
     */
    suspend fun send(entry: SetlistRemove): RemoveSongOutcome {
        val fields = mapOf(
            DATE to JsonPrimitive(entry.jamDate.toString()),
            SONG_ID to JsonPrimitive(entry.songId.value),
        )
        val outcome = writer.write(REMOVE_SONG, fields)
        if (outcome == WriteOutcome.Done || outcome == WriteOutcome.Rejected(SONG_NOT_IN_SETLIST)) {
            mirror(entry)
        }
        return if (outcome == WriteOutcome.Done) {
            entries.update { list -> list.filterNot { it.id == entry.id } }
            RemoveSongOutcome.Removed
        } else {
            entries.update { list ->
                list.map { if (it.id == entry.id) it.copy(state = SetlistRemove.State.Failed(outcome)) else it }
            }
            RemoveSongOutcome.NotRemoved(outcome)
        }
    }

    /** Removes the failed entry [id]; a sending entry or an unknown id is left alone. */
    fun dismiss(id: Long) {
        entries.update { list -> list.filterNot { it.id == id && it.state is SetlistRemove.State.Failed } }
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
        const val SONG_NOT_IN_SETLIST = "song_not_in_setlist"
        const val DATE = "date"
        const val SONG_ID = "songId"
    }
}
