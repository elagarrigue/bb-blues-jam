package com.bbbjam.feature.assistant

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.assistant.AssistantFailure
import com.bbbjam.core.assistant.AssistantStatus
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.chat.AssistantChat
import com.bbbjam.feature.assistant.chat.ChatTurn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPresenterTest {
    private val assistant = FakeSetlistAssistant()

    private fun flow(chat: AssistantChat) = moleculeFlow(RecompositionMode.Immediate) {
        testPresenter(
            chat,
            testApplier(
                FakeSetlistSongAdder(),
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            ),
            SettableJamsRepository(),
        ).present(Unit)
    }

    private fun AssistantUiModel.asChat() = this as AssistantUiModel.Chat

    /**
     * The next model satisfying [predicate]. A handler that changes the chat and the draft in two
     * steps may emit a model in between, so a test waits for the settled one instead of counting.
     */
    private suspend fun ReceiveTurbine<AssistantUiModel>.awaitChat(
        predicate: (AssistantUiModel.Chat) -> Boolean,
    ): AssistantUiModel.Chat {
        while (true) {
            val model = awaitItem().asChat()
            if (predicate(model)) return model
        }
    }

    private suspend fun ReceiveTurbine<AssistantUiModel>.awaitReady() = awaitChat { it.status == StatusUiModel.None }

    @Test
    fun `an unconfigured assistant shows the not-configured model`() = runTest {
        val chat = AssistantChat(FakeSetlistAssistant(isConfigured = false), ChatScope(testScheduler).scope)
        flow(chat).test {
            assertTrue(awaitItem() is AssistantUiModel.NotConfigured)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing then Send goes empty, pending, proposal and clears the draft`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        val reply = assistant.expectReply()
        flow(chat).test {
            val first = awaitReady()
            assertTrue(first.turns.isEmpty())
            assertFalse(first.composer.sendEnabled)

            first.composer.events(ComposerUiModel.Event.TextChanged("  doce temas "))
            val typed = awaitItem().asChat()
            assertEquals("  doce temas ", typed.composer.text)
            assertTrue(typed.composer.sendEnabled)

            typed.composer.events(ComposerUiModel.Event.Send)
            val pending = awaitChat { it.turns.isNotEmpty() && it.composer.text.isEmpty() }
            assertEquals("", pending.composer.text)
            assertEquals("doce temas", pending.turns.single().request)
            assertTrue(pending.turns.single().reply is ReplyUiModel.Pending)
            assertFalse(pending.composer.sendEnabled)
            assertEquals(UiText.res(R.string.assistant_send_waiting), pending.composer.sendLabel)

            reply.complete(proposal(songs = 2))
            val answered = awaitChat { it.turns.singleOrNull()?.reply is ReplyUiModel.Proposal }
            val proposalModel = answered.turns.single().reply as ReplyUiModel.Proposal
            assertEquals(UiText.plural(R.plurals.assistant_proposal_heading, 2), proposalModel.heading)
            assertEquals(UiText.res(R.string.assistant_send), answered.composer.sendLabel)
        }
        assertEquals(listOf("doce temas"), assistant.requests)
    }

    @Test
    fun `a double Send sends once`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        assistant.expectReply()
        flow(chat).test {
            val first = awaitReady()
            first.composer.events(ComposerUiModel.Event.TextChanged("uno"))
            val typed = awaitItem().asChat()
            typed.composer.events(ComposerUiModel.Event.Send)
            typed.composer.events(ComposerUiModel.Event.Send)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("uno"), assistant.requests)
        assertEquals(1, chat.turns.value.size)
    }

    @Test
    fun `Cancelar reaches the chat and Reintentar sends the same request again`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        assistant.expectReply()
        flow(chat).test {
            val first = awaitReady()
            first.composer.events(ComposerUiModel.Event.TextChanged("uno"))
            awaitItem().asChat().composer.events(ComposerUiModel.Event.Send)
            val pending = awaitChat { it.turns.singleOrNull()?.reply is ReplyUiModel.Pending }
            (pending.turns.single().reply as ReplyUiModel.Pending).events(ReplyUiModel.Pending.Event.Cancel)

            val cancelled = awaitChat { it.turns.singleOrNull()?.reply is ReplyUiModel.Cancelled }
            val reply = cancelled.turns.single().reply as ReplyUiModel.Cancelled
            assertEquals(UiText.res(R.string.assistant_cancelled), reply.message)
            assertTrue(assistant.sawCancellation)

            val next = assistant.expectReply()
            reply.retry!!.events(RetryUiModel.Event.Retry)
            val retrying = awaitChat { it.turns.singleOrNull()?.reply is ReplyUiModel.Pending }
            assertEquals("uno", retrying.turns.single().request)
            assertTrue(retrying.turns.single().reply is ReplyUiModel.Pending)
            next.complete(proposal(songs = 1))
            awaitChat { it.turns.singleOrNull()?.reply is ReplyUiModel.Proposal }
        }
    }

    @Test
    fun `the conversation outlives the composition`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        val reply = assistant.expectReply()
        flow(chat).test {
            awaitReady().composer.events(ComposerUiModel.Event.TextChanged("uno"))
            awaitItem().asChat().composer.events(ComposerUiModel.Event.Send)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        // The tab was left (the composition is gone): the request is still pending, not cancelled.
        assertEquals(ChatTurn.State.Pending, chat.turns.value.single().state)
        assertFalse(assistant.sawCancellation)
        reply.complete(proposal())
        flow(chat).test {
            val back = awaitItem().asChat()
            assertTrue(back.turns.single().reply is ReplyUiModel.Proposal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the status is checked when the tab is shown, with a Checking line until it answers`() = runTest {
        val gate = CompletableDeferred<Unit>()
        assistant.statusGate = gate
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        flow(chat).test {
            val checking = awaitChat { it.status is StatusUiModel.Checking }
            assertEquals(StatusUiModel.Checking(UiText.res(R.string.assistant_checking)), checking.status)
            assertFalse(checking.composer.sendEnabled)
            gate.complete(Unit)
            val ready = awaitChat { it.status == StatusUiModel.None }
            assertEquals(UiText.res(R.string.assistant_send), ready.composer.sendLabel)
        }
        assertEquals(1, assistant.statusChecks)
    }

    @Test
    fun `ServiceDown shows the banner, ignores Send, and Reintentar re-checks until it is available`() = runTest {
        assistant.status = AssistantStatus.ServiceDown
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        flow(chat).test {
            val down = awaitChat { it.status is StatusUiModel.Banner }
            val banner = down.status as StatusUiModel.Banner
            assertEquals(
                UiText.res(R.string.assistant_service_down),
                banner.message,
            )
            assertFalse(down.composer.sendEnabled)
            down.composer.events(ComposerUiModel.Event.TextChanged("uno"))
            val typed = awaitChat { it.composer.text == "uno" }
            assertFalse(typed.composer.sendEnabled)
            typed.composer.events(ComposerUiModel.Event.Send)
            expectNoEvents()
            assertTrue(assistant.requests.isEmpty())

            assistant.status = AssistantStatus.Available
            banner.events(StatusUiModel.Banner.Event.Retry)
            val back = awaitChat { it.status == StatusUiModel.None }
            assertTrue(back.composer.sendEnabled)
        }
        assertEquals(2, assistant.statusChecks)
    }

    @Test
    fun `Offline and Unauthorized show their copy`() = runTest {
        listOf(
            AssistantStatus.Offline to UiText.res(R.string.assistant_offline),
            AssistantStatus.Unauthorized to UiText.res(R.string.assistant_unauthorized),
        ).forEach { (status, text) ->
            val fake = FakeSetlistAssistant().also { it.status = status }
            val chat = AssistantChat(fake, ChatScope(testScheduler).scope)
            flow(chat).test {
                val model = awaitChat { it.status is StatusUiModel.Banner }
                assertEquals(text, (model.status as StatusUiModel.Banner).message)
                assertFalse(model.composer.sendEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `a send that fails as Unavailable re-checks and the banner appears`() = runTest {
        val chat = AssistantChat(assistant, ChatScope(testScheduler).scope)
        val reply = assistant.expectReply()
        flow(chat).test {
            awaitReady().composer.events(ComposerUiModel.Event.TextChanged("uno"))
            awaitChat { it.composer.text == "uno" }.composer.events(ComposerUiModel.Event.Send)
            awaitChat { it.turns.singleOrNull()?.reply is ReplyUiModel.Pending }
            assistant.status = AssistantStatus.ServiceDown
            reply.complete(failed(AssistantFailure.Unavailable(502)))
            val model = awaitChat { it.status is StatusUiModel.Banner }
            assertFalse(model.composer.sendEnabled)
            val failedReply = model.turns.single().reply as ReplyUiModel.Failed
            assertEquals(UiText.res(R.string.assistant_unavailable), failedReply.message)
            assertEquals(null, failedReply.retry)
        }
        assertEquals(2, assistant.statusChecks)
    }
}
