package com.bbbjam.core.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPromptTest {
    private val system = AssistantPrompt.SYSTEM

    @Test
    fun `free text dates are written in long Spanish form and never as ISO`() {
        assertTrue(system.contains("sábado 31 de octubre"))
        assertTrue(system.contains("never YYYY-MM-DD"))
    }

    @Test
    fun `the json date field stays ISO`() {
        assertTrue(system.contains("jam.date as YYYY-MM-DD"))
    }

    @Test
    fun `the earlier date rule points to the Jams tab and not to the Sheet`() {
        assertFalse(system.contains("Sheet"))
        assertTrue(system.contains("Jams tab"))
    }
}
