package com.bbbjam.core.data.jams

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Moved from the assistant feature's `JamPlanTest` (assertions unchanged), plus the held-back date. */
class JamCreationPlanTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val later = LocalDate.of(2026, 11, 28)
    private val fetched = Freshness(Instant.parse("2026-10-14T12:00:00Z"), lastFailure = null, isRefreshing = false)

    private fun jam(date: LocalDate = jamDate, status: JamStatus = JamStatus.PUBLISHED) =
        Jam(date, LocalTime.of(21, 0), "La Macanuda", status, Setlist.Available(emptyList()))

    private fun snapshot(upcoming: Jam? = jam(), heldBack: List<Jam> = emptyList()) =
        JamsSnapshot(upcoming, emptyList(), fetched, heldBack)

    private val withUpcoming = snapshot()

    @Test
    fun `an unread snapshot is unknown`() {
        assertEquals(JamPlan.Unknown, jamCreationPlan(later, null, today))
    }

    @Test
    fun `a date before today is blocked as past`() {
        assertEquals(
            JamPlan.Blocked(JamPlan.Blocked.Reason.PAST),
            jamCreationPlan(today.minusDays(1), withUpcoming, today),
        )
    }

    @Test
    fun `today is allowed`() {
        assertEquals(JamPlan.Ready(JamHeldBack.None), jamCreationPlan(today, snapshot(upcoming = null), today))
    }

    @Test
    fun `the upcoming date and a past jam date are blocked as existing`() {
        assertEquals(JamPlan.Blocked(JamPlan.Blocked.Reason.EXISTS), jamCreationPlan(jamDate, withUpcoming, today))
        val past = withUpcoming.copy(past = listOf(jam(LocalDate.of(2026, 10, 14))))
        assertEquals(JamPlan.Blocked(JamPlan.Blocked.Reason.EXISTS), jamCreationPlan(today, past, today))
    }

    @Test
    fun `a held-back jam date is blocked as existing too`() {
        val withHeldBack = snapshot(heldBack = listOf(jam(later, JamStatus.DRAFT)))
        assertEquals(JamPlan.Blocked(JamPlan.Blocked.Reason.EXISTS), jamCreationPlan(later, withHeldBack, today))
        assertEquals(
            JamPlan.Ready(JamHeldBack.NewWaits(jamDate)),
            jamCreationPlan(later.plusDays(7), withHeldBack, today),
        )
    }

    @Test
    fun `a date earlier than the upcoming jam is blocked and names that jam`() {
        assertEquals(
            JamPlan.Blocked(JamPlan.Blocked.Reason.EARLIER, jamDate),
            jamCreationPlan(LocalDate.of(2026, 10, 20), withUpcoming, today),
        )
    }

    @Test
    fun `a later date waits behind the upcoming jam`() {
        assertEquals(JamPlan.Ready(JamHeldBack.NewWaits(jamDate)), jamCreationPlan(later, withUpcoming, today))
    }

    @Test
    fun `with no upcoming jam the new one is not held back`() {
        assertEquals(JamPlan.Ready(JamHeldBack.None), jamCreationPlan(later, snapshot(upcoming = null), today))
    }
}
