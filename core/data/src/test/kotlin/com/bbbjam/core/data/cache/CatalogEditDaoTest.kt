package com.bbbjam.core.data.cache

import com.bbbjam.core.data.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Room on the JVM with the bundled SQLite driver, in memory. */
class CatalogEditDaoTest {
    private val database = Fixtures.inMemoryDatabase()
    private val catalog = database.catalogDao()
    private val dao = database.catalogEditDao()

    @After
    fun tearDown() {
        database.close()
    }

    private fun song(id: String, order: Int) = CatalogSongEntity(
        id = id,
        sheetOrder = order,
        title = "T $id",
        artist = "A $id",
        defaultKey = "A",
        tempo = "FAST",
        difficulty = "EASY",
        tags = listOf("classic"),
        songsterrId = 7L,
    )

    @Test
    fun `a created song goes after the last cached row with empty optional columns`() = runTest {
        catalog.insertSongs(listOf(song("a", 1), song("b", 5)))

        dao.insertCreated(song("c", 0).copy(tempo = null, difficulty = null, tags = emptyList(), songsterrId = null))

        val rows = catalog.observeSongs().first()
        assertEquals(listOf("a", "b", "c"), rows.map { it.id })
        assertEquals(6, rows.last().sheetOrder)
        assertEquals(null, rows.last().tempo)
    }

    @Test
    fun `the first created song of an empty cache has order 1`() = runTest {
        dao.insertCreated(song("a", 0))

        assertEquals(1, catalog.observeSongs().first().single().sheetOrder)
    }

    @Test
    fun `an update changes the three fields and leaves every other column`() = runTest {
        catalog.insertSongs(listOf(song("a", 1), song("b", 2)))

        assertEquals(1, dao.updateFields("a", "New", "Artist", "Bm"))

        val rows = catalog.observeSongs().first()
        assertEquals(song("a", 1).copy(title = "New", artist = "Artist", defaultKey = "Bm"), rows[0])
        assertEquals(song("b", 2), rows[1])
    }

    @Test
    fun `updating or deleting an uncached song changes nothing`() = runTest {
        catalog.insertSongs(listOf(song("a", 1)))

        assertEquals(0, dao.updateFields("zzz", "x", "y", "A"))
        assertEquals(0, dao.delete("zzz"))
        assertEquals(listOf("a"), catalog.observeSongs().first().map { it.id })
    }

    @Test
    fun `a delete drops only that song`() = runTest {
        catalog.insertSongs(listOf(song("a", 1), song("b", 2)))

        assertEquals(1, dao.delete("a"))

        assertEquals(listOf("b"), catalog.observeSongs().first().map { it.id })
    }
}
