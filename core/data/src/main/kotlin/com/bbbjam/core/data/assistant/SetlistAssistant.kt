package com.bbbjam.core.data.assistant

/**
 * The setlist assistant (phase 2, D-21 to D-24): an agent that reads the catalog and past jams through
 * read-only tools and answers with a catalog-validated [AssistantReply.Proposal]. It writes nothing
 * (D-24); loading a proposal into the setlist is a later slice that goes through the action contract.
 */
interface SetlistAssistant {
    /** False when the build has no `devexpert.key`; conversations then answer [AssistantReply.NotConfigured]. */
    val isConfigured: Boolean

    /** A new conversation with empty history. Conversations are plain objects, kept in memory only. */
    fun startConversation(): AssistantConversation
}

/** One chat. It keeps its answered turns in memory so the admin can refine a proposal. */
interface AssistantConversation {
    /**
     * Sends [request] and returns the reply. Calls are serialised per conversation. [request] must not be
     * blank (the caller disables sending blank text; a blank one is a caller bug and throws
     * [IllegalArgumentException]). Coroutine cancellation is rethrown, never mapped to a reply.
     */
    suspend fun send(request: String): AssistantReply
}
