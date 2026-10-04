package com.bbbjam.core.ui.state

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.time.Duration

/**
 * Error (DESIGN.md "Required States"): nothing is cached and the read failed. The title, the
 * message, then the retry button, full width, at least 48dp, in the primary action's amber. Not a
 * Material `Button`: its defaults are not design decisions.
 */
@Composable
fun ListErrorBlock(model: ListErrorUiModel, modifier: Modifier = Modifier) {
    val spacing = BluesJamTheme.spacing
    val shape = BluesJamTheme.shapes.md
    val style = ListStateDefaults.retryButtonStyle()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = model.title,
            style = BluesJamTheme.typography.songTitle,
            color = BluesJamTheme.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = model.message, style = BluesJamTheme.typography.body, color = BluesJamTheme.colors.textMuted)
        Box(
            modifier = Modifier
                .padding(top = spacing.sm)
                .fillMaxWidth()
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .clip(shape)
                .background(style.fill, shape)
                .clickable(role = Role.Button) { model.events(ListErrorUiModel.Event.Retry) }
                .padding(horizontal = spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = model.retryLabel, style = BluesJamTheme.typography.body, color = style.content)
        }
    }
}

/**
 * Offline (DESIGN.md "Required States"): drawn above cached data whose latest refresh failed. The
 * data stays the content; the retry is a secondary text action, not amber. The detail is a polite
 * live region, so "Actualizando…" is announced.
 */
@Composable
fun StalenessNotice(model: StalenessNoticeUiModel, modifier: Modifier = Modifier) {
    val spacing = BluesJamTheme.spacing
    Surface(
        color = ListStateDefaults.noticeFill(),
        contentColor = BluesJamTheme.colors.text,
        shape = BluesJamTheme.shapes.md,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = model.title,
                style = BluesJamTheme.typography.body,
                color = BluesJamTheme.colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = model.detail,
                style = BluesJamTheme.typography.body,
                color = BluesJamTheme.colors.textMuted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            model.retryLabel?.let { label ->
                Box(
                    modifier = Modifier
                        .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                        .clickable(role = Role.Button) { model.events(StalenessNoticeUiModel.Event.Retry) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = label,
                        style = BluesJamTheme.typography.body,
                        color = BluesJamTheme.colors.text,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }
        }
    }
}

/** Empty (DESIGN.md "Required States"): a concrete title and an invitation. No illustration. */
@Composable
fun EmptyStateBlock(model: EmptyStateUiModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm)) {
        Text(
            text = model.title,
            style = BluesJamTheme.typography.songTitle,
            color = BluesJamTheme.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = model.message, style = BluesJamTheme.typography.body, color = BluesJamTheme.colors.textMuted)
    }
}

/** The background and padding every list state preview is drawn on. */
@Composable
internal fun ListStatePreviewFrame(content: @Composable () -> Unit) {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.background) {
            Box(modifier = Modifier.padding(BluesJamTheme.spacing.md)) { content() }
        }
    }
}

private const val PREVIEW_AGE_HOURS = 3L

@Preview
@Composable
private fun ListErrorOfflinePreview() = ListStatePreviewFrame {
    ListErrorBlock(listError("No pudimos cargar la lista", isOffline = true) {})
}

@Preview
@Composable
private fun ListErrorOtherPreview() = ListStatePreviewFrame {
    ListErrorBlock(listError("No pudimos cargar la lista", isOffline = false) {})
}

@Preview
@Composable
private fun StalenessNoticePreview() = ListStatePreviewFrame {
    StalenessNotice(stalenessNotice(isOffline = true, Duration.ofHours(PREVIEW_AGE_HOURS), isRefreshing = false) {})
}

@Preview
@Composable
private fun StalenessNoticeRefreshingPreview() = ListStatePreviewFrame {
    StalenessNotice(stalenessNotice(isOffline = true, Duration.ofHours(PREVIEW_AGE_HOURS), isRefreshing = true) {})
}

@Preview
@Composable
private fun EmptyStateBlockPreview() = ListStatePreviewFrame {
    EmptyStateBlock(EmptyStateUiModel("Todavía no hay temas", "La lista está publicada pero todavía no tiene temas."))
}
