package com.bbbjam.core.data.setlist.engine

import com.bbbjam.core.data.DataScope
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One repository's mutation ids and FIFO write barriers, shared by every mutation manager. */
internal class SetlistMutationQueue {
    val ids = AtomicLong(0)
    val order = Mutex()
    val writes = Mutex()
}

/** What a mutation's `start` step decided under the order lock. */
internal sealed interface QueuedWrite<T> {
    /** The request to send, queued on the write lock behind earlier writes. */
    class Send<T>(val block: suspend () -> T) : QueuedWrite<T>

    /** The answer is already known: nothing is queued and the write lock is never taken. */
    class Immediate<T>(val value: T) : QueuedWrite<T>
}

/**
 * Runs one mutation in [scope] from the first instruction (undispatched), so a caller cancelled at
 * any point, even before its entry is published, never cancels a write it started: a presenter may
 * launch it and close its screen at once.
 *
 * [start] runs while holding [SetlistMutationQueue.order] with a fresh id, so it publishes its
 * entry in call order; the [QueuedWrite.Send] it returns runs under [SetlistMutationQueue.writes]
 * (both mutexes are fair), so writes are sent one at a time in call order. The caller only awaits.
 */
internal suspend fun <T> SetlistMutationQueue.enqueue(
    scope: DataScope,
    start: suspend (id: Long) -> QueuedWrite<T>,
): T = scope.async(start = CoroutineStart.UNDISPATCHED) {
    order.withLock {
        when (val write = start(ids.incrementAndGet())) {
            is QueuedWrite.Send -> scope.async(start = CoroutineStart.UNDISPATCHED) {
                writes.withLock { write.block() }
            }

            is QueuedWrite.Immediate -> CompletableDeferred(write.value)
        }
    }.await()
}.await()
