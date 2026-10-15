package com.bbbjam.core.data.assistant

import com.bbbjam.core.model.Instrument
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The assistant through the real Koog client, Ktor and OkHttp against a scripted local gateway. No test
 * reaches the network. The scripts speak the chat completions wire format.
 */
class DefaultSetlistAssistantTest {
    private val server = MockWebServer()
    private val key = "test-key-0123456789"
    private val catalog = FakeCatalog(
        listOf(
            song("sweet-little-angel", "Sweet Little Angel", "B.B. King", "Bm"),
            song("pappo-blues", "Pappo's Blues", "Pappo", "E"),
            song("crossroads", "Crossroads", "Robert Johnson", "A"),
        ),
    )
    private val jams = FakeJams(
        past = listOf(
            jam(
                "2026-07-25",
                listOf(jamSong(1, "pappo-blues", "Pappo's Blues", "Pappo", "E", mapOf(Instrument.GUITAR to "Juan"))),
            ),
        ),
    )

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun assistant(
        raw: String = key,
        songs: FakeCatalog = catalog,
        baseUrl: String = server.url("/").toString().trimEnd('/'),
    ): SetlistAssistant = DefaultSetlistAssistant(
        AssistantCredentials.of(raw),
        songs,
        jams,
        fixedCalendar(),
        AssistantHttp.executorFactory(baseUrl, OkHttpClient()),
    )

    // The gateway answers as a stream (assistant-streaming): every scripted reply is an SSE body.
    private fun toolCalls(vararg calls: Pair<String, String>): MockResponse =
        SseFixtures.response(SseFixtures.toolRound(*calls))

    private fun answer(content: String): MockResponse = SseFixtures.response(SseFixtures.answer(content, size = 11))

    private fun proposalJson(vararg ids: String, message: String = "Armé la lista", questions: String = "[]"): String {
        val songs = ids.joinToString(",") {
            """{"songId":"$it","title":"x","artist":"y","key":"C","reason":"Va bien acá"}"""
        }
        return """{"songs":[$songs],"message":"$message","questions":$questions}"""
    }

    private fun jamAnswer(date: String, time: String = "21:00", venue: String = "La Macanuda"): String =
        """{"songs":[],"message":"Listo","questions":[],"jam":{"date":"$date","time":"$time","venue":"$venue"}}"""

    private fun body(request: RecordedRequest): JsonObject =
        Json.parseToJsonElement(requireNotNull(request.body).utf8()).jsonObject

    private fun messages(request: RecordedRequest): JsonArray = body(request).getValue("messages").jsonArray

    private fun roles(request: RecordedRequest): List<String> =
        messages(request).map { it.jsonObject.getValue("role").jsonPrimitive.content }

    private fun contentOf(message: kotlinx.serialization.json.JsonElement): String {
        val content = message.jsonObject["content"] ?: return ""
        return if (content is JsonArray) content.joinToString(" ") { it.toString() } else content.toString()
    }

    @Test
    fun `without a key it answers NotConfigured and makes no request`() = runBlocking<Unit> {
        val reply = assistant(raw = "  ").startConversation().send("armame una jam")

        assertEquals(AssistantReply.NotConfigured, reply)
        assertEquals(0, server.requestCount)
        assertFalse(assistant(raw = "").isConfigured)
        assertTrue(assistant().isConfigured)
    }

