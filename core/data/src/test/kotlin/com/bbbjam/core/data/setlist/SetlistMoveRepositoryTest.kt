package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogDao
import com.bbbjam.core.data.cache.CatalogSongEntity
import com.bbbjam.core.data.cache.JamEntity
import com.bbbjam.core.data.cache.JamExtraEntity
import com.bbbjam.core.data.cache.JamRows
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.JamSongEntity
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.cache.toDomain
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
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

class SetlistMoveRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val catalogDao: CatalogDao = database.catalogDao()
    private val post = FakePost()

    @After
    fun tearDown() = database.close()

    @Test
    fun `moveSong publishes Sending then mirrors song slot and extra positions after confirmation`() = runTest {
        val repository = seeded()
        seedFour()
        post.answers += body("""{"schemaVersion":1,"ok":true,"position":1}""")
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val move = async { repository.moveSong(DATE, HOOCHIE, 1) }
        val sending = repository.observeMoves().first { it.isNotEmpty() }.single()
        assertEquals(SetlistMove.State.Sending, sending.state)
        assertEquals("Hoochie Coochie Man", sending.title)
        assertEquals(1, sending.toPosition)
        assertEquals(listOf(1, 2, 3, 4), cachedSongs().map { it.position })
        gate.complete(Unit)

        assertEquals(MoveSongOutcome.Moved, move.await())
        assertEquals(
            listOf("hoochie-coochie-man", "red-house", "crossroads", "pride-and-joy"),
            cachedSongs().map { it.songId.value },
        )
        assertEquals(listOf(1, 2, 3, 4), cachedSongs().map { it.position })
        assertEquals(28, jamsDao.observeJams().first().single().slots.size)
        assertEquals(emptyList<SetlistMove>(), repository.observeMoves().first())
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("moveSong", request.getValue("action").jsonPrimitive.content)
        assertEquals("2026-10-31", request.getValue("date").jsonPrimitive.content)
        assertEquals("hoochie-coochie-man", request.getValue("songId").jsonPrimitive.content)
        assertEquals(1, request.getValue("toPosition").jsonPrimitive.content.toInt())
    }

    @Test
    fun `moveSong failures preserve Room and invalid local targets are rejected without a request`() = runTest {
        val repository = seeded()
        seedFour()
        val before = jamsDao.observeJams().first()
        post.answers += error("unordered_setlist")

        assertEquals(
            MoveSongOutcome.NotMoved(WriteOutcome.Rejected("unordered_setlist")),
            repository.moveSong(DATE, HOOCHIE, 2),
        )
        assertEquals(before, jamsDao.observeJams().first())
        val failed = repository.observeMoves().first().single()
        assertEquals(SetlistMove.State.Failed(WriteOutcome.Rejected("unordered_setlist")), failed.state)

        assertEquals(
            MoveSongOutcome.NotMoved(WriteOutcome.Rejected("invalid_position")),
            repository.moveSong(DATE, HOOCHIE, 0),
        )
        assertEquals("the locally invalid target must not be sent", 1, post.bodies.size)
        assertTrue(repository.observeMoves().first().all { it.state is SetlistMove.State.Failed })
        repository.dismiss(failed.id)
        assertEquals(1, repository.observeMoves().first().size)
    }

    private suspend fun seedFour() {
        val date = DATE.toString()
        val songs = listOf(
            JamSongEntity(date, 1, "red-house", "Red House", "Jimi Hendrix", "Bb"),
            JamSongEntity(date, 2, "crossroads", "Crossroads", "Eric Clapton", "A"),
            JamSongEntity(date, 3, HOOCHIE.value, "Hoochie Coochie Man", "Muddy Waters", "A"),
            JamSongEntity(date, 4, "pride-and-joy", "Pride and Joy", "Stevie Ray Vaughan", "E"),
        )
        val extras = listOf(JamExtraEntity(date, 3, 1, "Hugo", "Trompeta"), JamExtraEntity(date, 4, 1, "Eva", "Saxo"))
        jamsDao.replaceJams(JamRows(listOf(jam("AVAILABLE")), songs, (1..4).flatMap { slots(it) }, extras), state())
    }

    private suspend fun TestScope.seeded(): DefaultSetlistRepository {
        catalogDao.replaceCatalog(
            listOf(
                CatalogSongEntity("crossroads", 1, "Crossroads", "Eric Clapton", "A", null, null, emptyList(), null),
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
        store.save("local-test-passphrase")
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
        val DATE = LocalDate.of(2026, 10, 31)
        val HOOCHIE = SongId("hoochie-coochie-man")

        fun body(text: String) = TransportResult.Body(text)

        fun error(code: String) = TransportResult.Body("""{"schemaVersion":1,"error":{"code":"$code","message":"m"}}""")
    }
}
