package com.bbbjam.feature.assistant.apply

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.AssistantUiModel
import com.bbbjam.feature.assistant.CallLog
import com.bbbjam.feature.assistant.ChatScope
import com.bbbjam.feature.assistant.FakeSetlistAssistant
import com.bbbjam.feature.assistant.FakeSetlistSongAdder
import com.bbbjam.feature.assistant.FakeSetlistSongMover
import com.bbbjam.feature.assistant.FakeSetlistSongRemover
import com.bbbjam.feature.assistant.JAM_DAY
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.ReplyUiModel
import com.bbbjam.feature.assistant.SettableJamsRepository
import com.bbbjam.feature.assistant.chat.AssistantChat
import com.bbbjam.feature.assistant.jam
import com.bbbjam.feature.assistant.jamSong
import com.bbbjam.feature.assistant.listed
import com.bbbjam.feature.assistant.listedOrder
import com.bbbjam.feature.assistant.proposal
import com.bbbjam.feature.assistant.run.ApplyMode
import com.bbbjam.feature.assistant.snapshot
import com.bbbjam.feature.assistant.testApplier
import com.bbbjam.feature.assistant.testPresenter
import com.bbbjam.feature.assistant.themesOf
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

/** Agregar and Reemplazar in the chat (`assistant-proposal-targets`, Part B): dialog, confirm, run, summary. */
class ReplacePresenterTest {
    private val assistant = FakeSetlistAssistant()
    private val log: CallLog = mutableListOf()
    private val adder = FakeSetlistSongAdder(log)
    private val remover = FakeSetlistSongRemover(log)
    private val mover = FakeSetlistSongMover(log)
    private val jams = SettableJamsRepository()
    private val applier = testApplier(
        adder,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        remover,
        mover,
    )

    // The list holds s2 (with two musicians), s9 and s10; the proposal is s1 to s4.
    private fun listWithMusicians() = Setlist.Available(
        listOf(
            jamSong(2).copy(position = 1),
            jamSong(9).copy(
                position = 2,
                lineup = Lineup(listOf(Slot(Instrument.GUITAR, "Ana"), Slot(Instrument.BASS, "Beto"))),
            ),
            jamSong(10).copy(position = 3, key = Key("Em")),
        ),
    )

    private fun TestScope.chat() = AssistantChat(assistant, ChatScope(testScheduler).scope)

    private fun flow(chat: AssistantChat) =
        moleculeFlow(RecompositionMode.Immediate) { testPresenter(chat, applier, jams).present(Unit) }

