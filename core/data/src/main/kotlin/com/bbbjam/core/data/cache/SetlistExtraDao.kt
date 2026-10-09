package com.bbbjam.core.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal interface SetlistExtraDao : SetlistSlotDao {
    @Query("DELETE FROM jam_extra WHERE jam_date = :date AND position = :position")
    suspend fun deleteExtras(date: String, position: Int)

    @Insert
    suspend fun insertExtras(extras: List<JamExtraEntity>)

    @Transaction
    suspend fun replaceExtras(date: String, songId: String, extras: List<JamExtraEntity>): Boolean {
        val songs = if (setlistState(date) == SETLIST_AVAILABLE) songsOf(date) else emptyList()
        val song = songs.singleOrNull { it.songId == songId } ?: return false
        deleteExtras(date, song.position)
        insertExtras(
            extras.mapIndexed { index, extra ->
                extra.copy(
                    jamDate = date,
                    position = song.position,
                    entryOrder =
                        index + 1,
                )
            },
        )
        return true
    }
}
