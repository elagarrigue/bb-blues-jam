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
internal interface SetlistDao :
    SetlistSlotDao,
    SetlistAssignmentDao {
    @Query("SELECT setlist_state FROM jam WHERE date = :date")
    override suspend fun setlistState(date: String): String?

    /** -1 when a song already holds the position. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongIfAbsent(song: JamSongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    override suspend fun insertSlotsIfAbsent(slots: List<JamSlotEntity>)

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

    /**
     * Mirrors one key change the server confirmed (`setKey`, `admin-set-key`): sets `key` of the song
     * [songId] of [date], and nothing else. Only while the cached setlist is available and exactly one
     * cached song of [date] has that id; otherwise nothing changes and the next refresh brings the
     * Sheet's state. One statement, so the conditions and the update are atomic. The catalog's
     * default key is never touched (D-08). Returns the rows changed: 1 when updated, else 0.
     */
    @Query(
        """
        UPDATE jam_song SET key = :key
        WHERE jam_date = :date AND song_id = :songId
          AND (SELECT setlist_state FROM jam WHERE date = :date) = '$SETLIST_AVAILABLE'
          AND (SELECT COUNT(*) FROM jam_song WHERE jam_date = :date AND song_id = :songId) = 1
        """,
    )
    suspend fun updateKey(date: String, songId: String, key: String): Int
}

/** What an IGNORE insert returns when the row already existed. */
private const val NOT_INSERTED = -1L
