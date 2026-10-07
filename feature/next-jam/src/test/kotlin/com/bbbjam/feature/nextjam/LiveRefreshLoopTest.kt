package com.bbbjam.feature.nextjam

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The periodic loop (`live-refresh-during-jam`, Decision 3) in virtual time: "now" is [base] plus the
 * test scheduler's clock, and every call is recorded at its virtual second.
 */
class LiveRefreshLoopTest {
    private val base: Instant = Instant.parse("2026-10-31T23:30:00Z")

    private fun window(fromSeconds: Long, toSeconds: Long) = base.plusSeconds(fromSeconds)..<base.plusSeconds(toSeconds)

    private class Run(val calls: MutableList<Long> = mutableListOf())

    /**
     * Starts the loop in [this] test scope; [results] are the outcomes of successive calls (true after
     * the list ends), [callTakes] how long each call takes.
     */
    private fun TestScope.start(
        windows: List<OpenEndRange<Instant>>,
        lastFetchedAt: Instant? = null,
        jitter: Duration = Duration.ZERO,
        results: List<Boolean> = emptyList(),
        callTakes: Duration = Duration.ZERO,
    ): Pair<Run, kotlinx.coroutines.Job> {
        val run = Run()
        val job = launch {
            runLiveRefresh(
                windows = windows,
                now = { base.plusMillis(testScheduler.currentTime) },
                lastFetchedAt = { lastFetchedAt },
                jitter = { jitter },
            ) {
                run.calls += testScheduler.currentTime / MILLIS
                delay(callTakes.toMillis())
                results.getOrElse(run.calls.size - 1) { true }
            }
        }
        return run to job
    }

    @Test
    fun `inside the window it refreshes at once and then every 30 s after a success`() = runTest {
        val (run, job) = start(listOf(window(0, HOURS_4)))

        advanceTimeBy(Duration.ofSeconds(89).toMillis())
        runCurrent()

        assertEquals(listOf(0L, 30L, 60L), run.calls)
        job.cancel()
    }

    @Test
    fun `after a failure the next call waits 60 s`() = runTest {
        val (run, job) = start(listOf(window(0, HOURS_4)), results = listOf(true, false, true))

        advanceTimeBy(Duration.ofSeconds(121).toMillis())
        runCurrent()

        assertEquals(listOf(0L, 30L, 90L, 120L), run.calls)
        job.cancel()
    }

    @Test
    fun `the period is measured from when a slow call returns`() = runTest {
        val (run, job) = start(listOf(window(0, HOURS_4)), callTakes = Duration.ofSeconds(5))

        advanceTimeBy(Duration.ofSeconds(71).toMillis())
        runCurrent()

        assertEquals(listOf(0L, 35L, 70L), run.calls)
        job.cancel()
    }

    @Test
    fun `outside every window it makes no call over 24 h`() = runTest {
        // One window already closed, one opening in two days.
        val windows = listOf(window(-DAY, -DAY + HOURS_4), window(2 * DAY, 2 * DAY + HOURS_4))
        val (run, job) = start(windows)

        advanceTimeBy(Duration.ofHours(24).toMillis())
        runCurrent()

        assertEquals(emptyList<Long>(), run.calls)
        job.cancel()
    }

    @Test
    fun `with no window ahead the loop returns`() = runTest {
        val (run, job) = start(listOf(window(-DAY, -DAY + HOURS_4)))

        runCurrent()

        assertEquals(true, job.isCompleted)
        assertEquals(emptyList<Long>(), run.calls)
    }

    @Test
    fun `a window opening later waits until it opens plus the jitter, then calls`() = runTest {
        val (run, job) = start(listOf(window(600, 600 + HOURS_4)), jitter = Duration.ofSeconds(7))

        advanceTimeBy(Duration.ofSeconds(606).toMillis())
        runCurrent()
        assertEquals(emptyList<Long>(), run.calls)

        advanceTimeBy(Duration.ofSeconds(1).toMillis())
        runCurrent()
        assertEquals(listOf(607L), run.calls)
        job.cancel()
    }

    @Test
    fun `the loop stops calling when the window closes`() = runTest {
        val (run, job) = start(listOf(window(0, 61)))

        advanceTimeBy(Duration.ofHours(5).toMillis())
        runCurrent()

        assertEquals(listOf(0L, 30L, 60L), run.calls)
        assertEquals(true, job.isCompleted)
    }

    @Test
    fun `entering the window with data 10 s old waits 20 s before the first call`() = runTest {
        val (run, job) = start(listOf(window(0, HOURS_4)), lastFetchedAt = base.minusSeconds(10))

        advanceTimeBy(Duration.ofSeconds(19).toMillis())
        runCurrent()
        assertEquals(emptyList<Long>(), run.calls)

        advanceTimeBy(Duration.ofSeconds(1).toMillis())
        runCurrent()
        assertEquals(listOf(20L), run.calls)
        job.cancel()
    }

    @Test
    fun `entering the window with data older than 30 s calls at once`() = runTest {
        val (run, job) = start(listOf(window(0, HOURS_4)), lastFetchedAt = base.minusSeconds(45))

        runCurrent()

        assertEquals(listOf(0L), run.calls)
        job.cancel()
    }

    private companion object {
        const val MILLIS = 1_000L
        const val HOURS_4 = 4 * 3_600L
        const val DAY = 24 * 3_600L
    }
}
