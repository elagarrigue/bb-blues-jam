package com.bbbjam.core.data.setlist.remove

import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The remove-song mutation (`admin-remove-song-from-setlist`, D-13) with its observable entries and dismissal. */
interface SetlistSongRemoveRepository {
    /**
     * Removes the song [songId] from the setlist of [jamDate] (`admin-remove-song-from-setlist`). The
     * server finds the row by song id, deletes it and moves every later song up one position; the
     * catalog is never touched. Publishes a [SetlistRemove.State.Sending] entry while the write runs;
     * on success the cache mirrors the removal and the entry is gone, otherwise the entry turns
     * [SetlistRemove.State.Failed] and the cache is unchanged (except `song_not_in_setlist`, see
     * [RemoveSongOutcome.NotRemoved]).
     *
     * Sent in call order with the adds, one write at a time; cancelling the caller never cancels it.
     */
    suspend fun removeSong(jamDate: LocalDate, songId: SongId): RemoveSongOutcome

    /** The removals in progress or failed, in call order. In memory only: lost with the process. */
    fun observeRemoves(): Flow<List<SetlistRemove>>

    /** Removes a failed removal [id]; a sending entry, an unknown id or an id of another mutation is left alone. */
    fun dismissRemove(id: Long)
}
