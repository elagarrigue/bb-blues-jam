package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The setlist mutations of the upcoming jam (`admin-add-song-to-setlist`, D-13): plain repository
 * functions, usable with no UI, so the action registry and the phase 2 assistant can call them.
 * Every write goes through the admin POST path; Apps Script authorizes it, never the local flag.
 */
interface SetlistRepository {
    /**
     * Appends the catalog song [songId] to the setlist of [jamDate] in [key] (the admin's choice,
     * D-08) with the default open lineup (D-18). Publishes a [SetlistAdd.State.Sending] entry while
     * the write runs; on success the song is in the cache from the server's answer and the entry is
     * gone, otherwise the entry turns [SetlistAdd.State.Failed] and nothing is cached.
     *
     * Writes are sent one at a time, in call order. Cancelling the caller never cancels an add it
     * started: the whole add runs in the data layer's scope from its first instruction.
     */
    suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome

    /** The adds in progress or failed, in call order. In memory only: lost with the process. */
    fun observeAdds(): Flow<List<SetlistAdd>>

    /** Removes the failed entry [id]; an entry still sending, or an unknown id, is left alone. */
    fun dismiss(id: Long)
}
