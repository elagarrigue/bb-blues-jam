package com.bbbjam.core.data.assistant

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * The gateway and model the assistant talks to (D-21). [MODEL_ID] is the one constant to change when the
 * gateway renames its alias.
 *
 * The model declares [LLMCapability.OpenAIEndpoint.Completions] and never `Responses`, so Koog uses the
 * chat completions endpoint and `/v1/responses` is unreachable.
 */
internal object AssistantModel {
    const val BASE_URL = "https://inference.devexpert.io"
    const val CHAT_COMPLETIONS_PATH = "v1/chat/completions"
    const val MODEL_ID = "chat"

    /** Iterations (LLM calls plus tool rounds) the agent may spend on one request. */
    const val MAX_AGENT_ITERATIONS = 12

    val model: LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = MODEL_ID,
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.OpenAIEndpoint.Completions,
        ),
    )
}