    private fun AssistantChat.ask(reply: com.bbbjam.core.assistant.AssistantReply) {
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

    private fun ReplyUiModel.Proposal.replace() = targets().actions.first { it.target == ApplyMode.REPLACE }

    private fun ReplyUiModel.Proposal.add() = targets().actions.first { it.target == ApplyMode.ADD }

    @Test
    fun `the targets block offers Agregar, Reemplazar and Crear, all enabled`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listWithMusicians()))
        val chat = chat()
        chat.ask(proposal(songs = 4))
        flow(chat).test {
            val targets = awaitProposal { it.apply is ApplyUiModel.Targets }.targets()
            assertEquals(
                listOf(
                    UiText.res(R.string.assistant_apply_add),
                    UiText.res(R.string.assistant_replace_action),
                    UiText.res(R.string.assistant_create_action),
                ),
                targets.actions.map { it.label },
            )
            assertEquals(listOf(ApplyMode.ADD, ApplyMode.REPLACE, ApplyMode.CREATE), targets.actions.map { it.target })
            assertTrue(targets.actions.all { it.enabled })
            assertTrue(targets.notes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the replace dialog names the date, the removed titles, the lost musicians, the kept and the added`() =
        runTest {
            jams.snapshot = snapshot(jam(setlist = listWithMusicians()))
            val chat = chat()
            chat.ask(proposal(songs = 4))
            flow(chat).test {
                awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
                val dialog = awaitProposal { it.targets().confirmation != null }.targets().confirmation!!

                assertEquals(UiText.res(R.string.assistant_replace_dialog_title), dialog.title)
                assertEquals(
                    listOf(
                        UiText.plural(R.plurals.assistant_replace_intro, 4, 4, JAM_DAY),
                        UiText.plural(R.plurals.assistant_replace_removed_titles, 2, 2, "Tema 9, Tema 10"),
                        UiText.plural(R.plurals.assistant_replace_lost, 2),
                        UiText.plural(R.plurals.assistant_replace_kept, 1),
                        UiText.plural(R.plurals.assistant_replace_added, 3),
                    ),
                    dialog.lines,
                )
                assertEquals(UiText.res(R.string.assistant_replace_confirm), dialog.confirmLabel)
                assertEquals(UiText.res(R.string.assistant_cancel), dialog.cancelLabel)
                assertTrue(dialog.destructive)
                assertTrue(log.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an empty list says so and the dialog is not destructive`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listed()))
        val chat = chat()
        chat.ask(proposal(songs = 2))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
            val dialog = awaitProposal { it.targets().confirmation != null }.targets().confirmation!!

            assertEquals(UiText.res(R.string.assistant_replace_empty, themesOf(2)), dialog.lines.last())
            assertFalse(dialog.destructive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling the replace dialog writes nothing`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listWithMusicians()))
        val chat = chat()
        chat.ask(proposal(songs = 4))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
            val dialog = awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
            dialog.events(ConfirmationUiModel.Event.Cancel)

            assertNull(awaitProposal { it.targets().confirmation == null }.targets().confirmation)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(log.isEmpty())
    }

    @Test
    fun `confirming runs adds, removes and moves in that order, and the success needs the matching list`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listWithMusicians()))
        val chat = chat()
        chat.ask(proposal(songs = 4))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
            val dialog = awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
            dialog.events(ConfirmationUiModel.Event.Confirm)
            dialog.events(ConfirmationUiModel.Event.Confirm)

            // The jams snapshot still shows the old list: the run finished but the check fails (R3).
            val mismatchText = UiText.res(R.string.assistant_replace_mismatch)
            val mismatch = awaitProposal { it.summary?.text == mismatchText }
            assertEquals(mismatchText, mismatch.summary!!.text)
            assertEquals(ApplyTone.FAILURE, mismatch.summary!!.tone)
            assertEquals(List(2) { UiText.res(R.string.assistant_replace_removed) }, mismatch.removals.map { it.text })
            assertEquals(listOf("Tema 9", "Tema 10"), mismatch.removals.map { it.title })

            jams.snapshot = snapshot(jam(setlist = listedOrder(1, 2, 3, 4)))
            val successText = UiText.res(R.string.assistant_replace_success, JAM_DAY)
            val done = awaitProposal { it.summary?.text == successText }
            assertEquals(successText, done.summary!!.text)
            assertEquals(ApplyTone.SUCCESS, done.summary!!.tone)
            assertEquals(ApplyUiModel.None, done.apply)
            cancelAndIgnoreRemainingEvents()
        }
        // Confirmed twice, replaced once: three adds, two removes, then only the needed moves.
        assertEquals(3, adder.calls.size)
        assertEquals(2, remover.calls.size)
        assertEquals(listOf("add s1", "add s3", "add s4", "remove s9", "remove s10", "move s1@1"), log)
        assertEquals(1, mover.calls.size)
        assertFalse(adder.calls.any { it.songId == SongId("s2") })
    }

    @Test
    fun `a failed add cuts the replace before any removal and the summary says so`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listWithMusicians()))
        adder.reject(3, WriteOutcome.Offline)
        val chat = chat()
        chat.ask(proposal(songs = 4))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
            awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
                .events(ConfirmationUiModel.Event.Confirm)

            val cutText = UiText.res(
                R.string.assistant_replace_cut_in_adds,
                1,
                3,
                UiText.res(R.string.assistant_apply_write_offline),
            )
            val cut = awaitProposal { it.summary?.text == cutText }
            assertEquals(cutText, cut.summary!!.text)
            assertEquals(
                List(2) { UiText.res(R.string.assistant_replace_removal_not_attempted) },
                cut.removals.map { it.text },
            )
            // Not a success: the actions are back, and the replace one offers the retry.
            assertEquals(UiText.res(R.string.assistant_replace_retry), cut.replace().label)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(remover.calls.isEmpty())
    }

    @Test
    fun `a move refused as unordered_setlist gets its own line`() = runTest {
        jams.snapshot = snapshot(jam(setlist = Setlist.Available(listOf(jamSong(2).copy(position = 1)))))
        mover.reject(1, WriteOutcome.Rejected("unordered_setlist"))
        val chat = chat()
        chat.ask(proposal(songs = 2))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
            awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
                .events(ConfirmationUiModel.Event.Confirm)

            val orderedText = UiText.res(
                R.string.assistant_replace_not_ordered,
                UiText.res(R.string.assistant_replace_unordered),
            )
            val ordered = awaitProposal { it.summary?.text == orderedText }
            assertEquals(orderedText, ordered.summary!!.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a list equal to the proposal disables Reemplazar with its note`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listedOrder(1, 2)))
        val chat = chat()
        chat.ask(proposal(songs = 2))
        flow(chat).test {
            val targets = awaitProposal { it.apply is ApplyUiModel.Targets }.targets()
            assertFalse(targets.actions.first { it.target == ApplyMode.REPLACE }.enabled)
            assertEquals(listOf(UiText.res(R.string.assistant_replace_note_equal)), targets.notes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Agregar still adds without removing or moving`() = runTest {
        jams.snapshot = snapshot(jam(JamStatus.DRAFT, Setlist.Available(listOf(jamSong(9).copy(position = 1)))))
        val chat = chat()
        chat.ask(proposal(songs = 2))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.add().events(TargetActionUiModel.Event.Request)
            awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
                .events(ConfirmationUiModel.Event.Confirm)
            val addedText = UiText.plural(R.plurals.assistant_apply_success, 2, 2, JAM_DAY)
            awaitProposal { it.summary?.text == addedText }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("add s1", "add s2"), log)
    }
}
