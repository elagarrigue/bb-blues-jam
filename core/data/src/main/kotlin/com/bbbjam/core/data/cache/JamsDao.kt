package com.bbbjam.core.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Every row of one jams replace, inserted parents first. */
internal data class JamRows(
    val jams: List<JamEntity>,
    val songs: List<JamSongEntity>,
    val slots: List<JamSlotEntity>,
    val extras: List<JamExtraEntity>,
)

/**
 * The jam tables. Writes are transactional, so a reader never sees a partial setlist. The
 * `sync_state` queries repeat [CatalogDao]'s for the `"jams"` row.
 */
@Dao
internal interface JamsDao {
    /** Every jam with its songs (titles resolved against the catalog), slots and extras, in Sheet order. */
    @Transaction
    @Query("SELECT * FROM jam ORDER BY sheet_order")
    fun observeJams(): Flow<List<JamWithChildren>>

    @Query("SELECT * FROM sync_state WHERE resource = :resource")
    fun observeSyncState(resource: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE resource = :resource")
    suspend fun syncState(resource: String): SyncStateEntity?

    /** Deletes every jam; the foreign keys cascade to songs, slots and extras. */
    @Query("DELETE FROM jam")
    suspend fun deleteJams()

    @Insert
    suspend fun insertJams(jams: List<JamEntity>)

    @Insert
    suspend fun insertSongs(songs: List<JamSongEntity>)

    @Insert
    suspend fun insertSlots(slots: List<JamSlotEntity>)

    @Insert
    suspend fun insertExtras(extras: List<JamExtraEntity>)

    /** One row per resource, replaced whole (see [CatalogDao.upsertSyncState] for why not `@Upsert`). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: SyncStateEntity)

    /** Replaces every jam and the sync state in one transaction (the Sheet is the authority, D-04). */
    @Transaction
    suspend fun replaceJams(rows: JamRows, state: SyncStateEntity) {
        deleteJams()
        insertJams(rows.jams)
        insertSongs(rows.songs)
        insertSlots(rows.slots)
        insertExtras(rows.extras)
        upsertSyncState(state)
    }

    /** Records a failed attempt; the jams and the last success time are untouched. */
    @Transaction
    suspend fun recordFailure(resource: String, attemptedAt: Long, failure: String) {
        val previous = syncState(resource)
        upsertSyncState(SyncStateEntity(resource, previous?.fetchedAt, attemptedAt, failure))
    }
}
