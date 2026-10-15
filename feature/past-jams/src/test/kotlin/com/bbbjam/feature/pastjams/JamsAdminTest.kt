package com.bbbjam.feature.pastjams

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.status.JamStatusBadgeUiModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `admin-jams-tab`: what the Jams tab draws for the admin (the upcoming and held-back jams above the
 * past ones) and for a musician (the past jams, nothing else). Copy is written out from the spec, never
 * read from [PastJamsCopy].
 */
class JamsAdminTest {
    private val repository = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val statuses = FakeJamStatusRepository()
    private val publishes = FakePublishRepository()
    private val creator = FakeJamCreator()

    /** 2 October 2026, 12:00 in Buenos Aires. */
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)
    private val upcomingDate = LocalDate.of(2026, 10, 31)

    private fun songs(count: Int) = (1..count).map {
        JamSong(it, SongId("song-$it"), "Song $it", "Someone", Key("A"), Lineup.default())
    }

    private fun jam(date: LocalDate, status: JamStatus, setlist: Setlist, venue: String = "La Macanuda") =
        Jam(date, LocalTime.of(21, 0), venue, status, setlist)

    private fun upcoming(status: JamStatus, count: Int = 1) = jam(upcomingDate, status, Setlist.Available(songs(count)))

    private val pastJam = jam(LocalDate.of(2026, 7, 25), JamStatus.PUBLISHED, Setlist.Available(songs(3)))

    private fun snapshot(upcoming: Jam? = null, heldBack: List<Jam> = emptyList(), past: List<Jam> = emptyList()) =
        JamsSnapshot(upcoming, past, fetched, heldBack)

    private fun presenter() = PastJamsPresenter(repository, calendar, session, statuses, publishes, creator)

    private fun PastJamsUiModel.admin(): JamsAdminUiModel? = when (this) {
        is PastJamsUiModel.Jams -> admin
        is PastJamsUiModel.Empty -> admin
        else -> null
    }

    private fun PastJamsUiModel.card(): FutureJamUiModel = checkNotNull(admin()).future.first()

    private fun PastJamsUiModel.controls(): JamControlsUiModel = checkNotNull(card().controls)

    private fun JamStatusUiModel.labels() = actions.map { it.label }

    private fun entry(id: Long, target: JamStatus, state: com.bbbjam.core.data.setlist.JamStatusChange.State) =
        com.bbbjam.core.data.setlist.JamStatusChange(id, upcomingDate, target, state)

    @Test
    fun `a musician gets the past jams exactly as before and no admin section, whatever else the snapshot holds`() =
        runTest {
            session.isAdmin.value = false
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(
                    snapshot(
                        upcoming = upcoming(JamStatus.CREATED),
                        heldBack = listOf(jam(LocalDate.of(2026, 11, 28), JamStatus.DRAFT, Setlist.Withheld)),
                        past = listOf(pastJam),
                    ),
                )
                val model = expectMostRecentItem() as PastJamsUiModel.Jams
                assertEquals("Jams anteriores", model.title)
                assertNull(model.admin)
                assertEquals(listOf(LocalDate.of(2026, 7, 25)), model.rows.map { it.date })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the admin sees the upcoming and the held-back jam as cards in date order, the held-back one with no action`() =
        runTest {
            val held = jam(LocalDate.of(2026, 11, 28), JamStatus.DRAFT, Setlist.Available(emptyList()), "El Sótano")
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(
                    snapshot(
                        upcoming = upcoming(JamStatus.CREATED, 12),
                        heldBack = listOf(held),
                        past = listOf(pastJam),
                    ),
                )
                val model = expectMostRecentItem() as PastJamsUiModel.Jams
                assertEquals("Jams", model.title)
                val admin = checkNotNull(model.admin)
                assertEquals("PRÓXIMAS", admin.upcomingHeading)
                assertEquals("ANTERIORES", admin.pastHeading)
                assertNull(admin.noFuture)
                assertEquals(listOf(upcomingDate, LocalDate.of(2026, 11, 28)), admin.future.map { it.date })

                val first = admin.future[0]
                assertEquals("Sábado 31 de octubre · 21:00", first.dateLabel)
                assertEquals("La Macanuda", first.venue)
                assertEquals(JamStatusBadgeUiModel("Creada", isPublished = false), first.badge)
                assertEquals("Los músicos ven la fecha, pero todavía no la lista.", first.note)
                assertEquals("12 temas", first.songCount)
                assertNull(first.heldBack)
                assertEquals(listOf("Pasar a borrador"), checkNotNull(first.controls).status.labels())
                assertEquals("Publicar lista", (first.controls?.publish as PublishUiModel.Idle).label)

                val second = admin.future[1]
                assertEquals("Sábado 28 de noviembre · 21:00", second.dateLabel)
                assertEquals(JamStatusBadgeUiModel("Borrador", isPublished = false), second.badge)
                assertEquals("Los músicos todavía no ven esta jam.", second.note)
                assertEquals("Sin temas todavía", second.songCount)
                assertEquals("En espera: su estado se cambia cuando sea la próxima jam.", second.heldBack)
                assertNull(second.controls)

                // The past rows are what a musician sees, under ANTERIORES.
                assertEquals(listOf(LocalDate.of(2026, 7, 25)), model.rows.map { it.date })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `song counts say one, none or unreadable and a past draft stays hidden for the admin too`() = runTest {
        val unreadable =
            jam(LocalDate.of(2026, 11, 28), JamStatus.CREATED, Setlist.Unavailable(SetlistProblem.INVALID_TAB))
        val pastDraft = jam(LocalDate.of(2026, 6, 27), JamStatus.DRAFT, Setlist.Available(songs(2)))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(
                snapshot(
                    upcoming = upcoming(JamStatus.PUBLISHED, 1),
                    heldBack = listOf(unreadable),
                    past = listOf(pastDraft),
                ),
            )
            val model = expectMostRecentItem()
            val admin = checkNotNull(model.admin())
            assertEquals(listOf("1 tema", "No se pudo leer la lista"), admin.future.map { it.songCount })
            // Only a past draft: nothing to list below ANTERIORES, so the empty block, with the admin section.
            assertTrue(model is PastJamsUiModel.Empty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with no future jam the admin gets the line, and the loading title is Jams for the admin`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            assertEquals("Jams", (expectMostRecentItem() as PastJamsUiModel.Loading).title)
            repository.snapshots.emit(snapshot(past = listOf(pastJam)))
            val model = expectMostRecentItem() as PastJamsUiModel.Jams
            val admin = checkNotNull(model.admin)
            assertEquals("No hay ninguna jam programada.", admin.noFuture)
            assertEquals(emptyList<FutureJamUiModel>(), admin.future)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each state offers the other actions, publish only before publication`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT)))
            val draft = expectMostRecentItem().controls()
            assertEquals(listOf("Pasar a creada"), draft.status.labels())
            assertTrue(draft.publish is PublishUiModel.Idle)

            repository.snapshots.emit(snapshot(upcoming(JamStatus.CREATED)))
            val created = expectMostRecentItem().controls()
            assertEquals(listOf("Pasar a borrador"), created.status.labels())
            assertTrue(created.publish is PublishUiModel.Idle)

            repository.snapshots.emit(snapshot(upcoming(JamStatus.PUBLISHED)))
            val published = expectMostRecentItem().controls()
            assertEquals(listOf("Pasar a creada", "Pasar a borrador"), published.status.labels())
            assertNull(published.publish)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `borrador to creada applies at once and a double tap calls once`() = runTest {
        val hold = kotlinx.coroutines.CompletableDeferred<Unit>().also { statuses.hold = it }
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT)))
            val change = expectMostRecentItem().controls().status
            change.events(JamStatusUiModel.Event.Request(JamStatus.CREATED))
            change.events(JamStatusUiModel.Event.Request(JamStatus.CREATED))
            assertEquals(listOf(upcomingDate to JamStatus.CREATED), statuses.calls)
            assertNull(change.confirming)
            hold.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `leaving publicada asks first, cancel writes nothing and confirm writes once`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.PUBLISHED)))
            val idle = expectMostRecentItem().controls().status
            idle.events(JamStatusUiModel.Event.Request(JamStatus.DRAFT))
            val asking = awaitItem().controls().status
            assertEquals(emptyList<Pair<LocalDate, JamStatus>>(), statuses.calls)
            val dialog = checkNotNull(asking.confirming)
            assertEquals("¿Dejar de publicar la lista?", dialog.prompt)
            assertEquals(
                "Los músicos van a dejar de ver esta jam, con su fecha y su lista, cuando actualicen la app.",
                dialog.body,
            )
            assertEquals("Pasar a borrador", dialog.confirmLabel)
            assertEquals("Cancelar", dialog.cancelLabel)

            asking.events(JamStatusUiModel.Event.Cancel)
            assertNull(awaitItem().controls().status.confirming)
            assertEquals(emptyList<Pair<LocalDate, JamStatus>>(), statuses.calls)

            asking.events(JamStatusUiModel.Event.Request(JamStatus.CREATED))
            val toCreated = awaitItem().controls().status
            assertEquals(
                "Los músicos van a dejar de ver la lista cuando actualicen la app. Van a seguir viendo la fecha.",
                checkNotNull(toCreated.confirming).body,
            )
            toCreated.events(JamStatusUiModel.Event.Confirm)
            toCreated.events(JamStatusUiModel.Event.Confirm)
            assertEquals(listOf(upcomingDate to JamStatus.CREATED), statuses.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sending shows one line and no actions and the badge stays the confirmed one`() = runTest {
        statuses.changes.value =
            listOf(entry(7, JamStatus.DRAFT, com.bbbjam.core.data.setlist.JamStatusChange.State.Sending))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.PUBLISHED)))
            val sending = expectMostRecentItem()
            assertEquals("Publicada", sending.card().badge.label)
            val change = sending.controls().status
            assertEquals("Cambiando el estado…", change.sending)
            assertEquals(emptyList<StatusActionUiModel>(), change.actions)
            assertNull(sending.controls().publish)

            statuses.changes.value = emptyList()
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT)))
            val done = expectMostRecentItem()
            assertEquals("Borrador", done.card().badge.label)
            assertEquals(listOf("Pasar a creada"), done.controls().status.labels())
            assertNull(done.controls().status.sending)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a sending publication hides the status actions`() = runTest {
        publishes.entries.value =
            listOf(
                com.bbbjam.core.data.setlist.SetlistPublish(
                    9,
                    upcomingDate,
                    com.bbbjam.core.data.setlist.SetlistPublish.State.Sending,
                ),
            )
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.CREATED)))
            val controls = expectMostRecentItem().controls()
            assertEquals(emptyList<StatusActionUiModel>(), controls.status.actions)
            assertNull(controls.status.sending)
            assertEquals("Publicando…", (controls.publish as PublishUiModel.Publishing).status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a status failure is a card with retry and close, an outdated script has the guide message and close only`() =
        runTest {
            val failed = com.bbbjam.core.data.setlist.JamStatusChange.State.Failed(
                com.bbbjam.core.data.admin.WriteOutcome.Offline,
            )
            statuses.changes.value = listOf(entry(3, JamStatus.DRAFT, failed))
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(snapshot(upcoming(JamStatus.PUBLISHED)))
                val change = expectMostRecentItem().controls().status
                val card = checkNotNull(change.failure)
                assertEquals("No se pudo cambiar el estado", card.title)
                assertEquals("No hay conexión. Probá de nuevo cuando tengas internet.", card.message)
                assertEquals("La jam sigue como estaba.", card.consequence)
                assertEquals("Reintentar", card.retryLabel)
                assertEquals("Cerrar", card.dismissLabel)
                change.events(JamStatusUiModel.Event.Retry(3))
                assertEquals(listOf(upcomingDate to JamStatus.DRAFT), statuses.calls)
                assertTrue(statuses.dismissed.contains(3L))

                statuses.changes.value = listOf(
                    entry(4, JamStatus.DRAFT, com.bbbjam.core.data.setlist.JamStatusChange.State.Outdated),
                )
                val outdated = expectMostRecentItem().controls().status
                val outdatedCard = checkNotNull(outdated.failure)
                assertEquals(
                    "El script de la planilla todavía no sabe cambiar el estado. " +
                        "Actualizalo con la guía y probá de nuevo.",
                    outdatedCard.message,
                )
                assertNull(outdatedCard.retryLabel)
                outdated.events(JamStatusUiModel.Event.Dismiss(4))
                assertTrue(statuses.dismissed.contains(4L))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `publish confirmation is explicit and a sending publish keeps the confirmed badge`() = runTest {
        val hold = kotlinx.coroutines.CompletableDeferred<Unit>().also { publishes.hold = it }
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT, 2)))
            val idle = expectMostRecentItem().controls().publish as PublishUiModel.Idle
            idle.events(PublishUiModel.Event.Request)
            val confirmation = awaitItem().controls().publish as PublishUiModel.Confirming
            assertEquals("¿Publicar la lista?", confirmation.prompt)
            assertEquals("Los músicos van a ver los 2 temas cuando abran o actualicen la app.", confirmation.details)
            assertEquals("Después podés volver a cambiar el estado desde acá.", confirmation.reversibleNote)
            assertEquals("Publicar", confirmation.confirmLabel)
            assertEquals("Cancelar", confirmation.cancelLabel)

            confirmation.events(PublishUiModel.Event.Cancel)
            val idleAgain = awaitItem().controls().publish as PublishUiModel.Idle
            idleAgain.events(PublishUiModel.Event.Request)
            val confirm = awaitItem().controls().publish as PublishUiModel.Confirming
            confirm.events(PublishUiModel.Event.Confirm)
            confirm.events(PublishUiModel.Event.Confirm)
            val sending = awaitItem()
            assertEquals(listOf(upcomingDate), publishes.calls)
            assertEquals("Borrador", sending.card().badge.label)
            assertEquals("Publicando…", (sending.controls().publish as PublishUiModel.Publishing).status)
            hold.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a single song says the song, an empty or unreadable list hides publish, a published jam has none`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.CREATED, 1)))
            val one = expectMostRecentItem().controls().publish as PublishUiModel.Idle
            one.events(PublishUiModel.Event.Request)
            val confirming = awaitItem().controls().publish as PublishUiModel.Confirming
            assertEquals("Los músicos van a ver el tema cuando abran o actualicen la app.", confirming.details)

            repository.snapshots.emit(snapshot(jam(upcomingDate, JamStatus.DRAFT, Setlist.Available(emptyList()))))
            assertNull(expectMostRecentItem().controls().publish)
            repository.snapshots.emit(
                snapshot(jam(upcomingDate, JamStatus.DRAFT, Setlist.Unavailable(SetlistProblem.INVALID_TAB))),
            )
            assertNull(expectMostRecentItem().controls().publish)
            repository.snapshots.emit(snapshot(upcoming(JamStatus.PUBLISHED)))
            assertNull(expectMostRecentItem().controls().publish)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed publish stays in the card, retry republishes and close dismisses, only before publication`() =
        runTest {
            val entry = com.bbbjam.core.data.setlist.SetlistPublish(
                51,
                upcomingDate,
                com.bbbjam.core.data.setlist.SetlistPublish.State.Failed(
                    com.bbbjam.core.data.admin.WriteOutcome.Offline,
                ),
            )
            publishes.entries.value = listOf(entry)
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT)))
                val failed = expectMostRecentItem().controls().publish as PublishUiModel.Failed
                assertEquals("No se pudo publicar la lista", failed.title)
                assertEquals(
                    "No pudimos confirmar la publicación: no hay conexión o el servidor tardó. " +
                        "Reintentá; si ya estaba publicada, no pasa nada.",
                    failed.message,
                )
                assertEquals("Los músicos siguen sin ver la lista.", failed.consequence)
                failed.events(PublishUiModel.Event.Retry)
                assertEquals(listOf(upcomingDate), publishes.calls)
                assertTrue(publishes.dismissed.contains(51L))
                failed.events(PublishUiModel.Event.Dismiss)
                assertTrue(publishes.dismissed.size >= 2)

                repository.snapshots.emit(snapshot(upcoming(JamStatus.PUBLISHED)))
                assertNull(expectMostRecentItem().controls().publish)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `logging out hides the admin section from the same snapshot`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming = upcoming(JamStatus.CREATED), past = listOf(pastJam)))
            assertNotNull((expectMostRecentItem() as PastJamsUiModel.Jams).admin)
            session.isAdmin.value = false
            val musician = expectMostRecentItem() as PastJamsUiModel.Jams
            assertNull(musician.admin)
            assertEquals("Jams anteriores", musician.title)
            assertFalse(musician.toString().contains("Pasar a"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
