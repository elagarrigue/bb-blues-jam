package com.bbbjam.core.assistant

import ai.koog.agents.core.agent.AIAgentFunctionalStrategy
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.IncompleteStreamException
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponse
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.json.JsonStructure
import com.bbbjam.core.model.Song
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The model kept calling tools past [AssistantModel.MAX_AGENT_ITERATIONS] requests. */
internal class TooManyRoundsException : IllegalStateException("The assistant exceeded its request budget")

/**
 * Turns the parser's raw song objects into catalog-validated drafts (D-14). The accumulator is the one the
 * final validation uses, so a streamed song and its final counterpart cannot differ. Calls [onDraft] with
 * the cumulative accepted list whenever it grew, and with an empty list when a tool round discards it.
 */
internal class DraftTracker(private val catalog: List<Song>, private val onDraft: (AssistantDraft) -> Unit) {
    private val parser = ProposalStreamParser()
    private var accumulator = ProposalValidator.Accumulator(catalog)
    private val accepted = ArrayList<ProposedSong>()

    /** Feeds one text delta; emits a draft when a song object closed and passed the catalog. */
    fun feed(text: String) {
        val songs = parser.feed(text).mapNotNull(::decode).mapNotNull { accumulator.offer(it) }
        if (songs.isEmpty()) return
        accepted += songs
        onDraft(AssistantDraft(accepted.toList()))
    }

    /** The round ended in tool calls: whatever text it streamed is not the answer. */
    fun discard() {
        parser.reset()
        accumulator = ProposalValidator.Accumulator(catalog)
        if (accepted.isNotEmpty()) {
            accepted.clear()
            onDraft(AssistantDraft(emptyList()))
        }
    }

    private fun decode(raw: String): ProposedSongDto? = try {
        LENIENT.decodeFromString(ProposedSongDto.serializer(), raw)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        val LENIENT = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The agent loop of the assistant, streaming (`assistant-streaming`): at most
 * [AssistantModel.MAX_AGENT_ITERATIONS] requests, each streamed with the tools. A round that ends in tool
 * calls runs them and goes on; the round that ends in text is parsed whole into the [ProposalDto], which
 * replaces whatever [onDraft] showed. Manual structured output, as before: the schema travels in the
 * prompt, never in `response_format`.
 */
internal fun streamingProposalStrategy(
    catalog: List<Song>,
    onDraft: (AssistantDraft) -> Unit,
): AIAgentFunctionalStrategy<String, ProposalDto> {
    val config = StructuredRequestConfig(default = StructuredRequest.Manual(JsonStructure.create<ProposalDto>()))
    return functionalStrategy("streaming_proposal") { input ->
        val tracker = DraftTracker(catalog, onDraft)
        llm.writeSession {
            prompt = config.updatePrompt(model, prompt)
            appendPrompt { user(input) }
        }
        var result: ProposalDto? = null
        var rounds = 0
        while (result == null) {
            if (rounds++ >= AssistantModel.MAX_AGENT_ITERATIONS) throw TooManyRoundsException()
            val frames = ArrayList<StreamFrame>()
            llm.writeSession {
                requestLLMStreaming().collect { frame ->
                    currentCoroutineContext().ensureActive()
                    frames += frame
                    if (frame is StreamFrame.TextDelta) tracker.feed(frame.text)
                }
            }
            // Koog's client closes a stream that just stops (a clean close, a dropped body) with an end frame
            // of its own and no finish reason (seen in StreamingAssistantTest): that is not an answer.
            if (frames.none { it is StreamFrame.End && it.finishReason != null }) throw IncompleteStreamException()
            val message = frames.toMessageResponse().withOneTextAndNoReasoning()
            llm.writeSession { appendPrompt { message(message) } }
            val calls = getToolCalls(message)
            if (calls.isEmpty()) {
                result = llm.writeSession { parseResponseToStructuredResponse(message, config, null).data }
            } else {
                tracker.discard()
                val results = executeTools(calls)
                llm.writeSession {
                    appendPrompt { user { results.forEach { toolResult(it.toMessagePart()) } } }
                }
            }
        }
        result
    }
}

/**
 * Shapes the streamed message for the parse and for the history. The gateway streams an empty `content`
 * with its reasoning, which Koog turns into a text part of its own, and the structured parse wants exactly
 * one: the text parts are joined into one, in place of the first. The reasoning parts are dropped: the
 * model's thoughts are not sent back to the gateway (the non-streaming requests never did either).
 */
private fun Message.Assistant.withOneTextAndNoReasoning(): Message.Assistant {
    val joined = MessagePart.Text(parts.filterIsInstance<MessagePart.Text>().joinToString("") { it.text })
    var placed = false
    val shaped = ArrayList<MessagePart.ResponsePart>()
    for (part in parts) {
        when {
            part is MessagePart.Reasoning -> Unit

            part is MessagePart.Text && placed -> Unit

            part is MessagePart.Text -> {
                shaped += joined
                placed = true
            }

            else -> shaped += part
        }
    }
    return copy(parts = shaped)
}
