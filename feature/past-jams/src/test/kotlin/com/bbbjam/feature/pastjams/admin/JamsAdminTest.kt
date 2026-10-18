package com.bbbjam.feature.pastjams.admin

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
import com.bbbjam.core.ui.status.JamStatusChipsUiModel
import com.bbbjam.feature.pastjams.FakeAdminSession
import com.bbbjam.feature.pastjams.FakeJamCreator
import com.bbbjam.feature.pastjams.FakeJamEditRepository
import com.bbbjam.feature.pastjams.FakeJamStatusRepository
import com.bbbjam.feature.pastjams.FakeJamsRepository
import com.bbbjam.feature.pastjams.FakePublishRepository
import com.bbbjam.feature.pastjams.PastJamsCopy
import com.bbbjam.feature.pastjams.PastJamsPresenter
import com.bbbjam.feature.pastjams.PastJamsUiModel
import com.bbbjam.feature.pastjams.status.JamStatusUiModel
import com.bbbjam.feature.pastjams.status.PublishUiModel
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

    private val edits = FakeJamEditRepository()

    private fun presenter() =
        PastJamsPresenter(repository, calendar, session, JamsAdminWrites(statuses, publishes, creator, edits))

    private fun PastJamsUiModel.admin(): JamsAdminUiModel? = when (this) {
        is PastJamsUiModel.Jams -> admin
        is PastJamsUiModel.Empty -> admin
        else -> null
    }

    private fun PastJamsUiModel.card(): FutureJamUiModel = checkNotNull(admin()).future.first()

    private fun PastJamsUiModel.controls(): JamControlsUiModel = card().controls

    /** The labels of the chips the admin can tap to change the status (the selected one is not a change). */
    private fun JamStatusUiModel.labels() = chips.chips.filter { it.enabled && !it.selected }.map { it.label }

    private fun JamStatusUiModel.selected() = chips.chips.single { it.selected }.label

    private fun JamStatusUiModel.select(status: JamStatus) = chips.events(JamStatusChipsUiModel.Event.Select(status))

    private fun JamStatusUiModel.allDisabled() = chips.chips.none { it.enabled }

    /** The chip drawn with the pulsing outline: the status being applied. */
    private fun JamStatusUiModel.pending() = chips.chips.filter { it.pending }.map { it.label }

    private fun entry(
        id: Long,
        target: JamStatus,
        state: com.bbbjam.core.data.setlist.jamstatus.JamStatusChange.State,
    ) = com.bbbjam.core.data.setlist.jamstatus.JamStatusChange(id, upcomingDate, target, state)

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
                assertEquals("Creada", first.controls.status.selected())
                assertEquals("Los músicos ven la fecha, pero todavía no la lista.", first.note)
                assertEquals("12 temas", first.songCount)
                assertNull(first.heldBack)
                assertEquals(listOf("Borrador", "Publicada"), first.controls.status.labels())
                assertNull(first.controls.status.publishUnavailable)
                assertNull(first.controls.publish)

                val second = admin.future[1]
                assertEquals("Sábado 28 de noviembre · 21:00", second.dateLabel)
                assertEquals("Borrador", second.controls.status.selected())
                assertEquals("Los músicos todavía no ven esta jam.", second.note)
                assertEquals("Sin temas todavía", second.songCount)
                assertEquals("En espera: la próxima jam es la del sábado 31 de octubre.", second.heldBack)
                assertEquals(listOf("Creada"), second.controls.status.labels())
                assertEquals("Solo la próxima jam se puede publicar.", second.controls.status.publishUnavailable)
                assertNull(second.controls.publish)

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
    fun `each state offers the other chips, Publicada only before publication and never from the confirm`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT)))
            val draft = expectMostRecentItem().controls()
            assertEquals(listOf("Creada", "Publicada"), draft.status.labels())
            assertEquals("Borrador", draft.status.selected())
            assertNull(draft.publish)

            repository.snapshots.emit(snapshot(upcoming(JamStatus.CREATED)))
            val created = expectMostRecentItem().controls()
            assertEquals(listOf("Borrador", "Publicada"), created.status.labels())
            assertEquals("Creada", created.status.selected())
            assertNull(created.publish)

            repository.snapshots.emit(snapshot(upcoming(JamStatus.PUBLISHED)))
            val published = expectMostRecentItem().controls()
            assertEquals(listOf("Borrador", "Creada"), published.status.labels())
            assertEquals("Publicada", published.status.selected())
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
            change.select(JamStatus.CREATED)
            change.select(JamStatus.CREATED)
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
            idle.select(JamStatus.DRAFT)
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

            asking.select(JamStatus.CREATED)
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
    fun `a sending change marks the requested chip pending, all chips disabled, no text, selection unchanged`() =
        runTest {
            statuses.changes.value =
                listOf(entry(7, JamStatus.DRAFT, com.bbbjam.core.data.setlist.jamstatus.JamStatusChange.State.Sending))
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(snapshot(upcoming(JamStatus.PUBLISHED)))
                val sending = expectMostRecentItem()
                assertEquals("Publicada", sending.controls().status.selected())
                val change = sending.controls().status
                assertEquals(listOf("Borrador"), change.pending())
                assertEquals(
                    "Cambiando el estado a Borrador…",
                    change.chips.chips.single { it.pending }.description,
                )
                assertTrue(change.allDisabled())
                assertFalse(sending.toString().contains("Cambiando el estado…"))
                assertNull(sending.controls().publish)

                statuses.changes.value = emptyList()
                repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT)))
                val done = expectMostRecentItem()
                assertEquals("Borrador", done.controls().status.selected())
                assertEquals(listOf("Creada", "Publicada"), done.controls().status.labels())
                assertEquals(emptyList<String>(), done.controls().status.pending())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a sending publication marks Publicada pending, disables the chips and draws no text`() = runTest {
        publishes.entries.value =
            listOf(
                com.bbbjam.core.data.setlist.publish.SetlistPublish(
                    9,
                    upcomingDate,
                    com.bbbjam.core.data.setlist.publish.SetlistPublish.State.Sending,
                ),
            )
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.CREATED)))
            val controls = expectMostRecentItem().controls()
            assertTrue(controls.status.allDisabled())
            assertEquals("Creada", controls.status.selected())
            assertEquals(listOf("Publicada"), controls.status.pending())
            assertEquals("Publicando la lista…", controls.status.chips.chips.single { it.pending }.description)
            assertNull(controls.publish)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a status failure is a card with retry and close, an outdated script has the guide message and close only`() =
        runTest {
            val failed = com.bbbjam.core.data.setlist.jamstatus.JamStatusChange.State.Failed(
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
                    entry(4, JamStatus.DRAFT, com.bbbjam.core.data.setlist.jamstatus.JamStatusChange.State.Outdated),
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
    fun `publish confirmation is explicit and a sending publish keeps the confirmed chip`() = runTest {
        val hold = kotlinx.coroutines.CompletableDeferred<Unit>().also { publishes.hold = it }
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT, 2)))
            val idle = expectMostRecentItem().controls()
            assertNull(idle.publish)
            idle.status.select(JamStatus.PUBLISHED)
            assertEquals(emptyList<LocalDate>(), publishes.calls)
            val confirmation = awaitItem().controls().publish as PublishUiModel.Confirming
            assertEquals("¿Publicar la lista?", confirmation.prompt)
            assertEquals("Los músicos van a ver los 2 temas cuando abran o actualicen la app.", confirmation.details)
            assertEquals("Después podés volver a cambiar el estado desde acá.", confirmation.reversibleNote)
            assertEquals("Publicar", confirmation.confirmLabel)
            assertEquals("Cancelar", confirmation.cancelLabel)

            confirmation.events(PublishUiModel.Event.Cancel)
            val idleAgain = awaitItem().controls()
            assertNull(idleAgain.publish)
            assertEquals(emptyList<LocalDate>(), publishes.calls)
            idleAgain.status.select(JamStatus.PUBLISHED)
            val confirm = awaitItem().controls().publish as PublishUiModel.Confirming
            confirm.events(PublishUiModel.Event.Confirm)
            confirm.events(PublishUiModel.Event.Confirm)
            val sending = awaitItem()
            assertEquals(listOf(upcomingDate), publishes.calls)
            assertEquals("Borrador", sending.controls().status.selected())
            assertEquals(listOf("Publicada"), sending.controls().status.pending())
            assertNull(sending.controls().publish)
            hold.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `publish confirmation clears when the jam leaves and needs a new chip tap after it returns`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT, 2)))
            val idle = expectMostRecentItem().controls().status
            idle.select(JamStatus.PUBLISHED)
            assertTrue(awaitItem().controls().publish is PublishUiModel.Confirming)

            repository.snapshots.emit(snapshot())
            assertTrue(checkNotNull(expectMostRecentItem().admin()).future.isEmpty())
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT, 2)))
            val returned = expectMostRecentItem().controls()
            assertNull(returned.publish)
            returned.status.select(JamStatus.PUBLISHED)
            assertTrue(awaitItem().controls().publish is PublishUiModel.Confirming)
            assertEquals(emptyList<LocalDate>(), publishes.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `publish confirmation clears when the jam is published or a write starts`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT, 2)))
            val idle = expectMostRecentItem().controls().status
            idle.select(JamStatus.PUBLISHED)
            assertTrue(awaitItem().controls().publish is PublishUiModel.Confirming)

            publishes.entries.value = listOf(
                com.bbbjam.core.data.setlist.publish.SetlistPublish(
                    55,
                    upcomingDate,
                    com.bbbjam.core.data.setlist.publish.SetlistPublish.State.Sending,
                ),
            )
            val sending = expectMostRecentItem().controls()
            assertNull(sending.publish)
            assertEquals(listOf("Publicada"), sending.status.pending())
            publishes.entries.value = emptyList()
            val idleAgain = expectMostRecentItem().controls()
            assertNull(idleAgain.publish)

            idleAgain.status.select(JamStatus.PUBLISHED)
            assertTrue(awaitItem().controls().publish is PublishUiModel.Confirming)
            repository.snapshots.emit(snapshot(upcoming(JamStatus.PUBLISHED, 2)))
            assertNull(expectMostRecentItem().controls().publish)
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT, 2)))
            assertNull(expectMostRecentItem().controls().publish)
            assertEquals(emptyList<LocalDate>(), publishes.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a single song says the song, an empty or unreadable list disables Publicada with a reason`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.CREATED, 1)))
            expectMostRecentItem().controls().status.select(JamStatus.PUBLISHED)
            val confirming = awaitItem().controls().publish as PublishUiModel.Confirming
            assertEquals("Los músicos van a ver el tema cuando abran o actualicen la app.", confirming.details)

            repository.snapshots.emit(snapshot(jam(upcomingDate, JamStatus.DRAFT, Setlist.Available(emptyList()))))
            val empty = expectMostRecentItem().controls()
            assertNull(empty.publish)
            assertEquals(listOf("Creada"), empty.status.labels())
            assertEquals("Para publicar, agregá al menos un tema en Próxima jam.", empty.status.publishUnavailable)
            empty.status.select(JamStatus.PUBLISHED)
            expectNoEvents()
            assertEquals(emptyList<LocalDate>(), publishes.calls)
            repository.snapshots.emit(
                snapshot(jam(upcomingDate, JamStatus.DRAFT, Setlist.Unavailable(SetlistProblem.INVALID_TAB))),
            )
            val unreadable = expectMostRecentItem().controls()
            assertNull(unreadable.publish)
            assertEquals(listOf("Creada"), unreadable.status.labels())
            assertEquals("Para publicar, la lista se tiene que poder leer.", unreadable.status.publishUnavailable)
            unreadable.status.select(JamStatus.PUBLISHED)
            expectNoEvents()
            assertEquals(emptyList<LocalDate>(), publishes.calls)
            repository.snapshots.emit(snapshot(upcoming(JamStatus.PUBLISHED)))
            val published = expectMostRecentItem().controls()
            assertNull(published.publish)
            assertNull(published.status.publishUnavailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed publish stays in the card, retry republishes and close dismisses, only before publication`() =
        runTest {
            val entry = com.bbbjam.core.data.setlist.publish.SetlistPublish(
                51,
                upcomingDate,
                com.bbbjam.core.data.setlist.publish.SetlistPublish.State.Failed(
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

    private val heldDate = LocalDate.of(2026, 11, 28)

    @Test
    fun `tapping the selected chip writes nothing and Publicada on a held-back card is disabled with its reason`() =
        runTest {
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(
                    snapshot(
                        upcoming = upcoming(JamStatus.CREATED, 2),
                        heldBack = listOf(jam(heldDate, JamStatus.DRAFT, Setlist.Available(songs(3)))),
                    ),
                )
                val model = expectMostRecentItem()
                val current = model.controls().status
                current.select(JamStatus.CREATED)
                assertEquals(emptyList<Pair<LocalDate, JamStatus>>(), statuses.calls)

                val held = checkNotNull(model.admin()).future[1].controls.status
                assertEquals(listOf("Creada"), held.labels())
                assertEquals("Solo la próxima jam se puede publicar.", held.publishUnavailable)
                held.select(JamStatus.PUBLISHED)
                assertEquals(emptyList<LocalDate>(), publishes.calls)
                assertEquals(emptyList<Pair<LocalDate, JamStatus>>(), statuses.calls)
                held.select(JamStatus.CREATED)
                assertEquals(listOf(heldDate to JamStatus.CREATED), statuses.calls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `tapping Publicada again while publish confirmation is open is a no-op`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT, 2)))
            expectMostRecentItem().controls().status.select(JamStatus.PUBLISHED)
            val confirming = awaitItem().controls()
            assertTrue(confirming.publish is PublishUiModel.Confirming)
            // Publicada again while confirming is a no-op, and the selection has not moved.
            confirming.status.select(JamStatus.PUBLISHED)
            expectNoEvents()
            assertEquals("Borrador", confirming.status.selected())
            assertEquals(emptyList<Pair<LocalDate, JamStatus>>(), statuses.calls)
            assertEquals(emptyList<LocalDate>(), publishes.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed status change keeps the previous chip selected and the failure card`() = runTest {
        val failed = com.bbbjam.core.data.setlist.jamstatus.JamStatusChange.State.Failed(
            com.bbbjam.core.data.admin.WriteOutcome.Offline,
        )
        statuses.changes.value = listOf(entry(3, JamStatus.CREATED, failed))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT, 1)))
            val status = expectMostRecentItem().controls().status
            assertEquals("Borrador", status.selected())
            assertEquals(listOf("Creada", "Publicada"), status.labels())
            assertEquals("No se pudo cambiar el estado", checkNotNull(status.failure).title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed change or publication leaves no chip pending`() = runTest {
        statuses.changes.value = listOf(
            entry(
                7,
                JamStatus.CREATED,
                com.bbbjam.core.data.setlist.jamstatus.JamStatusChange.State.Failed(
                    com.bbbjam.core.data.admin.WriteOutcome.Offline,
                ),
            ),
        )
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT, 2)))
            val status = expectMostRecentItem().controls().status
            assertEquals(emptyList<String>(), status.pending())
            assertEquals("No se pudo cambiar el estado", checkNotNull(status.failure).title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `while a change is sending a tap on any chip writes nothing`() = runTest {
        statuses.changes.value =
            listOf(entry(7, JamStatus.CREATED, com.bbbjam.core.data.setlist.jamstatus.JamStatusChange.State.Sending))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming(JamStatus.DRAFT, 2)))
            val status = expectMostRecentItem().controls().status
            JamStatus.entries.forEach { status.select(it) }
            assertEquals(emptyList<Pair<LocalDate, JamStatus>>(), statuses.calls)
            assertEquals(emptyList<LocalDate>(), publishes.calls)
            assertNull(status.publishUnavailable)
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
            assertFalse(musician.toString().contains("Estado:"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
