package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.TransportResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The setlist tests' POST double: it records every body, counts them in [posted], waits on [gate]
 * when one is set, and answers from [answers] in order.
 */
internal class QueuedPostTransport : AppsScriptPostTransport {
    val answers = ArrayDeque<TransportResult>()
    val bodies = mutableListOf<String>()
    val posted = MutableStateFlow(0)
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun post(json: String): TransportResult {
        bodies += json
        posted.value = bodies.size
        gate?.await()
        return answers.removeFirst()
    }
}
