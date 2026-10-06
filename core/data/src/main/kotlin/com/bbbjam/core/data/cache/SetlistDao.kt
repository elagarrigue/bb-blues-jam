package com.bbbjam.core.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * The setlist mutations' writes to the jam tables (`admin-add-song-to-setlist`). Separate from
 * [JamsDao], which replaces the jams from a read: these add single rows the server confirmed, and
 * never replace or delete anything.
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
}

/** What an IGNORE insert returns when the row already existed. */
private const val NOT_INSERTED = -1L
