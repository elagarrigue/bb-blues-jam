package com.bbbjam.feature.assistant

import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacePlanTest {
    private val proposal = listOf(3, 1, 5, 2).map { song(it) }

    private fun ready(plan: ReplacePlan) = plan as ReplacePlan.Ready

    private fun ids(vararg n: Int) = n.map { SongId("s$it") }

    @Test
    fun `no snapshot yet is unknown`() {
        assertEquals(ReplacePlan.Unknown, replacePlan(proposal, null))
    }

    @Test
    fun `no upcoming jam is blocked`() {
        assertEquals(
            ReplacePlan.Blocked(ReplacePlan.Blocked.Reason.NO_UPCOMING),
            replacePlan(proposal, snapshot(upcoming = null)),
        )
    }

    @Test
    fun `a withheld or unavailable setlist is blocked as unreadable`() {
        val blocked = ReplacePlan.Blocked(ReplacePlan.Blocked.Reason.UNREADABLE)
        assertEquals(blocked, replacePlan(proposal, snapshot(jam(JamStatus.DRAFT, Setlist.Withheld))))
        assertEquals(blocked, replacePlan(proposal, snapshot(jam(JamStatus.PUBLISHED, unreadable))))
    }

    @Test
    fun `a cached list that repeats a song id is blocked`() {
        val repeated = listedOrder(1, 2, 1)
        assertEquals(
            ReplacePlan.Blocked(ReplacePlan.Blocked.Reason.REPEATED_IDS),
            replacePlan(proposal, snapshot(jam(setlist = repeated))),
        )
    }

    @Test
    fun `a list with the same ids in the same order is already equal`() {
        assertEquals(
            ReplacePlan.Blocked(ReplacePlan.Blocked.Reason.ALREADY_EQUAL),
            replacePlan(proposal, snapshot(jam(setlist = listedOrder(3, 1, 5, 2)))),
        )
    }

    @Test
    fun `the same ids in another order are not equal and need only moves`() {
        val plan = ready(replacePlan(proposal, snapshot(jam(setlist = listedOrder(1, 2, 3, 5)))))
        assertTrue(plan.toAdd.isEmpty())
        assertTrue(plan.toRemove.isEmpty())
        assertEquals(ids(3, 1, 5, 2), plan.target)
        assertEquals(ids(1, 2, 3, 5), plan.current)
    }

    @Test
    fun `an empty list is ready with only adds`() {
        val plan = ready(replacePlan(proposal, snapshot(jam(setlist = listedOrder()))))
        assertEquals(ids(3, 1, 5, 2), plan.toAdd.map { it.songId })
        assertTrue(plan.toRemove.isEmpty())
        assertTrue(plan.kept.isEmpty())
        assertEquals(0, plan.existingCount)
        assertEquals(0, plan.lostAssignments)
    }

    @Test
    fun `adds follow the proposal order, removes the list order, and the rest is kept`() {
        val plan = ready(replacePlan(proposal, snapshot(jam(setlist = listedOrder(7, 1, 8, 2)))))

        assertEquals(ids(3, 5), plan.toAdd.map { it.songId })
        assertEquals(ids(7, 8), plan.toRemove.map { it.songId })
        assertEquals(ids(1, 2), plan.kept.map { it.songId })
        assertEquals(4, plan.existingCount)
        assertEquals(JAM_DATE, plan.jamDate)
        assertEquals(JamStatus.DRAFT, plan.status)
        assertEquals(ids(3, 1, 5, 2), plan.target)
        assertEquals(ids(7, 1, 8, 2), plan.current)
    }

    @Test
    fun `lost assignments are the filled slots and extras of the removed songs only`() {
        val busy = JamSong(
            1,
            SongId("s7"),
            "T",
            "A",
            Key("Bm"),
            Lineup(listOf(Slot(Instrument.GUITAR, "Ana"), Slot(Instrument.BASS, "Beto"), Slot(Instrument.DRUMS))),
            listOf(ExtraParticipant("Cris", "saxo")),
        )
        val keptBusy = JamSong(2, SongId("s1"), "T", "A", Key("Bm"), Lineup(listOf(Slot(Instrument.GUITAR, "Dani"))))
        val plan = ready(
            replacePlan(proposal, snapshot(jam(setlist = Setlist.Available(listOf(busy, keptBusy, jamSong(8)))))),
        )

        assertEquals(3, plan.lostAssignments)
    }

    @Test
    fun `kept songs are the cached rows, so their key is the admin's and not the proposal's`() {
        val adminKey = JamSong(1, SongId("s1"), "T", "A", Key("Eb"), Lineup.default())
        val plan =
            ready(
                replacePlan(
                    listOf(song(1, key = "A")),
                    snapshot(jam(setlist = Setlist.Available(listOf(adminKey, jamSong(9).copy(position = 2))))),
                ),
            )

        assertTrue(plan.toAdd.isEmpty())
        assertEquals(listOf(Key("Eb")), plan.kept.map { it.key })
    }
}

class ReorderMovesTest {
    private fun ids(vararg n: Int) = n.map { SongId("s$it") }

    private fun moves(current: List<SongId>, target: List<SongId>) = reorderMoves(current, target)

    private fun simulate(current: List<SongId>, moves: List<SongMove>): List<SongId> {
        val list = current.toMutableList()
        for (move in moves) {
            list.remove(move.songId)
            list.add(move.toPosition - 1, move.songId)
        }
        return list
    }

    @Test
    fun `an identical order needs no move`() {
        assertTrue(moves(ids(1, 2, 3), ids(1, 2, 3)).isEmpty())
    }

    @Test
    fun `a reversed list needs at most n minus one moves and ends in the target`() {
        val current = ids(1, 2, 3, 4, 5)
        val target = ids(5, 4, 3, 2, 1)
        val result = moves(current, target)

        assertTrue(result.size <= 4)
        assertEquals(target, simulate(current, result))
    }

    @Test
    fun `one song out of place is one move`() {
        assertEquals(listOf(SongMove(SongId("s4"), 1)), moves(ids(1, 2, 3, 4), ids(4, 1, 2, 3)))
    }

    @Test
    fun `positions are one based absolute positions`() {
        assertEquals(
            listOf(SongMove(SongId("s2"), 1), SongMove(SongId("s3"), 2)),
            moves(ids(1, 2, 3), ids(2, 3, 1)),
        )
    }

    @Test
    fun `a target shorter than the list because an add failed still orders what is present`() {
        val current = ids(1, 2, 3)
        val target = ids(3, 1)
        val result = moves(current, target)

        assertEquals(target, simulate(current, result).take(2))
    }
}
