package com.bbbjam.core.data.remote

import com.bbbjam.core.data.DataFailure
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** A decoded payload, or the failure that replaced it. */
internal sealed interface Decoded<out T> {
    data class Ok<T>(val value: T) : Decoded<T>

    data class Failed(val failure: DataFailure) : Decoded<Nothing>
}

/**
 * Reads the Apps Script envelope (`docs/apps-script-api.md`, Envelope), in this order: the body must
 * be a JSON object; `schemaVersion` must be 1; an `error` key is a [DataFailure.Service]; the
 * payload key must be present and decode. Unknown keys are ignored so additive fields do not break
 * an old app. Failure details never quote the body, which could be an HTML page.
 */
internal object AppsScriptEnvelope {
    private const val SCHEMA_VERSION = 1
    private const val MAX_DETAIL = 200

    val json: Json = Json { ignoreUnknownKeys = true }

    fun <T> decode(body: String, payloadKey: String, serializer: KSerializer<T>): Decoded<List<T>> {
        val root = parseObject(body) ?: return invalid("body is not a JSON object")
        val version = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        val error = root["error"]
        val payload = root[payloadKey]
        return when {
            version != SCHEMA_VERSION -> invalid("schemaVersion ${version ?: "missing"}")
            error != null -> serviceFailure(error)
            payload == null || payload is JsonNull -> invalid("missing $payloadKey")
            else -> decodePayload(payload, payloadKey, serializer)
        }
    }

    private fun parseObject(body: String): JsonObject? = try {
        json.parseToJsonElement(body) as? JsonObject
    } catch (ignored: SerializationException) {
        null
    }

    private fun serviceFailure(error: JsonElement): Decoded<Nothing> {
        val code = ((error as? JsonObject)?.get("code") as? JsonPrimitive)?.takeIf { it.isString }?.content
        return if (code.isNullOrBlank()) invalid("malformed error") else Decoded.Failed(DataFailure.Service(code))
    }

    private fun <T> decodePayload(
        payload: JsonElement,
        payloadKey: String,
        serializer: KSerializer<T>,
    ): Decoded<List<T>> = try {
        Decoded.Ok(json.decodeFromJsonElement(ListSerializer(serializer), payload))
    } catch (e: SerializationException) {
        invalid("$payloadKey: ${e.message.orEmpty().lineSequence().first()}".take(MAX_DETAIL))
    } catch (e: IllegalArgumentException) {
        invalid("$payloadKey: ${e.message.orEmpty().lineSequence().first()}".take(MAX_DETAIL))
    }

    private fun invalid(detail: String): Decoded.Failed = Decoded.Failed(DataFailure.InvalidResponse(detail))
}
