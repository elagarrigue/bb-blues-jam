package com.bbbjam.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The distinct hex values of the `colors` block in `DESIGN.md`, which is authoritative. `slotOpen`
 * has the same value as `primary`, so both are [Amber].
 *
 * Internal on purpose: a screen never picks a raw value, it picks a role from [BluesJamColors].
 * That is what keeps amber reserved for its five uses (D-17).
 *
 * [TextMuted], [SlotFilled], [Archive] and [Error] are derived tokens: each value appears in the
 * Stitch export's Material 3 scheme, but the role it was given here is a reading of the written
 * direction, not a measurement. `ContrastTest` re-checks them if one changes.
 */
internal object BluesJamPalette {
    val Background = Color(0xFF111318)
    val Surface = Color(0xFF1A1B21)
    val SurfaceRaised = Color(0xFF1E1F25)
    val Amber = Color(0xFFFFB300)
    val OnAmber = Color(0xFF281900)
    val Text = Color(0xFFE2E2E9)

    /** Derived: the export's `on-surface-variant`. */
    val TextMuted = Color(0xFFD6C4AC)
    val Border = Color(0xFF514532)

    /** Derived: the export's `surface-container-highest`. */
    val SlotFilled = Color(0xFF33353A)

    /** Derived: the export's `outline`. */
    val Archive = Color(0xFF9E8E78)

    /** Derived: the export's `error`. */
    val Error = Color(0xFFFFB4AB)
}
