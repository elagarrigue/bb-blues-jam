package com.bbbjam.feature.nextjam.admin

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors
import com.bbbjam.feature.nextjam.songdetail.lineupEditor

/**
 * The admin controls' colours on Próxima jam (`admin-add-song-to-setlist`, V1), apart from the
 * composables so a JVM test can check them. Every admin control here is neutral: publication moved to
 * the Jams tab (`admin-jams-tab`).
 */
internal object AdminControlsDefaults {
    data class Fill(val container: Color, val content: Color)

    data class FailureStyle(val container: Color, val title: Color, val message: Color, val action: Color)

    data class DraftStyle(val badgeFill: Color, val badgeText: Color, val note: Color)

    /** The admin's read-only status block on Próxima jam: the note and the hint are `textMuted`. */
    data class StatusStyle(val note: Color)

    /** "Agregar tema": `surfaceRaised` with `text`. */
    fun addButton(colors: BluesJamColors = BluesJamColors): Fill = Fill(colors.surfaceRaised, colors.text)

    /** A pending row: a `surface` card with `textMuted` text, so it reads as not yet real. */
    fun pendingRow(colors: BluesJamColors = BluesJamColors): Fill = Fill(colors.surface, colors.textMuted)

    /** A failure card: `surface`, the title in `error`, the message `textMuted`, "Cerrar" in `text`. */
    fun failureCard(colors: BluesJamColors = BluesJamColors): FailureStyle =
        FailureStyle(container = colors.surface, title = colors.error, message = colors.textMuted, action = colors.text)

    data class RemovalStyle(
        val action: Color,
        val prompt: Color,
        val details: Color,
        val confirm: Color,
        val cancel: Color,
        val status: Color,
    )

    /**
     * Removing a song (`admin-remove-song-from-setlist`): "Quitar de la lista" and "Quitar" in
     * `error` (a destructive action, never amber), "Cancelar" and the prompt in `text`, the details
     * and "Quitando…" in `textMuted`. Drawn on the row's `surface`.
     */
    fun removal(colors: BluesJamColors = BluesJamColors): RemovalStyle = RemovalStyle(
        action = colors.error,
        prompt = colors.text,
        details = colors.textMuted,
        confirm = colors.error,
        cancel = colors.text,
        status = colors.textMuted,
    )

    /**
     * The removal confirmation dialog (`admin-row-icon-actions`, 12 October 2026 amendment):
     * `surfaceRaised` container, title and body in `text`, the detail lines `textMuted`, "OK" in
     * `error` (a destructive action, never amber) and "Cancelar" in `text`.
     */
    data class RemovalDialogStyle(
        val container: Color,
        val title: Color,
        val details: Color,
        val confirm: Color,
        val cancel: Color,
    )

    fun removalDialog(colors: BluesJamColors = BluesJamColors): RemovalDialogStyle = RemovalDialogStyle(
        container = colors.surfaceRaised,
        title = colors.text,
        details = colors.textMuted,
        confirm = colors.error,
        cancel = colors.text,
    )

    data class KeyChangeStyle(val status: Color)

    /**
     * [underlineDisabled] is false: a disabled "Subir"/"Bajar" loses its underline as well as its
     * colour, so the state is shown by shape and not by brightness alone (`ui-polish-pass`, U3).
     */
    data class MoveStyle(
        val position: Color,
        val action: Color,
        val disabled: Color,
        val underlineDisabled: Boolean = false,
    )

    /** A lineup "−"/"+" button: [fill] and [outline] are null when the button draws none. */
    data class CountButtonStyle(val fill: Color?, val outline: Color?, val glyph: Color)

    data class LineupEditorStyle(
        val action: Color,
        val heading: Color,
        val line: Color,
        val instrument: Color,
        val count: Color,
        val button: CountButtonStyle,
        val disabledButton: CountButtonStyle,
        val blockedNote: Color,
    )

    /** A text field in an admin form, with every role set explicitly (Material's focus colour is amber). */
    data class FieldStyle(
        val container: Color,
        val text: Color,
        val label: Color,
        val unfocusedBorder: Color,
        val focusedBorder: Color,
        val cursor: Color,
    )

    /** Setting a key (`admin-set-key`): the "Guardando…" line under the title uses `textMuted`. */
    fun keyChange(colors: BluesJamColors = BluesJamColors): KeyChangeStyle = KeyChangeStyle(status = colors.textMuted)

    /** Reordering uses neutral text actions; neither enabled nor disabled states use amber. */
    fun move(colors: BluesJamColors = BluesJamColors): MoveStyle =
        MoveStyle(position = colors.textMuted, action = colors.text, disabled = colors.textMuted)

    /**
     * Lineup counts and controls stay neutral; changing a count never uses the open-slot amber. An
     * enabled button has a `surfaceRaised` fill and a hairline `border` outline (the fill alone is
     * barely distinct from the `surface` line) with a `text` glyph; a disabled one has neither fill
     * nor outline and a `textMuted` glyph (`ui-polish-pass`, U3).
     */
    fun lineupEditor(colors: BluesJamColors = BluesJamColors): LineupEditorStyle = LineupEditorStyle(
        action = colors.text,
        heading = colors.textMuted,
        line = colors.surface,
        instrument = colors.text,
        count = colors.textMuted,
        button = CountButtonStyle(fill = colors.surfaceRaised, outline = colors.border, glyph = colors.text),
        disabledButton = CountButtonStyle(fill = null, outline = null, glyph = colors.textMuted),
        blockedNote = colors.textMuted,
    )

    /**
     * The "Otros" name and instrument fields (`ui-polish-pass`, U4): the same roles as the admin
     * login's field, no amber. Focused border `text`, unfocused `border`, label `textMuted`.
     */
    fun extraField(colors: BluesJamColors = BluesJamColors): FieldStyle = FieldStyle(
        container = colors.surface,
        text = colors.text,
        label = colors.textMuted,
        unfocusedBorder = colors.border,
        focusedBorder = colors.text,
        cursor = colors.text,
    )

    /** The draft badge (`badge-draft`: `textMuted` on `surfaceRaised`) and its note (`textMuted`). */
    fun draft(colors: BluesJamColors = BluesJamColors): DraftStyle =
        DraftStyle(badgeFill = colors.surfaceRaised, badgeText = colors.textMuted, note = colors.textMuted)

    /** The status block's note and hint (the badge itself is `:core:ui`'s). */
    fun status(colors: BluesJamColors = BluesJamColors): StatusStyle = StatusStyle(note = colors.textMuted)
}
