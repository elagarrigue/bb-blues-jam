package com.bbbjam.core.data.setlist

import com.bbbjam.core.data.DataScope
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.admin.AdminCredentialStore
import com.bbbjam.core.data.admin.AdminWriter
import com.bbbjam.core.data.cache.CatalogSongEntity
import com.bbbjam.core.data.cache.JamEntity
import com.bbbjam.core.data.cache.JamRows
import com.bbbjam.core.data.cache.JamSlotEntity
import com.bbbjam.core.data.cache.JamSongEntity
import com.bbbjam.core.data.cache.SyncStateEntity
import com.bbbjam.core.data.remote.TransportResult
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Each typed dismiss of a setlist contract removes a failed entry of its own kind only (the shared
 * `dismiss(id)` fan-out is gone): a sending entry, an unknown id and an id of another kind stay.
 */
class SetlistDismissTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val post = QueuedPostTransport()

    @After
    fun tearDown() = database.close()

    private class Kind(
        val name: String,
        val write: suspend () -> Unit,
        val ids: suspend () -> List<Long>,
        val dismiss: (Long) -> Unit,
    )

    private fun kinds(repository: DefaultSetlistRepository): List<Kind> = songKinds(repository) + jamKinds(repository)

    private fun songKinds(repository: DefaultSetlistRepository): List<Kind> {
        val song = SongId("red-house")
        val bass = Instrument.BASS
        return listOf(
            Kind(
                "add",
                { repository.addSong(DATE, SongId("crossroads"), Key("A")) },
                { repository.observeAdds().first().map { it.id } },
                repository::dismissAdd,
            ),
            Kind(
                "remove",
                { repository.removeSong(DATE, song) },
                { repository.observeRemoves().first().map { it.id } },
                repository::dismissRemove,
            ),
            Kind(
                "key",
                { repository.setKey(DATE, song, Key("C")) },
                { repository.observeKeyChanges().first().map { it.id } },
                repository::dismissKeyChange,
            ),
            Kind(
                "lineup",
                { repository.setSlotCount(DATE, song, bass, 1) },
                { repository.observeLineupChanges().first().map { it.id } },
                repository::dismissLineupChange,
            ),
            Kind(
                "assignment",
                { repository.assignSlot(DATE, song, bass, SlotPosition(1), MusicianName.parseOrNull("Ana")!!) },
                { repository.observeAssignments().first().map { it.id } },
                repository::dismissAssignment,
            ),
            Kind(
                "clear",
                { repository.clearSlot(DATE, song, bass, SlotPosition(1), "Ana") },
                { repository.observeSlotClears().first().map { it.id } },
                repository::dismissSlotClear,
            ),
            Kind(
                "move",
                { repository.moveSong(DATE, song, 1) },
                { repository.observeMoves().first().map { it.id } },
                repository::dismissMove,
            ),
            Kind(
                "extra",
                { repository.addExtraParticipant(DATE, song, "Hugo", "Trompeta") },
                { repository.observeExtraParticipantChanges().first().map { it.id } },
                repository::dismissExtraParticipantChange,
            ),
        )
    }

    private fun jamKinds(repository: DefaultSetlistRepository): List<Kind> = listOf(
        Kind(
            "publish",
            { repository.publishSetlist(DATE) },
            { repository.observePublishes().first().map { it.id } },
            repository::dismissPublish,
        ),
        Kind(
            "status",
            { repository.setJamStatus(DATE, JamStatus.PUBLISHED) },
            { repository.observeJamStatusChanges().first().map { it.id } },
            repository::dismissJamStatusChange,
        ),
        Kind(
            "edit",
            { repository.updateJam(DATE, NEW, LocalTime.of(20, 0), "Bar") },
            { repository.observeJamEdits().first().map { it.id } },
            repository::dismissJamEdit,
        ),
    )

    @Test
    fun `a dismiss removes a failed entry of its own kind only, never a sending one`() = runTest {
        val repository = seeded()
        val kinds = kinds(repository)
        repeat(kinds.size * 2) { post.answers += TransportResult.Body(BUSY) }
        val gate = CompletableDeferred<Unit>().also { post.gate = it }
        val writes = kinds.map { kind -> async(start = CoroutineStart.UNDISPATCHED) { kind.write() } }
        kinds.forEach { kind -> waitForEntry(kind) }

        val sending = kinds.associate { it.name to it.ids().single() }
        assertEquals("one entry id per kind, all different", kinds.size, sending.values.toSet().size)
        assertUntouched(kinds, sending, "while sending")

        gate.complete(Unit)
        writes.awaitAll()
        val failed = kinds.associate { it.name to it.ids().single() }
        assertEquals("the ids do not change when the writes end", sending, failed)
        assertUntouched(kinds, failed, "after failing, through another kind's dismiss")

        kinds.forEach { kind ->
            kind.dismiss(failed.getValue(kind.name))
            assertEquals("${kind.name} removed by its own dismiss", emptyList<Long>(), kind.ids())
            kinds.filterNot { it === kind }.forEach { other ->
                if (other.ids().isNotEmpty()) {
                    assertEquals(
                        "${other.name} untouched by ${kind.name}'s dismiss",
                        listOf(failed.getValue(other.name)),
                        other.ids(),
                    )
                }
            }
        }
    }

    /**
     * No dismiss of another kind, and no dismiss of an unknown id, removes an entry. The sending and
     * failed states are checked by the caller: an own-kind dismiss is only issued once the entry failed.
     */
    private suspend fun assertUntouched(kinds: List<Kind>, ids: Map<String, Long>, phase: String) {
        kinds.forEach { kind ->
            kind.dismiss(UNKNOWN_ID)
            kinds.filterNot { it === kind }.forEach { other -> kind.dismiss(ids.getValue(other.name)) }
        }
        kinds.forEach { kind ->
            assertEquals("${kind.name} $phase", listOf(ids.getValue(kind.name)), kind.ids())
        }
        // An own-kind dismiss of a sending entry is a no-op as well (only meaningful before the writes end).
        if (phase == "while sending") {
            kinds.forEach { kind -> kind.dismiss(ids.getValue(kind.name)) }
            kinds.forEach { kind ->
                assertEquals("${kind.name} sending, own dismiss", listOf(ids.getValue(kind.name)), kind.ids())
            }
        }
    }

    private suspend fun waitForEntry(kind: Kind) {
        while (kind.ids().isEmpty()) yield()
    }

    private suspend fun TestScope.seeded(): DefaultSetlistRepository {
        database.catalogDao().replaceCatalog(
            listOf(
                CatalogSongEntity("crossroads", 1, "Crossroads", "Eric Clapton", "A", null, null, emptyList(), null),
            ),
            SyncStateEntity("catalog", 1, 1, null),
        )
        val redHouse = JamSongEntity(DATE.toString(), 1, "red-house", "Red House", "Jimi Hendrix", "Bb")
        val slots = Lineup.DEFAULT_INSTRUMENTS.mapIndexed { column, instrument ->
            JamSlotEntity(DATE.toString(), 1, column, instrument.name, null)
        }
        val jam = JamEntity(DATE.toString(), 1, "21:00", "La Macanuda", "DRAFT", "AVAILABLE", null, 0)
        jamsDao.replaceJams(
            JamRows(listOf(jam), listOf(redHouse), slots, emptyList()),
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
        )
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 10, 31)
        val NEW: LocalDate = LocalDate.of(2026, 11, 7)
        const val UNKNOWN_ID = 999L
        const val BUSY = """{"schemaVersion":1,"error":{"code":"busy","message":"m"}}"""
    }
}
