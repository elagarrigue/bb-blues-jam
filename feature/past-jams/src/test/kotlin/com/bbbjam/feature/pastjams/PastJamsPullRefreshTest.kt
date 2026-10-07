package com.bbbjam.feature.pastjams

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.state.PullRefreshUiModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pull to refresh on Anteriores (`live-refresh-during-jam`, L2): one read per pull, and no periodic refresh. */
class PastJamsPullRefreshTest {
    private val repository = FakeJamsRepository()

    /** 2026-10-31 21:30 in Buenos Aires: inside that night's jam window, which Anteriores ignores. */
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-11-01T00:30:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
    private val fetched = Freshness(Instant.parse("2026-10-31T20:00:00Z"), lastFailure = null, isRefreshing = false)
    private val pastJam = Jam(
        LocalDate.of(2026, 9, 26),
        LocalTime.of(21, 0),
        "La Macanuda",
        JamStatus.PUBLISHED,
        Setlist.Available(emptyList()),
    )
    private val tonight = pastJam.copy(date = LocalDate.of(2026, 10, 31))

    private class Screen(var latest: PastJamsUiModel? = null) {
        val model: PastJamsUiModel
            get() = checkNotNull(latest)
    }

    private fun TestScope.present(): Screen {
        val presenter = PastJamsPresenter(repository, calendar)
        val screen = Screen()
        backgroundScope.launch {
            moleculeFlow(RecompositionMode.Immediate) { presenter.present(PastJamsPresenter.Params()) }
                .collect { screen.latest = it }
        }
        runCurrent()
        return screen
    }

    @Test
    fun `one pull is one refresh, spinning until it returns, and a second pull while pending is ignored`() = runTest {
        repository.snapshots.emit(JamsSnapshot(null, listOf(pastJam), fetched))
        val screen = present()
        assertTrue(screen.model is PastJamsUiModel.Jams)
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
    }

    @Test
    fun `every state carries the pull`() = runTest {
        val screen = present()
        assertTrue(screen.model is PastJamsUiModel.Loading)
        screen.model.pullRefresh.events(PullRefreshUiModel.Event.Refresh)
        runCurrent()
        assertEquals(1, repository.refreshCalls)

        repository.snapshots.emit(JamsSnapshot(null, emptyList(), fetched))
        runCurrent()
        assertTrue(screen.model is PastJamsUiModel.Empty)
        screen.model.pullRefresh.events(PullRefreshUiModel.Event.Refresh)
        runCurrent()
        assertEquals(2, repository.refreshCalls)
    }

    @Test
    fun `Anteriores never refreshes on its own, even inside a jam's window`() = runTest {
        repository.snapshots.emit(JamsSnapshot(null, listOf(tonight, pastJam), fetched))
        present()

        advanceTimeBy(Duration.ofHours(1).toMillis())
        runCurrent()

        assertEquals(0, repository.refreshCalls)
    }
}
