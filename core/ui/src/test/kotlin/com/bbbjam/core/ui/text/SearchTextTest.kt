package com.bbbjam.core.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {
    @Test
    fun `accents and case are ignored`() {
        assertEquals("bateria", "Batería".searchKey())
        assertEquals("nandu", "ÑANDÚ".searchKey())
        assertEquals("the thrill is gone", "The Thrill Is Gone".searchKey())
    }
}
