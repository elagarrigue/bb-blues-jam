package com.bbbjam.core.data.setlist.engine

import com.bbbjam.core.data.DataScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    @Test
    fun `enqueue runs start before the caller suspends, with no scheduler turn`() = runTest {
        val queue = SetlistMutationQueue()
        val scope = DataScope(backgroundScope)
        var started = false

        val caller = async(start = CoroutineStart.UNDISPATCHED) {
            queue.enqueue(scope) {
                started = true
                QueuedWrite.Send { "sent" }
            }
        }

        assertTrue("start must run in the data scope from the first instruction", started)
        runCurrent()
        assertEquals("sent", caller.await())
    }

    @Test
    fun `a cancelled caller never cancels a write it started`() = runTest {
        val queue = SetlistMutationQueue()
        val scope = DataScope(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        var finished = false

        val caller = async(start = CoroutineStart.UNDISPATCHED) {
            queue.enqueue(scope) {
                QueuedWrite.Send {
                    gate.await()
                    finished = true
                }
            }
        }
        caller.cancel()
        gate.complete(Unit)
        runCurrent()

        assertTrue(finished)
    }

    @Test
    fun `an immediate answer does not wait behind an earlier write`() = runTest {
        val queue = SetlistMutationQueue()
        val scope = DataScope(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        val slow = async(start = CoroutineStart.UNDISPATCHED) {
            queue.enqueue(scope) { QueuedWrite.Send { gate.await() } }
        }
        runCurrent()

        val immediate = async(start = CoroutineStart.UNDISPATCHED) {
            queue.enqueue(scope) { QueuedWrite.Immediate("known") }
        }
        runCurrent()

        assertTrue("the immediate answer must not take the write lock", immediate.isCompleted)
        assertFalse(slow.isCompleted)
        gate.complete(Unit)
        runCurrent()
        assertTrue(slow.isCompleted)
        assertEquals("known", immediate.await())
    }
}
