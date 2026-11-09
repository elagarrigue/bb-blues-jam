package com.bbbjam.core.data.cache

import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.model.Setlist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Room on the JVM, in memory: the played mark of `song-played-state` (D-31). */
class SetlistPlayedDaoTest {
    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val dao = database.setlistDao()

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seed() {
        val jams = listOf(
            JamEntity(UPCOMING, 1, "21:00", "Lugar", "DRAFT", "AVAILABLE", null, 0),
            JamEntity(LATER, 2, "21:00", "Lugar", "DRAFT", "WITHHELD", null, 0),
        )
        val songs = listOf(
            JamSongEntity(UPCOMING, 1, "crossroads", "Crossroads", "Eric Clapton", "A", played = true),
            JamSongEntity(UPCOMING, 2, "hoochie", "Hoochie", "Muddy Waters", "E"),
            JamSongEntity(UPCOMING, 3, "pride", "Pride", "SRV", "E", played = true),
            JamSongEntity(LATER, 1, "crossroads", "Crossroads", "Eric Clapton", "A"),
        )
        jamsDao.replaceJams(JamRows(jams, songs, emptyList(), emptyList()), SyncStateEntity("jams", 1, 1, null))
    }

    private suspend fun played(date: String = UPCOMING) = jamsDao.observeJams().first()
        .single { it.jam.date == date }.songs.sortedBy { it.position }.map { it.songId to it.played }

    @Test
    fun `a played song reads back as played in the domain and the others as not`() = runTest {
        seed()

        val songs = (
            jamsDao.observeJams().first().single {
                it.jam.date == UPCOMING
            }.toDomain().setlist as Setlist.Available
            ).songs
        assertEquals(listOf(true, false, true), songs.map { it.played })
    }

    @Test
    fun `updatePlayed sets only the matching song of that date`() = runTest {
        seed()

        assertEquals(1, dao.updatePlayed(UPCOMING, "hoochie", true))
        assertEquals(1, dao.updatePlayed(UPCOMING, "crossroads", false))

        assertEquals(listOf("crossroads" to false, "hoochie" to true, "pride" to true), played())
        assertEquals(listOf("crossroads" to false), played(LATER))
    }

    @Test
    fun `updatePlayed changes nothing for an unknown id, a repeated id or a jam that is not available`() = runTest {
        seed()
        dao.insertSongIfAbsent(JamSongEntity(UPCOMING, 4, "pride", "Pride", "SRV", "E"))
        val before = jamsDao.observeJams().first()

        assertEquals(0, dao.updatePlayed(UPCOMING, "zz-no-existe", true))
        assertEquals(0, dao.updatePlayed(UPCOMING, "pride", false))
        assertEquals(0, dao.updatePlayed(LATER, "crossroads", true))
        assertEquals(0, dao.updatePlayed("2026-12-19", "crossroads", true))

        assertEquals(before, jamsDao.observeJams().first())
    }

    @Test
    fun `removing or moving a song keeps the played mark of every song`() = runTest {
        seed()

        assertTrue(dao.removeSetlistSong(UPCOMING, "hoochie"))
        assertEquals(listOf("crossroads" to true, "pride" to true), played())
        assertTrue(dao.moveSetlistSong(UPCOMING, "pride", 1))
        assertEquals(listOf("pride" to true, "crossroads" to true), played())
        assertFalse(dao.moveSetlistSong(LATER, "crossroads", 1))
    }

    @Test
    fun `a date edit copies the played mark with the songs (copySongs keeps played)`() = runTest {
        seed()

        assertTrue(dao.moveJam(UPCOMING, MOVED, "20:00", "Bar"))

        assertEquals(listOf("crossroads" to true, "hoochie" to false, "pride" to true), played(MOVED))
    }

    private companion object {
        const val UPCOMING = "2026-10-31"
        const val LATER = "2026-11-28"
        const val MOVED = "2026-11-07"
    }
}
