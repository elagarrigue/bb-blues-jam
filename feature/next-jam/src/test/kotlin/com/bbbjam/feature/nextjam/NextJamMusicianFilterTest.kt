package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.assign.Assignment
import com.bbbjam.core.data.testing.FakeAdminSession
import com.bbbjam.core.data.testing.FakeJamsRepository
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.filter.FilterChipUiModel
import com.bbbjam.core.ui.filter.MusicianFilterUiModel
import com.bbbjam.core.ui.filter.MusicianOptionUiModel
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.lowercaseName
import com.bbbjam.feature.nextjam.admin.AdminState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The musician filter on Próxima jam (`musician-filter`): Molecule tests of [NextJamPresenter] through the
 * model's handlers and a fake preference. Lines are compared as the user reads them, from the shipped copy.
 */
class NextJamMusicianFilterTest {
    private val repository = FakeJamsRepository()
    private val preference = FakeMusicianFilterPreference()
    private val setlist = FakeSetlistRepository()

    private val calendar = JamCalendar(
        Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC),
        JamCalendar.BUENOS_AIRES,
    )
    private val today = LocalDate.of(2026, 10, 2)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    private fun presenter(isAdmin: Boolean = false) = NextJamPresenter(
        repository,
        calendar,
        FakeAdminSession(isAdmin),
        setlist.writes(),
        setlist.sources(),
        preference,
    )

    /** Song [position]: [guitar] and [guitar2] on the guitar slots, [bass] on bass (null: open), the rest open. */
    private fun song(
        position: Int,
        guitar: String? = null,
        guitar2: String? = null,
        bass: String? = null,
        extras: List<String> = emptyList(),
    ) = JamSong(
        position = position,
        songId = SongId("song-$position"),
        title = "Tema $position",
        artist = "Someone",
        key = Key("A"),
        lineup = Lineup(
            Lineup.DEFAULT_INSTRUMENTS.mapIndexed { index, instrument ->
                Slot(instrument, listOf(guitar, guitar2, bass).getOrNull(index))
            },
        ),
        extraParticipants = extras.map { ExtraParticipant(it, "saxo") },
    )

    private fun optionText(name: String, songs: Int) =
        UiText.plural(CoreR.plurals.musician_filter_option, songs, name, songs)

    private fun summaryText(shown: Int, total: Int, name: String, instrument: Instrument? = null) = if (instrument ==
        null
    ) {
        UiText.plural(CoreR.plurals.musician_filter_summary, shown, shown, total, name)
    } else {
        UiText.plural(
            CoreR.plurals.musician_filter_summary_instrument,
            shown,
            shown,
            total,
            name,
            instrument.lowercaseName(),
        )
    }

    private fun jam(setlist: Setlist, status: JamStatus = JamStatus.PUBLISHED) =
        Jam(LocalDate.of(2026, 10, 31), LocalTime.of(21, 0), "La Macanuda", status, setlist)

    private fun snapshot(setlist: Setlist, status: JamStatus = JamStatus.PUBLISHED) =
        JamsSnapshot(upcoming = jam(setlist, status), past = emptyList(), freshness = fetched)

    private fun NextJamUiModel.songs() = (this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs

    private fun NextJamUiModel.positions() = songs().rows.map { it.position }

    private fun NextJamUiModel.block() = checkNotNull(songs().musicianFilter)

    private fun NextJamUiModel.pick(name: String) =
        block().options.single { it.name == name }.events(MusicianOptionUiModel.Event.Select)

    /** The first later model satisfying [condition]: a handler may take more than one frame to settle. */
    private suspend fun ReceiveTurbine<NextJamUiModel>.awaitUntil(
        condition: (NextJamUiModel) -> Boolean,
    ): NextJamUiModel {
        var model = awaitItem()
        while (!condition(model)) model = awaitItem()
        return model
    }

    private suspend fun ReceiveTurbine<NextJamUiModel>.start(songs: List<JamSong>): NextJamUiModel {
        assertEquals(nextJamLoading, awaitItem())
        repository.snapshots.emit(snapshot(Setlist.Available(songs)))
        return awaitItem()
    }

    /** Juan on slots of songs 2 and 5 and as an extra of 7; 13 songs. */
    private val jamSongs = (1..13).map { position ->
        when (position) {
            2 -> song(2, "Juan", "Nico", bass = "Ana")
            5 -> song(5, guitar2 = "juan  ", bass = "Ana")
            7 -> song(7, bass = "Ana", extras = listOf("JUAN"))
            else -> song(position, "Ana", bass = "Ana")
        }
    }

    @Test
    fun `choosing a name shows only its songs and the line says how many remain`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            val first = start(jamSongs)
            assertEquals((1..13).toList(), first.positions())
            assertNull(first.block().selectedName)
            // One name, first spelling in position order, whatever the case and spaces.
            assertEquals(listOf("Ana", "Juan", "Nico"), first.block().options.map { it.name })
            assertEquals(optionText("Juan", 3), first.block().options[1].label)

            first.pick("Juan")
            val chosen = awaitItem()
            assertEquals(listOf(2, 5, 7), chosen.positions())
            assertEquals("Juan", chosen.block().selectedName)
            assertEquals(summaryText(3, 13, "Juan"), chosen.block().summary)
            assertEquals(listOf("Juan"), preference.selections)

            chosen.block().events(MusicianFilterUiModel.Event.Clear)
            val cleared = awaitItem()
            assertEquals((1..13).toList(), cleared.positions())
            assertNull(cleared.block().selectedName)
            assertEquals(listOf("Juan", null), preference.selections)
        }
    }

    @Test
    fun `a name combines with an instrument, whose counts follow the name's songs`() = runTest {
        // Song 2 has Juan and an open bass; song 5 has Juan and a filled bass; song 7 has Juan as an extra.
        val songs = listOf(
            song(1, "Ana"),
            song(2, "Juan"),
            song(5, bass = "Juan"),
            song(7, bass = "Ana", extras = listOf("Juan")),
        )
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            val first = start(songs)
            val bassChip = { model: NextJamUiModel ->
                model.songs().filterBar!!.chips.single { it.label.chipName() == "Bajo" }
            }
            // Bass is open in songs 1 and 2 before any name.
            assertEquals("2", bassChip(first).count)

            first.pick("Juan")
            val juan = awaitItem()
            assertEquals(listOf(2, 5, 7), juan.positions())
            // Counts are over Juan's songs only: bass is open in song 2 alone.
            assertEquals("1", bassChip(juan).count)
            assertEquals("3", juan.songs().filterBar!!.chips.first().count)

            bassChip(juan).events(FilterChipUiModel.Event.Toggle)
            val both = awaitItem()
            assertEquals(listOf(2), both.positions())
            assertEquals(summaryText(1, 4, "Juan", Instrument.BASS), both.block().summary)
            // The count line belongs to the musician block while a name is chosen.
            assertNull(both.songs().filterBar!!.summary)
            assertNull(both.songs().filterBar!!.noResults)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a name and an instrument that match nothing together have an empty block that clears both`() = runTest {
        val songs = listOf(song(1, "Juan", bass = "Nico"), song(2, "Ana"))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            val first = start(songs)
            val bassChip = first.songs().filterBar!!.chips.single { it.label.chipName() == "Bajo" }
            bassChip.events(FilterChipUiModel.Event.Toggle)
            val bass = awaitItem()
            assertEquals(listOf(2), bass.positions())
            bass.pick("Juan")
            val empty = awaitItem()
            assertEquals(emptyList<Int>(), empty.positions())
            assertEquals(
                UiText.res(CoreR.string.musician_filter_no_results_combined, "Juan", Instrument.BASS.lowercaseName()),
                empty.block().noResults,
            )
            empty.block().events(MusicianFilterUiModel.Event.ClearAll)
            val restored = awaitUntil { it.positions() == listOf(1, 2) }
            assertNull(restored.block().selectedName)
            assertNull(preference.stored.value)
            // Only Todos is checked again.
            val checked = restored.songs().filterBar!!.chips.filter { it.isSelected }.map { it.label.chipName() }
            assertEquals(listOf("Todos"), checked)
        }
    }

    @Test
    fun `the remembered name is restored before the first list and an absent one gives the empty state`() = runTest {
        val remembered = FakeMusicianFilterPreference("juan ")
        moleculeFlow(RecompositionMode.Immediate) {
            NextJamPresenter(
                repository,
                calendar,
                FakeAdminSession(),
                setlist.writes(),
                setlist.sources(),
                remembered,
            ).present(NextJamPresenter.Params())
        }.test {
            val shown = start(jamSongs)
            // Never an unfiltered list first.
            assertEquals(listOf(2, 5, 7), shown.positions())
            assertEquals("Juan", shown.block().selectedName)
        }

        val absent = FakeMusicianFilterPreference("Pedro")
        moleculeFlow(RecompositionMode.Immediate) {
            NextJamPresenter(
                repository,
                calendar,
                FakeAdminSession(),
                setlist.writes(),
                setlist.sources(),
                absent,
            ).present(NextJamPresenter.Params())
        }.test {
            val shown = start(jamSongs)
            assertEquals(emptyList<Int>(), shown.positions())
            assertEquals("Pedro", shown.block().selectedName)
            assertEquals(UiText.res(CoreR.string.musician_filter_no_results, "Pedro"), shown.block().noResults)
            shown.block().events(MusicianFilterUiModel.Event.ClearAll)
            val all = awaitUntil { it.positions().size == 13 }
            assertEquals((1..13).toList(), all.positions())
            assertNull(absent.stored.value)
        }
    }

    @Test
    fun `a nobody-assigned setlist draws no musician block, until a name is remembered`() = runTest {
        val empty = (1..3).map { song(it, bass = null) }
        assertNull(snapshot(Setlist.Available(empty)).toUiModel(today).songs().musicianFilter)
        assertNotNull(snapshot(Setlist.Available(empty)).toUiModel(today, musician = "Juan").songs().musicianFilter)
    }

    @Test
    fun `a draft, an unavailable and an empty setlist draw no musician block`() {
        val draft = snapshot(Setlist.Available(jamSongs), JamStatus.DRAFT).toUiModel(today, musician = "Juan")
        // A musician never gets a draft jam at all (D-26), so there is no setlist to filter.
        assertEquals(true, draft is NextJamUiModel.NoUpcomingJam)
        val unavailable = snapshot(Setlist.Unavailable(SetlistProblem.INVALID_ROWS)).toUiModel(today, musician = "Juan")
        assertEquals(true, (unavailable as NextJamUiModel.Jam).setlist is SetlistUiModel.Unavailable)
        val none = snapshot(Setlist.Available(emptyList())).toUiModel(today, musician = "Juan")
        assertEquals(true, (none as NextJamUiModel.Jam).setlist is SetlistUiModel.Empty)
    }

    @Test
    fun `a sending assignment feeds the roster and the rows`() {
        val songs = listOf(song(1, "Ana"), song(2, "Ana", bass = "Nico"))
        val sending = Assignment(
            id = 1,
            jamDate = LocalDate.of(2026, 10, 31),
            songId = SongId("song-1"),
            title = "Tema 1",
            instrument = Instrument.BASS,
            ordinal = SlotPosition(1),
            musicianName = requireNotNull(MusicianName.parseOrNull("Beto")),
            state = Assignment.State.Sending,
        )
        val admin = AdminState(emptyList(), {}, {}, assignments = listOf(sending))
        val model = snapshot(Setlist.Available(songs)).toUiModel(today, admin = admin, musician = "Beto")
        assertEquals(listOf("Ana", "Beto", "Nico"), model.block().options.map { it.name })
        assertEquals(listOf(1), model.positions())
        assertEquals(summaryText(1, 2, "Beto"), model.block().summary)
    }
}
