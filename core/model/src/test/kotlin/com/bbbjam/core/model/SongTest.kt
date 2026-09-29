package com.bbbjam.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTest {

    @Test
    fun `a song needs only id, title, artist and default key`() {
        val song = Song(
            id = SongId("cafe-madrid"),
            title = "Café Madrid",
            artist = "Memphis la Blusera",
            defaultKey = Key("A"),
        )

        assertNull(song.tempo)
        assertTrue(song.tags.isEmpty())
        assertNull(song.difficulty)
        assertNull(song.songsterrId)
        assertNull(song.mbid)
        assertNull(song.artistArea)
        assertNull(song.deezerTrackId)
        assertNull(song.previewUrl)
        assertNull(song.artworkUrl)
    }

    @Test
    fun `catalog fields and enrichment are kept when present`() {
        val song = Song(
            id = SongId("sweet-little-angel"),
            title = "Sweet Little Angel",
            artist = "B.B. King",
            defaultKey = Key("Bb"),
            tempo = Tempo.SLOW,
            tags = listOf("chicago", "12 compases"),
            difficulty = Difficulty.EASY,
            songsterrId = 12345L,
            artistArea = "United States",
        )

        assertEquals(Tempo.SLOW, song.tempo)
        assertEquals(listOf("chicago", "12 compases"), song.tags)
        assertEquals(Difficulty.EASY, song.difficulty)
        assertEquals(12345L, song.songsterrId)
        assertEquals("United States", song.artistArea)
    }
}
