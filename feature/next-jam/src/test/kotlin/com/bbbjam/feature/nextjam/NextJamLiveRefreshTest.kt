package com.bbbjam.feature.nextjam

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.state.ListErrorUiModel
import com.bbbjam.core.ui.state.PullRefreshUiModel
import com.bbbjam.core.ui.state.StalenessNoticeUiModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `live-refresh-during-jam` on Próxima jam, in virtual time: the clock is [base] plus the test
 * scheduler's, so the presenter's "now" and its delays move together. The copy is written out from
 * the approved tables, never read from a `…Copy` object.
 */
class NextJamLiveRefreshTest {
    private val repository = FakeJamsRepository()
    private val setlist = FakeSetlistRepository()

    /** 2026-10-31 20:45 in Buenos Aires: inside the 21:00 jam's window. */
    private val base: Instant = Instant.parse("2026-10-31T23:45:00Z")
    private val jamDate = LocalDate.of(2026, 10, 31)

    /** Fetched long before [base], so entering the window refreshes at once. */
    private val fetched = Freshness(Instant.parse("2026-10-31T20:00:00Z"), lastFailure = null, isRefreshing = false)

    private val crossroads = JamSong(1, SongId("crossroads"), "Crossroads", "Cream", Key("A"), Lineup.default())
    private val hoochie =
        JamSong(2, SongId("hoochie"), "Hoochie Coochie Man", "Muddy Waters", Key("E"), Lineup.default())

    private fun jam(date: LocalDate = jamDate, songs: List<JamSong> = listOf(crossroads, hoochie)) =
        Jam(date, LocalTime.of(21, 0), "La Macanuda", JamStatus.PUBLISHED, Setlist.Available(songs))

    private fun snapshot(upcoming: Jam? = jam(), past: List<Jam> = emptyList(), freshness: Freshness = fetched) =
        JamsSnapshot(upcoming, past, freshness)

