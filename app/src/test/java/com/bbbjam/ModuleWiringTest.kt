package com.bbbjam

import com.bbbjam.core.data.CoreDataMarker
import com.bbbjam.core.model.CoreModelMarker
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Compiles only if `:app` depends on `:core:model` and `:core:data`. Each line is removed by the
 * slice that deletes the corresponding marker; the `:core:ui` line went with its marker in
 * `molecule-presenter-harness`, and `:app` uses `:core:ui` for real from `design-tokens-theme`.
 */
class ModuleWiringTest {
    @Test
    fun appSeesEveryCoreModule() {
        assertEquals(":core:model", CoreModelMarker.PATH)
        assertEquals(":core:data", CoreDataMarker.PATH)
    }
}
