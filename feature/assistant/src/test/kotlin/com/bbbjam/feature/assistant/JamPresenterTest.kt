package com.bbbjam.feature.assistant

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.JamRejection
import com.bbbjam.core.data.assistant.ProposedJam
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.model.JamStatus
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamPresenterTest {
    private val assistant = FakeSetlistAssistant()
    private val adder = FakeSetlistSongAdder()
    private val creator = FakeJamCreator()
    private val jams = FakeJamsRepository()
    private val applier = testApplier(adder, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    private val runner = JamCreationRunner(creator, jams, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    private val newDate = LocalDate.of(2026, 11, 28)
    private val newJam = ProposedJam(newDate, LocalTime.of(21, 30), "La Macanuda")

    private fun TestScope.chat() = AssistantChat(assistant, ChatScope(testScheduler).scope)

    private fun flow(chat: AssistantChat) =
        moleculeFlow(RecompositionMode.Immediate) { testPresenter(chat, applier, jams, runner).present(Unit) }

    private fun AssistantChat.ask(request: String, reply: AssistantReply) {
        val deferred = assistant.expectReply()
        send(request)
        deferred.complete(reply)
    }

    private fun jamProposal(songs: Int = 0, jam: ProposedJam? = newJam) =
        proposal(songs = songs, message = "Listo.").copy(jam = jam)

    private suspend fun ReceiveTurbine<AssistantUiModel>.awaitProposal(
        predicate: (ReplyUiModel.Proposal) -> Boolean = { true },
    ): ReplyUiModel.Proposal {
        while (true) {
            val chat = awaitItem() as? AssistantUiModel.Chat
            val proposal = chat?.turns?.lastOrNull()?.reply as? ReplyUiModel.Proposal
            if (proposal != null && predicate(proposal)) return proposal
        }
    }

    private suspend fun ReceiveTurbine<AssistantUiModel>.awaitChat(
        predicate: (AssistantUiModel.Chat) -> Boolean,
    ): AssistantUiModel.Chat {
        while (true) {
            val model = awaitItem() as? AssistantUiModel.Chat
            if (model != null && predicate(model)) return model
        }
    }

    private fun TurnUiModel.card() = (reply as ReplyUiModel.Proposal).jam

    @Test
    fun `the card shows the date, time, venue and the held-back note, with the button on the latest turn only`() =
        runTest {
            val chat = chat()
            chat.ask("uno", jamProposal())
            flow(chat).test {
                val card = awaitProposal { it.jam?.action != null }.jam!!
                assertEquals("Nueva jam", card.heading)
                assertEquals("Sábado 28 de noviembre de 2026", card.date)
                assertEquals("21:30 · La Macanuda", card.detail)
                assertEquals(
                    "Queda en espera: la próxima jam sigue siendo la del sábado 31 de octubre.",
                    card.note,
                )
                assertEquals("Crear la jam", card.action!!.label)
                assertTrue(card.action!!.enabled)
                assertNull(card.result)

                val second = assistant.expectReply()
                chat.send("dos")
                val pending = awaitChat { it.turns.size == 2 }
                assertNull(pending.turns[0].card()!!.action)

                second.complete(jamProposal(jam = newJam.copy(date = LocalDate.of(2026, 12, 12))))
                val answered = awaitChat { it.turns.size == 2 && it.turns[1].card()?.action != null }
                assertNull(answered.turns[0].card()!!.action)
                assertNull(answered.turns[0].card()!!.note)
                assertEquals("Sábado 28 de noviembre de 2026", answered.turns[0].card()!!.date)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `requesting shows the dialog lines and cancelling writes nothing`() = runTest {
        val chat = chat()
        chat.ask("uno", jamProposal())
        flow(chat).test {
            val ready = awaitProposal { it.jam?.action != null }
            assertNull(ready.jam!!.action!!.confirmation)
            ready.jam!!.action!!.events(JamActionUiModel.Event.Request)

            val dialog = awaitProposal { it.jam?.action?.confirmation != null }.jam!!.action!!.confirmation!!
            assertEquals("¿Crear la jam?", dialog.title)
            assertEquals(
                listOf(
                    "Fecha: sábado 28 de noviembre de 2026",
                    "Hora: 21:30",
                    "Lugar: La Macanuda",
                    "Se crea como borrador: los músicos no la ven hasta que la pases a creada o publiques la lista.",
                    "La próxima jam sigue siendo la del sábado 31 de octubre; esta queda en espera hasta que pase.",
                ),
                dialog.lines,
            )
            assertEquals("Crear la jam", dialog.confirmLabel)
            assertEquals("Cancelar", dialog.cancelLabel)
            assertTrue(creator.calls.isEmpty())

            dialog.events(ConfirmationUiModel.Event.Cancel)
            assertNull(awaitProposal { it.jam?.action?.confirmation == null }.jam!!.action!!.confirmation)
        }
        assertTrue(creator.calls.isEmpty())
    }

    @Test
    fun `with no upcoming jam the dialog has no held-back line`() = runTest {
        jams.snapshot = snapshot(upcoming = null)
        val chat = chat()
        chat.ask("uno", jamProposal())
        flow(chat).test {
            val ready = awaitProposal { it.jam?.action != null }
            assertNull(ready.jam!!.note)
            ready.jam!!.action!!.events(JamActionUiModel.Event.Request)
            val dialog = awaitProposal { it.jam?.action?.confirmation != null }.jam!!.action!!.confirmation!!
            assertEquals(4, dialog.lines.size)
        }
    }

    @Test
    fun `a date earlier than the upcoming jam or already taken is blocked with a note and no button`() = runTest {
        val chat = chat()
        chat.ask("uno", jamProposal(jam = newJam.copy(date = LocalDate.of(2026, 10, 20))))
        flow(chat).test {
            val earlier = awaitProposal { it.jam?.note != null }.jam!!
            assertNull(earlier.action)
            assertEquals(
                "Esa fecha es anterior a la próxima jam (sábado 31 de octubre): la dejaría en espera. " +
                    "Si de verdad querés eso, agregala en la planilla.",
                earlier.note,
            )
            cancelAndIgnoreRemainingEvents()
        }

        val taken = chat()
        taken.ask("dos", jamProposal(jam = newJam.copy(date = JAM_DATE)))
        flow(taken).test {
            val exists = awaitProposal { it.jam?.note != null }.jam!!
            assertNull(exists.action)
            assertEquals("Ya hay una jam con esa fecha. Pedime otra fecha.", exists.note)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(creator.calls.isEmpty())
    }

    @Test
    fun `confirming twice creates once, then shows the result and refreshes the jams once`() = runTest {
        val chat = chat()
        chat.ask("uno", jamProposal())
        flow(chat).test {
            awaitProposal { it.jam?.action != null }.jam!!.action!!.events(JamActionUiModel.Event.Request)
            val dialog = awaitProposal { it.jam?.action?.confirmation != null }.jam!!.action!!.confirmation!!
            dialog.events(ConfirmationUiModel.Event.Confirm)
            dialog.events(ConfirmationUiModel.Event.Confirm)

            val done = awaitProposal { it.jam?.result?.text == "Jam creada como borrador." }.jam!!
            assertEquals(ApplyTone.SUCCESS, done.result!!.tone)
            assertNull(done.action)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(CreateCall(newDate, LocalTime.of(21, 30), "La Macanuda")), creator.calls)
        assertEquals(1, jams.refreshes)
    }

    @Test
    fun `a failure shows its sentence and offers Reintentar through the same dialog`() = runTest {
        creator.outcome = CreateJamOutcome.NotCreated(WriteOutcome.Rejected("jam_exists"))
        val chat = chat()
        chat.ask("uno", jamProposal())
        flow(chat).test {
            awaitProposal { it.jam?.action != null }.jam!!.action!!.events(JamActionUiModel.Event.Request)
            awaitProposal { it.jam?.action?.confirmation != null }.jam!!.action!!.confirmation!!.events(
                ConfirmationUiModel.Event.Confirm,
            )

            val failed = awaitProposal { it.jam?.result?.tone == ApplyTone.FAILURE }.jam!!
            assertEquals("No se pudo crear: ya hay una jam con esa fecha en la planilla.", failed.result!!.text)
            assertEquals(ApplyTone.FAILURE, failed.result!!.tone)
            assertEquals("Reintentar", failed.action!!.label)
            assertEquals(0, jams.refreshes)

            creator.outcome = CreateJamOutcome.ScriptOutdated
            failed.action!!.events(JamActionUiModel.Event.Request)
            awaitProposal { it.jam?.action?.confirmation != null }.jam!!.action!!.confirmation!!.events(
                ConfirmationUiModel.Event.Confirm,
            )
            val outdated = awaitProposal { it.jam?.result?.text?.startsWith("El script") == true }.jam!!
            assertEquals(
                "El script de la planilla todavía no sabe crear jams. Actualizalo con la guía y probá de nuevo.",
                outdated.result!!.text,
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, creator.calls.size)
    }

    @Test
    fun `the list targets of a jam turn act on the upcoming jam and do not wait for the new one`() = runTest {
        val chat = chat()
        chat.ask("uno", jamProposal(songs = 2))
        flow(chat).test {
            val reply = awaitProposal { it.apply is ApplyUiModel.Targets }
            val targets = reply.apply as ApplyUiModel.Targets
            assertEquals(
                listOf(
                    "Agregar a la próxima jam",
                    "Reemplazar la lista de la próxima jam",
                    "Crear una jam nueva con esta lista",
                ),
                targets.actions.map { it.label },
            )
            assertTrue(targets.actions.all { it.enabled })
            // C5: the card is information; the targets block creates the jam.
            assertNull(reply.jam!!.action)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a rejected jam draws a muted note and no card`() = runTest {
        val chat = chat()
        chat.ask("uno", proposal(songs = 0).copy(jamRejection = JamRejection.PAST_DATE))
        flow(chat).test {
            val reply = awaitProposal { it.jamNote != null }
            assertEquals("La fecha propuesta ya pasó; pedile otra.", reply.jamNote)
            assertNull(reply.jam)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a proposal without a jam keeps the apply rules`() = runTest {
        val chat = chat()
        chat.ask("uno", proposal(songs = 2))
        flow(chat).test {
            val reply = awaitProposal { it.apply is ApplyUiModel.Targets }
            assertNull(reply.jam)
            assertNotNull(reply.apply)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `creating is disabled while a load runs`() = runTest {
        val chat = chat()
        chat.ask("uno", jamProposal(songs = 0))
        val running = testApplier(adder, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        adder.gates[com.bbbjam.core.model.SongId("s1")] = kotlinx.coroutines.CompletableDeferred()
        val plan = ApplyPlan.Ready(JAM_DATE, JamStatus.DRAFT, listOf(song(1)), emptyList(), 0)
        running.apply(99, plan)
        moleculeFlow(RecompositionMode.Immediate) {
            testPresenter(chat, running, jams, runner).present(Unit)
        }.test {
            val card = awaitProposal { it.jam?.action != null }.jam!!
            assertEquals(false, card.action!!.enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
