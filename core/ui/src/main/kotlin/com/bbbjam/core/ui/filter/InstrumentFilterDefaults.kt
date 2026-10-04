package com.bbbjam.core.ui.filter

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The filter chip's visual rules (DESIGN.md `chip-filter`), kept apart from the composable so a JVM
 * test can check them. Amber ([BluesJamColors.activeFilter]) is read only here, inside `:core:ui`,
 * and only for a selected chip, so no feature needs `activeFilter` in its amber allowlist. Never
 * `slotOpen`: a selected filter is not an open slot.
 */
internal object InstrumentFilterDefaults {
    data class ChipStyle(val fill: Color, val content: Color)

    fun chipStyle(selected: Boolean, colors: BluesJamColors = BluesJamColors): ChipStyle = if (selected) {
        ChipStyle(fill = colors.activeFilter, content = colors.onActiveFilter)
    } else {
        ChipStyle(fill = colors.surfaceRaised, content = colors.textMuted)
    }
}
