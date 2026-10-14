package com.bbbjam.feature.assistant

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The colours of loading a proposal (`assistant-apply-proposal`, DESIGN.md "Asistente"). No amber:
 * "Enviar" stays the only primary action of the tab.
 */
internal object ApplyDefaults {
    data class ButtonStyle(val fill: Color, val content: Color)

    /** The confirming dialog: `surfaceRaised` container, `text` actions (it adds, it does not destroy). */
    data class ApplyDialogStyle(
        val container: Color,
        val title: Color,
        val lines: Color,
        val confirm: Color,
        val cancel: Color,
    )

    fun applyDialog(colors: BluesJamColors = BluesJamColors): ApplyDialogStyle = ApplyDialogStyle(
        container = colors.surfaceRaised,
        title = colors.text,
        lines = colors.textMuted,
        confirm = colors.text,
        cancel = colors.text,
    )

    /** "Usar esta lista": a secondary button, never amber ("Enviar" stays the only primary action). */
    fun applyButton(enabled: Boolean, colors: BluesJamColors = BluesJamColors): ButtonStyle = if (enabled) {
        ButtonStyle(fill = colors.surfaceRaised, content = colors.text)
    } else {
        ButtonStyle(fill = colors.surface, content = colors.textMuted)
    }

    /** The blocked note is `textMuted`. */
    fun applyNote(colors: BluesJamColors = BluesJamColors): Color = colors.textMuted

    /** The run's summary: `text` while running or done, `error` when partial or cut, `textMuted` when stopped. */
    fun summary(tone: ApplyTone, colors: BluesJamColors = BluesJamColors): Color = when (tone) {
        ApplyTone.NEUTRAL, ApplyTone.SUCCESS -> colors.text
        ApplyTone.FAILURE -> colors.error
        ApplyTone.MUTED -> colors.textMuted
    }

    /** A song's status caption: `error` for a failure, `textMuted` otherwise. */
    fun status(kind: ApplyStatusKind, colors: BluesJamColors = BluesJamColors): Color = when (kind) {
        ApplyStatusKind.FAILED -> colors.error

        ApplyStatusKind.PROGRESS, ApplyStatusKind.DONE, ApplyStatusKind.SKIPPED, ApplyStatusKind.NOT_ATTEMPTED ->
            colors.textMuted
    }
}
