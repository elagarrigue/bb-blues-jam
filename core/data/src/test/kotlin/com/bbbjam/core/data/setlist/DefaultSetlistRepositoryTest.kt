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
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SlotPosition
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

/**
 * The add, remove and set-key mutations with no UI: a real in-memory Room cache, a real DataStore
 * file, the real [AdminWriter] and a fake POST transport. Writes run in the test's background scope.
 */
class DefaultSetlistRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val catalogDao = database.catalogDao()
    private val post = FakePost()

    @Test
    fun `clearSlot writes expected identity and updates Room only after confirmed slot payload`() = runTest {
        val repository = seeded()
        database.setlistDao().assignOpenSlot(DATE.toString(), "red-house", 1, 0, "Ana")
        post.answers +=
            body(
                """{
                    "schemaVersion": 1,
                    "ok": true,
                    "column": 0,
                    "slots": {
                        "guitar1": null, "guitar2": null, "bass": null, "drums": null,
                        "vocals": null, "harmonica": null, "keyboards": null
                    }
                }""",
            )

        assertEquals(
            ClearSlotOutcome.Cleared,
            repository.clearSlot(DATE, SongId("red-house"), Instrument.GUITAR, SlotPosition(1), "Ana"),
        )

        assertTrue(cachedSongs().single().lineup.slots.first().isOpen)
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("clearSlot", request.getValue("action").jsonPrimitive.content)
        assertEquals("Ana", request.getValue("expectedName").jsonPrimitive.content)
        assertEquals(emptyList<SlotClear>(), repository.observeSlotClears().first())
    }

    @Test
    fun `clearSlot rejects stale local target without posting and restores failure without Room mutation`() = runTest {
        val repository = seeded()
        assertEquals(
            ClearSlotOutcome.NotCleared(WriteOutcome.Rejected("slot_empty")),
            repository.clearSlot(DATE, SongId("red-house"), Instrument.GUITAR, SlotPosition(1), "Ana"),
        )
        assertTrue(post.bodies.isEmpty())
        assertEquals(
            SlotClear.State.Failed(WriteOutcome.Rejected("slot_empty")),
            repository.observeSlotClears().first().single().state,
        )

        database.setlistDao().assignOpenSlot(DATE.toString(), "red-house", 1, 0, "Otra")
        assertEquals(
            ClearSlotOutcome.NotCleared(WriteOutcome.Rejected("slot_changed")),
            repository.clearSlot(DATE, SongId("red-house"), Instrument.GUITAR, SlotPosition(1), "Ana"),
        )
        assertTrue(post.bodies.isEmpty())
        assertEquals("Otra", cachedSongs().single().lineup.slots.first().musicianName)
    }

    @Test
    fun `clearSlot failure and malformed success preserve the confirmed Room line`() = runTest {
        val repository = seeded()
        database.setlistDao().assignOpenSlot(DATE.toString(), "red-house", 1, 0, "Ana")
        val before = jamsDao.observeJams().first()
        post.answers += body(error("slot_changed"))
        assertEquals(
            ClearSlotOutcome.NotCleared(WriteOutcome.Rejected("slot_changed")),
            repository.clearSlot(DATE, SongId("red-house"), Instrument.GUITAR, SlotPosition(1), "Ana"),
        )
        assertEquals(before, jamsDao.observeJams().first())

        post.answers += body("""{"schemaVersion":1,"ok":true,"column":0,"slots":{"guitar1":null}}""")
        assertEquals(
            ClearSlotOutcome.NotCleared(WriteOutcome.Unavailable),
            repository.clearSlot(DATE, SongId("red-house"), Instrument.GUITAR, SlotPosition(1), "Ana"),
        )
        assertEquals(before, jamsDao.observeJams().first())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `addSong, called with no UI, caches the confirmed song with seven open slots and clears its entry`() = runTest {
        val repository = seeded()
        post.answers += ok(position = 2)
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val add = async { repository.addSong(DATE, CROSSROADS, Key("E")) }
        val sending = repository.observeAdds().first { it.isNotEmpty() }.single()
        assertEquals(SetlistAdd.State.Sending, sending.state)
        assertEquals(
            listOf("Crossroads", "Eric Clapton", Key("E")),
            listOf(sending.title, sending.artist, sending.key),
        )
        gate.complete(Unit)

        assertEquals(AddSongOutcome.Added(2), add.await())
        val songs = cachedSongs()
        assertEquals(listOf(1, 2), songs.map { it.position })
        assertEquals(CROSSROADS, songs[1].songId)
        assertEquals(Key("E"), songs[1].key)
        assertEquals(Lineup.default(), songs[1].lineup)
        assertEquals(emptyList<SetlistAdd>(), repository.observeAdds().first())

        val body = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals(setOf("action", "passphrase", "date", "songId", "key"), body.keys)
        assertEquals("addSong", body.getValue("action").jsonPrimitive.content)
        assertEquals("2026-10-31", body.getValue("date").jsonPrimitive.content)
        assertEquals("crossroads", body.getValue("songId").jsonPrimitive.content)
        assertEquals("E", body.getValue("key").jsonPrimitive.content)
    }

    @Test
    fun `every answer but a well-formed ok leaves no phantom row and publishes a failed entry`() = runTest {
        val repository = seeded()
        val answers = listOf(
            body(error("invalid_passphrase")) to WriteOutcome.AccessRefused,
            body(error("passphrase_not_set")) to WriteOutcome.AccessRefused,
            TransportResult.Failed(DataFailure.Offline) to WriteOutcome.Offline,
            body(error("busy")) to WriteOutcome.Unavailable,
            body(error("rate_limited")) to WriteOutcome.Unavailable,
            body(error("song_already_in_setlist")) to WriteOutcome.Rejected("song_already_in_setlist"),
            body(error("jam_not_editable")) to WriteOutcome.Rejected("jam_not_editable"),
            body("<html></html>") to WriteOutcome.Unavailable,
            body("""{"schemaVersion":1,"ok":true}""") to WriteOutcome.Unavailable,
            body("""{"schemaVersion":1,"ok":true,"position":"2","title":"T","artist":"A"}""") to
                WriteOutcome.Unavailable,
            body("""{"schemaVersion":1,"ok":true,"position":0,"title":"T","artist":"A"}""") to WriteOutcome.Unavailable,
            body("""{"schemaVersion":1,"ok":true,"position":2,"title":"","artist":"A"}""") to WriteOutcome.Unavailable,
        )
        val before = jamsDao.observeJams().first()

        answers.forEach { (answer, reason) ->
            post.answers += answer

            assertEquals(
                answer.toString(),
                AddSongOutcome.NotAdded(reason),
                repository.addSong(DATE, CROSSROADS, Key("A")),
            )
            assertEquals(answer.toString(), before, jamsDao.observeJams().first())
            val entry = repository.observeAdds().first().last()
            assertEquals(answer.toString(), SetlistAdd.State.Failed(reason), entry.state)
        }
        assertEquals(answers.size, post.bodies.size)
        assertEquals(answers.size, repository.observeAdds().first().size)
    }

    @Test
    fun `a failed entry stays until dismissed, and dismiss leaves a sending entry and unknown ids alone`() = runTest {
        val repository = seeded()
        post.answers += body(error("busy"))
        repository.addSong(DATE, CROSSROADS, Key("A"))
        val failed = repository.observeAdds().first().single()

        post.answers += ok(position = 2)
        val gate = CompletableDeferred<Unit>().also { post.gate = it }
        val add = async { repository.addSong(DATE, CROSSROADS, Key("A")) }
        val sending = repository.observeAdds().first { it.size == 2 }.last()

        repository.dismiss(sending.id)
        repository.dismiss(999)
        assertEquals(listOf(failed.id, sending.id), repository.observeAdds().first().map { it.id })
        repository.dismiss(failed.id)
        assertEquals(listOf(sending.id), repository.observeAdds().first().map { it.id })
        gate.complete(Unit)
        add.await()
        assertEquals(emptyList<SetlistAdd>(), repository.observeAdds().first())
    }

    @Test
    fun `a song missing from the cached catalog is unknown_song and nothing is sent`() = runTest {
        val repository = seeded()

        val outcome = repository.addSong(DATE, SongId("zz-no-existe"), Key("A"))

        assertEquals(AddSongOutcome.NotAdded(WriteOutcome.Rejected("unknown_song")), outcome)
        assertTrue(post.bodies.isEmpty())
        assertEquals(
            SetlistAdd.State.Failed(WriteOutcome.Rejected("unknown_song")),
            repository.observeAdds().first().single().state,
        )
    }

    @Test
    fun `with no passphrase stored (the debug admin) it is AccessRefused and nothing is sent`() = runTest {
        val repository = seeded(passphrase = null)

        assertEquals(
            AddSongOutcome.NotAdded(WriteOutcome.AccessRefused),
            repository.addSong(DATE, CROSSROADS, Key("A")),
        )
        assertTrue(post.bodies.isEmpty())
        assertEquals(listOf(1), cachedSongs().map { it.position })
    }

    @Test
    fun `two quick adds are sent one at a time in call order and get consecutive positions`() = runTest {
        val repository = seeded()
        post.answers += ok(position = 2)
        post.answers += ok(position = 3, title = "The Thrill Is Gone", artist = "B.B. King")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val first = async { repository.addSong(DATE, CROSSROADS, Key("A")) }
        val second = async { repository.addSong(DATE, THRILL, Key("Bm")) }
        repository.observeAdds().first { it.size == 2 }
        post.posted.first { it == 1 }
        // The second write waits for the first, which the gate holds.
        assertEquals(1, post.bodies.size)
        assertEquals(2, repository.observeAdds().first().size)
        gate.complete(Unit)

        assertEquals(AddSongOutcome.Added(2), first.await())
        assertEquals(AddSongOutcome.Added(3), second.await())
        assertEquals(listOf("crossroads", "the-thrill-is-gone"), post.bodies.map { songIdOf(it) })
        assertEquals(listOf(1, 2, 3), cachedSongs().map { it.position })
        assertEquals(listOf("red-house", "crossroads", "the-thrill-is-gone"), cachedSongs().map { it.songId.value })
    }

    @Test
    fun `cancelling the caller does not cancel a write in flight`() = runTest {
        val repository = seeded()
        post.answers += ok(position = 2)
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val caller = launch { repository.addSong(DATE, CROSSROADS, Key("A")) }
        post.posted.first { it == 1 }
        caller.cancel()
        gate.complete(Unit)
        repository.observeAdds().first { it.isEmpty() }

        assertTrue(caller.isCancelled)
        assertEquals(listOf(1, 2), cachedSongs().map { it.position })
        assertEquals(emptyList<SetlistAdd>(), repository.observeAdds().first())
    }

    @Test
    fun `a caller cancelled at once, before the song is even resolved, still adds it`() = runTest {
        val repository = seeded()
        post.answers += ok(position = 2)

        val caller = launch(start = CoroutineStart.UNDISPATCHED) { repository.addSong(DATE, CROSSROADS, Key("A")) }
        caller.cancel()
        jamsDao.observeJams().first { jams -> jams.single().songs.size == 2 }

        assertTrue(caller.isCancelled)
        assertEquals(1, post.bodies.size)
        assertEquals(listOf(1, 2), cachedSongs().map { it.position })
    }

    @Test
    fun `a row a refresh already brought wins, and a jam that is not available gets no row`() = runTest {
        val repository = seeded()
        val refreshed = JamSongEntity(DATE.toString(), 2, "crossroads", "Crossroads", "Eric Clapton", "A")
        database.setlistDao().insertSetlistSong(refreshed, slots(2))
        post.answers += ok(position = 2)

        assertEquals(AddSongOutcome.Added(2), repository.addSong(DATE, CROSSROADS, Key("E")))
        assertEquals(Key("A"), cachedSongs()[1].key)

        jamsDao.replaceJams(JamRows(listOf(jam("WITHHELD")), emptyList(), emptyList(), emptyList()), state())
        post.answers += ok(position = 3)
        assertEquals(AddSongOutcome.Added(3), repository.addSong(DATE, CROSSROADS, Key("E")))
        assertEquals(Setlist.Withheld, jamsDao.observeJams().first().single().toDomain().setlist)
    }

    // ---- removeSong ----

    @Test
    fun `removeSong, called with no UI, mirrors the removal and renumbering and clears its entry`() = runTest {
        val repository = seeded()
        seedFour()
        val catalogBefore = catalogDao.song("crossroads")
        post.answers += body("""{"schemaVersion":1,"ok":true,"position":2}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val remove = async { repository.removeSong(DATE, HOOCHIE) }
        val sending = repository.observeRemoves().first { it.isNotEmpty() }.single()
        assertEquals(SetlistRemove.State.Sending, sending.state)
        // Not in the catalog: the tab's title copy.
        assertEquals("Hoochie Coochie Man", sending.title)
        assertEquals(listOf(1, 2, 3, 4), cachedSongs().map { it.position })
        gate.complete(Unit)

        assertEquals(RemoveSongOutcome.Removed, remove.await())
        val songs = cachedSongs()
        assertEquals(listOf("red-house", "crossroads", "pride-and-joy"), songs.map { it.songId.value })
        assertEquals(listOf(1, 2, 3), songs.map { it.position })
        assertEquals(Key("E"), songs[2].key)
        assertEquals(1, jamsDao.observeJams().first().single().extras.size)
        assertEquals(emptyList<SetlistRemove>(), repository.observeRemoves().first())
        // The catalog entry is untouched.
        assertEquals(catalogBefore, catalogDao.song("crossroads"))

        val body = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals(setOf("action", "passphrase", "date", "songId"), body.keys)
        assertEquals("removeSong", body.getValue("action").jsonPrimitive.content)
        assertEquals("2026-10-31", body.getValue("date").jsonPrimitive.content)
        assertEquals("hoochie-coochie-man", body.getValue("songId").jsonPrimitive.content)
    }

    @Test
    fun `every failed removal leaves the cache as it was and publishes a failed entry`() = runTest {
        val repository = seeded()
        seedFour()
        val answers = listOf(
            body(error("invalid_passphrase")) to WriteOutcome.AccessRefused,
            TransportResult.Failed(DataFailure.Offline) to WriteOutcome.Offline,
            body(error("busy")) to WriteOutcome.Unavailable,
            body(error("duplicate_song")) to WriteOutcome.Rejected("duplicate_song"),
            body(error("jam_not_editable")) to WriteOutcome.Rejected("jam_not_editable"),
            body("<html></html>") to WriteOutcome.Unavailable,
        )
        val before = jamsDao.observeJams().first()

        answers.forEach { (answer, reason) ->
            post.answers += answer

            assertEquals(answer.toString(), RemoveSongOutcome.NotRemoved(reason), repository.removeSong(DATE, HOOCHIE))
            assertEquals(answer.toString(), before, jamsDao.observeJams().first())
            val entry = repository.observeRemoves().first().last()
            assertEquals(answer.toString(), SetlistRemove.State.Failed(reason), entry.state)
        }
        assertEquals(answers.size, repository.observeRemoves().first().size)
    }

    @Test
    fun `song_not_in_setlist also removes the cached row, and the entry still fails`() = runTest {
        val repository = seeded()
        seedFour()
        post.answers += body(error("song_not_in_setlist"))

        val outcome = repository.removeSong(DATE, HOOCHIE)

        val reason = WriteOutcome.Rejected("song_not_in_setlist")
        assertEquals(RemoveSongOutcome.NotRemoved(reason), outcome)
        assertEquals(listOf("red-house", "crossroads", "pride-and-joy"), cachedSongs().map { it.songId.value })
        assertEquals(listOf(1, 2, 3), cachedSongs().map { it.position })
        assertEquals(SetlistRemove.State.Failed(reason), repository.observeRemoves().first().single().state)
    }

    @Test
    fun `with no passphrase stored it is AccessRefused, nothing is sent and the row stays`() = runTest {
        val repository = seeded(passphrase = null)

        assertEquals(
            RemoveSongOutcome.NotRemoved(WriteOutcome.AccessRefused),
            repository.removeSong(DATE, SongId("red-house")),
        )
        assertTrue(post.bodies.isEmpty())
        assertEquals(listOf(1), cachedSongs().map { it.position })
        // Not in the cached catalog: the title is the tab's copy.
        assertEquals("Red House", repository.observeRemoves().first().single().title)
    }

    @Test
    fun `adds and removes share one id counter, one dismiss and one write queue in call order`() = runTest {
        val repository = seeded()
        post.answers += body(error("busy"))
        repository.removeSong(DATE, SongId("zz-no-existe"))
        val failedRemove = repository.observeRemoves().first().single()
        assertEquals("zz-no-existe", failedRemove.title)

        post.answers += ok(position = 2)
        post.answers += body("""{"schemaVersion":1,"ok":true,"position":1}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }
        val add = async { repository.addSong(DATE, CROSSROADS, Key("A")) }
        val remove = async { repository.removeSong(DATE, SongId("red-house")) }
        repository.observeRemoves().first { it.size == 2 }
        post.posted.first { it == 2 }
        // The removal waits for the add, which the gate holds.
        assertEquals(2, post.bodies.size)
        gate.complete(Unit)

        assertEquals(AddSongOutcome.Added(2), add.await())
        assertEquals(RemoveSongOutcome.Removed, remove.await())
        assertEquals(listOf("removeSong", "addSong", "removeSong"), post.bodies.map { actionOf(it) })
        assertEquals(listOf(CROSSROADS to 1), cachedSongs().map { it.songId to it.position })

        assertEquals(emptyList<SetlistAdd>(), repository.observeAdds().first())
        assertEquals(listOf(failedRemove.id), repository.observeRemoves().first().map { it.id })
        repository.dismiss(failedRemove.id)
        assertEquals(emptyList<SetlistRemove>(), repository.observeRemoves().first())
    }

    @Test
    fun `cancelling the caller of a removal does not cancel it`() = runTest {
        val repository = seeded()
        post.answers += body("""{"schemaVersion":1,"ok":true,"position":1}""")

        val caller = launch(start = CoroutineStart.UNDISPATCHED) { repository.removeSong(DATE, SongId("red-house")) }
        caller.cancel()
        jamsDao.observeJams().first { jams -> jams.single().songs.isEmpty() }

        assertTrue(caller.isCancelled)
        assertEquals(1, post.bodies.size)
    }

    // ---- setKey ----

    @Test
    fun `setKey, called with no UI, sends the key, updates only that cached song and clears its entry`() = runTest {
        val repository = seeded()
        seedFour()
        val catalogBefore = catalogDao.song("crossroads")
        post.answers += body("""{"schemaVersion":1,"ok":true}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val change = async { repository.setKey(DATE, CROSSROADS, Key("Bb")) }
        val sending = repository.observeKeyChanges().first { it.isNotEmpty() }.single()
        assertEquals(KeyChange.State.Sending, sending.state)
        assertEquals(listOf("Crossroads", Key("Bb"), CROSSROADS), listOf(sending.title, sending.key, sending.songId))
        // Nothing is cached while sending: the pending key lives only in the entry.
        assertEquals(Key("A"), cachedSongs()[1].key)
        gate.complete(Unit)

        assertEquals(SetKeyOutcome.KeySet, change.await())
        assertEquals(
            listOf("red-house" to "Bb", "crossroads" to "Bb", "hoochie-coochie-man" to "A", "pride-and-joy" to "E"),
            cachedSongs().map { it.songId.value to it.key.value },
        )
        assertEquals(listOf(1, 2, 3, 4), cachedSongs().map { it.position })
        assertEquals(emptyList<KeyChange>(), repository.observeKeyChanges().first())
        // The catalog entry, and its default key, is untouched (D-08).
        assertEquals(catalogBefore, catalogDao.song("crossroads"))

        val body = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals(setOf("action", "passphrase", "date", "songId", "key"), body.keys)
        assertEquals("setKey", body.getValue("action").jsonPrimitive.content)
        assertEquals("2026-10-31", body.getValue("date").jsonPrimitive.content)
        assertEquals("crossroads", body.getValue("songId").jsonPrimitive.content)
        assertEquals("Bb", body.getValue("key").jsonPrimitive.content)
    }

    @Test
    fun `every failed key change leaves the cache and the catalog as they were and publishes a failed entry`() =
        runTest {
            val repository = seeded()
            seedFour()
            val answers = listOf(
                body(error("invalid_passphrase")) to WriteOutcome.AccessRefused,
                TransportResult.Failed(DataFailure.Offline) to WriteOutcome.Offline,
                body(error("busy")) to WriteOutcome.Unavailable,
                body(error("song_not_in_setlist")) to WriteOutcome.Rejected("song_not_in_setlist"),
                body(error("duplicate_song")) to WriteOutcome.Rejected("duplicate_song"),
                body(error("jam_not_editable")) to WriteOutcome.Rejected("jam_not_editable"),
                body("<html></html>") to WriteOutcome.Unavailable,
            )
            val before = jamsDao.observeJams().first()
            val catalogBefore = catalogDao.song("crossroads")

            answers.forEach { (answer, reason) ->
                post.answers += answer

                assertEquals(
                    answer.toString(),
                    SetKeyOutcome.NotSet(reason),
                    repository.setKey(DATE, CROSSROADS, Key("Bb")),
                )
                assertEquals(answer.toString(), before, jamsDao.observeJams().first())
                val entry = repository.observeKeyChanges().first().last()
                assertEquals(answer.toString(), KeyChange.State.Failed(reason), entry.state)
            }
            assertEquals(answers.size, repository.observeKeyChanges().first().size)
            assertEquals(catalogBefore, catalogDao.song("crossroads"))
        }

    @Test
    fun `the cache holds the new key before the entry is removed`() = runTest {
        lateinit var repository: DefaultSetlistRepository
        val seen = mutableListOf<List<KeyChange.State>>()
        val real = database.setlistDao()
        val spy = object : SetlistDao by real {
            override suspend fun updateKey(date: String, songId: String, key: String): Int {
                seen += repository.observeKeyChanges().first().map { it.state }
                return real.updateKey(date, songId, key)
            }
        }
        repository = seeded(setlistDao = spy)
        post.answers += body("""{"schemaVersion":1,"ok":true}""")

        assertEquals(SetKeyOutcome.KeySet, repository.setKey(DATE, SongId("red-house"), Key("C")))

        // When the cache was written, the pending entry was still there; now it is gone.
        assertEquals(listOf(listOf<KeyChange.State>(KeyChange.State.Sending)), seen)
        assertEquals(Key("C"), cachedSongs().single().key)
        assertEquals(emptyList<KeyChange>(), repository.observeKeyChanges().first())
    }

    @Test
    fun `a cache that refuses the key still answers KeySet and clears the entry`() = runTest {
        val real = database.setlistDao()
        val refusing = object : SetlistDao by real {
            override suspend fun updateKey(date: String, songId: String, key: String): Int = throw SQLException()
        }
        val repository = seeded(setlistDao = refusing)
        post.answers += body("""{"schemaVersion":1,"ok":true}""")

        assertEquals(SetKeyOutcome.KeySet, repository.setKey(DATE, SongId("red-house"), Key("C")))

        assertEquals(Key("Bb"), cachedSongs().single().key)
        assertEquals(emptyList<KeyChange>(), repository.observeKeyChanges().first())
    }

    @Test
    fun `with no passphrase stored it is AccessRefused, nothing is sent and the key stays`() = runTest {
        val repository = seeded(passphrase = null)

        assertEquals(
            SetKeyOutcome.NotSet(WriteOutcome.AccessRefused),
            repository.setKey(DATE, SongId("red-house"), Key("C")),
        )
        assertTrue(post.bodies.isEmpty())
        assertEquals(Key("Bb"), cachedSongs().single().key)
        val entry = repository.observeKeyChanges().first().single()
        // Not in the cached catalog: the title is the tab's copy.
        assertEquals("Red House", entry.title)
        assertEquals(KeyChange.State.Failed(WriteOutcome.AccessRefused), entry.state)
    }

    @Test
    fun `adds, removes and key changes share one id counter, one dismiss and one write queue in call order`() =
        runTest {
            val repository = seeded()
            post.answers += body(error("busy"))
            repository.setKey(DATE, SongId("zz-no-existe"), Key("A"))
            val failedKey = repository.observeKeyChanges().first().single()
            assertEquals("zz-no-existe", failedKey.title)

            post.answers += ok(position = 2)
            post.answers += body("""{"schemaVersion":1,"ok":true,"position":1}""")
            post.answers += body("""{"schemaVersion":1,"ok":true}""")
            val gate = CompletableDeferred<Unit>().also { post.gate = it }
            val add = async { repository.addSong(DATE, CROSSROADS, Key("A")) }
            val remove = async { repository.removeSong(DATE, SongId("red-house")) }
            val change = async { repository.setKey(DATE, CROSSROADS, Key("G")) }
            repository.observeKeyChanges().first { it.size == 2 }
            post.posted.first { it == 2 }
            // The removal and the key change wait for the add, which the gate holds.
            assertEquals(2, post.bodies.size)
            val ids = listOf(
                failedKey.id,
                repository.observeAdds().first().single().id,
                repository.observeRemoves().first().single().id,
                repository.observeKeyChanges().first().last().id,
            )
            assertEquals(ids.sorted().distinct(), ids)
            gate.complete(Unit)

            assertEquals(AddSongOutcome.Added(2), add.await())
            assertEquals(RemoveSongOutcome.Removed, remove.await())
            assertEquals(SetKeyOutcome.KeySet, change.await())
            assertEquals(listOf("setKey", "addSong", "removeSong", "setKey"), post.bodies.map { actionOf(it) })
            assertEquals(listOf(CROSSROADS to Key("G")), cachedSongs().map { it.songId to it.key })

            assertEquals(listOf(failedKey.id), repository.observeKeyChanges().first().map { it.id })
            repository.dismiss(failedKey.id)
            assertEquals(emptyList<KeyChange>(), repository.observeKeyChanges().first())
        }

    @Test
    fun `two quick changes are sent in call order, and a later failure leaves the earlier confirmed key`() = runTest {
        val repository = seeded()
        post.answers += body("""{"schemaVersion":1,"ok":true}""")
        post.answers += body(error("busy"))
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val first = async { repository.setKey(DATE, SongId("red-house"), Key("C")) }
        val second = async { repository.setKey(DATE, SongId("red-house"), Key("D")) }
        val both = repository.observeKeyChanges().first { it.size == 2 }
        assertEquals(listOf(Key("C"), Key("D")), both.map { it.key })
        assertTrue("ids are unique and increasing", both[0].id < both[1].id)
        gate.complete(Unit)

        assertEquals(SetKeyOutcome.KeySet, first.await())
        assertEquals(SetKeyOutcome.NotSet(WriteOutcome.Unavailable), second.await())
        assertEquals(listOf("C", "D"), post.bodies.map { keyOf(it) })
        assertEquals(Key("C"), cachedSongs().single().key)
        val left = repository.observeKeyChanges().first().single()
        assertEquals(both[1].id, left.id)
        assertEquals(KeyChange.State.Failed(WriteOutcome.Unavailable), left.state)
    }

    @Test
    fun `a sending key change is not dismissed, a failed one is`() = runTest {
        val repository = seeded()
        post.answers += body(error("busy"))
        repository.setKey(DATE, SongId("red-house"), Key("C"))
        val failed = repository.observeKeyChanges().first().single()

        post.answers += body("""{"schemaVersion":1,"ok":true}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }
        val change = async { repository.setKey(DATE, SongId("red-house"), Key("D")) }
        val sending = repository.observeKeyChanges().first { it.size == 2 }.last()

        repository.dismiss(sending.id)
        repository.dismiss(999)
        assertEquals(listOf(failed.id, sending.id), repository.observeKeyChanges().first().map { it.id })
        repository.dismiss(failed.id)
        assertEquals(listOf(sending.id), repository.observeKeyChanges().first().map { it.id })
        gate.complete(Unit)
        change.await()
        assertEquals(emptyList<KeyChange>(), repository.observeKeyChanges().first())
    }

    @Test
    fun `cancelling the caller of a key change does not cancel it`() = runTest {
        val repository = seeded()
        post.answers += body("""{"schemaVersion":1,"ok":true}""")

        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.setKey(DATE, SongId("red-house"), Key("C"))
        }
        caller.cancel()
        jamsDao.observeJams().first { jams -> jams.single().songs.single().key == "C" }

        assertTrue(caller.isCancelled)
        assertEquals(1, post.bodies.size)
        assertEquals(emptyList<KeyChange>(), repository.observeKeyChanges().first())
    }

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
        failureLog: WriteFailureLog = WriteFailureLog { },
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
            failureLog = failureLog,
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

    private fun keyOf(json: String) = Json.parseToJsonElement(json).jsonObject.getValue("key").jsonPrimitive.content

    private fun songIdOf(json: String) =
        Json.parseToJsonElement(json).jsonObject.getValue("songId").jsonPrimitive.content

    /** Answers from [answers] in order; while [gate] is set, each post waits for it first. */
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
        val CROSSROADS = SongId("crossroads")
        val THRILL = SongId("the-thrill-is-gone")
        val HOOCHIE = SongId("hoochie-coochie-man")
        const val TEST_VALUE = "not-a-real-passphrase"

        fun body(text: String) = TransportResult.Body(text)

        fun ok(position: Int, title: String = "Crossroads", artist: String = "Eric Clapton") =
            body("""{"schemaVersion":1,"ok":true,"position":$position,"title":"$title","artist":"$artist"}""")

        fun error(code: String) = """{"schemaVersion":1,"error":{"code":"$code","message":"m"}}"""
    }
}
