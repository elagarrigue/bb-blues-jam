package com.bbbjam.core.data.cache

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Shared inherited DAO primitives keep each mutation contract small. */
internal interface SetlistSlotDao {
    suspend fun setlistState(date: String): String?

    @Query("SELECT * FROM jam_song WHERE jam_date = :date ORDER BY position")
    suspend fun songsOf(date: String): List<JamSongEntity>
    suspend fun insertSlotsIfAbsent(slots: List<JamSlotEntity>)

    @Query("SELECT * FROM jam_slot WHERE jam_date = :date AND position >= :position")
    suspend fun slotsFrom(date: String, position: Int): List<JamSlotEntity>

    @Query("SELECT * FROM jam_extra WHERE jam_date = :date AND position >= :position")
    suspend fun extrasFrom(date: String, position: Int): List<JamExtraEntity>

    @Delete
    suspend fun deleteRows(slots: List<JamSlotEntity>, extras: List<JamExtraEntity>, songs: List<JamSongEntity>)

    @Insert
    suspend fun insertRows(songs: List<JamSongEntity>, slots: List<JamSlotEntity>, extras: List<JamExtraEntity>)

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

    /** Reorders confirmed cached songs and their children in one transaction. */
    @Transaction
    suspend fun moveSetlistSong(date: String, songId: String, toPosition: Int): Boolean {
        val songs = if (setlistState(date) == SETLIST_AVAILABLE) songsOf(date) else emptyList()
        val moved = songs.singleOrNull { it.songId == songId }
        if (moved == null || songs.isEmpty()) return false
        val ordered = songs.sortedBy { it.position }.toMutableList()
        ordered.remove(moved)
        ordered.add((toPosition.coerceAtMost(songs.size).coerceAtLeast(1)) - 1, moved)
        val positions = ordered.mapIndexed { index, song -> song.position to index + 1 }
            .filter { (old, target) -> old != target }
            .toMap()
        val firstChangedPosition = positions.keys.minOrNull()
        if (firstChangedPosition != null) {
            val changed = songs.filter { it.position in positions }
            val slots = slotsFrom(date, firstChangedPosition).filter { it.position in positions }
            val extras = extrasFrom(date, firstChangedPosition).filter { it.position in positions }
            deleteRows(slots, extras, changed)
            insertRows(
                changed.map { song -> song.copy(position = positions.getValue(song.position)) },
                slots.map { slot -> slot.copy(position = positions.getValue(slot.position)) },
                extras.map { extra -> extra.copy(position = positions.getValue(extra.position)) },
            )
        }
        return true
    }
}
