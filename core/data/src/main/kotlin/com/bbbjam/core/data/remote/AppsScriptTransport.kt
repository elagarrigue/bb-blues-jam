package com.bbbjam.core.data.remote

import com.bbbjam.core.data.DataFailure

/** One `GET <url>?resource=<resource>` against the Apps Script web app. */
internal fun interface AppsScriptTransport {
    suspend fun get(resource: String): TransportResult
}

/** The raw outcome of a request: a 2xx body, or a failure decided before reading the envelope. */
internal sealed interface TransportResult {
    data class Body(val text: String) : TransportResult

    data class Failed(val failure: DataFailure) : TransportResult
}

/** Route names of `docs/apps-script-api.md`. */
internal object Resources {
    const val CATALOG = "catalog"
}
