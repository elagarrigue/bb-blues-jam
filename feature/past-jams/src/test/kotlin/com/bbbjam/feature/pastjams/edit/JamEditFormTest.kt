package com.bbbjam.feature.pastjams.edit

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamFormInput
import com.bbbjam.core.data.jams.JamFormValues
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.jams.VenueProblem
import com.bbbjam.core.data.jams.formatFormDate
import com.bbbjam.core.data.jams.parseFormDate
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

/** `admin-edit-delete-jam` C4: the edit dialog's pure rules. Pure, no Compose. */
class JamEditFormTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val upcomingDate = LocalDate.of(2026, 10, 31)
    private val heldDate = LocalDate.of(2026, 11, 13)
    private val fetched = Freshness(Instant.parse("2026-10-14T12:00:00Z"), lastFailure = null, isRefreshing = false)

    private fun jam(date: LocalDate) =
        Jam(date, LocalTime.of(21, 0), "La Macanuda", JamStatus.DRAFT, Setlist.Available(emptyList()))

    private val upcoming = jam(upcomingDate)
    private val held = jam(heldDate)
    private val snapshot = JamsSnapshot(upcoming, emptyList(), fetched, listOf(held))

    private fun input(date: String = "31/10/2026", time: String = "21:00", venue: String = "La Macanuda") =
        JamFormInput(date, time, venue)

    @Test
    fun `formatFormDate round-trips parseFormDate`() {
        assertEquals("05/11/2026", formatFormDate(LocalDate.of(2026, 11, 5)))
        assertEquals("31/10/2026", formatFormDate(upcomingDate))
        listOf(upcomingDate, LocalDate.of(2027, 1, 1), LocalDate.of(2026, 2, 28)).forEach {
            assertEquals(it, parseFormDate(formatFormDate(it)))
        }
    }

    @Test
    fun `an untouched form has nothing to save`() {
        val check = checkJamEdit(upcoming, input(), snapshot, today)
        assertNull(check.dateProblem)
        assertFalse(check.dateChanged)
        assertFalse(check.canSave)
    }

    @Test
    fun `a changed time or venue can be saved, the venue compared after normalization`() {
        assertTrue(checkJamEdit(upcoming, input(time = "20:30"), snapshot, today).canSave)
        assertTrue(checkJamEdit(upcoming, input(venue = "El Sotano"), snapshot, today).canSave)
        assertFalse(checkJamEdit(upcoming, input(venue = "  La   Macanuda "), snapshot, today).canSave)
    }

    @Test
    fun `a new date is saved with its parsed values and flags the date change`() {
        val check =
            checkJamEdit(upcoming, input(date = "7/11/2026", time = "20.30", venue = " Bar  X "), snapshot, today)
        assertTrue(check.canSave)
        assertTrue(check.dateChanged)
        assertEquals(JamFormValues(LocalDate.of(2026, 11, 7), LocalTime.of(20, 30), "Bar X"), check.values)
    }

    @Test
    fun `each field has its own error`() {
        assertEquals(JamEditDateProblem.FORMAT, checkJamEdit(upcoming, input(date = "x"), snapshot, today).dateProblem)
        assertEquals(
            JamEditDateProblem.PAST,
            checkJamEdit(upcoming, input(date = "13/10/2026"), snapshot, today).dateProblem,
        )
        assertEquals(
            JamEditDateProblem.EXISTS,
            checkJamEdit(upcoming, input(date = "13/11/2026"), snapshot, today).dateProblem,
        )
        assertTrue(checkJamEdit(upcoming, input(time = "25:00"), snapshot, today).timeInvalid)
        val empty = checkJamEdit(upcoming, input(venue = " "), snapshot, today)
        assertEquals(VenueProblem.EMPTY, empty.venueProblem)
        assertFalse(empty.canSave)
        assertEquals(VenueProblem.FORMULA, checkJamEdit(upcoming, input(venue = "=x"), snapshot, today).venueProblem)
    }

    @Test
    fun `the upcoming jam cannot pass the first held-back jam and the error names that jam`() {
        val check = checkJamEdit(upcoming, input(date = "14/11/2026"), snapshot, today)
        assertEquals(JamEditDateProblem.LATER, check.dateProblem)
        assertEquals(heldDate, check.otherDate)
        assertFalse(check.canSave)
        assertNull(checkJamEdit(upcoming, input(date = "12/11/2026"), snapshot, today).dateProblem)
    }

    @Test
    fun `a held-back jam cannot move before the upcoming jam and the error names the upcoming jam`() {
        val check = checkJamEdit(held, input(date = "30/10/2026"), snapshot, today)
        assertEquals(JamEditDateProblem.EARLIER, check.dateProblem)
        assertEquals(upcomingDate, check.otherDate)
        assertFalse(check.canSave)
        assertTrue(checkJamEdit(held, input(date = "20/12/2026"), snapshot, today).canSave)
    }

    @Test
    fun `today is allowed and the relevant jam is only named when it matters`() {
        val check = checkJamEdit(upcoming, input(date = "14/10/2026"), snapshot, today)
        assertNull(check.dateProblem)
        assertNull(check.otherDate)
        assertTrue(check.canSave)
    }

    @Test
    fun `without a snapshot nothing can be saved`() {
        assertFalse(checkJamEdit(upcoming, input(time = "20:00"), null, today).canSave)
    }
}
