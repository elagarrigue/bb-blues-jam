package com.bbbjam.core.data

import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.bbbjam.core.data.cache.BluesJamDatabase
import com.bbbjam.core.data.remote.AppsScriptEnvelope
import com.bbbjam.core.data.remote.Decoded
import com.bbbjam.core.data.remote.JamDto
import com.bbbjam.core.data.remote.SetlistRowDto
import com.bbbjam.core.data.remote.SlotsDto
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

    /** A body in the contract's envelope holding [jamsJson]. */
    fun jamsBody(jamsJson: String): String = """{"schemaVersion":1,"jams":$jamsJson}"""

    /** The jams of a jams sample, through the real envelope decoder. */
    fun sampleJams(name: String): List<JamDto> {
        val decoded = AppsScriptEnvelope.decode(sample(name), "jams", JamDto.serializer())
        return (decoded as Decoded.Ok).value
    }

    /**
     * The seed's `Jams.csv` and its jam tabs as the jams route would serve them: cells by header,
     * trimmed, empty as null, and only a `PUBLICADA` jam's tab read (the draft rule). The seed has
     * no quoted cells; a `"` fails rather than being parsed wrongly.
     */
    fun seedJams(): List<JamDto> = seedCsv("Jams.csv").map { jam ->
        val date = jam["fecha"]
        val setlist = if (jam["estado"] == "PUBLICADA") seedCsv("$date.csv").map(::seedSetlistRow) else null
        JamDto(date, jam["hora"], jam["lugar"], jam["estado"], setlist, setlistError = null)
    }

    private fun seedSetlistRow(row: Map<String, String?>) = SetlistRowDto(
        position = row["posicion"],
        songId = row["id_tema"],
        title = row["titulo"],
        artist = row["artista"],
        key = row["tono"],
        slots = SlotsDto(
            guitar1 = row["Guitarra 1"],
            guitar2 = row["Guitarra 2"],
            bass = row["Bajo"],
            drums = row["Batería"],
            vocals = row["Voz"],
            harmonica = row["Armónica"],
            keyboards = row["Teclados"],
        ),
        extraParticipants = row["Otros"],
    )

    private fun seedCsv(name: String): List<Map<String, String?>> {
        val lines = repoFile("docs/sheet-seed/$name").readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        require(lines.none { '"' in it }) { "$name has a quoted cell; this reader does not parse quotes" }
        val headers = lines.first().split(',').map { it.trim() }
        return lines.drop(1).map { line ->
            val cells = line.split(',').map { cell -> cell.trim().takeIf { it.isNotEmpty() } }
            require(cells.size == headers.size) { "$name: ${cells.size} cells for ${headers.size} headers" }
            headers.zip(cells).toMap()
        }
    }
}

/** A clock the test moves by hand. */
internal class MutableClock(var instant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = instant
}
