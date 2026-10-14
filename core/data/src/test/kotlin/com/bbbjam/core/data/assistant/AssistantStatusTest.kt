package com.bbbjam.core.data.assistant

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** `checkStatus`: one plain read of the model list against a scripted local gateway; no test reaches the network. */
class AssistantStatusTest {
    private val server = MockWebServer()
    private val key = "test-key-0123456789"

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun assistant(raw: String = key, baseUrl: String = server.url("/").toString().trimEnd('/')) =
        DefaultSetlistAssistant(
            AssistantCredentials.of(raw),
            FakeCatalog(emptyList()),
            FakeJams(emptyList()),
            AssistantHttp.executorFactory(baseUrl, OkHttpClient()),
            AssistantStatusProbe(baseUrl, OkHttpClient()),
        )

    @Test
    fun `a 200 is Available and the request is a plain GET of the model list with the Bearer key`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("{\"data\":[]}").build())
        assertEquals(AssistantStatus.Available, assistant().checkStatus())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/models", request.url.encodedPath)
        assertEquals("Bearer $key", request.headers["Authorization"])
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun `a 502 with an empty body is ServiceDown`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(502).build())
        assertEquals(AssistantStatus.ServiceDown, assistant().checkStatus())
    }

    @Test
    fun `any other server error or unexpected status is ServiceDown`() = runBlocking {
        listOf(500, 503, 404, 429).forEach { code ->
            server.enqueue(MockResponse.Builder().code(code).build())
            assertEquals("HTTP $code", AssistantStatus.ServiceDown, assistant().checkStatus())
        }
    }

    @Test
    fun `401 and 403 are Unauthorized`() = runBlocking {
        listOf(401, 403).forEach { code ->
            server.enqueue(MockResponse.Builder().code(code).build())
            assertEquals("HTTP $code", AssistantStatus.Unauthorized, assistant().checkStatus())
        }
    }

    @Test
    fun `a dropped connection is Offline`() = runBlocking {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        assertEquals(AssistantStatus.Offline, assistant().checkStatus())
    }

    @Test
    fun `a gateway that is not listening is Offline`() = runBlocking {
        val closed = server.url("/").toString().trimEnd('/')
        server.close()
        assertEquals(AssistantStatus.Offline, assistant(baseUrl = closed).checkStatus())
    }

    @Test
    fun `a missing key is NotConfigured and makes no request`() = runBlocking {
        assertEquals(AssistantStatus.NotConfigured, assistant(raw = "  ").checkStatus())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cancelling the caller cancels the call and rethrows`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).headersDelay(5, java.util.concurrent.TimeUnit.SECONDS).build())
        val check = async(start = CoroutineStart.UNDISPATCHED) { assistant().checkStatus() }
        delay(200)
        check.cancel()
        var cancelled = false
        try {
            check.await()
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }
}
