package com.bbbjam.feature.nextjam

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The admin controls' colours on Próxima jam (`admin-add-song-to-setlist`, V1), apart from the
 * composables so a JVM test can check them. **No amber**: this module may read only `key`
 * (`AMBER_ROLE_ALLOWLIST`), and "Agregar tema" is a quiet control added after the list, not the
 * screen's primary action. The draft badge uses the `badge-draft` tokens, as [DraftSetlistDefaults].
 */
internal object AdminControlsDefaults {
    data class Fill(val container: Color, val content: Color)

    data class FailureStyle(val container: Color, val title: Color, val message: Color, val action: Color)

    data class DraftStyle(val badgeFill: Color, val badgeText: Color, val note: Color)

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

    data class KeyChangeStyle(val action: Color, val status: Color)

    data class MoveStyle(val position: Color, val action: Color, val disabled: Color)

    data class LineupEditorStyle(
        val action: Color,
        val heading: Color,
        val line: Color,
        val instrument: Color,
        val count: Color,
        val buttonFill: Color,
        val buttonGlyph: Color,
        val disabledGlyph: Color,
        val blockedNote: Color,
    )

    /**
     * Setting a key (`admin-set-key`): "Cambiar tonalidad" in `text`, a quiet text action like "Ver
     * detalle del tema", and the "Guardando…" line under the title in `textMuted`. Never amber: the
     * only amber on the row stays the key itself.
     */
    fun keyChange(colors: BluesJamColors = BluesJamColors): KeyChangeStyle =
        KeyChangeStyle(action = colors.text, status = colors.textMuted)

    /** Reordering uses neutral text actions; neither enabled nor disabled states use amber. */
    fun move(colors: BluesJamColors = BluesJamColors): MoveStyle =
        MoveStyle(position = colors.textMuted, action = colors.text, disabled = colors.textMuted)

    /** Lineup counts and controls stay neutral; changing a count never uses the open-slot amber. */
    fun lineupEditor(colors: BluesJamColors = BluesJamColors): LineupEditorStyle = LineupEditorStyle(
        action = colors.text,
        heading = colors.textMuted,
        line = colors.surface,
        instrument = colors.text,
        count = colors.textMuted,
        buttonFill = colors.surfaceRaised,
        buttonGlyph = colors.text,
        disabledGlyph = colors.textMuted,
        blockedNote = colors.textMuted,
    )

    /** The draft badge (`badge-draft`: `textMuted` on `surfaceRaised`) and its note (`textMuted`). */
    fun draft(colors: BluesJamColors = BluesJamColors): DraftStyle =
        DraftStyle(badgeFill = colors.surfaceRaised, badgeText = colors.textMuted, note = colors.textMuted)
}
