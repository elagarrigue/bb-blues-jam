package com.bbbjam

import com.bbbjam.core.data.CoreDataMarker
import com.bbbjam.core.model.CoreModelMarker
import com.bbbjam.core.ui.CoreUiMarker
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Compiles only if `:app` depends on all three `:core` modules. Each line is removed by the slice
 * that deletes the corresponding marker.
 */
class ModuleWiringTest {
    @Test
    fun appSeesEveryCoreModule() {
        assertEquals(":core:model", CoreModelMarker.PATH)
        assertEquals(":core:ui", CoreUiMarker.PATH)
        assertEquals(":core:data", CoreDataMarker.PATH)
    }
}
