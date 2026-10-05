package com.bbbjam.feature.pastjams

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.back
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.header
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.jam
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.julyDate
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.loading
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.notFound
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.row
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.snapshot
import com.bbbjam.feature.pastjams.PastJamDetailFixtures.song
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The detail presenter on Molecule: loading, live transitions, and the back event. */
class PastJamDetailPresenterTest {
    private val repository = FakeJamsRepository()
    private val presenter = PastJamDetailPresenter(repository)

    @Test
    fun `loading, then the jam, then the not-published line, then not found when the jam leaves the snapshot`() =
        runTest {
            val params = PastJamDetailPresenter.Params(julyDate) {}
            moleculeFlow(RecompositionMode.Immediate) { presenter.present(params) }.test {
                assertEquals(loading, awaitItem())
                repository.snapshots.emit(snapshot(listOf(jam(setlist = Setlist.Available(listOf(song(1), song(2)))))))
                assertEquals(
                    PastJamDetailUiModel.Jam(
                        header("2 temas"),
                        PastSetlistUiModel.Songs(listOf(row(1, "01"), row(2, "02")), droppedRowsNote = null),
                        back,
                    ),
                    awaitItem(),
                )
                repository.snapshots.emit(snapshot(listOf(jam(setlist = Setlist.Withheld, status = JamStatus.DRAFT))))
                assertEquals(
                    PastJamDetailUiModel.Jam(
                        header(null),
                        PastSetlistUiModel.NotShown("La lista de esta jam no se publicó."),
                        back,
                    ),
                    awaitItem(),
                )
                repository.snapshots.emit(snapshot(emptyList()))
                assertEquals(notFound, awaitItem())
            }
            assertEquals(0, repository.refreshCalls)
        }

    @Test
    fun `Back calls onBack, and an earlier model's handler calls the current callback`() = runTest {
        val calls = mutableListOf<String>()
        var params by mutableStateOf(PastJamDetailPresenter.Params(julyDate) { calls += "first" })
        moleculeFlow(RecompositionMode.Immediate) { presenter.present(params) }.test {
            val first = awaitItem()
            first.back.events(BackUiModel.Event.Back)
            assertEquals(listOf("first"), calls)

            params = PastJamDetailPresenter.Params(julyDate) { calls += "second" }
            awaitItem()
            first.back.events(BackUiModel.Event.Back)
            assertEquals(listOf("first", "second"), calls)
        }
    }
}
