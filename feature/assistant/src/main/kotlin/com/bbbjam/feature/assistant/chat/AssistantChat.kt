package com.bbbjam.feature.assistant.chat

import com.bbbjam.core.data.assistant.AssistantConversation
import com.bbbjam.core.data.assistant.AssistantFailure
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.AssistantStatus
import com.bbbjam.core.data.assistant.ProposedSong
import com.bbbjam.core.data.assistant.SetlistAssistant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The admin's chat with the assistant, alive for the app process (`assistant-admin-tab`, Decision 1).
 * A presenter is a Koin `factory` and its composition is disposed when the tab is left, so it cannot
 * hold an 8 s+ paid request or the conversation history; this plain class is bound as a `single`
 * instead. It is not a ViewModel and holds no Android type (D-02): the requests run in [scope], which
 * the caller owns. It is UI session state, not a mutation: nothing is written anywhere (D-13, D-24).
 *
 * Requests run in [scope], not in a composition, so leaving the tab or rotating does not cancel one;
 * only [cancel] does. One turn is pending at a time. The conversation (and its history, kept by core)
 * is created on the first send and reused by every later turn, including retries.
 */
class AssistantChat(private val assistant: SetlistAssistant, private val scope: CoroutineScope) {
    private val lock = Any()
    private val state = MutableStateFlow<List<ChatTurn>>(emptyList())
    private val jobs = mutableMapOf<Long, Job>()
    private val serviceState = MutableStateFlow<ServiceState>(ServiceState.Checking)
    private var statusJob: Job? = null
    private var conversation: AssistantConversation? = null
    private var nextId = 1L

    /** False when the build has no key: the screen shows the not-configured block and sends nothing. */
    val isConfigured: Boolean
        get() = assistant.isConfigured

    /** Every turn so far, oldest first. */
    val turns: StateFlow<List<ChatTurn>> = state.asStateFlow()

    /** Whether the gateway can be used: [ServiceState.Checking] until the first [checkStatus] answers. */
    val service: StateFlow<ServiceState> = serviceState.asStateFlow()

    /** True when the last status read said the gateway is Available; the UI sends only then. */
    val isServiceAvailable: Boolean
        get() = serviceState.value == ServiceState.Known(AssistantStatus.Available)

    /**
     * Asks the assistant for its service status in [scope] (a read that consumes no tokens). Single-flight:
     * a check already running is not repeated. The state shows [ServiceState.Checking] only when the last
     * known one was not Available, so a re-check from Available is silent.
     */
    fun checkStatus() {
        synchronized(lock) {
            if (statusJob?.isActive == true) return
            if (serviceState.value != ServiceState.Known(AssistantStatus.Available)) {
                serviceState.value = ServiceState.Checking
            }
            statusJob = scope.launch { serviceState.value = ServiceState.Known(assistant.checkStatus()) }
        }
    }

    /**
     * Appends a pending turn for [request] (trimmed) and starts it. Returns false, doing nothing, when
     * the request is blank, the assistant is not configured or a turn is still pending.
     */
    fun send(request: String): Boolean = synchronized(lock) {
        val text = request.trim()
        if (text.isEmpty() || !isConfigured || hasPending()) return false
        start(text)
        true
    }

    /** Cancels turn [id] if it is pending; the turn becomes [ChatTurn.State.Cancelled]. */
    fun cancel(id: Long) {
        val job = synchronized(lock) {
            val pending = state.value.any { it.id == id && it.state == ChatTurn.State.Pending }
            if (!pending) return
            finish(id, ChatTurn.State.Cancelled)
            jobs.remove(id)
        }
        job?.cancel()
    }

    /**
     * Replaces the last turn with a new pending one for the same request. Allowed only for the last
     * turn, only when it ended in a failure, a cancellation or an interruption, and with none pending.
     */
    fun retry(id: Long): Boolean = synchronized(lock) {
        val last = state.value.lastOrNull() ?: return false
        if (last.id != id || hasPending() || !last.state.isRetryable()) return false
        state.update { it.dropLast(1) }
        start(last.request)
        true
    }

    private fun hasPending() = state.value.any { it.state == ChatTurn.State.Pending }

    private fun start(request: String) {
        val id = nextId++
        val chat = conversation ?: assistant.startConversation().also { conversation = it }
        state.update { it + ChatTurn(id, request, ChatTurn.State.Pending) }
        jobs[id] = scope.launch { run(id, chat, request) }
    }

    private suspend fun run(id: Long, chat: AssistantConversation, request: String) {
        try {
            val reply = chat.send(request) { draft -> setDraft(id, draft.songs) }
            finish(id, ChatTurn.State.Answered(reply))
            // The service may have just gone away: a failed send that names it re-checks the status.
            if (reply is AssistantReply.Failed && reply.failure.suggestsOutage()) checkStatus()
        } catch (cancelled: CancellationException) {
            finish(id, ChatTurn.State.Cancelled)
            throw cancelled
        } finally {
            // Only an unexpected exception escaping core leaves the turn pending here; the scope's
            // handler logs it, nothing is caught broadly.
            finish(id, ChatTurn.State.Interrupted)
            synchronized(lock) { jobs.remove(id) }
        }
    }

    /** Keeps the songs streamed so far on turn [id] while it is pending; a late call after a cancel is ignored. */
    private fun setDraft(id: Long, songs: List<ProposedSong>) {
        state.update { turns ->
            turns.map { if (it.id == id && it.state == ChatTurn.State.Pending) it.copy(draft = songs) else it }
        }
    }

    /** Moves turn [id] out of Pending only: a turn that already ended keeps its state. */
    private fun finish(id: Long, to: ChatTurn.State) {
        state.update { turns ->
            turns.map { if (it.id == id && it.state == ChatTurn.State.Pending) it.copy(state = to) else it }
        }
    }

    private fun AssistantFailure.suggestsOutage() =
        this == AssistantFailure.Offline || this is AssistantFailure.Unavailable

    private fun ChatTurn.State.isRetryable(): Boolean = when (this) {
        ChatTurn.State.Pending -> false
        is ChatTurn.State.Answered -> reply is AssistantReply.Failed
        ChatTurn.State.Cancelled, ChatTurn.State.Interrupted -> true
    }
}

/**
 * One request and what became of it. [id] comes from a counter and is never reused. [draft] holds the
 * catalog-validated songs streamed while the turn was pending; it is a preview, never a proposal: the
 * final reply replaces it, and it stays only to show an incomplete answer (`assistant-streaming`).
 */
data class ChatTurn(val id: Long, val request: String, val state: State, val draft: List<ProposedSong> = emptyList()) {
    sealed interface State {
        /** Sent, no answer yet. */
        data object Pending : State

        /** The assistant answered with [reply] (a proposal, or a failure to show). */
        data class Answered(val reply: AssistantReply) : State

        /** The admin cancelled it. */
        data object Cancelled : State

        /** An unexpected exception ended it without a reply. */
        data object Interrupted : State
    }
}

/** What the chat knows about the service: not yet, or the last [status] it read. */
sealed interface ServiceState {
    /** No answer yet (the first check, or a re-check after a problem). */
    data object Checking : ServiceState

    data class Known(val status: AssistantStatus) : ServiceState
}
