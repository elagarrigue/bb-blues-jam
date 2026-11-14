package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.bbbjam.core.ui.theme.BluesJamColors

/** Slow red pulse, always opaque with dark text; keeps the warning readable throughout the cycle. */
internal object PresenceWarningDefaults {
    const val PULSE_MILLIS = 1400
    private const val MIN_STRENGTH = 0.7f

    fun container(progress: Float, colors: BluesJamColors = BluesJamColors): Color =
        lerp(colors.background, colors.error, MIN_STRENGTH + (1f - MIN_STRENGTH) * progress.coerceIn(0f, 1f))

    fun content(colors: BluesJamColors = BluesJamColors): Color = colors.background
}
