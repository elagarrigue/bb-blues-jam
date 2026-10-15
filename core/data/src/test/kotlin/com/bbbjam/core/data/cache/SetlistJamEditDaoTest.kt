package com.bbbjam.core.data.cache

import com.bbbjam.core.data.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Room on the JVM, in memory: the mirror of a confirmed jam edit or deletion (`admin-edit-delete-jam`). */
class SetlistJamEditDaoTest {
    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val dao = database.setlistDao()

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seed() {
        val jams = listOf(
            JamEntity(UPCOMING, 1, "21:00", "La Macanuda", "CREATED", "AVAILABLE", null, 2),
            JamEntity(LATER, 2, "21:00", "Otro", "DRAFT", "AVAILABLE", null, 0),
        )
        val songs = listOf(
            JamSongEntity(UPCOMING, 1, "crossroads", "Crossroads", "Eric Clapton", "A"),
            JamSongEntity(UPCOMING, 2, "hoochie", "Hoochie", "Muddy Waters", "E"),
            JamSongEntity(LATER, 1, "pride", "Pride", "SRV", "E"),
        )
        val slots = listOf(
            JamSlotEntity(UPCOMING, 1, 0, "GUITAR", "Ana"),
            JamSlotEntity(UPCOMING, 2, 3, "DRUMS", null),
            JamSlotEntity(LATER, 1, 0, "GUITAR", "Zoe"),
        )
        val extras = listOf(JamExtraEntity(UPCOMING, 2, 1, "Hugo", "Trompeta"))
        jamsDao.replaceJams(JamRows(jams, songs, slots, extras), SyncStateEntity("jams", 1, 1, null))
    }

    @Test
    fun `moveJam keeps the sheet order, status, songs, slots and extras under the new date`() = runTest {
        seed()

        assertTrue(dao.moveJam(UPCOMING, MOVED, "20:00", "El Sotano"))

        val all = jamsDao.observeJams().first()
        assertEquals(listOf(MOVED, LATER), all.map { it.jam.date }.sorted())
        val moved = all.single { it.jam.date == MOVED }
        assertEquals(JamEntity(MOVED, 1, "20:00", "El Sotano", "CREATED", "AVAILABLE", null, 2), moved.jam)
        assertEquals(listOf("crossroads", "hoochie"), moved.songs.sortedBy { it.position }.map { it.songId })
        assertEquals(setOf(0 to "Ana", 3 to null), moved.slots.map { it.columnIndex to it.musicianName }.toSet())
        assertEquals(listOf("Hugo"), moved.extras.map { it.name })
        assertEquals(1, all.single { it.jam.date == LATER }.songs.size)
        assertEquals(0, dao.jamCount(UPCOMING))
    }

    @Test
    fun `moveJam does nothing when the old jam is not cached or the new date is`() = runTest {
        seed()

        assertFalse(dao.moveJam("1999-01-01", MOVED, "20:00", "X"))
        assertFalse(dao.moveJam(UPCOMING, LATER, "20:00", "X"))

        val all = jamsDao.observeJams().first()
        assertEquals(listOf(UPCOMING, LATER).sorted(), all.map { it.jam.date }.sorted())
        assertEquals("La Macanuda", all.single { it.jam.date == UPCOMING }.jam.venue)
    }

    @Test
    fun `updateJamDetails changes only the time and the venue of the cached jam`() = runTest {
        seed()

        assertEquals(1, dao.updateJamDetails(UPCOMING, "19:30", "Bar"))
        assertEquals(0, dao.updateJamDetails("1999-01-01", "19:30", "Bar"))

        val jam = jamsDao.observeJams().first().single { it.jam.date == UPCOMING }
        assertEquals("19:30", jam.jam.startTime)
        assertEquals("Bar", jam.jam.venue)
        assertEquals("CREATED", jam.jam.status)
        assertEquals(2, jam.songs.size)
    }

    @Test
    fun `deleteJamRow removes the jam with its songs, slots and extras and no other`() = runTest {
        seed()

        assertEquals(1, dao.deleteJamRow(UPCOMING))
        assertEquals(0, dao.deleteJamRow(UPCOMING))

        val all = jamsDao.observeJams().first()
        assertEquals(listOf(LATER), all.map { it.jam.date })
        assertEquals(1, all.single().slots.size)
        assertTrue(all.single().extras.isEmpty())
    }

    private companion object {
        const val UPCOMING = "2026-10-31"
        const val LATER = "2026-11-28"
        const val MOVED = "2026-11-07"
    }
}
