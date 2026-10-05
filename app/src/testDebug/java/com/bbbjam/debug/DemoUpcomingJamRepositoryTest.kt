package com.bbbjam.debug

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRefreshOutcome
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoUpcomingJamRepositoryTest {
    // 2026-10-05 12:00 UTC is 09:00 of the same day in Buenos Aires.
    private val calendar = JamCalendar(
        Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC),
        JamCalendar.BUENOS_AIRES,
    )
    private val today = LocalDate.parse("2026-10-05")

    private val pastJam = Jam(
        date = LocalDate.parse("2026-09-26"),
        startTime = LocalTime.parse("21:00"),
        venue = "Real",
        status = JamStatus.PUBLISHED,
        setlist = Setlist.Unavailable(SetlistProblem.MISSING_TAB),
    )
    private val realUpcoming = pastJam.copy(date = LocalDate.parse("2026-10-31"), venue = "Real upcoming")
    private val freshness = Freshness(Instant.parse("2026-10-05T11:00:00Z"), DataFailure.Offline, isRefreshing = false)

    private class FakeJams(private vararg val snapshots: JamsSnapshot) : JamsRepository {
        var refreshCalls = 0
        val outcome = JamsRefreshOutcome.Failed(DataFailure.Offline)

        override fun observeJams(): Flow<JamsSnapshot> = flowOf(*snapshots)

        override suspend fun refresh(): JamsRefreshOutcome {
            refreshCalls++
            return outcome
        }
    }

    private fun observe(vararg snapshots: JamsSnapshot, draft: Boolean = false): List<JamsSnapshot> = runBlocking {
        DemoUpcomingJamRepository(FakeJams(*snapshots), calendar, draft).observeJams().toList()
    }

    @Test
    fun draftDemoFillsAnEmptyUpcomingWithTheSameSongs() {
        val upcoming = observe(JamsSnapshot(null, listOf(pastJam), freshness), draft = true).single().upcoming!!

        assertEquals(JamStatus.DRAFT, upcoming.status)
        assertTrue(upcoming.isDemo())
        assertEquals(DemoUpcomingJam.on(today).setlist, upcoming.setlist)
        assertEquals(DemoUpcomingJam.on(today).copy(status = JamStatus.DRAFT), upcoming)
    }

    @Test
    fun realUpcomingJamStillWinsWithTheDraftDemo() {
        val real = JamsSnapshot(realUpcoming, listOf(pastJam), freshness)

        assertEquals(listOf(real), observe(real, draft = true))
    }

    @Test
    fun realUpcomingJamWinsAndPassesThroughUnchanged() {
        val real = JamsSnapshot(realUpcoming, listOf(pastJam), freshness)

        assertEquals(listOf(real), observe(real))
    }

    @Test
    fun demoFillsAnEmptyUpcoming() {
        val upcoming = observe(JamsSnapshot(null, listOf(pastJam), freshness)).single().upcoming!!

        assertEquals(DemoUpcomingJam.on(today), upcoming)
        assertEquals(today.plusDays(DemoUpcomingJam.DAYS_AHEAD), upcoming.date)
        assertEquals("Demo (solo debug)", upcoming.venue)
        assertEquals(JamStatus.PUBLISHED, upcoming.status)
        assertTrue(upcoming.isDemo())
        assertFalse(realUpcoming.isDemo())
    }

    @Test
    fun pastAndFreshnessAreTheRealOnes() {
        val real = JamsSnapshot(null, listOf(pastJam), freshness)

        val decorated = observe(real).single()

        assertEquals(real.past, decorated.past)
        assertEquals(real.freshness, decorated.freshness)
    }

    @Test
    fun everyEmissionIsDecoratedOnItsOwn() {
        val empty = JamsSnapshot(null, emptyList(), Freshness(null, null, isRefreshing = true))
        val withReal = JamsSnapshot(realUpcoming, emptyList(), freshness)

        val emitted = observe(empty, withReal, empty)

        assertEquals(listOf(true, false, true), emitted.map { it.upcoming!!.isDemo() })
        assertEquals(listOf(empty.freshness, withReal.freshness, empty.freshness), emitted.map { it.freshness })
    }

    @Test
    fun refreshDelegatesOnce() = runBlocking {
        val real = FakeJams()

        val outcome = DemoUpcomingJamRepository(real, calendar).refresh()

        assertEquals(1, real.refreshCalls)
        assertSame(real.outcome, outcome)
    }

    @Test
    fun demoSetlistCoversTheDeviceChecks() {
        val songs = (DemoUpcomingJam.on(today).setlist as Setlist.Available).songs
        val slots = songs.flatMap { it.lineup.slots }

        assertEquals((1..songs.size).toList(), songs.map { it.position })
        Instrument.entries.forEach { instrument ->
            assertTrue("$instrument open somewhere", slots.any { it.instrument == instrument && it.isOpen })
            assertTrue("$instrument filled somewhere", slots.any { it.instrument == instrument && it.isFilled })
        }
        assertTrue("a song with no open slot", songs.any { it.lineup.openSlots.isEmpty() })
        assertTrue(
            "a song with every default slot open",
            songs.any { it.lineup.slots.size == DEFAULT_SLOTS && it.lineup.slots.all { slot -> slot.isOpen } },
        )
        assertTrue("an instrument left out of a lineup", songs.any { it.lineup.slots.size < DEFAULT_SLOTS })
        val extras = songs.flatMap { it.extraParticipants }
        assertTrue("a short Otros instrument", extras.any { it.instrument.length <= SHORT_INSTRUMENT })
        assertTrue("a long Otros instrument", extras.any { it.instrument.length > LONG_INSTRUMENT })
        assertTrue("a key with an accidental", songs.any { it.key.value.contains('#') || it.key.value.contains('b') })
    }

    private companion object {
        const val DEFAULT_SLOTS = 7
        const val SHORT_INSTRUMENT = 10
        const val LONG_INSTRUMENT = 40
    }
}
