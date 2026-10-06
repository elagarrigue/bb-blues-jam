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
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.JamSongEntity
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.cache.toDomain
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
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
 * The add mutation with no UI: a real in-memory Room cache, a real DataStore file, the real
 * [AdminWriter] and a fake POST transport. Writes run in the test's background scope.
 */
class DefaultSetlistRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val catalogDao = database.catalogDao()
    private val post = FakePost()

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

    // ---- fixtures ----

    /** A repository over a cache holding the catalog and the upcoming draft with Red House at 1. */
    private suspend fun TestScope.seeded(passphrase: String? = TEST_VALUE): DefaultSetlistRepository {
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
            database.setlistDao(),
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
        const val TEST_VALUE = "not-a-real-passphrase"

        fun body(text: String) = TransportResult.Body(text)

        fun ok(position: Int, title: String = "Crossroads", artist: String = "Eric Clapton") =
            body("""{"schemaVersion":1,"ok":true,"position":$position,"title":"$title","artist":"$artist"}""")

        fun error(code: String) = """{"schemaVersion":1,"error":{"code":"$code","message":"m"}}"""
    }
}
