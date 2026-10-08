package com.bbbjam.core.data.setlist

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex

/** One repository's mutation ids and FIFO write barriers, shared by every mutation manager. */
internal class SetlistMutationQueue {
    val ids = AtomicLong(0)
    val order = Mutex()
    val writes = Mutex()
}
