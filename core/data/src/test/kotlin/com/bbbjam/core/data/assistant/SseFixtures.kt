package com.bbbjam.core.data.assistant

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import mockwebserver3.MockResponse

/**
 * Scripted chat completions streams in the gateway's wire format (docs/evidence/assistant-streaming/
 * gateway-probe.md): `data:` events ending with `[DONE]`, a reasoning prelude carrying the extra
 * `reasoning_content` / `reasoning` / `reasoning_details` fields with `content: ""`, content that
 * splits anywhere, tool calls as an id and name first and argument fragments after, `finish_reason` in
 * its own chunk and `usage` in a last one.
 */
internal object SseFixtures {
    private fun chunk(delta: JsonObject, finishReason: String? = null, usage: Boolean = false): String =
        buildJsonObject {
            put("id", "chatcmpl-1")
            put("object", "chat.completion.chunk")
            put("created", 1)
            put("model", "chat")
            putJsonArray("choices") {
                add(
                    buildJsonObject {
                        put("index", 0)
                        put("delta", delta)
                        put("finish_reason", finishReason?.let(::JsonPrimitive) ?: JsonPrimitive(null as String?))
                    },
                )
            }
            if (usage) {
                putJsonObject("usage") {
                    put("prompt_tokens", 1)
                    put("completion_tokens", 1)
                    put("total_tokens", 2)
                }
            }
        }.toString()

    fun reasoning(text: String): String = chunk(
        buildJsonObject {
            put("role", "assistant")
            put("content", "")
            put("reasoning_content", text)
            put("reasoning", text)
            put(
                "reasoning_details",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "reasoning.text")
                            put("text", text)
                            put("format", "unknown")
                            put("index", 0)
                        },
                    )
                },
            )
        },
    )

    fun content(text: String): String = chunk(buildJsonObject { put("content", text) })

    private fun toolStart(index: Int, id: String, name: String): String = chunk(
        buildJsonObject {
            putJsonArray("tool_calls") {
                add(
                    buildJsonObject {
                        put("index", index)
                        put("id", id)
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", name)
                            put("arguments", "")
                        }
                    },
                )
            }
        },
    )

    private fun toolArguments(index: Int, fragment: String): String = chunk(
        buildJsonObject {
            putJsonArray("tool_calls") {
                add(
                    buildJsonObject {
                        put("index", index)
                        putJsonObject("function") { put("arguments", fragment) }
                    },
                )
            }
        },
    )

    /** The end frames: `finish_reason` in its own chunk, then the usage chunk. */
    fun finish(reason: String): List<String> =
        listOf(chunk(buildJsonObject {}, finishReason = reason), chunk(buildJsonObject {}, usage = true))

    /** Splits [text] into pieces of [size] characters: a boundary falls inside keys, strings and escapes. */
    fun pieces(text: String, size: Int = 7): List<String> = text.chunked(size)

    /** A tool round: optional streamed text first, then the calls with their arguments split in two. */
    fun toolRound(vararg calls: Pair<String, String>, text: List<String> = emptyList()): List<String> {
        val events = ArrayList<String>()
        events += reasoning("Pienso qué herramienta usar.")
        text.forEach { events += content(it) }
        calls.forEachIndexed { index, (name, arguments) ->
            events += toolStart(index, "call_$index", name)
            val half = arguments.length / 2
            events += toolArguments(index, arguments.substring(0, half))
            events += toolArguments(index, arguments.substring(half))
        }
        return events + finish("tool_calls")
    }

    /** A text answer: a reasoning prelude, then [parts] as content, then the end frames. */
    fun answer(parts: List<String>, end: Boolean = true): List<String> =
        listOf(reasoning("Pienso la lista.")) + parts.map(::content) + if (end) finish("stop") else emptyList()

    /** The same, from a whole text split every [size] characters. */
    fun answer(text: String, size: Int = 7, end: Boolean = true): List<String> = answer(pieces(text, size), end)

    /** The events as a response; [done] adds the `[DONE]` terminator. */
    fun response(events: List<String>, done: Boolean = true): MockResponse = MockResponse.Builder()
        .addHeader("Content-Type", "text/event-stream; charset=utf-8")
        .body(body(events, done))
        .build()

    fun body(events: List<String>, done: Boolean = true): String =
        events.joinToString("") { "data: $it\n\n" } + if (done) "data: [DONE]\n\n" else ""
}
