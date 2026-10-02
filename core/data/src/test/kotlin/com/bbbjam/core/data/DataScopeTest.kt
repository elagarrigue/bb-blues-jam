package com.bbbjam.core.data

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataScopeTest {

    @Test
    fun `an exception no refresh handled reaches the reporter and the scope keeps working`() {
        val scheduler = TestCoroutineScheduler()
        val reported = mutableListOf<Throwable>()
        val scope = DataScope.create(StandardTestDispatcher(scheduler)) { reported += it }
        var ranAfter = false

        scope.launch { error("boom") }
        scheduler.advanceUntilIdle()
        scope.launch { ranAfter = true }
        scheduler.advanceUntilIdle()

        assertEquals(listOf("boom"), reported.map { it.message })
        assertTrue(reported.single() is IllegalStateException)
        assertTrue(scope.isActive)
        assertTrue(ranAfter)
    }
}
