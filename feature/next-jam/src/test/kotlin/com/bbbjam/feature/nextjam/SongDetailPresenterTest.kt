package com.bbbjam.feature.nextjam

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.feature.nextjam.SongDetailFixtures.id
import com.bbbjam.feature.nextjam.SongDetailFixtures.jam
import com.bbbjam.feature.nextjam.SongDetailFixtures.loading
import com.bbbjam.feature.nextjam.SongDetailFixtures.notFound
import com.bbbjam.feature.nextjam.SongDetailFixtures.snapshot
import com.bbbjam.feature.nextjam.SongDetailFixtures.song
import com.bbbjam.feature.nextjam.SongDetailFixtures.songModel
import com.bbbjam.feature.nextjam.SongDetailFixtures.upcomingDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The presenter on Molecule: loading, a live transition, and the back event. */
class SongDetailPresenterTest {
    /** The next model unlike [previous]: reading the admin flag may recompose once with the same model. */
    private suspend fun ReceiveTurbine<SongDetailUiModel>.awaitDistinct(
        previous: SongDetailUiModel,
    ): SongDetailUiModel {
        var item = awaitItem()
        while (item == previous) item = awaitItem()
        return item
    }

    private val repository = FakeJamsRepository()
    private val presenter = SongDetailPresenter(repository, FakeAdminSession(), FakeSetlistRepository())

    @Test
    fun `loading, then the song, then not found when a refresh drops the row`() = runTest {
        val params = SongDetailPresenter.Params(upcomingDate, id(2), 2, onBack = {})
        moleculeFlow(RecompositionMode.Immediate) { presenter.present(params) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(upcomingDate, Setlist.Available(listOf(song(1), song(2))))))
            assertEquals(songModel(), awaitDistinct(loading))
            repository.snapshots.emit(snapshot(jam(upcomingDate, Setlist.Available(listOf(song(1))))))
            assertEquals(notFound, awaitDistinct(songModel()))
        }
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `Back calls onBack, and an earlier model's handler calls the current callback`() = runTest {
        val calls = mutableListOf<String>()
        var params by mutableStateOf(SongDetailPresenter.Params(upcomingDate, id(2), 2, onBack = { calls += "first" }))
        moleculeFlow(RecompositionMode.Immediate) { presenter.present(params) }.test {
            val first = awaitItem()
            first.back.events(BackUiModel.Event.Back)
            assertEquals(listOf("first"), calls)

            params = SongDetailPresenter.Params(upcomingDate, id(2), 2, onBack = { calls += "second" })
            awaitItem()
            first.back.events(BackUiModel.Event.Back)
            assertEquals(listOf("first", "second"), calls)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
