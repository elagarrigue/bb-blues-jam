package com.bbbjam.feature.assistant

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.SongId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyPresenterTest {
    private val assistant = FakeSetlistAssistant()
    private val adder = FakeSetlistSongAdder()
    private val jams = FakeJamsRepository()
    private val applier = ProposalApplier(adder, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    private fun TestScope.chat() = AssistantChat(assistant, ChatScope(testScheduler).scope)

    private fun flow(chat: AssistantChat) =
        moleculeFlow(RecompositionMode.Immediate) { AssistantPresenter(chat, applier, jams).present(Unit) }

    private fun AssistantChat.ask(request: String, reply: com.bbbjam.core.data.assistant.AssistantReply) {
        val deferred = assistant.expectReply()
        send(request)
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

    private fun ReplyUiModel.Proposal.action() = apply as ApplyUiModel.Action

    private suspend fun ReceiveTurbine<AssistantUiModel>.awaitChat(
        predicate: (AssistantUiModel.Chat) -> Boolean,
    ): AssistantUiModel.Chat {
        while (true) {
            val model = awaitItem() as? AssistantUiModel.Chat
            if (model != null && predicate(model)) return model
        }
    }

    private fun TurnUiModel.apply() = (reply as ReplyUiModel.Proposal).apply

    @Test
    fun `the action is drawn only under the latest proposal and not while a turn is pending`() = runTest {
        val chat = chat()
        chat.ask("uno", proposal(songs = 2))
        flow(chat).test {
            val first = awaitProposal { it.apply is ApplyUiModel.Action }
            assertEquals("Usar esta lista", first.action().label)
            assertTrue(first.action().enabled)

            val second = assistant.expectReply()
            chat.send("dos")
            val pending = awaitChat { it.turns.size == 2 }
            assertEquals(ApplyUiModel.None, pending.turns[0].apply())

            second.complete(proposal(songs = 3))
            val answered = awaitChat { it.turns.size == 2 && it.turns[1].apply() is ApplyUiModel.Action }
            assertEquals(ApplyUiModel.None, answered.turns[0].apply())
        }
    }

    @Test
    fun `a proposal without songs gets no action`() = runTest {
        val chat = chat()
        chat.ask("nada", proposal(songs = 0, questions = listOf("¿Cuántos?")))
        flow(chat).test {
            val reply = awaitProposal()
            assertEquals(ApplyUiModel.None, reply.apply)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requesting shows the confirmation with the counts, and cancelling writes nothing`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listed(2, 9)))
        val chat = chat()
        chat.ask("tres", proposal(songs = 4))
        flow(chat).test {
            val ready = awaitProposal { it.apply is ApplyUiModel.Action }
            assertNull(ready.action().confirmation)
            ready.action().events(ApplyUiModel.Action.Event.Request)

            val dialog = awaitProposal { it.action().confirmation != null }.action().confirmation!!
            assertEquals("¿Cargar esta lista?", dialog.title)
            assertEquals(
                listOf(
                    "Se van a agregar 3 temas al final de la lista de la jam del sábado 31 de octubre, " +
                        "en este orden y con la tonalidad del catálogo. Después podés cambiar cualquier tonalidad.",
                    "La lista ya tiene 2 temas; los nuevos van después.",
                    "1 ya está en la lista y no se agrega: Tema 2.",
                ),
                dialog.lines,
            )
            assertEquals("Agregar 3 temas", dialog.confirmLabel)
            assertEquals("Cancelar", dialog.cancelLabel)
            assertTrue(adder.calls.isEmpty())

            dialog.events(ConfirmationUiModel.Event.Cancel)
            assertNull(awaitProposal { it.action().confirmation == null }.action().confirmation)
        }
        assertTrue(adder.calls.isEmpty())
    }

    @Test
    fun `a published jam adds the warning line`() = runTest {
        jams.snapshot = snapshot(jam(JamStatus.PUBLISHED, listed(1)))
        val chat = chat()
        chat.ask("dos", proposal(songs = 2))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Action }.action().events(ApplyUiModel.Action.Event.Request)
            val dialog = awaitProposal { it.action().confirmation != null }.action().confirmation!!
            assertEquals(
                "La lista ya está publicada: los músicos van a ver los temas apenas se agreguen.",
                dialog.lines.last(),
            )
            assertEquals("Se va a agregar 1 tema", dialog.lines.first().substringBefore(" al final"))
            assertEquals("Agregar 1 tema", dialog.confirmLabel)
        }
    }

    @Test
    fun `confirming twice in one frame starts one run, in order, and ends with the success summary`() = runTest {
        val chat = chat()
        chat.ask("tres", proposal(songs = 3))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Action }.action().events(ApplyUiModel.Action.Event.Request)
            val dialog = awaitProposal { it.action().confirmation != null }.action().confirmation!!
            dialog.events(ConfirmationUiModel.Event.Confirm)
            dialog.events(ConfirmationUiModel.Event.Confirm)

            val done = awaitProposal { it.summary != null && it.summary!!.stopLabel == null }
            assertEquals(
                "Listo: se agregaron 3 temas a la jam del sábado 31 de octubre.",
                done.summary!!.text,
            )
            assertEquals(ApplyTone.SUCCESS, done.summary!!.tone)
            assertEquals(ApplyUiModel.None, done.apply)
            assertEquals(listOf("s1", "s2", "s3"), adder.calls.map { it.songId.value })
            assertTrue(done.songs.all { it.applyStatus?.kind == ApplyStatusKind.DONE })
        }
        assertEquals(3, adder.calls.size)
    }

    @Test
    fun `a partial failure shows per-song captions, an error summary and offers the retry`() = runTest {
        adder.reject(2, WriteOutcome.Rejected("unknown_song"))
        val chat = chat()
        chat.ask("tres", proposal(songs = 3))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Action }.action().events(ApplyUiModel.Action.Event.Request)
            awaitProposal { it.action().confirmation != null }.action().confirmation!!.events(
                ConfirmationUiModel.Event.Confirm,
            )

            val done = awaitProposal { it.summary?.stopLabel == null && it.summary != null }
            assertEquals("Se agregaron 2 de 3 temas. 1 no se agregó.", done.summary!!.text)
            assertEquals(ApplyTone.FAILURE, done.summary!!.tone)
            assertEquals(
                "No se agregó: Ese tema ya no está en el catálogo.",
                done.songs[1].applyStatus!!.text,
            )
            assertEquals(ApplyStatusKind.FAILED, done.songs[1].applyStatus!!.kind)
            assertEquals("Reintentar los que faltan", done.action().label)
        }
    }

    @Test
    fun `an abort leaves the rest not attempted and says why`() = runTest {
        adder.reject(1, WriteOutcome.AccessRefused)
        val chat = chat()
        chat.ask("tres", proposal(songs = 3))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Action }.action().events(ApplyUiModel.Action.Event.Request)
            awaitProposal { it.action().confirmation != null }.action().confirmation!!.events(
                ConfirmationUiModel.Event.Confirm,
            )

            val done = awaitProposal { it.summary != null && it.summary!!.stopLabel == null }
            assertEquals(
                "Se cortó la carga: se agregaron 0 de 3 temas. " +
                    "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar.",
                done.summary!!.text,
            )
            assertEquals(1, adder.calls.size)
            assertEquals(
                listOf(ApplyStatusKind.FAILED, ApplyStatusKind.NOT_ATTEMPTED, ApplyStatusKind.NOT_ATTEMPTED),
                done.songs.map { it.applyStatus!!.kind },
            )
            assertEquals("Sin intentar", done.songs[2].applyStatus!!.text)
        }
    }

    @Test
    fun `stop is offered while running and keeps the song in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        adder.gates[SongId("s2")] = gate
        val chat = chat()
        chat.ask("cuatro", proposal(songs = 4))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Action }.action().events(ApplyUiModel.Action.Event.Request)
            awaitProposal { it.action().confirmation != null }.action().confirmation!!.events(
                ConfirmationUiModel.Event.Confirm,
            )

            val running = awaitProposal { it.summary?.stopLabel != null && it.songs[1].applyStatus != null }
            assertEquals("Detener", running.summary!!.stopLabel)
            assertEquals("Agregando…", running.songs[1].applyStatus!!.text)
            assertEquals(ApplyUiModel.None, running.apply)

            running.summary!!.events(ApplySummaryUiModel.Event.Stop)
            gate.complete(Unit)

            val done = awaitProposal { it.summary != null && it.summary!!.stopLabel == null }
            assertEquals("Detuviste la carga: se agregaron 2 de 4 temas.", done.summary!!.text)
            assertEquals(ApplyTone.MUTED, done.summary!!.tone)
            assertEquals(2, adder.calls.size)
        }
    }

    @Test
    fun `blocked states draw a note and no button, and an unread snapshot draws nothing`() = runTest {
        val cases = listOf(
            snapshot(upcoming = null) to "No hay una próxima jam cargada. Creala en la planilla y volvé a intentar.",
            snapshot(jam(JamStatus.DRAFT, com.bbbjam.core.model.Setlist.Withheld)) to
                "No pude leer la lista de la próxima jam. Abrí Próxima jam para actualizarla y volvé.",
            snapshot(jam(setlist = listed(1, 2))) to "Todos estos temas ya están en la lista de la próxima jam.",
        )
        for ((snapshot, note) in cases) {
            val blockedJams = FakeJamsRepository(snapshot)
            val chat = chat()
            chat.ask("dos", proposal(songs = 2))
            moleculeFlow(RecompositionMode.Immediate) {
                AssistantPresenter(chat, applier, blockedJams).present(Unit)
            }.test {
                assertEquals(ApplyUiModel.Blocked(note), awaitProposal { it.apply != ApplyUiModel.None }.apply)
            }
        }
        val unread = FakeJamsRepository(null)
        val chat = chat()
        chat.ask("dos", proposal(songs = 2))
        moleculeFlow(RecompositionMode.Immediate) {
            AssistantPresenter(chat, applier, unread).present(Unit)
        }.test {
            assertFalse(awaitProposal().apply is ApplyUiModel.Blocked)
            assertEquals(ApplyUiModel.None, awaitProposal().apply)
        }
        assertTrue(adder.calls.isEmpty())
    }
}
