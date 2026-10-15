package com.bbbjam.feature.pastjams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.bbbjam.core.ui.jamform.JamFormDialog
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.theme.BluesJamTheme

/** `Nueva jam`, the result of the last creation and, while it is open, the form dialog. */
@Composable
internal fun NewJamBlock(model: NewJamUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm)) {
        NewJamButton(model.label) { model.events(NewJamUiModel.Event.Open) }
        model.progress?.let { NewJamProgressView(it, model.events) }
    }
    model.form?.let { JamFormDialog(it) }
}

/** A neutral full-width button (`surfaceRaised`, `text`, 48dp), never amber. */
@Composable
private fun NewJamButton(label: String, onClick: () -> Unit) {
    val style = JamsAdminDefaults.newJamButton()
    Surface(color = style.fill, contentColor = style.content, shape = BluesJamTheme.shapes.md) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = BluesJamTheme.typography.body,
                color = style.content,
                modifier = Modifier.padding(horizontal = BluesJamTheme.spacing.md),
            )
        }
    }
}

@Composable
private fun NewJamProgressView(model: NewJamProgressUiModel, events: EventHandler<NewJamUiModel.Event>) {
    val note = JamsAdminDefaults.status()
    when (model) {
        is NewJamProgressUiModel.Creating -> StatusLine(model.status, note, LiveRegionMode.Polite)
        is NewJamProgressUiModel.Created -> StatusLine(model.message, note, LiveRegionMode.Polite)
        is NewJamProgressUiModel.Failed -> CreateFailureCard(model, events)
    }
}

@Composable
private fun StatusLine(text: String, color: Color, mode: LiveRegionMode) {
    Text(
        text = text,
        style = BluesJamTheme.typography.body,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .semantics { liveRegion = mode },
    )
}

@Composable
private fun CreateFailureCard(model: NewJamProgressUiModel.Failed, events: EventHandler<NewJamUiModel.Event>) {
    val spacing = BluesJamTheme.spacing
    val style = JamsAdminDefaults.failure()
    Surface(
        color = style.container,
        shape = BluesJamTheme.shapes.md,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Assertive
        },
    ) {
        Column(
            modifier = Modifier.padding(start = spacing.md, end = spacing.md, top = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(text = model.title, style = BluesJamTheme.typography.body, color = style.title)
            Text(text = model.message, style = BluesJamTheme.typography.body, color = style.message)
            Text(text = model.consequence, style = BluesJamTheme.typography.caption, color = style.message)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                model.retryLabel?.let { label ->
                    PublishTextAction(label, Modifier.weight(1f)) { events(NewJamUiModel.Event.Retry) }
                }
                PublishTextAction(model.dismissLabel, Modifier.weight(1f)) { events(NewJamUiModel.Event.Dismiss) }
            }
        }
    }
}
