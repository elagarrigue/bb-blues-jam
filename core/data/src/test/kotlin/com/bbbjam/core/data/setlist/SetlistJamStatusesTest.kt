package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.JamEntity
import com.bbbjam.core.data.cache.JamRows
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.data.setlist.engine.SetlistMutationQueue
import com.bbbjam.core.data.setlist.engine.WriteFailureLog
import com.bbbjam.core.data.setlist.jamstatus.JamStatusChange
import com.bbbjam.core.data.setlist.jamstatus.SetJamStatusOutcome
import com.bbbjam.core.model.JamStatus
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
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

class SetlistJamStatusesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val post = QueuedPostTransport()

    @Test
    fun `a confirmed status is sent as sheet text, mirrored after the echo and the entry removed`() = runTest {
        val repository = seeded("PUBLISHED")
        post.answers += echo("BORRADOR", changed = true)
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val change = async { repository.setJamStatus(DATE, JamStatus.DRAFT) }
        val sending = repository.observeJamStatusChanges().first { it.isNotEmpty() }.single()
        assertEquals(JamStatusChange.State.Sending, sending.state)
        assertEquals(JamStatus.DRAFT, sending.target)
        post.posted.first { it == 1 }
        assertEquals("PUBLISHED", status())
        gate.complete(Unit)

        assertEquals(SetJamStatusOutcome.StatusSet(JamStatus.DRAFT, changed = true), change.await())
        assertEquals("DRAFT", status())
        assertEquals(emptyList<JamStatusChange>(), repository.observeJamStatusChanges().first())
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("setJamStatus", request.getValue("action").jsonPrimitive.content)
        assertEquals("2026-10-31", request.getValue("date").jsonPrimitive.content)
        assertEquals("BORRADOR", request.getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `created and published targets mirror through the right statement`() = runTest {
        val repository = seeded("DRAFT")
        post.answers += echo("CREADA", changed = true)
        post.answers += echo("PUBLICADA", changed = true)

        assertEquals(
            SetJamStatusOutcome.StatusSet(JamStatus.CREATED, true),
            repository.setJamStatus(DATE, JamStatus.CREATED),
        )
        assertEquals("CREATED", status())
        assertEquals(
            SetJamStatusOutcome.StatusSet(JamStatus.PUBLISHED, true),
            repository.setJamStatus(DATE, JamStatus.PUBLISHED),
        )
        assertEquals("PUBLISHED", status())
    }

    @Test
    fun `an unchanged echo is still a confirmed status`() = runTest {
        val repository = seeded("CREATED")
        post.answers += echo("CREADA", changed = false)

        assertEquals(
            SetJamStatusOutcome.StatusSet(JamStatus.CREATED, false),
            repository.setJamStatus(DATE, JamStatus.CREATED),
        )
        assertEquals("CREATED", status())
    }

    @Test
    fun `a refusal fails the entry, leaves Room alone and logs one safe line`() = runTest {
        val log = mutableListOf<String>()
        val repository = seeded("PUBLISHED", log)
        post.answers += error("jam_not_editable")
        post.answers += TransportResult.Failed(DataFailure.Offline)

        assertEquals(
            SetJamStatusOutcome.NotSet(WriteOutcome.Rejected("jam_not_editable")),
            repository.setJamStatus(DATE, JamStatus.DRAFT),
        )
        assertEquals(SetJamStatusOutcome.NotSet(WriteOutcome.Offline), repository.setJamStatus(DATE, JamStatus.CREATED))
        assertEquals("PUBLISHED", status())
        val states = repository.observeJamStatusChanges().first().map { it.state }
        assertEquals(
            listOf(
                JamStatusChange.State.Failed(WriteOutcome.Rejected("jam_not_editable")),
                JamStatusChange.State.Failed(WriteOutcome.Offline),
            ),
            states,
        )
        assertEquals(
            listOf(
                "setJamStatus failed: date=2026-10-31 target=BORRADOR outcome=Rejected(jam_not_editable)",
                "setJamStatus failed: date=2026-10-31 target=CREADA outcome=Offline",
            ),
            log,
        )
    }

    @Test
    fun `unknown_action is a script that must be updated, with an Outdated entry and Room unchanged`() = runTest {
        val repository = seeded("PUBLISHED")
        post.answers += error("unknown_action")

        assertEquals(SetJamStatusOutcome.ScriptOutdated, repository.setJamStatus(DATE, JamStatus.DRAFT))
        val entry = repository.observeJamStatusChanges().first().single()
        assertEquals(JamStatusChange.State.Outdated, entry.state)
        assertEquals("PUBLISHED", status())
        repository.dismissJamStatusChange(entry.id)
        assertEquals(emptyList<JamStatusChange>(), repository.observeJamStatusChanges().first())
    }

    @Test
    fun `an echo that differs or lacks changed is not a confirmation`() = runTest {
        val repository = seeded("PUBLISHED")
        post.answers += echo("CREADA", changed = true)
        post.answers +=
            body("""{"schemaVersion":1,"ok":true,"date":"2026-10-31","status":"BORRADOR","previous":"PUBLICADA"}""")
        post.answers += body("""{"schemaVersion":1,"ok":true,"date":"2026-11-28","status":"BORRADOR","changed":true}""")

        repeat(3) {
            assertEquals(
                SetJamStatusOutcome.NotSet(WriteOutcome.Unavailable),
                repository.setJamStatus(DATE, JamStatus.DRAFT),
            )
        }
        assertEquals("PUBLISHED", status())
    }

    @Test
    fun `a sending entry cannot be dismissed`() = runTest {
        val repository = seeded("PUBLISHED")
        post.answers += echo("BORRADOR", changed = true)
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val change = async { repository.setJamStatus(DATE, JamStatus.DRAFT) }
        val sending = repository.observeJamStatusChanges().first { it.isNotEmpty() }.single()
        repository.dismissJamStatusChange(sending.id)
        assertEquals(1, repository.observeJamStatusChanges().first().size)
        post.posted.first { it == 1 }
        gate.complete(Unit)
        change.await()
    }

    @Test
    fun `a status change queues with another mutation in call order`() = runTest {
        val repository = seeded("PUBLISHED")
        post.answers += echo("BORRADOR", changed = true)
        post.answers += body("""{"schemaVersion":1,"ok":true,"alreadyPublished":false}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val change = async { repository.setJamStatus(DATE, JamStatus.DRAFT) }
        post.posted.first { it == 1 }
        val publish = async { repository.publishSetlist(DATE) }
        testScheduler.runCurrent()
        assertEquals(1, post.bodies.size)
        gate.complete(Unit)

        change.await()
        publish.await()
        val actions = post.bodies.map {
            Json.parseToJsonElement(it).jsonObject.getValue("action").jsonPrimitive.content
        }
        assertEquals(listOf("setJamStatus", "publishSetlist"), actions)
    }

    @Test
    fun `caller cancellation does not cancel the server write`() = runTest {
        val repository = seeded("PUBLISHED")
        post.answers += echo("BORRADOR", changed = true)
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val caller = launch(start = CoroutineStart.UNDISPATCHED) { repository.setJamStatus(DATE, JamStatus.DRAFT) }
        post.posted.first { it == 1 }
        caller.cancel()
        gate.complete(Unit)

        jamsDao.observeJams().first { it.single().jam.status == "DRAFT" }
        assertTrue(caller.isCancelled)
        assertEquals(1, post.bodies.size)
        assertEquals(emptyList<JamStatusChange>(), repository.observeJamStatusChanges().first())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun status(): String = jamsDao.observeJams().first().single().jam.status

    private suspend fun TestScope.seeded(
        status: String,
        log: MutableList<String> = mutableListOf(),
    ): DefaultSetlistRepository {
        jamsDao.replaceJams(
            JamRows(
                listOf(JamEntity("2026-10-31", 1, "21:00", "La Macanuda", status, "AVAILABLE", null, 0)),
                emptyList(),
                emptyList(),
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
            database.catalogDao(),
            DataScope(backgroundScope),
            SetlistMutationQueue(),
            WriteFailureLog(log::add),
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 10, 31)

        fun body(text: String) = TransportResult.Body(text)

        fun error(code: String) = body("""{"schemaVersion":1,"error":{"code":"$code","message":"m"}}""")

        fun echo(status: String, changed: Boolean) = body(
            """{"schemaVersion":1,"ok":true,"date":"2026-10-31","status":"$status",""" +
                """"previous":"X","changed":$changed}""",
        )
    }
}
