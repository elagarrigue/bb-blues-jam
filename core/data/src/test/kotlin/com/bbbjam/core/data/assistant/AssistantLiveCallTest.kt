package com.bbbjam.core.data.assistant

import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.catalog.CatalogMapper
import com.bbbjam.core.data.jams.JamsMapper
import com.bbbjam.core.model.Instrument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.Properties
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in live call to the real gateway, outside the gate: it is skipped unless the system property
 * `bbbjam.assistantLive` is `true` (`-Pbbbjam.assistantLive=true`, see `core/data/build.gradle.kts`).
 * It reads the key itself from `local.properties` (never through Gradle), runs the brief's example
 * request once against in-memory repositories seeded from the repository's samples (no Sheet access,
 * and no write path exists), and writes `docs/evidence/assistant-streaming/live-call.md` without the
 * key or the Authorization header. It fails if the key appears in anything it printed or wrote.
 */
class AssistantLiveCallTest {
    private val rootDir = File(System.getProperty("bbbjam.rootDir").orEmpty())

    private class Exchange(
        val path: String,
        val hasTools: Boolean,
        val toolsOffered: List<String>,
        val responseFormat: String?,
        val messageRoles: List<String>,
        var status: Int = 0,
        var finishReason: String? = null,
        var toolCallsRequested: List<String> = emptyList(),
        var content: String? = null,
        var millis: Long = 0,
    )

