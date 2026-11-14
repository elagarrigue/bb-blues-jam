package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.presence.PresenceAnswer
import com.bbbjam.core.data.testing.FakeAdminSession
import com.bbbjam.core.data.testing.FakeJamsRepository
import com.bbbjam.core.model.Setlist
import com.bbbjam.feature.nextjam.FakeSetlistRepository
import com.bbbjam.feature.nextjam.FakeSongPresence
import com.bbbjam.feature.nextjam.songdetail.SongDetailFixtures.id
import com.bbbjam.feature.nextjam.songdetail.SongDetailFixtures.jam
import com.bbbjam.feature.nextjam.songdetail.SongDetailFixtures.pastDate
import com.bbbjam.feature.nextjam.songdetail.SongDetailFixtures.snapshot
import com.bbbjam.feature.nextjam.songdetail.SongDetailFixtures.song
import com.bbbjam.feature.nextjam.songdetail.SongDetailFixtures.upcomingDate
import com.bbbjam.feature.nextjam.sources
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongDetailLiveRefreshTest {
    private val jams = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val presence = FakeSongPresence(PresenceAnswer.OthersHere)
    private val presenter = SongDetailPresenter(jams, session, FakeSetlistRepository().sources(), presence)
    private val params = SongDetailPresenter.Params(upcomingDate, id(2), 2, onBack = {}, isResumed = true)

    private fun data(key: String = "Bm") = snapshot(jam(upcomingDate, Setlist.Available(listOf(song(2, key = key)))))

    private suspend fun TestScope.settle(turbine: ReceiveTurbine<SongDetailUiModel>) {
        var item = turbine.awaitItem()
        while (item !is SongDetailUiModel.Song) item = turbine.awaitItem()
        runCurrent()
    }

    @Test
    fun `shared editing refreshes immediately and every second and shows new cache data`() = runTest {
        jams.snapshots.emit(data())
        moleculeFlow(RecompositionMode.Immediate) { presenter.present(params) }.test {
            settle(this)
            assertEquals(1, jams.refreshCalls)
            advanceTimeBy(3.seconds)
            runCurrent()
            assertEquals(4, jams.refreshCalls)
            jams.snapshots.emit(data(key = "D"))
            runCurrent()
            assertEquals("D", (expectMostRecentItem() as SongDetailUiModel.Song).key)
            jams.snapshots.emit(snapshot(jam(upcomingDate, Setlist.Available(emptyList()))))
            runCurrent()
            assertTrue(expectMostRecentItem() is SongDetailUiModel.NotFound)
            val removedCalls = jams.refreshCalls
            advanceTimeBy(3.seconds)
            assertEquals(removedCalls, jams.refreshCalls)
            cancelAndIgnoreRemainingEvents()
        }
        val calls = jams.refreshCalls
        advanceTimeBy(5.seconds)
        assertEquals(calls, jams.refreshCalls)
    }

    @Test
    fun `hiding the screen and logging out stop refreshing`() = runTest {
        jams.snapshots.emit(data())
        var current by mutableStateOf(params)
        moleculeFlow(RecompositionMode.Immediate) { presenter.present(current) }.test {
            settle(this)
            current = params.copy(isResumed = false)
            runCurrent()
            val hiddenCalls = jams.refreshCalls
            advanceTimeBy(3.seconds)
            assertEquals(hiddenCalls, jams.refreshCalls)
            current = params
            runCurrent()
            assertEquals(hiddenCalls + 1, jams.refreshCalls)
            session.isAdmin.value = false
            runCurrent()
            val logoutCalls = jams.refreshCalls
            advanceTimeBy(3.seconds)
            assertEquals(logoutCalls, jams.refreshCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unknown presence keeps refreshing but Alone stops at the next heartbeat`() = runTest {
        jams.snapshots.emit(data())
        moleculeFlow(RecompositionMode.Immediate) { presenter.present(params) }.test {
            settle(this)
            presence.answer = PresenceAnswer.Unknown
            advanceTimeBy(31.seconds)
            runCurrent()
            assertEquals(32, jams.refreshCalls)
            assertNotNull((expectMostRecentItem() as SongDetailUiModel.Song).admin?.presenceNotice)
            presence.answer = PresenceAnswer.Alone
            advanceTimeBy(30.seconds)
            runCurrent()
            val aloneCalls = jams.refreshCalls
            advanceTimeBy(3.seconds)
            assertEquals(aloneCalls, jams.refreshCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a slow failed refresh does not overlap and retries one second after its answer`() = runTest {
        jams.snapshots.emit(data())
        val held = CompletableDeferred<Unit>()
        jams.hold = held
        jams.fails = true
        moleculeFlow(RecompositionMode.Immediate) { presenter.present(params) }.test {
            settle(this)
            advanceTimeBy(5.seconds)
            runCurrent()
            assertEquals(1, jams.refreshCalls)
            held.complete(Unit)
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(2, jams.refreshCalls)
            assertEquals("Bm", (expectMostRecentItem() as SongDetailUiModel.Song).key)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `alone or unknown first never polls and musicians hidden past or missing songs never poll`() = runTest {
        jams.snapshots.emit(data().copy(past = listOf(jam(pastDate, Setlist.Available(listOf(song(2)))))))
        val cases = listOf(
            Triple(true, params, PresenceAnswer.Alone),
            Triple(true, params, PresenceAnswer.Unknown),
            Triple(false, params, PresenceAnswer.OthersHere),
            Triple(true, params.copy(isResumed = false), PresenceAnswer.OthersHere),
            Triple(true, params.copy(jamDate = pastDate), PresenceAnswer.OthersHere),
            Triple(true, params.copy(songId = id(9)), PresenceAnswer.OthersHere),
        )
        for ((admin, current, answer) in cases) {
            session.isAdmin.value = admin
            presence.answer = answer
            moleculeFlow(RecompositionMode.Immediate) { presenter.present(current) }.test {
                awaitItem()
                advanceTimeBy(3.seconds)
                runCurrent()
                assertEquals(0, jams.refreshCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
