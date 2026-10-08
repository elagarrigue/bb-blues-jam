package com.bbbjam.core.data.setlist

import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The setlist mutations of the upcoming jam (`admin-add-song-to-setlist`,
 * `admin-remove-song-from-setlist`, `admin-set-key`, `admin-adjust-lineup`, D-13): plain repository
 * functions, usable with no UI, so the action registry and the phase 2 assistant can call them.
 * Every write goes through the admin POST path; Apps Script authorizes it, never the local flag.
 */
interface SetlistRepository : SetlistSlotClearRepository {
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

    /** Changes only open slot counts, bounded by the default lineup; the server authorizes it. */
    suspend fun setSlotCount(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        count: Int,
    ): SetSlotCountOutcome

    /** Pending and failed lineup writes, sharing the mutation id counter. */
    fun observeLineupChanges(): Flow<List<LineupChange>>

    /** Assigns [musicianName] to one fixed, 1-based instrument slot; the server authorizes it. */
    suspend fun assignSlot(
        jamDate: LocalDate,
        songId: SongId,
        instrument: Instrument,
        ordinal: SlotPosition,
        musicianName: MusicianName,
    ): AssignSlotOutcome

    /** Pending and failed assignments, in call order. */
    fun observeAssignments(): Flow<List<Assignment>>

    /**
     * Removes the failed add, removal, key, lineup change or assignment [id]; a sending entry or unknown id is
     * left alone.
     */
    fun dismiss(id: Long)
}
