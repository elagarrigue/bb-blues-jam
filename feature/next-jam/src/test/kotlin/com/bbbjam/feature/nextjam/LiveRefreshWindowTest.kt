package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.TimeZone
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live window (`live-refresh-during-jam`, Decision 2): `[start − 30 min, start + 4 h)` in Buenos Aires. */
class LiveRefreshWindowTest {
    private val zone = JamCalendar.BUENOS_AIRES
    private val jam = Jam(
        LocalDate.of(2026, 10, 31),
        LocalTime.of(21, 0),
        "La Macanuda",
        JamStatus.PUBLISHED,
        Setlist.Available(emptyList()),
    )

    private fun buenosAires(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun `the window opens 30 min before the start and closes 4 h after it, Buenos Aires time`() {
        val window = liveWindow(jam, zone)

        assertFalse(buenosAires("2026-10-31T20:29:59") in window)
        assertTrue(buenosAires("2026-10-31T20:30:00") in window)
        assertTrue(buenosAires("2026-11-01T00:59:59") in window)
        assertFalse(buenosAires("2026-11-01T01:00:00") in window)
    }

    @Test
    fun `the same instants written in UTC prove the zone`() {
        val window = liveWindow(jam, zone)

        assertFalse(Instant.parse("2026-10-31T23:29:59Z") in window)
        assertTrue(Instant.parse("2026-10-31T23:30:00Z") in window)
        assertTrue(Instant.parse("2026-11-01T03:59:59Z") in window)
        assertFalse(Instant.parse("2026-11-01T04:00:00Z") in window)
        assertEquals(Instant.parse("2026-10-31T23:30:00Z"), window.start)
        assertEquals(Instant.parse("2026-11-01T04:00:00Z"), window.endExclusive)
    }

    @Test
    fun `a device default zone of UTC changes nothing`() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val window = liveWindow(jam, zone)
            assertEquals(Instant.parse("2026-10-31T23:30:00Z"), window.start)
            assertEquals(Instant.parse("2026-11-01T04:00:00Z"), window.endExclusive)
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun `the candidate jams are the upcoming one and the latest past one, by date and start time only`() {
        val upcoming = jam.copy(date = LocalDate.of(2026, 11, 28))
        val latestPast = jam
        val olderPast = jam.copy(date = LocalDate.of(2026, 9, 26))
        val freshness = Freshness(null, null, isRefreshing = false)

        assertEquals(
            listOf(LiveJam(upcoming.date, upcoming.startTime), LiveJam(latestPast.date, latestPast.startTime)),
            JamsSnapshot(upcoming, listOf(latestPast, olderPast), freshness).liveJams(),
        )
        assertEquals(emptyList<LiveJam>(), JamsSnapshot(null, emptyList(), freshness).liveJams())
        // A new snapshot of the same jams with other songs gives an equal list: the loop's key.
        assertEquals(
            JamsSnapshot(upcoming, emptyList(), freshness).liveJams(),
            JamsSnapshot(upcoming.copy(venue = "Otro"), emptyList(), freshness.copy(isRefreshing = true)).liveJams(),
        )
    }

    @Test
    fun `the constants are the approved ones and the jitter stays under one interval`() {
        assertEquals(Duration.ofMinutes(30), LiveRefresh.OPENS_BEFORE)
        assertEquals(Duration.ofHours(4), LiveRefresh.CLOSES_AFTER)
        assertEquals(Duration.ofSeconds(30), LiveRefresh.INTERVAL)
        assertEquals(Duration.ofSeconds(60), LiveRefresh.FAILURE_INTERVAL)
        val random = Random(SEED)
        repeat(JITTER_SAMPLES) {
            val jitter = random.jitter()
            assertFalse(jitter.isNegative)
            assertTrue(jitter < LiveRefresh.INTERVAL)
        }
    }

    private companion object {
        const val SEED = 7
        const val JITTER_SAMPLES = 1_000
    }
}
