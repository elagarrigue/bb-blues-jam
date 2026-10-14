package com.bbbjam.feature.assistant

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The Asistente tab's colours, apart from the composables so a JVM test can check them (DESIGN.md
 * "Asistente"). Amber appears only on the enabled "Enviar" button (`primaryAction`) and on the
 * proposed songs' keys (`key`), the two roles this module may read (`AMBER_ROLE_ALLOWLIST`). The
 * field is set explicitly from tokens, as the admin login's is: Material's defaults are amber.
 */
internal object AssistantDefaults {
    data class ButtonStyle(val fill: Color, val content: Color)

    data class FieldStyle(
        val container: Color,
        val text: Color,
        val label: Color,
        val unfocusedBorder: Color,
        val focusedBorder: Color,
        val cursor: Color,
    )

    data class CardStyle(val fill: Color, val caption: Color, val text: Color)

    data class SongStyle(val position: Color, val title: Color, val key: Color, val artist: Color, val reason: Color)

    data class NoticeStyle(val failure: Color, val cancelled: Color, val action: Color, val caption: Color)

    data class BannerStyle(val fill: Color, val text: Color, val action: Color, val checking: Color)

    /** The service banner and the checking line: `surfaceRaised` card, `text` copy, `textMuted` line; never amber. */
    fun banner(colors: BluesJamColors = BluesJamColors): BannerStyle = BannerStyle(
        fill = colors.surfaceRaised,
        text = colors.text,
        action = colors.text,
        checking = colors.textMuted,
    )

    /** The admin's own request: `surfaceRaised`. */
    fun requestCard(colors: BluesJamColors = BluesJamColors): CardStyle =
        CardStyle(fill = colors.surfaceRaised, caption = colors.textMuted, text = colors.text)

    /** The assistant's reply: `surface`. */
    fun replyCard(colors: BluesJamColors = BluesJamColors): CardStyle =
        CardStyle(fill = colors.surface, caption = colors.textMuted, text = colors.text)

    /** One proposed song: the key is the only amber. */
    fun song(colors: BluesJamColors = BluesJamColors): SongStyle = SongStyle(
        position = colors.textMuted,
        title = colors.text,
        key = colors.key,
        artist = colors.textMuted,
        reason = colors.text,
    )

    /** Failure copy in `error`, a cancellation in `textMuted`, the text actions in `text` (not amber). */
    fun notice(colors: BluesJamColors = BluesJamColors): NoticeStyle = NoticeStyle(
        failure = colors.error,
        cancelled = colors.textMuted,
        action = colors.text,
        caption = colors.textMuted,
    )

    /** "Enviar": the primary action when enabled; `surfaceRaised`/`textMuted` when not. */
    fun sendButton(enabled: Boolean, colors: BluesJamColors = BluesJamColors): ButtonStyle = if (enabled) {
        ButtonStyle(fill = colors.primaryAction, content = colors.onPrimaryAction)
    } else {
        ButtonStyle(fill = colors.surfaceRaised, content = colors.textMuted)
    }

    /** The request field: the same colours as the admin login's field, no amber. */
    fun field(colors: BluesJamColors = BluesJamColors): FieldStyle = FieldStyle(
        container = colors.surface,
        text = colors.text,
        label = colors.textMuted,
        unfocusedBorder = colors.border,
        focusedBorder = colors.text,
        cursor = colors.text,
    )
}
