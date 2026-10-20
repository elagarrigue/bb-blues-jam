package com.bbbjam.feature.catalog

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.catalog.edit.CreateSongOutcome
import com.bbbjam.core.data.catalog.edit.DeleteSongOutcome
import com.bbbjam.core.data.catalog.edit.SongFields
import com.bbbjam.core.data.catalog.edit.UpdateSongOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.testing.FakeAdminSession
import com.bbbjam.core.data.testing.FakeCatalogRepository
import com.bbbjam.core.data.testing.FakeJamsRepository
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `admin-catalog-tab`: the Temas tab's states and its add, edit and delete flows, with the writes faked. */
class CatalogPresenterTest {
    private val catalog = FakeCatalogRepository()
    private val edits = FakeCatalogEdits()
    private val jams = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)

    private val july = LocalDate.of(2026, 7, 25)
    private val october = LocalDate.of(2026, 10, 31)
    private val november = LocalDate.of(2026, 11, 28)

    private fun presenter() = CatalogPresenter(catalog, edits, jams, calendar, session)

    private fun content(model: CatalogUiModel) = model as CatalogUiModel.Content

    private fun CatalogUiModel.Content.rows() = (list as CatalogListUiModel.Songs).rows

    private fun CatalogUiModel.Content.row(song: com.bbbjam.core.model.Song) = rows().single { it.id == song.id.value }

    private fun CatalogUiModel.Content.type(field: SongFormField, text: String) =
        checkNotNull(form).events(SongFormUiModel.Event.Edit(field, text))

    private fun CatalogUiModel.Content.fill(title: String, artist: String, key: String) {
        type(SongFormField.TITLE, title)
        type(SongFormField.ARTIST, artist)
        type(SongFormField.KEY, key)
    }

    private fun present(block: suspend app.cash.turbine.TurbineTestContext<CatalogUiModel>.() -> Unit) = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test { block() }
    }

    @Test
    fun `loading until the admin flag is read, then hidden for a musician`() = runTest {
        session.isAdmin.value = false
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(Unit) }.test {
            catalog.snapshots.emit(catalogSnapshot())
            assertEquals(CatalogUiModel.Hidden, expectMostRecentItem())
            session.isAdmin.value = true
            assertTrue(expectMostRecentItem() is CatalogUiModel.Content)
            session.isAdmin.value = false
            assertEquals(CatalogUiModel.Hidden, expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, edits.creates.size + edits.updates.size + edits.deletes.size)
    }

    @Test
    fun `the admin sees the catalog sorted by title and artist ignoring case and accents, with a count`() = present {
        catalog.snapshots.emit(catalogSnapshot())

        val model = content(expectMostRecentItem())

        assertEquals(
            listOf("Ágüa de río", "Crossroads", "Red House", "The Thrill Is Gone"),
            model.rows().map {
                it.title
            },
        )
        assertEquals(UiText.plural(R.plurals.catalog_count, 4), model.count)
        assertEquals(UiText.res(R.string.catalog_title), model.title)
        assertTrue(model.add.enabled)
        assertNull(model.notice)
        assertNull(model.form)
        assertNull(model.delete)
        val row = model.row(CROSSROADS)
        assertEquals("A", row.key)
        assertEquals(UiText.res(R.string.catalog_edit_description, "Crossroads"), row.editDescription)
        assertEquals(UiText.res(R.string.catalog_delete_description, "Crossroads"), row.deleteDescription)
    }

    @Test
    fun `the search filters by title or artist ignoring accents and says so when nothing matches`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        val all = content(expectMostRecentItem())

        all.search.events(SearchFieldUiModel.Event.Changed("rio"))
        assertEquals(listOf("Ágüa de río"), content(expectMostRecentItem()).rows().map { it.title })

        all.search.events(SearchFieldUiModel.Event.Changed("hendrix"))
        assertEquals(listOf("Red House"), content(expectMostRecentItem()).rows().map { it.title })

        all.search.events(SearchFieldUiModel.Event.Changed("zzz"))
        assertEquals(
            CatalogListUiModel.NoResults(UiText.res(R.string.catalog_no_results, "zzz")),
            content(expectMostRecentItem()).list,
        )
    }

    @Test
    fun `adding sends the normalized fields once, closes the form and shows the result line`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        val gate = CompletableDeferred<Unit>().also { edits.hold = it }
        content(expectMostRecentItem()).add.events(ActionUiModel.Event.Click)
        val open = content(expectMostRecentItem())
        assertEquals(UiText.res(R.string.catalog_form_add_title), checkNotNull(open.form).title)
        assertFalse(open.form.canSubmit)

        open.fill("  Pride  and Joy ", "Stevie Ray Vaughan", "e")
        val filled = content(expectMostRecentItem())
        assertTrue(checkNotNull(filled.form).canSubmit)
        filled.form.events(SongFormUiModel.Event.Submit)
        filled.form.events(SongFormUiModel.Event.Submit)

        val sending = content(expectMostRecentItem())
        assertNull(sending.form)
        assertEquals(CatalogNoticeUiModel.Progress(UiText.res(R.string.catalog_saving)), sending.notice)
        assertFalse(sending.add.enabled)
        assertTrue(sending.rows().none { it.actionsEnabled })
        assertEquals(listOf(SongFields("Pride and Joy", "Stevie Ray Vaughan", Key("E"))), edits.creates)

        gate.complete(Unit)
        val done = content(expectMostRecentItem())
        assertEquals(CatalogNoticeUiModel.Done(UiText.res(R.string.catalog_added)), done.notice)
        assertTrue(done.add.enabled)
        assertEquals(1, edits.creates.size)
    }

    @Test
    fun `the form shows its errors once a field is typed and a duplicate under the artist`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        content(expectMostRecentItem()).add.events(ActionUiModel.Event.Click)
        val open = content(expectMostRecentItem())
        assertNull(open.form?.titleField?.error)
        assertNull(open.form?.keyField?.error)

        open.fill("=x", "Alguien", "H")
        val bad = checkNotNull(content(expectMostRecentItem()).form)
        assertEquals(UiText.res(R.string.catalog_error_formula), bad.titleField.error)
        assertEquals(UiText.res(R.string.catalog_error_key), bad.keyField.error)
        assertFalse(bad.canSubmit)

        open.fill("crossroads", "ERIC CLAPTON", "A")
        val duplicate = checkNotNull(content(expectMostRecentItem()).form)
        assertEquals(UiText.res(R.string.catalog_error_duplicate), duplicate.artistField.error)
        assertFalse(duplicate.canSubmit)

        open.type(SongFormField.TITLE, "")
        assertEquals(
            UiText.res(R.string.catalog_error_title_empty),
            checkNotNull(content(expectMostRecentItem()).form).titleField.error,
        )
    }

    @Test
    fun `cancelling the form sends nothing`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        content(expectMostRecentItem()).add.events(ActionUiModel.Event.Click)
        val open = content(expectMostRecentItem())
        open.fill("Algo", "Alguien", "A")
        checkNotNull(content(expectMostRecentItem()).form).events(SongFormUiModel.Event.Cancel)

        assertNull(content(expectMostRecentItem()).form)
        assertTrue(edits.creates.isEmpty())
    }

    @Test
    fun `editing opens with the song's values, Guardar is off until something differs, and only the id is sent`() =
        present {
            catalog.snapshots.emit(catalogSnapshot())
            content(expectMostRecentItem()).row(CROSSROADS).events(SongRowUiModel.Event.Edit)

            val open = checkNotNull(content(expectMostRecentItem()).form)
            assertEquals(UiText.res(R.string.catalog_form_edit_title), open.title)
            assertEquals("Crossroads", open.titleField.value)
            assertEquals("Eric Clapton", open.artistField.value)
            assertEquals("A", open.keyField.value)
            assertEquals(UiText.res(R.string.catalog_form_save), open.submitLabel)
            assertFalse(open.canSubmit)

            open.events(SongFormUiModel.Event.Edit(SongFormField.ARTIST, "Cream"))
            val changed = checkNotNull(content(expectMostRecentItem()).form)
            assertTrue(changed.canSubmit)
            changed.events(SongFormUiModel.Event.Submit)

            assertEquals(listOf(SongId("crossroads") to SongFields("Crossroads", "Cream", Key("A"))), edits.updates)
            assertEquals(
                CatalogNoticeUiModel.Done(UiText.res(R.string.catalog_saved)),
                content(expectMostRecentItem()).notice,
            )
        }

    @Test
    fun `a song does not clash with itself when edited`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        content(expectMostRecentItem()).row(CROSSROADS).events(SongRowUiModel.Event.Edit)
        val open = checkNotNull(content(expectMostRecentItem()).form)

        open.events(SongFormUiModel.Event.Edit(SongFormField.KEY, "e"))

        val form = checkNotNull(content(expectMostRecentItem()).form)
        assertNull(form.artistField.error)
        assertTrue(form.canSubmit)
    }

    @Test
    fun `deleting a song only in past jams asks first, then sends once`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        jams.snapshots.emit(jamsSnapshot(past = listOf(jamWith(july, setlistOf(CROSSROADS)))))
        content(expectMostRecentItem()).row(CROSSROADS).events(SongRowUiModel.Event.Delete)

        val dialog = content(expectMostRecentItem()).delete as DeleteSongUiModel.Confirming
        assertEquals(UiText.res(R.string.catalog_delete_title, "Crossroads"), dialog.title)
        assertEquals(UiText.raw("Eric Clapton"), dialog.lines.first())
        assertTrue(edits.deletes.isEmpty())

        val gate = CompletableDeferred<Unit>().also { edits.hold = it }
        dialog.events(DeleteSongUiModel.Confirming.Event.Confirm)
        dialog.events(DeleteSongUiModel.Confirming.Event.Confirm)
        assertEquals(listOf(CROSSROADS.id), edits.deletes)
        gate.complete(Unit)

        val after = content(expectMostRecentItem())
        assertNull(after.delete)
        assertEquals(listOf(CROSSROADS.id), edits.deletes)
        assertEquals(CatalogNoticeUiModel.Done(UiText.res(R.string.catalog_deleted)), after.notice)
    }

    @Test
    fun `deleting a song in the upcoming or a held-back jam is refused with the dates and nothing is sent`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        jams.snapshots.emit(
            jamsSnapshot(
                upcoming = jamWith(october, setlistOf(CROSSROADS)),
                heldBack = listOf(jamWith(november, setlistOf(CROSSROADS, RED_HOUSE))),
            ),
        )

        content(expectMostRecentItem()).row(CROSSROADS).events(SongRowUiModel.Event.Delete)
        val both = content(expectMostRecentItem()).delete as DeleteSongUiModel.Refused
        assertEquals(UiText.res(R.string.catalog_refused_title, "Crossroads"), both.title)
        assertEquals(
            UiText.res(
                R.string.catalog_refused_two,
                SpanishDateNames.sentence(october),
                SpanishDateNames.sentence(november),
            ),
            both.lines.first(),
        )
        assertEquals(UiText.res(R.string.catalog_refused_advice), both.lines.last())

        both.events(DeleteSongUiModel.Refused.Event.Dismiss)
        val dismissed = content(expectMostRecentItem())
        assertNull(dismissed.delete)

        dismissed.row(RED_HOUSE).events(SongRowUiModel.Event.Delete)
        val one = content(expectMostRecentItem()).delete as DeleteSongUiModel.Refused
        assertEquals(UiText.res(R.string.catalog_refused_one, SpanishDateNames.sentence(november)), one.lines.first())
        assertTrue(edits.deletes.isEmpty())
    }

    @Test
    fun `a server refusal shows the failure card with its reason, no retry, and refreshes the jams`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        jams.snapshots.emit(jamsSnapshot())
        edits.delete = DeleteSongOutcome.NotDeleted(WriteOutcome.Rejected("song_in_future_jam"))

        content(expectMostRecentItem()).row(CROSSROADS).events(SongRowUiModel.Event.Delete)
        (content(expectMostRecentItem()).delete as DeleteSongUiModel.Confirming)
            .events(DeleteSongUiModel.Confirming.Event.Confirm)

        val failure = content(expectMostRecentItem()).notice as CatalogNoticeUiModel.Failure
        assertEquals(UiText.res(R.string.catalog_delete_failed), failure.title)
        assertEquals(UiText.res(R.string.catalog_failure_in_future_jam), failure.message)
        assertEquals(UiText.res(R.string.catalog_unchanged), failure.consequence)
        assertNull(failure.retryLabel)
        assertEquals(UiText.res(R.string.catalog_close), failure.dismissLabel)
        assertEquals(1, jams.refreshCalls)

        failure.events(CatalogNoticeUiModel.Failure.Event.Dismiss)
        assertNull(content(expectMostRecentItem()).notice)
    }

    @Test
    fun `an offline write can be retried with the same request`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        edits.update = UpdateSongOutcome.NotUpdated(WriteOutcome.Offline)
        content(expectMostRecentItem()).row(CROSSROADS).events(SongRowUiModel.Event.Edit)
        checkNotNull(content(expectMostRecentItem()).form)
            .events(SongFormUiModel.Event.Edit(SongFormField.KEY, "B"))
        checkNotNull(content(expectMostRecentItem()).form).events(SongFormUiModel.Event.Submit)

        val failure = content(expectMostRecentItem()).notice as CatalogNoticeUiModel.Failure
        assertEquals(UiText.res(R.string.catalog_update_failed), failure.title)
        assertEquals(UiText.res(CoreR.string.write_failure_offline), failure.message)
        assertEquals(UiText.res(CoreR.string.list_state_retry), failure.retryLabel)

        edits.update = UpdateSongOutcome.Updated(changed = true)
        failure.events(CatalogNoticeUiModel.Failure.Event.Retry)

        assertEquals(2, edits.updates.size)
        assertEquals(edits.updates[0], edits.updates[1])
        assertEquals(
            CatalogNoticeUiModel.Done(UiText.res(R.string.catalog_saved)),
            content(expectMostRecentItem()).notice,
        )
    }

    @Test
    fun `an outdated script gets its own card with Cerrar only`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        edits.create = CreateSongOutcome.ScriptOutdated
        content(expectMostRecentItem()).add.events(ActionUiModel.Event.Click)
        content(expectMostRecentItem()).fill("Algo", "Alguien", "A")
        checkNotNull(content(expectMostRecentItem()).form).events(SongFormUiModel.Event.Submit)

        val failure = content(expectMostRecentItem()).notice as CatalogNoticeUiModel.Failure
        assertEquals(UiText.res(R.string.catalog_create_failed), failure.title)
        assertEquals(UiText.res(R.string.catalog_failure_script_outdated), failure.message)
        assertNull(failure.retryLabel)
    }

    @Test
    fun `the reasons map to the shared and the catalog lines`() {
        assertEquals(
            UiText.res(R.string.catalog_failure_song_exists),
            failureReason(WriteOutcome.Rejected("song_exists")),
        )
        assertEquals(
            UiText.res(CoreR.string.write_failure_not_in_catalog),
            failureReason(WriteOutcome.Rejected("unknown_song")),
        )
        assertEquals(
            UiText.res(R.string.catalog_failure_duplicate_id),
            failureReason(WriteOutcome.Rejected("duplicate_id")),
        )
        assertEquals(
            UiText.res(CoreR.string.write_failure_sheet_refused),
            failureReason(WriteOutcome.Rejected("invalid_title")),
        )
        assertEquals(
            UiText.res(CoreR.string.write_failure_access_refused),
            failureReason(WriteOutcome.AccessRefused),
        )
        assertEquals(UiText.res(CoreR.string.write_failure_unavailable), failureReason(WriteOutcome.Unavailable))
        assertTrue(isRetryable(WriteOutcome.Offline) && isRetryable(WriteOutcome.Unavailable))
        assertFalse(isRetryable(WriteOutcome.Rejected("song_exists")))
    }

    @Test
    fun `while a write runs every submit and row action is disabled and a second write is ignored`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        val gate = CompletableDeferred<Unit>().also { edits.hold = it }
        content(expectMostRecentItem()).row(CROSSROADS).events(SongRowUiModel.Event.Delete)
        (content(expectMostRecentItem()).delete as DeleteSongUiModel.Confirming)
            .events(DeleteSongUiModel.Confirming.Event.Confirm)
        val busy = content(expectMostRecentItem())
        assertEquals(CatalogNoticeUiModel.Progress(UiText.res(R.string.catalog_deleting)), busy.notice)

        busy.row(RED_HOUSE).events(SongRowUiModel.Event.Delete)
        busy.add.events(ActionUiModel.Event.Click)
        expectNoEvents()
        assertEquals(listOf(CROSSROADS.id), edits.deletes)
        gate.complete(Unit)
        assertTrue(content(expectMostRecentItem()).add.enabled)
    }

    @Test
    fun `a pull refreshes the catalog once and a retry re-subscribes`() = present {
        catalog.snapshots.emit(catalogSnapshot())
        val model = content(expectMostRecentItem())

        model.pullRefresh.events(com.bbbjam.core.ui.state.PullRefreshUiModel.Event.Refresh)

        assertEquals(1, catalog.refreshCalls)
        assertEquals(2, catalog.subscriptions)
        cancelAndIgnoreRemainingEvents()
    }
}
