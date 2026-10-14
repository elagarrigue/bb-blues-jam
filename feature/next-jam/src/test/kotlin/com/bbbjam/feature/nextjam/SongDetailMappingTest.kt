package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.ui.lineup.LineupPanelUiModel
import com.bbbjam.feature.nextjam.SongDetailFixtures.failedRead
import com.bbbjam.feature.nextjam.SongDetailFixtures.id
import com.bbbjam.feature.nextjam.SongDetailFixtures.jam
import com.bbbjam.feature.nextjam.SongDetailFixtures.notFound
import com.bbbjam.feature.nextjam.SongDetailFixtures.pastDate
import com.bbbjam.feature.nextjam.SongDetailFixtures.snapshot
import com.bbbjam.feature.nextjam.SongDetailFixtures.song
import com.bbbjam.feature.nextjam.SongDetailFixtures.songModel
import com.bbbjam.feature.nextjam.SongDetailFixtures.upcomingDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Decision 2 of `song-detail-screen` and P2 of `song-detail-replaces-row-expansion`, on the pure mapping. */
class SongDetailMappingTest {
    private val upcoming = jam(upcomingDate, Setlist.Available(listOf(song(1, title = "Sweet Little Angel"), song(2))))

    @Test
    fun `a song of the upcoming jam, found by date and position`() {
        assertEquals(songModel(), snapshot(upcoming).toSongDetail(upcomingDate, id(2), 2) {})
    }

    @Test
    fun `the song is found by id, so a renumbered position still shows the same song (P2)`() {
        // A move or removal confirmed while the detail is open renumbers the list: song-2 is now 1.
        val renumbered = jam(upcomingDate, Setlist.Available(listOf(song(2).copy(position = 1))))
        assertEquals(songModel(), snapshot(renumbered).toSongDetail(upcomingDate, id(2), 2) {})
    }

    @Test
    fun `a repeated id picks the row at the position, and a missing id is not found`() {
        val twice = jam(
            upcomingDate,
            Setlist.Available(
                listOf(song(1, title = "First copy"), song(1, title = "Second copy").copy(position = 2)),
            ),
        )
        val second = snapshot(twice).toSongDetail(upcomingDate, id(1), 2) {} as SongDetailUiModel.Song
        assertEquals("Second copy", second.title)
        val first = snapshot(twice).toSongDetail(upcomingDate, id(1), 1) {} as SongDetailUiModel.Song
        assertEquals("First copy", first.title)
        assertEquals(notFound, snapshot(twice).toSongDetail(upcomingDate, id(1), 3) {})
        assertEquals(notFound, snapshot(upcoming).toSongDetail(upcomingDate, id(7), 1) {})
    }

    @Test
    fun `a song of a past jam is found too`() {
        val past = jam(pastDate, Setlist.Available(listOf(song(3, title = "Dust My Broom", key = "D"))))
        assertEquals(
            songModel(title = "Dust My Broom", key = "D"),
            snapshot(upcoming, past = listOf(past)).toSongDetail(pastDate, id(3), 3) {},
        )
    }

    @Test
    fun `no such jam, no such position, or an empty snapshot is not found`() {
        assertEquals(notFound, snapshot(upcoming).toSongDetail(upcomingDate.plusDays(1), id(2), 2) {})
        assertEquals(notFound, snapshot(upcoming).toSongDetail(upcomingDate, id(3), 3) {})
        assertEquals(notFound, snapshot(null).toSongDetail(upcomingDate, id(2), 2) {})
        assertEquals(notFound, failedRead.toSongDetail(upcomingDate, id(2), 2) {})
    }

    @Test
    fun `a withheld or unavailable setlist is not found, never an error`() {
        val draft = jam(upcomingDate, Setlist.Withheld, JamStatus.DRAFT)
        assertEquals(notFound, snapshot(draft).toSongDetail(upcomingDate, id(1), 1) {})
        SetlistProblem.entries.forEach { problem ->
            assertEquals(
                notFound,
                snapshot(jam(upcomingDate, Setlist.Unavailable(problem))).toSongDetail(upcomingDate, id(1), 1) {},
            )
        }
    }

    @Test
    fun `a draft jam with available songs is not found (unpublished-setlist-state, scenario 4)`() {
        val draft = jam(upcomingDate, Setlist.Available(listOf(song(1), song(2))), JamStatus.DRAFT)
        assertEquals(notFound, snapshot(draft).toSongDetail(upcomingDate, id(1), 1) {})
        assertEquals(notFound, snapshot(draft).toSongDetail(upcomingDate, id(2), 2) {})
    }

    @Test
    fun `a song of a published jam behind a BORRADOR upcoming jam is found for a musician (D-26, S1)`() {
        val draft = jam(upcomingDate, Setlist.Available(listOf(song(1), song(2))), JamStatus.DRAFT)
        val laterDate = upcomingDate.plusDays(28)
        val later = jam(laterDate, Setlist.Available(listOf(song(1), song(2), song(3))))
        val cached = JamsSnapshot(draft, emptyList(), SongDetailFixtures.fetched, listOf(later))

        assertEquals(songModel(), cached.toSongDetail(laterDate, id(2), 2) {})
        // The draft's own song stays hidden, whatever its position.
        assertEquals(notFound, cached.toSongDetail(upcomingDate, id(2), 2) {})
    }

    @Test
    fun `a song of a past BORRADOR jam is not found`() {
        val past = jam(pastDate, Setlist.Available(listOf(song(1), song(2), song(3))), JamStatus.DRAFT)

        assertEquals(notFound, snapshot(null, past = listOf(past)).toSongDetail(pastDate, id(3), 3) {})
    }

    @Test
    fun `a blank artist is null, and an empty lineup is the note, not an error`() {
        val bare =
            jam(
                upcomingDate,
                Setlist.Available(listOf(song(1, artist = "  ", lineup = Lineup(emptyList()), extras = emptyList()))),
            )
        val model = snapshot(bare).toSongDetail(upcomingDate, id(1), 1) {} as SongDetailUiModel.Song
        assertNull(model.artist)
        assertEquals(
            LineupPanelUiModel(emptyList(), emptyList(), emptyList(), "No quedan cupos libres.", null),
            model.lineup,
        )
    }

    @Test
    fun `the key is the jam song's key as the admin set it`() {
        // The catalog's default key for this song might be E; the detail never reads the catalog
        // (D-08), so only the jam song's A can appear.
        val inA = jam(upcomingDate, Setlist.Available(listOf(song(1, key = "A"))))
        val model = snapshot(inA).toSongDetail(upcomingDate, id(1), 1) {} as SongDetailUiModel.Song
        assertEquals("A", model.key)
        assertEquals("Tonalidad A", model.keyDescription)
        assertEquals("Tonalidad", model.keyLabel)
    }

    @Test
    fun `the back handler of every state calls onBack`() {
        var calls = 0
        listOf(
            snapshot(upcoming).toSongDetail(upcomingDate, id(2), 2) { calls++ },
            snapshot(null).toSongDetail(upcomingDate, id(2), 2) { calls++ },
        ).forEach { it.back.events(com.bbbjam.core.ui.nav.BackUiModel.Event.Back) }
        assertEquals(2, calls)
    }
}