    @Test
    fun `a blank request is a caller bug`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { assistant().startConversation().send("   ") }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an empty catalog fails before any request`() = runBlocking<Unit> {
        val reply = assistant(songs = FakeCatalog(emptyList())).startConversation().send("armame una jam")

        assertEquals(AssistantReply.Failed(AssistantFailure.CatalogEmpty), reply)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `tools run against the repositories and the proposal keeps the model order and the request shape`() =
        runBlocking<Unit> {
            server.enqueue(toolCalls("searchCatalog" to """{"query":""}""", "pastJams" to """{"musician":"Juan"}"""))
            server.enqueue(answer(proposalJson("crossroads", "pappo-blues", message = "Listo")))

            val reply = assistant().startConversation().send("blues nacional, con algo que haya tocado Juan")

            val proposal = reply as AssistantReply.Proposal
            assertEquals(listOf("crossroads", "pappo-blues"), proposal.songs.map { it.songId.value })
            assertEquals("Listo", proposal.message)
            assertEquals(2, server.requestCount)

            val first = server.takeRequest()
            assertEquals("POST", first.method)
            assertEquals("/v1/chat/completions", first.url.encodedPath)
            assertEquals("Bearer $key", first.headers["Authorization"])
            assertEquals("chat", body(first).getValue("model").jsonPrimitive.content)
            val toolNames = body(first).getValue("tools").jsonArray.map {
                it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content
            }
            assertEquals(setOf("searchCatalog", "pastJams", "jamSchedule"), toolNames.toSet())
            // The gateway's models skip the tools when `response_format` is sent (live call, 13 October
            // 2026), so the schema travels in the prompt (StructuredRequest.Manual) and no response_format.
            assertFalse("response_format" in body(first))
            assertTrue(contentOf(messages(first)[1]).contains("songId"))
            assertTrue(contentOf(messages(first)[2]).contains("blues nacional"))
            assertEquals(listOf("system", "user", "user"), roles(first))

            val second = server.takeRequest()
            val toolMessages = messages(second).map { it.jsonObject }.filter {
                it.getValue("role").jsonPrimitive.content == "tool"
            }
            assertEquals(2, toolMessages.size)
            val results = toolMessages.joinToString("\n") { contentOf(it) }
            assertTrue(results.contains("sweet-little-angel | Sweet Little Angel | B.B. King | Bm"))
            assertTrue(results.contains("pappo-blues | Pappo's Blues | Pappo | E | players: Juan (guitar)"))
        }

    @Test
    fun `a valid jam is validated against today and kept beside the proposal`() = runBlocking<Unit> {
        server.enqueue(toolCalls("jamSchedule" to """{"note":""}"""))
        server.enqueue(answer(jamAnswer("2026-11-28", venue = "  La   Macanuda ")))

        val reply = assistant().startConversation().send("creá la jam del 28 de noviembre") as AssistantReply.Proposal

        assertEquals(ProposedJam(LocalDate.of(2026, 11, 28), LocalTime.of(21, 0), "La Macanuda"), reply.jam)
        assertEquals(null, reply.jamRejection)
        server.takeRequest()
        val toolResult = messages(server.takeRequest()).map { it.jsonObject }.single {
            it.getValue("role").jsonPrimitive.content == "tool"
        }
        assertTrue(contentOf(toolResult).contains("today 2026-10-14 (miércoles 14 de octubre, Buenos Aires)"))
    }

    @Test
    fun `an answer without the jam key parses and carries no jam`() = runBlocking<Unit> {
        server.enqueue(answer(proposalJson("crossroads")))

        val reply = assistant().startConversation().send("armame una jam") as AssistantReply.Proposal

        assertEquals(null, reply.jam)
        assertEquals(null, reply.jamRejection)
    }

    @Test
    fun `an unusable jam becomes a rejection and the history keeps only a valid one`() = runBlocking<Unit> {
        server.enqueue(answer(jamAnswer("2026-10-01")))
        server.enqueue(answer(jamAnswer("2026-11-28")))
        server.enqueue(answer(proposalJson("crossroads")))
        val conversation = assistant().startConversation()

        val past = conversation.send("creá la jam del 1 de octubre") as AssistantReply.Proposal
        assertEquals(null, past.jam)
        assertEquals(JamRejection.PAST_DATE, past.jamRejection)
        conversation.send("mejor el 28 de noviembre")
        conversation.send("ahora sí armame una lista")

        server.takeRequest()
        server.takeRequest()
        val third = messages(server.takeRequest()).map { contentOf(it) }
        assertFalse(third.any { it.contains("2026-10-01") })
        assertTrue(third.any { it.contains("jam") && it.contains("2026-11-28") && it.contains("La Macanuda") })
    }

    @Test
    fun `unknown, malformed and repeated ids are rejected and catalog fields win`() = runBlocking<Unit> {
        server.enqueue(
            answer(proposalJson("sweet-little-angel", "made-up", "NOT VALID", "sweet-little-angel", "crossroads")),
        )

        val reply = assistant().startConversation().send("armame una jam") as AssistantReply.Proposal

        assertEquals(listOf("sweet-little-angel", "crossroads"), reply.songs.map { it.songId.value })
        assertEquals(listOf("made-up", "NOT VALID", "sweet-little-angel"), reply.rejectedSongIds)
        val first = reply.songs.first()
        assertEquals("Sweet Little Angel", first.title)
        assertEquals("B.B. King", first.artist)
        assertEquals("Bm", first.key.value)
    }

    @Test
    fun `questions with no songs are a valid proposal`() = runBlocking<Unit> {
        server.enqueue(answer(proposalJson(message = "Necesito más datos", questions = """["¿Cuántos temas?"]""")))

        val reply = assistant().startConversation().send("armame una jam") as AssistantReply.Proposal

        assertEquals(emptyList<ProposedSong>(), reply.songs)
        assertEquals(listOf("¿Cuántos temas?"), reply.questions)
    }

    @Test
    fun `an answer that is not JSON is an invalid response`() = runBlocking<Unit> {
        server.enqueue(answer("Claro, te armo la lista cuando quieras."))

        val reply = assistant().startConversation().send("armame una jam")

        assertEquals(AssistantReply.Failed(AssistantFailure.InvalidResponse), reply)
    }

    @Test
    fun `an answer missing a required field is an invalid response and never a partial list`() = runBlocking<Unit> {
        server.enqueue(
            answer("""{"songs":[{"songId":"crossroads","title":"x","artist":"y","key":"A","reason":"r"}]}"""),
        )

        val reply = assistant().startConversation().send("armame una jam")

        assertEquals(AssistantReply.Failed(AssistantFailure.InvalidResponse), reply)
    }

    @Test
    fun `a closed gateway is offline`() = runBlocking<Unit> {
        val url = server.url("/").toString().trimEnd('/')
        server.close()

        val reply = assistant(baseUrl = url).startConversation().send("armame una jam")

        assertEquals(AssistantReply.Failed(AssistantFailure.Offline), reply)
    }

    @Test
    fun `401 and 403 are unauthorized and other statuses are unavailable`() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"bad key"}""").build())
        server.enqueue(MockResponse.Builder().code(403).body("""{"error":"no"}""").build())
        server.enqueue(MockResponse.Builder().code(503).body("""{"error":"down"}""").build())
        val conversation = assistant().startConversation()

        assertEquals(AssistantReply.Failed(AssistantFailure.Unauthorized), conversation.send("uno"))
        assertEquals(AssistantReply.Failed(AssistantFailure.Unauthorized), conversation.send("dos"))
        assertEquals(AssistantReply.Failed(AssistantFailure.Unavailable(503)), conversation.send("tres"))
    }

    @Test
    fun `a model that never stops calling tools ends as too many steps`() = runBlocking<Unit> {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                toolCalls("searchCatalog" to """{"query":"x"}""")
        }

        val reply = assistant().startConversation().send("armame una jam")

        assertEquals(AssistantReply.Failed(AssistantFailure.TooManySteps), reply)
        assertTrue(server.requestCount <= AssistantModel.MAX_AGENT_ITERATIONS + 1)
    }

    @Test
    fun `a refinement carries the first request and the first validated proposal before the new text`() =
        runBlocking<Unit> {
            server.enqueue(answer(proposalJson("sweet-little-angel", "made-up", "pappo-blues")))
            server.enqueue(answer(proposalJson("crossroads")))
            val conversation = assistant().startConversation()

            conversation.send("blues nacional para abrir")
            conversation.send("cambiá el tercero por algo de Pappo")

            server.takeRequest()
            val second = server.takeRequest()
            assertEquals(listOf("system", "user", "assistant", "user", "user"), roles(second))
            val texts = messages(second).map { contentOf(it) }
            assertTrue(texts[1].contains("blues nacional para abrir"))
            assertTrue(texts[2].contains("sweet-little-angel"))
            assertTrue(texts[2].contains("pappo-blues"))
            assertFalse("the rejected id must not be re-sent", texts[2].contains("made-up"))
            // Koog puts the output-format instruction (user) after the history and before the new request.
            assertTrue(texts[3].contains("songId"))
            assertTrue(texts[4].contains("cambiá el tercero por algo de Pappo"))
        }

    @Test
    fun `a failed turn does not enter the history`() = runBlocking<Unit> {
        server.enqueue(answer("no es JSON"))
        server.enqueue(answer(proposalJson("crossroads")))
        val conversation = assistant().startConversation()

        conversation.send("primero")
        conversation.send("segundo")

        server.takeRequest()
        assertEquals(listOf("system", "user", "user"), roles(server.takeRequest()))
    }

    @Test
    fun `a cancelled send throws CancellationException and is not mapped to a reply`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse.Builder().headersDelay(10, TimeUnit.SECONDS).body(proposalJson("crossroads")).build(),
        )
        val outcome = AtomicReference<Result<AssistantReply>?>(null)
        val conversation = assistant().startConversation()
        val pending = async(Dispatchers.Default) {
            val result = runCatching { conversation.send("armame una jam") }
            outcome.set(result)
            result.getOrThrow()
        }
        delay(CANCEL_AFTER_MILLIS)

        pending.cancel()
        pending.join()

        val recorded = requireNotNull(outcome.get()) { "send did not finish" }
        assertTrue("send returned ${recorded.getOrNull()}", recorded.isFailure)
        assertTrue(recorded.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test
    fun `the key appears in no output, reply or toString`() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"bad key"}""").build())
        server.enqueue(answer(proposalJson("crossroads")))
        val assistant = assistant()
        val originalOut = System.out
        val originalErr = System.err
        val captured = ByteArrayOutputStream()
        val replies = try {
            System.setOut(PrintStream(captured, true))
            System.setErr(PrintStream(captured, true))
            val conversation = assistant.startConversation()
            listOf(conversation.send("uno"), conversation.send("dos"))
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        assertFalse(captured.toString().contains(key))
        replies.forEach { assertFalse(it.toString().contains(key)) }
        assertFalse(assistant.toString().contains(key))
        assertFalse(AssistantCredentials.of(key).toString().contains(key))
    }

    private companion object {
        const val CANCEL_AFTER_MILLIS = 500L
    }
}
