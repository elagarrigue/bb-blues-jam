package com.bbbjam.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreModelMarkerTest {
    @Test
    fun markerReportsItsModulePath() {
        assertEquals(":core:model", CoreModelMarker.PATH)
    }
}
