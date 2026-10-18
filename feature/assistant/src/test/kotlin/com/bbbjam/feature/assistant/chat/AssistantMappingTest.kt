package com.bbbjam.feature.assistant.chat

import com.bbbjam.core.assistant.AssistantFailure
import com.bbbjam.core.assistant.AssistantReply
import com.bbbjam.core.assistant.AssistantStatus
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.AssistantUiModel
import com.bbbjam.feature.assistant.ProposedSongUiModel
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.ReplyUiModel
import com.bbbjam.feature.assistant.RetryUiModel
import com.bbbjam.feature.assistant.StatusUiModel
import com.bbbjam.feature.assistant.apply.ApplyInputs
import com.bbbjam.feature.assistant.apply.ApplyUiModel
import com.bbbjam.feature.assistant.apply.applyConfirmText
import com.bbbjam.feature.assistant.apply.applyPartialText
import com.bbbjam.feature.assistant.apply.applySkippedText
import com.bbbjam.feature.assistant.apply.applyStoppedText
import com.bbbjam.feature.assistant.apply.applySuccessText
import com.bbbjam.feature.assistant.failed
import com.bbbjam.feature.assistant.proposal
import com.bbbjam.feature.assistant.snapshot
import com.bbbjam.feature.assistant.testHandlers
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure mapping, with the expected resources named from the approved table. */
class AssistantMappingTest {
    private val handlers = testHandlers()

    private val available = ServiceState.Known(AssistantStatus.Available)

    private fun chat(turns: List<ChatTurn> = emptyList(), draft: String = "", service: ServiceState = available) =
        assistantModel(true, turns, service, draft, handlers) as AssistantUiModel.Chat

    private fun answered(id: Long, reply: AssistantReply) = ChatTurn(id, "pedido $id", ChatTurn.State.Answered(reply))

    private fun replyOf(reply: AssistantReply) = chat(listOf(answered(1, reply))).turns.single().reply

    @Test
    fun `not configured shows only the title and the block`() {
        val model = assistantModel(false, listOf(answered(1, proposal())), available, "hola", handlers)
        assertEquals(
            AssistantUiModel.NotConfigured(
                UiText.res(CoreR.string.tab_assistant),
                EmptyStateUiModel(
                    UiText.res(R.string.assistant_not_configured_title),
                    UiText.res(R.string.assistant_not_configured_message),
                ),
            ),
            model,
        )
    }

    @Test
    fun `the empty chat has the intro and a disabled Enviar`() {
        val model = chat()
        assertEquals(UiText.res(CoreR.string.tab_assistant), model.title)
        assertEquals(
            EmptyStateUiModel(
                UiText.res(R.string.assistant_intro_title),
                UiText.res(R.string.assistant_intro_message),
            ),
            model.intro,
        )
        assertTrue(model.turns.isEmpty())
        assertEquals(UiText.res(R.string.assistant_composer_label), model.composer.label)
        assertEquals(UiText.res(R.string.assistant_composer_placeholder), model.composer.placeholder)
        assertEquals(UiText.res(R.string.assistant_send), model.composer.sendLabel)
        assertFalse(model.composer.sendEnabled)
    }

    @Test
    fun `the intro is gone as soon as there is a turn`() {
        assertNull(chat(listOf(ChatTurn(1, "uno", ChatTurn.State.Pending))).intro)
    }

    @Test
    fun `Enviar is enabled only with non-blank text and no pending turn`() {
        assertTrue(chat(draft = "hola").composer.sendEnabled)
        assertFalse(chat(draft = "   ").composer.sendEnabled)
        val pending = chat(listOf(ChatTurn(1, "uno", ChatTurn.State.Pending)), draft = "hola").composer
        assertFalse(pending.sendEnabled)
        assertEquals(UiText.res(R.string.assistant_send_waiting), pending.sendLabel)
        assertEquals("hola", pending.text)
        val done = chat(listOf(answered(1, proposal())), draft = "hola").composer
        assertTrue(done.sendEnabled)
        assertEquals(UiText.res(R.string.assistant_send), done.sendLabel)
    }

    @Test
    fun `a pending turn shows the wait copy and a Cancelar keyed by its id`() {
        val turn = chat(listOf(ChatTurn(7, "uno", ChatTurn.State.Pending))).turns.single()
        assertEquals(7, turn.id)
        assertEquals(UiText.res(R.string.assistant_author_you), turn.authorLabel)
        assertEquals("uno", turn.request)
        assertEquals(
            ReplyUiModel.Pending(
                UiText.res(R.string.assistant_pending_label),
                null,
                emptyList(),
                UiText.res(R.string.assistant_cancel),
                EventHandler(7L) {
                },
            ),
            turn.reply,
        )
    }

