package com.bbbjam

import com.bbbjam.core.model.JamStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Compiles only if `:core:model` is visible from `:app`. This is also true through the `api` edges
 * of `:core:ui` and `:core:data`; the direct edge is `implementation(project(":core:model"))` in
 * `app/build.gradle.kts`. The `:core:ui` line went in `molecule-presenter-harness` and the
 * `:core:data` line with its marker in `catalog-repository-cache`, whose `appModule` and
 * `BluesJamApp` now use `:core:data` directly.
 */
class ModuleWiringTest {
    @Test
    fun appSeesEveryCoreModule() {
        assertEquals(JamStatus.DRAFT, JamStatus.valueOf("DRAFT"))
    }
}
