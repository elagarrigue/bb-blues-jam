package com.bbbjam.core.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Mirrors the one jam the app creates (`assistant-create-jam`, D-25). The jam is a draft whose tab
 * does not exist yet, which the admin read maps to an available, empty setlist.
 */
@Dao
internal interface SetlistJamDao {
    @Query("SELECT COALESCE(MAX(sheet_order), 0) FROM jam")
    suspend fun maxSheetOrder(): Int

    /** Mirrors a confirmed DRAFT or CREATED status of a cached jam; 0 when it is not cached. */
    @Query("UPDATE jam SET status = :status WHERE date = :date")
    suspend fun updateJamStatus(date: String, status: String): Int

    /** -1 when a jam already has the date (a refresh that brought the row first wins). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertJamIfAbsent(jam: JamEntity): Long

    /** One transaction: the draft goes after every cached jam. True when inserted. */
    @Transaction
    suspend fun insertCreatedJam(date: String, startTime: String, venue: String): Boolean = insertJamIfAbsent(
        JamEntity(date, maxSheetOrder() + 1, startTime, venue, "DRAFT", SETLIST_AVAILABLE, null, 0),
    ) != -1L
}
