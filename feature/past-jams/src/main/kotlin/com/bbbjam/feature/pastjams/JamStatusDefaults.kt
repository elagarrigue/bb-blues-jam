package com.bbbjam.feature.pastjams

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The status change controls' colours (`admin-set-jam-status`), apart from the composables so a JVM
 * test can check them. Neutral roles only: amber stays on `Publicar` and on the selected Publicada chip
 * (in `:core:ui`).
 */
internal object JamStatusDefaults {
    /** A neutral text action (`Reintentar`, `Cerrar`): underlined `text`, never amber. */
    data class ActionStyle(val action: Color)

    fun action(colors: BluesJamColors = BluesJamColors): ActionStyle = ActionStyle(action = colors.text)

    /** The unpublish dialog: `surfaceRaised`, `text` title, `textMuted` body, `error` confirm, `text` cancel. */
    data class DialogStyle(
        val container: Color,
        val title: Color,
        val body: Color,
        val confirm: Color,
        val cancel: Color,
    )

    fun dialog(colors: BluesJamColors = BluesJamColors): DialogStyle = DialogStyle(
        container = colors.surfaceRaised,
        title = colors.text,
        body = colors.textMuted,
        confirm = colors.error,
        cancel = colors.text,
    )

    /** The publish dialog: the same roles with `primaryAction` on its explicit confirm button. */
    fun publishDialog(colors: BluesJamColors = BluesJamColors): DialogStyle = DialogStyle(
        container = colors.surfaceRaised,
        title = colors.text,
        body = colors.textMuted,
        confirm = colors.primaryAction,
        cancel = colors.text,
    )
}
