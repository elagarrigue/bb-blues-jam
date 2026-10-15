package com.bbbjam.feature.assistant

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.SongId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalApplierTest {
    private val adder = FakeSetlistSongAdder()

    private companion object {
        val KEYS = listOf("A", "B", "C", "D", "E", "F", "G", "A#", "Bb", "C#", "D#")
    }

    private fun plan(count: Int = 5, skipped: List<Int> = emptyList()) = ApplyPlan.Ready(
        JAM_DATE,
        JamStatus.DRAFT,
        (1..count).filter { it !in skipped }.map { song(it, key = KEYS[it - 1]) },
        skipped.map { song(it) },
        existingCount = 0,
    )

    private fun TestScope.applier(): Pair<ProposalApplier, ChatScope> {
        val scope = ChatScope(testScheduler)
        return testApplier(adder, scope.scope) to scope
    }

    private fun ProposalApplier.run(turn: Long = 1) = runs.value.getValue(turn)

    private fun ApplyRun.states() = songs.associate { it.song.songId.value to it.state }

    @Test
    fun `songs are added in proposal order with the proposal keys and skipped ones are never sent`() = runTest {
        val (applier, _) = applier()
        assertTrue(applier.apply(1, plan(skipped = listOf(2))))

        assertEquals(listOf("s1", "s3", "s4", "s5"), adder.calls.map { it.songId.value })
        assertEquals(listOf("A", "C", "D", "E"), adder.calls.map { it.key.value })
        assertEquals(setOf(JAM_DATE), adder.calls.map { it.date }.toSet())
        val run = applier.run()
        assertEquals(ApplyRun.State.Finished, run.state)
        assertTrue(run.isSuccess)
        assertEquals(4, run.addedCount)
        assertEquals(SongApply.State.Skipped, run.states().getValue("s2"))
    }

    @Test
    fun `a server duplicate is skipped and the run continues`() = runTest {
        val (applier, _) = applier()
        adder.reject(2, WriteOutcome.Rejected("song_already_in_setlist"))
        applier.apply(1, plan(3))

        assertEquals(3, adder.calls.size)
        assertEquals(SongApply.State.Skipped, applier.run().states().getValue("s2"))
        assertTrue(applier.run().isSuccess)
    }

    @Test
    fun `a per-song refusal continues and the run is never a success`() = runTest {
        val (applier, _) = applier()
        for ((n, code) in listOf(2 to "unknown_song", 3 to "invalid_song", 4 to "invalid_key")) {
            adder.reject(n, WriteOutcome.Rejected(code))
        }
        applier.apply(1, plan(5))

        assertEquals(5, adder.calls.size)
        val run = applier.run()
        assertEquals(ApplyRun.State.Finished, run.state)
        assertEquals(3, run.failedCount)
        assertEquals(2, run.addedCount)
        assertFalse(run.isSuccess)
    }

    @Test
    fun `a systemic outcome aborts after one call and leaves the rest not attempted`() = runTest {
        val outcomes = listOf(
            WriteOutcome.AccessRefused,
            WriteOutcome.Offline,
            WriteOutcome.Unavailable,
            WriteOutcome.Rejected("jam_not_editable"),
            WriteOutcome.Rejected("unknown_jam"),
            WriteOutcome.Rejected("something_new"),
        )
        for (outcome in outcomes) {
            val fresh = FakeSetlistSongAdder().apply { reject(1, outcome) }
            val applier = testApplier(fresh, ChatScope(testScheduler).scope)
            applier.apply(1, plan(4))

            assertEquals(outcome.toString(), 1, fresh.calls.size)
            val run = applier.run()
            assertEquals(ApplyRun.State.Aborted(outcome), run.state)
            assertEquals(SongApply.State.Failed(outcome), run.states().getValue("s1"))
            assertEquals(
                listOf<SongApply.State>(SongApply.State.NotAttempted),
                run.states().filterKeys { it != "s1" }.values.distinct(),
            )
            assertFalse(run.isSuccess)
        }
    }

    @Test
    fun `stop keeps the song in flight and leaves the rest not attempted`() = runTest {
        val (applier, _) = applier()
        val gate = CompletableDeferred<Unit>()
        adder.gates[SongId("s4")] = gate
        applier.apply(1, plan(11))
        assertEquals(4, adder.calls.size)
        assertEquals(SongApply.State.Adding, applier.run().states().getValue("s4"))

        applier.stop(1)
        gate.complete(Unit)

        assertEquals(4, adder.calls.size)
        val run = applier.run()
        assertEquals(ApplyRun.State.Stopped, run.state)
        assertEquals(4, run.addedCount)
        assertTrue(run.states().getValue("s4") is SongApply.State.Added)
        assertEquals(SongApply.State.NotAttempted, run.states().getValue("s5"))
        assertFalse(run.isSuccess)
    }

    @Test
    fun `a second apply while one runs returns false and sends nothing`() = runTest {
        val (applier, _) = applier()
        val gate = CompletableDeferred<Unit>()
        adder.gates[SongId("s1")] = gate
        assertTrue(applier.apply(1, plan(3)))
        assertFalse(applier.apply(1, plan(3)))
        assertFalse(applier.apply(2, plan(3)))
        assertEquals(1, adder.calls.size)

        gate.complete(Unit)
        assertEquals(3, adder.calls.size)
        assertTrue(applier.apply(2, plan(3)))
    }

    @Test
    fun `cancelling the scope marks the run interrupted, never a success`() = runTest {
        val (applier, scope) = applier()
        adder.gates[SongId("s2")] = CompletableDeferred()
        applier.apply(1, plan(4))

        scope.scope.cancel()

        val run = applier.run()
        assertEquals(ApplyRun.State.Interrupted, run.state)
        assertFalse(run.isSuccess)
        assertEquals(SongApply.State.Added(1), run.states().getValue("s1"))
        assertEquals(SongApply.State.Failed(WriteOutcome.Offline), run.states().getValue("s2"))
        assertEquals(SongApply.State.NotAttempted, run.states().getValue("s3"))
    }

    @Test
    fun `the key sent is the proposal key untouched`() = runTest {
        val (applier, _) = applier()
        applier.apply(1, plan(1))
        assertEquals(Key("A"), adder.calls.single().key)
    }
}
