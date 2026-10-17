package com.bbbjam.feature.assistant.run

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.assistant.ProposedJam
import com.bbbjam.core.data.setlist.CreateJamOutcome
import com.bbbjam.core.model.SongId
import com.bbbjam.feature.assistant.CallLog
import com.bbbjam.feature.assistant.ChatScope
import com.bbbjam.feature.assistant.FakeJamCreator
import com.bbbjam.feature.assistant.FakeSetlistSongAdder
import com.bbbjam.feature.assistant.FakeSetlistSongMover
import com.bbbjam.feature.assistant.FakeSetlistSongRemover
import com.bbbjam.feature.assistant.JAM_DATE
import com.bbbjam.feature.assistant.jam
import com.bbbjam.feature.assistant.listedOrder
import com.bbbjam.feature.assistant.snapshot
import com.bbbjam.feature.assistant.song
import com.bbbjam.feature.assistant.testApplier
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The replace and create runs of [ProposalApplier] (`assistant-proposal-targets`, R1 to R4, C2). */
class ProposalApplierReplaceTest {
    private val log: CallLog = mutableListOf()
    private val adder = FakeSetlistSongAdder(log)
    private val remover = FakeSetlistSongRemover(log)
    private val mover = FakeSetlistSongMover(log)
    private val creator = FakeJamCreator(log)

    // The list holds 7, 1, 8, 2; the proposal is 3, 1, 5, 2.
    private val proposal = listOf(3, 1, 5, 2).map { song(it) }
    private val newJam = ProposedJam(LocalDate.of(2026, 11, 28), LocalTime.of(21, 0), "Sala")

    private fun plan(list: IntArray = intArrayOf(7, 1, 8, 2)) =
        replacePlan(proposal, snapshot(jam(setlist = listedOrder(*list)))) as ReplacePlan.Ready

    private fun TestScope.applier(): Pair<ProposalApplier, ChatScope> {
        val scope = ChatScope(testScheduler)
        return testApplier(adder, scope.scope, remover, mover, creator) to scope
    }

    private fun ProposalApplier.run(turn: Long = 1) = runs.value.getValue(turn)

    private fun ApplyRun.songStates() = songs.associate { it.song.songId.value to it.state }

    private fun ApplyRun.removalStates() = removals.associate { it.song.songId.value to it.state }

    @Test
    fun `a replace adds, then removes, then moves, one call at a time`() = runTest {
        val (applier, _) = applier()
        assertTrue(applier.replace(1, plan()))

        assertEquals(
            listOf("add s3", "add s5", "remove s7", "remove s8", "move s3@1", "move s5@3"),
            log,
        )
        val run = applier.run()
        assertEquals(ApplyMode.REPLACE, run.mode)
        assertEquals(JAM_DATE, run.jamDate)
        assertEquals(ApplyRun.State.Finished, run.state)
        assertTrue(run.isSuccess)
        assertEquals(OrderStep.Ordered, run.order)
        assertEquals(listOf("s3", "s1", "s5", "s2").map(::SongId), run.target)
        assertEquals(SongApply.State.Kept, run.songStates().getValue("s1"))
        assertEquals(SongApply.State.Kept, run.songStates().getValue("s2"))
        assertTrue(run.removals.all { it.state == SongRemoval.State.Removed })
    }

    @Test
    fun `kept songs are never sent anywhere and added ones carry the proposal key`() = runTest {
        val (applier, _) = applier()
        applier.replace(1, plan())

        val touched = adder.calls.map { it.songId.value } + remover.calls.map { it.songId.value }
        assertFalse("s1" in touched)
        assertFalse("s2" in touched)
        assertEquals(listOf("Bm", "Bm"), adder.calls.map { it.key.value })
    }

    @Test
    fun `an empty list only adds and needs no move`() = runTest {
        val (applier, _) = applier()
        applier.replace(1, plan(intArrayOf()))

        assertEquals(listOf("add s3", "add s1", "add s5", "add s2"), log)
        assertTrue(applier.run().isSuccess)
        assertTrue(applier.run().removals.isEmpty())
    }

    @Test
    fun `a list already holding the proposal in another order only moves`() = runTest {
        val (applier, _) = applier()
        applier.replace(1, plan(intArrayOf(1, 2, 3, 5)))

        assertTrue(log.isNotEmpty())
        assertTrue(log.all { it.startsWith("move") })
        assertTrue(applier.run().isSuccess)
    }

