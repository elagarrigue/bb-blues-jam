package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.ProposedJam
import com.bbbjam.core.model.JamStatus
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class JamPlanTest {
    private fun proposed(date: LocalDate) = ProposedJam(date, LocalTime.of(21, 0), "La Macanuda")

    private val later = LocalDate.of(2026, 11, 28)
    private val withUpcoming = snapshot(jam(JamStatus.PUBLISHED))

    @Test
    fun `an unread snapshot is unknown`() {
        assertEquals(JamPlan.Unknown, jamPlan(proposed(later), null, TODAY))
    }

    @Test
    fun `a date before today is blocked as past`() {
        assertEquals(
            JamPlan.Blocked(JamPlan.Blocked.Reason.PAST),
            jamPlan(proposed(TODAY.minusDays(1)), withUpcoming, TODAY),
        )
    }

    @Test
    fun `today is allowed`() {
        assertEquals(JamPlan.Ready(JamHeldBack.None), jamPlan(proposed(TODAY), snapshot(upcoming = null), TODAY))
    }

    @Test
    fun `the upcoming date and a past jam date are blocked as existing`() {
        assertEquals(JamPlan.Blocked(JamPlan.Blocked.Reason.EXISTS), jamPlan(proposed(JAM_DATE), withUpcoming, TODAY))
        val past = withUpcoming.copy(past = listOf(jam().copy(date = LocalDate.of(2026, 10, 14))))
        assertEquals(JamPlan.Blocked(JamPlan.Blocked.Reason.EXISTS), jamPlan(proposed(TODAY), past, TODAY))
    }

    @Test
    fun `a date earlier than the upcoming jam is blocked and names that jam`() {
        assertEquals(
            JamPlan.Blocked(JamPlan.Blocked.Reason.EARLIER, JAM_DATE),
            jamPlan(proposed(LocalDate.of(2026, 10, 20)), withUpcoming, TODAY),
        )
    }

    @Test
    fun `a later date waits behind the upcoming jam`() {
        assertEquals(
            JamPlan.Ready(JamHeldBack.NewWaits(JAM_DATE)),
            jamPlan(proposed(later), withUpcoming, TODAY),
        )
    }

    @Test
    fun `with no upcoming jam the new one is not held back`() {
        assertEquals(JamPlan.Ready(JamHeldBack.None), jamPlan(proposed(later), snapshot(upcoming = null), TODAY))
    }
}
