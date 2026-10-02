package com.bbbjam.core.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The on-device cache. Every table is a re-fetchable copy of Sheet data, so a schema change simply
 * drops the tables and refetches: no exported schema and no migrations.
 */
@Database(
    entities = [CatalogSongEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
internal abstract class BluesJamDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        const val FILE_NAME = "bluesjam-cache.db"

        fun create(context: Context): BluesJamDatabase =
            Room.databaseBuilder(context, BluesJamDatabase::class.java, FILE_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
