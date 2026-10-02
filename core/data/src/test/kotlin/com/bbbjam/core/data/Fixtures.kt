package com.bbbjam.core.data

import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.bbbjam.core.data.cache.BluesJamDatabase
import com.bbbjam.core.data.remote.AppsScriptEnvelope
import com.bbbjam.core.data.remote.Decoded
import com.bbbjam.core.data.remote.SongDto
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Repository files the tests read: the contract samples and the seed are the fixtures. */
internal object Fixtures {
    private val rootDir: File by lazy {
        File(requireNotNull(System.getProperty("bbbjam.rootDir")) { "bbbjam.rootDir is not set" })
    }

    fun repoFile(path: String): File = File(rootDir, path).also { require(it.isFile) { "missing fixture $path" } }

    fun sample(name: String): String = repoFile("docs/api-samples/$name").readText(Charsets.UTF_8)

    /** The songs of a catalog sample, through the real envelope decoder. */
    fun sampleSongs(name: String): List<SongDto> {
        val decoded = AppsScriptEnvelope.decode(sample(name), "songs", SongDto.serializer())
        return (decoded as Decoded.Ok).value
    }

    /** An in-memory cache on the bundled SQLite driver: Room on the JVM, no Robolectric. */
    fun inMemoryDatabase(): BluesJamDatabase =
        Room.inMemoryDatabaseBuilder(ContextWrapper(null), BluesJamDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()

    /** A body in the contract's envelope holding [songsJson]. */
    fun catalogBody(songsJson: String): String = """{"schemaVersion":1,"songs":$songsJson}"""
}

/** A clock the test moves by hand. */
internal class MutableClock(var instant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = instant
}
