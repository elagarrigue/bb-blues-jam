package com.bbbjam.feature.nextjam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import com.bbbjam.core.ui.theme.BluesJamTheme

/** Admin publication status and confirmed-only publish controls, directly below the jam header. */
@Composable
internal fun AdminStatusBlock(model: AdminStatusUiModel) {
    val spacing = BluesJamTheme.spacing
    val style = AdminControlsDefaults.status()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        val fill = if (model.isPublished) style.publishedFill else style.draftFill
        val content = if (model.isPublished) style.publishedText else style.draftText
        Surface(color = fill, contentColor = content, shape = BluesJamTheme.shapes.sm) {
            Text(
                text = model.badge.uppercase(),
                style = BluesJamTheme.typography.caption,
                color = content,
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            )
        }
        Text(text = model.note, style = BluesJamTheme.typography.caption, color = style.note)
        model.publish?.let { PublishControl(it) }
    }
}

@Composable
private fun PublishControl(model: PublishUiModel) {
    val spacing = BluesJamTheme.spacing
    val style = AdminControlsDefaults.status()
    when (model) {
        is PublishUiModel.Idle -> PrimaryPublishButton(model.label, Modifier.fillMaxWidth()) {
            model.events(PublishUiModel.Event.Request)
        }

        is PublishUiModel.Confirming -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(text = model.prompt, style = BluesJamTheme.typography.body, color = BluesJamTheme.colors.text)
            Text(text = model.details, style = BluesJamTheme.typography.caption, color = style.note)
            Text(text = model.irreversibleNote, style = BluesJamTheme.typography.caption, color = style.note)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                PrimaryPublishButton(model.confirmLabel, Modifier.weight(1f)) {
                    model.events(PublishUiModel.Event.Confirm)
                }
                PublishTextAction(model.cancelLabel, Modifier.weight(1f)) {
                    model.events(PublishUiModel.Event.Cancel)
                }
            }
        }

        is PublishUiModel.Publishing -> Text(
            text = model.status,
            style = BluesJamTheme.typography.body,
            color = style.note,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )

        is PublishUiModel.Failed -> Surface(
            color = style.failureContainer,
            shape = BluesJamTheme.shapes.md,
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Assertive
            },
        ) {
            Column(
                modifier = Modifier.padding(start = spacing.md, end = spacing.md, top = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(text = model.title, style = BluesJamTheme.typography.body, color = style.failureTitle)
                Text(text = model.message, style = BluesJamTheme.typography.body, color = style.failureMessage)
                Text(text = model.consequence, style = BluesJamTheme.typography.caption, color = style.failureMessage)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    PublishTextAction(model.retryLabel, Modifier.weight(1f)) {
                        model.events(PublishUiModel.Event.Retry)
                    }
                    PublishTextAction(model.dismissLabel, Modifier.weight(1f)) {
                        model.events(PublishUiModel.Event.Dismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryPublishButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val style = AdminControlsDefaults.status()
    Box(
        modifier = modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { },
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = style.publishFill, contentColor = style.publishText, shape = BluesJamTheme.shapes.md) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = LocalMinimumInteractiveComponentSize.current),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = BluesJamTheme.typography.body,
                    color = style.publishText,
                    modifier = Modifier.padding(horizontal = BluesJamTheme.spacing.md),
                )
            }
        }
    }
}

@Composable
private fun PublishTextAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = BluesJamTheme.typography.body,
            color = BluesJamTheme.colors.text,
            textDecoration = TextDecoration.Underline,
        )
    }
}
