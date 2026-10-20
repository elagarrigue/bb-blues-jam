package com.bbbjam.core.data.catalog.edit

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogSongEntity
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.CatalogSnapshot
import com.bbbjam.core.data.catalog.RefreshOutcome
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.data.setlist.QueuedPostTransport
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultCatalogEditRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val catalogDao = database.catalogDao()
    private val post = QueuedPostTransport()
    private val catalog = CountingCatalog()
    private val fields = SongFields("Red House", "Jimi Hendrix", Key("Bb"))

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `create sends the three fields and no id, then mirrors the echo and refreshes the catalog`() = runTest {
        val repository = repository()
        post.answers += created("red-house", "Red House", "Jimi Hendrix", "Bb")

        val outcome = repository.createSong(fields)
        testScheduler.advanceUntilIdle()

        assertEquals(CreateSongOutcome.Created(SongId("red-house")), outcome)
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("createSong", request.getValue("action").jsonPrimitive.content)
        assertEquals("Red House", request.getValue("title").jsonPrimitive.content)
        assertEquals("Jimi Hendrix", request.getValue("artist").jsonPrimitive.content)
        assertEquals("Bb", request.getValue("defaultKey").jsonPrimitive.content)
        assertFalse("id" in request)
        val rows = catalogDao.observeSongs().first()
        assertEquals(listOf("red-house"), rows.map { it.id })
        assertEquals(1, rows.single().sheetOrder)
        assertEquals(1, catalog.refreshes)
    }

    @Test
    fun `update sends the id, mirrors only the three fields and keeps tempo, tags, difficulty and songsterr`() =
        runTest {
            catalogDao.insertSongs(listOf(row("crossroads", 1)))
            val repository = repository()
            post.answers += updated("crossroads", "Crossroads", "Cream", "B", changed = true)

            val outcome = repository.updateSong(SongId("crossroads"), SongFields("Crossroads", "Cream", Key("B")))
            testScheduler.advanceUntilIdle()

            assertEquals(UpdateSongOutcome.Updated(true), outcome)
            val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
            assertEquals("updateSong", request.getValue("action").jsonPrimitive.content)
            assertEquals("crossroads", request.getValue("id").jsonPrimitive.content)
            assertEquals(
                row("crossroads", 1).copy(artist = "Cream", defaultKey = "B"),
                catalogDao.observeSongs().first().single(),
            )
            assertEquals(1, catalog.refreshes)
        }

    @Test
    fun `an unchanged update is still a success`() = runTest {
        catalogDao.insertSongs(listOf(row("crossroads", 1)))
        post.answers += updated("crossroads", "Crossroads", "Eric Clapton", "A", changed = false)

        assertEquals(
            UpdateSongOutcome.Updated(false),
            repository().updateSong(SongId("crossroads"), SongFields("Crossroads", "Eric Clapton", Key("A"))),
        )
    }

    @Test
    fun `delete sends the id and drops the song only after the server confirms`() = runTest {
        catalogDao.insertSongs(listOf(row("a", 1), row("b", 2)))
        val repository = repository()
        post.answers += TransportResult.Body("""{"schemaVersion":1,"ok":true,"id":"a"}""")

        assertEquals(DeleteSongOutcome.Deleted, repository.deleteSong(SongId("a")))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("b"), catalogDao.observeSongs().first().map { it.id })
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("deleteSong", request.getValue("action").jsonPrimitive.content)
        assertEquals("a", request.getValue("id").jsonPrimitive.content)
        assertEquals(1, catalog.refreshes)
    }

    @Test
    fun `every refusal leaves Room alone and does not refresh`() = runTest {
        catalogDao.insertSongs(listOf(row("a", 1)))
        val before = catalogDao.observeSongs().first()
        val repository = repository()
        post.answers += error("song_exists")
        post.answers += error("song_in_future_jam")
        post.answers += TransportResult.Failed(DataFailure.Offline)
        post.answers += error("unknown_song")

        assertEquals(
            CreateSongOutcome.NotCreated(WriteOutcome.Rejected("song_exists")),
            repository.createSong(fields),
        )
        assertEquals(
            DeleteSongOutcome.NotDeleted(WriteOutcome.Rejected("song_in_future_jam")),
            repository.deleteSong(SongId("a")),
        )
        assertEquals(
            UpdateSongOutcome.NotUpdated(WriteOutcome.Offline),
            repository.updateSong(SongId("a"), fields),
        )
        assertEquals(
            DeleteSongOutcome.NotDeleted(WriteOutcome.Rejected("unknown_song")),
            repository.deleteSong(SongId("a")),
        )
        testScheduler.advanceUntilIdle()

        assertEquals(before, catalogDao.observeSongs().first())
        assertEquals(0, catalog.refreshes)
    }

    @Test
    fun `a script without the action is ScriptOutdated for each call`() = runTest {
        val repository = repository()
        repeat(3) { post.answers += error("unknown_action") }

        assertEquals(CreateSongOutcome.ScriptOutdated, repository.createSong(fields))
        assertEquals(UpdateSongOutcome.ScriptOutdated, repository.updateSong(SongId("a"), fields))
        assertEquals(DeleteSongOutcome.ScriptOutdated, repository.deleteSong(SongId("a")))
        assertTrue(catalogDao.observeSongs().first().isEmpty())
    }

    @Test
    fun `an invalid echo is Unavailable, writes nothing to Room and still refreshes`() = runTest {
        val repository = repository()
        post.answers += created("Bad Id", "Red House", "Jimi Hendrix", "Bb")
        post.answers += created("red-house", "Red House", "Jimi Hendrix", "H")
        post.answers += TransportResult.Body("""{"schemaVersion":1,"ok":true}""")

        assertEquals(CreateSongOutcome.NotCreated(WriteOutcome.Unavailable), repository.createSong(fields))
        assertEquals(CreateSongOutcome.NotCreated(WriteOutcome.Unavailable), repository.createSong(fields))
        assertEquals(UpdateSongOutcome.NotUpdated(WriteOutcome.Unavailable), repository.updateSong(SongId("a"), fields))
        testScheduler.advanceUntilIdle()

        assertTrue(catalogDao.observeSongs().first().isEmpty())
        assertEquals(3, catalog.refreshes)
    }

    @Test
    fun `an update echo for another song is not a confirmation`() = runTest {
        catalogDao.insertSongs(listOf(row("a", 1)))
        post.answers += updated("b", "x", "y", "A", changed = true)

        assertEquals(
            UpdateSongOutcome.NotUpdated(WriteOutcome.Unavailable),
            repository().updateSong(SongId("a"), fields),
        )
        assertEquals(row("a", 1), catalogDao.observeSongs().first().single())
    }

    @Test
    fun `without a stored passphrase nothing is sent`() = runTest {
        val repository = repository(passphrase = null)

        assertEquals(CreateSongOutcome.NotCreated(WriteOutcome.AccessRefused), repository.createSong(fields))
        assertTrue(post.bodies.isEmpty())
    }

    @Test
    fun `cancelling the caller does not cancel the write nor its mirror`() = runTest {
        val repository = repository()
        val gate = CompletableDeferred<Unit>().also { post.gate = it }
        post.answers += created("red-house", "Red House", "Jimi Hendrix", "Bb")

        val caller = async(start = CoroutineStart.UNDISPATCHED) { repository.createSong(fields) }
        post.posted.first { it == 1 }
        caller.cancel()
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("red-house"), catalogDao.observeSongs().first().map { it.id })
    }

    @Test
    fun `writes run one at a time in call order`() = runTest {
        val repository = repository()
        val gate = CompletableDeferred<Unit>().also { post.gate = it }
        post.answers += created("one", "One", "A", "A")
        post.answers += created("two", "Two", "B", "B")

        val first = async(start = CoroutineStart.UNDISPATCHED) { repository.createSong(fields) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { repository.createSong(fields) }
        post.posted.first { it == 1 }
        testScheduler.advanceUntilIdle()
        assertEquals(1, post.bodies.size)
        gate.complete(Unit)

        assertEquals(CreateSongOutcome.Created(SongId("one")), first.await())
        assertEquals(CreateSongOutcome.Created(SongId("two")), second.await())
        assertEquals(listOf("one", "two"), catalogDao.observeSongs().first().map { it.id })
    }

    private fun row(id: String, order: Int) = CatalogSongEntity(
        id = id,
        sheetOrder = order,
        title = "Crossroads",
        artist = "Eric Clapton",
        defaultKey = "A",
        tempo = "FAST",
        difficulty = "EASY",
        tags = listOf("classic"),
        songsterrId = 7L,
    )

    private suspend fun TestScope.repository(
        passphrase: String? = "not-a-real-passphrase",
    ): DefaultCatalogEditRepository {
        val store = AdminCredentialStore(
            AdminCredentialStore.dataStore(backgroundScope) {
                File(folder.root, "${AdminCredentialStore.FILE_NAME}.preferences_pb")
            },
        )
        if (passphrase != null) store.save(passphrase)
        return DefaultCatalogEditRepository(
            AdminWriter(post, store),
            database.catalogEditDao(),
            catalog,
            DataScope(backgroundScope),
        )
    }

    /** A catalog that only counts the refreshes asked of it. */
    private class CountingCatalog : CatalogRepository {
        var refreshes = 0

        override fun observeCatalog(): Flow<CatalogSnapshot> = emptyFlow()

        override suspend fun refresh(): RefreshOutcome {
            refreshes++
            return RefreshOutcome.Failed(DataFailure.Offline)
        }
    }

    private companion object {
        fun body(text: String) = TransportResult.Body(text)

        fun error(code: String) = body("""{"schemaVersion":1,"error":{"code":"$code","message":"m"}}""")

        fun created(id: String, title: String, artist: String, key: String) = body(
            """{"schemaVersion":1,"ok":true,"song":{"id":"$id","title":"$title","artist":"$artist",""" +
                """"defaultKey":"$key"}}""",
        )

        fun updated(id: String, title: String, artist: String, key: String, changed: Boolean) = body(
            """{"schemaVersion":1,"ok":true,"song":{"id":"$id","title":"$title","artist":"$artist",""" +
                """"defaultKey":"$key"},"changed":$changed}""",
        )
    }
}
