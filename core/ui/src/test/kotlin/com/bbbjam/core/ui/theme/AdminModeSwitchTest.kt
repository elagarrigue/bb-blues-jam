package com.bbbjam.core.ui.theme

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The admin flag swaps only the three surface roles, and amber never moves (`admin-mode-highlight`). */
class AdminModeSwitchTest {

    @After
    fun resetToMusician() {
        AdminModeState.isAdmin = false
    }

    @Test
    fun `musician mode reads the musician palette`() {
        assertEquals(BluesJamPalette.Background, BluesJamColors.background)
        assertEquals(BluesJamPalette.Surface, BluesJamColors.surface)
        assertEquals(BluesJamPalette.SurfaceRaised, BluesJamColors.surfaceRaised)
    }

    @Test
    fun `admin mode swaps background and surfaces to the admin ramp`() {
        AdminModeState.isAdmin = true

        assertEquals(BluesJamAdminColors.background, BluesJamColors.background)
        assertEquals(BluesJamAdminColors.surface, BluesJamColors.surface)
        assertEquals(BluesJamAdminColors.surfaceRaised, BluesJamColors.surfaceRaised)
        assertEquals(BluesJamAdminColors.background, BluesJamMaterial.colorScheme.background)
        assertEquals(BluesJamPalette.Amber, BluesJamColors.key)
    }
}
