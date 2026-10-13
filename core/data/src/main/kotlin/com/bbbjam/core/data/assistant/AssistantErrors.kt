package com.bbbjam.core.data.assistant

import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.structure.LLMStructuredParsingError
import java.io.IOException
import kotlinx.serialization.SerializationException

/** Maps what an agent run throws to an [AssistantFailure]. Cancellation never reaches it. */
internal object AssistantErrors {
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
    private const val MAX_CAUSE_DEPTH = 10

    /**
     * Walks [error]'s cause chain and returns the failure of the first cause that names one: an HTTP
     * status, a connection problem, an exhausted iteration budget or an unparseable answer. A wrapper
     * with nothing recognizable inside is an invalid response.
     */
    fun failureOf(error: Throwable): AssistantFailure {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            val found = failureOfSingle(current)
            if (found != null) return found
            current = current.cause
            depth++
        }
        return AssistantFailure.InvalidResponse
    }

    private fun failureOfSingle(error: Throwable): AssistantFailure? = when (error) {
        is KoogHttpClientException -> error.statusCode?.let(::httpFailure)
        is IOException -> AssistantFailure.Offline
        is AIAgentMaxNumberOfIterationsReachedException -> AssistantFailure.TooManySteps
        is LLMStructuredParsingError, is SerializationException -> AssistantFailure.InvalidResponse
        else -> null
    }

    private fun httpFailure(status: Int): AssistantFailure = when (status) {
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> AssistantFailure.Unauthorized
        else -> AssistantFailure.Unavailable(status)
    }
}
