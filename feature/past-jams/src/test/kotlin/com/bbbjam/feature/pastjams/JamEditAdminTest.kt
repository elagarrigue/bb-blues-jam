package com.bbbjam.feature.pastjams

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.JamEdit
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.SongId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `admin-edit-delete-jam`: the edit and delete controls of the Jams tab's future cards, the dialogs and the
 * results. Copy is written out from the spec, never read from [PastJamsCopy].
 */
class JamEditAdminTest {
    private val repository = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val statuses = FakeJamStatusRepository()
    private val publishes = FakePublishRepository()
    private val creator = FakeJamCreator()
    private val edits = FakeJamEditRepository()

    /** 2 October 2026, 12:00 in Buenos Aires. */
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)
    private val upcomingDate = LocalDate.of(2026, 10, 31)
    private val heldDate = LocalDate.of(2026, 11, 13)

    private fun songs(count: Int) = (1..count).map {
        JamSong(it, SongId("song-$it"), "Song $it", "Someone", Key("A"), Lineup.default())
    }

    private fun jam(date: LocalDate, status: JamStatus, setlist: Setlist, venue: String = "La Macanuda") =
        Jam(date, LocalTime.of(21, 0), venue, status, setlist)

    private fun upcoming(status: JamStatus = JamStatus.DRAFT, count: Int = 12) =
        jam(upcomingDate, status, Setlist.Available(songs(count)))

    private fun held(status: JamStatus = JamStatus.DRAFT, count: Int = 0) =
        jam(heldDate, status, Setlist.Available(songs(count)), "El Sotano")

    private fun snapshot(upcoming: Jam? = upcoming(), heldBack: List<Jam> = listOf(held())) =
        JamsSnapshot(upcoming, emptyList(), fetched, heldBack)

    private fun presenter() =
        PastJamsPresenter(repository, calendar, session, JamsAdminWrites(statuses, publishes, creator, edits))

    private fun PastJamsUiModel.admin(): JamsAdminUiModel = checkNotNull(
        when (this) {
            is PastJamsUiModel.Jams -> admin
            is PastJamsUiModel.Empty -> admin
            else -> null
        },
    )

    private fun PastJamsUiModel.card(index: Int) = admin().future[index]

    private fun JamEdit.Change.at(id: Long, date: LocalDate, state: JamEdit.State) = JamEdit(id, date, this, state)

    @Test
    fun `every future card can be edited, deleting needs an available setlist, held-back cards get status`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val model = expectMostRecentItem()
            val first = model.card(0)
            val second = model.card(1)
            assertEquals("Editar la jam", first.manage.editLabel)
            assertEquals("Borrar la jam", first.manage.deleteLabel)
            assertEquals("Editar la jam", second.manage.editLabel)
            assertEquals("Borrar la jam", second.manage.deleteLabel)
            assertEquals(listOf("Pasar a creada"), checkNotNull(second.controls).status.actions.map { it.label })
            assertNull(checkNotNull(second.controls).publish)
            assertTrue(checkNotNull(first.controls).publish is PublishUiModel.Idle)

            repository.snapshots.emit(
                snapshot(
                    heldBack = listOf(
                        jam(heldDate, JamStatus.DRAFT, Setlist.Unavailable(SetlistProblem.INVALID_TAB)),
                    ),
                ),
            )
            val unreadable = expectMostRecentItem().card(1)
            assertEquals("Editar la jam", unreadable.manage.editLabel)
            assertNull(unreadable.manage.deleteLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a held-back card names the upcoming jam, a visible one behind a borrador says musicians see it`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(heldBack = listOf(held(JamStatus.CREATED))))
            val model = expectMostRecentItem()
            assertEquals("En espera: la próxima jam es la del sábado 31 de octubre.", model.card(1).heldBack)
            assertEquals(
                "Los músicos la ven como la próxima jam, porque la del sábado 31 de octubre está en borrador.",
                model.card(1).musicianNote,
            )
            assertNull(model.card(0).heldBack)
            assertNull(model.card(0).musicianNote)

            repository.snapshots.emit(snapshot(heldBack = listOf(held(JamStatus.DRAFT))))
            assertNull(expectMostRecentItem().card(1).musicianNote)
            repository.snapshots.emit(
                snapshot(upcoming = upcoming(JamStatus.CREATED), heldBack = listOf(held(JamStatus.PUBLISHED))),
            )
            assertNull(expectMostRecentItem().card(1).musicianNote)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only the held-back jam musicians see first gets the note, not every visible one behind a borrador`() =
        runTest {
            val third = jam(LocalDate.of(2026, 11, 20), JamStatus.CREATED, Setlist.Available(emptyList()))
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(snapshot(heldBack = listOf(held(JamStatus.CREATED), third)))
                val model = expectMostRecentItem()
                assertNull(model.card(0).musicianNote)
                assertEquals(
                    "Los músicos la ven como la próxima jam, porque la del sábado 31 de octubre está en borrador.",
                    model.card(1).musicianNote,
                )
                assertNull(model.card(2).musicianNote)

                repository.snapshots.emit(snapshot(heldBack = listOf(held(JamStatus.DRAFT), third)))
                val skipped = expectMostRecentItem()
                assertNull(skipped.card(1).musicianNote)
                assertEquals(
                    "Los músicos la ven como la próxima jam, porque la del sábado 31 de octubre está en borrador.",
                    skipped.card(2).musicianNote,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a musician sees no admin section`() = runTest {
        session.isAdmin.value = false
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val model = expectMostRecentItem()
            assertTrue(model is PastJamsUiModel.Empty)
            assertNull((model as PastJamsUiModel.Empty).admin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Editar la jam opens the form seeded with the jam, Cancelar writes nothing`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val closed = expectMostRecentItem()
            assertNull(closed.admin().editForm)
            closed.card(0).manage.events(JamManageUiModel.Event.Edit)
            val open = expectMostRecentItem()
            val form = checkNotNull(open.admin().editForm)
            assertEquals("Editar la jam", form.title)
            assertEquals("Fecha", form.dateLabel)
            assertEquals("DD/MM/AAAA", form.dateHint)
            assertEquals("Hora", form.timeLabel)
            assertEquals("Lugar", form.venueLabel)
            assertEquals("31/10/2026", form.date)
            assertEquals("21:00", form.time)
            assertEquals("La Macanuda", form.venue)
            assertEquals(false, form.canSave)
            assertEquals("Guardar", form.saveLabel)
            assertEquals("Cancelar", form.cancelLabel)
            assertNull(form.dateNote)
            assertNull(form.visibilityNote)

            form.events(EditJamFormUiModel.Event.Cancel)
            assertNull(expectMostRecentItem().admin().editForm)
            assertTrue(edits.updates.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing shows the errors and notes, Guardar calls updateJam once with the parsed values`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming = upcoming(JamStatus.CREATED)))
            expectMostRecentItem().card(0).manage.events(JamManageUiModel.Event.Edit)
            var form = checkNotNull(expectMostRecentItem().admin().editForm)
            assertEquals("Los músicos ven el cambio cuando actualicen la app.", form.visibilityNote)

            form.events(EditJamFormUiModel.Event.Edit(NewJamField.DATE, "14/11/2026"))
            form = checkNotNull(expectMostRecentItem().admin().editForm)
            assertEquals(
                "Tiene que ser antes de la jam del viernes 13 de noviembre: si no, esa pasaría a ser la próxima.",
                form.dateError,
            )
            assertEquals(false, form.canSave)

            form.events(EditJamFormUiModel.Event.Edit(NewJamField.TIME, "25:00"))
            form = checkNotNull(expectMostRecentItem().admin().editForm)
            assertEquals("Escribí la hora como HH:MM, por ejemplo 21:00.", form.timeError)

            form.events(EditJamFormUiModel.Event.Edit(NewJamField.DATE, "7/11/2026"))
            form = checkNotNull(expectMostRecentItem().admin().editForm)
            form.events(EditJamFormUiModel.Event.Edit(NewJamField.TIME, "20:30"))
            form = checkNotNull(expectMostRecentItem().admin().editForm)
            assertNull(form.dateError)
            assertNull(form.timeError)
            assertEquals("La lista y los músicos anotados pasan a la nueva fecha.", form.dateNote)
            assertEquals(true, form.canSave)

            form.events(EditJamFormUiModel.Event.Save)
            form.events(EditJamFormUiModel.Event.Save)
            assertEquals(
                listOf(
                    FakeJamEditRepository.Update(
                        upcomingDate,
                        LocalDate.of(2026, 11, 7),
                        LocalTime.of(20, 30),
                        "La Macanuda",
                    ),
                ),
                edits.updates,
            )
            assertNull(expectMostRecentItem().admin().editForm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the held-back form blocks a date before the upcoming jam`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            expectMostRecentItem().card(1).manage.events(JamManageUiModel.Event.Edit)
            val form = checkNotNull(expectMostRecentItem().admin().editForm)
            assertEquals("13/11/2026", form.date)
            form.events(EditJamFormUiModel.Event.Edit(NewJamField.DATE, "30/10/2026"))
            val blocked = checkNotNull(expectMostRecentItem().admin().editForm)
            assertEquals(
                "Tiene que ser después de la próxima jam (sábado 31 de octubre): si no, la dejaría en espera.",
                blocked.dateError,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Borrar la jam on the upcoming jam names the songs, the status and the next jam, and confirm calls once`() =
        runTest {
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(snapshot(upcoming = upcoming(JamStatus.PUBLISHED, 12)))
                expectMostRecentItem().card(0).manage.events(JamManageUiModel.Event.Delete)
                val dialog = checkNotNull(expectMostRecentItem().admin().deleteConfirm)
                assertEquals("¿Borrar la jam del sábado 31 de octubre?", dialog.title)
                assertEquals(
                    listOf(
                        "Se pierden los 12 temas de la lista y los músicos anotados.",
                        "Los músicos ya ven esta jam y su lista: la van a dejar de ver cuando actualicen la app.",
                        "La próxima jam pasa a ser la del viernes 13 de noviembre.",
                        "No se puede deshacer.",
                    ),
                    dialog.lines,
                )
                assertEquals("Borrar la jam", dialog.confirmLabel)
                assertEquals("Cancelar", dialog.cancelLabel)

                dialog.events(DeleteJamUiModel.Event.Cancel)
                val cancelled = expectMostRecentItem()
                assertNull(cancelled.admin().deleteConfirm)
                assertTrue(edits.deletes.isEmpty())

                cancelled.card(0).manage.events(JamManageUiModel.Event.Delete)
                val again = checkNotNull(expectMostRecentItem().admin().deleteConfirm)
                again.events(DeleteJamUiModel.Event.Confirm)
                again.events(DeleteJamUiModel.Event.Confirm)
                assertEquals(listOf(upcomingDate to 12), edits.deletes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the delete dialog varies with the status, the count and the jam`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming = upcoming(JamStatus.CREATED, 1), heldBack = emptyList()))
            expectMostRecentItem().card(0).manage.events(JamManageUiModel.Event.Delete)
            assertEquals(
                listOf(
                    "Se pierde el tema de la lista y los músicos anotados.",
                    "Los músicos ven la fecha: la van a dejar de ver cuando actualicen la app.",
                    "No va a quedar ninguna jam programada.",
                    "No se puede deshacer.",
                ),
                checkNotNull(expectMostRecentItem().admin().deleteConfirm).lines,
            )

            repository.snapshots.emit(snapshot(upcoming = upcoming(JamStatus.DRAFT, 0)))
            val model = expectMostRecentItem()
            checkNotNull(model.admin().deleteConfirm).events(DeleteJamUiModel.Event.Cancel)
            expectMostRecentItem().card(1).manage.events(JamManageUiModel.Event.Delete)
            val held = checkNotNull(expectMostRecentItem().admin().deleteConfirm)
            assertEquals("¿Borrar la jam del viernes 13 de noviembre?", held.title)
            assertEquals(
                listOf(
                    "La lista está vacía.",
                    "La próxima jam sigue siendo la del sábado 31 de octubre.",
                    "No se puede deshacer.",
                ),
                held.lines,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `while an edit is sending its card shows one line and no actions, the other card is unaffected`() = runTest {
        edits.entries.value = listOf(
            JamEdit.Change.Update(LocalDate.of(2026, 11, 7), LocalTime.of(20, 0), "Bar")
                .at(5, upcomingDate, JamEdit.State.Sending),
        )
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(upcoming = upcoming(JamStatus.CREATED)))
            val model = expectMostRecentItem()
            val sending = model.card(0)
            assertEquals("Guardando los cambios…", sending.manage.sending)
            assertNull(sending.manage.editLabel)
            assertNull(sending.manage.deleteLabel)
            assertEquals(emptyList<StatusActionUiModel>(), checkNotNull(sending.controls).status.actions)
            assertNull(checkNotNull(sending.controls).publish)
            val other = model.card(1)
            assertNull(other.manage.sending)
            assertEquals("Editar la jam", other.manage.editLabel)

            edits.entries.value = listOf(
                JamEdit.Change.Delete(0).at(6, heldDate, JamEdit.State.Sending),
            )
            val deleting = expectMostRecentItem().card(1)
            assertEquals("Borrando la jam…", deleting.manage.sending)
            assertNull(deleting.manage.editLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a status change sending hides edit and delete of that card`() = runTest {
        statuses.changes.value = listOf(
            com.bbbjam.core.data.setlist.JamStatusChange(
                7,
                heldDate,
                JamStatus.CREATED,
                com.bbbjam.core.data.setlist.JamStatusChange.State.Sending,
            ),
        )
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val model = expectMostRecentItem()
            assertNull(model.card(1).manage.editLabel)
            assertEquals("Editar la jam", model.card(0).manage.editLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed update is a card on the jam, retry resends the same change and close dismisses`() = runTest {
        val change = JamEdit.Change.Update(LocalDate.of(2026, 11, 7), LocalTime.of(20, 0), "Bar")
        edits.entries.value = listOf(
            change.at(3, upcomingDate, JamEdit.State.Failed(WriteOutcome.Rejected("jam_exists"))),
        )
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val manage = expectMostRecentItem().card(0).manage
            val failure = checkNotNull(manage.failure)
            assertEquals("No se pudo guardar la jam", failure.title)
            assertEquals("Ya hay una jam o una pestaña con esa fecha en la planilla.", failure.message)
            assertEquals("La jam sigue como estaba.", failure.consequence)
            assertEquals("Reintentar", failure.retryLabel)
            assertEquals("Cerrar", failure.dismissLabel)

            manage.events(JamManageUiModel.Event.Retry(3))
            assertEquals(listOf(3L), edits.dismissed)
            assertEquals(
                listOf(
                    FakeJamEditRepository.Update(
                        upcomingDate,
                        LocalDate.of(2026, 11, 7),
                        LocalTime.of(20, 0),
                        "Bar",
                    ),
                ),
                edits.updates,
            )
            manage.events(JamManageUiModel.Event.Dismiss(3))
            assertTrue(edits.dismissed.contains(3L))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete failures map setlist_changed and an outdated script to Cerrar only`() = runTest {
        edits.entries.value = listOf(
            JamEdit.Change.Delete(12).at(
                4,
                upcomingDate,
                JamEdit.State.Failed(WriteOutcome.Rejected("setlist_changed")),
            ),
            JamEdit.Change.Update(LocalDate.of(2026, 11, 20), LocalTime.of(20, 0), "Bar")
                .at(5, heldDate, JamEdit.State.Outdated),
        )
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val model = expectMostRecentItem()
            val delete = checkNotNull(model.card(0).manage.failure)
            assertEquals("No se pudo borrar la jam", delete.title)
            assertEquals("La lista cambió mientras tanto. Revisala y probá de nuevo.", delete.message)
            assertNull(delete.retryLabel)
            val outdated = checkNotNull(model.card(1).manage.failure)
            assertEquals("No se pudo guardar la jam", outdated.title)
            assertEquals(
                "El script de la planilla todavía no sabe editar ni borrar jams. " +
                    "Actualizalo con la guía y probá de nuevo.",
                outdated.message,
            )
            assertNull(outdated.retryLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `after the repository emits the moved jam the card shows the new date, and a vanished jam closes its dialog`() =
        runTest {
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(snapshot())
                expectMostRecentItem().card(1).manage.events(JamManageUiModel.Event.Edit)
                assertNotNull(expectMostRecentItem().admin().editForm)

                repository.snapshots.emit(snapshot(heldBack = emptyList()))
                val gone = expectMostRecentItem()
                assertNull(gone.admin().editForm)
                assertEquals(1, gone.admin().future.size)

                repository.snapshots.emit(
                    snapshot(
                        heldBack = listOf(
                            jam(LocalDate.of(2026, 11, 20), JamStatus.DRAFT, Setlist.Available(emptyList())),
                        ),
                    ),
                )
                val moved = expectMostRecentItem()
                assertNull(moved.admin().editForm)
                assertEquals("Viernes 20 de noviembre · 21:00", moved.card(1).dateLabel)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
