package com.bbbjam.core.data.admin

import app.cash.turbine.test
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.TransportResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The session over a fake POST transport and a real DataStore file on the JVM. */
class DefaultAdminSessionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FakePost(private val answer: TransportResult) : AppsScriptPostTransport {
        val bodies = mutableListOf<String>()

        override suspend fun post(json: String): TransportResult {
            bodies += json
            return answer
        }
    }

    private fun withSession(
        answer: TransportResult,
        block: suspend (session: DefaultAdminSession, post: FakePost, harness: StoreHarness) -> Unit,
    ) {
        val post = FakePost(answer)
        StoreHarness(folder.root).use { harness ->
            runBlocking { block(DefaultAdminSession(post, harness.store), post, harness) }
        }
    }

    @Test
    fun `ok stores the trimmed passphrase and is Success`() = withSession(body(OK)) { session, post, harness ->
        assertEquals(LoginOutcome.Success, session.logIn("  $TEST_VALUE \n"))

        assertEquals(1, post.bodies.size)
        assertEquals(TEST_VALUE, harness.store.passphrase())
        assertTrue(session.observeIsAdmin().first())
    }

    @Test
    fun `the request is the checkPassphrase action with the trimmed value`() =
        withSession(body(OK)) { session, post, _ ->
            session.logIn("  $TEST_VALUE  ")

            val json = Json.parseToJsonElement(post.bodies.single()).jsonObject
            assertEquals(setOf("action", "passphrase"), json.keys)
            assertEquals("checkPassphrase", json.getValue("action").jsonPrimitive.content)
            assertEquals(TEST_VALUE, json.getValue("passphrase").jsonPrimitive.content)
        }

    @Test
    fun `quotes and backslashes are escaped, not concatenated`() = withSession(body(OK)) { session, post, harness ->
        val tricky = "a\"b\\c\",\"action\":\"other"

        session.logIn(tricky)

        val json = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals(setOf("action", "passphrase"), json.keys)
        assertEquals("checkPassphrase", json.getValue("action").jsonPrimitive.content)
        assertEquals(tricky, json.getValue("passphrase").jsonPrimitive.content)
        assertEquals(tricky, harness.store.passphrase())
    }

    @Test
    fun `invalid_passphrase is WrongPassphrase and stores nothing`() =
        withSession(body(error("invalid_passphrase"))) { session, _, harness ->
            assertEquals(LoginOutcome.WrongPassphrase, session.logIn("definitely-wrong"))
            assertNull(harness.store.passphrase())
            assertFalse(session.observeIsAdmin().first())
        }

    @Test
    fun `offline is Offline and stores nothing`() =
        withSession(TransportResult.Failed(DataFailure.Offline)) { session, _, harness ->
            assertEquals(LoginOutcome.Offline, session.logIn(TEST_VALUE))
            assertNull(harness.store.passphrase())
        }

    @Test
    fun `every other answer is Unavailable and stores nothing`() {
        val answers = listOf(
            TransportResult.Failed(DataFailure.NotConfigured),
            TransportResult.Failed(DataFailure.InvalidResponse("HTTP 500")),
            body(error("passphrase_not_set")),
            body(error("unknown_action")),
            body(error("internal_error")),
            body("<!DOCTYPE html><html><body>Script function not found: doPost</body></html>"),
            body("""{"schemaVersion":1}"""),
            body("""{"schemaVersion":1,"ok":false}"""),
            body("""{"schemaVersion":1,"ok":"true"}"""),
            body("""{"schemaVersion":2,"ok":true}"""),
        )
        answers.forEach { answer ->
            withSession(answer) { session, _, harness ->
                assertEquals(answer.toString(), LoginOutcome.Unavailable, session.logIn(TEST_VALUE))
                assertNull(answer.toString(), harness.store.passphrase())
            }
        }
    }

    @Test
    fun `a blank passphrase is WrongPassphrase with no request`() = withSession(body(OK)) { session, post, harness ->
        listOf("", "   ", "\n\t").forEach { blank ->
            assertEquals(LoginOutcome.WrongPassphrase, session.logIn(blank))
        }
        assertTrue(post.bodies.isEmpty())
        assertNull(harness.store.passphrase())
    }

    @Test
    fun `observeIsAdmin goes false, true on login, false on logout`() = withSession(body(OK)) { session, _, harness ->
        session.observeIsAdmin().test {
            assertFalse(awaitItem())
            session.logIn(TEST_VALUE)
            assertTrue(awaitItem())
            session.logOut()
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(harness.store.passphrase())
    }

    private companion object {
        const val TEST_VALUE = "not-a-real-passphrase"
        const val OK = """{"schemaVersion":1,"ok":true}"""

        fun body(text: String) = TransportResult.Body(text)

        fun error(code: String) = """{"schemaVersion":1,"error":{"code":"$code","message":"m"}}"""
    }
}
