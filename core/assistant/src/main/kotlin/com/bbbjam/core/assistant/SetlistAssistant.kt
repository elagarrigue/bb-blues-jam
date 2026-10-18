package com.bbbjam.core.assistant

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

    /**
     * Whether the gateway can be used now: one plain read of its model list that consumes no tokens.
     * [AssistantStatus.NotConfigured] makes no request. Cancellation is rethrown.
     */
    suspend fun checkStatus(): AssistantStatus
}

/** One chat. It keeps its answered turns in memory so the admin can refine a proposal. */
interface AssistantConversation {
    /**
     * Sends [request] and returns the reply. Calls are serialised per conversation. [request] must not be
     * blank (the caller disables sending blank text; a blank one is a caller bug and throws
     * [IllegalArgumentException]). Coroutine cancellation is rethrown, never mapped to a reply.
     *
     * The answer streams: [onDraft] is called, from the request's coroutine and never after this function
     * returns, each time another catalog-validated song of the answer in progress is complete. The returned
     * reply is still the source of truth: the whole text parsed and validated, replacing any draft.
     */
    suspend fun send(request: String, onDraft: (AssistantDraft) -> Unit = {}): AssistantReply
}

/**
 * The songs of the answer in progress that are complete and found in the catalog (D-14), in arrival order
 * and cumulative. Empty when a round turned out to be a tool round and its text was discarded. Not a
 * proposal: nothing can be loaded from it.
 */
data class AssistantDraft(val songs: List<ProposedSong>)
