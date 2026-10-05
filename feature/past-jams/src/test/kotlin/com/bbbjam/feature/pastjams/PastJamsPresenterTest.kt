package com.bbbjam.feature.pastjams

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.ListErrorUiModel
import com.bbbjam.core.ui.state.StalenessNoticeUiModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The presenter with Molecule and Turbine on the JVM. Every user-facing string expected here is
 * written out from the approved copy table in `docs/specs/past-jams-list.md` (C1), never read from
 * [PastJamsCopy].
 */
class PastJamsPresenterTest {
    private val repository = FakeJamsRepository()

    /** 5 October 2026, 12:00 in Buenos Aires. */
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-05T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)

    private val title = "Jams anteriores"
    private val loading = PastJamsUiModel.Loading(title, "Cargando las jams anteriores")
    private val fetched = Freshness(Instant.parse("2026-10-05T14:59:00Z"), lastFailure = null, isRefreshing = false)
    private val offline = Freshness(fetchedAt = null, lastFailure = DataFailure.Offline, isRefreshing = false)

    private val offlineError = PastJamsUiModel.Failed(
        title,
        ListErrorUiModel(
            "No pudimos cargar las jams anteriores",
            "No hay conexión y todavía no hay nada guardado. Revisá los datos o el wifi y probá de nuevo.",
            "Reintentar",
            EventHandler {},
        ),
    )

    private val empty = PastJamsUiModel.Empty(
        title,
        EmptyStateUiModel(
            "Todavía no hay jams anteriores",
            "Después de cada jam, su lista queda guardada acá para que veas qué se tocó.",
        ),
        staleness = null,
    )

    private fun song(position: Int, title: String) =
        JamSong(position, SongId("song-$position"), title, "Someone", Key("A"), Lineup.default())

    private fun jam(date: String, venue: String = "La Macanuda") = Jam(
        LocalDate.parse(date),
        LocalTime.of(21, 0),
        venue,
        JamStatus.PUBLISHED,
        Setlist.Available(listOf(song(1, "Sweet Home Chicago"))),
    )

    private fun row(date: LocalDate, label: String, venue: String = "La Macanuda") =
        PastJamRowUiModel(date, label, venue, PastJamSummary.Songs("1 tema", "Sweet Home Chicago"))

    private val julyRow = row(LocalDate.of(2026, 7, 25), "Sábado 25 de julio de 2026")
    private val juneRow = row(LocalDate.of(2026, 6, 27), "Sábado 27 de junio de 2026")
    private val mayRow = row(LocalDate.of(2026, 5, 30), "Sábado 30 de mayo de 2026")

    private fun snapshot(past: List<Jam>, freshness: Freshness = fetched, upcoming: Jam? = null) =
        JamsSnapshot(upcoming, past, freshness)

    private fun presenter() = PastJamsPresenter(repository, calendar)

    /** Skips models until one matches: a re-subscription may recompose once with the same model. */
    private suspend fun ReceiveTurbine<PastJamsUiModel>.awaitMatching(
        predicate: (PastJamsUiModel) -> Boolean,
    ): PastJamsUiModel {
        var item = awaitItem()
        while (!predicate(item)) item = awaitItem()
        return item
    }

    @Test
    fun `loading, then the past jams newest first from out-of-order input`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(listOf(jam("2026-05-30"), jam("2026-07-25"), jam("2026-06-27"))))
            assertEquals(PastJamsUiModel.Jams(title, listOf(julyRow, juneRow, mayRow), staleness = null), awaitItem())
        }
        assertEquals(0, repository.refreshCalls)
        assertEquals(1, repository.subscriptions)
    }

    @Test
    fun `the upcoming jam is never listed`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(past = emptyList(), upcoming = jam("2026-10-31")))
            assertEquals(empty, awaitItem())
            repository.snapshots.emit(snapshot(listOf(jam("2026-07-25")), upcoming = jam("2026-10-31")))
            assertEquals(PastJamsUiModel.Jams(title, listOf(julyRow), staleness = null), awaitItem())
        }
    }

    @Test
    fun `an empty snapshot after a fetch is the empty block`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(emptyList()))
            assertEquals(empty, awaitItem())
        }
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `the offline error, then Retry re-subscribes and refreshes once, then the skeleton, then the rows`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(emptyList(), offline))
            val failed = awaitItem()
            assertEquals(offlineError, failed)
            assertEquals(1, repository.subscriptions)
            assertEquals(0, repository.refreshCalls)

            (failed as PastJamsUiModel.Failed).error.events(ListErrorUiModel.Event.Retry)
            // The refresh is running: the skeleton again, not the error.
            repository.snapshots.emit(snapshot(emptyList(), offline.copy(isRefreshing = true)))
            assertEquals(loading, awaitMatching { it != failed })
            assertEquals(1, repository.refreshCalls)
            assertEquals(2, repository.subscriptions)

            repository.snapshots.emit(snapshot(listOf(jam("2026-07-25"))))
            assertEquals(PastJamsUiModel.Jams(title, listOf(julyRow), staleness = null), awaitItem())
        }
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `the notice appears when a refresh fails and goes on success, and age alone draws none`() = runTest {
        val twoHoursAgo = Instant.parse("2026-10-05T13:00:00Z")
        val aMonthAgo = Instant.parse("2026-09-05T13:00:00Z")
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(listOf(jam("2026-07-25")), Freshness(aMonthAgo, null, false)))
            assertNull((awaitItem() as PastJamsUiModel.Jams).staleness)

            repository.snapshots.emit(
                snapshot(listOf(jam("2026-07-25")), Freshness(twoHoursAgo, DataFailure.Offline, isRefreshing = false)),
            )
            val stale = awaitItem() as PastJamsUiModel.Jams
            assertEquals(
                StalenessNoticeUiModel(
                    "Sin conexión",
                    "Mostrando lo guardado hace 2 horas.",
                    "Reintentar",
                    EventHandler {
                    },
                ),
                stale.staleness,
            )

            checkNotNull(stale.staleness).events(StalenessNoticeUiModel.Event.Retry)
            assertEquals(1, repository.refreshCalls)

            repository.snapshots.emit(snapshot(listOf(jam("2026-07-25"))))
            val recovered = awaitMatching { it is PastJamsUiModel.Jams && it.staleness == null }
            assertEquals(PastJamsUiModel.Jams(title, listOf(julyRow), staleness = null), recovered)
        }
        assertEquals(1, repository.refreshCalls)
    }
}
