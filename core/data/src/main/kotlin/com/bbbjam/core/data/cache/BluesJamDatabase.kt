package com.bbbjam.core.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The on-device cache. Every table is a re-fetchable copy of Sheet data, so a schema change simply
 * drops the tables and refetches: no exported schema and no migrations. Version 2 added the jam
 * tables and the `jam_song_resolved` view (`jams-repository-cache`); upgrading from 1 drops the
 * cached catalog too, which the next start refetches. [SetlistDao] (`admin-add-song-to-setlist`)
 * writes to the same tables, so it changed nothing in the schema.
 */
@Database(
    entities = [
        CatalogSongEntity::class,
        SyncStateEntity::class,
        JamEntity::class,
        JamSongEntity::class,
        JamSlotEntity::class,
        JamExtraEntity::class,
    ],
    views = [JamSongResolved::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
internal abstract class BluesJamDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    abstract fun jamsDao(): JamsDao

    abstract fun setlistDao(): SetlistDao

    companion object {
        const val FILE_NAME = "bluesjam-cache.db"

        fun create(context: Context): BluesJamDatabase =
            Room.databaseBuilder(context, BluesJamDatabase::class.java, FILE_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
