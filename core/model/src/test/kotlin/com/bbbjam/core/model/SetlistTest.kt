package com.bbbjam.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SetlistTest {

    @Test
    fun `ascending unique positions build, gaps included, and are never renumbered`() {
        listOf(listOf(1, 2, 3), listOf(2), listOf(1, 3), listOf(4, 7, 9)).forEach { positions ->
            val setlist = Setlist.Available(positions.map { jamSong(it) })

            assertEquals(positions, setlist.songs.map { it.position })
        }
    }

    @Test
    fun `a duplicate or an out-of-order position is rejected`() {
        listOf(listOf(1, 1), listOf(2, 1), listOf(1, 3, 3), listOf(1, 3, 2)).forEach { positions ->
            assertThrows("$positions accepted", IllegalArgumentException::class.java) {
                Setlist.Available(positions.map { jamSong(it) })
            }
        }
    }

    @Test
    fun `an empty setlist with no dropped rows builds`() {
        assertEquals(0, Setlist.Available(emptyList()).droppedRows)
    }

    @Test
    fun `the dropped row count is kept and never negative`() {
        assertEquals(2, Setlist.Available(listOf(jamSong(1)), droppedRows = 2).droppedRows)
        assertThrows(IllegalArgumentException::class.java) { Setlist.Available(listOf(jamSong(1)), droppedRows = -1) }
    }

    @Test
    fun `a setlist whose every row was dropped cannot be an empty available one`() {
        assertThrows(IllegalArgumentException::class.java) { Setlist.Available(emptyList(), droppedRows = 1) }
    }

    private fun jamSong(position: Int): JamSong = JamSong(
        position = position,
        songId = SongId("song-$position"),
        title = "Song $position",
        artist = "Artist",
        key = Key("E"),
        lineup = Lineup.default(),
    )
}
