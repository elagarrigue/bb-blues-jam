package com.bbbjam.core.data.remote

import com.bbbjam.core.data.DataFailure

/** One `GET <url>?resource=<resource>` against the Apps Script web app. */
internal fun interface AppsScriptTransport {
    suspend fun get(resource: String): TransportResult
}

/**
 * One `POST <url>` with a JSON body against the Apps Script web app (`docs/apps-script-api.md`,
 * POST actions). A secret such as the admin passphrase only ever travels in this body, never in a
 * URL. Separate from [AppsScriptTransport] so the read paths and their fakes stay as they are.
 */
internal fun interface AppsScriptPostTransport {
    suspend fun post(json: String): TransportResult
}

/** The raw outcome of a request: a 2xx body, or a failure decided before reading the envelope. */
internal sealed interface TransportResult {
    data class Body(val text: String) : TransportResult

    data class Failed(val failure: DataFailure) : TransportResult
}

/** Route names of `docs/apps-script-api.md`. */
internal object Resources {
    const val CATALOG = "catalog"
    const val JAMS = "jams"
}
