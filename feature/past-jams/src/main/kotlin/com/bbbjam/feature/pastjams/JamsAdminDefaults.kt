package com.bbbjam.feature.pastjams

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The admin section's colours on the Jams tab (`admin-jams-tab`), apart from the composables so a JVM
 * test can check them. Neutral roles only, except `Publicar lista` (`primaryAction` / `onPrimaryAction`,
 * the module's one admin amber use besides the badge in `:core:ui`). The unpublish dialog's roles are in
 * [JamStatusDefaults].
 */
internal object JamsAdminDefaults {
    /** A future jam's card: `surface`, the date in `text`, the venue, count and notes in `textMuted`. */
    data class CardStyle(val fill: Color, val date: Color, val venue: Color, val count: Color, val note: Color)

    fun card(colors: BluesJamColors = BluesJamColors): CardStyle = CardStyle(
        fill = colors.surface,
        date = colors.text,
        venue = colors.textMuted,
        count = colors.textMuted,
        note = colors.textMuted,
    )

    /** `PRÓXIMAS` and `ANTERIORES`: `textMuted` captions. */
    fun heading(colors: BluesJamColors = BluesJamColors): Color = colors.textMuted

    /** `Publicar lista` and `Publicar`: the primary action pair. */
    data class PublishStyle(val fill: Color, val text: Color)

    fun publish(colors: BluesJamColors = BluesJamColors): PublishStyle =
        PublishStyle(fill = colors.primaryAction, text = colors.onPrimaryAction)

    /** A failure card: `surface`, the title in `error`, the message `textMuted`, actions in `text`. */
    data class FailureStyle(val container: Color, val title: Color, val message: Color, val action: Color)

    fun failure(colors: BluesJamColors = BluesJamColors): FailureStyle = FailureStyle(
        container = colors.surface,
        title = colors.error,
        message = colors.textMuted,
        action = colors.text,
    )

    /** The sending lines and the inline publish confirmation's notes: `textMuted`. */
    fun status(colors: BluesJamColors = BluesJamColors): Color = colors.textMuted

    /** `Nueva jam`: `surfaceRaised` fill with `text`, never amber. */
    data class ButtonStyle(val fill: Color, val content: Color)

    fun newJamButton(colors: BluesJamColors = BluesJamColors): ButtonStyle =
        ButtonStyle(fill = colors.surfaceRaised, content = colors.text)

    /**
     * The form dialog: `surfaceRaised`, title, `Crear la jam` and `Cancelar` in `text` (creating is not
     * destructive: never `error`, never amber), a disabled `Crear la jam` and the notes in `textMuted`,
     * field errors in `error`.
     */
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
