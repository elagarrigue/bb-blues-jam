package com.bbbjam.core.ui.presenter

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SamplePresenterTest {
    private val titles = MutableSharedFlow<String>(replay = 1)

    private fun data(title: String, expanded: Boolean) = SampleUiModel.Data(title, expanded, EventHandler {})

    @Test
    fun `emits loading, then data once the source emits`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { SamplePresenter(titles).present(Unit) }.test {
            assertEquals(SampleUiModel.Loading, awaitItem())
            titles.emit("Sweet Home Chicago")
            assertEquals(data("Sweet Home Chicago", expanded = false), awaitItem())
        }
    }

    @Test
    fun `toggle expanded event changes the state`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { SamplePresenter(titles).present(Unit) }.test {
            assertEquals(SampleUiModel.Loading, awaitItem())
            titles.emit("Sweet Home Chicago")
            val first = awaitItem() as SampleUiModel.Data
            assertEquals(data("Sweet Home Chicago", expanded = false), first)

            first.events(SampleUiModel.Data.Event.ToggleExpanded)
            assertEquals(data("Sweet Home Chicago", expanded = true), awaitItem())

            // The first model's handler still writes through the current state, not a stale copy.
            first.events(SampleUiModel.Data.Event.ToggleExpanded)
            assertEquals(data("Sweet Home Chicago", expanded = false), awaitItem())
        }
    }

    @Test
    fun `local state survives a new value from the source`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { SamplePresenter(titles).present(Unit) }.test {
            assertEquals(SampleUiModel.Loading, awaitItem())
            titles.emit("Sweet Home Chicago")
            val first = awaitItem() as SampleUiModel.Data

            first.events(SampleUiModel.Data.Event.ToggleExpanded)
            assertEquals(data("Sweet Home Chicago", expanded = true), awaitItem())

            titles.emit("The Thrill Is Gone")
            assertEquals(data("The Thrill Is Gone", expanded = true), awaitItem())
        }
    }
}