    @Test
    fun `a proposal lists numbered songs, the message, questions and no ids`() {
        val reply = replyOf(proposal(songs = 2, message = "Cerramos arriba.", questions = listOf("¿Con armónica?")))
        assertEquals(
            ReplyUiModel.Proposal(
                authorLabel = UiText.res(CoreR.string.tab_assistant),
                heading = UiText.plural(R.plurals.assistant_proposal_heading, 2),
                songs = listOf(
                    ProposedSongUiModel(
                        "01",
                        "Tema 1",
                        "Artista 1",
                        "Bm",
                        SongListText.keyDescription("Bm"),
                        "Razón 1",
                    ),
                    ProposedSongUiModel(
                        "02",
                        "Tema 2",
                        "Artista 2",
                        "Bm",
                        SongListText.keyDescription("Bm"),
                        "Razón 2",
                    ),
                ),
                message = "Cerramos arriba.",
                questionsHeading = UiText.res(R.string.assistant_questions_heading),
                questions = listOf("¿Con armónica?"),
                discardedNote = null,
            ),
            reply,
        )
    }

    @Test
    fun `one song says tema and no questions means no heading`() {
        val reply = replyOf(proposal(songs = 1)) as ReplyUiModel.Proposal
        assertEquals(UiText.plural(R.plurals.assistant_proposal_heading, 1), reply.heading)
        assertNull(reply.questionsHeading)
        assertTrue(reply.questions.isEmpty())
    }

    @Test
    fun `zero songs has no heading and no rows, just the message and questions`() {
        val reply = replyOf(proposal(songs = 0, message = "¿Cuántos temas?", questions = listOf("¿Cuántos?"))) as
            ReplyUiModel.Proposal
        assertNull(reply.heading)
        assertTrue(reply.songs.isEmpty())
        assertEquals("¿Cuántos temas?", reply.message)
        assertEquals(UiText.res(R.string.assistant_questions_heading), reply.questionsHeading)
    }

    @Test
    fun `the discarded note counts rejected ids in the singular and plural and never shows them`() {
        val none = replyOf(proposal(rejected = emptyList())) as ReplyUiModel.Proposal
        assertNull(none.discardedNote)
        val one = replyOf(proposal(rejected = listOf("zz-secreto"))) as ReplyUiModel.Proposal
        assertEquals(UiText.plural(R.plurals.assistant_discarded, 1), one.discardedNote)
        val two = replyOf(proposal(rejected = listOf("a-1", "b-2"))) as ReplyUiModel.Proposal
        assertEquals(UiText.plural(R.plurals.assistant_discarded, 2), two.discardedNote)
        assertFalse(two.toString().contains("a-1"))
    }

    @Test
    fun `every failure has its Spanish copy and the status is never shown`() {
        val expected = mapOf(
            AssistantFailure.Offline to UiText.res(R.string.assistant_offline),
            AssistantFailure.Unauthorized to UiText.res(R.string.assistant_unauthorized),
            AssistantFailure.Unavailable(503) to
                UiText.res(R.string.assistant_unavailable),
            AssistantFailure.InvalidResponse to UiText.res(R.string.assistant_invalid_response),
            AssistantFailure.TooManySteps to
                UiText.res(R.string.assistant_too_many_steps),
            AssistantFailure.CatalogEmpty to
                UiText.res(R.string.assistant_catalog_empty),
            AssistantFailure.Incomplete to UiText.res(R.string.assistant_incomplete_retry),
        )
        expected.forEach { (failure, text) ->
            val reply = replyOf(failed(failure)) as ReplyUiModel.Failed
            assertEquals(text, reply.message)
            assertEquals(UiText.res(R.string.assistant_retry), reply.retry?.label)
        }
        assertFalse(
            (
                replyOf(
                    failed(AssistantFailure.Unavailable(503)),
                ) as ReplyUiModel.Failed
                ).message.toString().contains("503"),
        )
        // The sealed type has exactly these seven members.
        assertEquals(7, expected.size)
    }

    @Test
    fun `cancelled and interrupted turns have their copy and a retry when last`() {
        val cancelled = chat(listOf(ChatTurn(1, "a", ChatTurn.State.Cancelled))).turns.single().reply
        assertEquals(
            ReplyUiModel.Cancelled(
                UiText.res(R.string.assistant_cancelled),
                RetryUiModel(
                    UiText.res(R.string.assistant_retry),
                    EventHandler(1L) {
                    },
                ),
            ),
            cancelled,
        )
        val interrupted = chat(listOf(ChatTurn(1, "a", ChatTurn.State.Interrupted))).turns.single().reply
        assertEquals(
            ReplyUiModel.Failed(
                UiText.res(R.string.assistant_interrupted),
                RetryUiModel(UiText.res(R.string.assistant_retry), EventHandler(1L) {}),
            ),
            interrupted,
        )
    }

    @Test
    fun `a NotConfigured reply maps to the not-configured message without retry`() {
        val reply = replyOf(AssistantReply.NotConfigured) as ReplyUiModel.Failed
        assertEquals(UiText.res(R.string.assistant_not_configured_message), reply.message)
        assertNull(reply.retry)
    }

