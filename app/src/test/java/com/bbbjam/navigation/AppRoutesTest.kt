package com.bbbjam.navigation

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The string routes (R1): built and parsed only in [AppRoutes], checked here on the JVM. */
class AppRoutesTest {

    @Test
    fun `the song detail route is the ISO date and the position`() {
        assertEquals("song/2026-10-31/2", AppRoutes.songDetail(LocalDate.of(2026, 10, 31), 2))
    }

    @Test
    fun `the pattern holds both argument names`() {
        assertEquals("song/{jamDate}/{position}", AppRoutes.SONG_DETAIL)
        assertTrue(AppRoutes.SONG_DETAIL.contains("{${AppRoutes.JAM_DATE}}"))
        assertTrue(AppRoutes.SONG_DETAIL.contains("{${AppRoutes.POSITION}}"))
        assertEquals("tabs", AppRoutes.TABS)
    }

    @Test
    fun `the tab routes are distinct plain strings`() {
        assertEquals("next-jam", AppRoutes.NEXT_JAM)
        assertEquals("past-jams", AppRoutes.PAST_JAMS)
        assertEquals("info", AppRoutes.INFO)
        assertEquals("admin-login", AppRoutes.ADMIN_LOGIN)
        val routes = listOf(
            AppRoutes.TABS,
            AppRoutes.NEXT_JAM,
            AppRoutes.PAST_JAMS,
            AppRoutes.INFO,
            AppRoutes.SONG_DETAIL,
            AppRoutes.PAST_JAM_DETAIL,
            AppRoutes.ADMIN_LOGIN,
        )
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `parse accepts a valid date and position`() {
        assertEquals(SongDetailArgs(LocalDate.of(2026, 10, 31), 2), AppRoutes.parseSongDetail("2026-10-31", 2))
    }

    @Test
    fun `parse rejects a missing value, a malformed date and a position below 1`() {
        assertNull(AppRoutes.parseSongDetail(null, 2))
        assertNull(AppRoutes.parseSongDetail("2026-10-31", null))
        assertNull(AppRoutes.parseSongDetail("31/10/2026", 2))
        assertNull(AppRoutes.parseSongDetail("2026-13-01", 2))
        assertNull(AppRoutes.parseSongDetail("", 2))
        assertNull(AppRoutes.parseSongDetail("2026-10-31", 0))
        assertNull(AppRoutes.parseSongDetail("2026-10-31", -1))
    }

    @Test
    fun `every month round-trips, single-digit days and months included`() {
        (1..12).forEach { month ->
            listOf(1, 9, 28).forEach { day ->
                val date = LocalDate.of(2026, month, day)
                val segments = AppRoutes.songDetail(date, 13).split('/')
                assertEquals(SongDetailArgs(date, 13), AppRoutes.parseSongDetail(segments[1], segments[2].toInt()))
            }
        }
    }

    @Test
    fun `the past jam detail route is the ISO date`() {
        assertEquals("pastJam/{jamDate}", AppRoutes.PAST_JAM_DETAIL)
        assertTrue(AppRoutes.PAST_JAM_DETAIL.contains("{${AppRoutes.JAM_DATE}}"))
        assertEquals("pastJam/2026-07-25", AppRoutes.pastJamDetail(LocalDate.of(2026, 7, 25)))
    }

    @Test
    fun `past jam parse accepts an ISO date and rejects a missing or malformed one`() {
        assertEquals(LocalDate.of(2026, 7, 25), AppRoutes.parsePastJamDetail("2026-07-25"))
        assertNull(AppRoutes.parsePastJamDetail(null))
        assertNull(AppRoutes.parsePastJamDetail(""))
        assertNull(AppRoutes.parsePastJamDetail("25/07/2026"))
        assertNull(AppRoutes.parsePastJamDetail("2026-13-01"))
    }

    @Test
    fun `every month round-trips through the past jam route`() {
        (1..12).forEach { month ->
            listOf(1, 9, 28).forEach { day ->
                val date = LocalDate.of(2026, month, day)
                val segments = AppRoutes.pastJamDetail(date).split('/')
                assertEquals(2, segments.size)
                assertEquals(date, AppRoutes.parsePastJamDetail(segments[1]))
            }
        }
    }
}
