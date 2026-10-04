package com.bbbjam.feature.nextjam

import androidx.compose.runtime.saveable.SaverScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
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
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.filter.FilterChipUiModel
import com.bbbjam.core.ui.filter.InstrumentFilterBarUiModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The instrument filter on Próxima jam (`instrument-filter-chips`, multi-select with OR semantics):
 * Molecule tests of [NextJamPresenter] through its chips. Strings are written out from the approved
 * copy (C1), never read from the shipped copy.
 */
class NextJamFilterTest {
    private val repository = FakeJamsRepository()

    private val calendar = JamCalendar(
        Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC),
        JamCalendar.BUENOS_AIRES,
    )

    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    private fun presenter() = NextJamPresenter(repository, calendar)

    private fun jam(setlist: Setlist, date: LocalDate = LocalDate.of(2026, 10, 31)) = Jam(
        date = date,
        startTime = LocalTime.of(21, 0),
        venue = "La Macanuda",
        status = JamStatus.PUBLISHED,
        setlist = setlist,
    )

    private fun snapshot(upcoming: Jam) = JamsSnapshot(upcoming = upcoming, past = emptyList(), freshness = fetched)

    private fun song(position: Int, lineup: Lineup) = JamSong(
        position = position,
        songId = SongId("song-$position"),
        title = "Tema $position",
        artist = "Someone",
        key = Key("A"),
        lineup = lineup,
    )

    private fun NextJamUiModel.rows() = ((this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows

    private fun NextJamUiModel.expandedPositions() = rows().filter { it.isExpanded }.map { it.position }

    private fun NextJamUiModel.toggle(position: Int) =
        rows().single { it.position == position }.events(SongRowUiModel.Event.ToggleExpanded)

    /** The default seven slots, open only for [open]; every other slot holds a name. */
    private fun openFor(vararg open: Instrument) =
        Lineup(Lineup.DEFAULT_INSTRUMENTS.map { Slot(it, if (it in open) null else "Nombre") })

    /**
     * Seven songs, each instrument open on its own set of positions: guitarra {1, 7}, bajo {2, 3},
     * batería {4, 6}, voz {3, 6}, armónica {4}, teclados {1}. Song 1 has its bass filled and
     * `Juan (bajo)` in Otros (D-18); song 5 has every slot filled.
     */
    private val filterSongs = listOf(
        openFor(Instrument.GUITAR, Instrument.KEYBOARDS),
        openFor(Instrument.BASS),
        openFor(Instrument.BASS, Instrument.VOCALS),
        openFor(Instrument.DRUMS, Instrument.HARMONICA),
        openFor(),
        openFor(Instrument.VOCALS, Instrument.DRUMS),
        openFor(Instrument.GUITAR),
    ).mapIndexed { index, lineup ->
        val song = song(index + 1, lineup)
        if (index == 0) song.copy(extraParticipants = listOf(ExtraParticipant("Juan", "bajo"))) else song
    }

    private val filterCounts = listOf(2, 2, 2, 2, 1, 1)

    private fun NextJamUiModel.bar() = ((this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).filterBar!!

    private fun NextJamUiModel.positions() = rows().map { it.position }

    private fun NextJamUiModel.checked() = bar().chips.filter { it.isSelected }.map { it.label }

    private fun NextJamUiModel.tap(label: String) =
        bar().chips.single { it.label == label }.events(FilterChipUiModel.Event.Toggle)

    private suspend fun ReceiveTurbine<NextJamUiModel>.start(songs: List<JamSong> = filterSongs): NextJamUiModel {
        assertEquals(NextJamUiModel.Loading, awaitItem())
        repository.snapshots.emit(snapshot(jam(Setlist.Available(songs))))
        return awaitItem()
    }

    @Test
    fun `the bar counts each instrument, Todos is checked and every row shows`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            val first = start()
            assertEquals(bar(7, filterCounts), first.bar())
            assertEquals((1..7).toList(), first.positions())
        }
    }

    @Test
    fun `each of the six instruments shows exactly its songs, and deselecting restores all`() = runTest {
        val expected = listOf(
            Triple("Guitarra", listOf(1, 7), "2 de 7 temas con cupo libre para guitarra"),
            Triple("Bajo", listOf(2, 3), "2 de 7 temas con cupo libre para bajo"),
            Triple("Batería", listOf(4, 6), "2 de 7 temas con cupo libre para batería"),
            Triple("Voz", listOf(3, 6), "2 de 7 temas con cupo libre para voz"),
            Triple("Armónica", listOf(4), "1 de 7 temas con cupo libre para armónica"),
            Triple("Teclados", listOf(1), "1 de 7 temas con cupo libre para teclados"),
        )
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            var model = start()
            expected.forEach { (label, positions, summary) ->
                model.tap(label)
                model = awaitItem()
                assertEquals(bar(7, filterCounts, setOf(label), summary = summary), model.bar())
                assertEquals(positions, model.positions())
                // The number of rows is the selected chip's count.
                assertEquals(model.bar().chips.single { it.label == label }.count, "${model.rows().size}")

                // Tapping a checked chip unchecks it (multi-select approval of F1).
                model.tap(label)
                model = awaitItem()
                assertEquals(listOf("Todos"), model.checked())
                assertEquals((1..7).toList(), model.positions())
            }
        }
    }

    @Test
    fun `instruments add up with OR, the line joins them with o, and Todos clears`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            start().tap("Bajo")
            val bass = awaitItem()
            assertEquals(listOf(2, 3), bass.positions())

            // Song 6 is open for voz only: it joins under bajo + voz.
            bass.tap("Voz")
            val bassOrVocals = awaitItem()
            assertEquals(listOf("Bajo", "Voz"), bassOrVocals.checked())
            assertEquals(listOf(2, 3, 6), bassOrVocals.positions())
            assertEquals("3 de 7 temas con cupo libre para bajo o voz", bassOrVocals.bar().summary)

            // Chosen after the others, guitarra still comes first in the line (chip order).
            bassOrVocals.tap("Guitarra")
            val three = awaitItem()
            assertEquals(listOf("Guitarra", "Bajo", "Voz"), three.checked())
            assertEquals(listOf(1, 2, 3, 6, 7), three.positions())
            assertEquals("5 de 7 temas con cupo libre para guitarra, bajo o voz", three.bar().summary)

            three.tap("Bajo")
            val guitarOrVocals = awaitItem()
            assertEquals(listOf(1, 3, 6, 7), guitarOrVocals.positions())
            assertEquals("4 de 7 temas con cupo libre para guitarra o voz", guitarOrVocals.bar().summary)

            guitarOrVocals.tap("Todos")
            val all = awaitItem()
            assertEquals(bar(7, filterCounts), all.bar())
            assertEquals((1..7).toList(), all.positions())
        }
    }

    @Test
    fun `an extra never counts and an all-filled song shows only under Todos`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            val first = start()
            // Song 5 (every slot filled) is listed under Todos.
            assertEquals(true, 5 in first.positions())
            // Song 1 has `Juan (bajo)` in Otros and its bass slot filled: not a bass song (D-18).
            first.tap("Bajo")
            var model = awaitItem()
            assertEquals(false, 1 in model.positions())
            // Song 5 is listed under no instrument.
            instrumentChipLabels.forEach { label ->
                model.tap("Todos")
                model = awaitItem()
                model.tap(label)
                model = awaitItem()
                assertEquals(listOf(label), model.checked())
                assertEquals(false, 5 in model.positions())
            }
        }
    }

    @Test
    fun `a zero-count chip leads to no results naming it, and both clears restore every row`() = runTest {
        // Songs 1, 2, 3, 5 and 7: armónica and batería are open nowhere.
        val songs = filterSongs.filterIndexed { index, _ -> index in listOf(0, 1, 2, 4, 6) }
        val counts = listOf(2, 2, 0, 1, 0, 1)
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            val first = start(songs)
            assertEquals("0", first.bar().chips[5].count)

            first.tap("Armónica")
            val none = awaitItem()
            assertEquals(
                bar(5, counts, setOf("Armónica"), noResults = "Ningún tema tiene cupo libre para armónica."),
                none.bar(),
            )
            assertEquals(emptyList<Int>(), none.positions())

            none.bar().events(InstrumentFilterBarUiModel.Event.Clear)
            val cleared = awaitItem()
            assertEquals(bar(5, counts), cleared.bar())
            assertEquals(listOf(1, 2, 3, 5, 7), cleared.positions())

            // Two instruments with no song: the message names both.
            cleared.tap("Armónica")
            awaitItem().tap("Batería")
            val bothNone = awaitItem()
            assertEquals("Ningún tema tiene cupo libre para batería o armónica.", bothNone.bar().noResults)
            assertEquals(null, bothNone.bar().summary)

            bothNone.tap("Todos")
            assertEquals(listOf(1, 2, 3, 5, 7), awaitItem().positions())
        }
    }

    @Test
    fun `tapping Todos with nothing selected changes nothing`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            val first = start()
            first.tap("Todos")
            expectNoEvents()
            // A refresh (song 7 dropped) still shows every song with Todos checked.
            repository.snapshots.emit(snapshot(jam(Setlist.Available(filterSongs.take(6)))))
            val after = awaitItem()
            assertEquals(listOf("Todos"), after.checked())
            assertEquals((1..6).toList(), after.positions())
        }
    }

    @Test
    fun `filtering keeps every row's expansion, shown or hidden`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            start().toggle(3)
            awaitItem().toggle(1)
            val expanded = awaitItem()
            assertEquals(listOf(1, 3), expanded.expandedPositions())

            // Row 1 is hidden under bajo; row 3 stays expanded.
            expanded.tap("Bajo")
            val bass = awaitItem()
            assertEquals(listOf(2, 3), bass.positions())
            assertEquals(listOf(3), bass.expandedPositions())

            bass.tap("Todos")
            assertEquals(listOf(1, 3), awaitItem().expandedPositions())
        }
    }

    @Test
    fun `the selection survives a refresh and a new upcoming jam`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            start().tap("Bajo")
            assertEquals(listOf(2, 3), awaitItem().positions())

            // A refresh fills song 3's bass.
            val refreshed = filterSongs.mapIndexed { index, song ->
                if (index == 2) song.copy(lineup = openFor(Instrument.VOCALS)) else song
            }
            repository.snapshots.emit(snapshot(jam(Setlist.Available(refreshed))))
            val afterRefresh = awaitItem()
            assertEquals(listOf(2), afterRefresh.positions())
            assertEquals("1 de 7 temas con cupo libre para bajo", afterRefresh.bar().summary)
            assertEquals(listOf("Bajo"), afterRefresh.checked())

            // F5: the filter describes the musician, not the jam.
            val nextJam = jam(Setlist.Available(filterSongs), date = LocalDate.of(2026, 11, 28))
            repository.snapshots.emit(snapshot(nextJam))
            val next = awaitItem()
            assertEquals(listOf("Bajo"), next.checked())
            assertEquals(listOf(2, 3), next.positions())
        }
    }

    @Test
    fun `an earlier model's chip handler still writes through the current selection`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            val stale = start()
            repository.snapshots.emit(snapshot(jam(Setlist.Available(filterSongs.take(6)))))
            assertEquals((1..6).toList(), awaitItem().positions())

            // Handlers have no key, so Compose may keep the first model's chip; it must still work.
            stale.tap("Bajo")
            assertEquals(listOf(2, 3), awaitItem().positions())

            // A stale chip adds to the current selection; it never resets it to the set it was built with.
            stale.tap("Voz")
            val both = awaitItem()
            assertEquals(listOf("Bajo", "Voz"), both.checked())
            assertEquals(listOf(2, 3, 6), both.positions())
        }
    }

    @Test
    fun `the selection saver restores the same instruments`() {
        val selected = setOf(Instrument.VOCALS, Instrument.BASS)
        val saved = with(InstrumentFilterSaver) { SaverScope { true }.save(selected) }
        assertEquals(selected, InstrumentFilterSaver.restore(saved!!))
    }
}
