package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.cache.JamEntity
import com.bbbjam.core.data.cache.JamRows
import com.bbbjam.core.data.cache.JamSongEntity
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.data.setlist.engine.SetlistMutationQueue
import com.bbbjam.core.data.setlist.engine.WriteFailureLog
import com.bbbjam.core.data.setlist.played.PlayedChange
import com.bbbjam.core.data.setlist.played.SetSongPlayedOutcome
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import java.io.File
import java.time.LocalDate
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SetlistPlayedChangesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val post = QueuedPostTransport()

    @Test
    fun `a confirmed mark is sent, mirrored after the echo and the entry removed`() = runTest {
        val repository = seeded()
        post.answers += echo(played = true, changed = true)
        val gate = CompletableDeferred<Unit>().also { post.gate = it }

        val call = async { repository.setSongPlayed(DATE, HOOCHIE, true) }
        val sending = repository.observePlayedChanges().first { it.isNotEmpty() }.single()
        assertEquals(PlayedChange.State.Sending, sending.state)
        assertEquals(true, sending.played)
        assertEquals("Hoochie Coochie Man", sending.title)
        post.posted.first { it == 1 }
        assertEquals(false, played())
        gate.complete(Unit)

        assertEquals(SetSongPlayedOutcome.PlayedSet(true), call.await())
        assertEquals(true, played())
        assertEquals(emptyList<PlayedChange>(), repository.observePlayedChanges().first())
        val request = Json.parseToJsonElement(post.bodies.single()).jsonObject
        assertEquals("setSongPlayed", request.getValue("action").jsonPrimitive.content)
        assertEquals("2026-10-31", request.getValue("date").jsonPrimitive.content)
        assertEquals("hoochie-coochie-man", request.getValue("songId").jsonPrimitive.content)
        assertEquals("true", request.getValue("played").jsonPrimitive.content)
    }

    @Test
    fun `an unmark mirrors false and an unchanged echo is still confirmed`() = runTest {
        val repository = seeded(played = true)
        post.answers += echo(played = false, changed = true)
        post.answers += echo(played = false, changed = false)

        assertEquals(SetSongPlayedOutcome.PlayedSet(false), repository.setSongPlayed(DATE, HOOCHIE, false))
        assertEquals(false, played())
        assertEquals(SetSongPlayedOutcome.PlayedSet(false), repository.setSongPlayed(DATE, HOOCHIE, false))
    }

    @Test
    fun `a refusal fails the entry and leaves Room alone, and an offline write too`() = runTest {
        val repository = seeded()
        post.answers += error("jam_not_editable")
        post.answers += TransportResult.Failed(DataFailure.Offline)

        assertEquals(
            SetSongPlayedOutcome.NotSet(WriteOutcome.Rejected("jam_not_editable")),
            repository.setSongPlayed(DATE, HOOCHIE, true),
        )
        assertEquals(SetSongPlayedOutcome.NotSet(WriteOutcome.Offline), repository.setSongPlayed(DATE, HOOCHIE, true))

        assertEquals(false, played())
        assertEquals(
            listOf(
                PlayedChange.State.Failed(WriteOutcome.Rejected("jam_not_editable")),
                PlayedChange.State.Failed(WriteOutcome.Offline),
            ),
            repository.observePlayedChanges().first().map { it.state },
        )
    }

    @Test
    fun `an echo of the other value is not a confirmation`() = runTest {
        val repository = seeded()
        post.answers += echo(played = false, changed = true)

        assertEquals(
            SetSongPlayedOutcome.NotSet(WriteOutcome.Unavailable),
            repository.setSongPlayed(DATE, HOOCHIE, true),
        )
        assertEquals(false, played())
    }

    @Test
    fun `an old script answering unknown_action is ScriptOutdated, changes nothing and keeps an Outdated entry`() =
        runTest {
            val repository = seeded()
            post.answers += error("unknown_action")

            assertEquals(SetSongPlayedOutcome.ScriptOutdated, repository.setSongPlayed(DATE, HOOCHIE, true))

            assertEquals(false, played())
            val entry = repository.observePlayedChanges().first().single()
            assertEquals(PlayedChange.State.Outdated, entry.state)
            repository.dismissPlayedChange(entry.id)
            assertEquals(emptyList<PlayedChange>(), repository.observePlayedChanges().first())
        }

    @Test
    fun `writes of different kinds reach the server in call order and dismiss takes only its own kind`() = runTest {
        val repository = seeded()
        post.answers += error("song_played")
        post.answers += TransportResult.Body("""{"schemaVersion":1,"ok":true}""")
        post.answers += echo(played = false, changed = true)

        repository.setSongPlayed(DATE, HOOCHIE, true)
        repository.setKey(DATE, HOOCHIE, Key("G"))
        repository.setSongPlayed(DATE, HOOCHIE, false)

        val actions = post.bodies.map {
            Json.parseToJsonElement(it).jsonObject.getValue("action").jsonPrimitive.content
        }
        assertEquals(listOf("setSongPlayed", "setKey", "setSongPlayed"), actions)
        val failed = repository.observePlayedChanges().first().single()
        assertEquals(PlayedChange.State.Failed(WriteOutcome.Rejected("song_played")), failed.state)
        repository.dismissKeyChange(failed.id)
        assertEquals(1, repository.observePlayedChanges().first().size)
        repository.dismissPlayedChange(failed.id)
        assertEquals(0, repository.observePlayedChanges().first().size)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun played(): Boolean = jamsDao.observeJams().first().single().songs.single().played

    private suspend fun TestScope.seeded(played: Boolean = false): DefaultSetlistRepository {
        jamsDao.replaceJams(
            JamRows(
                listOf(JamEntity("2026-10-31", 1, "21:00", "La Macanuda", "DRAFT", "AVAILABLE", null, 0)),
                listOf(
                    JamSongEntity(
                        "2026-10-31",
                        1,
                        HOOCHIE.value,
                        "Hoochie Coochie Man",
                        "Muddy Waters",
                        "A",
                        played = played,
                    ),
                ),
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
            WriteFailureLog { },
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 10, 31)
        val HOOCHIE = SongId("hoochie-coochie-man")

        fun body(text: String) = TransportResult.Body(text)

        fun error(code: String) = body("""{"schemaVersion":1,"error":{"code":"$code","message":"m"}}""")

        fun echo(played: Boolean, changed: Boolean) =
            body("""{"schemaVersion":1,"ok":true,"played":$played,"changed":$changed}""")
    }
}
