package com.bbbjam.core.data.catalog

import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.remote.SongDto
import com.bbbjam.core.model.Difficulty
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import com.bbbjam.core.model.Tempo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogMapperTest {

    @Test
    fun `the seed sample maps to 13 songs with no issue`() {
        val mapped = CatalogMapper.map(Fixtures.sampleSongs("catalog-seed.json"))

        assertEquals(13, mapped.songs.size)
        assertEquals(emptyList<RejectedSong>(), mapped.rejected)
        assertEquals(emptyList<DroppedFields>(), mapped.dropped)
        assertEquals(
            Song(SongId("sweet-little-angel"), "Sweet Little Angel", "B.B. King", Key("B")),
            mapped.songs.first(),
        )
        assertEquals("tres-palabras", mapped.songs.last().id.value)
    }

    @Test
    fun `the seed CSV maps to the same catalog as its JSON sample`() {
        val csv = Fixtures.repoFile("docs/sheet-seed/Catalogo.csv").readText(Charsets.UTF_8)
        assertFalse("Catalogo.csv holds a quote; this reader does not parse CSV quoting", csv.contains('"'))
        val lines = csv.lines().filter { it.isNotBlank() }
        val headers = lines.first().split(',').map { it.trim() }
        val rows = lines.drop(1).map { line ->
            val cells = headers.zip(line.split(',').map { it.trim().ifEmpty { null } }).toMap()
            SongDto(
                id = cells["id"],
                title = cells["titulo"],
                artist = cells["artista"],
                defaultKey = cells["tono_default"],
                tempo = cells["tempo"],
                tags = cells["etiquetas"],
                difficulty = cells["dificultad"],
                songsterrId = cells["songsterr_id"],
            )
        }

        assertEquals(13, rows.size)
        assertEquals(CatalogMapper.map(Fixtures.sampleSongs("catalog-seed.json")), CatalogMapper.map(rows))
    }

    @Test
    fun `the edge sample keeps three songs with their exact values and rejects sin-tono`() {
        val mapped = CatalogMapper.map(Fixtures.sampleSongs("catalog-edge.json"))

        assertEquals(
            listOf(
                Song(
                    id = SongId("the-thrill-is-gone"),
                    title = "The Thrill Is Gone",
                    artist = "B.B. King",
                    defaultKey = Key("Bm"),
                    tempo = Tempo.SLOW,
                    tags = listOf("slow blues", "12 compases"),
                    difficulty = Difficulty.MEDIUM,
                    songsterrId = 12345L,
                ),
                Song(SongId("crossroads"), "Crossroads", "Robert Johnson", Key("A")),
                Song(
                    id = SongId("got-my-mojo-working"),
                    title = "Got My Mojo Working",
                    artist = "Muddy Waters",
                    defaultKey = Key("E"),
                    tempo = Tempo.FAST,
                    tags = listOf("shuffle"),
                    difficulty = Difficulty.EASY,
                    songsterrId = 42L,
                ),
            ),
            mapped.songs,
        )
        assertEquals(
            listOf(
                RejectedSong(
                    index = 4,
                    id = "sin-tono",
                    issues = listOf(SongIssue.MissingDefaultKey, SongIssue.InvalidTempo, SongIssue.InvalidSongsterrId),
                ),
            ),
            mapped.rejected,
        )
        assertEquals(emptyList<DroppedFields>(), mapped.dropped)
    }

    @Test
    fun `an issue in a required field rejects the song and keeps the others`() {
        val cases = listOf(
            row(id = null) to SongIssue.MissingId,
            row(id = "  ") to SongIssue.MissingId,
            row(id = "Blues-Uno") to SongIssue.InvalidId,
            row(id = "blues uno") to SongIssue.InvalidId,
            row(title = null) to SongIssue.MissingTitle,
            row(artist = "") to SongIssue.MissingArtist,
            row(defaultKey = null) to SongIssue.MissingDefaultKey,
            row(defaultKey = "B m") to SongIssue.InvalidDefaultKey,
            row(defaultKey = "Si") to SongIssue.InvalidDefaultKey,
            row(defaultKey = "Bmaj") to SongIssue.InvalidDefaultKey,
        )
        cases.forEach { (bad, issue) ->
            val mapped = CatalogMapper.map(listOf(row(id = "otro-tema"), bad))

            assertEquals("$bad", listOf(SongId("otro-tema")), mapped.songs.map { it.id })
            val expectedId = bad.id?.trim()?.ifEmpty { null }
            assertEquals("$bad", listOf(RejectedSong(2, expectedId, listOf(issue))), mapped.rejected)
            assertTrue(issue.rejectsSong)
        }
    }

    @Test
    fun `every issue of a rejected row is collected in column order`() {
        val mapped = CatalogMapper.map(
            listOf(SongDto("Mal Id", null, null, "H", "rapido", null, "dificil", "1e3")),
        )

        assertEquals(emptyList<Song>(), mapped.songs)
        assertEquals(
            listOf(
                RejectedSong(
                    1,
                    "Mal Id",
                    listOf(
                        SongIssue.InvalidId,
                        SongIssue.MissingTitle,
                        SongIssue.MissingArtist,
                        SongIssue.InvalidDefaultKey,
                        SongIssue.InvalidTempo,
                        SongIssue.InvalidDifficulty,
                        SongIssue.InvalidSongsterrId,
                    ),
                ),
            ),
            mapped.rejected,
        )
    }

    /** User approval Q2: this test fails if an invalid optional value rejected the song instead. */
    @Test
    fun `an invalid tempo drops the field and keeps the song`() {
        val mapped = CatalogMapper.map(listOf(row(tempo = "rapido", tags = "shuffle", songsterrId = "7")))

        assertEquals(
            listOf(
                Song(SongId("blues-uno"), "Blues Uno", "Alguien", Key("A"), tags = listOf("shuffle"), songsterrId = 7L),
            ),
            mapped.songs,
        )
        assertEquals(emptyList<RejectedSong>(), mapped.rejected)
        assertEquals(listOf(DroppedFields(1, "blues-uno", listOf(SongIssue.InvalidTempo))), mapped.dropped)
    }

    @Test
    fun `invalid optional values are dropped one by one, with no case or accent folding`() {
        val cases = listOf(
            row(tempo = "rapido") to SongIssue.InvalidTempo,
            row(tempo = "Rápido") to SongIssue.InvalidTempo,
            row(tempo = "LENTO") to SongIssue.InvalidTempo,
            row(difficulty = "facil") to SongIssue.InvalidDifficulty,
            row(difficulty = "Media") to SongIssue.InvalidDifficulty,
            row(songsterrId = "12.5") to SongIssue.InvalidSongsterrId,
            row(songsterrId = "1e3") to SongIssue.InvalidSongsterrId,
            row(songsterrId = "-5") to SongIssue.InvalidSongsterrId,
            row(songsterrId = "99999999999999999999") to SongIssue.InvalidSongsterrId,
        )
        cases.forEach { (bad, issue) ->
            val mapped = CatalogMapper.map(listOf(bad))

            assertEquals("$bad", listOf(Song(SongId("blues-uno"), "Blues Uno", "Alguien", Key("A"))), mapped.songs)
            assertEquals("$bad", listOf(DroppedFields(1, "blues-uno", listOf(issue))), mapped.dropped)
            assertFalse(issue.rejectsSong)
        }
    }

    @Test
    fun `every vocabulary value maps exactly`() {
        val tempos = mapOf("lento" to Tempo.SLOW, "medio" to Tempo.MEDIUM, "rápido" to Tempo.FAST)
        val difficulties = mapOf("fácil" to Difficulty.EASY, "media" to Difficulty.MEDIUM, "difícil" to Difficulty.HARD)

        tempos.forEach { (cell, tempo) -> assertEquals(tempo, single(row(tempo = cell)).tempo) }
        difficulties.forEach { (cell, difficulty) ->
            assertEquals(difficulty, single(row(difficulty = cell)).difficulty)
        }
        assertEquals(9_007_199_254_740_993L, single(row(songsterrId = "9007199254740993")).songsterrId)
        assertEquals(42L, single(row(songsterrId = " 0042 ")).songsterrId)
    }

    @Test
    fun `cells are trimmed and empty means none`() {
        val song = single(SongDto(" blues-uno ", " Blues Uno ", " Alguien ", "B ", " ", "", " ", "  "))

        assertEquals(Song(SongId("blues-uno"), "Blues Uno", "Alguien", Key("B")), song)
    }

    @Test
    fun `tags split on commas, trimmed, empty ones dropped, order and duplicates kept`() {
        assertEquals(listOf("a", "b"), single(row(tags = " a,, b ,")).tags)
        assertEquals(listOf("shuffle", "slow blues", "shuffle"), single(row(tags = "shuffle, slow blues,shuffle")).tags)
        assertEquals(emptyList<String>(), single(row(tags = " , ,")).tags)
        assertEquals(emptyList<String>(), single(row(tags = null)).tags)
    }

    @Test
    fun `every valid row sharing an id is rejected`() {
        val mapped = CatalogMapper.map(
            listOf(
                row(id = "crossroads", title = "Crossroads"),
                row(id = "otro-tema"),
                row(id = "crossroads", title = "Crossroads (Cream)", tempo = "Rápido"),
                row(id = "crossroads", defaultKey = null),
            ),
        )

        assertEquals(listOf(SongId("otro-tema")), mapped.songs.map { it.id })
        assertEquals(
            listOf(
                RejectedSong(1, "crossroads", listOf(SongIssue.DuplicateId)),
                RejectedSong(3, "crossroads", listOf(SongIssue.InvalidTempo, SongIssue.DuplicateId)),
                RejectedSong(4, "crossroads", listOf(SongIssue.MissingDefaultKey)),
            ),
            mapped.rejected,
        )
        assertEquals(emptyList<DroppedFields>(), mapped.dropped)
    }

    @Test
    fun `an empty song list maps to an empty catalog`() {
        assertEquals(MappedCatalog(emptyList(), emptyList(), emptyList()), CatalogMapper.map(emptyList()))
    }

    private fun single(dto: SongDto): Song {
        val mapped = CatalogMapper.map(listOf(dto))
        assertEquals("$dto", emptyList<RejectedSong>(), mapped.rejected)
        return mapped.songs.single()
    }

    private fun row(
        id: String? = "blues-uno",
        title: String? = "Blues Uno",
        artist: String? = "Alguien",
        defaultKey: String? = "A",
        tempo: String? = null,
        tags: String? = null,
        difficulty: String? = null,
        songsterrId: String? = null,
    ) = SongDto(id, title, artist, defaultKey, tempo, tags, difficulty, songsterrId)
}
