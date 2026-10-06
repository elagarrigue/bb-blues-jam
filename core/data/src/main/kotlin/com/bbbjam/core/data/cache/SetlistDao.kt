package com.bbbjam.core.data.cache

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * The setlist mutations' writes to the jam tables (`admin-add-song-to-setlist`,
 * `admin-remove-song-from-setlist`). Separate from [JamsDao], which replaces the jams from a read:
 * these mirror one change the server confirmed, a song added or a song removed, and never replace
 * the jams.
 */
@Dao
internal interface SetlistDao {
    @Query("SELECT setlist_state FROM jam WHERE date = :date")
    suspend fun setlistState(date: String): String?

    /** -1 when a song already holds the position. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongIfAbsent(song: JamSongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSlotsIfAbsent(slots: List<JamSlotEntity>)

    /**
     * Adds one song the server just appended, with its slots, in one transaction, and only while the
     * cached jam's setlist is available. A song already at that position wins (a refresh that
     * brought the row first), and so does a jam that is not cached or not available: the next
     * refresh brings the row then. True when the song was inserted.
     */
    @Transaction
    suspend fun insertSetlistSong(song: JamSongEntity, slots: List<JamSlotEntity>): Boolean {
        val inserted = setlistState(song.jamDate) == SETLIST_AVAILABLE && insertSongIfAbsent(song) != NOT_INSERTED
        if (inserted) insertSlotsIfAbsent(slots)
        return inserted
    }

    /** The cached songs of [date] with the tab's title copies, in position order. */
    @Query("SELECT * FROM jam_song WHERE jam_date = :date ORDER BY position")
    suspend fun songsOf(date: String): List<JamSongEntity>

    @Query("SELECT * FROM jam_slot WHERE jam_date = :date AND position >= :position")
    suspend fun slotsFrom(date: String, position: Int): List<JamSlotEntity>

    @Query("SELECT * FROM jam_extra WHERE jam_date = :date AND position >= :position")
    suspend fun extrasFrom(date: String, position: Int): List<JamExtraEntity>

    /** Deletes the given rows explicitly, children first, so nothing depends on the cascade. */
    @Delete
    suspend fun deleteRows(slots: List<JamSlotEntity>, extras: List<JamExtraEntity>, songs: List<JamSongEntity>)

    /** Inserts the given rows, parents first. */
    @Insert
    suspend fun insertRows(songs: List<JamSongEntity>, slots: List<JamSlotEntity>, extras: List<JamExtraEntity>)

    /**
     * Mirrors one removal the server confirmed (`removeSong`), in one transaction: the song [songId]
     * of [date] is deleted with its slots and extras, and every song after it moves up one position
     * with its own slots and extras, as the server renumbered the tab. Positions are part of the
     * primary keys and the foreign keys have no `onUpdate`, so moving a row is a delete and a
     * reinsert. Only while the cached setlist is available and exactly one cached song has that id;
     * otherwise nothing changes and the next refresh brings the Sheet's state. True when removed.
     */
    @Transaction
    suspend fun removeSetlistSong(date: String, songId: String): Boolean {
        val songs = if (setlistState(date) == SETLIST_AVAILABLE) songsOf(date) else emptyList()
        val removed = songs.singleOrNull { it.songId == songId } ?: return false
        val from = removed.position
        val slots = slotsFrom(date, from)
        val extras = extrasFrom(date, from)
        val moved = songs.filter { it.position >= from }
        deleteRows(slots, extras, moved)
        insertRows(
            moved.filter { it.position > from }.map { it.copy(position = it.position - 1) },
            slots.filter { it.position > from }.map { it.copy(position = it.position - 1) },
            extras.filter { it.position > from }.map { it.copy(position = it.position - 1) },
        )
        return true
    }
}

/** What an IGNORE insert returns when the row already existed. */
private const val NOT_INSERTED = -1L
