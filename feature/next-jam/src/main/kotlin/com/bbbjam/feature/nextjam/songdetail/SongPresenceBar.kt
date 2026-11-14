package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.bbbjam.core.ui.state.rememberReducedMotion
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.resolve
import com.bbbjam.core.ui.theme.BluesJamTheme

/** Fixed below the scrollable detail. Colour changes redraw only; the warning is announced once. */
@Composable
internal fun SongPresenceBar(notice: UiText, sidePadding: PaddingValues) {
    val pulse = if (rememberReducedMotion()) null else rememberPresencePulse()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawRect(PresenceWarningDefaults.container(pulse?.invoke() ?: 1f)) }
            .padding(sidePadding)
            .padding(BluesJamTheme.spacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = notice.resolve(),
            style = BluesJamTheme.typography.body,
            color = PresenceWarningDefaults.content(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun rememberPresencePulse(): () -> Float {
    val transition = rememberInfiniteTransition(label = "presence-warning")
    val progress = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(PresenceWarningDefaults.PULSE_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "presence-warning-colour",
    )
    return { progress.value }
}
