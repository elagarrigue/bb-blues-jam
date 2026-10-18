package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The add-song mutation (`admin-add-song-to-setlist`, D-13) with its observable entries and dismissal. */
interface SetlistSongAddRepository {
    /**
     * Appends the catalog song [songId] to the setlist of [jamDate] in [key] (the admin's choice,
     * D-08) with the default open lineup (D-18). Publishes a [SetlistAdd.State.Sending] entry while
     * the write runs; on success the song is in the cache from the server's answer and the entry is
     * gone, otherwise the entry turns [SetlistAdd.State.Failed] and nothing is cached.
     *
     * [jamDate] is any future jam: the upcoming one or a held-back one (D-29, `assistant-proposal-targets`);
     * every other setlist mutation stays upcoming-only. The cache is mirrored into that jam only.
     *
     * Writes are sent one at a time, in call order. Cancelling the caller never cancels an add it
     * started: the whole add runs in the data layer's scope from its first instruction.
     */
    suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome

    /** The adds in progress or failed, in call order. In memory only: lost with the process. */
    fun observeAdds(): Flow<List<SetlistAdd>>

    /** Removes a failed add [id]; a sending entry, an unknown id or an id of another mutation is left alone. */
    fun dismissAdd(id: Long)
}
