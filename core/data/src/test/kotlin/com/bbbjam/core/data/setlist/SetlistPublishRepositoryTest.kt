package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogSongEntity
import com.bbbjam.core.data.cache.JamEntity
import com.bbbjam.core.data.cache.JamRows
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.JamSongEntity
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.SongId
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SetlistPublishRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val catalogDao = database.catalogDao()
    private val post = FakePost()

    @Test
    fun `publish stays draft while sending then mirrors only confirmed status`() = runTest {
        val repository = seeded()
        post.answers += body("""{"schemaVersion":1,"ok":true,"alreadyPublished":false}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val publish = async { repository.publishSetlist(DATE) }
        val sending = repository.observePublishes().first { it.isNotEmpty() }.single()
        assertEquals(SetlistPublish.State.Sending, sending.state)
        post.posted.first { it == 1 }
        assertEquals("DRAFT", jamsDao.observeJams().first().single().jam.status)
        gate.complete(Unit)

        assertEquals(PublishOutcome.Published(alreadyPublished = false), publish.await())
        assertEquals("PUBLISHED", jamsDao.observeJams().first().single().jam.status)
        assertEquals(emptyList<SetlistPublish>(), repository.observePublishes().first())
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("publishSetlist", request.getValue("action").jsonPrimitive.content)
        assertEquals(DATE.toString(), request.getValue("date").jsonPrimitive.content)
    }

    @Test
    fun `failure keeps draft observable and logs one safe line`() = runTest {
        val log = mutableListOf<String>()
        val repository = seeded(failureLog = WriteFailureLog(log::add))
        val before = jamsDao.observeJams().first()
        post.answers += body("""{"schemaVersion":1,"error":{"code":"empty_setlist","message":"m"}}""")

        assertEquals(
            PublishOutcome.NotPublished(WriteOutcome.Rejected("empty_setlist")),
            repository.publishSetlist(DATE),
        )
        assertEquals(before, jamsDao.observeJams().first())
        assertEquals(
            WriteOutcome.Rejected("empty_setlist"),
            (repository.observePublishes().first().single().state as SetlistPublish.State.Failed).reason,
        )
        assertEquals(listOf("publishSetlist failed: date=2026-10-31 outcome=Rejected(empty_setlist)"), log)
        repository.dismiss(repository.observePublishes().first().single().id)
        assertEquals(emptyList<SetlistPublish>(), repository.observePublishes().first())
    }

    @Test
    fun `publish queues behind add and malformed success is never accepted`() = runTest {
        val repository = seeded()
        post.answers +=
            body("""{"schemaVersion":1,"ok":true,"position":2,"title":"Crossroads","artist":"Eric Clapton"}""")
        post.answers += body("""{"schemaVersion":1,"ok":true,"alreadyPublished":true}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }
        val add = async { repository.addSong(DATE, SongId("crossroads"), com.bbbjam.core.model.Key("E")) }
        post.posted.first { it == 1 }
        val publish = async { repository.publishSetlist(DATE) }
        testScheduler.runCurrent()
        assertEquals(1, post.bodies.size)
        gate.complete(Unit)
        assertEquals(AddSongOutcome.Added(2), add.await())
        assertEquals(PublishOutcome.Published(alreadyPublished = true), publish.await())
        assertEquals(listOf("addSong", "publishSetlist"), post.bodies.map(::actionOf))

        post.answers += body("""{"schemaVersion":1,"ok":true}""")
        assertEquals(PublishOutcome.NotPublished(WriteOutcome.Unavailable), repository.publishSetlist(DATE))
    }

    @Test
    fun `caller cancellation does not cancel the server write`() = runTest {
        val repository = seeded()
        post.answers += body("""{"schemaVersion":1,"ok":true,"alreadyPublished":false}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }
        val caller = launch(start = CoroutineStart.UNDISPATCHED) { repository.publishSetlist(DATE) }
        repository.observePublishes().first { it.isNotEmpty() }
        caller.cancel()
        gate.complete(Unit)

        jamsDao.observeJams().first { it.single().jam.status == "PUBLISHED" }
        assertTrue(caller.isCancelled)
        assertEquals(1, post.bodies.size)
        assertEquals(emptyList<SetlistPublish>(), repository.observePublishes().first())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun TestScope.seeded(failureLog: WriteFailureLog = WriteFailureLog { }): DefaultSetlistRepository {
        catalogDao.replaceCatalog(
            listOf(
                CatalogSongEntity("crossroads", 1, "Crossroads", "Eric Clapton", "A", null, null, emptyList(), null),
            ),
            SyncStateEntity("catalog", 1, 1, null),
        )
        val date = DATE.toString()
        jamsDao.replaceJams(
            JamRows(
                listOf(JamEntity(date, 1, "21:00", "La Macanuda", "DRAFT", "AVAILABLE", null, 0)),
                listOf(JamSongEntity(date, 1, "red-house", "Red House", "Jimi Hendrix", "Bb")),
                Lineup.DEFAULT_INSTRUMENTS.mapIndexed { column, instrument ->
                    JamSlotEntity(date, 1, column, instrument.name, null)
                },
                emptyList(),
            ),
            SyncStateEntity("jams", 1, 1, null),
        )
        val store = AdminCredentialStore(
            AdminCredentialStore.dataStore(backgroundScope) {
                File(folder.root, "${AdminCredentialStore.FILE_NAME}.preferences_pb")
            },
        )
        store.save("not-a-real-passphrase")
        return DefaultSetlistRepository(
            AdminWriter(post, store),
            database.setlistDao(),
            catalogDao,
            DataScope(backgroundScope),
            failureLog = failureLog,
        )
    }

    private fun actionOf(json: String) =
        Json.parseToJsonElement(json).jsonObject.getValue("action").jsonPrimitive.content

    private class FakePost : AppsScriptPostTransport {
        val answers = ArrayDeque<TransportResult>()
        val bodies = mutableListOf<String>()
        val posted = MutableStateFlow(0)
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun post(json: String): TransportResult {
            bodies += json
            posted.value = bodies.size
            gate?.await()
            return answers.removeFirst()
        }
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 10, 31)

        fun body(text: String) = TransportResult.Body(text)
    }
}
