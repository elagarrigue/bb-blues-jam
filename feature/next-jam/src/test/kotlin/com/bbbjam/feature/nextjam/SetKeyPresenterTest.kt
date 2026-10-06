package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `admin-set-key` Part B: the key picker. Copy written out from the approved table (V1). */
class SetKeyPresenterTest {
    private val jams = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val setlist = FakeSetlistRepository()
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val crossroads = SongId("crossroads")
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)
    private val gone =
        EmptyStateUiModel("Este tema ya no está en la lista", "Volvé a la próxima jam para ver la actual.")

    private fun snapshot(key: String = "A", status: JamStatus = JamStatus.DRAFT, date: LocalDate = jamDate) =
        JamsSnapshot(
            upcoming = Jam(
                date,
                LocalTime.of(21, 0),
                "La Macanuda",
                status,
                Setlist.Available(listOf(JamSong(1, crossroads, "Crossroads", "Cream", Key(key), Lineup.default()))),
            ),
            past = emptyList(),
            freshness = fetched,
        )

    private fun SetKeyUiModel.cells() = (this as SetKeyUiModel.Content).sections.flatMap { it.rows.flatten() }

    private fun SetKeyUiModel.cell(key: String) = cells().single { it.key == key }

    @Test
    fun `loading until both reads arrive, then the content with the current key marked actual`() = runTest {
        val presenter = SetKeyPresenter(jams, session, setlist)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present(SetKeyPresenter.Params(jamDate, crossroads, {}, {}))
        }.test {
            assertTrue(awaitItem() is SetKeyUiModel.Loading)
            jams.snapshots.emit(snapshot())
            val model = expectMostRecentItem() as SetKeyUiModel.Content
            assertEquals("Cambiar tonalidad", model.title)
            assertEquals("Crossroads", model.songTitle)
            assertEquals("Tonalidad actual", model.currentLabel)
            assertEquals("A", model.currentKey)
            assertEquals("Tonalidad A", model.currentKeyDescription)
            assertEquals(listOf("Mayores", "Menores"), model.sections.map { it.label })
            assertEquals(listOf(listOf(4, 4, 4), listOf(4, 4, 4)), model.sections.map { s -> s.rows.map { it.size } })
            assertEquals(
                listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B") +
                    listOf("Cm", "C#m", "Dm", "Ebm", "Em", "Fm", "F#m", "Gm", "G#m", "Am", "Bbm", "Bm"),
                model.cells().map { it.key },
            )
            val current = model.cell("A")
            assertTrue(current.isCurrent)
            assertEquals("actual", current.currentLabel)
            assertEquals(listOf("A"), model.cells().filter { it.isCurrent }.map { it.key })
            val bb = model.cell("Bb")
            assertEquals("Tonalidad Bb", bb.description)
            assertEquals("elegir esta tonalidad", bb.pickLabel)
            assertEquals(null, bb.currentLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a pick sets the key once and closes once, and the current cell sends nothing`() = runTest {
        var done = 0
        val presenter = SetKeyPresenter(jams, session, setlist)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present(SetKeyPresenter.Params(jamDate, crossroads, {}, { done++ }))
        }.test {
            jams.snapshots.emit(snapshot())
            val model = expectMostRecentItem()
            model.cell("A").events(KeyCellUiModel.Event.Pick)
            assertEquals(emptyList<Any>(), setlist.keyCalls)
            assertEquals(0, done)

            model.cell("Bb").events(KeyCellUiModel.Event.Pick)
            model.cell("Bb").events(KeyCellUiModel.Event.Pick)
            model.cell("C").events(KeyCellUiModel.Event.Pick)
            assertEquals(listOf(FakeSetlistRepository.AddCall(jamDate, crossroads, Key("Bb"))), setlist.keyCalls)
            assertEquals(1, done)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a pending change is the current key, so the picker agrees with the row, and a failed one is not`() {
        val sending = KeyChange(3, jamDate, crossroads, "Crossroads", Key("Bb"), KeyChange.State.Sending)
        val model = setKeyModel(true, snapshot(), listOf(sending), jamDate, crossroads) as SetKeyUiModel.Content
        assertEquals("Bb", model.currentKey)
        assertEquals(listOf("Bb"), model.cells().filter { it.isCurrent }.map { it.key })

        val failed = sending.copy(state = KeyChange.State.Failed(WriteOutcome.Offline))
        val reverted = setKeyModel(true, snapshot(), listOf(failed), jamDate, crossroads) as SetKeyUiModel.Content
        assertEquals("A", reverted.currentKey)
    }

    @Test
    fun `a key in another spelling is shown as is and marks no cell`() {
        val model = setKeyModel(true, snapshot(key = "A#"), emptyList(), jamDate, crossroads) as SetKeyUiModel.Content
        assertEquals("A#", model.currentKey)
        assertFalse(model.cells().any { it.isCurrent })
    }

    @Test
    fun `gone for a musician, another date, or a song no longer listed`() {
        assertEquals(
            gone,
            (setKeyModel(false, snapshot(), emptyList(), jamDate, crossroads) as SetKeyUiModel.Gone).empty,
        )
        assertTrue(
            setKeyModel(
                true,
                snapshot(date = jamDate.plusDays(28)),
                emptyList(),
                jamDate,
                crossroads,
            ) is SetKeyUiModel.Gone,
        )
        assertTrue(setKeyModel(true, snapshot(), emptyList(), jamDate, SongId("hoochie")) is SetKeyUiModel.Gone)
        assertTrue(setKeyModel(null, snapshot(), emptyList(), jamDate, crossroads) is SetKeyUiModel.Loading)
        assertTrue(setKeyModel(true, null, emptyList(), jamDate, crossroads) is SetKeyUiModel.Loading)
    }

    @Test
    fun `logging out while the picker is open turns it gone`() = runTest {
        val presenter = SetKeyPresenter(jams, session, setlist)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present(SetKeyPresenter.Params(jamDate, crossroads, {}, {}))
        }.test {
            jams.snapshots.emit(snapshot(status = JamStatus.PUBLISHED))
            assertTrue(expectMostRecentItem() is SetKeyUiModel.Content)
            session.isAdmin.value = false
            assertTrue(awaitItem() is SetKeyUiModel.Gone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `back calls the current callback`() = runTest {
        var backs = 0
        val presenter = SetKeyPresenter(jams, session, setlist)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present(SetKeyPresenter.Params(jamDate, crossroads, { backs++ }, {}))
        }.test {
            val loading = awaitItem()
            loading.back.events(BackUiModel.Event.Back)
            assertEquals(1, backs)
            assertEquals(emptyList<Any>(), setlist.keyCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
