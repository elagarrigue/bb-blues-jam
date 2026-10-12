package com.bbbjam.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The admin-mode background ramp (`admin-mode-highlight`, orchestrator amendment A1, 12 October
 * 2026). The user asked for the background to *stand out*, so [Background] is a clearly tinted
 * deep wine tone (hue ~346°, far from the musician palette's cool indigo hue ~223°), not merely a
 * different near-black that would read the same on a phone. [Surface]/[SurfaceRaised] are derived
 * by applying the same per-channel delta the musician palette already uses from [Background]
 * (`+9,+8,+9` and `+13,+12,+13`), so the three-step elevation ramp keeps the same relative "lift".
 *
 * These three tokens live in their own object, not as new properties of [BluesJamPalette], so
 * `BluesJamColorsTest`'s reflection over the musician objects is unaffected (A2).
 *
 * Internal on purpose, same convention as [BluesJamPalette]: a screen never picks a raw value.
 */
internal object BluesJamAdminPalette {
    val Background = Color(0xFF2A1218)
    val Surface = Color(0xFF331A21)
    val SurfaceRaised = Color(0xFF371E25)
}
