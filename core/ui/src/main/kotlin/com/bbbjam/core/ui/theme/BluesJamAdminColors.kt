package com.bbbjam.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The three admin-mode roles that replace [BluesJamColors.background]/`.surface`/`.surfaceRaised`
 * while [AdminModeState.isAdmin] is true (`admin-mode-highlight`). Not amber, and not a new amber-adjacent
 * role (D-17): every other [BluesJamColors] role, including every amber one, keeps its musician
 * value in admin mode. Kept as a separate, three-property object so `BluesJamColorsTest`'s
 * reflection over [BluesJamColors]/[BluesJamPalette] does not need to reason about a second surface
 * ramp.
 */
internal object BluesJamAdminColors {
    val background: Color = BluesJamAdminPalette.Background
    val surface: Color = BluesJamAdminPalette.Surface
    val surfaceRaised: Color = BluesJamAdminPalette.SurfaceRaised
}
