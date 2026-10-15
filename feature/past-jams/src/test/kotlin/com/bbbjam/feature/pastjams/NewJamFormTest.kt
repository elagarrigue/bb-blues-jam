package com.bbbjam.feature.pastjams

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.VenueProblem
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Nueva jam" form's pure rules (`admin-jams-tab`, J7/J8). */
class NewJamFormTest {
    private val today = LocalDate.of(2026, 10, 2)
    private val upcomingDate = LocalDate.of(2026, 10, 31)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    private fun jam(date: LocalDate, time: LocalTime = LocalTime.of(21, 0), venue: String = "La Macanuda") =
        Jam(date, time, venue, JamStatus.CREATED, Setlist.Available(emptyList()))

    private fun snapshot(
        upcoming: Jam? = jam(upcomingDate),
        heldBack: List<Jam> = emptyList(),
        past: List<Jam> = emptyList(),
    ) = JamsSnapshot(upcoming, past, fetched, heldBack)

    private fun check(
        date: String,
        time: String = "21:00",
        venue: String = "La Macanuda",
        snapshot: JamsSnapshot? = snapshot(),
    ) = checkNewJam(NewJamInput(date, time, venue), snapshot, today)

    @Test
    fun `the date accepts one or two digits and slash, dash or dot`() {
        val expected = LocalDate.of(2026, 11, 5)
        listOf("5/11/2026", "05-11-2026", "05.11.2026", " 5/11/2026 ", "5-11.2026").forEach {
            assertEquals(it, expected, parseFormDate(it))
        }
        listOf("", "31/02/2026", "5/11/26", "2026-11-05", "a/b/c", "5/11", "0/1/2026", "5112026").forEach {
            assertNull(it, parseFormDate(it))
        }
    }

    @Test
    fun `the time accepts colon or dot and a one-digit hour`() {
        assertEquals(LocalTime.of(21, 0), parseFormTime("21:00"))
        assertEquals(LocalTime.of(21, 0), parseFormTime("21.00"))
        assertEquals(LocalTime.of(9, 30), parseFormTime("9:30"))
        assertEquals(LocalTime.of(0, 5), parseFormTime(" 00:05 "))
        listOf("", "24:00", "21:60", "9", "21:5", "2100", "ab:cd").forEach { assertNull(it, parseFormTime(it)) }
        assertEquals("09:30", formatFormTime(LocalTime.of(9, 30)))
    }

    @Test
    fun `a date that is not a date, a past date, an existing date and an earlier date are field errors`() {
        assertEquals(DateProblem.FORMAT, check("mañana").dateProblem)
        assertEquals(DateProblem.PAST, check("1/10/2026").dateProblem)
        assertEquals(DateProblem.EXISTS, check("31/10/2026").dateProblem)
        val earlier = check("20/10/2026")
        assertEquals(DateProblem.EARLIER, earlier.dateProblem)
        assertEquals(upcomingDate, earlier.earlierThan)
        assertFalse(earlier.canCreate)
    }

    @Test
    fun `a past jam and a held-back jam also block their date`() {
        val past = snapshot(upcoming = null, past = listOf(jam(LocalDate.of(2026, 10, 2))))
        assertEquals(DateProblem.EXISTS, check("2/10/2026", snapshot = past).dateProblem)
        val heldBack = snapshot(heldBack = listOf(jam(LocalDate.of(2026, 11, 28))))
        assertEquals(DateProblem.EXISTS, check("28/11/2026", snapshot = heldBack).dateProblem)
    }

    @Test
    fun `today is allowed with no upcoming jam and creates a jam that is not held back`() {
        val result = check("2/10/2026", snapshot = snapshot(upcoming = null))
        assertNull(result.dateProblem)
        assertNull(result.waitsBehind)
        assertEquals(NewJamValues(today, LocalTime.of(21, 0), "La Macanuda"), result.values)
        assertTrue(result.canCreate)
    }

    @Test
    fun `a later date waits behind the upcoming jam and names it`() {
        val result = check("28/11/2026")
        assertNull(result.dateProblem)
        assertEquals(upcomingDate, result.waitsBehind)
        assertEquals(NewJamValues(LocalDate.of(2026, 11, 28), LocalTime.of(21, 0), "La Macanuda"), result.values)
    }

    @Test
    fun `time and venue problems block creation and the venue is normalized`() {
        assertTrue(check("28/11/2026", time = "25:00").timeInvalid)
        assertFalse(check("28/11/2026", time = "25:00").canCreate)
        assertEquals(VenueProblem.EMPTY, check("28/11/2026", venue = "  ").venueProblem)
        assertEquals(VenueProblem.TOO_LONG, check("28/11/2026", venue = "x".repeat(81)).venueProblem)
        assertEquals(VenueProblem.FORMULA, check("28/11/2026", venue = "=A1").venueProblem)
        assertFalse(check("28/11/2026", venue = "=A1").canCreate)
        assertEquals("El Sótano", check("28/11/2026", time = "9.30", venue = "  El   Sótano ").values?.venue)
        assertEquals(LocalTime.of(9, 30), check("28/11/2026", time = "9.30").values?.time)
    }

    @Test
    fun `with no snapshot nothing can be created`() {
        assertFalse(check("28/11/2026", snapshot = null).canCreate)
    }

    @Test
    fun `the seed is the latest-dated jam, held back, upcoming or past, else 21 00 and nothing`() {
        assertEquals(NewJamSeed(LocalTime.of(21, 0), ""), newJamSeed(null))
        assertEquals(NewJamSeed(LocalTime.of(21, 0), ""), newJamSeed(snapshot(upcoming = null)))
        val pastJam = jam(LocalDate.of(2026, 9, 26), LocalTime.of(20, 0), "El Sótano")
        val onlyPast = snapshot(upcoming = null, past = listOf(pastJam))
        assertEquals(NewJamSeed(LocalTime.of(20, 0), "El Sótano"), newJamSeed(onlyPast))
        val heldBackJam = jam(LocalDate.of(2026, 11, 28), LocalTime.of(22, 30), "Bar Tito")
        assertEquals(NewJamSeed(LocalTime.of(22, 30), "Bar Tito"), newJamSeed(snapshot(heldBack = listOf(heldBackJam))))
        assertEquals(NewJamSeed(LocalTime.of(21, 0), "La Macanuda"), newJamSeed(snapshot()))
    }
}
