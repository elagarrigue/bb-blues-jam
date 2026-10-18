package com.bbbjam.feature.assistant.jam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.assistant.AssistantReply
import com.bbbjam.core.assistant.JamRejection
import com.bbbjam.core.assistant.ProposedJam
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.setlist.jamcreate.CreateJamOutcome
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.AssistantUiModel
import com.bbbjam.feature.assistant.ChatScope
import com.bbbjam.feature.assistant.CreateCall
import com.bbbjam.feature.assistant.FakeSetlistAssistant
import com.bbbjam.feature.assistant.FakeSetlistSongAdder
import com.bbbjam.feature.assistant.JAM_DATE
import com.bbbjam.feature.assistant.JAM_DAY
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.ReplyUiModel
import com.bbbjam.feature.assistant.ScriptedJamCreator
import com.bbbjam.feature.assistant.SettableJamsRepository
import com.bbbjam.feature.assistant.TurnUiModel
import com.bbbjam.feature.assistant.apply.ApplyTone
import com.bbbjam.feature.assistant.apply.ApplyUiModel
import com.bbbjam.feature.assistant.apply.ConfirmationUiModel
import com.bbbjam.feature.assistant.chat.AssistantChat
import com.bbbjam.feature.assistant.jam
import com.bbbjam.feature.assistant.proposal
import com.bbbjam.feature.assistant.run.ApplyPlan
import com.bbbjam.feature.assistant.run.JamCreationRunner
import com.bbbjam.feature.assistant.run.RunLock
import com.bbbjam.feature.assistant.snapshot
import com.bbbjam.feature.assistant.song
import com.bbbjam.feature.assistant.testApplier
import com.bbbjam.feature.assistant.testPresenter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
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
    private val creator = ScriptedJamCreator()
    private val jams = SettableJamsRepository()
    private val applier = testApplier(adder, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    private val runner =
        JamCreationRunner(creator, jams, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), RunLock())
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
                assertEquals(UiText.res(R.string.assistant_jam_card_heading), card.heading)
                assertEquals(NOV_28_WORDS, card.date)
                assertEquals(UiText.res(R.string.assistant_jam_detail, "21:30", "La Macanuda"), card.detail)
                assertEquals(
                    UiText.res(R.string.assistant_jam_held_back_note, JAM_DAY),
                    card.note,
                )
                assertEquals(UiText.res(R.string.assistant_jam_create), card.action!!.label)
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
                assertEquals(NOV_28_WORDS, answered.turns[0].card()!!.date)
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
            assertEquals(UiText.res(R.string.assistant_jam_dialog_title), dialog.title)
            assertEquals(
                listOf(
                    UiText.res(
                        R.string.assistant_jam_dialog_date,
                        UiText.res(
                            R.string.assistant_jam_date_in_words_lower,
                            SpanishDateNames.dayLower(DayOfWeek.SATURDAY),
                            28,
                            SpanishDateNames.month(Month.NOVEMBER),
                            2026,
                        ),
                    ),
                    UiText.res(R.string.assistant_jam_dialog_time, "21:30"),
                    UiText.res(R.string.assistant_jam_dialog_venue, "La Macanuda"),
                    UiText.res(R.string.assistant_jam_draft_line),
                    UiText.res(R.string.assistant_jam_held_back_line, JAM_DAY),
                ),
                dialog.lines,
            )
            assertEquals(UiText.res(R.string.assistant_jam_create), dialog.confirmLabel)
            assertEquals(UiText.res(R.string.assistant_cancel), dialog.cancelLabel)
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
                UiText.res(R.string.assistant_jam_blocked_earlier, JAM_DAY),
                earlier.note,
            )
            cancelAndIgnoreRemainingEvents()
        }

        val taken = chat()
        taken.ask("dos", jamProposal(jam = newJam.copy(date = JAM_DATE)))
        flow(taken).test {
            val exists = awaitProposal { it.jam?.note != null }.jam!!
            assertNull(exists.action)
            assertEquals(UiText.res(R.string.assistant_jam_blocked_exists), exists.note)
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

            val done = awaitProposal { it.jam?.result?.text == UiText.res(R.string.assistant_jam_created) }.jam!!
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
            assertEquals(
                UiText.res(R.string.assistant_jam_failure, UiText.res(R.string.assistant_jam_reason_jam_exists)),
                failed.result!!.text,
            )
            assertEquals(ApplyTone.FAILURE, failed.result!!.tone)
            assertEquals(UiText.res(R.string.assistant_jam_create_retry), failed.action!!.label)
            assertEquals(0, jams.refreshes)

            creator.outcome = CreateJamOutcome.ScriptOutdated
            failed.action!!.events(JamActionUiModel.Event.Request)
            awaitProposal { it.jam?.action?.confirmation != null }.jam!!.action!!.confirmation!!.events(
                ConfirmationUiModel.Event.Confirm,
            )
            val outdatedText = UiText.res(R.string.assistant_jam_script_outdated)
            val outdated = awaitProposal { it.jam?.result?.text == outdatedText }.jam!!
            assertEquals(outdatedText, outdated.result!!.text)
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
                    UiText.res(R.string.assistant_apply_add),
                    UiText.res(R.string.assistant_replace_action),
                    UiText.res(R.string.assistant_create_action),
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
            assertEquals(UiText.res(R.string.assistant_jam_rejected_past), reply.jamNote)
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

/** "Sábado 28 de noviembre de 2026", the date of the proposed jam. */
private val NOV_28_WORDS: UiText = UiText.res(
    R.string.assistant_jam_date_in_words,
    SpanishDateNames.day(DayOfWeek.SATURDAY),
    28,
    SpanishDateNames.month(Month.NOVEMBER),
    2026,
)
