package com.bbbjam.core.data.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Mirrors a confirmed edit or deletion of a future jam (`admin-edit-delete-jam`, D-28). Nothing here runs
 * before the server answered; the Sheet is authoritative and the next refresh repairs any difference.
 */
@Dao
internal interface SetlistJamEditDao {
    @Query("SELECT COUNT(*) FROM jam WHERE date = :date")
    suspend fun jamCount(date: String): Int

    /** The date stays: only the time and the venue change. 0 when the jam is not cached. */
    @Query("UPDATE jam SET start_time = :startTime, venue = :venue WHERE date = :date")
    suspend fun updateJamDetails(date: String, startTime: String, venue: String): Int

    @Query(
        """
        INSERT INTO jam (date, sheet_order, start_time, venue, status, setlist_state, setlist_problem, dropped_rows)
        SELECT :newDate, sheet_order, :startTime, :venue, status, setlist_state, setlist_problem, dropped_rows
        FROM jam WHERE date = :oldDate
        """,
    )
    suspend fun copyJam(oldDate: String, newDate: String, startTime: String, venue: String)

    @Query(
        """
        INSERT INTO jam_song (jam_date, position, song_id, title_copy, artist_copy, key)
        SELECT :newDate, position, song_id, title_copy, artist_copy, key FROM jam_song WHERE jam_date = :oldDate
        """,
    )
    suspend fun copySongs(oldDate: String, newDate: String)

    @Query(
        """
        INSERT INTO jam_slot (jam_date, position, column_index, instrument, musician_name)
        SELECT :newDate, position, column_index, instrument, musician_name FROM jam_slot WHERE jam_date = :oldDate
        """,
    )
    suspend fun copySlots(oldDate: String, newDate: String)

    @Query(
        """
        INSERT INTO jam_extra (jam_date, position, entry_order, name, instrument)
        SELECT :newDate, position, entry_order, name, instrument FROM jam_extra WHERE jam_date = :oldDate
        """,
    )
    suspend fun copyExtras(oldDate: String, newDate: String)

    /** Deletes the jam; its songs, slots and extras go with it (the foreign keys cascade). */
    @Query("DELETE FROM jam WHERE date = :date")
    suspend fun deleteJamRow(date: String): Int

    /**
     * Moves the cached jam [oldDate] to [newDate] with its time and venue, keeping its sheet order, status,
     * setlist and lineups, in one transaction. Nothing happens when the old jam is not cached or the new
     * date already is (the next refresh repairs it). True when moved.
     */
    @Transaction
    suspend fun moveJam(oldDate: String, newDate: String, startTime: String, venue: String): Boolean {
        if (jamCount(oldDate) == 0 || jamCount(newDate) > 0) return false
        copyJam(oldDate, newDate, startTime, venue)
        copySongs(oldDate, newDate)
        copySlots(oldDate, newDate)
        copyExtras(oldDate, newDate)
        deleteJamRow(oldDate)
        return true
    }
}
