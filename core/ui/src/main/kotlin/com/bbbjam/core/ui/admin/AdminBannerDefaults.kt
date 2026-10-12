package com.bbbjam.core.ui.admin

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors

/**
 * [AdminBanner]'s colours, kept apart from the composable so a JVM test can check them (the
 * `…Defaults` convention every other `:core:ui` component follows). `fill` is `surfaceRaised`
 * (already visually distinct from the body `background` underneath it, reused rather than adding a
 * fourth admin token) and `content` is `text`; both are never amber (D-17 — "we are in admin mode"
 * is not one of amber's five roles).
 */
internal object AdminBannerDefaults {
    fun fill(colors: BluesJamColors = BluesJamColors): Color = colors.surfaceRaised

    fun content(colors: BluesJamColors = BluesJamColors): Color = colors.text
}
