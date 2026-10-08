package com.bbbjam.core.data.cache

import androidx.room.Query

/** Narrow cache operations for a confirmed one-slot assignment. */
internal interface SetlistAssignmentDao {
    @Query("SELECT * FROM jam_slot WHERE jam_date = :date AND position = :position ORDER BY column_index")
    suspend fun slotsOf(date: String, position: Int): List<JamSlotEntity>

    /** Changes precisely one cached open cell, only while its song is uniquely present and editable. */
    @Query(
        """
        UPDATE jam_slot SET musician_name = :name
        WHERE jam_date = :date AND position = :position AND column_index = :columnIndex
          AND musician_name IS NULL
          AND (SELECT setlist_state FROM jam WHERE date = :date) = 'AVAILABLE'
          AND (SELECT COUNT(*) FROM jam_song WHERE jam_date = :date AND song_id = :songId) = 1
          AND EXISTS (SELECT 1 FROM jam_song WHERE jam_date = :date AND position = :position AND song_id = :songId)
        """,
    )
    suspend fun assignOpenSlot(date: String, songId: String, position: Int, columnIndex: Int, name: String): Int
}
