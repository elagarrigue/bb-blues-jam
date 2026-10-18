package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.JamEntity
import com.bbbjam.core.data.cache.JamRows
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.JamSongEntity
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.JamStatus
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
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

class SetlistJamEditsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val post = QueuedPostTransport()

    @Test
    fun `a confirmed move sends all fields, moves the jam with its children and removes the entry`() = runTest {
        val repository = seeded()
        post.answers += updated(NEW, "20:00", "El Sotano", changed = true)
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val call = async { repository.updateJam(DATE, NEW, LocalTime.of(20, 0), "El Sotano") }
        val sending = repository.observeJamEdits().first { it.isNotEmpty() }.single()
        assertEquals(JamEdit.State.Sending, sending.state)
        assertEquals(JamEdit.Change.Update(NEW, LocalTime.of(20, 0), "El Sotano"), sending.change)
        post.posted.first { it == 1 }
        assertEquals(listOf(DATE.toString(), LATER.toString()), dates())
        gate.complete(Unit)

        assertEquals(UpdateJamOutcome.Updated(NEW, LocalTime.of(20, 0), "El Sotano", true), call.await())
        assertEquals(listOf(NEW.toString(), LATER.toString()), dates())
        val moved = jamsDao.observeJams().first().single { it.jam.date == NEW.toString() }
        assertEquals("20:00", moved.jam.startTime)
        assertEquals("El Sotano", moved.jam.venue)
        assertEquals(listOf("crossroads"), moved.songs.map { it.songId })
        assertEquals(1, moved.slots.size)
        assertEquals(emptyList<JamEdit>(), repository.observeJamEdits().first())
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("updateJam", request.getValue("action").jsonPrimitive.content)
        assertEquals("2026-10-31", request.getValue("date").jsonPrimitive.content)
        assertEquals("2026-11-07", request.getValue("newDate").jsonPrimitive.content)
        assertEquals("20:00", request.getValue("time").jsonPrimitive.content)
        assertEquals("El Sotano", request.getValue("venue").jsonPrimitive.content)
    }

    @Test
    fun `an update on the same date changes only the time and the venue, with the server's venue text`() = runTest {
        val repository = seeded()
        post.answers += updated(DATE, "19:00", "La Macanuda 2", changed = true)

        val outcome = repository.updateJam(DATE, DATE, LocalTime.of(19, 0), "  La  Macanuda 2 ")

        assertEquals(UpdateJamOutcome.Updated(DATE, LocalTime.of(19, 0), "La Macanuda 2", true), outcome)
        val jam = jamsDao.observeJams().first().single { it.jam.date == DATE.toString() }
        assertEquals("19:00", jam.jam.startTime)
        assertEquals("La Macanuda 2", jam.jam.venue)
        assertEquals(1, jam.songs.size)
    }

    @Test
    fun `an unchanged echo is still a confirmed update`() = runTest {
        val repository = seeded()
        post.answers += updated(DATE, "21:00", "La Macanuda", changed = false, previous = DATE)

        assertEquals(
            UpdateJamOutcome.Updated(DATE, LocalTime.of(21, 0), "La Macanuda", false),
            repository.updateJam(DATE, DATE, LocalTime.of(21, 0), "La Macanuda"),
        )
    }

    @Test
    fun `an update refusal fails the entry, leaves Room alone and logs one line without the venue`() = runTest {
        val log = mutableListOf<String>()
        val repository = seeded(log)
        post.answers += error("jam_exists")
        post.answers += TransportResult.Failed(DataFailure.Offline)

        assertEquals(
            UpdateJamOutcome.NotUpdated(WriteOutcome.Rejected("jam_exists")),
            repository.updateJam(DATE, NEW, LocalTime.of(20, 0), "Secreto"),
        )
        assertEquals(
            UpdateJamOutcome.NotUpdated(WriteOutcome.Offline),
            repository.updateJam(DATE, NEW, LocalTime.of(20, 0), "Secreto"),
        )
        assertEquals(listOf(DATE.toString(), LATER.toString()), dates())
        assertEquals(
            listOf(
                JamEdit.State.Failed(WriteOutcome.Rejected("jam_exists")),
                JamEdit.State.Failed(WriteOutcome.Offline),
            ),
            repository.observeJamEdits().first().map { it.state },
        )
        assertEquals(
            listOf(
                "updateJam failed: date=2026-10-31 newDate=2026-11-07 outcome=Rejected(jam_exists)",
                "updateJam failed: date=2026-10-31 newDate=2026-11-07 outcome=Offline",
            ),
            log,
        )
    }

    @Test
    fun `unknown_action is an outdated script for both writes and the entries can be dismissed`() = runTest {
        val repository = seeded()
        post.answers += error("unknown_action")
        post.answers += error("unknown_action")

        assertEquals(
            UpdateJamOutcome.ScriptOutdated,
            repository.updateJam(DATE, NEW, LocalTime.of(20, 0), "Bar"),
        )
        assertEquals(DeleteJamOutcome.ScriptOutdated, repository.deleteJam(DATE, 1))
        val entries = repository.observeJamEdits().first()
        assertEquals(listOf(JamEdit.State.Outdated, JamEdit.State.Outdated), entries.map { it.state })
        assertEquals(listOf(DATE.toString(), LATER.toString()), dates())
        entries.forEach { repository.dismissJamEdit(it.id) }
        assertEquals(emptyList<JamEdit>(), repository.observeJamEdits().first())
    }

    @Test
    fun `an echo that differs is not a confirmation`() = runTest {
        val repository = seeded()
        post.answers += updated(NEW, "20:00", "Bar", changed = true, previous = LATER)
        post.answers += updated(LATER, "20:00", "Bar", changed = true)
        post.answers += updated(NEW, "21:00", "Bar", changed = true)
        post.answers += updated(NEW, "20:00", "  ", changed = true)
        post.answers += body(
            """{"schemaVersion":1,"ok":true,"date":"2026-11-07","previousDate":"2026-10-31",""" +
                """"time":"20:00","venue":"Bar"}""",
        )

        repeat(5) {
            assertEquals(
                UpdateJamOutcome.NotUpdated(WriteOutcome.Unavailable),
                repository.updateJam(DATE, NEW, LocalTime.of(20, 0), "Bar"),
            )
        }
        assertEquals(listOf(DATE.toString(), LATER.toString()), dates())
    }

    @Test
    fun `a confirmed delete sends the expected songs and drops the jam with its children`() = runTest {
        val repository = seeded()
        post.answers += body("""{"schemaVersion":1,"ok":true,"date":"2026-10-31","deletedSongs":1,"tabDeleted":true}""")

        assertEquals(DeleteJamOutcome.Deleted(1), repository.deleteJam(DATE, 1))

        assertEquals(listOf(LATER.toString()), dates())
        assertEquals(emptyList<JamEdit>(), repository.observeJamEdits().first())
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("deleteJam", request.getValue("action").jsonPrimitive.content)
        assertEquals("2026-10-31", request.getValue("date").jsonPrimitive.content)
        assertEquals("1", request.getValue("expectedSongs").jsonPrimitive.content)
    }

    @Test
    fun `a delete refusal keeps the jam, fails the entry and logs one line`() = runTest {
        val log = mutableListOf<String>()
        val repository = seeded(log)
        post.answers += error("setlist_changed")
        post.answers += body("""{"schemaVersion":1,"ok":true,"date":"2026-11-28","deletedSongs":1}""")
        post.answers += body("""{"schemaVersion":1,"ok":true,"date":"2026-10-31"}""")

        assertEquals(
            DeleteJamOutcome.NotDeleted(WriteOutcome.Rejected("setlist_changed")),
            repository.deleteJam(DATE, 2),
        )
        assertEquals(DeleteJamOutcome.NotDeleted(WriteOutcome.Unavailable), repository.deleteJam(DATE, 1))
        assertEquals(DeleteJamOutcome.NotDeleted(WriteOutcome.Unavailable), repository.deleteJam(DATE, 1))
        assertEquals(listOf(DATE.toString(), LATER.toString()), dates())
        assertEquals("deleteJam failed: date=2026-10-31 outcome=Rejected(setlist_changed)", log.first())
    }

    @Test
    fun `deleting the upcoming jam makes the first held-back jam the upcoming one`() = runTest {
        val repository = seeded()
        post.answers += body("""{"schemaVersion":1,"ok":true,"date":"2026-10-31","deletedSongs":1,"tabDeleted":true}""")

        repository.deleteJam(DATE, 1)

        val remaining = jamsDao.observeJams().first().map { it.jam.date }
        assertEquals(listOf(LATER.toString()), remaining)
        assertEquals(JamStatus.DRAFT.name, jamsDao.observeJams().first().single().jam.status)
    }

    @Test
    fun `a sending entry cannot be dismissed`() = runTest {
        val repository = seeded()
        post.answers += updated(DATE, "21:00", "La Macanuda", changed = false, previous = DATE)
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val call = async { repository.updateJam(DATE, DATE, LocalTime.of(21, 0), "La Macanuda") }
        val sending = repository.observeJamEdits().first { it.isNotEmpty() }.single()
        repository.dismissJamEdit(sending.id)
        assertEquals(1, repository.observeJamEdits().first().size)
        post.posted.first { it == 1 }
        gate.complete(Unit)
        call.await()
    }

    @Test
    fun `an edit queues with another mutation in call order`() = runTest {
        val repository = seeded()
        post.answers += updated(DATE, "20:00", "Bar", changed = true, previous = DATE)
        post.answers += body("""{"schemaVersion":1,"ok":true,"alreadyPublished":false}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val edit = async { repository.updateJam(DATE, DATE, LocalTime.of(20, 0), "Bar") }
        post.posted.first { it == 1 }
        val publish = async { repository.publishSetlist(DATE) }
        testScheduler.runCurrent()
        assertEquals(1, post.bodies.size)
        gate.complete(Unit)

        edit.await()
        publish.await()
        val actions = post.bodies.map {
            Json.parseToJsonElement(it).jsonObject.getValue("action").jsonPrimitive.content
        }
        assertEquals(listOf("updateJam", "publishSetlist"), actions)
    }

    @Test
    fun `caller cancellation does not cancel the server write`() = runTest {
        val repository = seeded()
        post.answers += updated(DATE, "20:00", "Bar", changed = true, previous = DATE)
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.updateJam(DATE, DATE, LocalTime.of(20, 0), "Bar")
        }
        post.posted.first { it == 1 }
        caller.cancel()
        gate.complete(Unit)

        jamsDao.observeJams().first { rows -> rows.any { it.jam.venue == "Bar" } }
        assertTrue(caller.isCancelled)
        assertEquals(emptyList<JamEdit>(), repository.observeJamEdits().first())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun dates(): List<String> = jamsDao.observeJams().first().map { it.jam.date }.sorted()

    private suspend fun TestScope.seeded(log: MutableList<String> = mutableListOf()): DefaultSetlistRepository {
        jamsDao.replaceJams(
            JamRows(
                listOf(
                    JamEntity("2026-10-31", 1, "21:00", "La Macanuda", "DRAFT", "AVAILABLE", null, 0),
                    JamEntity("2026-11-28", 2, "21:00", "Otro", "DRAFT", "AVAILABLE", null, 0),
                ),
                listOf(JamSongEntity("2026-10-31", 1, "crossroads", "Crossroads", "Eric Clapton", "A")),
                listOf(JamSlotEntity("2026-10-31", 1, 0, "GUITAR", "Ana")),
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
            failureLog = WriteFailureLog(log::add),
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 10, 31)
        val NEW: LocalDate = LocalDate.of(2026, 11, 7)
        val LATER: LocalDate = LocalDate.of(2026, 11, 28)

        fun body(text: String) = TransportResult.Body(text)

        fun error(code: String) = body("""{"schemaVersion":1,"error":{"code":"$code","message":"m"}}""")

        fun updated(date: LocalDate, time: String, venue: String, changed: Boolean, previous: LocalDate = DATE) = body(
            """{"schemaVersion":1,"ok":true,"date":"$date","previousDate":"$previous","time":"$time",""" +
                """"venue":"$venue","status":"BORRADOR","changed":$changed,"tabRenamed":false}""",
        )
    }
}
