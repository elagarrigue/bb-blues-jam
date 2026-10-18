package com.bbbjam.feature.assistant

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.assistant.AssistantFailure
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.AssistantStatus
import com.bbbjam.feature.assistant.apply.ApplyUiModel
import com.bbbjam.feature.assistant.chat.AssistantChat
import com.bbbjam.feature.assistant.chat.ChatTurn
import com.bbbjam.feature.assistant.chat.ServiceState
import com.bbbjam.feature.assistant.chat.assistantModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The streamed songs and the incomplete state (`assistant-streaming`, Part C), copy written out. */
class StreamedAnswerTest {
    private val assistant = FakeSetlistAssistant()
    private val handlers = testHandlers()
    private val available = ServiceState.Known(AssistantStatus.Available)
    private val lost = "La respuesta se cortó antes de terminar. Esto es lo que llegó; no está completa."
    private val notComplete = " Lo que llegó no está completo."

    private fun model(turns: List<ChatTurn>) =
        assistantModel(true, turns, available, "", handlers) as AssistantUiModel.Chat

    private fun reply(turn: ChatTurn) = model(listOf(turn)).turns.single().reply

    private fun row(n: Int) = ProposedSongUiModel("0$n", "Tema $n", "Artista $n", "Bm", "Tonalidad Bm", "Razón $n")

    // --- C1: the chat ---

    @Test
    fun `drafts reach the pending turn and the final reply keeps the turn Answered with the final list`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        val reply = assistant.expectReply()
        chat.send("uno")

        assistant.draft(song(1))
        assertEquals(listOf(song(1)), chat.turns.value.single().draft)
        assistant.draft(song(1), song(2))
        assertEquals(listOf(song(1), song(2)), chat.turns.value.single().draft)

