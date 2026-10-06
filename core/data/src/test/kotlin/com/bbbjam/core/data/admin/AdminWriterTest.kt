package com.bbbjam.core.data.admin

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.TransportResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The shared write path over a fake POST transport and a real DataStore file on the JVM. */
class AdminWriterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FakePost(private val answer: TransportResult) : AppsScriptPostTransport {
        val bodies = mutableListOf<String>()

        override suspend fun post(json: String): TransportResult {
            bodies += json
            return answer
        }
    }

    /** A fresh store (holding [stored], when not null) and a writer over it, for one [block]. */
    private fun withWriter(
        answer: TransportResult,
        stored: String? = TEST_VALUE,
        block: suspend (writer: AdminWriter, post: FakePost, harness: StoreHarness) -> Unit,
    ) {
        val post = FakePost(answer)
        StoreHarness(folder.newFolder()).use { harness ->
            runBlocking {
                if (stored != null) harness.store.save(stored)
                block(AdminWriter(post, harness.store), post, harness)
            }
        }
    }

    private fun sentPassphrases(post: FakePost): List<String> = post.bodies.map {
        Json.parseToJsonElement(it).jsonObject.getValue("passphrase").jsonPrimitive.content
    }

    @Test
    fun `ok is Done`() = withWriter(body(OK)) { writer, post, _ ->
        assertEquals(WriteOutcome.Done, writer.write(ACTION))
        assertEquals(1, post.bodies.size)
    }

    @Test
    fun `the body is the action, the stored passphrase and the fields, nothing else`() =
        withWriter(body(OK)) { writer, post, _ ->
            writer.write(ACTION, mapOf("position" to JsonPrimitive(3), "key" to JsonPrimitive("Bm")))

            val json = Json.parseToJsonElement(post.bodies.single()).jsonObject
            assertEquals(setOf("action", "passphrase", "position", "key"), json.keys)
            assertEquals(ACTION, json.getValue("action").jsonPrimitive.content)
            assertEquals(TEST_VALUE, json.getValue("passphrase").jsonPrimitive.content)
            assertEquals("3", json.getValue("position").jsonPrimitive.content)
            assertEquals("Bm", json.getValue("key").jsonPrimitive.content)
        }

    @Test
    fun `quotes and backslashes in the passphrase are escaped, not concatenated`() {
        val tricky = "a\"b\\c\",\"action\":\"other"
        withWriter(body(OK), stored = tricky) { writer, post, _ ->
            writer.write(ACTION)

            val json = Json.parseToJsonElement(post.bodies.single()).jsonObject
            assertEquals(setOf("action", "passphrase"), json.keys)
            assertEquals(ACTION, json.getValue("action").jsonPrimitive.content)
            assertEquals(tricky, json.getValue("passphrase").jsonPrimitive.content)
        }
    }

    @Test
    fun `a field named action or passphrase is refused before anything is sent`() =
        withWriter(body(OK)) { writer, post, _ ->
            listOf("action", "passphrase").forEach { reserved ->
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { writer.write(ACTION, mapOf(reserved to JsonPrimitive("x"))) }
                }
            }
            assertTrue(post.bodies.isEmpty())
        }

    @Test
    fun `with nothing stored it is AccessRefused and sends no request`() =
        withWriter(body(OK), stored = null) { writer, post, harness ->
            assertEquals(WriteOutcome.AccessRefused, writer.write(ACTION))
            assertTrue(post.bodies.isEmpty())
            assertNull(harness.store.passphrase())
        }

    @Test
    fun `a refused write keeps the stored passphrase and admin mode`() {
        listOf("invalid_passphrase", "passphrase_not_set").forEach { code ->
            withWriter(body(error(code))) { writer, post, harness ->
                assertEquals(code, WriteOutcome.AccessRefused, writer.write(ACTION))

                assertEquals(code, TEST_VALUE, harness.store.passphrase())
                assertTrue(code, harness.store.observeIsAdmin().first())
                assertTrue(code, DefaultAdminSession(post, harness.store).observeIsAdmin().first())

                assertEquals(code, WriteOutcome.AccessRefused, writer.write(ACTION))
                assertEquals(code, listOf(TEST_VALUE, TEST_VALUE), sentPassphrases(post))
            }
        }
    }

    @Test
    fun `offline is Offline`() = withWriter(TransportResult.Failed(DataFailure.Offline)) { writer, _, _ ->
        assertEquals(WriteOutcome.Offline, writer.write(ACTION))
    }

    @Test
    fun `rate_limited, busy, unknown_action, no URL, an HTML page or any invalid answer is Unavailable`() {
        val answers = listOf(
            body(error("rate_limited")),
            body(error("busy")),
            body(error("unknown_action")),
            TransportResult.Failed(DataFailure.NotConfigured),
            TransportResult.Failed(DataFailure.InvalidResponse("HTTP 500")),
            body("<!DOCTYPE html><html><body>Script function not found: doPost</body></html>"),
            body("""{"schemaVersion":1}"""),
            body("""{"schemaVersion":1,"ok":false}"""),
            body("""{"schemaVersion":1,"ok":"true"}"""),
            body("""{"schemaVersion":2,"ok":true}"""),
            body("""{"schemaVersion":1,"error":{"message":"no code"}}"""),
        )
        answers.forEach { answer ->
            withWriter(answer) { writer, _, _ ->
                assertEquals(answer.toString(), WriteOutcome.Unavailable, writer.write(ACTION))
            }
        }
    }

    @Test
    fun `any other service code is Rejected with that code`() {
        listOf("internal_error", "invalid_field", "missing_tab").forEach { code ->
            withWriter(body(error(code))) { writer, _, _ ->
                assertEquals(WriteOutcome.Rejected(code), writer.write(ACTION))
            }
        }
    }

    @Test
    fun `no answer ever changes the store`() {
        val answers = listOf(
            body(OK),
            body(error("invalid_passphrase")),
            body(error("passphrase_not_set")),
            body(error("rate_limited")),
            body(error("internal_error")),
            TransportResult.Failed(DataFailure.Offline),
        )
        answers.forEach { answer ->
            withWriter(answer) { writer, _, harness ->
                writer.write(ACTION)
                assertEquals(answer.toString(), TEST_VALUE, harness.store.passphrase())
            }
            withWriter(answer, stored = null) { writer, _, harness ->
                writer.write(ACTION)
                assertNull(answer.toString(), harness.store.passphrase())
            }
        }
    }

    @Test
    fun `send keeps an ok answer's payload and refuses everything else as write maps it`() {
        val payload = """{"schemaVersion":1,"ok":true,"position":4,"title":"Crossroads"}"""
        withWriter(body(payload)) { writer, post, harness ->
            val answer = writer.send(ACTION, mapOf("songId" to JsonPrimitive("crossroads")))

            val ok = answer as AdminAnswer.Ok
            assertEquals(Json.parseToJsonElement(payload), ok.body)
            assertEquals(1, post.bodies.size)
            assertEquals(TEST_VALUE, harness.store.passphrase())
        }
        val refused = listOf(
            body(error("invalid_passphrase")) to WriteOutcome.AccessRefused,
            body(error("busy")) to WriteOutcome.Unavailable,
            body(error("unknown_song")) to WriteOutcome.Rejected("unknown_song"),
            body("""{"schemaVersion":1,"ok":false}""") to WriteOutcome.Unavailable,
            TransportResult.Failed(DataFailure.Offline) to WriteOutcome.Offline,
        )
        refused.forEach { (answer, outcome) ->
            withWriter(answer) { writer, _, harness ->
                val sent = writer.send(ACTION) as AdminAnswer.Refused
                assertEquals(answer.toString(), outcome, sent.outcome)
                assertEquals(answer.toString(), outcome, writer.write(ACTION))
                assertTrue(answer.toString(), sent.failure != null)
                assertEquals(answer.toString(), TEST_VALUE, harness.store.passphrase())
            }
        }
        withWriter(body(payload), stored = null) { writer, post, _ ->
            assertEquals(AdminAnswer.Refused(WriteOutcome.AccessRefused, failure = null), writer.send(ACTION))
            assertTrue(post.bodies.isEmpty())
        }
    }

    private companion object {
        const val ACTION = "checkWriteAccess"
        const val TEST_VALUE = "not-a-real-passphrase"
        const val OK = """{"schemaVersion":1,"ok":true}"""

        fun body(text: String) = TransportResult.Body(text)

        fun error(code: String) = """{"schemaVersion":1,"error":{"code":"$code","message":"m"}}"""
    }
}
