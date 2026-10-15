package com.bbbjam.feature.assistant

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.ProposedJam
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.ui.jamform.JamFormField
import com.bbbjam.core.ui.jamform.JamFormUiModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Crear una jam nueva con esta lista (`assistant-proposal-targets`, Part C): the form, the run, the summaries. */
class CreatePresenterTest {
    private val assistant = FakeSetlistAssistant()
    private val log: CallLog = mutableListOf()
    private val adder = FakeSetlistSongAdder(log)
    private val creator = FakeJamCreator(log)
    private val jams = FakeJamsRepository()
    private val lock = RunLock()
    private val applier = ProposalApplier(
        adder,
        FakeSetlistSongRemover(log),
        FakeSetlistSongMover(log),
        creator,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        lock,
    )
    private val heldBack = LocalDate.of(2026, 11, 28)

    private fun TestScope.chat() = AssistantChat(assistant, ChatScope(testScheduler).scope)

    private fun flow(chat: AssistantChat) =
        moleculeFlow(RecompositionMode.Immediate) { testPresenter(chat, applier, jams).present(Unit) }

    private fun AssistantChat.ask(reply: AssistantReply) {
        val deferred = assistant.expectReply()
        send("pedido")
        deferred.complete(reply)
    }

    private suspend fun ReceiveTurbine<AssistantUiModel>.awaitProposal(
        predicate: (ReplyUiModel.Proposal) -> Boolean = { true },
    ): ReplyUiModel.Proposal {
        while (true) {
            val chat = awaitItem() as? AssistantUiModel.Chat
            val proposal = chat?.turns?.lastOrNull()?.reply as? ReplyUiModel.Proposal
            if (proposal != null && predicate(proposal)) return proposal
        }
    }

    private fun ReplyUiModel.Proposal.targets() = apply as ApplyUiModel.Targets

    private fun ReplyUiModel.Proposal.create() = targets().actions.first { it.target == ApplyMode.CREATE }

    private fun ReplyUiModel.Proposal.form() = targets().createForm

    private suspend fun ReceiveTurbine<AssistantUiModel>.openForm(): JamFormUiModel {
        awaitProposal { it.apply is ApplyUiModel.Targets }.create().events(TargetActionUiModel.Event.Request)
        return checkNotNull(awaitProposal { it.form() != null }.form())
    }

    private fun upcomingJam(date: LocalDate = JAM_DATE) =
        Jam(date, LocalTime.of(21, 0), "Sala", JamStatus.DRAFT, listed())

