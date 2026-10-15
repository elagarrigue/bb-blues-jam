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
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.theme.BluesJamTheme

/** `Nueva jam`, the result of the last creation and, while it is open, the form dialog. */
@Composable
internal fun NewJamBlock(model: NewJamUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm)) {
        NewJamButton(model.label) { model.events(NewJamUiModel.Event.Open) }
        model.progress?.let { NewJamProgressView(it, model.events) }
    }
    model.form?.let { NewJamDialog(it, model.events) }
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

/**
 * The form dialog: three labelled outlined fields (fecha and hora with the numeric keyboard), the notes
 * and `Crear la jam`, enabled only when the plan is ready. Back and a tap outside equal `Cancelar`;
 * nothing is written until `Crear la jam` calls `createJam` through the presenter (D-13).
 */
@Composable
private fun NewJamDialog(model: NewJamFormUiModel, events: EventHandler<NewJamUiModel.Event>) {
    val style = JamsAdminDefaults.form()
    AlertDialog(
        onDismissRequest = { events(NewJamUiModel.Event.Cancel) },
        containerColor = style.container,
        title = { Text(text = model.title, style = BluesJamTheme.typography.body, color = style.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
                FormField(model.dateLabel, model.date, model.dateHint, model.dateError, KeyboardType.Number, style) {
                    events(NewJamUiModel.Event.Edit(NewJamField.DATE, it))
                }
                FormField(model.timeLabel, model.time, model.timeHint, model.timeError, KeyboardType.Number, style) {
                    events(NewJamUiModel.Event.Edit(NewJamField.TIME, it))
                }
                FormField(model.venueLabel, model.venue, null, model.venueError, KeyboardType.Text, style) {
                    events(NewJamUiModel.Event.Edit(NewJamField.VENUE, it))
                }
                model.waitsNote?.let { Text(text = it, style = BluesJamTheme.typography.caption, color = style.note) }
                Text(text = model.draftNote, style = BluesJamTheme.typography.caption, color = style.note)
            }
        },
        confirmButton = {
            TextButton(enabled = model.canCreate, onClick = { events(NewJamUiModel.Event.Create) }) {
                Text(text = model.createLabel, color = if (model.canCreate) style.confirm else style.disabled)
            }
        },
        dismissButton = {
            TextButton(onClick = { events(NewJamUiModel.Event.Cancel) }) {
                Text(text = model.cancelLabel, color = style.cancel)
            }
        },
    )
}

@Composable
internal fun FormField(
    label: String,
    value: String,
    hint: String?,
    error: String?,
    keyboard: KeyboardType,
    style: JamsAdminDefaults.FormStyle,
    onValueChange: (String) -> Unit,
) {
    val field = style.field
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = BluesJamTheme.typography.body,
        label = { Text(text = label) },
        supportingText = (error ?: hint)?.let { text -> { Text(text = text) } },
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
        shape = BluesJamTheme.shapes.md,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = field.text,
            unfocusedTextColor = field.text,
            errorTextColor = field.text,
            focusedContainerColor = field.container,
            unfocusedContainerColor = field.container,
            errorContainerColor = field.container,
            cursorColor = field.cursor,
            errorCursorColor = style.error,
            focusedBorderColor = field.focusedBorder,
            unfocusedBorderColor = field.unfocusedBorder,
            errorBorderColor = style.error,
            focusedLabelColor = field.label,
            unfocusedLabelColor = field.label,
            errorLabelColor = style.error,
            focusedSupportingTextColor = style.note,
            unfocusedSupportingTextColor = style.note,
            errorSupportingTextColor = style.error,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
