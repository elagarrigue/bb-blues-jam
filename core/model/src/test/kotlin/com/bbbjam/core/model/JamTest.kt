package com.bbbjam.core.model

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JamTest {

    @Test
    fun `positions one to n in order build a jam`() {
        val jam = jam(positions = listOf(1, 2, 3))

        assertEquals(listOf(1, 2, 3), jam.setlist.map { it.position })
    }

    @Test
    fun `an empty setlist builds a jam`() {
        assertTrue(jam(positions = emptyList()).setlist.isEmpty())
    }

    @Test
    fun `a gap, a duplicate, a wrong start or a wrong order is rejected`() {
        listOf(listOf(2), listOf(1, 3), listOf(1, 1), listOf(2, 1)).forEach { positions ->
            assertThrows("$positions accepted", IllegalArgumentException::class.java) { jam(positions) }
        }
    }

    @Test
    fun `a jam is historical only after its date`() {
        val jam = jam(positions = emptyList())

        assertTrue(jam.isHistorical(today = LocalDate.of(2026, 7, 26)))
        assertFalse(jam.isHistorical(today = LocalDate.of(2026, 7, 25)))
        assertFalse(jam.isHistorical(today = LocalDate.of(2026, 7, 24)))
    }

    private fun jam(positions: List<Int>): Jam = Jam(
        date = LocalDate.of(2026, 7, 25),
        startTime = LocalTime.of(21, 0),
        venue = "Bar de prueba",
        status = JamStatus.PUBLISHED,
        setlist = positions.map { jamSong(it) },
    )

    private fun jamSong(position: Int): JamSong = JamSong(
        position = position,
        songId = SongId("song-$position"),
        title = "Song $position",
        artist = "Artist",
        key = Key("E"),
        lineup = Lineup.default(),
    )
}
