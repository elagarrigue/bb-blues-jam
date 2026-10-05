package com.bbbjam.feature.songdetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.feature.songdetail.SongDetailFixtures.jam
import com.bbbjam.feature.songdetail.SongDetailFixtures.loading
import com.bbbjam.feature.songdetail.SongDetailFixtures.notFound
import com.bbbjam.feature.songdetail.SongDetailFixtures.snapshot
import com.bbbjam.feature.songdetail.SongDetailFixtures.song
import com.bbbjam.feature.songdetail.SongDetailFixtures.songModel
import com.bbbjam.feature.songdetail.SongDetailFixtures.upcomingDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The presenter on Molecule: loading, a live transition, and the back event. */
class SongDetailPresenterTest {
    private val repository = FakeJamsRepository()
    private val presenter = SongDetailPresenter(repository)

    @Test
    fun `loading, then the song, then not found when a refresh drops the row`() = runTest {
        val params = SongDetailPresenter.Params(upcomingDate, 2) {}
        moleculeFlow(RecompositionMode.Immediate) { presenter.present(params) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(upcomingDate, Setlist.Available(listOf(song(1), song(2))))))
            assertEquals(songModel(), awaitItem())
            repository.snapshots.emit(snapshot(jam(upcomingDate, Setlist.Available(listOf(song(1))))))
            assertEquals(notFound, awaitItem())
        }
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `Back calls onBack, and an earlier model's handler calls the current callback`() = runTest {
        val calls = mutableListOf<String>()
        var params by mutableStateOf(SongDetailPresenter.Params(upcomingDate, 2) { calls += "first" })
        moleculeFlow(RecompositionMode.Immediate) { presenter.present(params) }.test {
            val first = awaitItem()
            first.back.events(BackUiModel.Event.Back)
            assertEquals(listOf("first"), calls)

            params = SongDetailPresenter.Params(upcomingDate, 2) { calls += "second" }
            awaitItem()
            first.back.events(BackUiModel.Event.Back)
            assertEquals(listOf("first", "second"), calls)
        }
    }
}
