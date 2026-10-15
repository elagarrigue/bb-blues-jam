package com.bbbjam.core.ui.jamform

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The colours of the jam form dialog (moved from the Jams tab's `JamsAdminDefaults.form()`): `surfaceRaised`,
 * title, submit and `Cancelar` in `text` (creating is not destructive: never `error`, never amber), a
 * disabled submit and the notes in `textMuted`, field errors in `error`.
 */
internal object JamFormDefaults {
    data class FormStyle(
        val container: Color,
        val title: Color,
        val confirm: Color,
        val disabled: Color,
        val cancel: Color,
        val note: Color,
        val error: Color,
        val field: FieldStyle,
    )

    /** A text field of the form with every role set explicitly (Material's focus colour is amber). */
    data class FieldStyle(
        val container: Color,
        val text: Color,
        val label: Color,
        val unfocusedBorder: Color,
        val focusedBorder: Color,
        val cursor: Color,
    )

    fun form(colors: BluesJamColors = BluesJamColors): FormStyle = FormStyle(
        container = colors.surfaceRaised,
        title = colors.text,
        confirm = colors.text,
        disabled = colors.textMuted,
        cancel = colors.text,
        note = colors.textMuted,
        error = colors.error,
        field = FieldStyle(
            container = colors.surfaceRaised,
            text = colors.text,
            label = colors.textMuted,
            unfocusedBorder = colors.border,
            focusedBorder = colors.text,
            cursor = colors.text,
        ),
    )
}
