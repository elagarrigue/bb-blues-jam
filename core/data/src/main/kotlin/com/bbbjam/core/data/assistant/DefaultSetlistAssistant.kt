package com.bbbjam.core.data.assistant

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.exception.AIAgentException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.streaming.IncompleteStreamException
import ai.koog.prompt.structure.LLMStructuredParsingError
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.model.Song
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The [SetlistAssistant] over a Koog agent. Its only data access is [catalog] and [jams], read through
 * the three read-only tools (D-24); it holds no writer. The executor is built on the first request that
 * has a key, once. A conversation runs one agent per turn and keeps answered turns in memory.
 */
internal class DefaultSetlistAssistant(
    private val credentials: AssistantCredentials,
    private val catalog: CatalogRepository,
    private val jams: JamsRepository,
    private val calendar: JamCalendar,
    private val executorFactory: AssistantExecutorFactory,
    private val statusProbe: AssistantStatusProbe = AssistantStatusProbe(),
) : SetlistAssistant {
    private val executor: PromptExecutor by lazy { executorFactory.create(credentials.key) }

    override val isConfigured: Boolean
        get() = credentials.isConfigured

    override fun startConversation(): AssistantConversation = Conversation()

    override suspend fun checkStatus(): AssistantStatus = statusProbe.check(credentials.key)

    private inner class Conversation : AssistantConversation {
        private val history = ArrayList<AssistantPrompt.Turn>()
        private val turns = Mutex()

        override suspend fun send(request: String, onDraft: (AssistantDraft) -> Unit): AssistantReply {
            if (!credentials.isConfigured) return AssistantReply.NotConfigured
            require(request.isNotBlank()) { "The assistant needs a non-blank request" }
            return turns.withLock {
                val songs = catalog.observeCatalog().first().songs
                if (songs.isEmpty()) return@withLock AssistantReply.Failed(AssistantFailure.CatalogEmpty)
                try {
                    val dto = runAgent(request.trim(), songs, onDraft)
                    val proposal = withJam(ProposalValidator.validate(dto, songs), dto.jam)
                    history += AssistantPrompt.Turn(
                        request.trim(),
                        Json.encodeToString(ProposalDto.serializer(), ProposalValidator.toDto(proposal)),
                    )
                    proposal
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: KoogHttpClientException) {
                    failed(e)
                } catch (e: LLMClientException) {
                    failed(e)
                } catch (e: LLMStructuredParsingError) {
                    failed(e)
                } catch (e: AIAgentException) {
                    failed(e)
                } catch (e: SerializationException) {
                    failed(e)
                } catch (e: IncompleteStreamException) {
                    failed(e)
                } catch (e: TooManyRoundsException) {
                    failed(e)
                } catch (e: IOException) {
                    failed(e)
                }
            }
        }

        private fun withJam(proposal: AssistantReply.Proposal, jam: JamProposalDto?): AssistantReply.Proposal =
            when (val checked = jam?.let { JamProposalValidator.validate(it, calendar.today()) }) {
                null -> proposal
                is JamValidation.Valid -> proposal.copy(jam = checked.jam)
                is JamValidation.Rejected -> proposal.copy(jamRejection = checked.reason)
            }

        private fun failed(error: Throwable): AssistantReply = AssistantReply.Failed(AssistantErrors.failureOf(error))

        private suspend fun runAgent(
            request: String,
            songs: List<Song>,
            onDraft: (AssistantDraft) -> Unit,
        ): ProposalDto {
            val agent = AIAgent(
                promptExecutor = executor,
                agentConfig = AIAgentConfig(
                    prompt = AssistantPrompt.build(history),
                    model = AssistantModel.model,
                    maxAgentIterations = AssistantModel.MAX_AGENT_ITERATIONS,
                ),
                // Streamed, with the schema in the prompt (Manual, not Native): with `response_format` the
                // gateway's model skips the tools and answers at once (live call of 13 October 2026,
                // docs/evidence/assistant-agent-core). The final text is parsed whole after the stream.
                strategy = streamingProposalStrategy(songs, onDraft),
                toolRegistry = ToolRegistry {
                    tool(CatalogTool(catalog))
                    tool(PastJamsTool(jams))
                    tool(JamScheduleTool(jams, calendar))
                },
            )
            return agent.run(request)
        }
    }
}
