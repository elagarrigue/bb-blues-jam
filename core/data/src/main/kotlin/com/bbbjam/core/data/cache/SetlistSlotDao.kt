package com.bbbjam.core.data.cache

import androidx.room.Query
import androidx.room.Transaction

/** Shared inherited DAO primitives keep each mutation contract small. */
internal interface SetlistSlotDao {
    suspend fun setlistState(date: String): String?
    suspend fun songsOf(date: String): List<JamSongEntity>
    suspend fun insertSlotsIfAbsent(slots: List<JamSlotEntity>)

    @Query("DELETE FROM jam_slot WHERE jam_date = :date AND position = :position")
    suspend fun deleteSlots(date: String, position: Int)

    /** Mirrors only the uniquely identified song's server-confirmed slots in one transaction. */
    @Transaction
    suspend fun replaceSlots(date: String, songId: String, slots: List<JamSlotEntity>): Boolean {
        val songs = if (setlistState(date) == SETLIST_AVAILABLE) songsOf(date) else emptyList()
        val song = songs.singleOrNull { it.songId == songId } ?: return false
        deleteSlots(date, song.position)
        insertSlotsIfAbsent(slots.map { it.copy(jamDate = date, position = song.position) })
        return true
    }
}
