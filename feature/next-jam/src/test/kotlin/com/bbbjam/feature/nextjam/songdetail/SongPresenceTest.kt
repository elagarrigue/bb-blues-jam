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
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.nextjam.FakeSetlistRepository
import com.bbbjam.feature.nextjam.FakeSongPresence
import com.bbbjam.feature.nextjam.R
import com.bbbjam.feature.nextjam.songdetail.SongDetailFixtures.id
import com.bbbjam.feature.nextjam.songdetail.SongDetailFixtures.jam
import com.bbbjam.feature.nextjam.songdetail.SongDetailFixtures.snapshot
import com.bbbjam.feature.nextjam.songdetail.SongDetailFixtures.song
import com.bbbjam.feature.nextjam.songdetail.SongDetailFixtures.upcomingDate
import com.bbbjam.feature.nextjam.sources
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `admin-presence-notice` (D-32): the heartbeat, the notice and who may announce. */
class SongPresenceTest {
    private val jams = FakeJamsRepository()
    private val presence = FakeSongPresence()
    private val notice = UiText.res(R.string.song_detail_presence_notice)
    private val twoSongs = snapshot(jam(upcomingDate, Setlist.Available(listOf(song(1), song(2)))))

    private fun presenter(session: FakeAdminSession) =
        SongDetailPresenter(jams, session, FakeSetlistRepository().sources(), presence)

    private fun params(date: LocalDate = upcomingDate, resumed: Boolean = true) =
        SongDetailPresenter.Params(date, id(2), 2, onBack = {}, isResumed = resumed)

    private fun SongDetailUiModel.notice() = (this as? SongDetailUiModel.Song)?.admin?.presenceNotice

    /** Reads until the song is drawn, then lets the effects run. */
    private suspend fun TestScope.settle(turbine: ReceiveTurbine<SongDetailUiModel>): SongDetailUiModel.Song {
        var item = turbine.awaitItem()
        while (item !is SongDetailUiModel.Song) item = turbine.awaitItem()
        runCurrent()
        return item
    }

    @Test
    fun `the heartbeat announces every 30 seconds counted from each answer`() = runTest {
        val times = mutableListOf<Long>()
        val job = launch {
            runPresenceHeartbeat(
                interval = PresenceHeartbeat.INTERVAL,
                announce = {
                    times += currentTime
                    delay(5.seconds)
                    PresenceAnswer.Alone
                },
                onAnswer = {},
            )
        }
        advanceTimeBy(80.seconds)
        runCurrent()
        job.cancel()
        assertEquals(listOf(0L, 35_000L, 70_000L), times)
    }

    @Test
    fun `an admin on a visible upcoming song announces and the notice follows the answer`() = runTest {
        jams.snapshots.emit(twoSongs)
        val params = params()
        val session = FakeAdminSession(isAdmin = true)
        moleculeFlow(RecompositionMode.Immediate) { presenter(session).present(params) }.test {
            settle(this)
            assertEquals(listOf(upcomingDate to id(2)), presence.announces)
            presence.answer = PresenceAnswer.OthersHere
            advanceTimeBy(31.seconds)
            assertEquals(notice, expectMostRecentItem().notice())
            presence.answer = PresenceAnswer.Unknown
            advanceTimeBy(31.seconds)
            expectNoEvents()
            presence.answer = PresenceAnswer.Alone
            advanceTimeBy(31.seconds)
            assertNull(expectMostRecentItem().notice())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Unknown first draws no notice and no failure card`() = runTest {
        presence.answer = PresenceAnswer.Unknown
        jams.snapshots.emit(twoSongs)
        val params = params()
        val session = FakeAdminSession(isAdmin = true)
        moleculeFlow(RecompositionMode.Immediate) { presenter(session).present(params) }.test {
            val shown = settle(this)
            advanceTimeBy(65.seconds)
            expectNoEvents()
            assertNull(shown.admin?.presenceNotice)
            assertTrue(checkNotNull(shown.admin).failures.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a musician, a hidden screen and a past jam song never announce`() = runTest {
        val past = LocalDate.of(2026, 9, 5)
        jams.snapshots.emit(
            snapshot(
                jam(upcomingDate, Setlist.Available(listOf(song(1), song(2)))),
                past = listOf(jam(past, Setlist.Available(listOf(song(1), song(2))))),
            ),
        )
        val cases = listOf(
            FakeAdminSession(isAdmin = false) to params(),
            FakeAdminSession(isAdmin = true) to params(resumed = false),
            FakeAdminSession(isAdmin = true) to params(date = past),
        )
        for ((session, params) in cases) {
            moleculeFlow(RecompositionMode.Immediate) { presenter(session).present(params) }.test {
                settle(this)
                advanceTimeBy(65.seconds)
                cancelAndIgnoreRemainingEvents()
            }
        }
        assertTrue(presence.announces.isEmpty())
        assertTrue(presence.leaves.isEmpty())
    }

    @Test
    fun `leaving the visible state releases once, stops announcing and clears the notice`() = runTest {
        presence.answer = PresenceAnswer.OthersHere
        jams.snapshots.emit(twoSongs)
        var params by mutableStateOf(params())
        val session = FakeAdminSession(isAdmin = true)
        moleculeFlow(RecompositionMode.Immediate) { presenter(session).present(params) }.test {
            settle(this)
            assertEquals(notice, expectMostRecentItem().notice())
            params = params(resumed = false)
            runCurrent()
            assertEquals(1, presence.leaves.size)
            val announced = presence.announces.size
            advanceTimeBy(95.seconds)
            assertEquals(announced, presence.announces.size)
            assertNull(expectMostRecentItem().notice())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `logging out releases and stops`() = runTest {
        jams.snapshots.emit(twoSongs)
        val session = FakeAdminSession(isAdmin = true)
        val params = params()
        moleculeFlow(RecompositionMode.Immediate) { presenter(session).present(params) }.test {
            settle(this)
            assertEquals(1, presence.announces.size)
            session.isAdmin.value = false
            runCurrent()
            assertEquals(1, presence.leaves.size)
            advanceTimeBy(95.seconds)
            assertEquals(1, presence.announces.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
