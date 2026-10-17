package com.bbbjam.feature.assistant.run

import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Setlist
import com.bbbjam.feature.assistant.JAM_DATE
import com.bbbjam.feature.assistant.jam
import com.bbbjam.feature.assistant.listed
import com.bbbjam.feature.assistant.snapshot
import com.bbbjam.feature.assistant.song
import com.bbbjam.feature.assistant.unreadable
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyPlanTest {
    private val songs = (1..4).map { song(it) }

    @Test
    fun `no snapshot yet is unknown`() {
        assertEquals(ApplyPlan.Unknown, applyPlan(songs, null))
    }

    @Test
    fun `no upcoming jam is blocked`() {
        assertEquals(
            ApplyPlan.Blocked(ApplyPlan.Blocked.Reason.NO_UPCOMING),
            applyPlan(songs, snapshot(upcoming = null)),
        )
    }

    @Test
    fun `a withheld or unavailable setlist is blocked as unreadable`() {
        val blocked = ApplyPlan.Blocked(ApplyPlan.Blocked.Reason.UNREADABLE)
        assertEquals(blocked, applyPlan(songs, snapshot(jam(JamStatus.DRAFT, Setlist.Withheld))))
        assertEquals(blocked, applyPlan(songs, snapshot(jam(JamStatus.PUBLISHED, unreadable))))
    }

    @Test
    fun `every song already listed is blocked`() {
        val plan = applyPlan(songs, snapshot(jam(setlist = listed(1, 2, 3, 4, 9))))
        assertEquals(ApplyPlan.Blocked(ApplyPlan.Blocked.Reason.ALL_PRESENT), plan)
    }

    @Test
    fun `a draft is ready with the new songs in proposal order and the repeats skipped`() {
        val plan = applyPlan(songs, snapshot(jam(setlist = listed(2, 9))))
        assertEquals(
            ApplyPlan.Ready(JAM_DATE, JamStatus.DRAFT, listOf(song(1), song(3), song(4)), listOf(song(2)), 2),
            plan,
        )
    }

    @Test
    fun `a published jam is ready and says so`() {
        val plan = applyPlan(songs, snapshot(jam(JamStatus.PUBLISHED, listed(1)))) as ApplyPlan.Ready
        assertEquals(JamStatus.PUBLISHED, plan.status)
        assertEquals(listOf(song(2), song(3), song(4)), plan.toAdd)
    }

    @Test
    fun `an empty list is ready with nothing existing`() {
        val plan = applyPlan(songs, snapshot()) as ApplyPlan.Ready
        assertEquals(0, plan.existingCount)
        assertEquals(songs, plan.toAdd)
        assertEquals(emptyList<Any>(), plan.skipped)
    }
}