    @Test
    fun `an add that aborts leaves every removal and the order not attempted`() = runTest {
        val (applier, _) = applier()
        adder.reject(3, WriteOutcome.Offline)
        applier.replace(1, plan())

        assertEquals(listOf("add s3"), log)
        val run = applier.run()
        assertEquals(ApplyRun.State.Aborted(WriteOutcome.Offline), run.state)
        assertTrue(run.removals.all { it.state == SongRemoval.State.NotAttempted })
        assertEquals(OrderStep.NotAttempted, run.order)
        assertFalse(run.isSuccess)
    }

    @Test
    fun `a per-song add refusal still removes and orders what is present`() = runTest {
        val (applier, _) = applier()
        adder.reject(5, WriteOutcome.Rejected("unknown_song"))
        applier.replace(1, plan())

        assertEquals(listOf("add s3", "add s5", "remove s7", "remove s8", "move s3@1"), log)
        assertFalse(applier.run().isSuccess)
        assertEquals(OrderStep.Ordered, applier.run().order)
    }

    @Test
    fun `song_not_in_setlist counts as removed`() = runTest {
        val (applier, _) = applier()
        remover.reject(7, WriteOutcome.Rejected("song_not_in_setlist"))
        applier.replace(1, plan())

        assertEquals(SongRemoval.State.Removed, applier.run().removalStates().getValue("s7"))
        assertTrue(applier.run().isSuccess)
    }

    @Test
    fun `a removal that aborts leaves the rest and the order not attempted`() = runTest {
        val (applier, _) = applier()
        remover.reject(7, WriteOutcome.Unavailable)
        applier.replace(1, plan())

        assertEquals(listOf("add s3", "add s5", "remove s7"), log)
        val run = applier.run()
        assertEquals(ApplyRun.State.Aborted(WriteOutcome.Unavailable), run.state)
        assertEquals(SongRemoval.State.Failed(WriteOutcome.Unavailable), run.removalStates().getValue("s7"))
        assertEquals(SongRemoval.State.NotAttempted, run.removalStates().getValue("s8"))
        assertEquals(OrderStep.NotAttempted, run.order)
        assertFalse(run.isSuccess)
    }

    @Test
    fun `a per-song removal failure continues but skips the reorder and is no success`() = runTest {
        val (applier, _) = applier()
        remover.reject(7, WriteOutcome.Rejected("duplicate_song"))
        applier.replace(1, plan())

        assertEquals(listOf("add s3", "add s5", "remove s7", "remove s8"), log)
        val run = applier.run()
        assertEquals(SongRemoval.State.Removed, run.removalStates().getValue("s8"))
        assertEquals(OrderStep.NotAttempted, run.order)
        assertFalse(run.isSuccess)
    }

    @Test
    fun `a refused move aborts the reorder and is no success`() = runTest {
        val (applier, _) = applier()
        val refusal = WriteOutcome.Rejected("unordered_setlist")
        mover.reject(3, refusal)
        applier.replace(1, plan())

        assertEquals("move s3@1", log.last())
        val run = applier.run()
        assertEquals(ApplyRun.State.Aborted(refusal), run.state)
        assertEquals(OrderStep.Failed(refusal), run.order)
        assertFalse(run.isSuccess)
    }

    @Test
    fun `stop between phases keeps what was done and leaves the rest not attempted`() = runTest {
        val (applier, _) = applier()
        val gate = CompletableDeferred<Unit>()
        remover.gates[SongId("s7")] = gate
        applier.replace(1, plan())

        applier.stop(1)
        gate.complete(Unit)

        assertEquals(listOf("add s3", "add s5", "remove s7"), log)
        val run = applier.run()
        assertEquals(ApplyRun.State.Stopped, run.state)
        assertEquals(SongRemoval.State.Removed, run.removalStates().getValue("s7"))
        assertEquals(SongRemoval.State.NotAttempted, run.removalStates().getValue("s8"))
        assertEquals(OrderStep.NotAttempted, run.order)
        assertFalse(run.isSuccess)
    }

