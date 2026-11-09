package com.bbbjam.core.ui.filter

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The musician filter's visual rules, kept apart from the composable so a JVM test can check them.
 * The chosen-name chip is a selected filter: [BluesJamColors.activeFilter], read here only, inside
 * `:core:ui`, so no feature needs it in its amber allowlist. The picker dialog is never amber.
 */
internal object MusicianFilterDefaults {
    data class ChipStyle(val fill: Color, val content: Color)

    data class DialogStyle(
        val container: Color,
        val title: Color,
        val option: Color,
        val radio: Color,
        val action: Color,
    )

    fun chipStyle(selected: Boolean, colors: BluesJamColors = BluesJamColors): ChipStyle = if (selected) {
        ChipStyle(fill = colors.activeFilter, content = colors.onActiveFilter)
    } else {
        ChipStyle(fill = colors.surfaceRaised, content = colors.textMuted)
    }

    fun dialogStyle(colors: BluesJamColors = BluesJamColors): DialogStyle = DialogStyle(
        container = colors.surfaceRaised,
        title = colors.text,
        option = colors.text,
        radio = colors.text,
        action = colors.text,
    )
}
