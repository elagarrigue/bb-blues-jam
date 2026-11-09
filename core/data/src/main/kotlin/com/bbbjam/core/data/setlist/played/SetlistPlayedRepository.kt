package com.bbbjam.core.data.setlist.played

import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Marking a song of the upcoming jam as already played, and back (`song-played-state`, D-31, D-13). */
interface SetlistPlayedRepository {
    /**
     * Sets the `tocado` mark of the song [songId] in the setlist of [jamDate] through the admin POST path.
     * A played song is read-only for the server (`song_played`), except for its position. Publishes a
     * [PlayedChange.State.Sending] entry while the write runs; on success the cache holds the new value and
     * only then is the entry removed, otherwise the entry turns failed (or outdated) and the cache is
     * unchanged. Queued in call order with the other mutations; cancelling the caller never cancels it.
     */
    suspend fun setSongPlayed(jamDate: LocalDate, songId: SongId, played: Boolean): SetSongPlayedOutcome

    /** The played changes in progress or failed, in call order. In memory only. */
    fun observePlayedChanges(): Flow<List<PlayedChange>>

    /** Removes a failed or outdated played change [id]; a sending entry or an id of another mutation is left alone. */
    fun dismissPlayedChange(id: Long)
}
