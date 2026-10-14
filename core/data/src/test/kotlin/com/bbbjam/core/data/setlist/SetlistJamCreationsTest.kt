package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogSongEntity
import com.bbbjam.core.data.cache.JamEntity
import com.bbbjam.core.data.cache.JamRows
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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

class SetlistJamCreationsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val post = QueuedPostTransport()

    @Test
    fun `created sends the fields and mirrors an empty available draft after the cached jams`() = runTest {
        val log = mutableListOf<String>()
        val repository = seeded(log = log)
        post.answers += created("La Macanuda")

        val outcome = repository.createJam(NEW_DATE, TIME, "  La   Macanuda ")

        assertEquals(CreateJamOutcome.Created(NEW_DATE, TIME, "La Macanuda"), outcome)
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("createJam", request.getValue("action").jsonPrimitive.content)
        assertEquals("2026-11-28", request.getValue("date").jsonPrimitive.content)
        assertEquals("21:00", request.getValue("time").jsonPrimitive.content)
        assertEquals("  La   Macanuda ", request.getValue("venue").jsonPrimitive.content)
        val jams = jamsDao.observeJams().first().map { it.jam }
        val draft = jams.single { it.date == "2026-11-28" }
        assertEquals(JamEntity("2026-11-28", 2, "21:00", "La Macanuda", "DRAFT", "AVAILABLE", null, 0), draft)
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `an existing cached row wins over the mirror`() = runTest {
        val repository = seeded(extra = JamEntity("2026-11-28", 2, "20:00", "Otro", "DRAFT", "AVAILABLE", null, 0))
        post.answers += created("La Macanuda")

        assertTrue(repository.createJam(NEW_DATE, TIME, "La Macanuda") is CreateJamOutcome.Created)
        val jam = jamsDao.observeJams().first().map { it.jam }.single { it.date == "2026-11-28" }
        assertEquals("Otro", jam.venue)
    }

    @Test
    fun `an echo that differs creates nothing and mirrors nothing`() = runTest {
        val repository = seeded()
        post.answers += body("""{"schemaVersion":1,"ok":true,"date":"2026-11-28","time":"21:00","venue":"X"}""")
        post.answers += body(
            """{"schemaVersion":1,"ok":true,"date":"2026-11-29","time":"21:00","venue":"X","status":"BORRADOR"}""",
        )

        assertEquals(CreateJamOutcome.NotCreated(WriteOutcome.Unavailable), repository.createJam(NEW_DATE, TIME, "X"))
        assertEquals(CreateJamOutcome.NotCreated(WriteOutcome.Unavailable), repository.createJam(NEW_DATE, TIME, "X"))
        assertEquals(1, jamsDao.observeJams().first().size)
    }

    @Test
    fun `refusals map to outcomes and log one safe line`() = runTest {
        val log = mutableListOf<String>()
        val repository = seeded(log = log)
        post.answers += error("unknown_action")
        post.answers += error("invalid_passphrase")
        post.answers += TransportResult.Failed(DataFailure.Offline)
        post.answers += error("jam_exists")

        assertEquals(CreateJamOutcome.ScriptOutdated, repository.createJam(NEW_DATE, TIME, "Bar"))
        assertEquals(
            CreateJamOutcome.NotCreated(WriteOutcome.AccessRefused),
            repository.createJam(NEW_DATE, TIME, "Bar"),
        )
        assertEquals(CreateJamOutcome.NotCreated(WriteOutcome.Offline), repository.createJam(NEW_DATE, TIME, "Bar"))
        assertEquals(
            CreateJamOutcome.NotCreated(WriteOutcome.Rejected("jam_exists")),
            repository.createJam(NEW_DATE, TIME, "Bar"),
        )
        assertEquals(
            listOf(
                "createJam failed: date=2026-11-28 outcome=ScriptOutdated",
                "createJam failed: date=2026-11-28 outcome=AccessRefused",
                "createJam failed: date=2026-11-28 outcome=Offline",
                "createJam failed: date=2026-11-28 outcome=Rejected(jam_exists)",
            ),
            log,
        )
        assertEquals(1, jamsDao.observeJams().first().size)
    }

    @Test
    fun `a create followed by an add is sent in call order`() = runTest {
        val repository = seeded()
        post.answers += created("Bar")
        post.answers +=
            body("""{"schemaVersion":1,"ok":true,"position":1,"title":"Crossroads","artist":"Eric Clapton"}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val create = async { repository.createJam(NEW_DATE, TIME, "Bar") }
        post.posted.first { it == 1 }
        val add = async { repository.addSong(NEW_DATE, SongId("crossroads"), Key("A")) }
        testScheduler.runCurrent()
        assertEquals(1, post.bodies.size)
        gate.complete(Unit)

        assertTrue(create.await() is CreateJamOutcome.Created)
        add.await()
        assertEquals(listOf("createJam", "addSong"), post.bodies.map(::actionOf))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun TestScope.seeded(
        log: MutableList<String> = mutableListOf(),
        extra: JamEntity? = null,
    ): DefaultSetlistRepository {
        database.catalogDao().replaceCatalog(
            listOf(
                CatalogSongEntity("crossroads", 1, "Crossroads", "Eric Clapton", "A", null, null, emptyList(), null),
            ),
            SyncStateEntity("catalog", 1, 1, null),
        )
        val first = JamEntity("2026-10-31", 1, "21:00", "La Macanuda", "PUBLISHED", "AVAILABLE", null, 0)
        jamsDao.replaceJams(
            JamRows(listOfNotNull(first, extra), emptyList(), emptyList(), emptyList()),
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
            failureLog = WriteFailureLog(log::add),
        )
    }

    private fun actionOf(json: String) =
        Json.parseToJsonElement(json).jsonObject.getValue("action").jsonPrimitive.content

    private companion object {
        val NEW_DATE: LocalDate = LocalDate.of(2026, 11, 28)
        val TIME: LocalTime = LocalTime.of(21, 0)

        fun body(text: String) = TransportResult.Body(text)

        fun error(code: String) = body("""{"schemaVersion":1,"error":{"code":"$code","message":"m"}}""")

        fun created(venue: String) = body(
            """{"schemaVersion":1,"ok":true,"date":"2026-11-28","time":"21:00","venue":"$venue","status":"BORRADOR"}""",
        )
    }
}
