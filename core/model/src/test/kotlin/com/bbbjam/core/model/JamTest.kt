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
    fun `a published jam with an available setlist keeps its positions`() {
        val jam = jam(JamStatus.PUBLISHED, Setlist.Available(listOf(jamSong(1), jamSong(2), jamSong(3))))

        assertEquals(listOf(1, 2, 3), (jam.setlist as Setlist.Available).songs.map { it.position })
    }

    @Test
    fun `positions with a gap build a jam and are not renumbered`() {
        val jam = jam(JamStatus.PUBLISHED, Setlist.Available(listOf(jamSong(1), jamSong(3)), droppedRows = 1))

        assertEquals(listOf(1, 3), (jam.setlist as Setlist.Available).songs.map { it.position })
    }

    @Test
    fun `a duplicate or a wrong order is rejected`() {
        listOf(listOf(1, 1), listOf(2, 1), listOf(1, 3, 2)).forEach { positions ->
            assertThrows("$positions accepted", IllegalArgumentException::class.java) {
                jam(JamStatus.PUBLISHED, Setlist.Available(positions.map { jamSong(it) }))
            }
        }
    }

    @Test
    fun `a published jam is never withheld`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            jam(JamStatus.PUBLISHED, Setlist.Withheld)
        }

        assertEquals("The published 2026-07-25 jam cannot have a withheld setlist", error.message)
    }

    @Test
    fun `a draft may be withheld or available, and a published jam unavailable`() {
        assertEquals(Setlist.Withheld, jam(JamStatus.DRAFT, Setlist.Withheld).setlist)
        assertEquals(
            1,
            (jam(JamStatus.DRAFT, Setlist.Available(listOf(jamSong(1)))).setlist as Setlist.Available).songs.size,
        )
        val unavailable = Setlist.Unavailable(SetlistProblem.MISSING_TAB)
        assertEquals(unavailable, jam(JamStatus.PUBLISHED, unavailable).setlist)
    }

    @Test
    fun `a jam is historical only after its date`() {
        val jam = jam(JamStatus.PUBLISHED, Setlist.Available(emptyList()))

        assertTrue(jam.isHistorical(today = LocalDate.of(2026, 7, 26)))
        assertFalse(jam.isHistorical(today = LocalDate.of(2026, 7, 25)))
        assertFalse(jam.isHistorical(today = LocalDate.of(2026, 7, 24)))
    }

    private fun jam(status: JamStatus, setlist: Setlist): Jam = Jam(
        date = LocalDate.of(2026, 7, 25),
        startTime = LocalTime.of(21, 0),
        venue = "Bar de prueba",
        status = status,
        setlist = setlist,
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
