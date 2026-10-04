package com.bbbjam.core.ui.state

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * The list states' visual rules, kept apart from the composables so a JVM test can check them.
 * Amber ([BluesJamColors.primaryAction]) is read only here, inside `:core:ui`, and only for the
 * error block's retry button (DESIGN.md: amber for the primary action), so no feature needs
 * `primaryAction` in its amber allowlist. Never `slotOpen` or `activeFilter`.
 */
internal object ListStateDefaults {
    data class ButtonStyle(val fill: Color, val content: Color)

    /** A skeleton row's fill and the fill of the bars drawn on it. */
    data class SkeletonFill(val row: Color, val bar: Color)

    /** The error block's retry button: the primary action. */
    fun retryButtonStyle(colors: BluesJamColors = BluesJamColors): ButtonStyle =
        ButtonStyle(fill = colors.primaryAction, content = colors.onPrimaryAction)

    /** Static placeholders: `surfaceRaised` bars on `surface` rows, never amber. */
    fun skeletonFill(colors: BluesJamColors = BluesJamColors): SkeletonFill =
        SkeletonFill(row = colors.surface, bar = colors.surfaceRaised)

    /** The staleness notice's background. */
    fun noticeFill(colors: BluesJamColors = BluesJamColors): Color = colors.surfaceRaised

    /** Width of a skeleton row's title bar, as a fraction of the row: close to a real title. */
    const val TITLE_BAR_FRACTION = 0.6f

    /** Width of a skeleton row's strip bar. */
    const val STRIP_BAR_FRACTION = 0.9f

    /** Widths of the three header bars (date, venue, time remaining). */
    val HEADER_BAR_FRACTIONS: List<Float> = listOf(0.7f, 0.45f, 0.3f)

    /** Row placeholders drawn under the header. */
    const val SKELETON_ROWS = 5
}
