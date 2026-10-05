package com.bbbjam.feature.pastjams

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.back
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.busyLineup
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.header
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.jam
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.julyDate
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.notFound
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.row
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.snapshot
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.song
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The state table of `docs/specs/past-jam-detail.md` (Technical Approach 2), through the pure mapping. */
class PastJamDetailMappingTest {

    private fun detail(vararg past: Jam, upcoming: Jam? = null) =
        snapshot(past.toList(), upcoming).toPastJamDetail(julyDate)

    @Test
    fun `a 13-song jam is the header with the count and 13 rows in position order`() {
        val songs = (1..13).map { song(it) }
        val model = detail(jam(setlist = Setlist.Available(songs)))
        assertEquals(
            PastJamDetailUiModel.Jam(
                header = header("13 temas"),
                setlist = PastSetlistUiModel.Songs(
                    rows = listOf(
                        row(1, "01"), row(2, "02"), row(3, "03"), row(4, "04"), row(5, "05"), row(6, "06"),
                        row(7, "07"), row(8, "08"), row(9, "09"), row(10, "10"), row(11, "11"), row(12, "12"),
                        row(13, "13"),
                    ),
                    droppedRowsNote = null,
                ),
                back = back,
            ),
            model,
        )
    }

    @Test
    fun `the key is the jam song's key and is described as Tonalidad`() {
        val model = detail(jam(setlist = Setlist.Available(listOf(song(1, key = "Bbm"))))) as PastJamDetailUiModel.Jam
        val single = (model.setlist as PastSetlistUiModel.Songs).rows.single()
        assertEquals("Bbm", single.key)
        assertEquals("Tonalidad Bbm", single.keyDescription)
    }

    @Test
    fun `a blank artist is null`() {
        val model = detail(jam(setlist = Setlist.Available(listOf(song(1, artist = "  "))))) as PastJamDetailUiModel.Jam
        assertNull((model.setlist as PastSetlistUiModel.Songs).rows.single().artist)
    }

    @Test
    fun `the dropped-rows note says how many rows were lost, and nothing when none`() {
        fun note(dropped: Int): String? {
            val model = detail(jam(setlist = Setlist.Available(listOf(song(1)), dropped))) as PastJamDetailUiModel.Jam
            return (model.setlist as PastSetlistUiModel.Songs).droppedRowsNote
        }
        assertNull(note(0))
        assertEquals("Falta 1 tema: no se pudo leer.", note(1))
        assertEquals("Faltan 2 temas: no se pudieron leer.", note(2))
    }

    @Test
    fun `a withheld, unreadable or empty setlist keeps the header without a count and shows the Anteriores line`() {
        assertEquals(
            PastJamDetailUiModel.Jam(
                header(null),
                PastSetlistUiModel.NotShown("La lista de esta jam no se publicó."),
                back,
            ),
            detail(jam(setlist = Setlist.Withheld, status = JamStatus.DRAFT)),
        )
        assertEquals(
            PastJamDetailUiModel.Jam(
                header(null),
                PastSetlistUiModel.NotShown("No se pudo leer la lista de esta jam."),
                back,
            ),
            detail(jam(setlist = Setlist.Unavailable(SetlistProblem.MISSING_TAB))),
        )
        assertEquals(
            PastJamDetailUiModel.Jam(
                header(null),
                PastSetlistUiModel.NotShown("Esta jam no tiene temas cargados."),
                back,
            ),
            detail(jam(setlist = Setlist.Available(emptyList()))),
        )
    }

    @Test
    fun `a draft whose songs reached the app is never shown`() {
        assertEquals(
            PastJamDetailUiModel.Jam(
                header(null),
                PastSetlistUiModel.NotShown("La lista de esta jam no se publicó."),
                back,
            ),
            detail(jam(setlist = Setlist.Available(listOf(song(1))), status = JamStatus.DRAFT)),
        )
    }

    @Test
    fun `no past jam with that date is not found, and the upcoming jam with the same date is ignored`() {
        assertEquals(notFound, detail())
        assertEquals(notFound, detail(jam(date = LocalDate.of(2026, 6, 27))))
        assertEquals(notFound, detail(upcoming = jam()))
    }

    @Test
    fun `slots, musicians and extras never change a row`() {
        val bare = detail(jam(setlist = Setlist.Available(listOf(song(1)))))
        val busy = detail(
            jam(
                setlist = Setlist.Available(
                    listOf(song(1, lineup = busyLineup, extras = listOf(ExtraParticipant("Juan", "saxo")))),
                ),
            ),
        )
        assertEquals(bare, busy)
    }

    @Test
    fun `Back calls onBack`() {
        var calls = 0
        val model = snapshot(listOf(jam())).toPastJamDetail(julyDate) { calls++ }
        model.back.events(BackUiModel.Event.Back)
        assertEquals(1, calls)
    }
}
