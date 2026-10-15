package com.bbbjam.feature.pastjams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The edit and delete actions under a future card's status block (`admin-edit-delete-jam` C2): two neutral
 * underlined 48dp actions (never amber), one sending line instead of them while the jam is written, and
 * the failure card. Colours only from [JamStatusDefaults] and [JamsAdminDefaults].
 */
@Composable
internal fun JamManageActions(model: JamManageUiModel) {
    val spacing = BluesJamTheme.spacing
    val actionColor = JamStatusDefaults.action().action
    model.sending?.let { line ->
        Text(
            text = line,
            style = BluesJamTheme.typography.body,
            color = JamsAdminDefaults.status(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (model.editLabel != null || model.deleteLabel != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            model.editLabel?.let { label ->
                PublishTextAction(label, color = actionColor) { model.events(JamManageUiModel.Event.Edit) }
            }
            model.deleteLabel?.let { label ->
                PublishTextAction(label, color = actionColor) { model.events(JamManageUiModel.Event.Delete) }
            }
        }
    }
    model.failure?.let { failure -> JamEditFailureCard(failure, model.events) }
}

@Composable
private fun JamEditFailureCard(failure: JamEditFailureUiModel, events: EventHandler<JamManageUiModel.Event>) {
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
            Text(text = failure.title, style = BluesJamTheme.typography.body, color = style.title)
            Text(text = failure.message, style = BluesJamTheme.typography.body, color = style.message)
            Text(text = failure.consequence, style = BluesJamTheme.typography.caption, color = style.message)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                failure.retryLabel?.let { label ->
                    PublishTextAction(label, color = style.action) { events(JamManageUiModel.Event.Retry(failure.id)) }
                }
                PublishTextAction(failure.dismissLabel, color = style.action) {
                    events(JamManageUiModel.Event.Dismiss(failure.id))
                }
            }
        }
    }
}

/**
 * The edit dialog, the look of `Nueva jam` (`surfaceRaised`, labelled outlined fields, `error` field errors,
 * `textMuted` notes). Back and a tap outside equal `Cancelar`; nothing is written until `Guardar` calls
 * `updateJam` through the presenter (D-13).
 */
@Composable
internal fun EditJamDialog(model: EditJamFormUiModel) {
    val style = JamsAdminDefaults.form()
    AlertDialog(
        onDismissRequest = { model.events(EditJamFormUiModel.Event.Cancel) },
        containerColor = style.container,
        title = { Text(text = model.title, style = BluesJamTheme.typography.body, color = style.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
                FormField(model.dateLabel, model.date, model.dateHint, model.dateError, KeyboardType.Number, style) {
                    model.events(EditJamFormUiModel.Event.Edit(NewJamField.DATE, it))
                }
                FormField(model.timeLabel, model.time, model.timeHint, model.timeError, KeyboardType.Number, style) {
                    model.events(EditJamFormUiModel.Event.Edit(NewJamField.TIME, it))
                }
                FormField(model.venueLabel, model.venue, null, model.venueError, KeyboardType.Text, style) {
                    model.events(EditJamFormUiModel.Event.Edit(NewJamField.VENUE, it))
                }
                model.dateNote?.let { Text(text = it, style = BluesJamTheme.typography.caption, color = style.note) }
                model.visibilityNote?.let {
                    Text(text = it, style = BluesJamTheme.typography.caption, color = style.note)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = model.canSave, onClick = { model.events(EditJamFormUiModel.Event.Save) }) {
                Text(text = model.saveLabel, color = if (model.canSave) style.confirm else style.disabled)
            }
        },
        dismissButton = {
            TextButton(onClick = { model.events(EditJamFormUiModel.Event.Cancel) }) {
                Text(text = model.cancelLabel, color = style.cancel)
            }
        },
    )
}

/**
 * The delete confirmation: a modal [AlertDialog] like [UnpublishDialog] (`surfaceRaised`, `text` title,
 * `textMuted` body, `error` confirm, `text` cancel). Back or a tap outside equals `Cancelar`; nothing is
 * deleted until the confirm button calls `deleteJam` through the presenter (D-13).
 */
@Composable
internal fun DeleteJamDialog(model: DeleteJamUiModel) {
    val style = JamStatusDefaults.dialog()
    AlertDialog(
        onDismissRequest = { model.events(DeleteJamUiModel.Event.Cancel) },
        containerColor = style.container,
        title = { Text(text = model.title, style = BluesJamTheme.typography.body, color = style.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
                model.lines.forEach { line ->
                    Text(text = line, style = BluesJamTheme.typography.caption, color = style.body)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { model.events(DeleteJamUiModel.Event.Confirm) }) {
                Text(text = model.confirmLabel, color = style.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = { model.events(DeleteJamUiModel.Event.Cancel) }) {
                Text(text = model.cancelLabel, color = style.cancel)
            }
        },
    )
}
