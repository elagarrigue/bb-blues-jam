package com.bbbjam.core.data.remote

import kotlinx.coroutines.CompletableDeferred

/** A GET transport that counts and records its calls and answers [result]; [gate] holds the answer back. */
internal class FakeTransport(var result: TransportResult) : AppsScriptTransport {
    var calls = 0
    val resources = mutableListOf<String>()
    val queries = mutableListOf<Map<String, String>>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun get(resource: String, parameters: Map<String, String>): TransportResult {
        calls++
        resources += resource
        queries += parameters
        gate?.await()
        return result
    }
}
