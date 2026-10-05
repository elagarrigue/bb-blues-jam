package com.bbbjam.feature.songdetail

import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.ui.lineup.InstrumentGroupsUiModel
import com.bbbjam.feature.songdetail.SongDetailFixtures.failedRead
import com.bbbjam.feature.songdetail.SongDetailFixtures.jam
import com.bbbjam.feature.songdetail.SongDetailFixtures.notFound
import com.bbbjam.feature.songdetail.SongDetailFixtures.pastDate
import com.bbbjam.feature.songdetail.SongDetailFixtures.snapshot
import com.bbbjam.feature.songdetail.SongDetailFixtures.song
import com.bbbjam.feature.songdetail.SongDetailFixtures.songModel
import com.bbbjam.feature.songdetail.SongDetailFixtures.upcomingDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Decision 2 of `song-detail-screen`, row by row, on the pure mapping. */
class SongDetailMappingTest {
    private val upcoming = jam(upcomingDate, Setlist.Available(listOf(song(1, title = "Sweet Little Angel"), song(2))))

    @Test
    fun `a song of the upcoming jam, found by date and position`() {
        assertEquals(songModel(), snapshot(upcoming).toSongDetail(upcomingDate, 2) {})
    }

    @Test
    fun `a song of a past jam is found too`() {
        val past = jam(pastDate, Setlist.Available(listOf(song(3, title = "Dust My Broom", key = "D"))))
        assertEquals(
            songModel(title = "Dust My Broom", key = "D"),
            snapshot(upcoming, past = listOf(past)).toSongDetail(pastDate, 3) {},
        )
    }

    @Test
    fun `no such jam, no such position, or an empty snapshot is not found`() {
        assertEquals(notFound, snapshot(upcoming).toSongDetail(upcomingDate.plusDays(1), 2) {})
        assertEquals(notFound, snapshot(upcoming).toSongDetail(upcomingDate, 3) {})
        assertEquals(notFound, snapshot(null).toSongDetail(upcomingDate, 2) {})
        assertEquals(notFound, failedRead.toSongDetail(upcomingDate, 2) {})
    }

    @Test
    fun `a withheld or unavailable setlist is not found, never an error`() {
        val draft = jam(upcomingDate, Setlist.Withheld, JamStatus.DRAFT)
        assertEquals(notFound, snapshot(draft).toSongDetail(upcomingDate, 1) {})
        SetlistProblem.entries.forEach { problem ->
            assertEquals(
                notFound,
                snapshot(jam(upcomingDate, Setlist.Unavailable(problem))).toSongDetail(upcomingDate, 1) {},
            )
        }
    }

    @Test
    fun `a draft jam with available songs is not found (unpublished-setlist-state, scenario 4)`() {
        val draft = jam(upcomingDate, Setlist.Available(listOf(song(1), song(2))), JamStatus.DRAFT)
        assertEquals(notFound, snapshot(draft).toSongDetail(upcomingDate, 1) {})
        assertEquals(notFound, snapshot(draft).toSongDetail(upcomingDate, 2) {})
    }

    @Test
    fun `a blank artist is null, and an empty lineup is the note, not an error`() {
        val bare =
            jam(
                upcomingDate,
                Setlist.Available(listOf(song(1, artist = "  ", lineup = Lineup(emptyList()), extras = emptyList()))),
            )
        val model = snapshot(bare).toSongDetail(upcomingDate, 1) {} as SongDetailUiModel.Song
        assertNull(model.artist)
        assertEquals(InstrumentGroupsUiModel(emptyList(), emptyList(), "No quedan cupos libres.", null), model.lineup)
    }

    @Test
    fun `the key is the jam song's key as the admin set it`() {
        // The catalog's default key for this song might be E; the detail never reads the catalog
        // (D-08), so only the jam song's A can appear.
        val inA = jam(upcomingDate, Setlist.Available(listOf(song(1, key = "A"))))
        val model = snapshot(inA).toSongDetail(upcomingDate, 1) {} as SongDetailUiModel.Song
        assertEquals("A", model.key)
        assertEquals("Tonalidad A", model.keyDescription)
        assertEquals("Tonalidad", model.keyLabel)
    }

    @Test
    fun `the back handler of every state calls onBack`() {
        var calls = 0
        listOf(
            snapshot(upcoming).toSongDetail(upcomingDate, 2) { calls++ },
            snapshot(null).toSongDetail(upcomingDate, 2) { calls++ },
        ).forEach { it.back.events(com.bbbjam.core.ui.nav.BackUiModel.Event.Back) }
        assertEquals(2, calls)
    }
}
