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
import kotlinx.serialization.json.booleanOrNull
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

    /**
     * Reads an action's answer (`docs/apps-script-api.md`, POST actions) in the same order as
     * [decode]: a JSON object, `schemaVersion` 1, an `error` key as a [DataFailure.Service], then
     * `ok` must be the JSON boolean `true`; anything else is an invalid response.
     */
    fun decodeOk(body: String): Decoded<Unit> = when (val decoded = decodeOkObject(body)) {
        is Decoded.Ok -> Decoded.Ok(Unit)
        is Decoded.Failed -> decoded
    }

    /**
     * [decodeOk] with the root object kept, for an action whose answer carries a payload beside
     * `ok` (`readJams`, `addSong`). The same checks in the same order; reading the payload is the
     * caller's job.
     */
    fun decodeOkObject(body: String): Decoded<JsonObject> {
        val root = parseObject(body) ?: return invalid("body is not a JSON object")
        val version = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        val error = root["error"]
        val ok = (root["ok"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
        return when {
            version != SCHEMA_VERSION -> invalid("schemaVersion ${version ?: "missing"}")
            error != null -> serviceFailure(error)
            ok != true -> invalid("ok is not true")
            else -> Decoded.Ok(root)
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
