package com.bbbjam.feature.assistant.run

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.assistant.ProposedJam
import com.bbbjam.core.data.setlist.jamcreate.CreateJamOutcome
import com.bbbjam.feature.assistant.CreateCall
import com.bbbjam.feature.assistant.ScriptedJamCreator
import com.bbbjam.feature.assistant.SettableJamsRepository
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JamCreationRunnerTest {
    private val creator = ScriptedJamCreator()
    private val jams = SettableJamsRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val runner = JamCreationRunner(creator, jams, scope)
    private val jam = ProposedJam(LocalDate.of(2026, 11, 28), LocalTime.of(21, 0), "La Macanuda")

    @Test
    fun `a success is recorded and refreshes the jams once`() = runTest {
        assertTrue(runner.create(1, jam))

        assertEquals(mapOf(1L to JamCreation.Created), runner.creations.value)
        assertEquals(listOf(CreateCall(jam.date, jam.startTime, jam.venue)), creator.calls)
        assertEquals(1, jams.refreshes)
    }

    @Test
    fun `only one creation runs at a time`() = runTest {
        creator.gate = CompletableDeferred()

        assertTrue(runner.create(1, jam))
        assertEquals(JamCreation.Creating, runner.creations.value[1])
        assertFalse(runner.create(2, jam))
        assertEquals(1, creator.calls.size)

        creator.gate!!.complete(Unit)
        assertEquals(JamCreation.Created, runner.creations.value[1])
        assertTrue(runner.create(2, jam))
    }

    @Test
    fun `refusals and an outdated script are recorded and do not refresh`() = runTest {
        creator.outcome = CreateJamOutcome.NotCreated(WriteOutcome.Rejected("jam_exists"))
        runner.create(1, jam)
        creator.outcome = CreateJamOutcome.ScriptOutdated
        runner.create(1, jam)

        assertEquals(JamCreation.Failed(JamFailure.ScriptOutdated), runner.creations.value[1])
        creator.outcome = CreateJamOutcome.NotCreated(WriteOutcome.Offline)
        runner.create(3, jam)
        assertEquals(JamCreation.Failed(JamFailure.Write(WriteOutcome.Offline)), runner.creations.value[3])
        assertEquals(0, jams.refreshes)
    }

    @Test
    fun `a cancelled scope marks the creation interrupted, never created`() = runTest {
        creator.gate = CompletableDeferred()
        runner.create(1, jam)

        scope.cancel()

        assertEquals(JamCreation.Failed(JamFailure.Interrupted), runner.creations.value[1])
        assertEquals(0, jams.refreshes)
    }
}
