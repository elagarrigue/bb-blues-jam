package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignMusicianSuggestionsTest {
    private val date = LocalDate.of(2026, 10, 31)
    private val freshness = Freshness(Instant.parse("2026-10-01T00:00:00Z"), null, false)

    @Test
    fun `current suggestions include extras and word-prefix matching ignores accents and case`() {
        val snapshot = snapshot(
            upcoming = jam(
                date,
                listOf(song("Tincho Pérez", extras = listOf(ExtraParticipant("María Sol", "saxo")))),
            ),
        )

        assertEquals(listOf("Tincho Pérez", "María Sol"), musicianSuggestions(snapshot, date, "").current)
        assertEquals(listOf("María Sol"), musicianSuggestions(snapshot, date, "SOL").current)
        assertEquals(listOf("Tincho Pérez"), musicianSuggestions(snapshot, date, "perez").current)
    }

    @Test
    fun `past suggestions deduplicate accent and case using newest spelling and exclude current names`() {
        val snapshot = snapshot(
            upcoming = jam(date, listOf(song("mArIa"))),
            past = listOf(
                jam(
                    date.minusMonths(1),
                    listOf(song("JUAN Pérez", extras = listOf(ExtraParticipant("Pablo", "saxo")))),
                ),
                jam(
                    date.minusMonths(2),
                    listOf(song("Juan Perez", extras = listOf(ExtraParticipant("María", "saxo")))),
                ),
            ),
        )

        assertEquals(listOf("JUAN Pérez", "Pablo"), musicianSuggestions(snapshot, date, "").past)
    }

    @Test
    fun `suggestions cap each section at eight and accept only prefixes at word boundaries`() {
        val names = (1..10).map { "Guitarrista $it" }
        val snapshot = snapshot(
            upcoming = jam(date, names.mapIndexed { index, name -> song(name, position = index + 1) }),
        )

        assertEquals(8, musicianSuggestions(snapshot, date, "").current.size)
        assertEquals(0, musicianSuggestions(snapshot, date, "tarri").current.size)
        assertTrue(musicianSuggestions(snapshot, date, "guitar").current.isNotEmpty())
    }

    private fun snapshot(upcoming: Jam? = null, past: List<Jam> = emptyList()) = JamsSnapshot(upcoming, past, freshness)

    private fun jam(date: LocalDate, songs: List<JamSong>) = Jam(
        date,
        LocalTime.of(21, 0),
        "La Macanuda",
        JamStatus.PUBLISHED,
        Setlist.Available(songs),
    )

    private fun song(name: String, extras: List<ExtraParticipant> = emptyList(), position: Int = 1) = JamSong(
        position = position,
        songId = SongId("song-${slug(name)}-${name.length}"),
        title = "Crossroads",
        artist = "Cream",
        key = Key("A"),
        lineup = Lineup(listOf(Slot(Instrument.GUITAR, name))),
        extraParticipants = extras,
    )

    private fun slug(name: String): String = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}
