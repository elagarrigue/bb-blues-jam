package com.bbbjam.core.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * The mirror of a confirmed catalog write (`admin-catalog-tab`). Separate from [CatalogDao], which
 * replaces the whole catalog. Only the three written columns ever change; the Sheet-only columns stay.
 */
@Dao
internal interface CatalogEditDao {
    @Query("SELECT COALESCE(MAX(sheet_order), 0) FROM catalog_song")
    suspend fun maxSheetOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: CatalogSongEntity)

    /** Inserts [song] after the last cached row (`sheet_order` = max + 1), whatever order it carries. */
    @Transaction
    suspend fun insertCreated(song: CatalogSongEntity) {
        insert(song.copy(sheetOrder = maxSheetOrder() + 1))
    }

    /** Sets title, artist and default key of [id]; returns the rows changed (0 when it is not cached). */
    @Query("UPDATE catalog_song SET title = :title, artist = :artist, default_key = :defaultKey WHERE id = :id")
    suspend fun updateFields(id: String, title: String, artist: String, defaultKey: String): Int

    @Query("DELETE FROM catalog_song WHERE id = :id")
    suspend fun delete(id: String): Int
}
