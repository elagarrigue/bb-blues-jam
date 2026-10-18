package com.bbbjam.core.assistant

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The assistant's streaming path through the real Koog client, Ktor SSE and OkHttp, on a scripted gateway. */
class StreamingAssistantTest {
    private val server = MockWebServer()
    private val key = "test-key-0123456789"
    private val catalog = FakeCatalog(
        listOf(
            song("sweet-little-angel", "Sweet Little Angel", "B.B. King", "Bm"),
            song("pappo-blues", "Pappo's Blues", "Pappo", "E"),
            song("crossroads", "Crossroads", "Robert Johnson", "A"),
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

    private fun assistant(baseUrl: String = server.url("/").toString().trimEnd('/')): SetlistAssistant =
        DefaultSetlistAssistant(
            AssistantCredentials.of(key),
            catalog,
            FakePastJams(),
            fixedCalendar(),
            AssistantHttp.executorFactory(baseUrl, OkHttpClient()),
            AssistantStatusProbe(),
        )

    private fun body(request: RecordedRequest): JsonObject =
        Json.parseToJsonElement(requireNotNull(request.body).utf8()).jsonObject

    private fun messages(request: RecordedRequest): JsonArray = body(request).getValue("messages").jsonArray

    private fun role(message: JsonElement) = message.jsonObject.getValue("role").jsonPrimitive.content

    private fun roles(request: RecordedRequest) = messages(request).map(::role)

    private fun entry(id: String, reason: String = "Va bien acá") =
        """{"songId":"$id","title":"x","artist":"y","key":"C","reason":"$reason"}"""

    private fun answerText(vararg songs: String, message: String = "Listo") =
        """{"songs":[${songs.joinToString(",")}],"message":"$message","questions":[]}"""

    private fun ids(drafts: List<AssistantDraft>) = drafts.map { draft -> draft.songs.map { it.songId.value } }

    private suspend fun SetlistAssistant.sendCollecting(
        request: String,
        drafts: MutableList<AssistantDraft>,
    ): AssistantReply = startConversation().send(request) { drafts += it }

    @Test
    fun `spike - the gateway chunk shape streams a tool round and then the answer through Koog`() = runBlocking<Unit> {
        server.enqueue(
            SseFixtures.response(
                SseFixtures.toolRound(
                    "searchCatalog" to """{"query":""}""",
                    "pastJams" to """{"musician":"Juan"}""",
                ),
            ),
        )
        server.enqueue(
            SseFixtures.response(
                SseFixtures.answer(answerText(entry("crossroads"), entry("pappo-blues"), message = "Armé la lista")),
            ),
        )
        val drafts = CopyOnWriteArrayList<AssistantDraft>()

        val reply = assistant().sendCollecting("blues nacional", drafts)

        val proposal = reply as AssistantReply.Proposal
        assertEquals(listOf("crossroads", "pappo-blues"), proposal.songs.map { it.songId.value })
        assertEquals("Armé la lista", proposal.message)
        assertEquals(listOf(listOf("crossroads"), listOf("crossroads", "pappo-blues")), ids(drafts))

        val first = server.takeRequest()
        assertEquals("Bearer $key", first.headers["Authorization"])
        assertTrue(body(first).getValue("stream").jsonPrimitive.content.toBoolean())
        assertTrue("tools are sent while streaming", body(first).containsKey("tools"))
        assertFalse("no response_format", body(first).containsKey("response_format"))
        assertEquals(
            "the schema text appears once per request",
            1,
            messages(first).count { role(it) == "user" && it.toString().contains("songId") },
        )

        val second = server.takeRequest()
        val tools = messages(second).filter { role(it) == "tool" }
        assertEquals(2, tools.size)
        val results = tools.joinToString("\n") { it.toString() }
        assertTrue(results.contains("sweet-little-angel | Sweet Little Angel | B.B. King | Bm"))
        assertEquals(1, messages(second).count { role(it) == "user" && it.toString().contains("songId") })
        val assistantMessage = messages(second).first { role(it) == "assistant" }.toString()
        assertFalse("reasoning is not echoed back", assistantMessage.contains("reasoning"))
        assertTrue(assistantMessage.contains("searchCatalog") && assistantMessage.contains("pastJams"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `the drafts grow one song at a time and then the final reply holds the same songs`() = runBlocking<Unit> {
        val text = answerText(entry("crossroads"), entry("pappo-blues"), entry("sweet-little-angel"))
        server.enqueue(SseFixtures.response(SseFixtures.answer(text, size = 5)))
        val drafts = CopyOnWriteArrayList<AssistantDraft>()

        val reply = assistant().sendCollecting("tres temas", drafts) as AssistantReply.Proposal

        assertEquals(
            listOf(
                listOf("crossroads"),
                listOf("crossroads", "pappo-blues"),
                listOf("crossroads", "pappo-blues", "sweet-little-angel"),
            ),
            ids(drafts),
        )
        assertEquals(drafts.last().songs, reply.songs)
        val firstDraft = drafts.first().songs.single()
        assertEquals("Crossroads", firstDraft.title)
        assertEquals("Robert Johnson", firstDraft.artist)
        assertEquals("A", firstDraft.key.value)
        assertEquals("Va bien acá", firstDraft.reason)
    }

    @Test
    fun `unknown, malformed, repeated and undecodable songs never appear in a draft`() = runBlocking<Unit> {
        val text = answerText(
            entry("sweet-little-angel"),
            entry("made-up"),
            entry("NOT VALID"),
            entry("sweet-little-angel"),
            """{"reason":"sin id"}""",
            entry("crossroads"),
        )
        server.enqueue(SseFixtures.response(SseFixtures.answer(text, size = 9)))
        val drafts = CopyOnWriteArrayList<AssistantDraft>()

        val reply = assistant().sendCollecting("armame una jam", drafts)

        assertEquals(listOf(listOf("sweet-little-angel"), listOf("sweet-little-angel", "crossroads")), ids(drafts))
        // The final answer is the one that fails: a song without its id is not a valid proposal.
        assertEquals(AssistantReply.Failed(AssistantFailure.InvalidResponse), reply)
    }

    @Test
    fun `the final reply counts the discarded ids that no draft showed`() = runBlocking<Unit> {
        val text = answerText(entry("sweet-little-angel"), entry("made-up"), entry("sweet-little-angel"))
        server.enqueue(SseFixtures.response(SseFixtures.answer(text, size = 9)))
        val drafts = CopyOnWriteArrayList<AssistantDraft>()

        val reply = assistant().sendCollecting("armame una jam", drafts) as AssistantReply.Proposal

        assertEquals(listOf(listOf("sweet-little-angel")), ids(drafts))
        assertEquals(listOf("made-up", "sweet-little-angel"), reply.rejectedSongIds)
    }

    @Test
    fun `text streamed in a round that ends in tool calls is discarded and the draft resets`() = runBlocking<Unit> {
        val partial = """{"songs":[${entry("pappo-blues")},{"songId":"cro"""
        server.enqueue(
            SseFixtures.response(
                SseFixtures.toolRound("searchCatalog" to """{"query":""}""", text = SseFixtures.pieces(partial, 13)),
            ),
        )
        server.enqueue(SseFixtures.response(SseFixtures.answer(answerText(entry("crossroads")), size = 8)))
        val drafts = CopyOnWriteArrayList<AssistantDraft>()

        val reply = assistant().sendCollecting("armame una jam", drafts) as AssistantReply.Proposal

        assertEquals(listOf(listOf("pappo-blues"), emptyList(), listOf("crossroads")), ids(drafts))
        assertEquals(listOf("crossroads"), reply.songs.map { it.songId.value })
    }

    @Test
    fun `a stream that ends without its end frame after two songs is incomplete and the drafts held two`() =
        runBlocking<Unit> {
            val text = answerText(entry("crossroads"), entry("pappo-blues"), entry("sweet-little-angel"))
            val cut = text.indexOf("sweet-little-angel") + 4
            val events = SseFixtures.answer(text.take(cut), size = 6, end = false)
            server.enqueue(SseFixtures.response(events, done = false))
            val drafts = CopyOnWriteArrayList<AssistantDraft>()

            val reply = assistant().sendCollecting("tres temas", drafts)

            assertEquals(AssistantReply.Failed(AssistantFailure.Incomplete), reply)
            assertEquals(listOf(listOf("crossroads"), listOf("crossroads", "pappo-blues")), ids(drafts))
        }

    @Test
    fun `a connection dropped mid body after songs fails as a lost connection or an incomplete stream`() =
        runBlocking<Unit> {
            val text = answerText(entry("crossroads"), entry("pappo-blues"), entry("sweet-little-angel"))
            val cut = text.indexOf("sweet-little-angel") + 4
            val events = SseFixtures.answer(text.take(cut), size = 6, end = false)
            StallingGateway(SseFixtures.body(events, done = false), dropAfterSending = true).use { gateway ->
                val drafts = CopyOnWriteArrayList<AssistantDraft>()

                val reply = assistant(gateway.baseUrl).sendCollecting("tres temas", drafts)

                val failure = (reply as AssistantReply.Failed).failure
                assertTrue(
                    "was $failure",
                    failure == AssistantFailure.Offline || failure == AssistantFailure.Incomplete,
                )
                // Koog may drop the buffered tail of a connection that failed: only a prefix is guaranteed.
                assertTrue(ids(drafts).size <= 2)
            }
        }

    @Test
    fun `an HTTP error before the stream is unavailable and shows no draft`() = runBlocking<Unit> {
        server.enqueue(MockResponse.Builder().code(502).body("bad gateway").build())
        val drafts = CopyOnWriteArrayList<AssistantDraft>()

        val reply = assistant().sendCollecting("armame una jam", drafts)

        assertEquals(AssistantReply.Failed(AssistantFailure.Unavailable(502)), reply)
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `invalid final JSON after streamed songs is an invalid response and the history stays clean`() =
        runBlocking<Unit> {
            val broken = """{"songs":[${entry("crossroads")}],"message":"Listo"}"""
            server.enqueue(SseFixtures.response(SseFixtures.answer(broken, size = 8)))
            server.enqueue(SseFixtures.response(SseFixtures.answer(answerText(entry("pappo-blues")))))
            val conversation = assistant().startConversation()
            val drafts = CopyOnWriteArrayList<AssistantDraft>()

            val failed = conversation.send("primero") { drafts += it }
            conversation.send("segundo")

            assertEquals(AssistantReply.Failed(AssistantFailure.InvalidResponse), failed)
            assertEquals(listOf(listOf("crossroads")), ids(drafts))
            server.takeRequest()
            assertEquals(listOf("system", "user", "user"), roles(server.takeRequest()))
        }

    @Test
    fun `a model that never stops calling tools ends as too many steps after exactly the budget`() = runBlocking<Unit> {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                SseFixtures.response(SseFixtures.toolRound("searchCatalog" to """{"query":""}"""))
        }

        val reply = assistant().startConversation().send("armame una jam")

        assertEquals(AssistantReply.Failed(AssistantFailure.TooManySteps), reply)
        assertEquals(AssistantModel.MAX_AGENT_ITERATIONS, server.requestCount)
    }

    @Test
    fun `no draft arrives after send returns`() = runBlocking<Unit> {
        server.enqueue(SseFixtures.response(SseFixtures.answer(answerText(entry("crossroads")), size = 4)))
        val drafts = CopyOnWriteArrayList<AssistantDraft>()

        assistant().sendCollecting("uno", drafts)
        val sizeAtReturn = drafts.size
        delay(SETTLE_MILLIS)

        assertEquals(sizeAtReturn, drafts.size)
    }

    @Test
    fun `cancelling mid stream throws CancellationException, stops the drafts and closes the connection`() =
        runBlocking<Unit> {
            val text = answerText(entry("crossroads"), entry("pappo-blues"), entry("sweet-little-angel"))
            val cut = text.indexOf("sweet-little-angel") + 4
            val events = SseFixtures.answer(text.take(cut), size = 6, end = false)
            StallingGateway(SseFixtures.body(events, done = false)).use { gateway ->
                val drafts = CopyOnWriteArrayList<AssistantDraft>()
                val outcome = AtomicReference<Result<AssistantReply>?>(null)
                val conversation = assistant(gateway.baseUrl).startConversation()
                val pending = async(Dispatchers.Default) {
                    val result = runCatching { conversation.send("tres temas") { drafts += it } }
                    outcome.set(result)
                    result.getOrThrow()
                }
                assertTrue("the gateway sent its songs", gateway.awaitSent())
                waitFor { drafts.size >= 2 }

                pending.cancel()
                pending.join()
                val sizeAtCancel = drafts.size
                delay(SETTLE_MILLIS)

                val recorded = requireNotNull(outcome.get()) { "send did not finish" }
                assertTrue("was ${recorded.getOrNull()}", recorded.exceptionOrNull() is CancellationException)
                assertEquals(sizeAtCancel, drafts.size)
                assertEquals(listOf(listOf("crossroads"), listOf("crossroads", "pappo-blues")), ids(drafts))
                assertTrue("the server saw the connection close", gateway.awaitClosed())
            }
        }

    @Test
    fun `the key appears in no output, draft or reply while streaming`() = runBlocking<Unit> {
        server.enqueue(SseFixtures.response(SseFixtures.answer(answerText(entry("crossroads")), size = 6)))
        server.enqueue(MockResponse.Builder().code(401).body("bad key").build())
        val originalOut = System.out
        val originalErr = System.err
        val captured = ByteArrayOutputStream()
        val drafts = CopyOnWriteArrayList<AssistantDraft>()
        val replies = try {
            System.setOut(PrintStream(captured, true))
            System.setErr(PrintStream(captured, true))
            val conversation = assistant().startConversation()
            listOf(conversation.send("uno") { drafts += it }, conversation.send("dos") { drafts += it })
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        assertFalse(captured.toString().contains(key))
        replies.forEach { assertFalse(it.toString().contains(key)) }
        drafts.forEach { assertFalse(it.toString().contains(key)) }
    }

    private suspend fun waitFor(condition: () -> Boolean) {
        repeat(WAIT_ATTEMPTS) {
            if (condition()) return
            delay(WAIT_STEP_MILLIS)
        }
    }

    /**
     * A raw one-connection gateway that sends [sse] as a chunked 200 response and then stalls, so a test can
     * cancel mid-stream and see, on the server side, whether the client closed the connection; or, with
     * [dropAfterSending], closes the socket without ending the chunked body.
     */
    private class StallingGateway(private val sse: String, private val dropAfterSending: Boolean = false) :
        AutoCloseable {
        private val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val sent = CountDownLatch(1)
        private val closed = CountDownLatch(1)
        private val thread = Thread { serve() }.apply {
            isDaemon = true
            start()
        }

        val baseUrl: String
            get() = "http://127.0.0.1:${socket.localPort}"

        fun awaitSent(): Boolean = sent.await(AWAIT_SECONDS, TimeUnit.SECONDS)

        fun awaitClosed(): Boolean = closed.await(AWAIT_SECONDS, TimeUnit.SECONDS)

        private fun serve() {
            try {
                socket.accept().use(::respond)
            } catch (_: IOException) {
                closed.countDown()
            }
        }

        private fun respond(client: Socket) {
            val input = client.getInputStream()
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val next = input.read()
                if (next == -1) return
                head.append(next.toChar())
            }
            val bytes = sse.toByteArray(Charsets.UTF_8)
            val out = client.getOutputStream()
            out.write(
                "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n"
                    .toByteArray(),
            )
            out.write((Integer.toHexString(bytes.size) + "\r\n").toByteArray())
            out.write(bytes)
            out.write("\r\n".toByteArray())
            out.flush()
            sent.countDown()
            if (dropAfterSending) {
                closed.countDown()
                return
            }
            val buffer = ByteArray(BUFFER_SIZE)
            try {
                while (input.read(buffer) != -1) {
                    // Drain the request body; the stream ends when the client closes the connection.
                }
            } catch (_: IOException) {
                // A reset is a close too.
            }
            closed.countDown()
        }

        override fun close() {
            socket.close()
            thread.interrupt()
        }

        private companion object {
            const val AWAIT_SECONDS = 5L
            const val BUFFER_SIZE = 1024
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 300L
        const val WAIT_ATTEMPTS = 100
        const val WAIT_STEP_MILLIS = 50L
    }
}
