package com.bbbjam.core.ui.state

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.theme.BluesJamTheme

/** Reads the animator duration scale once per entry into the composition; default 1 (motion on). */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
}

/**
 * Three dots that fade in turn while a request runs (`assistant-streaming`; DESIGN.md M1 exception).
 * Static under reduced motion, no transition created. One polite node for a screen reader: [description].
 */
@Composable
fun LoadingDots(description: String, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val elapsed = if (reduced) null else rememberDotsClock()
    val spacing = BluesJamTheme.spacing
    val color = LoadingDotsDefaults.color()
    Canvas(
        modifier = modifier
            .size(
                width = spacing.sm * LoadingDotsDefaults.DOT_COUNT + spacing.xs * (LoadingDotsDefaults.DOT_COUNT - 1),
                height = spacing.sm,
            )
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        val diameter = spacing.sm.toPx()
        val step = diameter + spacing.xs.toPx()
        repeat(LoadingDotsDefaults.DOT_COUNT) { index ->
            val alpha = elapsed?.let { loadingDotAlpha(it().toLong(), index) } ?: LoadingDotsDefaults.STATIC_ALPHA
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = diameter / 2f,
                center = Offset(index * step + diameter / 2f, diameter / 2f),
            )
        }
    }
}

/**
 * A clock in milliseconds that loops over the dots' period; read it inside the draw scope only. Shared with the
 * pending status chip (`jam-status-chip-loading`), which pulses on the same timing.
 */
@Composable
internal fun rememberDotsClock(): () -> Float {
    val transition = rememberInfiniteTransition(label = "loading-dots")
    val value = transition.animateFloat(
        initialValue = 0f,
        targetValue = LoadingDotsDefaults.PERIOD_MILLIS.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(LoadingDotsDefaults.PERIOD_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loading-dots-clock",
    )
    return { value.value }
}

@Preview
@Composable
private fun LoadingDotsPreview() = ListStatePreviewFrame { LoadingDots(description = "Cargando") }