    @Test
    fun `retry is offered on the last turn only`() {
        val turns = listOf(
            ChatTurn(1, "a", ChatTurn.State.Cancelled),
            answered(2, failed(AssistantFailure.Offline)),
            ChatTurn(3, "c", ChatTurn.State.Interrupted),
        )
        val replies = chat(turns).turns.map { it.reply }
        assertNull((replies[0] as ReplyUiModel.Cancelled).retry)
        assertNull((replies[1] as ReplyUiModel.Failed).retry)
        assertEquals(UiText.res(R.string.assistant_retry), (replies[2] as ReplyUiModel.Failed).retry?.label)
    }

    @Test
    fun `no banner when the service is Available or the build is not configured for it`() {
        assertEquals(StatusUiModel.None, chat().status)
        assertEquals(StatusUiModel.None, chat(service = ServiceState.Known(AssistantStatus.NotConfigured)).status)
    }

    @Test
    fun `checking shows a static line and disables Enviar`() {
        val model = chat(draft = "hola", service = ServiceState.Checking)
        assertEquals(StatusUiModel.Checking(UiText.res(R.string.assistant_checking)), model.status)
        assertFalse(model.composer.sendEnabled)
        assertEquals(UiText.res(R.string.assistant_send), model.composer.sendLabel)
    }

    @Test
    fun `each problem has its banner copy, Reintentar, and a disabled Enviar`() {
        val expected = mapOf(
            AssistantStatus.ServiceDown to
                UiText.res(R.string.assistant_service_down),
            AssistantStatus.Offline to UiText.res(R.string.assistant_offline),
            AssistantStatus.Unauthorized to UiText.res(R.string.assistant_unauthorized),
        )
        expected.forEach { (status, text) ->
            val model = chat(draft = "hola", service = ServiceState.Known(status))
            assertEquals(
                StatusUiModel.Banner(text, UiText.res(R.string.assistant_retry), EventHandler {}),
                model.status,
            )
            assertFalse("$status", model.composer.sendEnabled)
        }
    }

    @Test
    fun `a turn's Reintentar is withheld while the service is not available`() {
        val turns = listOf(answered(1, failed(AssistantFailure.Offline)))
        assertEquals(
            UiText.res(R.string.assistant_retry),
            ((chat(turns).turns.single().reply) as ReplyUiModel.Failed).retry?.label,
        )
        val down = chat(turns, service = ServiceState.Known(AssistantStatus.ServiceDown))
        assertNull((down.turns.single().reply as ReplyUiModel.Failed).retry)
        val checking = chat(turns, service = ServiceState.Checking)
        assertNull((checking.turns.single().reply as ReplyUiModel.Failed).retry)
    }

    @Test
    fun `the apply texts pick the singular and the plural`() {
        val date = LocalDate.of(2026, 10, 31)
        val day = SpanishDateNames.sentence(date)
        fun themes(count: Int) = UiText.plural(R.plurals.assistant_apply_themes, count)
        fun alreadyThere(count: Int) = UiText.plural(R.plurals.assistant_apply_already_there, count)
        fun failedCount(count: Int) = UiText.plural(R.plurals.assistant_apply_failed_count, count)
        assertEquals(
            UiText.res(
                R.string.assistant_apply_success_with_skipped,
                UiText.plural(R.plurals.assistant_apply_success, 11, 11, day),
                alreadyThere(1),
            ),
            applySuccessText(11, 1, date),
        )
        assertEquals(UiText.plural(R.plurals.assistant_apply_success, 1, 1, day), applySuccessText(1, 0, date))
        assertEquals(
            UiText.res(
                R.string.assistant_apply_success_with_skipped,
                UiText.res(R.string.assistant_apply_success_none, day),
                alreadyThere(2),
            ),
            applySuccessText(0, 2, date),
        )
        assertEquals(
            UiText.res(R.string.assistant_apply_partial, 10, themes(11), failedCount(1)),
            applyPartialText(10, 11, 1),
        )
        assertEquals(
            UiText.res(R.string.assistant_apply_partial, 8, themes(11), failedCount(3)),
            applyPartialText(8, 11, 3),
        )
        assertEquals(UiText.res(R.string.assistant_apply_stopped, 4, themes(11)), applyStoppedText(4, 11))
        assertEquals(UiText.res(R.string.assistant_apply_add_confirm, themes(1)), applyConfirmText(1))
        assertEquals(
            UiText.plural(R.plurals.assistant_apply_add_skipped, 3, 3, "A, B, C"),
            applySkippedText(listOf("A", "B", "C")),
        )
    }

    @Test
    fun `a success shows no action and no pending run draws the summary only`() {
        val turns = listOf(answered(1, proposal(songs = 2)))
        val ready = ApplyInputs(snapshot = snapshot())
        val action = (chat(turns).turns.single().reply as ReplyUiModel.Proposal).apply
        assertEquals(ApplyUiModel.None, action)
        val withSnapshot = assistantModel(true, turns, available, "", handlers, ready) as AssistantUiModel.Chat
        assertTrue((withSnapshot.turns.single().reply as ReplyUiModel.Proposal).apply is ApplyUiModel.Targets)
    }
}
