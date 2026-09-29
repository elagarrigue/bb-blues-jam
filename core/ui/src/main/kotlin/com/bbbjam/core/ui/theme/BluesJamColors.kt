package com.bbbjam.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color roles a screen may use, read through `BluesJamTheme.colors`.
 *
 * Amber is not decoration (`DESIGN.md`). It is reachable only under the names of its five uses:
 * [primaryAction], [slotOpen], [key], [published] and [activeFilter], plus the content colors drawn
 * on top of them. Everything else is text, muted text or surface. `BluesJamColorsTest` fails if
 * another role becomes amber.
 */
object BluesJamColors {
    val background: Color = BluesJamPalette.Background
    val surface: Color = BluesJamPalette.Surface
    val surfaceRaised: Color = BluesJamPalette.SurfaceRaised
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
