package com.bbbjam

import com.bbbjam.core.data.CoreDataMarker
import com.bbbjam.core.model.JamStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Compiles only if `:core:model` and `:core:data` are visible from `:app`. For `:core:model` this is
 * also true through the `api` edges of `:core:ui` and `:core:data`, as it was with the old marker;
 * the direct edge is `implementation(project(":core:model"))` in `app/build.gradle.kts`. The
 * `:core:data` line goes with its marker; the `:core:ui` line went in `molecule-presenter-harness`.
 */
class ModuleWiringTest {
    @Test
    fun appSeesEveryCoreModule() {
        assertEquals(JamStatus.DRAFT, JamStatus.valueOf("DRAFT"))
        assertEquals(":core:data", CoreDataMarker.PATH)
    }
}
