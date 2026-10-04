package com.bbbjam.feature.nextjam

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

    private val hint = "Para tocar, anotate en la jam: la organización te suma a un tema."

    private fun openLine(instrument: String, description: String) =
        LineupLineUiModel(instrument, "LIBRE", description, InstrumentChipKind.OPEN_SLOT)

    private fun filledLine(instrument: String, name: String, description: String) =
        LineupLineUiModel(instrument, name, description, InstrumentChipKind.FILLED_SLOT)

    /** The expanded panel of `Lineup.default()`, written out from the approved copy (row expansion spec, C1). */
    private val defaultPanel = LineupPanelUiModel(
        openSlots = listOf(
            openLine("Guitarra", "Guitarra: libre"),
            openLine("Guitarra", "Guitarra: libre"),
            openLine("Bajo", "Bajo: libre"),
            openLine("Batería", "Batería: libre"),
            openLine("Voz", "Voz: libre"),
            openLine("Armónica", "Armónica: libre"),
            openLine("Teclados", "Teclados: libre"),
        ),
        filledSlots = emptyList(),
        extras = emptyList(),
        noOpenSlotsNote = null,
        hint = hint,
    )

    private val emptyPanel = LineupPanelUiModel(emptyList(), emptyList(), emptyList(), "No quedan cupos libres.", null)

    /** Scenario 3 of the row expansion spec: open first, then filled, then the extra. */
    private val mixedPanel = LineupPanelUiModel(
        openSlots = listOf(
            openLine("Guitarra", "Guitarra: libre"),
            openLine("Batería", "Batería: libre"),
            openLine("Voz", "Voz: libre"),
        ),
        filledSlots = listOf(
            filledLine("Guitarra", "Tincho", "Guitarra: Tincho"),
            filledLine("Bajo", "Nico", "Bajo: Nico"),
            filledLine("Armónica", "Mono", "Armónica: Mono"),
        ),
        extras = listOf(LineupLineUiModel("+ saxo", "Juan", "Otros: saxo, Juan", InstrumentChipKind.EXTRA)),
        noOpenSlotsNote = null,
        hint = hint,
    )

    /**
     * An expected collapsed row; [positionLabel] is written out and the position read from it.
     * Handlers without a key compare equal, so `EventHandler {}` matches any.
     */
    private fun row(
        positionLabel: String,
        title: String,
        key: String,
        chips: List<InstrumentChipUiModel> = defaultChips,
        panel: LineupPanelUiModel = defaultPanel,
    ) = SongRowUiModel(
        position = positionLabel.toInt(),
        positionLabel = positionLabel,
        title = title,
        key = key,
        keyDescription = "Tonalidad $key",
        instruments = chips,
        artist = "Someone",
        isExpanded = false,
        stateDescription = "contraído",
        toggleLabel = "ver los cupos",
        lineup = panel,
        events = EventHandler {},
    )

    private fun SongRowUiModel.expanded() =
        copy(isExpanded = true, stateDescription = "expandido", toggleLabel = "ocultar los cupos")

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
            assertEquals(NextJamUiModel.Jam(header, SetlistUiModel.Songs(expectedRows, null, bar(13))), awaitItem())
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
                        listOf(row("01", "Sweet Little Angel", "B")),
                        null,
                        bar(1),
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
            row("01", "Sweet Little Angel", "B"),
            row("03", "Dust My Broom", "D"),
        )
        assertEquals(
            NextJamUiModel.Jam(header, SetlistUiModel.Songs(gapRows, null, bar(2))),
            snapshot(jam(Setlist.Available(withGap, droppedRows = 0))).toUiModel(today),
        )
        assertEquals(
            NextJamUiModel.Jam(header, SetlistUiModel.Songs(gapRows, "Falta 1 tema: no se pudo leer.", bar(2))),
            snapshot(jam(Setlist.Available(withGap, droppedRows = 1))).toUiModel(today),
        )
        assertEquals(
            NextJamUiModel.Jam(header, SetlistUiModel.Songs(gapRows, "Faltan 2 temas: no se pudieron leer.", bar(2))),
            snapshot(jam(Setlist.Available(withGap, droppedRows = 2))).toUiModel(today),
        )
        assertEquals(
            // An empty setlist draws no filter bar: that is the empty case, not no-results.
            NextJamUiModel.Jam(header, SetlistUiModel.Songs(emptyList(), null, filterBar = null)),
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
                panel = mixedPanel,
            ),
            row("02", "Walking Thru the Park", "A", emptyList(), emptyPanel),
        )
        val model = snapshot(
            jam(Setlist.Available(listOf(withExtra, emptyLineup))),
        ).toUiModel(LocalDate.of(2026, 10, 2))
        // Song 1 is open for guitar, drums and vocals; song 2 has no slot at all.
        val counts = listOf(1, 0, 1, 1, 0, 0)
        assertEquals(NextJamUiModel.Jam(header, SetlistUiModel.Songs(expectedRows, null, bar(2, counts))), model)
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

    private fun NextJamUiModel.expandedPositions() = rows().filter { it.isExpanded }.map { it.position }

    private fun NextJamUiModel.toggle(position: Int) =
        rows().single { it.position == position }.events(SongRowUiModel.Event.ToggleExpanded)

    @Test
    fun `rows start collapsed, a tap expands only that row, and two rows stay expanded at once`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(NextJamUiModel.Loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Available(seedSongs.take(3)))))
            val first = awaitItem()
            assertEquals(
                NextJamUiModel.Jam(
                    header,
                    SetlistUiModel.Songs(
                        listOf(
                            row("01", "Sweet Little Angel", "B"),
                            row("02", "Walking Thru the Park", "A"),
                            row("03", "Dust My Broom", "D"),
                        ),
                        null,
                        bar(3),
                    ),
                ),
                first,
            )

            first.toggle(1)
            val one = awaitItem()
            assertEquals(
                listOf(
                    row("01", "Sweet Little Angel", "B").expanded(),
                    row("02", "Walking Thru the Park", "A"),
                    row("03", "Dust My Broom", "D"),
                ),
                one.rows(),
            )

            one.toggle(3)
            val two = awaitItem()
            assertEquals(listOf(1, 3), two.expandedPositions())
            assertEquals(listOf("expandido", "contraído", "expandido"), two.rows().map { it.stateDescription })
            assertEquals(
                listOf("ocultar los cupos", "ver los cupos", "ocultar los cupos"),
                two.rows().map { it.toggleLabel },
            )

            // Scenario 2: collapsing row 3 leaves row 1 expanded.
            two.toggle(3)
            assertEquals(listOf(1), awaitItem().expandedPositions())
        }
    }

    @Test
    fun `expansion follows the position through a refresh, and a new jam starts collapsed`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(NextJamUiModel.Loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Available(seedSongs.take(3)))))
            awaitItem().toggle(2)
            assertEquals(listOf(2), awaitItem().expandedPositions())

            // Scenario 4: the refresh drops row 1 and fills a name on row 2.
            val filledRow2 = seedSongs[1].copy(lineup = mixedLineup)
            repository.snapshots.emit(snapshot(jam(Setlist.Available(listOf(filledRow2, seedSongs[2])))))
            assertEquals(
                listOf(
                    row("02", "Walking Thru the Park", "A", mixedChips, mixedPanelWithoutExtra)
                        .expanded(),
                    row("03", "Dust My Broom", "D"),
                ),
                awaitItem().rows(),
            )

            // The next upcoming jam: same positions, another date, every row collapsed.
            val nextJam = jam(Setlist.Available(seedSongs.take(3)), date = LocalDate.of(2026, 11, 28))
            repository.snapshots.emit(snapshot(nextJam))
            val next = awaitItem()
            assertEquals(emptyList<Int>(), next.expandedPositions())
            next.toggle(2)
            assertEquals(listOf(2), awaitItem().expandedPositions())
        }
    }

    @Test
    fun `an earlier model's handler still writes through the same state after a refresh`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(NextJamUiModel.Loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Available(seedSongs.take(2)))))
            val stale = awaitItem()
            repository.snapshots.emit(
                snapshot(jam(Setlist.Available(listOf(seedSongs[0].copy(lineup = mixedLineup), seedSongs[1])))),
            )
            assertEquals(emptyList<Int>(), awaitItem().expandedPositions())

            // Handlers have no key, so Compose may keep the first model's one; it must still work.
            stale.toggle(1)
            val after = awaitItem()
            assertEquals(listOf(1), after.expandedPositions())
            assertEquals(mixedPanelWithoutExtra, after.rows()[0].lineup)
        }
    }

    @Test
    fun `an expanded row carries the artist and its lineup with open slots before filled ones`() {
        val song = seedSongs[0].copy(
            artist = "B.B. King",
            lineup = mixedLineup,
            extraParticipants = listOf(ExtraParticipant("Juan", "saxo")),
        )
        val date = LocalDate.of(2026, 10, 31)
        val model = snapshot(jam(Setlist.Available(listOf(song, seedSongs[1]))))
            .toUiModel(LocalDate.of(2026, 10, 2), ExpandedRows(date, setOf(1)))
        val expanded = model.rows()[0]
        assertEquals(true, expanded.isExpanded)
        assertEquals("B.B. King", expanded.artist)
        assertEquals(mixedPanel, expanded.lineup)
        // Every open line comes before every filled one, and the extra is last and never open.
        val kinds = (expanded.lineup.openSlots + expanded.lineup.filledSlots + expanded.lineup.extras).map { it.kind }
        assertEquals(
            List(3) { InstrumentChipKind.OPEN_SLOT } + List(3) { InstrumentChipKind.FILLED_SLOT } +
                InstrumentChipKind.EXTRA,
            kinds,
        )
        assertEquals(false, model.rows()[1].isExpanded)
        // Another jam's date never expands this jam's rows.
        val otherJam = snapshot(jam(Setlist.Available(listOf(song))))
            .toUiModel(LocalDate.of(2026, 10, 2), ExpandedRows(LocalDate.of(2026, 7, 25), setOf(1)))
        assertEquals(false, otherJam.rows()[0].isExpanded)
    }

    /** The strip of [mixedLineup], in column order (instrument strip spec, C1). */
    private val mixedChips = listOf(
        open("GTR: LIBRE", "Guitarra: libre"),
        filled("Gtr: Tincho", "Guitarra: Tincho"),
        filled("Bajo: Nico", "Bajo: Nico"),
        open("BAT: LIBRE", "Batería: libre"),
        open("VOZ: LIBRE", "Voz: libre"),
        filled("Arm: Mono", "Armónica: Mono"),
    )

    private val mixedPanelWithoutExtra get() = mixedPanel.copy(extras = emptyList())
}