    @Test
    fun `Crear is the third target and opens the form seeded from the proposal jam`() = runTest {
        val chat = chat()
        chat.ask(proposal(songs = 3).copy(jam = ProposedJam(heldBack, LocalTime.of(20, 30), "El Sótano")))
        flow(chat).test {
            val form = openForm()
            assertEquals("Nueva jam con esta lista", form.title)
            assertEquals("28/11/2026", form.date.value)
            assertEquals("20:30", form.time.value)
            assertEquals("El Sótano", form.venue.value)
            assertEquals("Crear y cargar 3 temas", form.submitLabel)
            assertEquals("Cancelar", form.cancelLabel)
            assertTrue(form.canSubmit)
            assertEquals(
                listOf(
                    "Se crea como borrador y se cargan los 3 temas en este orden.",
                    "Se crea como borrador: los músicos no la ven hasta que la pases a creada o publiques la lista.",
                    "Queda en espera detrás de la del sábado 31 de octubre; la lista se carga en esta jam nueva " +
                        "y la vas a ver en Próxima jam cuando pase esa.",
                ),
                form.notes,
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(log.isEmpty())
    }

    @Test
    fun `without a proposal jam the form takes the date empty and the time and venue of the latest jam`() = runTest {
        val latest = Jam(LocalDate.of(2026, 9, 26), LocalTime.of(22, 15), "La Macanuda", JamStatus.PUBLISHED, listed())
        jams.snapshot = JamsSnapshot(upcomingJam(), listOf(latest), Freshness(Instant.EPOCH, null, false))
        val chat = chat()
        chat.ask(proposal(songs = 2))
        flow(chat).test {
            val form = openForm()
            assertEquals("", form.date.value)
            assertEquals("21:00", form.time.value)
            assertEquals("Sala", form.venue.value)
            assertFalse(form.canSubmit)
            assertNull(form.date.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with no jam at all the form takes 21 00 and an empty venue, and Crear is the only target`() = runTest {
        jams.snapshot = snapshot(upcoming = null)
        val chat = chat()
        chat.ask(proposal(songs = 2))
        flow(chat).test {
            val reply = awaitProposal { it.apply is ApplyUiModel.Targets }
            assertEquals(listOf(ApplyMode.CREATE), reply.targets().actions.map { it.target })
            assertEquals(listOf("No hay una próxima jam: podés crear una con esta lista."), reply.targets().notes)
            reply.create().events(TargetActionUiModel.Event.Request)
            val form = checkNotNull(awaitProposal { it.form() != null }.form())
            assertEquals("21:00", form.time.value)
            assertEquals("", form.venue.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit is disabled for a past, an existing and an earlier date, and a held-back date submits`() = runTest {
        jams.snapshot = JamsSnapshot(upcomingJam(), emptyList(), Freshness(Instant.EPOCH, null, false))
        val chat = chat()
        chat.ask(proposal(songs = 2))
        flow(chat).test {
            var form = openForm()
            form.events(JamFormUiModel.Event.Edit(JamFormField.VENUE, "Sala"))
            val cases = listOf(
                "1/10/2026" to "Esa fecha ya pasó.",
                "31/10/2026" to "Ya hay una jam ese día.",
                "20/10/2026" to
                    "Es antes de la próxima jam (sábado 31 de octubre) y la dejaría en espera. " +
                    "Si de verdad querés eso, agregala en la planilla.",
            )
            for ((typed, error) in cases) {
                form.events(JamFormUiModel.Event.Edit(JamFormField.DATE, typed))
                form = checkNotNull(awaitProposal { it.form()?.date?.value == typed }.form())
                assertEquals(error, form.date.error)
                assertFalse(typed, form.canSubmit)
            }
            form.events(JamFormUiModel.Event.Edit(JamFormField.DATE, "28/11/2026"))
            form = checkNotNull(awaitProposal { it.form()?.date?.value == "28/11/2026" }.form())
            assertNull(form.date.error)
            assertTrue(form.canSubmit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submitting a held-back date creates the jam then adds every song to the echoed date, never the upcoming`() =
        runTest {
            jams.snapshot = JamsSnapshot(upcomingJam(), emptyList(), Freshness(Instant.EPOCH, null, false))
            val chat = chat()
            chat.ask(proposal(songs = 3))
            flow(chat).test {
                var form = openForm()
                form.events(JamFormUiModel.Event.Edit(JamFormField.DATE, "28/11/2026"))
                form = checkNotNull(awaitProposal { it.form()?.date?.value == "28/11/2026" }.form())
                form.events(JamFormUiModel.Event.Submit)
                form.events(JamFormUiModel.Event.Submit)

                val done = awaitProposal { it.summary?.text?.startsWith("Listo") == true }
                assertEquals(
                    "Listo: se creó la jam del sábado 28 de noviembre como borrador, en espera detrás de la del " +
                        "sábado 31 de octubre, y se cargaron 3 temas.",
                    done.summary!!.text,
                )
                assertEquals(ApplyUiModel.None, done.apply)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("create", "add s1", "add s2", "add s3"), log)
            assertEquals(listOf(heldBack), creator.calls.map { it.date })
            assertEquals(setOf(heldBack), adder.calls.map { it.date }.toSet())
        }

    @Test
    fun `a server that echoes another date receives the songs there`() = runTest {
        jams.snapshot = JamsSnapshot(upcomingJam(), emptyList(), Freshness(Instant.EPOCH, null, false))
        val echoed = LocalDate.of(2026, 11, 29)
        creator.outcome = com.bbbjam.core.data.setlist.CreateJamOutcome.Created(echoed, LocalTime.of(21, 0), "Sala")
        val chat = chat()
        chat.ask(proposal(songs = 1).copy(jam = ProposedJam(heldBack, LocalTime.of(21, 0), "Sala")))
        flow(chat).test {
            val form = openForm()
            form.events(JamFormUiModel.Event.Submit)
            awaitProposal { it.summary != null }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(setOf(echoed), adder.calls.map { it.date }.toSet())
    }

    @Test
    fun `cancelling the form writes nothing`() = runTest {
        val chat = chat()
        chat.ask(proposal(songs = 2).copy(jam = ProposedJam(heldBack, LocalTime.of(21, 0), "Sala")))
        flow(chat).test {
            val form = openForm()
            form.events(JamFormUiModel.Event.Cancel)
            assertNull(awaitProposal { it.form() == null }.form())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(log.isEmpty())
    }

    @Test
    fun `a failed create loads nothing, says so, and offers Crear again`() = runTest {
        creator.outcome = com.bbbjam.core.data.setlist.CreateJamOutcome.NotCreated(WriteOutcome.Rejected("jam_exists"))
        val chat = chat()
        chat.ask(proposal(songs = 2).copy(jam = ProposedJam(heldBack, LocalTime.of(21, 0), "Sala")))
        flow(chat).test {
            openForm().events(JamFormUiModel.Event.Submit)
            val failed = awaitProposal { it.summary?.text?.startsWith("No se pudo crear la jam") == true }
            assertEquals(
                "No se pudo crear la jam: ya hay una jam con esa fecha en la planilla. No se cargó ningún tema.",
                failed.summary!!.text,
            )
            assertTrue(failed.targets().actions.any { it.target == ApplyMode.CREATE })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("create"), log)
    }

    @Test
    fun `an old script refusing the held-back load keeps the jam and says to update it`() = runTest {
        jams.snapshot = JamsSnapshot(upcomingJam(), emptyList(), Freshness(Instant.EPOCH, null, false))
        adder.reject(1, WriteOutcome.Rejected("jam_not_editable"))
        val chat = chat()
        chat.ask(proposal(songs = 2).copy(jam = ProposedJam(heldBack, LocalTime.of(21, 0), "Sala")))
        flow(chat).test {
            openForm().events(JamFormUiModel.Event.Submit)
            val outdated = awaitProposal { it.summary?.text?.startsWith("La jam se creó, pero el script") == true }
            assertEquals(
                "La jam se creó, pero el script de la planilla todavía no sabe cargar listas en una jam en espera. " +
                    "Actualizalo con la guía y reintentá.",
                outdated.summary!!.text,
            )
            assertEquals(ApplyTone.FAILURE, outdated.summary!!.tone)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("create", "add s1"), log)
    }

    @Test
    fun `a partly loaded new jam offers the retry for that jam's own date`() = runTest {
        jams.snapshot = JamsSnapshot(upcomingJam(), emptyList(), Freshness(Instant.EPOCH, null, false))
        adder.reject(2, WriteOutcome.Rejected("unknown_song"))
        val chat = chat()
        chat.ask(proposal(songs = 3).copy(jam = ProposedJam(heldBack, LocalTime.of(21, 0), "Sala")))
        flow(chat).test {
            openForm().events(JamFormUiModel.Event.Submit)
            val partial = awaitProposal { it.summary?.text?.startsWith("La jam del sábado 28 de noviembre") == true }
            assertEquals(
                "La jam del sábado 28 de noviembre se creó como borrador, pero se cargaron 2 de 3 temas. " +
                    "1 no se agregó.",
                partial.summary!!.text,
            )

            // The created jam is now held back with the songs that did load.
            val created = upcomingJam(heldBack).copy(setlist = Setlist.Available(listOf(jamSong(1), jamSong(3))))
            jams.snapshot =
                JamsSnapshot(upcomingJam(), emptyList(), Freshness(Instant.EPOCH, null, false), listOf(created))
            val retry = awaitProposal { it.targets().actions.singleOrNull()?.label == "Reintentar los que faltan" }
            assertEquals(listOf(ApplyMode.ADD), retry.targets().actions.map { it.target })
            retry.targets().actions.single().events(TargetActionUiModel.Event.Request)
            val dialog = checkNotNull(awaitProposal { it.targets().confirmation != null }.targets().confirmation)
            assertTrue(dialog.lines.first().contains("sábado 28 de noviembre"))
            adder.outcomes.remove(com.bbbjam.core.model.SongId("s2"))
            dialog.events(ConfirmationUiModel.Event.Confirm)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(heldBack, adder.calls.last().date)
        assertEquals("s2", adder.calls.last().songId.value)
    }

    @Test
    fun `a list load and a jam creation never overlap`() = runTest {
        val runner = JamCreationRunner(creator, jams, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), lock)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        adder.gates[com.bbbjam.core.model.SongId("s1")] = gate
        val plan = ApplyPlan.Ready(JAM_DATE, JamStatus.DRAFT, listOf(song(1)), emptyList(), 0)
        assertTrue(applier.apply(1, plan))

        val jam = ProposedJam(heldBack, LocalTime.of(21, 0), "Sala")
        assertFalse(runner.create(2, jam))
        gate.complete(Unit)
        val creation = kotlinx.coroutines.CompletableDeferred<Unit>()
        creator.gate = creation
        assertTrue(runner.create(2, jam))
        assertFalse(applier.apply(3, plan))
        creation.complete(Unit)
        assertTrue(applier.apply(3, plan))
    }

    private companion object {
        val JAM_DATE: LocalDate = LocalDate.of(2026, 10, 31)
    }
}
