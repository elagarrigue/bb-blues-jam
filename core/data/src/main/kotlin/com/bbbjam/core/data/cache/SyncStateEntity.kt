package com.bbbjam.core.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The freshness of one cached resource (`"catalog"` or `"jams"`). Times are epoch
 * milliseconds. [failure] is the encoded [com.bbbjam.core.data.DataFailure] of the latest attempt,
 * or null when it succeeded.
 */
@Entity(tableName = "sync_state")
internal data class SyncStateEntity(
    @PrimaryKey val resource: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long?,
    @ColumnInfo(name = "attempted_at") val attemptedAt: Long?,
    val failure: String?,
)
