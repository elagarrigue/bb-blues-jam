package com.bbbjam.feature.info

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The admin login's colours, apart from the composable so a JVM test can check them. Amber
 * (`primaryAction`) only on the enabled "Entrar" button, DESIGN.md `button-primary`; the field is
 * set explicitly from tokens, because Material's defaults (amber `primary` for the focused border
 * and cursor) are not design decisions.
 */
internal object AdminLoginDefaults {
    data class ButtonStyle(val fill: Color, val content: Color)

    data class FieldStyle(
        val container: Color,
        val text: Color,
        val label: Color,
        val unfocusedBorder: Color,
        val focusedBorder: Color,
        val cursor: Color,
        val error: Color,
    )

    /** "Entrar": the primary action when enabled; `surfaceRaised`/`textMuted` when not. */
    fun submitButton(enabled: Boolean, colors: BluesJamColors = BluesJamColors): ButtonStyle = if (enabled) {
        ButtonStyle(fill = colors.primaryAction, content = colors.onPrimaryAction)
    } else {
        ButtonStyle(fill = colors.surfaceRaised, content = colors.textMuted)
    }

    /** The passphrase field: no amber anywhere. */
    fun field(colors: BluesJamColors = BluesJamColors): FieldStyle = FieldStyle(
        container = colors.surface,
        text = colors.text,
        label = colors.textMuted,
        unfocusedBorder = colors.border,
        focusedBorder = colors.text,
        cursor = colors.text,
        error = colors.error,
    )

    /** The Mostrar/Ocultar toggle: a quiet text action. */
    fun toggle(colors: BluesJamColors = BluesJamColors): Color = colors.textMuted
}