    private class ScheduledClock(private val start: Instant, private val elapsed: () -> Long) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = start.plusMillis(elapsed())
    }

    private fun TestScope.calendar(start: Instant = base) =
        JamCalendar(ScheduledClock(start) { testScheduler.currentTime }, JamCalendar.BUENOS_AIRES)

    /** The latest model of a presenter collected in the background; [params] is read in the composition. */
    private class Screen(var latest: NextJamUiModel? = null) {
        val model: NextJamUiModel
            get() = checkNotNull(latest)
    }

    private fun TestScope.present(
        isAdmin: Boolean = false,
        start: Instant = base,
        params: () -> NextJamPresenter.Params = { NextJamPresenter.Params(isResumed = true) },
    ): Screen {
        val presenter = NextJamPresenter(repository, calendar(start), FakeAdminSession(isAdmin), setlist, Random(SEED))
        val screen = Screen()
        backgroundScope.launch {
            moleculeFlow(RecompositionMode.Immediate) { presenter.present(params()) }.collect { screen.latest = it }
        }
        runCurrent()
        return screen
    }

    private fun TestScope.advanceSeconds(seconds: Long) {
        advanceTimeBy(Duration.ofSeconds(seconds).toMillis())
        runCurrent()
    }

    private fun NextJamUiModel.row(title: String) =
        ((this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows.single { it.title == title }

    @Test
    fun `a presenter not resumed never refreshes inside the window`() = runTest {
        repository.snapshots.emit(snapshot())
        present(params = { NextJamPresenter.Params() })

        advanceSeconds(FIVE_MINUTES)

        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `resuming starts the calls and leaving stops them`() = runTest {
        repository.snapshots.emit(snapshot())
        var resumed by mutableStateOf(false)
        present(params = { NextJamPresenter.Params(isResumed = resumed) })
        advanceSeconds(FIVE_MINUTES)
        assertEquals(0, repository.refreshCalls)

        resumed = true
        advanceSeconds(1)
        assertEquals(1, repository.refreshCalls)
        advanceSeconds(60)
        assertEquals(3, repository.refreshCalls)

        resumed = false
        advanceSeconds(FIVE_MINUTES)
        assertEquals(3, repository.refreshCalls)
    }

    @Test
    fun `a refresh that emits a new snapshot does not restart the loop`() = runTest {
        repository.snapshots.emit(snapshot())
        present()
        assertEquals(1, repository.refreshCalls)

        // New snapshots of the same jams, as every refresh brings, with the old fetch time: a restart
        // would see stale data and refresh at once.
        advanceSeconds(10)
        repository.snapshots.emit(snapshot(jam(songs = listOf(crossroads))))
        advanceSeconds(10)
        repository.snapshots.emit(snapshot(freshness = fetched.copy(isRefreshing = true)))
        repository.snapshots.emit(snapshot())
        advanceSeconds(10)

        assertEquals(2, repository.refreshCalls)
        advanceSeconds(60)
        assertEquals(4, repository.refreshCalls)
    }

    @Test
    fun `outside every window no call is made over 24 h`() = runTest {
        repository.snapshots.emit(snapshot())
        // 2 October, four weeks before the jam.
        present(start = Instant.parse("2026-10-02T15:00:00Z"))

        advanceSeconds(Duration.ofHours(24).seconds)

        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `after midnight the latest past jam keeps its window until start plus 4 h`() = runTest {
        // 2026-11-01 00:30 in Buenos Aires: the 31 October jam is historical, its window still open.
        repository.snapshots.emit(snapshot(upcoming = jam(LocalDate.of(2026, 11, 28)), past = listOf(jam())))
        present(start = Instant.parse("2026-11-01T03:30:00Z"))
        assertEquals(1, repository.refreshCalls)

        advanceSeconds(Duration.ofMinutes(29).seconds)
        assertEquals(59, repository.refreshCalls)

        // The window closed at 01:00; the next jam's opens on 28 November.
        advanceSeconds(Duration.ofHours(2).seconds)
        assertEquals(60, repository.refreshCalls)
    }

    @Test
    fun `a periodic refresh is quiet - the notice keeps the age, never Actualizando`() = runTest {
        val failed = fetched.copy(lastFailure = DataFailure.Offline)
        repository.snapshots.emit(snapshot(freshness = failed))
        repository.hold = CompletableDeferred()
        val screen = present()
        assertEquals(1, repository.refreshCalls)

        // The repository reports the periodic read as running.
        repository.snapshots.emit(snapshot(freshness = failed.copy(isRefreshing = true)))
        runCurrent()

        val notice = checkNotNull((screen.model as NextJamUiModel.Jam).staleness)
        assertEquals("Sin conexión", notice.title)
        assertEquals("Mostrando lo guardado hace 3 horas.", notice.detail)
        assertEquals("Reintentar", notice.retryLabel)
        assertFalse(screen.model.pullRefresh.isRefreshing)
    }

    @Test
    fun `a Retry during a periodic refresh shows Actualizando`() = runTest {
        val failed = fetched.copy(lastFailure = DataFailure.Offline)
        repository.snapshots.emit(snapshot(freshness = failed))
        repository.hold = CompletableDeferred()
        val screen = present()
        repository.snapshots.emit(snapshot(freshness = failed.copy(isRefreshing = true)))
        runCurrent()

        checkNotNull((screen.model as NextJamUiModel.Jam).staleness).events(StalenessNoticeUiModel.Event.Retry)
        runCurrent()

        assertEquals("Actualizando…", checkNotNull((screen.model as NextJamUiModel.Jam).staleness).detail)
        // The Retry joined the periodic read.
        assertEquals(2, repository.refreshCalls)
        assertEquals(1, repository.fetches)
    }

    @Test
    fun `a Failed model stays Failed during a periodic refresh`() = runTest {
        // Nothing fetched, the latest read failed; the latest past jam's window is open.
        val failed = Freshness(fetchedAt = null, lastFailure = DataFailure.Offline, isRefreshing = false)
        repository.snapshots.emit(snapshot(upcoming = null, past = listOf(jam()), freshness = failed))
        repository.hold = CompletableDeferred()
        val screen = present()
        assertEquals(1, repository.refreshCalls)

        repository.snapshots.emit(
            snapshot(upcoming = null, past = listOf(jam()), freshness = failed.copy(isRefreshing = true)),
        )
        runCurrent()

        val error = (screen.model as NextJamUiModel.Failed).error
        assertEquals("No pudimos cargar la próxima jam", error.title)
        error.events(ListErrorUiModel.Event.Retry)
        runCurrent()
        // A Retry is a user refresh: the skeleton while it runs, as before.
        assertTrue(screen.model is NextJamUiModel.Loading)
    }

    @Test
    fun `one pull is one refresh, spinning until it returns, and a second pull while pending is ignored`() = runTest {
        repository.snapshots.emit(snapshot())
        // Not resumed, so only pulls refresh.
        val screen = present(params = { NextJamPresenter.Params() })
        assertEquals(PullRefreshUiModel.IDLE, screen.model.pullRefresh)
        val subscriptions = repository.subscriptions
        repository.hold = CompletableDeferred()

        screen.model.pullRefresh.events(PullRefreshUiModel.Event.Refresh)
        runCurrent()
        assertTrue(screen.model.pullRefresh.isRefreshing)
        assertEquals(1, repository.refreshCalls)
        assertEquals(subscriptions + 1, repository.subscriptions)

        screen.model.pullRefresh.events(PullRefreshUiModel.Event.Refresh)
        runCurrent()
        assertEquals(1, repository.refreshCalls)

        checkNotNull(repository.hold).complete(Unit)
        runCurrent()
        assertFalse(screen.model.pullRefresh.isRefreshing)
        assertEquals(1, repository.refreshCalls)

        screen.model.pullRefresh.events(PullRefreshUiModel.Event.Refresh)
        runCurrent()
        assertEquals(2, repository.refreshCalls)
    }

    @Test
    fun `a pull during a periodic refresh joins it, and only the pull spins the indicator`() = runTest {
        repository.snapshots.emit(snapshot())
        repository.hold = CompletableDeferred()
        val screen = present()
        assertEquals(1, repository.refreshCalls)
        assertFalse(screen.model.pullRefresh.isRefreshing)

        screen.model.pullRefresh.events(PullRefreshUiModel.Event.Refresh)
        runCurrent()

        assertTrue(screen.model.pullRefresh.isRefreshing)
        assertEquals(2, repository.refreshCalls)
        assertEquals(1, repository.fetches)
        checkNotNull(repository.hold).complete(Unit)
        runCurrent()
        assertFalse(screen.model.pullRefresh.isRefreshing)
    }

    @Test
    fun `every state carries the pull`() = runTest {
        val screen = present(params = { NextJamPresenter.Params() })
        assertTrue(screen.model is NextJamUiModel.Loading)
        screen.model.pullRefresh.events(PullRefreshUiModel.Event.Refresh)
        runCurrent()
        assertEquals(1, repository.refreshCalls)

        repository.snapshots.emit(snapshot(upcoming = null))
        runCurrent()
        assertTrue(screen.model is NextJamUiModel.NoUpcomingJam)
        screen.model.pullRefresh.events(PullRefreshUiModel.Event.Refresh)
        runCurrent()
        assertEquals(2, repository.refreshCalls)
    }

    /**
     * The spec's risk "refresh racing an admin write": a refresh may land while a write is sending,
     * with data from before or after the write. The overlays come from the setlist entries, not the
     * snapshot, so they hold whatever the refresh brings, and the row shows the cache once the
     * write's entry is gone.
     */
    @Test
    fun `a refresh landing mid-write leaves the optimistic key and the removal in place`() = runTest {
        repository.snapshots.emit(snapshot())
        val screen = present(isAdmin = true)
        setlist.keyChanges.value =
            listOf(KeyChange(1, jamDate, SongId("crossroads"), "Crossroads", Key("Bb"), KeyChange.State.Sending))
        setlist.removes.value =
            listOf(SetlistRemove(2, jamDate, SongId("hoochie"), "Hoochie Coochie Man", SetlistRemove.State.Sending))
        runCurrent()

        // A periodic refresh answered before the writes landed: the old key, the song still listed.
        advanceSeconds(30)
        repository.snapshots.emit(snapshot(freshness = fetched.copy(fetchedAt = base.plusSeconds(30))))
        runCurrent()

        assertEquals("Bb", screen.model.row("Crossroads").key)
        assertEquals("Guardando…", checkNotNull(screen.model.row("Crossroads").admin).saveStatus)
        assertEquals(
            RemovalUiModel.Removing("Quitando…"),
            checkNotNull(screen.model.row("Hoochie Coochie Man").admin).removal,
        )

        // The writes finish (entries gone, cache mirrored) and the next refresh brings the new data.
        val written = listOf(crossroads.copy(key = Key("Bb")))
        setlist.keyChanges.value = emptyList()
        setlist.removes.value = emptyList()
        repository.snapshots.emit(snapshot(jam(songs = written)))
        runCurrent()
        assertEquals("Bb", screen.model.row("Crossroads").key)
        assertNull(checkNotNull(screen.model.row("Crossroads").admin).saveStatus)
        assertEquals(1, ((screen.model as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows.size)
    }

    @Test
    fun `a refresh that already holds an added song while its add is sending shows both until the add ends`() =
        runTest {
            repository.snapshots.emit(snapshot(jam(songs = listOf(crossroads))))
            val screen = present(isAdmin = true)
            setlist.adds.value = listOf(
                SetlistAdd(
                    3,
                    jamDate,
                    SongId("hoochie"),
                    "Hoochie Coochie Man",
                    "Muddy Waters",
                    Key("E"),
                    SetlistAdd.State.Sending,
                ),
            )
            runCurrent()

            // Answered after the server appended the row, before the add's own answer came back.
            repository.snapshots.emit(snapshot())
            runCurrent()
            val model = screen.model as NextJamUiModel.Jam
            assertEquals("E", model.row("Hoochie Coochie Man").key)
            assertEquals(listOf("Hoochie Coochie Man"), checkNotNull(model.admin).pending.map { it.title })

            // Transient: the add's entry goes when its answer arrives, and one row remains.
            setlist.adds.value = emptyList()
            runCurrent()
            assertEquals(
                emptyList<PendingRowUiModel>(),
                checkNotNull((screen.model as NextJamUiModel.Jam).admin).pending,
            )
            assertEquals(2, ((screen.model as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows.size)
        }

    private companion object {
        const val SEED = 7
        const val FIVE_MINUTES = 300L
    }
}
