package com.bbbjam.feature.assistant.jam

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors
import com.bbbjam.feature.assistant.apply.ApplyDefaults

/**
 * The colours of the "NUEVA JAM" card (`assistant-create-jam`, DESIGN.md "Crear la jam"). No amber:
 * "Enviar" stays the only primary action of the tab.
 */
internal object JamDefaults {
    data class CardStyle(val fill: Color, val heading: Color, val date: Color, val detail: Color, val note: Color)

    /** A `surfaceRaised` card inside the reply card. */
    fun card(colors: BluesJamColors = BluesJamColors): CardStyle = CardStyle(
        fill = colors.surfaceRaised,
        heading = colors.textMuted,
        date = colors.text,
        detail = colors.text,
        note = colors.textMuted,
    )

    /** "Crear la jam": a secondary button; `surface` fill so it shows on the raised card. */
    fun button(enabled: Boolean, colors: BluesJamColors = BluesJamColors): ApplyDefaults.ButtonStyle = if (enabled) {
        ApplyDefaults.ButtonStyle(fill = colors.surface, content = colors.text)
    } else {
        ApplyDefaults.ButtonStyle(fill = colors.surface, content = colors.textMuted)
    }

    /** The note under a proposal whose jam was rejected: `textMuted`. */
    fun rejectionNote(colors: BluesJamColors = BluesJamColors): Color = colors.textMuted
}
