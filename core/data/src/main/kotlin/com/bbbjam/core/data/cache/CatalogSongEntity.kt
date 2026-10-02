package com.bbbjam.core.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One cached catalog song. Holds only Sheet data: enrichment gets its own table keyed by song id,
 * because a catalog replace wipes this one (D-09). [tempo] and [difficulty] hold the enum `name`.
 */
@Entity(tableName = "catalog_song")
internal data class CatalogSongEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "sheet_order") val sheetOrder: Int,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "default_key") val defaultKey: String,
    val tempo: String?,
    val difficulty: String?,
    val tags: List<String>,
    @ColumnInfo(name = "songsterr_id") val songsterrId: Long?,
)
