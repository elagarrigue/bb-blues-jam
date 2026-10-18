package com.bbbjam.core.data.setlist

import android.database.SQLException
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogSongEntity
import com.bbbjam.core.data.cache.JamEntity
import com.bbbjam.core.data.cache.JamExtraEntity
import com.bbbjam.core.data.cache.JamRows
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.JamSongEntity
import com.bbbjam.core.data.cache.SetlistDao
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.cache.toDomain
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
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

/** Lineup repository writes against real Room/DataStore with a fake POST. */
class SetlistLineupChangesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val catalogDao = database.catalogDao()
    private val post = QueuedPostTransport()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `setSlotCount mirrors all seven confirmed columns before clearing the sending entry`() = runTest {
        val real = database.setlistDao()
        lateinit var repository: DefaultSetlistRepository
        val spy = object : SetlistDao by real {
            override suspend fun replaceSlots(date: String, songId: String, slots: List<JamSlotEntity>): Boolean {
                assertEquals(LineupChange.State.Sending, repository.observeLineupChanges().first().single().state)
                return real.replaceSlots(date, songId, slots)
            }
        }
        repository = seeded(setlistDao = spy)
        seedFour()
        post.answers += lineupOk()
        assertEquals(SetSlotCountOutcome.SlotCountSet, repository.setSlotCount(DATE, CROSSROADS, Instrument.GUITAR, 1))
        val songs = cachedSongs()
        assertEquals(1, songs[1].lineup.count(Instrument.GUITAR))
        assertEquals("Concurrent", songs[1].lineup.slots.first().musicianName)
        assertEquals(Lineup.default(), songs[0].lineup)
        assertEquals(emptyList<LineupChange>(), repository.observeLineupChanges().first())
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("setSlotCount", request.getValue("action").jsonPrimitive.content)
        assertEquals("guitar", request.getValue("instrument").jsonPrimitive.content)
        assertEquals("1", request.getValue("count").jsonPrimitive.content)
        post.answers +=
            body("""{"schemaVersion":1,"ok":true,"slots":${Fixtures.slotsJson("guitar1" to "Concurrent")}}""")
        assertEquals(SetSlotCountOutcome.SlotCountSet, repository.setSlotCount(DATE, CROSSROADS, Instrument.GUITAR, 2))
        assertEquals(2, cachedSongs()[1].lineup.count(Instrument.GUITAR))
    }

    @Test
    fun `growing a non-guitar instrument to three mirrors the eighteen confirmed columns`() = runTest {
        val repository = seeded()
        seedFour()
        post.answers += body(
            """{"schemaVersion":1,"ok":true,"slots":${
                Fixtures.slotsJson("vocals1" to "Lu", "vocals2" to null, "vocals3" to null)
            }}""",
        )
        assertEquals(SetSlotCountOutcome.SlotCountSet, repository.setSlotCount(DATE, CROSSROADS, Instrument.VOCALS, 3))
        val crossroads = jamsDao.observeJams().first().single().slots.filter { it.position == 2 }
        assertEquals(listOf(0, 1, 3, 6, 9, 10, 11, 12, 15), crossroads.map { it.columnIndex })
        assertEquals(Instrument.VOCALS.name, crossroads.first { it.columnIndex == 11 }.instrument)
        assertEquals(3, cachedSongs()[1].lineup.count(Instrument.VOCALS))
        assertEquals("Lu", cachedSongs()[1].lineup.slotAt(Instrument.VOCALS, SlotPosition(1))?.musicianName)
        assertEquals(
            "3",
            Json.parseToJsonElement(post.bodies.single()).jsonObject.getValue("count").jsonPrimitive.content,
        )
    }

    @Test
    fun `a legacy seven-key confirmed answer mirrors the default columns`() = runTest {
        val repository = seeded()
        seedFour()
        post.answers += body(
            """{"schemaVersion":1,"ok":true,"slots":{"guitar1":"Concurrent","guitar2":"-","bass":null,"drums":null,
            "vocals":"Lu","harmonica":null,"keyboards":null}}""",
        )
        assertEquals(SetSlotCountOutcome.SlotCountSet, repository.setSlotCount(DATE, CROSSROADS, Instrument.GUITAR, 1))
        val crossroads = jamsDao.observeJams().first().single().slots.filter { it.position == 2 }
        assertEquals(listOf(0, 3, 6, 9, 12, 15), crossroads.map { it.columnIndex })
        assertEquals("Lu", crossroads.first { it.columnIndex == 9 }.musicianName)
    }

    @Test
    fun `an eighteen-plus-legacy answer is mirrored and a disagreeing legacy key is unavailable`() = runTest {
        val repository = seeded()
        seedFour()
        val mirrors = ""","bass":null,"drums":null,"vocals":null,"harmonica":null,"keyboards":null}"""
        post.answers += body(
            """{"schemaVersion":1,"ok":true,"slots":${Fixtures.slotsJson().removeSuffix("}") + mirrors}}""",
        )
        assertEquals(SetSlotCountOutcome.SlotCountSet, repository.setSlotCount(DATE, CROSSROADS, Instrument.BASS, 1))
        val bad = Fixtures.slotsJson("bass1" to "Lu").removeSuffix("}") + mirrors
        post.answers += body("""{"schemaVersion":1,"ok":true,"slots":$bad}""")
        assertEquals(
            SetSlotCountOutcome.NotSet(WriteOutcome.Unavailable),
            repository.setSlotCount(DATE, CROSSROADS, Instrument.BASS, 1),
        )
    }

    @Test
    fun `clearSlot accepts the legacy seven-key answer and rejects any other key set`() = runTest {
        val repository = seeded()
        seedFour()
        database.setlistDao().assignOpenSlot(DATE.toString(), "red-house", 1, 0, "Ana")
        post.answers += body(
            """{"schemaVersion":1,"ok":true,"column":0,"slots":{"guitar1":null,"guitar2":"-","bass":null,"drums":null,
            "vocals":null,"harmonica":null,"keyboards":null}}""",
        )
        assertEquals(
            ClearSlotOutcome.Cleared,
            repository.clearSlot(DATE, SongId("red-house"), Instrument.GUITAR, SlotPosition(1), "Ana"),
        )
        assertEquals(
            listOf(0, 3, 6, 9, 12, 15),
            jamsDao.observeJams().first().single().slots.filter { it.position == 1 }.map { it.columnIndex },
        )
        database.setlistDao().assignOpenSlot(DATE.toString(), "red-house", 1, 0, "Ana")
        post.answers += body("""{"schemaVersion":1,"ok":true,"column":0,"slots":{"guitar1":null,"guitar2":"-"}}""")
        assertTrue(
            repository.clearSlot(DATE, SongId("red-house"), Instrument.GUITAR, SlotPosition(1), "Ana") !is
                ClearSlotOutcome.Cleared,
        )
    }

    @Test
    fun `a fourth slot is refused locally without posting, and a seven-key answer is unavailable`() = runTest {
        val repository = seeded()
        seedFour()
        assertEquals(
            SetSlotCountOutcome.NotSet(WriteOutcome.Rejected("invalid_count")),
            repository.setSlotCount(DATE, CROSSROADS, Instrument.BASS, 4),
        )
        assertTrue(post.bodies.isEmpty())
        post.answers += body(
            """{"schemaVersion":1,"ok":true,"slots":{"guitar1":null,"guitar2":null,"bass1":null,"drums1":null,
            "vocals1":null,"harmonica1":null,"keyboards1":null}}""",
        )
        assertEquals(
            SetSlotCountOutcome.NotSet(WriteOutcome.Unavailable),
            repository.setSlotCount(DATE, CROSSROADS, Instrument.BASS, 2),
        )
    }

    @Test
    fun `a second drums or keyboards slot is refused locally without posting`() = runTest {
        val repository = seeded()
        listOf(Instrument.DRUMS, Instrument.KEYBOARDS).forEach { instrument ->
            assertEquals(
                SetSlotCountOutcome.NotSet(WriteOutcome.Rejected("invalid_count")),
                repository.setSlotCount(DATE, CROSSROADS, instrument, 2),
            )
        }
        assertTrue(post.bodies.isEmpty())
    }

    @Test
    fun `lineup refusals and malformed successes preserve Room and can be dismissed`() = runTest {
        val repository = seeded()
        val before = jamsDao.observeJams().first()
        val cases = listOf(
            body(error("slot_filled")) to WriteOutcome.Rejected("slot_filled"),
            body(error("invalid_passphrase")) to WriteOutcome.AccessRefused,
            TransportResult.Failed(DataFailure.Offline) to WriteOutcome.Offline,
            body("""{"schemaVersion":1,"ok":true}""") to WriteOutcome.Unavailable,
            body("""{"schemaVersion":1,"ok":true,"slots":{"guitar1":false}}""") to WriteOutcome.Unavailable,
            body(
                """{"schemaVersion":1,"ok":true,"slots":${Fixtures.slotsJson("keyboards1" to " ")}}""",
            ) to WriteOutcome.Unavailable,
            body(
                """{"schemaVersion":1,"ok":true,
            "slots":{"guitar1":null,"guitar2":null,"bass1":null,"drums1":null,"vocals1":null,
            "harmonica1":null,"keyboards1":null}}""",
            ) to WriteOutcome.Unavailable,
        )
        cases.forEach { (answer, reason) ->
            post.answers += answer
            assertEquals(
                SetSlotCountOutcome.NotSet(reason),
                repository.setSlotCount(DATE, SongId("red-house"), Instrument.BASS, 0),
            )
            assertEquals(before, jamsDao.observeJams().first())
            val entry = repository.observeLineupChanges().first().last()
            assertEquals(LineupChange.State.Failed(reason), entry.state)
            repository.dismissLineupChange(entry.id)
            assertTrue(repository.observeLineupChanges().first().isEmpty())
        }
        assertEquals(
            SetSlotCountOutcome.NotSet(WriteOutcome.Rejected("invalid_count")),
            repository.setSlotCount(DATE, CROSSROADS, Instrument.GUITAR, 4),
        )
        assertEquals(cases.size, post.bodies.size)
    }

    @Test
    fun `lineup cache refusal and SQL failure do not change the confirmed server outcome`() = runTest {
        val real = database.setlistDao()
        val throwing = object : SetlistDao by real {
            override suspend fun replaceSlots(date: String, songId: String, slots: List<JamSlotEntity>): Boolean =
                throw SQLException()
        }
        val repository = seeded(setlistDao = throwing)
        val before = jamsDao.observeJams().first()
        post.answers += lineupOk()
        assertEquals(
            SetSlotCountOutcome.SlotCountSet,
            repository.setSlotCount(DATE, SongId("red-house"), Instrument.GUITAR, 1),
        )
        assertEquals(before, jamsDao.observeJams().first())
        assertTrue(repository.observeLineupChanges().first().isEmpty())
    }

    @Test
    fun `lineup calls queue with other mutations and survive caller cancellation`() = runTest {
        val repository = seeded()
        post.answers += lineupOk()
        post.answers += body("""{"schemaVersion":1,"ok":true}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }
        val first =
            launch(start = CoroutineStart.UNDISPATCHED) {
                repository.setSlotCount(DATE, SongId("red-house"), Instrument.GUITAR, 1)
            }
        repository.observeLineupChanges().first { it.isNotEmpty() }
        val second =
            async(start = CoroutineStart.UNDISPATCHED) { repository.setKey(DATE, SongId("red-house"), Key("C")) }
        val lineupId = repository.observeLineupChanges().first().single().id
        val keyId = repository.observeKeyChanges().first { it.isNotEmpty() }.single().id
        assertTrue(keyId > lineupId)
        repository.dismissLineupChange(lineupId)
        assertEquals(1, repository.observeLineupChanges().first().size)
        first.cancel()
        gate.complete(Unit)
        second.await()
        assertEquals(listOf("setSlotCount", "setKey"), post.bodies.map { actionOf(it) })
        assertTrue(repository.observeLineupChanges().first().isEmpty())
        assertEquals(1, cachedSongs().single().lineup.count(Instrument.GUITAR))
    }

    @Test
    fun `assignment sends typed ordinal and mirrors only the server-confirmed cell before clearing entry`() = runTest {
        val real = database.setlistDao()
        lateinit var repository: DefaultSetlistRepository
        val spy = object : SetlistDao by real {
            override suspend fun assignOpenSlot(
                date: String,
                songId: String,
                position: Int,
                columnIndex: Int,
                name: String,
            ): Int {
                assertEquals(Assignment.State.Sending, repository.observeAssignments().first().single().state)
                return real.assignOpenSlot(date, songId, position, columnIndex, name)
            }
        }
        repository = seeded(setlistDao = spy)
        post.answers += body("""{"schemaVersion":1,"ok":true,"column":1,"name":"Tincho"}""")
        assertEquals(
            AssignSlotOutcome.Assigned,
            repository.assignSlot(
                DATE,
                SongId("red-house"),
                Instrument.GUITAR,
                SlotPosition(2),
                MusicianName.parseOrNull("  Tincho  ")!!,
            ),
        )
        val updated = cachedSongs().single().lineup.slots
        assertEquals("Tincho", updated[1].musicianName)
        assertEquals(null, updated[0].musicianName)
        assertTrue(repository.observeAssignments().first().isEmpty())
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("assignSlot", request.getValue("action").jsonPrimitive.content)
        assertEquals("guitar", request.getValue("instrument").jsonPrimitive.content)
        assertEquals("2", request.getValue("ordinal").jsonPrimitive.content)
        assertEquals("Tincho", request.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `assignment refusals and malformed confirmations leave Room unchanged and can be dismissed`() = runTest {
        val repository = seeded()
        val before = jamsDao.observeJams().first()
        val cases = listOf(
            body(error("slot_taken")) to WriteOutcome.Rejected("slot_taken"),
            body(error("invalid_name")) to WriteOutcome.Rejected("invalid_name"),
            TransportResult.Failed(DataFailure.Offline) to WriteOutcome.Offline,
            body("""{"schemaVersion":1,"ok":true,"column":4,"name":"Tincho"}""") to WriteOutcome.Unavailable,
            body("""{"schemaVersion":1,"ok":true,"column":1,"name":"Other"}""") to WriteOutcome.Unavailable,
        )
        cases.forEach { (answer, reason) ->
            post.answers += answer
            assertEquals(
                AssignSlotOutcome.NotAssigned(reason),
                repository.assignSlot(
                    DATE,
                    SongId("red-house"),
                    Instrument.GUITAR,
                    SlotPosition(2),
                    MusicianName.parseOrNull("Tincho")!!,
                ),
            )
            assertEquals(before, jamsDao.observeJams().first())
            val entry = repository.observeAssignments().first().last()
            assertEquals(Assignment.State.Failed(reason), entry.state)
            repository.dismissAssignment(entry.id)
            assertTrue(repository.observeAssignments().first().isEmpty())
        }
    }

    @Test
    fun `missing or occupied local slot is refused without posting`() = runTest {
        val repository = seeded()
        assertEquals(
            AssignSlotOutcome.NotAssigned(WriteOutcome.Rejected("slot_not_in_lineup")),
            repository.assignSlot(
                DATE,
                SongId("red-house"),
                Instrument.GUITAR,
                SlotPosition(3),
                MusicianName.parseOrNull("Tincho")!!,
            ),
        )
        assertTrue(post.bodies.isEmpty())
        val occupied = Lineup.DEFAULT_INSTRUMENTS.mapIndexed { column, instrument ->
            JamSlotEntity(DATE.toString(), 1, column, instrument.name, if (column == 1) "Ya ocupado" else null)
        }
        database.setlistDao().replaceSlots(DATE.toString(), "red-house", occupied)
        assertEquals(
            AssignSlotOutcome.NotAssigned(WriteOutcome.Rejected("slot_taken")),
            repository.assignSlot(
                DATE,
                SongId("red-house"),
                Instrument.GUITAR,
                SlotPosition(2),
                MusicianName.parseOrNull("Tincho")!!,
            ),
        )
        assertTrue(post.bodies.isEmpty())
    }

    @Test
    fun `assignment shares write order and survives caller cancellation`() = runTest {
        val repository = seeded()
        post.answers += body("""{"schemaVersion":1,"ok":true,"column":1,"name":"Tincho"}""")
        post.answers += body("""{"schemaVersion":1,"ok":true}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.assignSlot(
                DATE,
                SongId("red-house"),
                Instrument.GUITAR,
                SlotPosition(2),
                MusicianName.parseOrNull("Tincho")!!,
            )
        }
        val assignment = repository.observeAssignments().first { it.isNotEmpty() }.single()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            repository.setKey(DATE, SongId("red-house"), Key("C"))
        }
        val key = repository.observeKeyChanges().first { it.isNotEmpty() }.single()
        assertTrue(key.id > assignment.id)
        first.cancel()
        gate.complete(Unit)
        second.await()
        assertEquals(listOf("assignSlot", "setKey"), post.bodies.map { actionOf(it) })
        assertTrue(repository.observeAssignments().first().isEmpty())
        assertEquals("Tincho", cachedSongs().single().lineup.slots[1].musicianName)
    }

    private fun lineupOk() = body(
        """{"schemaVersion":1,"ok":true,"slots":${Fixtures.slotsJson("guitar1" to "Concurrent", "guitar2" to "-")}}""",
    )

    // ---- fixtures ----

    /** Red House, Crossroads, Hoochie Coochie Man and Pride and Joy at 1..4; the last two with an extra. */
    private suspend fun seedFour() {
        val date = DATE.toString()
        val songs = listOf(
            JamSongEntity(date, 1, "red-house", "Red House", "Jimi Hendrix", "Bb"),
            JamSongEntity(date, 2, "crossroads", "Crossroads", "Eric Clapton", "A"),
            JamSongEntity(date, 3, HOOCHIE.value, "Hoochie Coochie Man", "Muddy Waters", "A"),
            JamSongEntity(date, 4, "pride-and-joy", "Pride and Joy", "Stevie Ray Vaughan", "E"),
        )
        val extras = listOf(
            JamExtraEntity(date, 3, 1, "Hugo", "Trompeta"),
            JamExtraEntity(date, 4, 1, "Eva", "Saxo"),
        )
        jamsDao.replaceJams(JamRows(listOf(jam("AVAILABLE")), songs, (1..4).flatMap { slots(it) }, extras), state())
    }

    private fun actionOf(json: String) =
        Json.parseToJsonElement(json).jsonObject.getValue("action").jsonPrimitive.content

    /** A repository over a cache holding the catalog and the upcoming draft with Red House at 1. */
    private suspend fun TestScope.seeded(
        passphrase: String? = TEST_VALUE,
        setlistDao: SetlistDao = database.setlistDao(),
    ): DefaultSetlistRepository {
        catalogDao.replaceCatalog(
            listOf(
                CatalogSongEntity("crossroads", 1, "Crossroads", "Eric Clapton", "A", null, null, emptyList(), null),
                CatalogSongEntity(
                    "the-thrill-is-gone", 2, "The Thrill Is Gone", "B.B. King", "Bm", null, null, emptyList(), null,
                ),
            ),
            SyncStateEntity("catalog", 1, 1, null),
        )
        val redHouse = JamSongEntity(DATE.toString(), 1, "red-house", "Red House", "Jimi Hendrix", "Bb")
        jamsDao.replaceJams(JamRows(listOf(jam("AVAILABLE")), listOf(redHouse), slots(1), emptyList()), state())
        val store = AdminCredentialStore(
            AdminCredentialStore.dataStore(backgroundScope) {
                File(folder.root, "${AdminCredentialStore.FILE_NAME}.preferences_pb")
            },
        )
        if (passphrase != null) store.save(passphrase)
        return DefaultSetlistRepository(
            AdminWriter(post, store),
            setlistDao,
            catalogDao,
            DataScope(backgroundScope),
        )
    }

    private suspend fun cachedSongs() =
        (jamsDao.observeJams().first().single().toDomain().setlist as Setlist.Available).songs

    private fun jam(setlistState: String) =
        JamEntity(DATE.toString(), 1, "21:00", "La Macanuda", "DRAFT", setlistState, null, 0)

    private fun slots(position: Int) = Lineup.DEFAULT_INSTRUMENTS.mapIndexed { column, instrument ->
        JamSlotEntity(DATE.toString(), position, column, instrument.name, null)
    }

    private fun state() = SyncStateEntity("jams", 1, 1, null)

    /** Answers from [answers] in order; while [gate] is set, each post waits for it first. */

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 10, 31)
        val CROSSROADS = SongId("crossroads")
        val HOOCHIE = SongId("hoochie-coochie-man")
        const val TEST_VALUE = "not-a-real-passphrase"

        fun body(text: String) = TransportResult.Body(text)

        fun error(code: String) = """{"schemaVersion":1,"error":{"code":"$code","message":"m"}}"""
    }
}
