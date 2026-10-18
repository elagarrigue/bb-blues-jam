package com.bbbjam.core.data.remote

/** A POST transport that records every body and answers [answer], which a test may change between calls. */
internal class FakePost(var answer: TransportResult) : AppsScriptPostTransport {
    val bodies = mutableListOf<String>()

    fun actions(): List<String> = bodies.map { Regex("\"action\":\"([^\"]+)\"").find(it)!!.groupValues[1] }

    override suspend fun post(json: String): TransportResult {
        bodies += json
        return answer
    }
}
