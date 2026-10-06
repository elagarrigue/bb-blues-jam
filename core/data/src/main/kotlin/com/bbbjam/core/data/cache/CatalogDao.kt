package com.bbbjam.core.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** The catalog tables. Writes are transactional, so a reader never sees a partial catalog. */
@Dao
internal interface CatalogDao {
    @Query("SELECT * FROM catalog_song ORDER BY sheet_order")
    fun observeSongs(): Flow<List<CatalogSongEntity>>

    /** The cached song with [id], or null. */
    @Query("SELECT * FROM catalog_song WHERE id = :id")
    suspend fun song(id: String): CatalogSongEntity?

    @Query("SELECT * FROM sync_state WHERE resource = :resource")
    fun observeSyncState(resource: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE resource = :resource")
    suspend fun syncState(resource: String): SyncStateEntity?

    @Query("DELETE FROM catalog_song")
    suspend fun deleteSongs()

    @Insert
    suspend fun insertSongs(songs: List<CatalogSongEntity>)

    /**
     * One row per resource, replaced whole. Not `@Upsert`: Room's upsert recognizes the conflict by
     * the exception message, which the stub `android.database.SQLException` of JVM tests drops.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: SyncStateEntity)

    /** Replaces every song and the sync state in one transaction (the Sheet is the authority, D-04). */
    @Transaction
    suspend fun replaceCatalog(songs: List<CatalogSongEntity>, state: SyncStateEntity) {
        deleteSongs()
        insertSongs(songs)
        upsertSyncState(state)
    }

    /** Records a failed attempt; the songs and the last success time are untouched. */
    @Transaction
    suspend fun recordFailure(resource: String, attemptedAt: Long, failure: String) {
        val previous = syncState(resource)
        upsertSyncState(SyncStateEntity(resource, previous?.fetchedAt, attemptedAt, failure))
    }
}
