package com.bbbjam.feature.pastjams.edit

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.jamedit.JamEdit
import com.bbbjam.core.data.testing.FakeAdminSession
import com.bbbjam.core.data.testing.FakeJamsRepository
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.pastjams.FakeJamCreator
import com.bbbjam.feature.pastjams.FakeJamEditRepository
import com.bbbjam.feature.pastjams.FakeJamStatusRepository
import com.bbbjam.feature.pastjams.FakePublishRepository
import com.bbbjam.feature.pastjams.PastJamsPresenter
import com.bbbjam.feature.pastjams.PastJamsUiModel
import com.bbbjam.feature.pastjams.R
import com.bbbjam.feature.pastjams.admin.JamsAdminUiModel
import com.bbbjam.feature.pastjams.admin.JamsAdminWrites
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
 * `admin-edit-delete-jam`: the edit and delete controls of the Jams tab's future cards, the dialogs and the
 * results. Texts are the `UiText` (resource id and arguments) the spec's copy maps to.
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

    private val createdChip = UiText.res(CoreR.string.jam_status_created)
    private val cancel = UiText.res(CoreR.string.jam_form_cancel)
    private val retry = UiText.res(CoreR.string.list_state_retry)
    private val close = UiText.res(R.string.past_jams_close)
    private val unchanged = UiText.res(R.string.past_jams_jam_unchanged)
    private val undoWarning = UiText.res(R.string.past_jams_undo_warning)

    /** "sábado 31 de octubre": a date inside a sentence. */
    private fun sentence(date: LocalDate) = SpanishDateNames.sentence(date)

    private fun behindDraft(date: LocalDate) = UiText.res(R.string.past_jams_visible_behind_draft, sentence(date))

    private fun JamEdit.Change.at(id: Long, date: LocalDate, state: JamEdit.State) = JamEdit(id, date, this, state)

    @Test
    fun `every future card can be edited, deleting needs an available setlist, held-back cards get status`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val model = expectMostRecentItem()
            val first = model.card(0)
            val second = model.card(1)
            assertEquals(
                IconActionUiModel(
                    UiText.res(R.string.past_jams_edit_description, sentence(upcomingDate)),
                    enabled = true,
                ),
                first.manage.edit,
            )
            assertEquals(
                IconActionUiModel(
                    UiText.res(R.string.past_jams_delete_description, sentence(upcomingDate)),
                    enabled = true,
                ),
                first.manage.delete,
            )
            assertEquals(
                IconActionUiModel(UiText.res(R.string.past_jams_edit_description, sentence(heldDate)), enabled = true),
                second.manage.edit,
            )
            assertEquals(
                IconActionUiModel(
                    UiText.res(R.string.past_jams_delete_description, sentence(heldDate)),
                    enabled = true,
                ),
                second.manage.delete,
            )
            assertEquals(
                listOf(createdChip),
                second.controls.status.chips.chips.filter { it.enabled && !it.selected }.map { it.label },
            )
            assertNull(second.controls.publish)
            assertNull(first.controls.publish)

            repository.snapshots.emit(
                snapshot(
                    heldBack = listOf(
                        jam(heldDate, JamStatus.DRAFT, Setlist.Unavailable(SetlistProblem.INVALID_TAB)),
                    ),
                ),
            )
            val unreadable = expectMostRecentItem().card(1)
            assertTrue(unreadable.manage.edit.enabled)
            assertNull(unreadable.manage.delete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a held-back card names the upcoming jam, a visible one behind a borrador says musicians see it`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(heldBack = listOf(held(JamStatus.CREATED))))
            val model = expectMostRecentItem()
            assertEquals(UiText.res(R.string.past_jams_held_back, sentence(upcomingDate)), model.card(1).heldBack)
            assertEquals(behindDraft(upcomingDate), model.card(1).musicianNote)
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
                assertEquals(behindDraft(upcomingDate), model.card(1).musicianNote)
                assertNull(model.card(2).musicianNote)

                repository.snapshots.emit(snapshot(heldBack = listOf(held(JamStatus.DRAFT), third)))
                val skipped = expectMostRecentItem()
                assertNull(skipped.card(1).musicianNote)
                assertEquals(behindDraft(upcomingDate), skipped.card(2).musicianNote)
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
            assertEquals(UiText.res(R.string.past_jams_edit), form.title)
            assertEquals(UiText.res(CoreR.string.jam_form_date_label), form.dateLabel)
            assertEquals(UiText.res(CoreR.string.jam_form_date_hint), form.dateHint)
            assertEquals(UiText.res(CoreR.string.jam_form_time_label), form.timeLabel)
            assertEquals(UiText.res(CoreR.string.jam_form_venue_label), form.venueLabel)
            assertEquals("31/10/2026", form.date)
            assertEquals("21:00", form.time)
            assertEquals("La Macanuda", form.venue)
            assertEquals(false, form.canSave)
            assertEquals(UiText.res(R.string.past_jams_save), form.saveLabel)
            assertEquals(cancel, form.cancelLabel)
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
            assertEquals(UiText.res(R.string.past_jams_visible_note), form.visibilityNote)

            form.events(EditJamFormUiModel.Event.Edit(JamFormField.DATE, "14/11/2026"))
            form = checkNotNull(expectMostRecentItem().admin().editForm)
            assertEquals(UiText.res(R.string.past_jams_later_error, sentence(heldDate)), form.dateError)
            assertEquals(false, form.canSave)

            form.events(EditJamFormUiModel.Event.Edit(JamFormField.TIME, "25:00"))
            form = checkNotNull(expectMostRecentItem().admin().editForm)
            assertEquals(UiText.res(CoreR.string.jam_form_time_error), form.timeError)

            form.events(EditJamFormUiModel.Event.Edit(JamFormField.DATE, "7/11/2026"))
            form = checkNotNull(expectMostRecentItem().admin().editForm)
            form.events(EditJamFormUiModel.Event.Edit(JamFormField.TIME, "20:30"))
            form = checkNotNull(expectMostRecentItem().admin().editForm)
            assertNull(form.dateError)
            assertNull(form.timeError)
            assertEquals(UiText.res(R.string.past_jams_date_moves_note), form.dateNote)
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
            form.events(EditJamFormUiModel.Event.Edit(JamFormField.DATE, "30/10/2026"))
            val blocked = checkNotNull(expectMostRecentItem().admin().editForm)
            assertEquals(
                UiText.res(R.string.past_jams_earlier_than_upcoming_error, sentence(upcomingDate)),
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
                assertEquals(UiText.res(R.string.past_jams_delete_title, sentence(upcomingDate)), dialog.title)
                assertEquals(
                    listOf(
                        UiText.plural(R.plurals.past_jams_songs_lost, 12),
                        UiText.res(R.string.past_jams_published_lost),
                        UiText.res(R.string.past_jams_next_jam_becomes, sentence(heldDate)),
                        undoWarning,
                    ),
                    dialog.lines,
                )
                assertEquals(UiText.res(R.string.past_jams_delete), dialog.confirmLabel)
                assertEquals(cancel, dialog.cancelLabel)

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
                    UiText.plural(R.plurals.past_jams_songs_lost, 1),
                    UiText.res(R.string.past_jams_created_lost),
                    UiText.res(R.string.past_jams_no_jam_left),
                    undoWarning,
                ),
                checkNotNull(expectMostRecentItem().admin().deleteConfirm).lines,
            )

            repository.snapshots.emit(snapshot(upcoming = upcoming(JamStatus.DRAFT, 0)))
            val model = expectMostRecentItem()
            checkNotNull(model.admin().deleteConfirm).events(DeleteJamUiModel.Event.Cancel)
            expectMostRecentItem().card(1).manage.events(JamManageUiModel.Event.Delete)
            val held = checkNotNull(expectMostRecentItem().admin().deleteConfirm)
            assertEquals(UiText.res(R.string.past_jams_delete_title, sentence(heldDate)), held.title)
            assertEquals(
                listOf(
                    UiText.res(R.string.past_jams_empty_list),
                    UiText.res(R.string.past_jams_next_jam_stays, sentence(upcomingDate)),
                    undoWarning,
                ),
                held.lines,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `while an edit is sending its card shows one line, disabled chips and icons, the other card is unaffected`() =
        runTest {
            edits.entries.value = listOf(
                JamEdit.Change.Update(LocalDate.of(2026, 11, 7), LocalTime.of(20, 0), "Bar")
                    .at(5, upcomingDate, JamEdit.State.Sending),
            )
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(snapshot(upcoming = upcoming(JamStatus.CREATED)))
                val model = expectMostRecentItem()
                val sending = model.card(0)
                assertEquals(UiText.res(R.string.past_jams_saving), sending.manage.sending)
                assertFalse(sending.manage.edit.enabled)
                assertFalse(checkNotNull(sending.manage.delete).enabled)
                assertTrue(sending.controls.status.chips.chips.none { it.enabled })
                assertTrue(sending.controls.status.chips.chips.none { it.pending })
                assertEquals(createdChip, sending.controls.status.chips.chips.single { it.selected }.label)
                assertNull(checkNotNull(sending.controls).publish)
                val other = model.card(1)
                assertNull(other.manage.sending)
                assertTrue(other.manage.edit.enabled)

                edits.entries.value = listOf(
                    JamEdit.Change.Delete(0).at(6, heldDate, JamEdit.State.Sending),
                )
                val deleting = expectMostRecentItem().card(1)
                assertEquals(UiText.res(R.string.past_jams_deleting), deleting.manage.sending)
                assertFalse(deleting.manage.edit.enabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a status change sending disables edit and delete of that card`() = runTest {
        statuses.changes.value = listOf(
            com.bbbjam.core.data.setlist.jamstatus.JamStatusChange(
                7,
                heldDate,
                JamStatus.CREATED,
                com.bbbjam.core.data.setlist.jamstatus.JamStatusChange.State.Sending,
            ),
        )
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val model = expectMostRecentItem()
            assertFalse(model.card(1).manage.edit.enabled)
            assertFalse(checkNotNull(model.card(1).manage.delete).enabled)
            assertTrue(model.card(0).manage.edit.enabled)
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
            assertEquals(UiText.res(R.string.past_jams_update_failed), failure.title)
            assertEquals(UiText.res(R.string.past_jams_create_exists), failure.message)
            assertEquals(unchanged, failure.consequence)
            assertEquals(retry, failure.retryLabel)
            assertEquals(close, failure.dismissLabel)

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
            assertEquals(UiText.res(R.string.past_jams_delete_failed), delete.title)
            assertEquals(UiText.res(R.string.past_jams_setlist_changed), delete.message)
            assertNull(delete.retryLabel)
            val outdated = checkNotNull(model.card(1).manage.failure)
            assertEquals(UiText.res(R.string.past_jams_update_failed), outdated.title)
            assertEquals(UiText.res(R.string.past_jams_edit_script_outdated), outdated.message)
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
                assertEquals(
                    UiText.res(
                        R.string.past_jams_date_with_time,
                        SpanishDateNames.label(LocalDate.of(2026, 11, 20)),
                        "21:00",
                    ),
                    moved.card(1).dateLabel,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
}
