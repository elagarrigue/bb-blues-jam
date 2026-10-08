package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.SetlistMove
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
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

class NextJamMoveTest {
    private val repository = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val setlist = FakeSetlistRepository()
    private val calendar = JamCalendar(
        Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC),
        JamCalendar.BUENOS_AIRES,
    )
    private val today = LocalDate.of(2026, 10, 2)
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)
    private val songs = listOf("crossroads", "hoochie", "thrill", "pride").mapIndexed { index, id ->
        JamSong(index + 1, SongId(id), id, "artist", Key("A"), Lineup.default())
    }

    private fun snapshot(items: List<JamSong> = songs) = JamsSnapshot(
        upcoming = Jam(jamDate, LocalTime.of(21, 0), "La Macanuda", JamStatus.PUBLISHED, Setlist.Available(items)),
        past = emptyList(),
        freshness = fetched,
    )

    private fun presenter() = NextJamPresenter(repository, calendar, session, setlist)
    private fun NextJamUiModel.row(id: String) = ((this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs)
        .rows.single { it.title == id }
    private fun NextJamUiModel.failures() = checkNotNull((this as NextJamUiModel.Jam).admin).failures
    private suspend fun ReceiveTurbine<NextJamUiModel>.awaitJam() =
        awaitMatching { it is NextJamUiModel.Jam } as NextJamUiModel.Jam

    private suspend fun ReceiveTurbine<NextJamUiModel>.awaitModel(predicate: (NextJamUiModel) -> Boolean) =
        awaitMatching(predicate)

    private suspend fun ReceiveTurbine<NextJamUiModel>.awaitMatching(
        predicate: (NextJamUiModel) -> Boolean,
    ): NextJamUiModel {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private fun move(id: Long, songId: String, target: Int, state: SetlistMove.State = SetlistMove.State.Sending) =
        SetlistMove(id, jamDate, SongId(songId), songId, target, state)

    @Test
    fun `expanded admin rows model absolute positions and keep disabled edge actions`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val model = awaitJam()
            assertEquals("Posición 1 de 4", checkNotNull(model.row("crossroads").admin?.move).positionLine)
            assertFalse(checkNotNull(model.row("crossroads").admin?.move).up.enabled)
            assertTrue(checkNotNull(model.row("crossroads").admin?.move).down.enabled)
            assertTrue(checkNotNull(model.row("pride").admin?.move).up.enabled)
            assertFalse(checkNotNull(model.row("pride").admin?.move).down.enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `two taps from one old model advance the target twice in call order`() = runTest {
        val hold = CompletableDeferred<Unit>()
        setlist.moveHold = hold
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val move = checkNotNull(awaitJam().row("thrill").admin?.move)
            move.up.events(MoveActionUiModel.Event.Move)
            move.up.events(MoveActionUiModel.Event.Move)

            assertEquals(listOf(2, 1), setlist.moveCalls.map { it.third })
            assertEquals(
                listOf(jamDate to SongId("thrill"), jamDate to SongId("thrill")),
                setlist.moveCalls.map {
                    it.first to
                        it.second
                },
            )
            hold.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sending move overlays the admin order and status while musicians stay unchanged`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            setlist.moves.value = listOf(move(1, "thrill", 1))
            val model = awaitModel { it is NextJamUiModel.Jam && it.row("thrill").position == 1 }
            assertEquals(
                listOf("thrill", "crossroads", "hoochie", "pride"),
                ((model as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows.map { it.title },
            )
            assertEquals(1, model.row("thrill").position)
            assertEquals("Guardando…", checkNotNull(model.row("thrill").admin).saveStatus)

            session.isAdmin.value = false
            val musician = awaitModel { it is NextJamUiModel.Jam && it.row("thrill").admin == null }
            assertEquals(
                listOf("crossroads", "hoochie", "thrill", "pride"),
                ((musician as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows.map { it.title },
            )
            assertNull(musician.row("thrill").admin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed move reverts and creates a dismissible card and removal sending hides the block`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            setlist.moves.value =
                listOf(move(9, "thrill", 1, SetlistMove.State.Failed(WriteOutcome.Rejected("unordered_setlist"))))
            val failed = awaitModel { it is NextJamUiModel.Jam && it.failures().isNotEmpty() }
            assertEquals(
                listOf("crossroads", "hoochie", "thrill", "pride"),
                ((failed as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows.map { it.title },
            )
            val card = failed.failures().single()
            assertEquals("No se pudo mover «thrill»", card.title)
            assertEquals(
                "Las posiciones de la planilla están desordenadas. Corregilas ahí y probá de nuevo.",
                card.message,
            )

            setlist.removes.value =
                listOf(SetlistRemove(10, jamDate, SongId("thrill"), "thrill", SetlistRemove.State.Sending))
            assertNull(checkNotNull(awaitItem().row("thrill").admin).move)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `duplicate ids receive position row keys and opening detail retains cached position`() = runTest {
        val duplicateSongs = listOf(songs[0], songs[1], songs[2].copy(songId = SongId("hoochie")), songs[3])
        var opened: Int? = null
        moleculeFlow(RecompositionMode.Immediate) {
            presenter().present(NextJamPresenter.Params(onOpenSong = { _, position -> opened = position }))
        }.test {
            repository.snapshots.emit(snapshot(duplicateSongs))
            val model = awaitJam()
            val rows = ((model as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows
            assertEquals("p2", rows.single { it.position == 2 }.rowKey)
            setlist.moves.value = listOf(move(1, "thrill", 2))
            val moved = awaitItem()
            val row = moved.row("thrill")
            row.events(SongRowUiModel.Event.OpenDetail)
            assertEquals(3, opened)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
