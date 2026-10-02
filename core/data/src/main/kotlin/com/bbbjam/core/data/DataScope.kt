package com.bbbjam.core.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The process-wide scope background reads run in, so a refresh outlives the screen or caller that
 * started it. Bound once in `dataModule`; tests pass their own scope.
 */
internal class DataScope(scope: CoroutineScope) : CoroutineScope by scope {
    companion object {
        /**
         * The production scope: a [SupervisorJob], so one failed refresh never cancels the others, and
         * a last-resort [CoroutineExceptionHandler] that hands an exception no refresh turned into a
         * failure outcome to [report] instead of crashing the process.
         */
        fun create(dispatcher: CoroutineDispatcher = Dispatchers.IO, report: (Throwable) -> Unit): DataScope =
            DataScope(CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, e -> report(e) }))
    }
}
