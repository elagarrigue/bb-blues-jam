package com.bbbjam.core.data.cache

import com.bbbjam.core.data.Fixtures
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Room on the JVM with the bundled SQLite driver, in memory. */
class SetlistDaoTest {
    private val database = Fixtures.inMemoryDatabase()
    private val jamsDao = database.jamsDao()
    private val dao = database.setlistDao()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insertSetlistSong adds a song with its slots only to an available setlist, and an existing position wins`() =
        runTest {
            val available = JamEntity(UPCOMING, 1, "21:00", "Lugar", "DRAFT", "AVAILABLE", null, 0)
            val withheld = JamEntity(LATER, 2, "21:00", "Lugar", "DRAFT", "WITHHELD", null, 0)
            jamsDao.replaceJams(
                JamRows(listOf(available, withheld), emptyList(), emptyList(), emptyList()),
                SyncStateEntity("jams", 1, 1, null),
            )

            assertTrue(dao.insertSetlistSong(song(UPCOMING, "crossroads"), slots(UPCOMING)))
            assertFalse(dao.insertSetlistSong(song(UPCOMING, "red-house"), slots(UPCOMING)))
            assertFalse(dao.insertSetlistSong(song(LATER, "crossroads"), slots(LATER)))
            assertFalse(dao.insertSetlistSong(song("2026-12-19", "crossroads"), slots("2026-12-19")))

            val stored = jamsDao.observeJams().first()
            val songs = (stored.single { it.jam.date == UPCOMING }.toDomain().setlist as Setlist.Available).songs
            assertEquals(listOf("crossroads"), songs.map { it.songId.value })
            assertEquals(Lineup.default(), songs.single().lineup)
            val later = stored.single { it.jam.date == LATER }
            assertEquals(emptyList<JamSongResolved>(), later.songs)
            assertEquals(emptyList<JamSlotEntity>(), later.slots)
            assertEquals(Setlist.Withheld, later.toDomain().setlist)
        }

    private fun song(date: String, songId: String) = JamSongEntity(date, 1, songId, "Título", "Artista", "A")

    private fun slots(date: String) = Lineup.DEFAULT_INSTRUMENTS.mapIndexed { column, instrument ->
        JamSlotEntity(date, 1, column, instrument.name, null)
    }

    private companion object {
        const val UPCOMING = "2026-10-31"
        const val LATER = "2026-11-28"
    }
}
