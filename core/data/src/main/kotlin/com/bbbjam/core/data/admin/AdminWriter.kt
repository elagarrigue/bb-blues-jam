package com.bbbjam.core.data.admin

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.remote.AppsScriptEnvelope
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.Decoded
import com.bbbjam.core.data.remote.ServiceCodes
import com.bbbjam.core.data.remote.TransportResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The one write path every mutation repository uses (`apps-script-write-auth`). It sends the stored
 * passphrase with the action in a POST body and maps the answer to a [WriteOutcome]. Apps Script
 * checks the passphrase on every action, so this class authorizes nothing by itself.
 *
 * It only reads the store: it never saves or clears the passphrase, whatever the answer (user
 * decision W3), so a refused write keeps admin mode on. It never logs, and the passphrase only ever
 * travels in the body.
 */
internal class AdminWriter(private val transport: AppsScriptPostTransport, private val store: AdminCredentialStore) {

    /**
     * Runs [action] with [fields] beside it in the body. A field named `action` or `passphrase` is a
     * programming error. With no passphrase stored, the answer is [WriteOutcome.AccessRefused] and
     * no request is sent.
     */
    suspend fun write(action: String, fields: Map<String, JsonElement> = emptyMap()): WriteOutcome {
        require(ACTION !in fields && PASSPHRASE !in fields) { "fields may not override $ACTION or $PASSPHRASE" }
        val passphrase = store.passphrase() ?: return WriteOutcome.AccessRefused
        val body = JsonObject(
            buildMap {
                put(ACTION, JsonPrimitive(action))
                put(PASSPHRASE, JsonPrimitive(passphrase))
                putAll(fields)
            },
        )
        val json = AppsScriptEnvelope.json.encodeToString(JsonObject.serializer(), body)
        return when (val result = transport.post(json)) {
            is TransportResult.Failed -> result.failure.toOutcome()

            is TransportResult.Body -> when (val decoded = AppsScriptEnvelope.decodeOk(result.text)) {
                is Decoded.Ok -> WriteOutcome.Done
                is Decoded.Failed -> decoded.failure.toOutcome()
            }
        }
    }

    private fun DataFailure.toOutcome(): WriteOutcome = when (this) {
        DataFailure.Offline -> WriteOutcome.Offline

        is DataFailure.Service -> when (code) {
            ServiceCodes.INVALID_PASSPHRASE, ServiceCodes.PASSPHRASE_NOT_SET -> WriteOutcome.AccessRefused
            ServiceCodes.RATE_LIMITED, ServiceCodes.BUSY, ServiceCodes.UNKNOWN_ACTION -> WriteOutcome.Unavailable
            else -> WriteOutcome.Rejected(code)
        }

        DataFailure.NotConfigured, is DataFailure.InvalidResponse, is DataFailure.Storage -> WriteOutcome.Unavailable
    }

    private companion object {
        const val ACTION = "action"
        const val PASSPHRASE = "passphrase"
    }
}
