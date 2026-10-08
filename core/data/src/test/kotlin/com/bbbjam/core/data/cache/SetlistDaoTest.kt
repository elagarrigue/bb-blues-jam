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

    @Test
    fun `removeSetlistSong deletes the song with its slots and extras and moves later songs up with their own`() =
        runTest {
            jamsDao.replaceJams(fourSongs(), SyncStateEntity("jams", 1, 1, null))

            assertTrue(dao.removeSetlistSong(UPCOMING, "hoochie"))

            val jam = jamsDao.observeJams().first().single { it.jam.date == UPCOMING }
            assertEquals(
                listOf(1 to "crossroads", 2 to "thrill", 3 to "pride"),
                jam.songs.sortedBy { it.position }.map { it.position to it.songId },
            )
            // Each kept song keeps its own key, musicians and extras at its new position.
            assertEquals(listOf("A", "Bm", "E"), jam.songs.sortedBy { it.position }.map { it.key })
            assertEquals(
                setOf(Triple(1, 0, "Ana"), Triple(2, 1, "Caro"), Triple(3, 3, "Dani")),
                jam.slots.filter {
                    it.musicianName != null
                }.map { Triple(it.position, it.columnIndex, it.musicianName) }
                    .toSet(),
            )
            assertEquals(21, jam.slots.size)
            assertEquals(listOf(3 to "Eva"), jam.extras.map { it.position to it.name })
            assertFalse(jam.slots.any { it.musicianName == "Fede" })
            assertFalse(jam.extras.any { it.name == "Hugo" })
            // The other jam is untouched.
            assertEquals(1, jamsDao.observeJams().first().single { it.jam.date == LATER }.songs.size)
        }

    @Test
    fun `removeSetlistSong changes nothing for an unknown id, a repeated id or a jam that is not available`() =
        runTest {
            val rows = fourSongs()
            val repeated = rows.copy(
                jams = rows.jams.map { if (it.date == LATER) it.copy(setlistState = "WITHHELD") else it },
                songs = rows.songs + JamSongEntity(UPCOMING, 5, "pride", "T", "A", "C"),
            )
            jamsDao.replaceJams(repeated, SyncStateEntity("jams", 1, 1, null))
            val before = jamsDao.observeJams().first()

            assertFalse(dao.removeSetlistSong(UPCOMING, "zz-no-existe"))
            assertFalse(dao.removeSetlistSong(UPCOMING, "pride"))
            assertFalse(dao.removeSetlistSong(LATER, "crossroads"))
            assertFalse(dao.removeSetlistSong("2026-12-19", "crossroads"))

            assertEquals(before, jamsDao.observeJams().first())
        }

    @Test
    fun `moveSetlistSong moves the song and its slots and extras in either direction with clamping`() = runTest {
        jamsDao.replaceJams(fourSongs(), SyncStateEntity("jams", 1, 1, null))

        assertTrue(dao.moveSetlistSong(UPCOMING, "thrill", 1))
        var jam = jamsDao.observeJams().first().single { it.jam.date == UPCOMING }
        assertEquals(
            listOf("thrill", "crossroads", "hoochie", "pride"),
            jam.songs.sortedBy {
                it.position
            }.map { it.songId },
        )
        assertEquals(listOf("Bm", "A", "A", "E"), jam.songs.sortedBy { it.position }.map { it.key })
        assertEquals(
            setOf(Triple(1, 1, "Caro"), Triple(2, 0, "Ana"), Triple(3, 0, "Fede"), Triple(4, 3, "Dani")),
            jam.slots.filter {
                it.musicianName != null
            }.map { Triple(it.position, it.columnIndex, it.musicianName) }.toSet(),
        )
        assertEquals(
            listOf(3 to "Hugo", 4 to "Eva"),
            jam.extras.sortedBy {
                it.position
            }.map { it.position to it.name },
        )

        assertTrue(dao.moveSetlistSong(UPCOMING, "thrill", 99))
        jam = jamsDao.observeJams().first().single { it.jam.date == UPCOMING }
        assertEquals(
            listOf("crossroads", "hoochie", "pride", "thrill"),
            jam.songs.sortedBy {
                it.position
            }.map { it.songId },
        )
        assertEquals(28, jam.slots.size)
        assertEquals(
            listOf(2 to "Hugo", 3 to "Eva"),
            jam.extras.sortedBy {
                it.position
            }.map { it.position to it.name },
        )
    }

    @Test
    fun `moveSetlistSong no-op succeeds and invalid targets or ids do not change rows`() = runTest {
        val rows = fourSongs().let {
            it.copy(jams = it.jams.map { jam -> if (jam.date == LATER) jam.copy(setlistState = "WITHHELD") else jam })
        }
        jamsDao.replaceJams(rows, SyncStateEntity("jams", 1, 1, null))
        val before = jamsDao.observeJams().first()

        assertTrue(dao.moveSetlistSong(UPCOMING, "crossroads", 1))
        assertFalse(dao.moveSetlistSong(UPCOMING, "missing", 2))
        assertFalse(dao.moveSetlistSong(LATER, "crossroads", 2))
        assertFalse(dao.moveSetlistSong("2026-12-19", "crossroads", 2))

        assertEquals(before, jamsDao.observeJams().first())
    }

    @Test
    fun `moveSetlistSong changes nothing for duplicate cached ids`() = runTest {
        val rows = fourSongs().let { it.copy(songs = it.songs + JamSongEntity(UPCOMING, 5, "thrill", "T", "A", "C")) }
        jamsDao.replaceJams(rows, SyncStateEntity("jams", 1, 1, null))
        val before = jamsDao.observeJams().first()

        assertFalse(dao.moveSetlistSong(UPCOMING, "thrill", 1))
        assertEquals(before, jamsDao.observeJams().first())
    }

    @Test
    fun `removing the last song leaves an available empty setlist`() = runTest {
        val available = JamEntity(UPCOMING, 1, "21:00", "Lugar", "DRAFT", "AVAILABLE", null, 0)
        jamsDao.replaceJams(
            JamRows(listOf(available), listOf(song(UPCOMING, "crossroads")), slots(UPCOMING), emptyList()),
            SyncStateEntity("jams", 1, 1, null),
        )

        assertTrue(dao.removeSetlistSong(UPCOMING, "crossroads"))

        val jam = jamsDao.observeJams().first().single()
        assertEquals(Setlist.Available(emptyList()), jam.toDomain().setlist)
        assertEquals(emptyList<JamSlotEntity>(), jam.slots)
    }

    @Test
    fun `updateKey sets only the key of the matching song of that date`() = runTest {
        jamsDao.replaceJams(fourSongs(), SyncStateEntity("jams", 1, 1, null))
        val before = jamsDao.observeJams().first()

        assertEquals(1, dao.updateKey(UPCOMING, "crossroads", "Bb"))

        val after = jamsDao.observeJams().first()
        val upcoming = after.single { it.jam.date == UPCOMING }
        assertEquals(
            listOf("crossroads" to "Bb", "hoochie" to "A", "thrill" to "Bm", "pride" to "E"),
            upcoming.songs.sortedBy { it.position }.map { it.songId to it.key },
        )
        // Everything else is as it was: the other columns, the slots, the extras and the other jam,
        // which has the same song id.
        val unchanged = before.single { it.jam.date == UPCOMING }
        assertEquals(
            unchanged.songs.sortedBy { it.position }.map { it.copy(key = "") },
            upcoming.songs.sortedBy { it.position }.map { it.copy(key = "") },
        )
        assertEquals(unchanged.slots.toSet(), upcoming.slots.toSet())
        assertEquals(unchanged.extras.toSet(), upcoming.extras.toSet())
        assertEquals(before.single { it.jam.date == LATER }, after.single { it.jam.date == LATER })
    }

    @Test
    fun `updateKey changes nothing for an unknown id, a repeated id or a jam that is not available`() = runTest {
        val rows = fourSongs()
        val repeated = rows.copy(
            jams = rows.jams.map { if (it.date == LATER) it.copy(setlistState = "WITHHELD") else it },
            songs = rows.songs + JamSongEntity(UPCOMING, 5, "pride", "T", "A", "C"),
        )
        jamsDao.replaceJams(repeated, SyncStateEntity("jams", 1, 1, null))
        val before = jamsDao.observeJams().first()

        assertEquals(0, dao.updateKey(UPCOMING, "zz-no-existe", "Bb"))
        assertEquals(0, dao.updateKey(UPCOMING, "pride", "Bb"))
        assertEquals(0, dao.updateKey(LATER, "crossroads", "Bb"))
        assertEquals(0, dao.updateKey("2026-12-19", "crossroads", "Bb"))

        assertEquals(before, jamsDao.observeJams().first())
    }

    /** The upcoming jam with four songs in a known order, each with a filled slot or an extra. */
    @Test
    fun `replaceSlots targets one song by id and refuses duplicate and unavailable setlists`() = runTest {
        val rows = fourSongs()
        jamsDao.replaceJams(rows, SyncStateEntity("jams", 1, 1, null))
        val replacement = listOf(JamSlotEntity("ignored", 99, 1, "GUITAR", "New"))
        assertTrue(dao.replaceSlots(UPCOMING, "thrill", replacement))
        val after = jamsDao.observeJams().first().single { it.jam.date == UPCOMING }
        assertEquals(listOf(JamSlotEntity(UPCOMING, 3, 1, "GUITAR", "New")), after.slots.filter { it.position == 3 })
        assertEquals(rows.slots.filter { it.position != 3 }, after.slots.filter { it.position != 3 })
        assertEquals(rows.extras, after.extras)
        val invalid = rows.copy(
            jams = rows.jams.map { if (it.date == LATER) it.copy(setlistState = "WITHHELD") else it },
            songs = rows.songs + JamSongEntity(UPCOMING, 5, "thrill", "T", "A", "C"),
        )
        jamsDao.replaceJams(invalid, SyncStateEntity("jams", 1, 1, null))
        val before = jamsDao.observeJams().first()
        assertFalse(dao.replaceSlots(UPCOMING, "thrill", replacement))
        assertFalse(dao.replaceSlots(UPCOMING, "missing", replacement))
        assertFalse(dao.replaceSlots(LATER, "crossroads", replacement))
        assertEquals(before, jamsDao.observeJams().first())
    }

    private fun fourSongs(): JamRows {
        val available = JamEntity(UPCOMING, 1, "21:00", "Lugar", "DRAFT", "AVAILABLE", null, 0)
        val later = JamEntity(LATER, 2, "21:00", "Lugar", "PUBLISHED", "AVAILABLE", null, 0)
        val songs = listOf(
            JamSongEntity(UPCOMING, 1, "crossroads", "Crossroads", "Eric Clapton", "A"),
            JamSongEntity(UPCOMING, 2, "hoochie", "Hoochie Coochie Man", "Muddy Waters", "A"),
            JamSongEntity(UPCOMING, 3, "thrill", "The Thrill Is Gone", "B.B. King", "Bm"),
            JamSongEntity(UPCOMING, 4, "pride", "Pride and Joy", "Stevie Ray Vaughan", "E"),
            JamSongEntity(LATER, 1, "crossroads", "Crossroads", "Eric Clapton", "A"),
        )
        val filled = mapOf(1 to (0 to "Ana"), 2 to (0 to "Fede"), 3 to (1 to "Caro"), 4 to (3 to "Dani"))
        val slots = (1..4).flatMap { position ->
            Lineup.DEFAULT_INSTRUMENTS.mapIndexed { column, instrument ->
                val name = filled.getValue(position).takeIf { it.first == column }?.second
                JamSlotEntity(UPCOMING, position, column, instrument.name, name)
            }
        }
        val extras = listOf(
            JamExtraEntity(UPCOMING, 2, 1, "Hugo", "Trompeta"),
            JamExtraEntity(UPCOMING, 4, 1, "Eva", "Saxo"),
        )
        return JamRows(listOf(available, later), songs, slots, extras)
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
