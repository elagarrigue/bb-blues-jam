package com.bbbjam.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class JamStatusTest {

    @Test
    fun `a jam is draft, created or published, in that order`() {
        assertEquals(listOf(JamStatus.DRAFT, JamStatus.CREATED, JamStatus.PUBLISHED), JamStatus.entries)
    }
}
