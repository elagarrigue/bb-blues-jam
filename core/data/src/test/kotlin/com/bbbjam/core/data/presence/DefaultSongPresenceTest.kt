package com.bbbjam.core.data.presence

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.StoreHarness
import com.bbbjam.core.data.remote.FakePost
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.SongId
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The presence client over a fake POST transport and real DataStore files on the JVM. */
class DefaultSongPresenceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val date = LocalDate.of(2026, 11, 21)
    private val song = SongId("hoochie-coochie-man")

    private fun withPresence(
        answer: TransportResult,
        stored: String? = "Blues-Pass 42",
        block: suspend (DefaultSongPresence, FakePost, DataScope) -> Unit,
    ) {
        val post = FakePost(answer)
        val dataScope = DataScope(CoroutineScope(Dispatchers.IO + SupervisorJob()))
        val holderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        StoreHarness(folder.newFolder()).use { harness ->
            try {
                runBlocking {
                    if (stored != null) harness.store.save(stored)
                    val file = File(folder.newFolder(), "${PresenceHolderStore.FILE_NAME}.preferences_pb")
                    val holders = PresenceHolderStore(
                        AdminCredentialStore.dataStore(holderScope) { file },
                    )
                    block(DefaultSongPresence(AdminWriter(post, harness.store), holders, dataScope), post, dataScope)
                }
            } finally {
                holderScope.cancel()
                dataScope.cancel()
            }
        }
    }

    private fun ok(others: String) = TransportResult.Body("""{"schemaVersion":1,"ok":true,"others":$others}""")

    @Test
    fun `announce sends date, songId and holder and no leaving flag`() = withPresence(ok("0")) { presence, post, _ ->
        presence.announce(date, song)
        val json = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals(setOf("action", "passphrase", "date", "songId", "holder"), json.keys)
        assertEquals("announcePresence", json.getValue("action").jsonPrimitive.content)
        assertEquals("2026-11-21", json.getValue("date").jsonPrimitive.content)
        assertEquals("hoochie-coochie-man", json.getValue("songId").jsonPrimitive.content)
    }

    @Test
    fun `others zero, two, missing, negative and non-int map to Alone, OthersHere and Unknown`() {
        val expected = listOf(
            "0" to PresenceAnswer.Alone,
            "2" to PresenceAnswer.OthersHere,
            "-1" to PresenceAnswer.Unknown,
            "\"2\"" to PresenceAnswer.Unknown,
            "1.5" to PresenceAnswer.Unknown,
            "null" to PresenceAnswer.Unknown,
        )
        for ((others, answer) in expected) {
            withPresence(ok(others)) { presence, _, _ -> assertEquals(others, answer, presence.announce(date, song)) }
        }
        withPresence(TransportResult.Body("""{"schemaVersion":1,"ok":true}""")) { presence, _, _ ->
            assertEquals(PresenceAnswer.Unknown, presence.announce(date, song))
        }
    }

    @Test
    fun `a refusal, an offline failure and a missing passphrase are Unknown`() {
        val refused = TransportResult.Body("""{"schemaVersion":1,"error":{"code":"unknown_action","message":"m"}}""")
        withPresence(refused) { presence, _, _ -> assertEquals(PresenceAnswer.Unknown, presence.announce(date, song)) }
        withPresence(TransportResult.Failed(DataFailure.Offline)) { presence, _, _ ->
            assertEquals(PresenceAnswer.Unknown, presence.announce(date, song))
        }
        withPresence(ok("1"), stored = null) { presence, post, _ ->
            assertEquals(PresenceAnswer.Unknown, presence.announce(date, song))
            assertTrue(post.bodies.isEmpty())
        }
    }

    @Test
    fun `the same install announces under the same holder`() = withPresence(ok("0")) { presence, post, _ ->
        presence.announce(date, song)
        presence.announce(date, song)
        val holders = post.bodies.map {
            Json.parseToJsonElement(it).jsonObject.getValue("holder").jsonPrimitive.content
        }
        assertEquals(1, holders.toSet().size)
    }

    @Test
    fun `leave sends leaving true in the data scope and survives a cancelled caller`() =
        withPresence(ok("0")) { presence, post, dataScope ->
            val caller = Job()
            val callerScope = CoroutineScope(Dispatchers.IO + caller)
            caller.cancel()
            presence.leave(date, song)
            callerScope.cancel()
            withTimeout(5_000) {
                while (post.bodies.isEmpty()) kotlinx.coroutines.yield()
            }
            val json = Json.parseToJsonElement(post.bodies.single()).jsonObject
            assertEquals("true", json.getValue("leaving").jsonPrimitive.content)
            assertTrue(dataScope.coroutineContext[Job]!!.isActive)
        }
}
