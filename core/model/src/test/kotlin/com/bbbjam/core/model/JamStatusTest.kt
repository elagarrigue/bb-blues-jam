package com.bbbjam.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class JamStatusTest {

    @Test
    fun `a jam is only draft or published`() {
        assertEquals(listOf(JamStatus.DRAFT, JamStatus.PUBLISHED), JamStatus.entries)
    }
}
