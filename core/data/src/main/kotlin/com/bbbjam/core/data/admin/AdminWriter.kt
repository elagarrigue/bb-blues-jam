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
 * The admin POST path (`apps-script-write-auth`): every mutation repository and the admin read of
 * a draft (`admin-add-song-to-setlist`) use it. It sends the stored passphrase with the action in a
 * POST body and maps the answer to a [WriteOutcome], or to an [AdminAnswer] when the caller needs
 * the answer's payload. Apps Script checks the passphrase on every action, so this class
 * authorizes nothing by itself.
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
    suspend fun write(action: String, fields: Map<String, JsonElement> = emptyMap()): WriteOutcome =
        when (val answer = send(action, fields)) {
            is AdminAnswer.Ok -> WriteOutcome.Done
            is AdminAnswer.Refused -> answer.outcome
        }

    /**
     * [write] with the answer's root object kept: [AdminAnswer.Ok] when the server answered `ok`,
     * otherwise [AdminAnswer.Refused] with the same outcome [write] would return. Same store, body
     * and mapping rules, and no answer touches the store (W3).
     */
    suspend fun send(action: String, fields: Map<String, JsonElement> = emptyMap()): AdminAnswer {
        require(ACTION !in fields && PASSPHRASE !in fields) { "fields may not override $ACTION or $PASSPHRASE" }
        val passphrase = store.passphrase() ?: return AdminAnswer.Refused(WriteOutcome.AccessRefused)
        val body = JsonObject(
            buildMap {
                put(ACTION, JsonPrimitive(action))
                put(PASSPHRASE, JsonPrimitive(passphrase))
                putAll(fields)
            },
        )
        val json = AppsScriptEnvelope.json.encodeToString(JsonObject.serializer(), body)
        return when (val result = transport.post(json)) {
            is TransportResult.Failed -> AdminAnswer.Refused(result.failure.toOutcome(), result.failure)

            is TransportResult.Body -> when (val decoded = AppsScriptEnvelope.decodeOkObject(result.text)) {
                is Decoded.Ok -> AdminAnswer.Ok(decoded.value)
                is Decoded.Failed -> AdminAnswer.Refused(decoded.failure.toOutcome(), decoded.failure)
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

/** What [AdminWriter.send] got back. */
internal sealed interface AdminAnswer {
    /** The server answered `ok`; [body] is the whole answer object, payload included. */
    data class Ok(val body: JsonObject) : AdminAnswer

    /**
     * Anything else, as the [WriteOutcome] [AdminWriter.write] maps it to (never
     * [WriteOutcome.Done]), with the [failure] behind it for a caller that records one; null when no
     * passphrase is stored and nothing was sent.
     */
    data class Refused(val outcome: WriteOutcome, val failure: DataFailure? = null) : AdminAnswer
}
