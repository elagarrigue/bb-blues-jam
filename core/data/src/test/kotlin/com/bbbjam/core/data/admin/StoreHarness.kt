package com.bbbjam.core.data.admin

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

/**
 * An [AdminCredentialStore] over a file in [dir], built by the production factory (same corruption
 * handler). [close] cancels its scope and waits, which releases the file the way a process death
 * does, so a new harness over the same [dir] reads what a restarted app would read.
 */
internal class StoreHarness(dir: File) : AutoCloseable {
    val file = File(dir, "${AdminCredentialStore.FILE_NAME}.preferences_pb")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val store = AdminCredentialStore(AdminCredentialStore.dataStore(scope) { file })

    override fun close() {
        val job = checkNotNull(scope.coroutineContext[Job])
        scope.cancel()
        runBlocking { job.join() }
    }
}
