package com.bbbjam.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicianRosterTest {
    private fun song(position: Int, vararg names: String?, extras: List<String> = emptyList()) = JamSong(
        position = position,
        songId = SongId("song-$position"),
        title = "Tema $position",
        artist = "Someone",
        key = Key("A"),
        lineup = Lineup(
            names.mapIndexed { index, name -> Slot(Instrument.entries[index], name) },
        ),
        extraParticipants = extras.map { ExtraParticipant(it, "saxo") },
    )

    @Test
    fun `the match key collapses whitespace and case`() {
        assertEquals("juan perez", musicianMatchKey("  Juan   PEREZ "))
        assertEquals(musicianMatchKey("Juan"), musicianMatchKey("juan  "))
    }

    @Test
    fun `involves matches a slot and an extra, and ignores open slots`() {
        val song = song(1, "Juan", null, extras = listOf("Ana"))
        assertTrue(song.involves("juan"))
        assertTrue(song.involves("ana"))
        assertFalse(song.involves("pedro"))
        assertFalse(song.involves(""))
    }

    @Test
    fun `the roster deduplicates by key, shows the first spelling and sorts case-insensitively`() {
        val songs = listOf(
            song(2, "juan  ", "beto"),
            song(1, "Juan", null, extras = listOf("ana")),
            song(3, "Zoe", "BETO"),
        )
        assertEquals(
            listOf(
                RosterEntry("ana", "ana", 1),
                RosterEntry("beto", "beto", 2),
                RosterEntry("Juan", "juan", 2),
                RosterEntry("Zoe", "zoe", 1),
            ),
            songs.musicianRoster(),
        )
    }

    @Test
    fun `a song counts once for a name held in a slot and in the extras`() {
        val roster = listOf(song(1, "Juan", extras = listOf("juan"))).musicianRoster()
        assertEquals(listOf(RosterEntry("Juan", "juan", 1)), roster)
    }

    @Test
    fun `open slots and placeholder cells are not musicians`() {
        val songs = listOf(song(1, null, "-", "--", "Ana"))
        assertEquals(listOf("Ana"), songs.musicianRoster().map { it.name })
        assertEquals(emptyList<RosterEntry>(), listOf(song(1, null, null)).musicianRoster())
    }
}
