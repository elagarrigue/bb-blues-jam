package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.CatalogSongEntity
import com.bbbjam.core.data.cache.JamEntity
import com.bbbjam.core.data.cache.JamExtraEntity
import com.bbbjam.core.data.cache.JamRows
import com.bbbjam.core.data.cache.JamSongEntity
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.remote.AppsScriptPostTransport
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.SongId
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SetlistExtraParticipantRepositoryTest {
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
    fun `add and remove mirror only the confirmed extras payload`() = runTest {
        val repository = seeded()
        post.answers += body("""{"schemaVersion":1,"ok":true,"extras":[{"name":"Ana","instrument":"percusion"}]}""")
        assertEquals(
            ExtraParticipantOutcome.Changed,
            repository.addExtraParticipant(DATE, SONG, " Ana ", " percusion "),
        )
        assertEquals(listOf("Ana"), cachedExtras())
        assertEquals("addExtraParticipant", post.bodies.single().substringAfter("\"action\":\"").substringBefore('"'))

        post.answers += body("""{"schemaVersion":1,"ok":true,"extras":[]}""")
        assertEquals(
            ExtraParticipantOutcome.Changed,
            repository.removeExtraParticipant(DATE, SONG, 1, "Ana", "percusion"),
        )
        assertTrue(cachedExtras().isEmpty())
        assertTrue(post.bodies.last().contains("removeExtraParticipant"))
    }

    @Test
    fun `malformed confirmation leaves Room unchanged and exposes a dismissible failure`() = runTest {
        val repository = seeded()
        database.setlistDao().replaceExtras(
            DATE.toString(),
            SONG.value,
            listOf(JamExtraEntity(DATE.toString(), 1, 1, "Juan", "saxo")),
        )
        val before = cachedExtras()
        post.answers += body("""{"schemaVersion":1,"ok":true,"extras":[{"name":"Ana","instrument":false}]}""")
        assertEquals(
            ExtraParticipantOutcome.NotChanged(WriteOutcome.Unavailable),
            repository.addExtraParticipant(DATE, SONG, "Ana", "saxo"),
        )
        assertEquals(before, cachedExtras())
        val failure = repository.observeExtraParticipantChanges().first().single()
        assertEquals(ExtraParticipantChange.State.Failed(WriteOutcome.Unavailable), failure.state)
        repository.dismiss(failure.id)
        assertTrue(repository.observeExtraParticipantChanges().first().isEmpty())
    }

    private suspend fun TestScope.seeded(): DefaultSetlistRepository {
        catalogDao.replaceCatalog(emptyList(), SyncStateEntity("catalog", 1, 1, null))
        jamsDao.replaceJams(
            JamRows(
                listOf(JamEntity(DATE.toString(), 1, "21:00", "La Macanuda", "DRAFT", "AVAILABLE", null, 0)),
                listOf(JamSongEntity(DATE.toString(), 1, SONG.value, "Red House", "Jimi Hendrix", "Bb")),
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
        store.save("test-passphrase")
        return DefaultSetlistRepository(
            AdminWriter(post, store),
            database.setlistDao(),
            catalogDao,
            DataScope(backgroundScope),
        )
    }

    private suspend fun cachedExtras() = jamsDao.observeJams().first().single().extras.map(JamExtraEntity::name)

    private fun body(value: String) = TransportResult.Body(value)

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
        val SONG = SongId("red-house")
    }
}
