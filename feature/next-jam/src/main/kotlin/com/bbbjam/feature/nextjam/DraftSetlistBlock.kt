package com.bbbjam.feature.nextjam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The draft card (`unpublished-setlist-state`, V1): a `surface` card holding the muted
 * `badge-draft` ("EN PREPARACIÓN"), the title as a heading and the message. Three text nodes in
 * that order for screen readers; nothing is clickable. Colours come only from
 * [DraftSetlistDefaults]; no amber.
 */
@Composable
internal fun DraftSetlistBlock(model: DraftSetlistUiModel, modifier: Modifier = Modifier) {
    val spacing = BluesJamTheme.spacing
    val typography = BluesJamTheme.typography
    val style = DraftSetlistDefaults.style(BluesJamTheme.colors)
    Surface(
        color = style.cardFill,
        contentColor = style.title,
        shape = BluesJamTheme.shapes.md,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Surface(color = style.badgeFill, contentColor = style.badgeText, shape = BluesJamTheme.shapes.sm) {
                Text(
                    text = model.label.uppercase(),
                    style = typography.caption,
                    color = style.badgeText,
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                )
            }
            Text(
                text = model.title,
                style = typography.songTitle,
                color = style.title,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = model.message, style = typography.body, color = style.message)
        }
    }
}
