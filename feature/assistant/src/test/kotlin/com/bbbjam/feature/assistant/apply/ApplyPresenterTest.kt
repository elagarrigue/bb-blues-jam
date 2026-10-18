package com.bbbjam.feature.assistant.apply

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.AssistantUiModel
import com.bbbjam.feature.assistant.ChatScope
import com.bbbjam.feature.assistant.FakeSetlistAssistant
import com.bbbjam.feature.assistant.FakeSetlistSongAdder
import com.bbbjam.feature.assistant.JAM_DAY
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.ReplyUiModel
import com.bbbjam.feature.assistant.SettableJamsRepository
import com.bbbjam.feature.assistant.TurnUiModel
import com.bbbjam.feature.assistant.chat.AssistantChat
import com.bbbjam.feature.assistant.jam
import com.bbbjam.feature.assistant.listed
import com.bbbjam.feature.assistant.listedOrder
import com.bbbjam.feature.assistant.proposal
import com.bbbjam.feature.assistant.run.ApplyMode
import com.bbbjam.feature.assistant.snapshot
import com.bbbjam.feature.assistant.testApplier
import com.bbbjam.feature.assistant.testPresenter
import com.bbbjam.feature.assistant.themesOf
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
    private val jams = SettableJamsRepository()
    private val applier = testApplier(adder, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    private fun TestScope.chat() = AssistantChat(assistant, ChatScope(testScheduler).scope)

    private fun flow(chat: AssistantChat) =
        moleculeFlow(RecompositionMode.Immediate) { testPresenter(chat, applier, jams).present(Unit) }

    private fun AssistantChat.ask(request: String, reply: com.bbbjam.core.assistant.AssistantReply) {
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

    private fun ReplyUiModel.Proposal.targets() = apply as ApplyUiModel.Targets

    private fun ReplyUiModel.Proposal.add() = targets().actions.first { it.target == ApplyMode.ADD }

    private fun ReplyUiModel.Proposal.replace() = targets().actions.first { it.target == ApplyMode.REPLACE }

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
            val first = awaitProposal { it.apply is ApplyUiModel.Targets }
            assertEquals(UiText.res(R.string.assistant_apply_add), first.add().label)
            assertTrue(first.add().enabled)

            val second = assistant.expectReply()
            chat.send("dos")
            val pending = awaitChat { it.turns.size == 2 }
            assertEquals(ApplyUiModel.None, pending.turns[0].apply())

            second.complete(proposal(songs = 3))
            val answered = awaitChat { it.turns.size == 2 && it.turns[1].apply() is ApplyUiModel.Targets }
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
            val ready = awaitProposal { it.apply is ApplyUiModel.Targets }
            assertNull(ready.targets().confirmation)
            ready.add().events(TargetActionUiModel.Event.Request)

            val dialog = awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
            assertEquals(UiText.res(R.string.assistant_apply_add_dialog_title), dialog.title)
            assertEquals(
                listOf(
                    UiText.plural(R.plurals.assistant_apply_add_intro, 3, 3, JAM_DAY),
                    UiText.res(R.string.assistant_apply_add_existing, themesOf(2)),
                    UiText.plural(R.plurals.assistant_apply_add_skipped, 1, 1, "Tema 2"),
                ),
                dialog.lines,
            )
            assertEquals(UiText.res(R.string.assistant_apply_add_confirm, themesOf(3)), dialog.confirmLabel)
            assertEquals(UiText.res(R.string.assistant_cancel), dialog.cancelLabel)
            assertTrue(adder.calls.isEmpty())

            dialog.events(ConfirmationUiModel.Event.Cancel)
            assertNull(awaitProposal { it.targets().confirmation == null }.targets().confirmation)
        }
        assertTrue(adder.calls.isEmpty())
    }

    @Test
    fun `a published jam adds the warning line`() = runTest {
        jams.snapshot = snapshot(jam(JamStatus.PUBLISHED, listed(1)))
        val chat = chat()
        chat.ask("dos", proposal(songs = 2))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.add().events(TargetActionUiModel.Event.Request)
            val dialog = awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
            assertEquals(
                UiText.res(R.string.assistant_apply_add_published),
                dialog.lines.last(),
            )
            assertEquals(UiText.plural(R.plurals.assistant_apply_add_intro, 1, 1, JAM_DAY), dialog.lines.first())
            assertEquals(UiText.res(R.string.assistant_apply_add_confirm, themesOf(1)), dialog.confirmLabel)
        }
    }

    @Test
    fun `confirming twice in one frame starts one run, in order, and ends with the success summary`() = runTest {
        val chat = chat()
        chat.ask("tres", proposal(songs = 3))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.add().events(TargetActionUiModel.Event.Request)
            val dialog = awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
            dialog.events(ConfirmationUiModel.Event.Confirm)
            dialog.events(ConfirmationUiModel.Event.Confirm)

            val done = awaitProposal { it.summary != null && it.summary!!.stopLabel == null }
            assertEquals(
                UiText.plural(R.plurals.assistant_apply_success, 3, 3, JAM_DAY),
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
            awaitProposal { it.apply is ApplyUiModel.Targets }.add().events(TargetActionUiModel.Event.Request)
            awaitProposal { it.targets().confirmation != null }.targets().confirmation!!.events(
                ConfirmationUiModel.Event.Confirm,
            )

            val done = awaitProposal { it.summary?.stopLabel == null && it.summary != null }
            assertEquals(
                UiText.res(
                    R.string.assistant_apply_partial,
                    2,
                    themesOf(3),
                    UiText.plural(R.plurals.assistant_apply_failed_count, 1),
                ),
                done.summary!!.text,
            )
            assertEquals(ApplyTone.FAILURE, done.summary!!.tone)
            assertEquals(
                UiText.res(
                    R.string.assistant_apply_song_failure,
                    UiText.res(CoreR.string.write_failure_not_in_catalog),
                ),
                done.songs[1].applyStatus!!.text,
            )
            assertEquals(ApplyStatusKind.FAILED, done.songs[1].applyStatus!!.kind)
            assertEquals(UiText.res(R.string.assistant_apply_add_retry), done.add().label)
        }
    }

    @Test
    fun `an abort leaves the rest not attempted and says why`() = runTest {
        adder.reject(1, WriteOutcome.AccessRefused)
        val chat = chat()
        chat.ask("tres", proposal(songs = 3))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.add().events(TargetActionUiModel.Event.Request)
            awaitProposal { it.targets().confirmation != null }.targets().confirmation!!.events(
                ConfirmationUiModel.Event.Confirm,
            )

            val done = awaitProposal { it.summary != null && it.summary!!.stopLabel == null }
            assertEquals(
                UiText.res(
                    R.string.assistant_apply_aborted,
                    0,
                    themesOf(3),
                    UiText.res(CoreR.string.write_failure_access_refused),
                ),
                done.summary!!.text,
            )
            assertEquals(1, adder.calls.size)
            assertEquals(
                listOf(ApplyStatusKind.FAILED, ApplyStatusKind.NOT_ATTEMPTED, ApplyStatusKind.NOT_ATTEMPTED),
                done.songs.map { it.applyStatus!!.kind },
            )
            assertEquals(UiText.res(R.string.assistant_apply_status_not_attempted), done.songs[2].applyStatus!!.text)
        }
    }

    @Test
    fun `stop is offered while running and keeps the song in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        adder.gates[SongId("s2")] = gate
        val chat = chat()
        chat.ask("cuatro", proposal(songs = 4))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.add().events(TargetActionUiModel.Event.Request)
            awaitProposal { it.targets().confirmation != null }.targets().confirmation!!.events(
                ConfirmationUiModel.Event.Confirm,
            )

            val running = awaitProposal { it.summary?.stopLabel != null && it.songs[1].applyStatus != null }
            assertEquals(UiText.res(R.string.assistant_apply_stop), running.summary!!.stopLabel)
            assertEquals(UiText.res(R.string.assistant_apply_status_adding), running.songs[1].applyStatus!!.text)
            assertEquals(ApplyUiModel.None, running.apply)

            running.summary!!.events(ApplySummaryUiModel.Event.Stop)
            gate.complete(Unit)

            val done = awaitProposal { it.summary != null && it.summary!!.stopLabel == null }
            assertEquals(UiText.res(R.string.assistant_apply_stopped, 2, themesOf(4)), done.summary!!.text)
            assertEquals(ApplyTone.MUTED, done.summary!!.tone)
            assertEquals(2, adder.calls.size)
        }
    }

    @Test
    fun `blocked states draw a note and no button, and an unread snapshot draws nothing`() = runTest {
        val cases = listOf(
            snapshot(upcoming = null) to UiText.res(R.string.assistant_create_note_no_upcoming),
            snapshot(jam(JamStatus.DRAFT, com.bbbjam.core.model.Setlist.Withheld)) to
                UiText.res(R.string.assistant_apply_blocked_unreadable),
        )
        for ((snapshot, note) in cases) {
            val blockedJams = SettableJamsRepository(snapshot)
            val chat = chat()
            chat.ask("dos", proposal(songs = 2))
            moleculeFlow(RecompositionMode.Immediate) {
                testPresenter(chat, applier, blockedJams).present(Unit)
            }.test {
                val targets = awaitProposal { it.apply != ApplyUiModel.None }.targets()
                assertEquals(listOf(ApplyMode.CREATE), targets.actions.map { it.target })
                assertEquals(listOf(note), targets.notes)
            }
        }
        val equalJams = SettableJamsRepository(snapshot(jam(setlist = listedOrder(1, 2))))
        val equalChat = chat()
        equalChat.ask("dos", proposal(songs = 2))
        moleculeFlow(RecompositionMode.Immediate) {
            testPresenter(equalChat, applier, equalJams).present(Unit)
        }.test {
            val targets = awaitProposal { it.apply != ApplyUiModel.None }.targets()
            assertEquals(
                listOf(false, false),
                targets.actions.filter { it.target != ApplyMode.CREATE }.map { it.enabled },
            )
            assertEquals(listOf(UiText.res(R.string.assistant_replace_note_equal)), targets.notes)
        }
        val unread = SettableJamsRepository(null)
        val chat = chat()
        chat.ask("dos", proposal(songs = 2))
        moleculeFlow(RecompositionMode.Immediate) {
            testPresenter(chat, applier, unread).present(Unit)
        }.test {
            assertFalse(awaitProposal().apply is ApplyUiModel.Targets)
            assertEquals(ApplyUiModel.None, awaitProposal().apply)
        }
        assertTrue(adder.calls.isEmpty())
    }
}