        val final = proposal(songs = 3)
        reply.complete(final)
        val turn = chat.turns.value.single()
        assertEquals(ChatTurn.State.Answered(final), turn.state)
        assertEquals(3, (reply(turn) as ReplyUiModel.Proposal).songs.size)
    }

    @Test
    fun `a draft that arrives after a cancel is ignored and the cancelled turn keeps what came before`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        assistant.expectReply()
        chat.send("uno")
        assistant.draft(song(1))

        chat.cancel(1)
        assistant.draft(song(1), song(2))

        val turn = chat.turns.value.single()
        assertEquals(ChatTurn.State.Cancelled, turn.state)
        assertEquals(listOf(song(1)), turn.draft)
    }

    @Test
    fun `a failure keeps the draft and a retry starts empty`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        val first = assistant.expectReply()
        chat.send("uno")
        assistant.draft(song(1), song(2))
        first.complete(failed(AssistantFailure.Incomplete))
        assertEquals(listOf(song(1), song(2)), chat.turns.value.single().draft)

        assistant.expectReply()
        assertTrue(chat.retry(1))
        assertEquals(emptyList<Any>(), chat.turns.value.single().draft)
        assertEquals(ChatTurn.State.Pending, chat.turns.value.single().state)
    }

    // --- C2: the mapping ---

    @Test
    fun `a pending turn lists the songs that arrived under the proposal heading`() {
        val songs = listOf(song(1), song(2))
        val pending = reply(ChatTurn(1, "uno", ChatTurn.State.Pending, songs)) as ReplyUiModel.Pending
        assertEquals("PROPUESTA · 2 temas", pending.songsHeading)
        assertEquals(listOf(row(1), row(2)), pending.songs)
        assertEquals("El asistente está armando la propuesta. Puede tardar unos segundos.", pending.loadingLabel)
        assertEquals("Cancelar", pending.cancelLabel)
        val one = reply(ChatTurn(1, "uno", ChatTurn.State.Pending, listOf(song(1)))) as ReplyUiModel.Pending
        assertEquals("PROPUESTA · 1 tema", one.songsHeading)
        val none = reply(ChatTurn(1, "uno", ChatTurn.State.Pending)) as ReplyUiModel.Pending
        assertNull(none.songsHeading)
        assertTrue(none.songs.isEmpty())
    }

    @Test
    fun `a pending turn hides the targets of an earlier proposal`() {
        val turns = listOf(
            ChatTurn(1, "uno", ChatTurn.State.Answered(proposal())),
            ChatTurn(2, "dos", ChatTurn.State.Pending, listOf(song(1))),
        )
        val earlier = model(turns).turns.first().reply as ReplyUiModel.Proposal
        assertEquals(ApplyUiModel.None, earlier.apply)
        assertNull(earlier.jam)
    }

    @Test
    fun `an answered proposal ignores the draft and shows the final list`() {
        val answer = ChatTurn.State.Answered(proposal(songs = 1))
        val turn = ChatTurn(1, "uno", answer, listOf(song(1), song(2), song(3)))
        val shown = reply(turn) as ReplyUiModel.Proposal
        assertEquals("PROPUESTA · 1 tema", shown.heading)
        assertEquals(listOf(row(1)), shown.songs)
    }

    @Test
    fun `a broken answer with songs is incomplete with the right line for each cause`() {
        val songs = listOf(song(1), song(2))
        val expected = mapOf(
            AssistantFailure.Incomplete to lost,
            AssistantFailure.InvalidResponse to lost,
            AssistantFailure.Offline to
                "No hay conexión. Para usar el asistente necesitás internet.$notComplete",
            AssistantFailure.Unauthorized to "El servicio rechazó la clave del asistente.$notComplete",
            AssistantFailure.Unavailable(502) to
                "El servicio del asistente no respondió. Probá de nuevo en un rato.$notComplete",
            AssistantFailure.TooManySteps to
                "El asistente no llegó a una propuesta. Probá con un pedido más concreto.$notComplete",
        )
        expected.forEach { (failure, line) ->
            val state = ChatTurn.State.Answered(AssistantReply.Failed(failure))
            val incomplete = reply(ChatTurn(1, "uno", state, songs)) as ReplyUiModel.Incomplete
            assertEquals("PROPUESTA INCOMPLETA · 2 temas", incomplete.heading)
            assertEquals(listOf(row(1), row(2)), incomplete.songs)
            assertEquals(line, incomplete.message)
            assertFalse(incomplete.isCancelled)
            assertEquals("Reintentar", incomplete.retry?.label)
        }
    }

    @Test
    fun `a cancelled or interrupted turn with songs is incomplete and one song reads singular`() {
        val cancelled = reply(ChatTurn(1, "uno", ChatTurn.State.Cancelled, listOf(song(1)))) as ReplyUiModel.Incomplete
        assertEquals("PROPUESTA INCOMPLETA · 1 tema", cancelled.heading)
        assertEquals("Cancelaste este pedido.", cancelled.message)
        assertTrue(cancelled.isCancelled)
        assertEquals("Reintentar", cancelled.retry?.label)

        val turn = ChatTurn(1, "uno", ChatTurn.State.Interrupted, listOf(song(1)))
        assertEquals(lost, (reply(turn) as ReplyUiModel.Incomplete).message)
    }

    @Test
    fun `without songs the failure, cancel and interruption are the existing replies`() {
        assertTrue(reply(ChatTurn(1, "u", ChatTurn.State.Cancelled)) is ReplyUiModel.Cancelled)
        assertTrue(reply(ChatTurn(1, "u", ChatTurn.State.Interrupted)) is ReplyUiModel.Failed)
        val state = ChatTurn.State.Answered(failed(AssistantFailure.Incomplete))
        val shown = reply(ChatTurn(1, "u", state)) as ReplyUiModel.Failed
        assertEquals("La respuesta se cortó antes de terminar. Probá de nuevo.", shown.message)
        assertEquals("Reintentar", shown.retry?.label)
    }

    @Test
    fun `Reintentar is offered only on the last turn`() {
        val turns = listOf(
            ChatTurn(1, "uno", ChatTurn.State.Cancelled, listOf(song(1))),
            ChatTurn(2, "dos", ChatTurn.State.Cancelled, listOf(song(1))),
        )
        val replies = model(turns).turns.map { it.reply as ReplyUiModel.Incomplete }
        assertNull(replies[0].retry)
        assertEquals("Reintentar", replies[1].retry?.label)
    }

    // --- C3: the presenter ---

    private fun flow(chat: AssistantChat) = moleculeFlow(RecompositionMode.Immediate) {
        testPresenter(
            chat,
            testApplier(FakeSetlistSongAdder(), CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)),
            SettableJamsRepository(),
        ).present(Unit)
    }

    private suspend fun ReceiveTurbine<AssistantUiModel>.awaitReply(
        predicate: (ReplyUiModel) -> Boolean,
    ): ReplyUiModel {
        while (true) {
            val reply = (awaitItem() as? AssistantUiModel.Chat)?.turns?.lastOrNull()?.reply
            if (reply != null && predicate(reply)) return reply
        }
    }

    @Test
    fun `pending without songs, then two songs, then the proposal with targets`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        val reply = assistant.expectReply()
        flow(chat).test {
            awaitItem()
            chat.send("uno")
            val empty = awaitReply { it is ReplyUiModel.Pending } as ReplyUiModel.Pending
            assertTrue(empty.songs.isEmpty())

            assistant.draft(song(1), song(2))
            val streaming = awaitReply { it is ReplyUiModel.Pending && it.songs.size == 2 } as ReplyUiModel.Pending
            assertEquals("PROPUESTA · 2 temas", streaming.songsHeading)

            reply.complete(proposal(songs = 2))
            val done = awaitReply { it is ReplyUiModel.Proposal && it.apply is ApplyUiModel.Targets }
            assertEquals(2, (done as ReplyUiModel.Proposal).songs.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `two songs, then the stream breaks, incomplete with Reintentar and no target`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        val reply = assistant.expectReply()
        flow(chat).test {
            awaitItem()
            chat.send("uno")
            awaitReply { it is ReplyUiModel.Pending }
            assistant.draft(song(1), song(2))
            awaitReply { it is ReplyUiModel.Pending && it.songs.size == 2 }

            reply.complete(failed(AssistantFailure.Incomplete))
            val incomplete = awaitReply { it is ReplyUiModel.Incomplete } as ReplyUiModel.Incomplete
            assertEquals(2, incomplete.songs.size)
            assertEquals("Reintentar", incomplete.retry?.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- scroll follow ---

    @Test
    fun `the list follows a growing stream only when it was already at the end`() {
        assertTrue(shouldFollow(wasAtEnd = true, grew = true))
        assertFalse(shouldFollow(wasAtEnd = false, grew = true))
        assertFalse(shouldFollow(wasAtEnd = true, grew = false))
        assertFalse(shouldFollow(wasAtEnd = false, grew = false))
    }
}
