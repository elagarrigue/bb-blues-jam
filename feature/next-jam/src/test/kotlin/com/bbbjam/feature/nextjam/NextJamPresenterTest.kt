package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.SongId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every user-facing string expected here is written out from the approved copy table in
 * `docs/specs/next-jam-read-only-list.md`, never read from [NextJamCopy], so any change to the
 * shipped copy fails this class.
 */
class NextJamPresenterTest {
    private val repository = FakeJamsRepository()

    private fun calendarAt(instant: String) =
        JamCalendar(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)

    /** 2 October 2026, 12:00 in Buenos Aires. */
    private val octoberSecond = calendarAt("2026-10-02T15:00:00Z")

    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)
    private val neverFetched = Freshness(fetchedAt = null, lastFailure = null, isRefreshing = true)

    /** The 13 songs of the 2026-07-25 tab (`docs/api-samples/jams-seed.json`): id, title and key, in position order. */
    private val seedSongs = listOf(
        Triple("sweet-little-angel", "Sweet Little Angel", "B"),
        Triple("walking-thru-the-park", "Walking Thru the Park", "A"),
        Triple("dust-my-broom", "Dust My Broom", "D"),
        Triple("blues-del-politico", "Blues Del Politico", "C"),
        Triple("the-thrill-is-gone", "The Thrill Is Gone", "Bm"),
        Triple("blues-del-equipaje", "Blues del Equipaje", "A"),
        Triple("cafe-madrid", "Café Madrid", "G"),
        Triple("got-my-mojo-working", "Got My Mojo Working", "E"),
        Triple("messin-with-the-kid", "Messin' With the Kid", "C"),
        Triple("crossroads", "Crossroads", "A"),
        Triple("the-score", "The Score", "C"),
        Triple("blues-de-rosario", "Blues de Rosario", "E"),
        Triple("tres-palabras", "Tres Palabras", "A"),
    ).mapIndexed { index, (id, title, key) -> song(index + 1, id, title, key) }

    private fun song(position: Int, id: String, title: String, key: String) = JamSong(
        position = position,
        songId = SongId(id),
        title = title,
        artist = "Someone",
        key = Key(key),
        lineup = Lineup.default(),
    )

    private fun jam(
        setlist: Setlist,
        status: JamStatus = JamStatus.PUBLISHED,
        date: LocalDate = LocalDate.of(2026, 10, 31),
    ) = Jam(date = date, startTime = LocalTime.of(21, 0), venue = "La Macanuda", status = status, setlist = setlist)

    private fun snapshot(upcoming: Jam?, freshness: Freshness = fetched) =
        JamsSnapshot(upcoming = upcoming, past = emptyList(), freshness = freshness)

    private val header = JamHeaderUiModel("Sábado 31 de octubre · 21:00", "La Macanuda", "En 29 días")

    private fun presenter(calendar: JamCalendar = octoberSecond) = NextJamPresenter(repository, calendar)

    private suspend fun ReceiveTurbine<NextJamUiModel>.awaitAfterLoading(): NextJamUiModel {
        var item = awaitItem()
        while (item == NextJamUiModel.Loading) item = awaitItem()
        return item
    }

    @Test
    fun `loading, then a published jam with its header and every row`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(NextJamUiModel.Loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Available(seedSongs))))
            val expectedRows = listOf(
                SongRowUiModel(1, "01", "Sweet Little Angel", "B", "Tonalidad B"),
                SongRowUiModel(2, "02", "Walking Thru the Park", "A", "Tonalidad A"),
                SongRowUiModel(3, "03", "Dust My Broom", "D", "Tonalidad D"),
                SongRowUiModel(4, "04", "Blues Del Politico", "C", "Tonalidad C"),
                SongRowUiModel(5, "05", "The Thrill Is Gone", "Bm", "Tonalidad Bm"),
                SongRowUiModel(6, "06", "Blues del Equipaje", "A", "Tonalidad A"),
                SongRowUiModel(7, "07", "Café Madrid", "G", "Tonalidad G"),
                SongRowUiModel(8, "08", "Got My Mojo Working", "E", "Tonalidad E"),
                SongRowUiModel(9, "09", "Messin' With the Kid", "C", "Tonalidad C"),
                SongRowUiModel(10, "10", "Crossroads", "A", "Tonalidad A"),
                SongRowUiModel(11, "11", "The Score", "C", "Tonalidad C"),
                SongRowUiModel(12, "12", "Blues de Rosario", "E", "Tonalidad E"),
                SongRowUiModel(13, "13", "Tres Palabras", "A", "Tonalidad A"),
            )
            assertEquals(NextJamUiModel.Jam(header, SetlistUiModel.Songs(expectedRows, null)), awaitItem())
        }
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `no upcoming jam after a fetch, then the jam appears`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(NextJamUiModel.Loading, awaitItem())
            repository.snapshots.emit(snapshot(upcoming = null))
            assertEquals(
                NextJamUiModel.NoUpcomingJam(
                    "La próxima jam todavía no tiene fecha. Cuando se confirme, la vas a ver acá.",
                ),
                awaitItem(),
            )
            repository.snapshots.emit(snapshot(jam(Setlist.Available(seedSongs.take(1)))))
            assertEquals(
                NextJamUiModel.Jam(
                    header,
                    SetlistUiModel.Songs(
                        listOf(SongRowUiModel(1, "01", "Sweet Little Angel", "B", "Tonalidad B")),
                        null,
                    ),
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `nothing ever fetched stays loading, with or without a failure`() = runTest {
        assertEquals(NextJamUiModel.Loading, snapshot(null, neverFetched).toUiModel(LocalDate.of(2026, 10, 2)))
        val offline = Freshness(fetchedAt = null, lastFailure = DataFailure.Offline, isRefreshing = false)
        assertEquals(NextJamUiModel.Loading, snapshot(null, offline).toUiModel(LocalDate.of(2026, 10, 2)))

        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(NextJamUiModel.Loading, awaitItem())
            repository.snapshots.emit(snapshot(null, neverFetched))
            repository.snapshots.emit(snapshot(null, offline))
            repository.snapshots.emit(snapshot(null, fetched))
            // Every model before the fetched snapshot is Loading, never "no jam".
            assertEquals(
                NextJamUiModel.NoUpcomingJam(
                    "La próxima jam todavía no tiene fecha. Cuando se confirme, la vas a ver acá.",
                ),
                awaitAfterLoading(),
            )
        }
    }

    @Test
    fun `dropped rows add a note, and positions with a gap keep their labels`() {
        val today = LocalDate.of(2026, 10, 2)
        val withGap = listOf(seedSongs[0], seedSongs[2])
        val gapRows = listOf(
            SongRowUiModel(1, "01", "Sweet Little Angel", "B", "Tonalidad B"),
            SongRowUiModel(3, "03", "Dust My Broom", "D", "Tonalidad D"),
        )
        assertEquals(
            NextJamUiModel.Jam(header, SetlistUiModel.Songs(gapRows, null)),
            snapshot(jam(Setlist.Available(withGap, droppedRows = 0))).toUiModel(today),
        )
        assertEquals(
            NextJamUiModel.Jam(header, SetlistUiModel.Songs(gapRows, "Falta 1 tema: no se pudo leer.")),
            snapshot(jam(Setlist.Available(withGap, droppedRows = 1))).toUiModel(today),
        )
        assertEquals(
            NextJamUiModel.Jam(header, SetlistUiModel.Songs(gapRows, "Faltan 2 temas: no se pudieron leer.")),
            snapshot(jam(Setlist.Available(withGap, droppedRows = 2))).toUiModel(today),
        )
        assertEquals(
            NextJamUiModel.Jam(header, SetlistUiModel.Songs(emptyList(), null)),
            snapshot(jam(Setlist.Available(emptyList()))).toUiModel(today),
        )
    }

    @Test
    fun `a withheld or unavailable setlist shows the header and one line, no rows`() = runTest {
        val today = LocalDate.of(2026, 10, 2)
        val withheld = NextJamUiModel.Jam(
            header,
            SetlistUiModel.NotShown("La lista de temas se está armando. Cuando se publique, la vas a ver acá."),
        )
        val unavailable = NextJamUiModel.Jam(
            header,
            SetlistUiModel.NotShown("No se pudo leer la lista de temas de esta jam. Avisale a la organización."),
        )
        assertEquals(withheld, snapshot(jam(Setlist.Withheld, JamStatus.DRAFT)).toUiModel(today))
        SetlistProblem.entries.forEach { problem ->
            assertEquals(unavailable, snapshot(jam(Setlist.Unavailable(problem))).toUiModel(today))
        }

        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(NextJamUiModel.Loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Withheld, JamStatus.DRAFT)))
            assertEquals(withheld, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Unavailable(SetlistProblem.MISSING_TAB))))
            assertEquals(unavailable, awaitItem())
        }
    }

    @Test
    fun `time remaining uses today in Buenos Aires, not UTC`() = runTest {
        val upcoming = snapshot(jam(Setlist.Available(seedSongs.take(1))))

        // 23:30 in Buenos Aires on 30 October is already 31 October in UTC.
        moleculeFlow(RecompositionMode.Immediate) { presenter(calendarAt("2026-10-31T02:30:00Z")).present(Unit) }.test {
            assertEquals(NextJamUiModel.Loading, awaitItem())
            repository.snapshots.emit(upcoming)
            assertEquals("Mañana", (awaitItem() as NextJamUiModel.Jam).header.timeRemaining)
        }

        // 20:00 in Buenos Aires on the jam's own date.
        moleculeFlow(RecompositionMode.Immediate) { presenter(calendarAt("2026-10-31T23:00:00Z")).present(Unit) }.test {
            assertEquals("Esta noche", (awaitAfterLoading() as NextJamUiModel.Jam).header.timeRemaining)
        }
    }
}
