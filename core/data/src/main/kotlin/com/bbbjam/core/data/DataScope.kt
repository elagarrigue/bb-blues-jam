package com.bbbjam.core.data

import kotlinx.coroutines.CoroutineScope

/**
 * The process-wide scope background reads run in, so a refresh outlives the screen or caller that
 * started it. Bound once in `dataModule`; tests pass their own scope.
 */
internal class DataScope(scope: CoroutineScope) : CoroutineScope by scope
