package com.bbbjam.core.model

import com.bbbjam.core.model.Instrument.BASS
import com.bbbjam.core.model.Instrument.GUITAR
import com.bbbjam.core.model.Instrument.HARMONICA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JamSongTest {

    @Test
    fun `a position below one is rejected`() {
        listOf(0, -1).forEach { position ->
            assertThrows("$position accepted", IllegalArgumentException::class.java) {
                jamSong(position = position)
            }
        }
    }

    @Test
    fun `a jam song has no extra participants by default`() {
        assertTrue(jamSong().extraParticipants.isEmpty())
    }

    @Test
    fun `extra participants never open a slot`() {
        val lineup = Lineup(listOf(Slot(GUITAR, "Pato"), Slot(GUITAR, "Lucho"), Slot(BASS, "Tincho")))
        val withExtras = jamSong(lineup = lineup).copy(
            extraParticipants = listOf(ExtraParticipant("Juan", "saxo"), ExtraParticipant("Ana", "guitarra")),
        )

        assertTrue(withExtras.lineup.openSlots.isEmpty())
        Instrument.entries.forEach { assertFalse(withExtras.lineup.hasOpenSlotFor(it)) }
        assertEquals(jamSong(lineup = lineup).lineup, withExtras.lineup)
    }

    @Test
    fun `extra participants do not take an open slot away`() {
        val lineup = Lineup(listOf(Slot(GUITAR, "Pato"), Slot(HARMONICA)))
        val withExtras = jamSong(lineup = lineup).copy(
            extraParticipants = listOf(ExtraParticipant("Ana", "armónica")),
        )

        assertEquals(listOf(Slot(HARMONICA)), withExtras.lineup.openSlots)
        assertTrue(withExtras.lineup.hasOpenSlotFor(HARMONICA))
    }

    @Test
    fun `the jam key can differ from the catalog default key`() {
        val song =
            Song(
                id = SongId("sweet-little-angel"),
                title = "Sweet Little Angel",
                artist = "B.B. King",
                defaultKey = Key("Bb"),
            )
        val scheduled = jamSong().copy(songId = song.id, key = Key("G"))

        assertEquals(song.id, scheduled.songId)
        assertEquals(Key("G"), scheduled.key)
        assertEquals(Key("Bb"), song.defaultKey)
    }

    private fun jamSong(position: Int = 1, lineup: Lineup = Lineup.default()): JamSong = JamSong(
        position = position,
        songId = SongId("the-thrill-is-gone"),
        title = "The Thrill Is Gone",
        artist = "B.B. King",
        key = Key("Bm"),
        lineup = lineup,
    )
}
