package com.bbbjam.core.data.remote

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Fixtures
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The transport against local MockWebServers; no test reaches the network. */
class OkHttpAppsScriptTransportTest {
    private val server = MockWebServer()
    private val redirectTarget = MockWebServer()

    @Before
    fun setUp() {
        server.start()
        redirectTarget.start()
    }

    @After
    fun tearDown() {
        server.close()
        redirectTarget.close()
    }

    @Test
    fun `the request is a GET with the resource in the query`() = runTest {
        server.enqueue(MockResponse.Builder().body(Fixtures.sample("catalog-seed.json")).build())

        val result = transport(server.url("/macros/s/abc/exec").toString()).get(Resources.CATALOG)

        assertEquals(TransportResult.Body(Fixtures.sample("catalog-seed.json")), result)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/macros/s/abc/exec", request.url.encodedPath)
        assertEquals("catalog", request.url.queryParameter("resource"))
    }

    @Test
    fun `a 302 to another host is followed`() = runTest {
        val body = Fixtures.sample("catalog-edge.json")
        redirectTarget.enqueue(MockResponse.Builder().body(body).build())
        server.enqueue(
            MockResponse.Builder()
                .code(302)
                .addHeader("Location", redirectTarget.url("/macros/echo?user_content_key=k").toString())
                .build(),
        )

        val result = transport(server.url("/exec").toString()).get(Resources.CATALOG)

        assertEquals(TransportResult.Body(body), result)
        assertEquals(1, server.requestCount)
        assertEquals("k", redirectTarget.takeRequest().url.queryParameter("user_content_key"))
    }

    @Test
    fun `the body is read as UTF-8`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .body("""{"title":"Café Madrid","tempo":"rápido"}""")
                .build(),
        )

        val result = transport(server.url("/exec").toString()).get(Resources.CATALOG)

        assertEquals(TransportResult.Body("""{"title":"Café Madrid","tempo":"rápido"}"""), result)
    }

    @Test
    fun `a non-2xx status is an invalid response`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("<html>error</html>").build())

        val result = transport(server.url("/exec").toString()).get(Resources.CATALOG)

        assertEquals(TransportResult.Failed(DataFailure.InvalidResponse("HTTP 500")), result)
    }

    @Test
    fun `a refused connection is offline`() = runTest {
        val url = server.url("/exec").toString()
        server.close()

        val result = transport(url).get(Resources.CATALOG)

        assertEquals(TransportResult.Failed(DataFailure.Offline), result)
    }

    @Test
    fun `no URL means not configured and no request`() = runTest {
        val result = transport(null).get(Resources.CATALOG)

        assertEquals(TransportResult.Failed(DataFailure.NotConfigured), result)
        assertEquals(0, server.requestCount)
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a POST carries the JSON body to the base URL with no query`() = runTest {
        server.enqueue(MockResponse.Builder().body(OK).build())
        val json = CheckPassphraseRequest.encode(TEST_VALUE)

        val result = transport(server.url("/macros/s/abc/exec").toString()).post(json)

        assertEquals(TransportResult.Body(OK), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/macros/s/abc/exec", request.url.encodedPath)
        assertNull(request.url.encodedQuery)
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
        assertEquals(json, request.body?.utf8())
        assertFalse(request.url.toString().contains(TEST_VALUE))
    }

    @Test
    fun `a 302 after a POST is followed as a GET, and the passphrase is in no URL`() = runTest {
        redirectTarget.enqueue(MockResponse.Builder().body(OK).build())
        server.enqueue(
            MockResponse.Builder()
                .code(302)
                .addHeader("Location", redirectTarget.url("/macros/echo?user_content_key=k").toString())
                .build(),
        )

        val result = transport(server.url("/exec").toString()).post(CheckPassphraseRequest.encode(TEST_VALUE))

        assertEquals(TransportResult.Body(OK), result)
        val first = server.takeRequest()
        val followed = redirectTarget.takeRequest()
        assertEquals("POST", first.method)
        assertEquals("GET", followed.method)
        assertEquals("k", followed.url.queryParameter("user_content_key"))
        listOf(first, followed).forEach { assertFalse(it.url.toString().contains(TEST_VALUE)) }
    }

    @Test
    fun `a POST maps failures as a GET does`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("<html>error</html>").build())
        val url = server.url("/exec").toString()

        assertEquals(TransportResult.Failed(DataFailure.InvalidResponse("HTTP 500")), transport(url).post("{}"))
        assertEquals(TransportResult.Failed(DataFailure.NotConfigured), transport(null).post("{}"))
        assertEquals(1, server.requestCount)
        server.close()
        assertEquals(TransportResult.Failed(DataFailure.Offline), transport(url).post("{}"))
    }

    private fun transport(url: String?) = OkHttpAppsScriptTransport(OkHttpAppsScriptTransport.client(), url)

    private companion object {
        const val TEST_VALUE = "not-a-real-passphrase"
        const val OK = """{"schemaVersion":1,"ok":true}"""
    }
}
