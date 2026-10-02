package com.bbbjam.core.data

/**
 * Why a read from the Apps Script web app failed. A failure never touches the cache: the last
 * stored data stays readable and [Freshness.lastFailure] says what went wrong.
 */
sealed interface DataFailure {
    /** No Apps Script URL is configured (a fresh clone, CI). No request was made. */
    data object NotConfigured : DataFailure

    /** The request did not complete: no network, a timeout, a dropped connection. */
    data object Offline : DataFailure

    /** The script answered with an envelope error, such as `missing_header` (`docs/apps-script-api.md`). */
    data class Service(val code: String) : DataFailure

    /**
     * The answer is not the contract: a non-2xx status, a body that is not JSON (an HTML page from
     * Google), a wrong shape or a `schemaVersion` other than 1. [detail] is for logs only.
     */
    data class InvalidResponse(val detail: String) : DataFailure
}

private const val NOT_CONFIGURED = "NotConfigured"
private const val OFFLINE = "Offline"
private const val SERVICE = "Service:"
private const val INVALID_RESPONSE = "InvalidResponse:"

/** The failure as stored in the cache's `sync_state.failure` column. */
internal fun DataFailure.encode(): String = when (this) {
    DataFailure.NotConfigured -> NOT_CONFIGURED
    DataFailure.Offline -> OFFLINE
    is DataFailure.Service -> SERVICE + code
    is DataFailure.InvalidResponse -> INVALID_RESPONSE + detail
}

/** The inverse of [encode]; an unknown value reads as an invalid response rather than failing. */
internal fun decodeDataFailure(stored: String): DataFailure = when {
    stored == NOT_CONFIGURED -> DataFailure.NotConfigured
    stored == OFFLINE -> DataFailure.Offline
    stored.startsWith(SERVICE) -> DataFailure.Service(stored.removePrefix(SERVICE))
    stored.startsWith(INVALID_RESPONSE) -> DataFailure.InvalidResponse(stored.removePrefix(INVALID_RESPONSE))
    else -> DataFailure.InvalidResponse(stored)
}
