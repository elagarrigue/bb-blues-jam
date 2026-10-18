package com.bbbjam.feature.pastjams.create

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.jamcreate.CreateJamOutcome
import com.bbbjam.core.data.testing.FakeAdminSession
import com.bbbjam.core.data.testing.FakeJamsRepository
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.core.ui.jamform.jamFormEarlierError
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.pastjams.FakeJamCreator
import com.bbbjam.feature.pastjams.FakeJamEditRepository
import com.bbbjam.feature.pastjams.FakeJamStatusRepository
import com.bbbjam.feature.pastjams.FakePublishRepository
import com.bbbjam.feature.pastjams.PastJamsPresenter
import com.bbbjam.feature.pastjams.PastJamsUiModel
import com.bbbjam.feature.pastjams.R
import com.bbbjam.feature.pastjams.admin.JamsAdminWrites
import com.bbbjam.feature.pastjams.notCreated
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

/** `admin-jams-tab` J7/J9: opening, editing and creating a jam through the presenter. Texts are `UiText`s. */
class NewJamCreateTest {
    private val repository = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val creator = FakeJamCreator()

    /** 2 October 2026, 12:00 in Buenos Aires. */
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)
    private val upcoming = Jam(
        LocalDate.of(2026, 10, 31),
        LocalTime.of(20, 30),
        "La Macanuda",
        JamStatus.CREATED,
        Setlist.Available(emptyList()),
    )
    private val snapshot = JamsSnapshot(upcoming, emptyList(), fetched)
    private val created = NewJamProgressUiModel.Created(UiText.res(R.string.past_jams_created))

    private fun presenter() = PastJamsPresenter(
        repository,
        calendar,
        session,
        JamsAdminWrites(FakeJamStatusRepository(), FakePublishRepository(), creator, FakeJamEditRepository()),
    )

    private fun PastJamsUiModel.newJam(): NewJamUiModel = when (this) {
        is PastJamsUiModel.Jams -> checkNotNull(admin).newJam
        is PastJamsUiModel.Empty -> checkNotNull(admin).newJam
        else -> error("no admin section: $this")
    }

    private fun NewJamUiModel.edit(field: JamFormField, text: String) = events(NewJamUiModel.Event.Edit(field, text))

    @Test
    fun `Nueva jam opens the form seeded from the latest jam with an empty date, and Cancelar calls nothing`() =
        runTest {
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(snapshot)
                val closed = expectMostRecentItem().newJam()
                assertEquals(UiText.res(R.string.past_jams_new_jam), closed.label)
                assertNull(closed.form)
                assertNull(closed.progress)

                closed.events(NewJamUiModel.Event.Open)
                val open = expectMostRecentItem().newJam()
                val form = checkNotNull(open.form)
                assertEquals(UiText.res(R.string.past_jams_new_jam), form.title)
                assertEquals(UiText.res(CoreR.string.jam_form_date_label), form.date.label)
                assertEquals(UiText.res(CoreR.string.jam_form_date_hint), form.date.hint)
                assertEquals(UiText.res(CoreR.string.jam_form_time_label), form.time.label)
                assertEquals(UiText.res(CoreR.string.jam_form_time_hint), form.time.hint)
                assertEquals(UiText.res(CoreR.string.jam_form_venue_label), form.venue.label)
                assertEquals("", form.date.value)
                assertEquals("20:30", form.time.value)
                assertEquals("La Macanuda", form.venue.value)
                assertNull(form.date.error)
                assertEquals(listOf(UiText.res(R.string.past_jams_new_jam_draft_note)), form.notes)
                assertEquals(UiText.res(R.string.past_jams_new_jam_create), form.submitLabel)
                assertEquals(UiText.res(CoreR.string.jam_form_cancel), form.cancelLabel)
                assertFalse(form.canSubmit)

                open.events(NewJamUiModel.Event.Cancel)
                assertNull(expectMostRecentItem().newJam().form)
                assertEquals(emptyList<FakeJamCreator.Call>(), creator.calls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the date shows its error as typed, an earlier date names the upcoming jam, a later one the held-back note`() =
        runTest {
            moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
                repository.snapshots.emit(snapshot)
                expectMostRecentItem().newJam().events(NewJamUiModel.Event.Open)
                var model = expectMostRecentItem().newJam()

                model.edit(JamFormField.DATE, "mañana")
                model = expectMostRecentItem().newJam()
                assertEquals(UiText.res(CoreR.string.jam_form_date_format_error), model.form?.date?.error)

                model.edit(JamFormField.DATE, "1/10/2026")
                model = expectMostRecentItem().newJam()
                assertEquals(UiText.res(CoreR.string.jam_form_date_past_error), model.form?.date?.error)

                model.edit(JamFormField.DATE, "31/10/2026")
                model = expectMostRecentItem().newJam()
                assertEquals(UiText.res(CoreR.string.jam_form_date_exists_error), model.form?.date?.error)

                model.edit(JamFormField.DATE, "20/10/2026")
                model = expectMostRecentItem().newJam()
                assertEquals(
                    UiText.res(
                        CoreR.string.jam_form_earlier_error,
                        SpanishDateNames.sentence(LocalDate.of(2026, 10, 31)),
                    ),
                    model.form?.date?.error,
                )
                assertEquals(jamFormEarlierError(LocalDate.of(2026, 10, 31)), model.form?.date?.error)
                assertFalse(checkNotNull(model.form).canSubmit)

                model.edit(JamFormField.DATE, "28/11/2026")
                model = expectMostRecentItem().newJam()
                val form = checkNotNull(model.form)
                assertNull(form.date.error)
                assertEquals(
                    UiText.res(R.string.past_jams_waits_note, SpanishDateNames.sentence(LocalDate.of(2026, 10, 31))),
                    form.notes.first(),
                )
                assertTrue(form.canSubmit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `time and venue errors appear once edited, and a blank one is not an error before that`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot.copy(upcoming = null))
            expectMostRecentItem().newJam().events(NewJamUiModel.Event.Open)
            var model = expectMostRecentItem().newJam()
            // No jam at all: 21:00 and an empty venue, and no error shouted before anything is typed.
            assertEquals("21:00", model.form?.time?.value)
            assertNull(model.form?.venue?.error)

            model.edit(JamFormField.VENUE, "   ")
            model = expectMostRecentItem().newJam()
            assertEquals(UiText.res(CoreR.string.jam_form_venue_empty_error), model.form?.venue?.error)
            model.edit(JamFormField.VENUE, "=SUMA(A1)")
            model = expectMostRecentItem().newJam()
            assertEquals(UiText.res(CoreR.string.jam_form_venue_formula_error), model.form?.venue?.error)
            model.edit(JamFormField.VENUE, "x".repeat(81))
            model = expectMostRecentItem().newJam()
            assertEquals(UiText.res(CoreR.string.jam_form_venue_long_error), model.form?.venue?.error)
            model.edit(JamFormField.TIME, "25:61")
            model = expectMostRecentItem().newJam()
            assertEquals(UiText.res(CoreR.string.jam_form_time_error), model.form?.time?.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Crear la jam calls createJam once with the parsed values and a double tap creates once`() = runTest {
        val hold = CompletableDeferred<Unit>().also { creator.hold = it }
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot)
            expectMostRecentItem().newJam().events(NewJamUiModel.Event.Open)
            var model = expectMostRecentItem().newJam()
            model.edit(JamFormField.DATE, "28-11-2026")
            model = expectMostRecentItem().newJam()
            model.edit(JamFormField.TIME, "9.30")
            model = expectMostRecentItem().newJam()
            model.edit(JamFormField.VENUE, "  El   Sótano ")
            model = expectMostRecentItem().newJam()

            model.events(NewJamUiModel.Event.Create)
            model.events(NewJamUiModel.Event.Create)
            assertEquals(
                listOf(FakeJamCreator.Call(LocalDate.of(2026, 11, 28), LocalTime.of(9, 30), "El Sótano")),
                creator.calls,
            )
            val creating = expectMostRecentItem().newJam()
            assertNull(creating.form)
            assertEquals(NewJamProgressUiModel.Creating(UiText.res(R.string.past_jams_creating)), creating.progress)

            hold.complete(Unit)
            val done = expectMostRecentItem().newJam()
            assertEquals(created, done.progress)
            done.events(NewJamUiModel.Event.Dismiss)
            assertNull(expectMostRecentItem().newJam().progress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Crear la jam does nothing while the plan is not ready`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot)
            expectMostRecentItem().newJam().events(NewJamUiModel.Event.Open)
            var model = expectMostRecentItem().newJam()
            model.edit(JamFormField.DATE, "20/10/2026")
            model = expectMostRecentItem().newJam()
            model.events(NewJamUiModel.Event.Create)
            assertEquals(emptyList<FakeJamCreator.Call>(), creator.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a refusal is a card with the reason, Reintentar resends the same values and Cerrar dismisses`() = runTest {
        creator.outcome = notCreated("jam_exists")
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot)
            expectMostRecentItem().newJam().events(NewJamUiModel.Event.Open)
            var model = expectMostRecentItem().newJam()
            model.edit(JamFormField.DATE, "28/11/2026")
            model = expectMostRecentItem().newJam()
            model.events(NewJamUiModel.Event.Create)

            val failed = expectMostRecentItem().newJam()
            val card = failed.progress as NewJamProgressUiModel.Failed
            assertEquals(UiText.res(R.string.past_jams_create_failed), card.title)
            assertEquals(UiText.res(R.string.past_jams_create_exists), card.message)
            assertEquals(UiText.res(R.string.past_jams_create_consequence), card.consequence)
            assertEquals(UiText.res(CoreR.string.list_state_retry), card.retryLabel)
            assertEquals(UiText.res(R.string.past_jams_close), card.dismissLabel)

            creator.outcome = null
            failed.events(NewJamUiModel.Event.Retry)
            assertEquals(2, creator.calls.size)
            assertEquals(creator.calls[0], creator.calls[1])
            assertEquals(created, expectMostRecentItem().newJam().progress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each create code has its approved reason`() {
        val exists = UiText.res(R.string.past_jams_create_exists)
        val invalid = UiText.res(R.string.past_jams_create_invalid)
        val expected = mapOf(
            "jam_exists" to exists,
            "tab_exists" to exists,
            "past_date" to UiText.res(CoreR.string.jam_form_date_past_error),
            "invalid_venue" to invalid,
            "invalid_time" to invalid,
            "missing_header" to UiText.res(CoreR.string.write_failure_sheet_refused),
        )
        expected.forEach { (code, message) -> assertEquals(code, message, createFailureMessage(notCreated(code))) }
        assertEquals(
            UiText.res(CoreR.string.write_failure_offline),
            createFailureMessage(CreateJamOutcome.NotCreated(WriteOutcome.Offline)),
        )
    }

    @Test
    fun `an outdated script has the guide line and Cerrar only`() = runTest {
        creator.outcome = CreateJamOutcome.ScriptOutdated
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot)
            expectMostRecentItem().newJam().events(NewJamUiModel.Event.Open)
            var model = expectMostRecentItem().newJam()
            model.edit(JamFormField.DATE, "28/11/2026")
            model = expectMostRecentItem().newJam()
            model.events(NewJamUiModel.Event.Create)

            val failed = expectMostRecentItem().newJam()
            val card = failed.progress as NewJamProgressUiModel.Failed
            assertEquals(UiText.res(R.string.past_jams_create_script_outdated), card.message)
            assertNull(card.retryLabel)
            failed.events(NewJamUiModel.Event.Retry)
            assertEquals(1, creator.calls.size)
            failed.events(NewJamUiModel.Event.Dismiss)
            assertNull(expectMostRecentItem().newJam().progress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with no future jam Nueva jam is still offered`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(PastJamsPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot.copy(upcoming = null))
            val model = expectMostRecentItem()
            assertEquals(UiText.res(R.string.past_jams_no_future), (model as PastJamsUiModel.Empty).admin?.noFuture)
            assertEquals(UiText.res(R.string.past_jams_new_jam), model.newJam().label)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
