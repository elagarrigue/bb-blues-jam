package com.bbbjam.core.data.cache

import app.cash.turbine.test
import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.data.jams.JamsMapper
import com.bbbjam.core.data.remote.JamDto
import com.bbbjam.core.data.remote.SetlistRowDto
import com.bbbjam.core.data.remote.SlotsDto
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Room on the JVM with the bundled SQLite driver, in memory. */
class JamsDaoTest {
    private val database = Fixtures.inMemoryDatabase()
    private val dao = database.jamsDao()
    private val catalogDao = database.catalogDao()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `every mapped jam round-trips through the rows, setlist states, slot columns and extras included`() = runTest {
        val mapped = JamsMapper.map(Fixtures.sampleJams("jams-edge.json")).jams

        dao.replaceJams(jamRows(mapped.map { it.jam to it.slotColumns }), state(fetchedAt = 1))

        val stored = dao.observeJams().first()
        assertEquals(mapped.map { it.jam }, stored.map { it.toDomain() })
        val crossroads = stored.first().slots.filter { it.position == 2 }
        assertEquals(listOf(0, 2, 3, 4, 5, 6), crossroads.map { it.columnIndex })
        assertEquals("Pedro", crossroads.first().musicianName)
        assertEquals(listOf(1, 2), stored.first().extras.map { it.entryOrder })
    }

    @Test
    fun `a lone guitar keeps the column it was typed in, Guitarra 2 or Guitarra 1`() = runTest {
        fun guitars(first: String, second: String) = SlotsDto(first, second, null, null, null, null, null)
        val rows = listOf(
            SetlistRowDto("1", "crossroads", "Crossroads", "Eric Clapton", "A", guitars("-", "Pedro"), null),
            SetlistRowDto(
                "2",
                "got-my-mojo-working",
                "Got My Mojo Working",
                "Muddy Waters",
                "E",
                guitars("Ana", "-"),
                null,
            ),
        )
        val mapped = JamsMapper.map(listOf(JamDto("2026-07-25", "21:00", "La Macanuda", "PUBLICADA", rows, null)))
            .jams.single()

        dao.replaceJams(jamRows(listOf(mapped.jam to mapped.slotColumns)), state(fetchedAt = 1))

        val stored = dao.observeJams().first().single()
        val guitars = stored.slots.filter { it.instrument == "GUITAR" }.associate { it.position to it.columnIndex }
        assertEquals(mapOf(1 to 1, 2 to 0), guitars)
        assertEquals(mapped.jam, stored.toDomain())
    }

    @Test
    fun `a replace is one emission, and deleting a jam cascades to its songs, slots and extras`() = runTest {
        val edge = JamsMapper.map(Fixtures.sampleJams("jams-edge.json")).jams
        val published = edge.first()
        val asDraft = published.jam.copy(status = JamStatus.DRAFT, setlist = Setlist.Withheld)

        dao.observeJams().test {
            assertEquals(emptyList<JamWithChildren>(), awaitItem())

            dao.replaceJams(jamRows(edge.map { it.jam to it.slotColumns }), state(fetchedAt = 1))
            assertEquals(5, awaitItem().size)

            dao.replaceJams(jamRows(listOf(asDraft to emptyMap())), state(fetchedAt = 2))
            val withheld = awaitItem().single()
            assertEquals(emptyList<JamSongResolved>(), withheld.songs)
            assertEquals(emptyList<JamSlotEntity>(), withheld.slots)
            assertEquals(emptyList<JamExtraEntity>(), withheld.extras)

            // The same keys again: only possible because the previous children were deleted.
            dao.replaceJams(jamRows(listOf(published.jam to published.slotColumns)), state(fetchedAt = 3))
            assertEquals(3, awaitItem().single().songs.size)
            dao.replaceJams(jamRows(listOf(published.jam to published.slotColumns)), state(fetchedAt = 4))
            val again = awaitItem().single()
            assertEquals(3, again.songs.size)
            assertEquals(6 + 6 + 7, again.slots.size)
            assertEquals(2, again.extras.size)
            expectNoEvents()
        }
        assertEquals(state(fetchedAt = 4), dao.syncState("jams"))
    }

    @Test
    fun `titles resolve from the catalog, else from the tab copy, and follow a catalog replace`() = runTest {
        val jam = JamsMapper.map(Fixtures.sampleJams("jams-edge.json")).jams.first()
        dao.replaceJams(jamRows(listOf(jam.jam to jam.slotColumns)), state(fetchedAt = 1))

        dao.observeJams().test {
            assertEquals("Crossroads", awaitItem().single().titleOf(2))

            catalogDao.replaceCatalog(
                listOf(catalogSong("crossroads", "Crossroads (catálogo)", "Cream")),
                catalogState(),
            )
            val resolved = awaitItem().single()
            assertEquals("Crossroads (catálogo)", resolved.titleOf(2))
            assertEquals("Cream", resolved.songs.single { it.position == 2 }.artist)
            assertEquals("The Thrill Is Gone", resolved.titleOf(1))
            // The key stays the tab's: the catalog's default key never reaches a setlist (D-08).
            assertEquals("A", resolved.songs.single { it.position == 2 }.key)

            catalogDao.replaceCatalog(emptyList(), catalogState())
            assertEquals("Crossroads", awaitItem().single().titleOf(2))
        }
    }

    @Test
    fun `recording a jams failure keeps the jams and leaves the catalog row alone`() = runTest {
        val jam = JamsMapper.map(Fixtures.sampleJams("jams-seed.json")).jams.single()
        dao.replaceJams(jamRows(listOf(jam.jam to jam.slotColumns)), state(fetchedAt = 1_000))
        catalogDao.replaceCatalog(emptyList(), catalogState())

        dao.recordFailure("jams", attemptedAt = 5_000, failure = "Offline")

        assertEquals(listOf(jam.jam), dao.observeJams().first().map { it.toDomain() })
        assertEquals(SyncStateEntity("jams", 1_000, 5_000, "Offline"), dao.syncState("jams"))
        assertEquals(catalogState(), catalogDao.syncState("catalog"))
        dao.observeSyncState("jams").test {
            assertEquals(SyncStateEntity("jams", 1_000, 5_000, "Offline"), awaitItem())
        }
    }

    @Test
    fun `a failure with nothing cached records the attempt only`() = runTest {
        dao.recordFailure("jams", attemptedAt = 5_000, failure = "NotConfigured")

        assertEquals(SyncStateEntity("jams", null, 5_000, "NotConfigured"), dao.syncState("jams"))
        assertEquals(emptyList<Jam>(), dao.observeJams().first())
        assertNull(dao.syncState("catalog"))
    }

    private fun JamWithChildren.titleOf(position: Int): String = songs.single { it.position == position }.title

    private fun catalogSong(id: String, title: String, artist: String) =
        CatalogSongEntity(id, 1, title, artist, "E", null, null, emptyList(), null)

    private fun catalogState() = SyncStateEntity("catalog", 7, 7, null)

    private fun state(fetchedAt: Long) = SyncStateEntity("jams", fetchedAt, fetchedAt, null)
}
