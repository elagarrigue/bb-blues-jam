package com.bbbjam.feature.catalog

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The Temas tab's colours (`admin-catalog-tab`), apart from the composables so a JVM test can check them. The
 * module's only amber role is `key`, on the default key of each row (DESIGN.md, Temas tab): buttons, dialogs and
 * fields are neutral, as `Nueva jam`. Text fields set every role explicitly, because Material's focus colour is
 * amber.
 */
internal object CatalogDefaults {
    /** A song row: `surface`; title `text`, artist `textMuted`, key `key`; the edit icon `text`, delete `error`. */
    data class RowStyle(
        val container: Color,
        val title: Color,
        val artist: Color,
        val key: Color,
        val edit: Color,
        val delete: Color,
        val muted: Color,
    )

    fun row(colors: BluesJamColors = BluesJamColors): RowStyle = RowStyle(
        container = colors.surface,
        title = colors.text,
        artist = colors.textMuted,
        key = colors.key,
        edit = colors.text,
        delete = colors.error,
        muted = colors.textMuted,
    )

    /** `Agregar tema`: `surfaceRaised` fill with `text`, never amber. */
    data class ButtonStyle(val fill: Color, val content: Color, val disabledContent: Color)

    fun addButton(colors: BluesJamColors = BluesJamColors): ButtonStyle =
        ButtonStyle(fill = colors.surfaceRaised, content = colors.text, disabledContent = colors.textMuted)

    /** The count and the sending and result lines: `textMuted`. */
    fun caption(colors: BluesJamColors = BluesJamColors): Color = colors.textMuted

    /** A failure card: `surface`, the title in `error`, the message `textMuted`, actions in `text`. */
    data class FailureStyle(val container: Color, val title: Color, val message: Color, val action: Color)

    fun failure(colors: BluesJamColors = BluesJamColors): FailureStyle = FailureStyle(
        container = colors.surface,
        title = colors.error,
        message = colors.textMuted,
        action = colors.text,
    )

    /** An outlined text field: no amber anywhere. */
    data class FieldStyle(
        val container: Color,
        val text: Color,
        val label: Color,
        val unfocusedBorder: Color,
        val focusedBorder: Color,
        val cursor: Color,
        val error: Color,
        val note: Color,
    )

    /** The search field, on `surface`. */
    fun search(colors: BluesJamColors = BluesJamColors): FieldStyle = field(colors, colors.surface)

    /** The form's fields, on the dialog's `surfaceRaised`. */
    fun formField(colors: BluesJamColors = BluesJamColors): FieldStyle = field(colors, colors.surfaceRaised)

    private fun field(colors: BluesJamColors, container: Color) = FieldStyle(
        container = container,
        text = colors.text,
        label = colors.textMuted,
        unfocusedBorder = colors.border,
        focusedBorder = colors.text,
        cursor = colors.text,
        error = colors.error,
        note = colors.textMuted,
    )

    /**
     * The add, edit, delete and refusal dialogs: `surfaceRaised`, `text` title, `textMuted` body, `text` cancel
     * and submit (a disabled submit in `textMuted`), `error` only on the destructive confirm.
     */
    data class DialogStyle(
        val container: Color,
        val title: Color,
        val body: Color,
        val submit: Color,
        val disabled: Color,
        val destructive: Color,
        val cancel: Color,
    )

    fun dialog(colors: BluesJamColors = BluesJamColors): DialogStyle = DialogStyle(
        container = colors.surfaceRaised,
        title = colors.text,
        body = colors.textMuted,
        submit = colors.text,
        disabled = colors.textMuted,
        destructive = colors.error,
        cancel = colors.text,
    )
}
