package com.bbbjam.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color roles a screen may use, read through `BluesJamTheme.colors`.
 *
 * Amber is not decoration (`DESIGN.md`). It is reachable only under the names of its five uses:
 * [primaryAction], [slotOpen], [key], [published] and [activeFilter], plus the content colors drawn
 * on top of them. Everything else is text, muted text or surface. `BluesJamColorsTest` fails if
 * another role becomes amber.
 *
 * [background], [surface] and [surfaceRaised] switch to the admin ramp ([BluesJamAdminColors])
 * while [AdminModeState.isAdmin] is true (`admin-mode-highlight`): the one seam that makes the
 * admin background apply app-wide with no `if (isAdmin)` branch anywhere else. They are plain
 * (non-`@Composable`) properties on purpose — a read-site survey found ~20 `…Defaults` objects
 * across every feature module that read `colors.surface`/`.surfaceRaised` from a **non-composable**
 * function body (kept apart from the composable precisely so a plain JVM test can call them with no
 * Compose runtime), so a `@Composable get()` seam (a `CompositionLocal`) would force every one of
 * those functions, and every one of their ~20 JVM tests, to change. [AdminModeState.isAdmin] is a
 * Compose `mutableStateOf` holder instead: [BluesJamTheme] writes it from composition (so any
 * composable reading these properties still recomposes correctly through Compose's snapshot
 * system, exactly as a `CompositionLocal` would), while a plain JVM test or a non-composable
 * `…Defaults` function reads the same holder with no Compose runtime required, defaulting to
 * `false`/the musician palette exactly as before this slice. Every other role is unaffected: a
 * plain `val`, the same in both modes (D-17 — amber is not an admin-mode indicator).
 */
object BluesJamColors {
    val background: Color
        get() = if (AdminModeState.isAdmin) BluesJamAdminColors.background else BluesJamPalette.Background
    val surface: Color
        get() = if (AdminModeState.isAdmin) BluesJamAdminColors.surface else BluesJamPalette.Surface
    val surfaceRaised: Color
        get() = if (AdminModeState.isAdmin) BluesJamAdminColors.surfaceRaised else BluesJamPalette.SurfaceRaised
    val text: Color = BluesJamPalette.Text
    val textMuted: Color = BluesJamPalette.TextMuted
    val border: Color = BluesJamPalette.Border
    val slotFilled: Color = BluesJamPalette.SlotFilled
    val archive: Color = BluesJamPalette.Archive
    val error: Color = BluesJamPalette.Error

    /** The primary action (publish, add song). */
    val primaryAction: Color = BluesJamPalette.Amber
    val onPrimaryAction: Color = BluesJamPalette.OnAmber

    /** An open slot, the opportunity to play. A filled slot is [slotFilled], never amber. */
    val slotOpen: Color = BluesJamPalette.Amber

    /** The key of a song, on the background. */
    val key: Color = BluesJamPalette.Amber

    /** The published state of a setlist. */
    val published: Color = BluesJamPalette.Amber
    val onPublished: Color = BluesJamPalette.OnAmber

    /** The active filter chip. */
    val activeFilter: Color = BluesJamPalette.Amber
    val onActiveFilter: Color = BluesJamPalette.OnAmber
}