    @Test
    fun `the brief's example request against the real gateway`() {
        assumeTrue(System.getProperty("bbbjam.assistantLive") == "true")
        val key = Properties().apply { File(rootDir, "local.properties").inputStream().use { load(it) } }
            .getProperty("devexpert.key").orEmpty().trim()
        assumeTrue(key.isNotEmpty())

        val songs = CatalogMapper.map(Fixtures.sampleSongs("catalog-seed.json")).songs
        val seeded = JamsMapper.map(Fixtures.sampleJams("jams-seed.json")).jams.map { it.jam }
        // Synthetic jam so that "algún tema que haya tocado Juan" has an answer; in memory only.
        val juanJam = jam(
            "2026-06-27",
            listOf("dust-my-broom", "crossroads", "the-thrill-is-gone").mapIndexed { index, id ->
                val song = songs.first { it.id.value == id }
                jamSong(
                    index + 1,
                    id,
                    song.title,
                    song.artist,
                    song.defaultKey.value,
                    mapOf(Instrument.GUITAR to "Juan"),
                )
            },
        )
        val exchanges = ArrayList<Exchange>()
        val client = AssistantHttp.okHttpClient().newBuilder().addInterceptor(recorder(exchanges)).build()
        val assistant = DefaultSetlistAssistant(
            AssistantCredentials.of(key),
            FakeCatalog(songs),
            FakePastJams(past = listOf(juanJam) + seeded),
            fixedCalendar(),
            AssistantHttp.executorFactory(AssistantModel.BASE_URL, client),
        )

        val originalOut = System.out
        val originalErr = System.err
        val printed = ByteArrayOutputStream()
        val drafts = ArrayList<AssistantDraft>()
        val started = System.nanoTime()
        val reply = try {
            System.setOut(PrintStream(printed, true))
            System.setErr(PrintStream(printed, true))
            runBlocking { assistant.startConversation().send(REQUEST) { drafts += it } }
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
        val totalMillis = (System.nanoTime() - started) / NANOS_PER_MILLI

        val report = report(reply, exchanges, totalMillis, songs.size, printed.toString().isNotBlank(), drafts)
        val file = File(rootDir, "docs/evidence/assistant-streaming/live-call.md")
        file.parentFile.mkdirs()
        file.writeText(report)

        assertFalse("the key leaked into the output", printed.toString().contains(key))
        assertFalse("the key leaked into the evidence", report.contains(key))
        assertFalse("the key leaked into the reply", reply.toString().contains(key))
    }

    private fun recorder(into: MutableList<Exchange>) = Interceptor { chain ->
        val request = chain.request()
        val sent = Buffer().also { request.body?.writeTo(it) }.readUtf8()
        val json = runCatching { Json.parseToJsonElement(sent).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
        val exchange = Exchange(
            path = request.url.encodedPath,
            hasTools = "tools" in json,
            toolsOffered = json["tools"]?.jsonArray.orEmpty().map {
                it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content
            },
            responseFormat = json["response_format"]?.jsonObject?.get("type")?.jsonPrimitive?.content,
            messageRoles = json["messages"]?.jsonArray.orEmpty().map {
                it.jsonObject.getValue("role").jsonPrimitive.content
            },
        )
        into += exchange
        val begun = System.nanoTime()
        val response: Response = chain.proceed(request)
        exchange.millis = (System.nanoTime() - begun) / NANOS_PER_MILLI
        exchange.status = response.code
        // The gateway answers as an SSE stream: read its `data:` events from the peeked body.
        val choices = runCatching {
            response.peekBody(PEEK_BYTES).string().lineSequence()
                .filter { it.startsWith("data: {") }
                .map { Json.parseToJsonElement(it.removePrefix("data: ")).jsonObject }
                .mapNotNull { it["choices"]?.jsonArray?.firstOrNull()?.jsonObject }
                .toList()
        }.getOrDefault(emptyList())
        exchange.finishReason =
            choices.mapNotNull { (it["finish_reason"] as? JsonPrimitive)?.contentOrNull }.lastOrNull()
        val deltas = choices.mapNotNull { it["delta"]?.jsonObject }
        exchange.content = deltas.mapNotNull { (it["content"] as? JsonPrimitive)?.contentOrNull }.joinToString("")
        exchange.toolCallsRequested = deltas.flatMap { it["tool_calls"]?.jsonArray.orEmpty() }.mapNotNull {
            (it.jsonObject["function"]?.jsonObject?.get("name") as? JsonPrimitive)?.contentOrNull
        }
        response
    }

    private fun report(
        reply: AssistantReply,
        exchanges: List<Exchange>,
        totalMillis: Long,
        catalogSize: Int,
        printedSomething: Boolean,
        drafts: List<AssistantDraft>,
    ): String = buildString {
        val toolsCalled = exchanges.flatMap { it.toolCallsRequested }
        appendLine("# assistant-streaming: live call (streamed)")
        appendLine()
        appendLine("Opt-in run against the real gateway; no Sheet access, no write path exists. The key and the")
        appendLine("Authorization header are not recorded.")
        appendLine()
        appendLine("- Base URL: ${AssistantModel.BASE_URL}")
        appendLine("- Model alias: `${AssistantModel.MODEL_ID}`")
        appendLine("- Endpoint path(s) hit: ${exchanges.map { it.path }.distinct().joinToString(", ")}")
        appendLine("- HTTP requests: ${exchanges.size}; statuses: ${exchanges.map { it.status }}")
        appendLine("- Latency: total $totalMillis ms; per request ${exchanges.map { it.millis }} ms")
        appendLine("- Catalog size (seed): $catalogSize songs")
        appendLine("- Tools offered: ${exchanges.firstOrNull()?.toolsOffered}")
        appendLine("- `response_format` sent: ${exchanges.map { it.responseFormat }.distinct()}")
        val honored = if (toolsCalled.isNotEmpty()) "yes" else "no"
        appendLine("- Tool calling honored: $honored (tools requested by the model: $toolsCalled)")
        appendLine("- Message roles per request: ${exchanges.map { it.messageRoles }}")
        appendLine("- finish_reason per response: ${exchanges.map { it.finishReason }}")
        val printed = if (printedSomething) "yes (checked: no key in it)" else "no"
        appendLine("- Drafts seen while streaming: ${drafts.size}; songs per draft: ${drafts.map { it.songs.size }}")
        appendLine("- Anything printed to stdout/stderr during the call: $printed")
        appendLine()
        appendLine("## Request (the brief's example, verbatim)")
        appendLine()
        appendLine("> $REQUEST")
        appendLine()
        appendLine("## Outcome")
        appendLine()
        when (reply) {
            is AssistantReply.Proposal -> {
                appendLine("- Structured output honored (final answer parsed against the schema): yes")
                appendLine("- Validated songs: ${reply.songs.size}; rejected ids: ${reply.rejectedSongIds}")
                appendLine("- Message: ${reply.message}")
                appendLine("- Questions: ${reply.questions}")
                appendLine()
                appendLine("| # | id | title | artist | key | reason |")
                appendLine("|---|---|---|---|---|---|")
                reply.songs.forEachIndexed { index, song ->
                    val cells = listOf(index + 1, song.songId, song.title, song.artist, song.key, song.reason)
                    appendLine(cells.joinToString(" | ", "| ", " |"))
                }
            }

            else -> {
                appendLine("- Reply: $reply")
                appendLine("- Structured output honored: no (no validated proposal)")
            }
        }
        appendLine()
        appendLine("## Raw final content of the last response")
        appendLine()
        appendLine("```")
        appendLine(exchanges.lastOrNull()?.content.orEmpty())
        appendLine("```")
    }

    private companion object {
        const val REQUEST =
            "Generá una lista de temas de blues nacional, 12, con inicio y final bien arriba y temas más " +
                "tranquis en el medio. Sumá algún tema que haya tocado Juan en alguna jam pasada."
        const val NANOS_PER_MILLI = 1_000_000L
        const val PEEK_BYTES = 1_000_000L
    }
}
