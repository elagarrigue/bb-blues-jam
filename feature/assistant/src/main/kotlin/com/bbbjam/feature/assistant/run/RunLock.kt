package com.bbbjam.feature.assistant.run

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The one write the assistant may have in flight at a time, shared by [ProposalApplier] and
 * [JamCreationRunner] (`assistant-proposal-targets`, U1): whichever starts first holds it until its run ends,
 * so a jam creation and a list load never overlap. A Koin `single`; no Android type (D-02).
 */
internal class RunLock {
    private val busy = AtomicBoolean(false)

    /** True when the caller now holds the lock; false, doing nothing, while another run holds it. */
    fun tryAcquire(): Boolean = busy.compareAndSet(false, true)

    fun release() {
        busy.set(false)
    }
}
