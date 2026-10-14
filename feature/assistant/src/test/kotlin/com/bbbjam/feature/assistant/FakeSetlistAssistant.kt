package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.AssistantConversation
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.AssistantStatus
import com.bbbjam.core.data.assistant.SetlistAssistant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * A [SetlistAssistant] whose replies the test controls: each `send` takes the next [CompletableDeferred]
 * queued with [expectReply] and waits for it. It records the requests, how many conversations were
 * started and whether a send saw its cancellation.
 */
internal class FakeSetlistAssistant(override val isConfigured: Boolean = true) : SetlistAssistant {
    val requests = mutableListOf<String>()
    var conversationsStarted = 0
        private set
    var sawCancellation = false
        private set
    var status: AssistantStatus = AssistantStatus.Available
    var statusChecks = 0
        private set

    /** When set, `checkStatus` waits for it, so a test can observe the Checking state. */
    var statusGate: CompletableDeferred<Unit>? = null
    private val queue = ArrayDeque<CompletableDeferred<AssistantReply>>()

    fun expectReply(): CompletableDeferred<AssistantReply> =
        CompletableDeferred<AssistantReply>().also { queue.addLast(it) }

    override suspend fun checkStatus(): AssistantStatus {
        statusChecks++
        statusGate?.await()
        return status
    }

    override fun startConversation(): AssistantConversation {
        conversationsStarted++
        return object : AssistantConversation {
            override suspend fun send(request: String): AssistantReply {
                requests += request
                val reply = queue.removeFirst()
                try {
                    return reply.await()
                } catch (cancelled: CancellationException) {
                    sawCancellation = true
                    throw cancelled
                }
            }
        }
    }
}