    @Test
    fun `cancelling mid-removal marks it failed as offline and the run interrupted`() = runTest {
        val (applier, scope) = applier()
        remover.gates[SongId("s8")] = CompletableDeferred()
        applier.replace(1, plan())

        scope.scope.cancel()

        val run = applier.run()
        assertEquals(ApplyRun.State.Interrupted, run.state)
        assertEquals(SongRemoval.State.Removed, run.removalStates().getValue("s7"))
        assertEquals(SongRemoval.State.Failed(WriteOutcome.Offline), run.removalStates().getValue("s8"))
        assertEquals(OrderStep.NotAttempted, run.order)
        assertFalse(run.isSuccess)
    }

    @Test
    fun `one run at a time across the three modes`() = runTest {
        val (applier, _) = applier()
        val gate = CompletableDeferred<Unit>()
        adder.gates[SongId("s3")] = gate
        assertTrue(applier.replace(1, plan()))

        assertFalse(applier.replace(2, plan()))
        assertFalse(applier.create(2, newJam.date, newJam.startTime, newJam.venue, proposal))
        assertFalse(applier.apply(2, ApplyPlan.Ready(JAM_DATE, plan().status, proposal, emptyList(), 0)))
        assertEquals(1, adder.calls.size)

        gate.complete(Unit)
        assertTrue(applier.create(2, newJam.date, newJam.startTime, newJam.venue, proposal))
    }

    @Test
    fun `a failed create adds nothing`() = runTest {
        val (applier, _) = applier()
        creator.outcome = CreateJamOutcome.NotCreated(WriteOutcome.Rejected("jam_exists"))
        applier.create(1, newJam.date, newJam.startTime, newJam.venue, proposal)

        assertEquals(listOf("create"), log)
        val run = applier.run()
        assertEquals(ApplyMode.CREATE, run.mode)
        assertEquals(JamStep.Failed(JamFailure.Write(WriteOutcome.Rejected("jam_exists"))), run.jam)
        assertEquals(ApplyRun.State.Aborted(WriteOutcome.Rejected("jam_exists")), run.state)
        assertNull(run.jamDate)
        assertTrue(run.songs.all { it.state == SongApply.State.NotAttempted })
        assertFalse(run.isSuccess)
    }

    @Test
    fun `an outdated script creates and adds nothing`() = runTest {
        val (applier, _) = applier()
        creator.outcome = CreateJamOutcome.ScriptOutdated
        applier.create(1, newJam.date, newJam.startTime, newJam.venue, proposal)

        assertEquals(listOf("create"), log)
        assertEquals(JamStep.Failed(JamFailure.ScriptOutdated), applier.run().jam)
        assertEquals(ApplyRun.State.ScriptOutdated, applier.run().state)
        assertFalse(applier.run().isSuccess)
    }

    @Test
    fun `create loads every song into the date the server echoed`() = runTest {
        val (applier, _) = applier()
        val echoed = LocalDate.of(2026, 11, 29)
        creator.outcome = CreateJamOutcome.Created(echoed, LocalTime.of(21, 0), "Sala")
        applier.create(1, newJam.date, newJam.startTime, newJam.venue, proposal)

        assertEquals(listOf("create", "add s3", "add s1", "add s5", "add s2"), log)
        assertEquals(setOf(echoed), adder.calls.map { it.date }.toSet())
        val run = applier.run()
        assertEquals(echoed, run.jamDate)
        assertEquals(JamStep.Created(echoed), run.jam)
        assertTrue(run.isSuccess)
        assertEquals(4, run.addedCount)
    }

    @Test
    fun `a create whose adds partly fail is created but no success`() = runTest {
        val (applier, _) = applier()
        adder.reject(1, WriteOutcome.Rejected("unknown_song"))
        applier.create(1, newJam.date, newJam.startTime, newJam.venue, proposal)

        val run = applier.run()
        assertEquals(JamStep.Created(newJam.date), run.jam)
        assertEquals(3, run.addedCount)
        assertEquals(1, run.failedCount)
        assertFalse(run.isSuccess)
    }

    @Test
    fun `cancelling during the create call marks the jam step interrupted`() = runTest {
        val (applier, scope) = applier()
        creator.gate = CompletableDeferred()
        applier.create(1, newJam.date, newJam.startTime, newJam.venue, proposal)
        assertEquals(JamStep.Creating, applier.run().jam)

        scope.scope.cancel()

        assertEquals(JamStep.Failed(JamFailure.Interrupted), applier.run().jam)
        assertEquals(ApplyRun.State.Interrupted, applier.run().state)
        assertTrue(adder.calls.isEmpty())
    }
}
