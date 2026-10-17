package com.bbbjam.feature.pastjams.create

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.feature.pastjams.FakeAdminSession
import com.bbbjam.feature.pastjams.FakeJamCreator
import com.bbbjam.feature.pastjams.FakeJamEditRepository
import com.bbbjam.feature.pastjams.FakeJamStatusRepository
import com.bbbjam.feature.pastjams.FakeJamsRepository
import com.bbbjam.feature.pastjams.FakePublishRepository
import com.bbbjam.feature.pastjams.PastJamsPresenter
import com.bbbjam.feature.pastjams.PastJamsUiModel
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

/** `admin-jams-tab` J7/J9: opening, editing and creating a jam through the presenter. Copy written out. */
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
    private val created = NewJamProgressUiModel.Created("Jam creada como borrador.")

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
                assertEquals("Nueva jam", closed.label)
                assertNull(closed.form)
                assertNull(closed.progress)

                closed.events(NewJamUiModel.Event.Open)
                val open = expectMostRecentItem().newJam()
                val form = checkNotNull(open.form)
                assertEquals("Nueva jam", form.title)
                assertEquals("Fecha", form.date.label)
                assertEquals("DD/MM/AAAA", form.date.hint)
                assertEquals("Hora", form.time.label)
                assertEquals("HH:MM", form.time.hint)
                assertEquals("Lugar", form.venue.label)
                assertEquals("", form.date.value)
                assertEquals("20:30", form.time.value)
                assertEquals("La Macanuda", form.venue.value)
                assertNull(form.date.error)
                assertEquals(
                    listOf(
                        "Se crea como borrador: los músicos no la ven hasta que la pases a creada " +
                            "o publiques la lista.",
                    ),
                    form.notes,
                )
                assertEquals("Crear la jam", form.submitLabel)
                assertEquals("Cancelar", form.cancelLabel)
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
                assertEquals("Escribí la fecha como DD/MM/AAAA.", model.form?.date?.error)

                model.edit(JamFormField.DATE, "1/10/2026")
                model = expectMostRecentItem().newJam()
                assertEquals("Esa fecha ya pasó.", model.form?.date?.error)

                model.edit(JamFormField.DATE, "31/10/2026")
                model = expectMostRecentItem().newJam()
                assertEquals("Ya hay una jam ese día.", model.form?.date?.error)

                model.edit(JamFormField.DATE, "20/10/2026")
                model = expectMostRecentItem().newJam()
                assertEquals(
                    "Es antes de la próxima jam (sábado 31 de octubre). " +
                        "Si querés adelantar esa jam, editá su fecha en Jams.",
                    model.form?.date?.error,
                )
                assertFalse(checkNotNull(model.form).canSubmit)

                model.edit(JamFormField.DATE, "28/11/2026")
                model = expectMostRecentItem().newJam()
                val form = checkNotNull(model.form)
                assertNull(form.date.error)
                assertEquals(
                    "Queda en espera: la próxima jam sigue siendo la del sábado 31 de octubre.",
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
            assertEquals("Escribí el lugar.", model.form?.venue?.error)
            model.edit(JamFormField.VENUE, "=SUMA(A1)")
            model = expectMostRecentItem().newJam()
            assertEquals("El lugar no puede empezar con =, +, - o @.", model.form?.venue?.error)
            model.edit(JamFormField.VENUE, "x".repeat(81))
            model = expectMostRecentItem().newJam()
            assertEquals("El lugar puede tener hasta 80 letras.", model.form?.venue?.error)
            model.edit(JamFormField.TIME, "25:61")
            model = expectMostRecentItem().newJam()
            assertEquals("Escribí la hora como HH:MM, por ejemplo 21:00.", model.form?.time?.error)
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
            assertEquals(NewJamProgressUiModel.Creating("Creando la jam…"), creating.progress)

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
            assertEquals("No se pudo crear la jam", card.title)
            assertEquals("Ya hay una jam o una pestaña con esa fecha en la planilla.", card.message)
            assertEquals("La jam no se creó.", card.consequence)
            assertEquals("Reintentar", card.retryLabel)
            assertEquals("Cerrar", card.dismissLabel)

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
        val expected = mapOf(
            "jam_exists" to "Ya hay una jam o una pestaña con esa fecha en la planilla.",
            "tab_exists" to "Ya hay una jam o una pestaña con esa fecha en la planilla.",
            "past_date" to "Esa fecha ya pasó.",
            "invalid_venue" to "La planilla rechazó los datos. Revisalos y probá de nuevo.",
            "invalid_time" to "La planilla rechazó los datos. Revisalos y probá de nuevo.",
            "missing_header" to "La planilla rechazó el cambio. Revisala y probá de nuevo.",
        )
        expected.forEach { (code, message) -> assertEquals(code, message, createFailureMessage(notCreated(code))) }
        assertEquals(
            "No hay conexión. Probá de nuevo cuando tengas internet.",
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
            assertEquals(
                "El script de la planilla todavía no sabe crear jams. Actualizalo con la guía y probá de nuevo.",
                card.message,
            )
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
            assertEquals("No hay ninguna jam programada.", (model as PastJamsUiModel.Empty).admin?.noFuture)
            assertEquals("Nueva jam", model.newJam().label)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
