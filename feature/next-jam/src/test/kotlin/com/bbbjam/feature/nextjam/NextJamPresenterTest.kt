package com.bbbjam.feature.nextjam

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.lineup.LineupLineUiModel
import com.bbbjam.core.ui.lineup.LineupPanelUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.ListErrorUiModel
import com.bbbjam.core.ui.state.StalenessNoticeUiModel
import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.strip.InstrumentChipUiModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every user-facing string expected here is written out from the approved copy tables in
 * `docs/specs/next-jam-read-only-list.md` and `docs/specs/list-states.md`, never read from
 * [NextJamCopy], so any change to the shipped copy fails this class.
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

    /** The 7 chips of `Lineup.default()`, written out from the approved copy (instrument strip spec, C1). */
    private val defaultChips = listOf(
        open("GTR: LIBRE", "Guitarra: libre"),
        open("GTR: LIBRE", "Guitarra: libre"),
        open("BAJO: LIBRE", "Bajo: libre"),
        open("BAT: LIBRE", "Batería: libre"),
        open("VOZ: LIBRE", "Voz: libre"),
        open("ARM: LIBRE", "Armónica: libre"),
        open("TEC: LIBRE", "Teclados: libre"),
    )

    private fun open(label: String, description: String) =
        InstrumentChipUiModel(label, description, InstrumentChipKind.OPEN_SLOT)

    private fun filled(label: String, description: String) =
        InstrumentChipUiModel(label, description, InstrumentChipKind.FILLED_SLOT)

    /**
     * An expected row (rows never expand); [positionLabel] is written out and the position read from it.
     * Handlers without a key compare equal, so `EventHandler {}` matches any.
     */
    private fun row(
        positionLabel: String,
        title: String,
        key: String,
        chips: List<InstrumentChipUiModel> = defaultChips,
    ) = SongRowUiModel(
        rowKey = seedSongs.firstOrNull { it.title == title }?.songId?.value ?: "p${positionLabel.toInt()}",
        position = positionLabel.toInt(),
        positionLabel = positionLabel,
        title = title,
        key = key,
        keyDescription = "Tonalidad $key",
        instruments = chips,
        openDetailLabel = "ver el detalle del tema",
        events = EventHandler {},
    )

    private fun jam(
        setlist: Setlist,
        status: JamStatus = JamStatus.PUBLISHED,
        date: LocalDate = LocalDate.of(2026, 10, 31),
    ) = Jam(date = date, startTime = LocalTime.of(21, 0), venue = "La Macanuda", status = status, setlist = setlist)

    private fun snapshot(upcoming: Jam?, freshness: Freshness = fetched) =
        JamsSnapshot(upcoming = upcoming, past = emptyList(), freshness = freshness)

    private val header = JamHeaderUiModel("Sábado 31 de octubre · 21:00", "La Macanuda", "En 29 días")

    private fun presenter(calendar: JamCalendar = octoberSecond) =
        NextJamPresenter(repository, calendar, FakeAdminSession(), FakeSetlistRepository())

    private suspend fun ReceiveTurbine<NextJamUiModel>.awaitAfterLoading(): NextJamUiModel =
        awaitMatching { it !is NextJamUiModel.Loading }

    /** Skips models until one matches: a re-subscription may recompose once with the same model. */
    private suspend fun ReceiveTurbine<NextJamUiModel>.awaitMatching(
        predicate: (NextJamUiModel) -> Boolean,
    ): NextJamUiModel {
        var item = awaitItem()
        while (!predicate(item)) item = awaitItem()
        return item
    }

    private val octoberSecondNoon = Instant.parse("2026-10-02T15:00:00Z")

    private val loading = NextJamUiModel.Loading("Cargando la próxima jam")

    private val noUpcomingJam = NextJamUiModel.NoUpcomingJam(
        EmptyStateUiModel(
            "Todavía no hay fecha",
            "La próxima jam todavía no tiene fecha. Cuando se confirme, la vas a ver acá.",
        ),
        staleness = null,
    )

    private val emptySetlist = SetlistUiModel.Empty(
        EmptyStateUiModel(
            "Todavía no hay temas",
            "La lista está publicada pero todavía no tiene temas. ¿Tenés uno en mente? Contáselo a la organización.",
        ),
    )

    private val offlineError = NextJamUiModel.Failed(
        ListErrorUiModel(
            "No pudimos cargar la próxima jam",
            "No hay conexión y todavía no hay nada guardado. Revisá los datos o el wifi y probá de nuevo.",
            "Reintentar",
            EventHandler {},
        ),
    )

    private val otherError = NextJamUiModel.Failed(
        ListErrorUiModel(
            "No pudimos cargar la próxima jam",
            "Algo falló al leer los datos. Probá de nuevo en un rato; si sigue fallando, avisale a la organización.",
            "Reintentar",
            EventHandler {},
        ),
    )

    @Test
    fun `loading, then a published jam with its header and every row`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Available(seedSongs))))
            val expectedRows = listOf(
                row("01", "Sweet Little Angel", "B"),
                row("02", "Walking Thru the Park", "A"),
                row("03", "Dust My Broom", "D"),
                row("04", "Blues Del Politico", "C"),
                row("05", "The Thrill Is Gone", "Bm"),
                row("06", "Blues del Equipaje", "A"),
                row("07", "Café Madrid", "G"),
                row("08", "Got My Mojo Working", "E"),
                row("09", "Messin' With the Kid", "C"),
                row("10", "Crossroads", "A"),
                row("11", "The Score", "C"),
                row("12", "Blues de Rosario", "E"),
                row("13", "Tres Palabras", "A"),
            )
            assertEquals(
                NextJamUiModel.Jam(header, SetlistUiModel.Songs(expectedRows, null, bar(13)), staleness = null),
                awaitItem(),
            )
        }
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `no upcoming jam after a fetch, then the jam appears`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(upcoming = null))
            assertEquals(
                noUpcomingJam,
                awaitItem(),
            )
            repository.snapshots.emit(snapshot(jam(Setlist.Available(seedSongs.take(1)))))
            assertEquals(
                NextJamUiModel.Jam(
                    header,
                    SetlistUiModel.Songs(
                        listOf(row("01", "Sweet Little Angel", "B")),
                        null,
                        bar(1),
                    ),
                    staleness = null,
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `nothing ever fetched is loading while reading and the error once a read failed`() = runTest {
        assertEquals(loading, snapshot(null, neverFetched).toUiModel(LocalDate.of(2026, 10, 2)))
        val offline = Freshness(fetchedAt = null, lastFailure = DataFailure.Offline, isRefreshing = false)
        assertEquals(offlineError, snapshot(null, offline).toUiModel(LocalDate.of(2026, 10, 2)))

        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(null, neverFetched))
            repository.snapshots.emit(snapshot(null, fetched))
            // Every model before the fetched snapshot is Loading, never "no jam".
            assertEquals(noUpcomingJam, awaitAfterLoading())
        }
    }

    @Test
    fun `skeleton, then the offline error, then Retry re-subscribes and refreshes, then the jam`() = runTest {
        val offline = Freshness(fetchedAt = null, lastFailure = DataFailure.Offline, isRefreshing = false)
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(null, offline))
            val failed = awaitItem()
            assertEquals(offlineError, failed)
            assertEquals(1, repository.subscriptions)
            assertEquals(0, repository.refreshCalls)

            (failed as NextJamUiModel.Failed).error.events(ListErrorUiModel.Event.Retry)
            // The refresh is running: the skeleton again, not the error.
            repository.snapshots.emit(snapshot(null, offline.copy(isRefreshing = true)))
            assertEquals(loading, awaitMatching { it != failed })
            assertEquals(1, repository.refreshCalls)
            assertEquals(2, repository.subscriptions)

            repository.snapshots.emit(snapshot(jam(Setlist.Available(seedSongs.take(1)))))
            assertEquals(
                NextJamUiModel.Jam(
                    header,
                    SetlistUiModel.Songs(listOf(row("01", "Sweet Little Angel", "B")), null, bar(1)),
                    staleness = null,
                ),
                awaitItem(),
            )
        }
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `the notice appears when a refresh fails, says refreshing on Retry, and goes on success`() = runTest {
        val cached = jam(Setlist.Available(seedSongs.take(1)))
        val twoHoursAgo = Instant.parse("2026-10-02T13:00:00Z")
        val failedOffline = Freshness(twoHoursAgo, DataFailure.Offline, isRefreshing = false)
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(cached, failedOffline))
            val stale = awaitItem() as NextJamUiModel.Jam
            val notice = checkNotNull(stale.staleness)
            assertEquals("Sin conexión", notice.title)
            assertEquals("Mostrando lo guardado hace 2 horas.", notice.detail)
            assertEquals("Reintentar", notice.retryLabel)
            // The header and rows are still drawn, filter bar included.
            assertEquals(header, stale.header)
            assertEquals(listOf(row("01", "Sweet Little Angel", "B")), stale.rows())
            assertEquals(bar(1), (stale.setlist as SetlistUiModel.Songs).filterBar)

            notice.events(StalenessNoticeUiModel.Event.Retry)
            repository.snapshots.emit(snapshot(cached, failedOffline.copy(isRefreshing = true)))
            val refreshing = awaitMatching { it != stale } as NextJamUiModel.Jam
            assertEquals("Actualizando…", refreshing.staleness?.detail)
            assertEquals(null, refreshing.staleness?.retryLabel)
            assertEquals(1, repository.refreshCalls)

            repository.snapshots.emit(snapshot(cached, Freshness(octoberSecondNoon, null, isRefreshing = false)))
            assertEquals(null, (awaitItem() as NextJamUiModel.Jam).staleness)
        }
    }

    @Test
    fun `an earlier model's Retry handler still re-subscribes and refreshes`() = runTest {
        val offline = Freshness(fetchedAt = null, lastFailure = DataFailure.Offline, isRefreshing = false)
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(null, offline))
            val first = awaitItem() as NextJamUiModel.Failed
            first.error.events(ListErrorUiModel.Event.Retry)
            repository.snapshots.emit(snapshot(null, offline.copy(isRefreshing = true)))
            assertEquals(loading, awaitMatching { it != first })
            repository.snapshots.emit(snapshot(null, offline.copy(lastFailure = DataFailure.Service("internal"))))
            assertEquals(otherError, awaitItem())

            // The first model's handler, not the latest one's.
            first.error.events(ListErrorUiModel.Event.Retry)
            repository.snapshots.emit(snapshot(null, fetched))
            assertEquals(noUpcomingJam, awaitMatching { it != otherError })
            assertEquals(2, repository.refreshCalls)
            assertEquals(3, repository.subscriptions)
        }
    }

    @Test
    fun `a published setlist with no song is the empty block, a filter with no match is not`() = runTest {
        val today = LocalDate.of(2026, 10, 2)
        assertEquals(
            NextJamUiModel.Jam(header, emptySetlist, staleness = null),
            snapshot(jam(Setlist.Available(emptyList()))).toUiModel(today),
        )
        // Song 1 has no open harmonica slot: the filter's own no-results, rows empty, bar kept.
        val filtered = snapshot(jam(Setlist.Available(listOf(seedSongs[0].copy(lineup = mixedLineup)))))
            .toUiModel(today, filter = setOf(Instrument.HARMONICA))
        val songs = (filtered as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs
        assertEquals(emptyList<SongRowUiModel>(), songs.rows)
        assertEquals("Ningún tema tiene cupo libre para armónica.", songs.filterBar?.noResults)
    }

    @Test
    fun `dropped rows add a note, and positions with a gap keep their labels`() {
        val today = LocalDate.of(2026, 10, 2)
        val withGap = listOf(seedSongs[0], seedSongs[2])
        val gapRows = listOf(
            row("01", "Sweet Little Angel", "B"),
            row("03", "Dust My Broom", "D"),
        )
        assertEquals(
            NextJamUiModel.Jam(header, SetlistUiModel.Songs(gapRows, null, bar(2)), staleness = null),
            snapshot(jam(Setlist.Available(withGap, droppedRows = 0))).toUiModel(today),
        )
        assertEquals(
            NextJamUiModel.Jam(
                header,
                SetlistUiModel.Songs(gapRows, "Falta 1 tema: no se pudo leer.", bar(2)),
                staleness = null,
            ),
            snapshot(jam(Setlist.Available(withGap, droppedRows = 1))).toUiModel(today),
        )
        assertEquals(
            NextJamUiModel.Jam(
                header,
                SetlistUiModel.Songs(gapRows, "Faltan 2 temas: no se pudieron leer.", bar(2)),
                staleness = null,
            ),
            snapshot(jam(Setlist.Available(withGap, droppedRows = 2))).toUiModel(today),
        )
        assertEquals(
            // An empty setlist is the empty block, with no filter bar: not the filter's no-results.
            NextJamUiModel.Jam(header, emptySetlist, staleness = null),
            snapshot(jam(Setlist.Available(emptyList()))).toUiModel(today),
        )
    }

    @Test
    fun `time remaining uses today in Buenos Aires, not UTC`() = runTest {
        val upcoming = snapshot(jam(Setlist.Available(seedSongs.take(1))))

        // 23:30 in Buenos Aires on 30 October is already 31 October in UTC.
        moleculeFlow(RecompositionMode.Immediate) {
            presenter(calendarAt("2026-10-31T02:30:00Z")).present(NextJamPresenter.Params())
        }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(upcoming)
            assertEquals("Mañana", (awaitItem() as NextJamUiModel.Jam).header.timeRemaining)
        }

        // 20:00 in Buenos Aires on the jam's own date.
        moleculeFlow(RecompositionMode.Immediate) {
            presenter(calendarAt("2026-10-31T23:00:00Z")).present(NextJamPresenter.Params())
        }.test {
            assertEquals("Esta noche", (awaitAfterLoading() as NextJamUiModel.Jam).header.timeRemaining)
        }
    }

    @Test
    fun `each row carries its strip, slots in column order and then the extras`() {
        val mixed = Lineup(
            listOf(
                Slot(Instrument.GUITAR),
                Slot(Instrument.GUITAR, "Tincho"),
                Slot(Instrument.BASS, "Nico"),
                Slot(Instrument.DRUMS),
                Slot(Instrument.VOCALS),
                Slot(Instrument.HARMONICA, "Mono"),
            ),
        )
        val withExtra = seedSongs[0].copy(lineup = mixed, extraParticipants = listOf(ExtraParticipant("Juan", "saxo")))
        val emptyLineup = seedSongs[1].copy(lineup = Lineup(emptyList()))
        val expectedRows = listOf(
            row(
                "01",
                "Sweet Little Angel",
                "B",
                listOf(
                    open("GTR: LIBRE", "Guitarra: libre"),
                    filled("Gtr: Tincho", "Guitarra: Tincho"),
                    filled("Bajo: Nico", "Bajo: Nico"),
                    open("BAT: LIBRE", "Batería: libre"),
                    open("VOZ: LIBRE", "Voz: libre"),
                    filled("Arm: Mono", "Armónica: Mono"),
                    InstrumentChipUiModel("+ saxo: Juan", "Otros: saxo, Juan", InstrumentChipKind.EXTRA),
                ),
            ),
            row("02", "Walking Thru the Park", "A", emptyList()),
        )
        val model = snapshot(
            jam(Setlist.Available(listOf(withExtra, emptyLineup))),
        ).toUiModel(LocalDate.of(2026, 10, 2))
        // Song 1 is open for guitar, drums and vocals; song 2 has no slot at all.
        val counts = listOf(1, 0, 1, 1, 0, 0)
        assertEquals(
            NextJamUiModel.Jam(header, SetlistUiModel.Songs(expectedRows, null, bar(2, counts)), staleness = null),
            model,
        )
        // The extra is never open: the open chips are exactly the lineup's open slots (D-18).
        val rows = ((model as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows
        val chips = rows[0].instruments
        assertEquals(mixed.openSlots.size, chips.count { it.isOpen })
    }

    private val mixedLineup = Lineup(
        listOf(
            Slot(Instrument.GUITAR),
            Slot(Instrument.GUITAR, "Tincho"),
            Slot(Instrument.BASS, "Nico"),
            Slot(Instrument.DRUMS),
            Slot(Instrument.VOCALS),
            Slot(Instrument.HARMONICA, "Mono"),
        ),
    )

    private fun NextJamUiModel.rows() = ((this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows

    private fun NextJamUiModel.songRows(): List<SongRowUiModel> =
        ((this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows

    @Test
    fun `OpenDetail on row 2 calls onOpenSong with the jam date, its song id and position 2`() = runTest {
        val opened = mutableListOf<Triple<LocalDate, SongId, Int>>()
        val params = NextJamPresenter.Params { date, songId, position -> opened += Triple(date, songId, position) }
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Available(seedSongs.take(3)))))
            val rows = awaitItem().songRows()
            assertEquals("ver el detalle del tema", rows[1].openDetailLabel)
            rows[1].events(SongRowUiModel.Event.OpenDetail)
            assertEquals(listOf(Triple(LocalDate.of(2026, 10, 31), SongId("walking-thru-the-park"), 2)), opened)
            // Opening is navigation only: no refresh.
            assertEquals(0, repository.refreshCalls)
        }
    }

    @Test
    fun `an earlier model's OpenDetail handler calls the current onOpenSong`() = runTest {
        val calls = mutableListOf<String>()
        var params by mutableStateOf(NextJamPresenter.Params { _, _, position -> calls += "first $position" })
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Available(seedSongs.take(2)))))
            val first = awaitItem().songRows()

            params = NextJamPresenter.Params { _, _, position -> calls += "second $position" }
            awaitItem()
            // The first model's handler, not the latest one's.
            first[0].events(SongRowUiModel.Event.OpenDetail)
            assertEquals(listOf("second 1"), calls)
        }
    }
}
