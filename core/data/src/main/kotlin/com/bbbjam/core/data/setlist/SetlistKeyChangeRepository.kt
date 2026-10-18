package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The set-key mutation (`admin-set-key`, D-13) with its observable entries and dismissal. */
interface SetlistKeyChangeRepository {
    /**
     * Sets the key of the song [songId] in the setlist of [jamDate] to [key], the admin's choice
     * (`admin-set-key`, D-08). The server finds the row by song id and writes only its `tono` cell;
     * the catalog, and its default key, is never read or written. Publishes a
     * [KeyChange.State.Sending] entry while the write runs; on success the cache holds the new key
     * and only then is the entry removed, otherwise the entry turns [KeyChange.State.Failed] and the
     * cache is unchanged.
     *
     * Sent in call order with the adds and removals, one write at a time; cancelling the caller never
     * cancels it.
     */
    suspend fun setKey(jamDate: LocalDate, songId: SongId, key: Key): SetKeyOutcome

    /** The key changes in progress or failed, in call order. In memory only: lost with the process. */
    fun observeKeyChanges(): Flow<List<KeyChange>>

    /** Removes a failed key change [id]; a sending entry, an unknown id or an id of another mutation is left alone. */
    fun dismissKeyChange(id: Long)
}
