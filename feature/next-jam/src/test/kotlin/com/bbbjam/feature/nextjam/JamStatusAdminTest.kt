package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.JamStatusChange
import com.bbbjam.core.data.setlist.SetlistPublish
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `admin-set-jam-status` (D-26): the admin's status actions on Próxima jam. Copy is written out from
 * the approved table, never read from [NextJamCopy].
 */
class JamStatusAdminTest {
    private val repository = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val setlist = FakeSetlistRepository()
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)
    private val redHouse = JamSong(1, SongId("red-house"), "Red House", "Jimi Hendrix", Key("Bb"), Lineup.default())

    private fun snapshot(status: JamStatus, songs: List<JamSong> = listOf(redHouse)) = JamsSnapshot(
        upcoming = Jam(jamDate, LocalTime.of(21, 0), "La Macanuda", status, Setlist.Available(songs)),
        past = emptyList(),
        freshness = fetched,
    )

    private fun presenter() = NextJamPresenter(repository, calendar, session, setlist)

    private fun NextJamUiModel.status() = checkNotNull((this as NextJamUiModel.Jam).admin).status

    private fun JamStatusUiModel.labels() = actions.map { it.label }

    private fun entry(id: Long, target: JamStatus, state: JamStatusChange.State) =
        JamStatusChange(id, jamDate, target, state)

    @Test
    fun `each state offers the other actions, publish only before publication`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(JamStatus.DRAFT))
            val draft = expectMostRecentItem().status()
            assertEquals(listOf("Pasar a creada"), checkNotNull(draft.statusChange).labels())
            assertTrue(draft.publish is PublishUiModel.Idle)

            repository.snapshots.emit(snapshot(JamStatus.CREATED))
            val created = expectMostRecentItem().status()
            assertEquals(listOf("Pasar a borrador"), checkNotNull(created.statusChange).labels())
            assertTrue(created.publish is PublishUiModel.Idle)

            repository.snapshots.emit(snapshot(JamStatus.PUBLISHED))
            val published = expectMostRecentItem().status()
            assertEquals(listOf("Pasar a creada", "Pasar a borrador"), checkNotNull(published.statusChange).labels())
            assertNull(published.publish)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a musician gets no admin model at all`() = runTest {
        session.isAdmin.value = false
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(JamStatus.PUBLISHED))
            val model = expectMostRecentItem() as NextJamUiModel.Jam
            assertNull(model.admin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `borrador to creada applies at once and a double tap calls once`() = runTest {
        val hold = CompletableDeferred<Unit>().also { setlist.statusHold = it }
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(JamStatus.DRAFT))
            val change = checkNotNull(expectMostRecentItem().status().statusChange)
            change.events(JamStatusUiModel.Event.Request(JamStatus.CREATED))
            change.events(JamStatusUiModel.Event.Request(JamStatus.CREATED))
            assertEquals(listOf(jamDate to JamStatus.CREATED), setlist.statusCalls)
            assertNull(change.confirming)
            hold.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `leaving publicada asks first, cancel writes nothing and confirm writes once`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(JamStatus.PUBLISHED))
            val idle = checkNotNull(expectMostRecentItem().status().statusChange)
            idle.events(JamStatusUiModel.Event.Request(JamStatus.DRAFT))
            val asking = checkNotNull(awaitItem().status().statusChange)
            assertEquals(emptyList<Pair<LocalDate, JamStatus>>(), setlist.statusCalls)
            val dialog = checkNotNull(asking.confirming)
            assertEquals("¿Dejar de publicar la lista?", dialog.prompt)
            assertEquals(
                "Los músicos van a dejar de ver esta jam, con su fecha y su lista, cuando actualicen la app.",
                dialog.body,
            )
            assertEquals("Pasar a borrador", dialog.confirmLabel)
            assertEquals("Cancelar", dialog.cancelLabel)

            asking.events(JamStatusUiModel.Event.Cancel)
            assertNull(awaitItem().status().statusChange?.confirming)
            assertEquals(emptyList<Pair<LocalDate, JamStatus>>(), setlist.statusCalls)

            asking.events(JamStatusUiModel.Event.Request(JamStatus.CREATED))
            val toCreated = checkNotNull(awaitItem().status().statusChange)
            assertEquals(
                "Los músicos van a dejar de ver la lista cuando actualicen la app. Van a seguir viendo la fecha.",
                checkNotNull(toCreated.confirming).body,
            )
            toCreated.events(JamStatusUiModel.Event.Confirm)
            toCreated.events(JamStatusUiModel.Event.Confirm)
            assertEquals(listOf(jamDate to JamStatus.CREATED), setlist.statusCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sending shows one line and no actions and the badge stays the confirmed one`() = runTest {
        setlist.statusChanges.value = listOf(entry(7, JamStatus.DRAFT, JamStatusChange.State.Sending))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(JamStatus.PUBLISHED))
            val sending = expectMostRecentItem().status()
            assertEquals("Publicada", sending.badge)
            val change = checkNotNull(sending.statusChange)
            assertEquals("Cambiando el estado…", change.sending)
            assertEquals(emptyList<StatusActionUiModel>(), change.actions)
            assertNull(sending.publish)

            setlist.statusChanges.value = emptyList()
            repository.snapshots.emit(snapshot(JamStatus.DRAFT))
            val done = expectMostRecentItem().status()
            assertEquals("Borrador", done.badge)
            assertEquals(listOf("Pasar a creada"), checkNotNull(done.statusChange).labels())
            assertNull(done.statusChange?.sending)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a sending publication hides the status actions`() = runTest {
        setlist.publishes.value = listOf(SetlistPublish(9, jamDate, SetlistPublish.State.Sending))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(JamStatus.CREATED))
            val status = expectMostRecentItem().status()
            assertEquals(emptyList<StatusActionUiModel>(), checkNotNull(status.statusChange).actions)
            assertNull(status.statusChange?.sending)
            assertEquals("Publicando…", (status.publish as PublishUiModel.Publishing).status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure is a card with retry and close, an outdated script has the guide message and close only`() =
        runTest {
            setlist.statusChanges.value = listOf(
                entry(3, JamStatus.DRAFT, JamStatusChange.State.Failed(WriteOutcome.Offline)),
            )
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
                repository.snapshots.emit(snapshot(JamStatus.PUBLISHED))
                val failed = checkNotNull(expectMostRecentItem().status().statusChange)
                val card = checkNotNull(failed.failure)
                assertEquals("No se pudo cambiar el estado", card.title)
                assertEquals("No hay conexión. Probá de nuevo cuando tengas internet.", card.message)
                assertEquals("La jam sigue como estaba.", card.consequence)
                assertEquals("Reintentar", card.retryLabel)
                assertEquals("Cerrar", card.dismissLabel)
                failed.events(JamStatusUiModel.Event.Retry(3))
                assertEquals(listOf(jamDate to JamStatus.DRAFT), setlist.statusCalls)
                assertTrue(setlist.dismissed.contains(3L))

                setlist.statusChanges.value = listOf(entry(4, JamStatus.DRAFT, JamStatusChange.State.Outdated))
                val outdated = checkNotNull(expectMostRecentItem().status().statusChange)
                val outdatedCard = checkNotNull(outdated.failure)
                assertEquals(
                    "El script de la planilla todavía no sabe cambiar el estado. " +
                        "Actualizalo con la guía y probá de nuevo.",
                    outdatedCard.message,
                )
                assertNull(outdatedCard.retryLabel)
                outdated.events(JamStatusUiModel.Event.Dismiss(4))
                assertTrue(setlist.dismissed.contains(4L))
                assertNotNull(outdated.events)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
