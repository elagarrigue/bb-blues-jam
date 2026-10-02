package com.bbbjam.core.data.cache

import app.cash.turbine.test
import com.bbbjam.core.data.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Room on the JVM with the bundled SQLite driver, in memory. */
class CatalogDaoTest {
    private val database = Fixtures.inMemoryDatabase()
    private val dao = database.catalogDao()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a replace is atomic, with one emission per replace and no empty catalog in between`() = runTest {
        dao.observeSongs().test {
            assertEquals(emptyList<CatalogSongEntity>(), awaitItem())

            dao.replaceCatalog(listOf(song("a", 1), song("b", 2), song("c", 3)), state(fetchedAt = 1))
            assertEquals(listOf("a", "b", "c"), awaitItem().map { it.id })

            dao.replaceCatalog(listOf(song("d", 1), song("a", 2)), state(fetchedAt = 2))
            assertEquals(listOf("d", "a"), awaitItem().map { it.id })

            dao.replaceCatalog(emptyList(), state(fetchedAt = 3))
            assertEquals(emptyList<CatalogSongEntity>(), awaitItem())
            expectNoEvents()
        }
        assertEquals(state(fetchedAt = 3), dao.syncState("catalog"))
    }

    @Test
    fun `songs come back in sheet order, not insertion or id order`() = runTest {
        dao.replaceCatalog(listOf(song("zeta", 1), song("alfa", 3), song("medio", 2)), state(fetchedAt = 1))

        dao.observeSongs().test {
            assertEquals(listOf("zeta", "medio", "alfa"), awaitItem().map { it.id })
        }
    }

    @Test
    fun `recording a failure keeps the songs and the last success time`() = runTest {
        dao.replaceCatalog(listOf(song("a", 1)), state(fetchedAt = 1_000))

        dao.recordFailure("catalog", attemptedAt = 5_000, failure = "Offline")

        dao.observeSongs().test { assertEquals(listOf("a"), awaitItem().map { it.id }) }
        assertEquals(SyncStateEntity("catalog", 1_000, 5_000, "Offline"), dao.syncState("catalog"))
    }

    @Test
    fun `a failure with nothing cached records the attempt only`() = runTest {
        dao.recordFailure("catalog", attemptedAt = 5_000, failure = "NotConfigured")

        assertEquals(SyncStateEntity("catalog", null, 5_000, "NotConfigured"), dao.syncState("catalog"))
        dao.observeSyncState("catalog").test {
            assertEquals(SyncStateEntity("catalog", null, 5_000, "NotConfigured"), awaitItem())
        }
        assertNull(dao.syncState("jams"))
    }

    @Test
    fun `every column round-trips, tags included`() = runTest {
        val full = CatalogSongEntity(
            id = "the-thrill-is-gone",
            sheetOrder = 1,
            title = "The Thrill Is Gone",
            artist = "B.B. King",
            defaultKey = "Bm",
            tempo = "SLOW",
            difficulty = "MEDIUM",
            tags = listOf("slow blues", "12 compases", "a, \"b\"", "shuffle", "shuffle"),
            songsterrId = 9_007_199_254_740_993L,
        )
        val bare = song("crossroads", 2)

        dao.replaceCatalog(listOf(full, bare), state(fetchedAt = 1))

        dao.observeSongs().test { assertEquals(listOf(full, bare), awaitItem()) }
    }

    private fun song(id: String, order: Int) =
        CatalogSongEntity(id, order, id, "Artista", "A", null, null, emptyList(), null)

    private fun state(fetchedAt: Long) = SyncStateEntity("catalog", fetchedAt, fetchedAt, null)
}
