package com.bbbjam.core.ui.state

import androidx.compose.ui.graphics.Color
import com.bbbjam.core.ui.theme.BluesJamColors
import kotlin.math.PI
import kotlin.math.cos

/** The loading dots' constants and colour role, kept apart so a JVM test can read them. */
internal object LoadingDotsDefaults {
    /** Dots drawn. */
    const val DOT_COUNT = 3

    /** One full fade of a dot, in milliseconds. */
    const val PERIOD_MILLIS = 1200

    /** How much later each dot starts than the one before it. */
    const val STAGGER_MILLIS = 200

    /** The faintest a dot gets while moving. */
    const val MIN_ALPHA = 0.3f

    /** Every dot's alpha when motion is reduced: no movement. */
    const val STATIC_ALPHA = 0.6f

    /** Loading is not an amber role (D-17): muted text. */
    fun color(colors: BluesJamColors = BluesJamColors): Color = colors.textMuted
}

/** The dot [index]'s alpha [elapsedMillis] after the animation started: a cosine fade, each dot staggered. */
internal fun loadingDotAlpha(elapsedMillis: Long, index: Int): Float {
    val period = LoadingDotsDefaults.PERIOD_MILLIS
    val shifted = elapsedMillis - index.toLong() * LoadingDotsDefaults.STAGGER_MILLIS
    val phase = Math.floorMod(shifted, period.toLong()).toFloat() / period
    val wave = 0.5f + 0.5f * cos(2.0 * PI * phase).toFloat()
    return LoadingDotsDefaults.MIN_ALPHA + (1f - LoadingDotsDefaults.MIN_ALPHA) * wave
}

/** The system "remove animations" setting (animator duration scale 0). */
internal fun isReducedMotion(animatorScale: Float): Boolean = animatorScale == 0f
