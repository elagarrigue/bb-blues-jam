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

class JamEditPlanTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val upcoming = LocalDate.of(2026, 10, 31)
    private val first = LocalDate.of(2026, 11, 13)
    private val second = LocalDate.of(2026, 11, 28)
    private val past = LocalDate.of(2026, 9, 26)
    private val fetched = Freshness(Instant.parse("2026-10-14T12:00:00Z"), lastFailure = null, isRefreshing = false)

    private fun jam(date: LocalDate) =
        Jam(date, LocalTime.of(21, 0), "La Macanuda", JamStatus.DRAFT, Setlist.Available(emptyList()))

    private val snapshot = JamsSnapshot(jam(upcoming), listOf(jam(past)), fetched, listOf(jam(first), jam(second)))

    private fun plan(jamDate: LocalDate, newDate: LocalDate, from: JamsSnapshot? = snapshot) =
        jamEditPlan(jamDate, newDate, from, today)

    private fun blocked(reason: JamEditPlan.Blocked.Reason, other: LocalDate? = null) =
        JamEditPlan.Blocked(reason, other)

    @Test
    fun `an unread snapshot is unknown`() {
        assertEquals(JamEditPlan.Unknown, plan(upcoming, first, null))
    }

    @Test
    fun `a jam that is neither upcoming nor held back is gone`() {
        assertEquals(blocked(JamEditPlan.Blocked.Reason.GONE), plan(past, today.plusDays(1)))
        assertEquals(blocked(JamEditPlan.Blocked.Reason.GONE), plan(LocalDate.of(2027, 1, 1), second))
    }

    @Test
    fun `the same date is ready without a date change`() {
        assertEquals(JamEditPlan.Ready(false), plan(upcoming, upcoming))
        assertEquals(JamEditPlan.Ready(false), plan(first, first))
    }

    @Test
    fun `a date before today is past and today is allowed`() {
        assertEquals(blocked(JamEditPlan.Blocked.Reason.PAST), plan(upcoming, today.minusDays(1)))
        assertEquals(JamEditPlan.Ready(true), plan(upcoming, today))
    }

    @Test
    fun `a date held by another upcoming, held-back or past jam exists`() {
        assertEquals(blocked(JamEditPlan.Blocked.Reason.EXISTS), plan(upcoming, first))
        assertEquals(blocked(JamEditPlan.Blocked.Reason.EXISTS), plan(first, upcoming))
        assertEquals(blocked(JamEditPlan.Blocked.Reason.EXISTS), plan(first, second))
        val withToday = snapshot.copy(past = listOf(jam(today)))
        assertEquals(blocked(JamEditPlan.Blocked.Reason.EXISTS), plan(upcoming, today, withToday))
    }

    @Test
    fun `the upcoming jam cannot move past the first held-back jam`() {
        assertEquals(
            blocked(JamEditPlan.Blocked.Reason.LATER, first),
            plan(upcoming, first.plusDays(1)),
        )
        assertEquals(JamEditPlan.Ready(true), plan(upcoming, first.minusDays(1)))
    }

    @Test
    fun `the upcoming jam can move earlier`() {
        assertEquals(JamEditPlan.Ready(true), plan(upcoming, LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun `a held-back jam cannot move before the upcoming jam but may reorder among held-back jams`() {
        assertEquals(
            blocked(JamEditPlan.Blocked.Reason.EARLIER, upcoming),
            plan(first, upcoming.minusDays(1)),
        )
        assertEquals(JamEditPlan.Ready(true), plan(first, second.plusDays(7)))
        assertEquals(JamEditPlan.Ready(true), plan(second, upcoming.plusDays(1)))
    }

    @Test
    fun `with no held-back jam the upcoming jam can move anywhere in the future`() {
        val alone = JamsSnapshot(jam(upcoming), emptyList(), fetched)
        assertEquals(JamEditPlan.Ready(true), plan(upcoming, LocalDate.of(2027, 3, 1), alone))
    }
}
