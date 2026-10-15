package com.bbbjam.core.ui.status

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The badge's colours, apart from the composable so a JVM test can check them. BORRADOR and CREADA
 * are muted (`textMuted` on `surfaceRaised`); PUBLICADA is the dedicated publication amber pair
 * (`published` / `onPublished`, D-17).
 */
internal object JamStatusBadgeDefaults {
    data class Style(val fill: Color, val content: Color)

    fun style(isPublished: Boolean, colors: BluesJamColors = BluesJamColors): Style = if (isPublished) {
        Style(fill = colors.published, content = colors.onPublished)
    } else {
        Style(fill = colors.surfaceRaised, content = colors.textMuted)
    }
}
