package com.bbbjam.feature.nextjam.overlay

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.LineupChange
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import com.bbbjam.feature.nextjam.FakeAdminSession
import com.bbbjam.feature.nextjam.FakeJamsRepository
import com.bbbjam.feature.nextjam.FakeSetlistRepository
import com.bbbjam.feature.nextjam.NextJamCopy
import com.bbbjam.feature.nextjam.NextJamPresenter
import com.bbbjam.feature.nextjam.NextJamUiModel
import com.bbbjam.feature.nextjam.SetlistUiModel
import com.bbbjam.feature.nextjam.admin.AdminState
import com.bbbjam.feature.nextjam.toUiModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `admin-set-key` Part B on Próxima jam: "Cambiar tonalidad", the optimistic key (O1) and its revert.
 * The copy is written out from the approved table, never read from [NextJamCopy].
 */
class NextJamLineupChangeTest {
    private val repository = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val setlist = FakeSetlistRepository()

    /** 2 October 2026, 12:00 in Buenos Aires. */
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
    private val today = LocalDate.of(2026, 10, 2)
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    private val crossroads = JamSong(1, SongId("crossroads"), "Crossroads", "Cream", Key("A"), Lineup.default())
    private val hoochie =
        JamSong(2, SongId("hoochie"), "Hoochie Coochie Man", "Muddy Waters", Key("E"), Lineup.default())

    private fun snapshot(songs: List<JamSong> = listOf(crossroads, hoochie)) = JamsSnapshot(
        upcoming = Jam(jamDate, LocalTime.of(21, 0), "La Macanuda", JamStatus.PUBLISHED, Setlist.Available(songs)),
        past = emptyList(),
        freshness = fetched,
    )

    private fun presenter() = NextJamPresenter(repository, calendar, session, setlist)

    private fun NextJamUiModel.rows() = ((this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows

    private fun NextJamUiModel.row(title: String) = rows().single { it.title == title }

    private fun NextJamUiModel.failures() = checkNotNull((this as NextJamUiModel.Jam).admin).failures

    private fun change(
        id: Long,
        count: Int,
        state: LineupChange.State = LineupChange.State.Sending,
        instrument: Instrument = Instrument.GUITAR,
    ) = LineupChange(id, jamDate, crossroads.songId, crossroads.title, instrument, count, state)

    private fun admin(changes: List<LineupChange>) = AdminState(emptyList(), {}, {}, lineupChanges = changes)

    @Test
    fun `Sending overlays strip filter and counts and Failed reverts`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(listOf(crossroads)))
            val original = expectMostRecentItem().row("Crossroads")
            setlist.lineupChanges.value = listOf(change(1, 0))
            val sending = awaitItem().row("Crossroads")
            assertEquals(original.instruments.size - 2, sending.instruments.size)
            assertEquals("Guardando…", sending.admin?.saveStatus)
            val filtered = snapshot(
                listOf(crossroads),
            ).toUiModel(today, filter = setOf(Instrument.GUITAR), admin = admin(setlist.lineupChanges.value))
            assertTrue(filtered.rows().isEmpty())
            val bar = ((filtered as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).filterBar
            assertEquals("0", checkNotNull(bar).chips.single { it.label == "Guitarra" }.count)
            setlist.lineupChanges.value =
                listOf(change(1, 0, LineupChange.State.Failed(WriteOutcome.Rejected("slot_filled"))))
            val failed = awaitItem()
            assertEquals(original.instruments, failed.row("Crossroads").instruments)
            assertNull(failed.row("Crossroads").admin?.saveStatus)
            assertEquals("No se pudo cambiar la formación de «Crossroads»", failed.failures().single().title)
            assertEquals(
                "Ese cupo tiene un músico anotado. Liberalo antes de sacarlo.",
                failed.failures().single().message,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a tab without the column for a slot reports why in Spanish`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(listOf(crossroads)))
            expectMostRecentItem()
            setlist.lineupChanges.value = listOf(
                change(
                    1,
                    2,
                    LineupChange.State.Failed(WriteOutcome.Rejected("instrument_not_expandable")),
                    Instrument.BASS,
                ),
            )
            assertEquals(
                "Esta hoja todavía no tiene columnas para ese cupo. Agregalas en el Sheet y volvé a probar.",
                awaitItem().failures().single().message,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `musician ignores changes and latest Sending per instrument wins`() = runTest {
        session.isAdmin.value = false
        setlist.lineupChanges.value = listOf(change(1, 0))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(listOf(crossroads)))
            val row = expectMostRecentItem().row("Crossroads")
            assertNull(row.admin)
            assertEquals(7, row.instruments.size)
            cancelAndIgnoreRemainingEvents()
        }
        val shown = listOf(change(8, 1), change(2, 0), change(9, 0, instrument = Instrument.BASS))
            .pendingLineup(jamDate, crossroads.songId, Lineup.default())
        assertEquals(1, shown.count(Instrument.GUITAR))
        assertEquals(0, shown.count(Instrument.BASS))
    }

    @Test
    fun `a song with no slot at all draws an empty strip`() {
        val zero = snapshot(listOf(crossroads.copy(lineup = Lineup(emptyList()))))
            .toUiModel(today, admin = admin(emptyList())).row("Crossroads")
        assertTrue(zero.instruments.isEmpty())
        assertNull(checkNotNull(zero.admin).saveStatus)
    }

    @Test
    fun `confirmed cache replaces overlay and key and lineup share one saving status`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(listOf(crossroads)))
            expectMostRecentItem()
            setlist.lineupChanges.value = listOf(change(1, 1))
            setlist.keyChanges.value =
                listOf(KeyChange(2, jamDate, crossroads.songId, crossroads.title, Key("C"), KeyChange.State.Sending))
            assertEquals("Guardando…", expectMostRecentItem().row("Crossroads").admin?.saveStatus)
            repository.snapshots.emit(
                snapshot(
                    listOf(
                        crossroads.copy(lineup = checkNotNull(crossroads.lineup.withSlotCount(Instrument.GUITAR, 1))),
                    ),
                ),
            )
            setlist.lineupChanges.value = emptyList()
            val row = expectMostRecentItem().row("Crossroads")
            assertEquals(6, row.instruments.size)
            assertEquals("Guardando…", row.admin?.saveStatus)
            setlist.keyChanges.value = emptyList()
            assertNull(expectMostRecentItem().row("Crossroads").admin?.saveStatus)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
