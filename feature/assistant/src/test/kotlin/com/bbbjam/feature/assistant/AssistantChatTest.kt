package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.AssistantFailure
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.AssistantStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantChatTest {
    private val assistant = FakeSetlistAssistant()

    @Test
    fun `send appends a pending turn and then the answer`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        val reply = assistant.expectReply()
        assertTrue(chat.send("  doce temas  "))
        assertEquals(listOf(ChatTurn(1, "doce temas", ChatTurn.State.Pending)), chat.turns.value)

        val answer = proposal()
        reply.complete(answer)
        assertEquals(listOf(ChatTurn(1, "doce temas", ChatTurn.State.Answered(answer))), chat.turns.value)
        assertEquals(listOf("doce temas"), assistant.requests)
    }

    @Test
    fun `blank, not configured and pending sends return false and change nothing`() = runTest {
        val cs = ChatScope(testScheduler)
        val chat = AssistantChat(assistant, cs.scope)
        assertFalse(chat.send("   "))
        assertFalse(chat.send(""))
        assertTrue(chat.turns.value.isEmpty())

        assistant.expectReply()
        assertTrue(chat.send("uno"))
        assertFalse(chat.send("dos"))
        assertEquals(1, chat.turns.value.size)

        val unconfigured = FakeSetlistAssistant(isConfigured = false)
        val other = AssistantChat(unconfigured, cs.scope)
        assertFalse(other.isConfigured)
        assertFalse(other.send("hola"))
        assertTrue(other.turns.value.isEmpty())
        assertEquals(0, unconfigured.conversationsStarted)
    }

    @Test
    fun `the conversation is created once and reused across turns and retries`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        assistant.expectReply().complete(proposal())
        assertTrue(chat.send("uno"))
        assistant.expectReply().complete(failed(AssistantFailure.Offline))
        assertTrue(chat.send("dos"))
        assistant.expectReply().complete(proposal())
        assertTrue(chat.retry(chat.turns.value.last().id))
        assertEquals(1, assistant.conversationsStarted)
        assertEquals(listOf("uno", "dos", "dos"), assistant.requests)
    }

    @Test
    fun `cancel ends a pending turn as Cancelled and the request saw the cancellation`() = runTest {
        val cs = ChatScope(testScheduler)
        val chat = AssistantChat(assistant, cs.scope)
        assistant.expectReply()
        chat.send("uno")
        chat.cancel(1)
        assertEquals(ChatTurn.State.Cancelled, chat.turns.value.single().state)
        assertTrue(assistant.sawCancellation)
        assertTrue(cs.escaped.isEmpty())
        // The turn is over, so a second send is accepted.
        assistant.expectReply()
        assertTrue(chat.send("dos"))
    }

    @Test
    fun `cancel does nothing to a turn that is not pending`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        val answer = proposal()
        assistant.expectReply().complete(answer)
        chat.send("uno")
        chat.cancel(1)
        chat.cancel(99)
        assertEquals(ChatTurn.State.Answered(answer), chat.turns.value.single().state)
    }

    @Test
    fun `an unexpected exception ends the turn as Interrupted and reaches the scope handler`() = runTest {
        val cs = ChatScope(testScheduler)
        val chat = AssistantChat(assistant, cs.scope)
        assistant.expectReply().completeExceptionally(IllegalStateException("boom"))
        chat.send("uno")
        assertEquals(ChatTurn.State.Interrupted, chat.turns.value.single().state)
        assertEquals(1, cs.escaped.size)
        assertTrue(cs.escaped.single() is IllegalStateException)
    }

    @Test
    fun `retry works only on the last turn, only after a failure, a cancellation or an interruption`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)

        // Success is not retryable.
        assistant.expectReply().complete(proposal())
        chat.send("uno")
        assertFalse(chat.retry(1))

        // A failure is, and the retry replaces the turn with a new pending one for the same request.
        assistant.expectReply().complete(failed(AssistantFailure.InvalidResponse))
        chat.send("dos")
        val pendingRetry = assistant.expectReply()
        assertFalse("not the last turn", chat.retry(1))
        assertTrue(chat.retry(2))
        assertEquals(listOf(1L, 3L), chat.turns.value.map { it.id })
        assertEquals(ChatTurn.State.Pending, chat.turns.value.last().state)
        assertEquals("dos", chat.turns.value.last().request)

        // Not while a turn is pending.
        assertFalse(chat.retry(3))
        pendingRetry.complete(failed(AssistantFailure.Offline))

        // Cancelled and interrupted turns are retryable too.
        assistant.expectReply()
        assertTrue(chat.retry(3))
        chat.cancel(4)
        assertEquals(ChatTurn.State.Cancelled, chat.turns.value.last().state)
        val interrupted = assistant.expectReply()
        assertTrue(chat.retry(4))
        interrupted.completeExceptionally(IllegalStateException("x"))
        assertEquals(ChatTurn.State.Interrupted, chat.turns.value.last().state)
        val answer = proposal()
        assistant.expectReply().complete(answer)
        assertTrue(chat.retry(5))
        assertEquals(ChatTurn.State.Answered(answer), chat.turns.value.last().state)
    }

    @Test
    fun `an answered NotConfigured reply is not retryable`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        assistant.expectReply().complete(AssistantReply.NotConfigured)
        chat.send("uno")
        assertFalse(chat.retry(1))
    }

    @Test
    fun `the service starts Checking and checkStatus reads it, silently from Available`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        assertEquals(ServiceState.Checking, chat.service.value)
        assertFalse(chat.isServiceAvailable)
        chat.checkStatus()
        assertEquals(ServiceState.Known(AssistantStatus.Available), chat.service.value)
        assertTrue(chat.isServiceAvailable)

        // A re-check from Available is silent: the state is never Checking in between.
        val gate = CompletableDeferred<Unit>()
        assistant.statusGate = gate
        assistant.status = AssistantStatus.ServiceDown
        chat.checkStatus()
        assertEquals(ServiceState.Known(AssistantStatus.Available), chat.service.value)
        gate.complete(Unit)
        assertEquals(ServiceState.Known(AssistantStatus.ServiceDown), chat.service.value)

        // From a problem it shows Checking until the answer, and a running check is not repeated.
        val second = CompletableDeferred<Unit>()
        assistant.statusGate = second
        assistant.status = AssistantStatus.Available
        chat.checkStatus()
        chat.checkStatus()
        assertEquals(ServiceState.Checking, chat.service.value)
        assertEquals(3, assistant.statusChecks)
        second.complete(Unit)
        assertEquals(ServiceState.Known(AssistantStatus.Available), chat.service.value)
    }

    @Test
    fun `only a send that fails as Offline or Unavailable re-checks the status`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        assistant.expectReply().complete(proposal())
        chat.send("uno")
        assistant.expectReply().complete(failed(AssistantFailure.InvalidResponse))
        chat.send("dos")
        assertEquals(0, assistant.statusChecks)

        assistant.expectReply().complete(failed(AssistantFailure.Offline))
        chat.send("tres")
        assertEquals(1, assistant.statusChecks)

        assistant.expectReply().complete(failed(AssistantFailure.Unavailable(502)))
        chat.send("cuatro")
        assertEquals(2, assistant.statusChecks)
    }
}
