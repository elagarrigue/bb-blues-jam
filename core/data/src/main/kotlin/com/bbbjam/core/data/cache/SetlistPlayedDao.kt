package com.bbbjam.core.data.cache

import androidx.room.Dao
import androidx.room.Query

/** Mirrors a played mark the server confirmed (`song-played-state`, D-31). */
@Dao
internal interface SetlistPlayedDao {
    /**
     * Sets `played` of the song [songId] of [date], and nothing else. Only while the cached setlist is
     * available and exactly one cached song of [date] has that id; otherwise nothing changes and the
     * next refresh brings the Sheet's state. One statement, so the conditions and the update are atomic.
     * Returns the rows changed: 1 when updated, else 0.
     */
    @Query(
        """
        UPDATE jam_song SET played = :played
        WHERE jam_date = :date AND song_id = :songId
          AND (SELECT setlist_state FROM jam WHERE date = :date) = '$SETLIST_AVAILABLE'
          AND (SELECT COUNT(*) FROM jam_song WHERE jam_date = :date AND song_id = :songId) = 1
        """,
    )
    suspend fun updatePlayed(date: String, songId: String, played: Boolean): Int
}
