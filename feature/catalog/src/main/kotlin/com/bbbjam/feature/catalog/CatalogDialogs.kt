package com.bbbjam.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.resolve
import com.bbbjam.core.ui.theme.BluesJamTheme

/** The add and edit dialog. Back and a tap outside equal `Cancelar`; nothing is written until `Submit`. */
@Composable
internal fun SongFormDialog(model: SongFormUiModel) {
    val style = CatalogDefaults.dialog()
    AlertDialog(
        onDismissRequest = { model.events(SongFormUiModel.Event.Cancel) },
        containerColor = style.container,
        title = { Text(text = model.title.resolve(), style = BluesJamTheme.typography.body, color = style.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
                FormField(model.titleField, KeyboardCapitalization.Sentences) {
                    model.events(SongFormUiModel.Event.Edit(SongFormField.TITLE, it))
                }
                FormField(model.artistField, KeyboardCapitalization.Words) {
                    model.events(SongFormUiModel.Event.Edit(SongFormField.ARTIST, it))
                }
                FormField(model.keyField, KeyboardCapitalization.None) {
                    model.events(SongFormUiModel.Event.Edit(SongFormField.KEY, it))
                }
                model.notes.forEach {
                    Text(text = it.resolve(), style = BluesJamTheme.typography.caption, color = style.body)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = model.canSubmit, onClick = { model.events(SongFormUiModel.Event.Submit) }) {
                Text(
                    text = model.submitLabel.resolve(),
                    color = if (model.canSubmit) style.submit else style.disabled,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { model.events(SongFormUiModel.Event.Cancel) }) {
                Text(text = model.cancelLabel.resolve(), color = style.cancel)
            }
        },
    )
}

@Composable
private fun FormField(
    model: FormFieldUiModel,
    capitalization: KeyboardCapitalization,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = model.value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = BluesJamTheme.typography.body,
        label = { Text(text = model.label.resolve()) },
        supportingText = (model.error ?: model.hint)?.let { text -> { Text(text = text.resolve()) } },
        isError = model.error != null,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, autoCorrectEnabled = false),
        shape = BluesJamTheme.shapes.md,
        colors = fieldColors(CatalogDefaults.formField()),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The delete confirmation or the refusal naming the jams that list the song. */
@Composable
internal fun DeleteSongDialog(model: DeleteSongUiModel) {
    val style = CatalogDefaults.dialog()
    when (model) {
        is DeleteSongUiModel.Confirming -> DialogShell(
            style = style,
            title = model.title,
            lines = model.lines,
            onDismiss = { model.events(DeleteSongUiModel.Confirming.Event.Cancel) },
            confirm = {
                DialogButton(model.confirmLabel, style.destructive) {
                    model.events(DeleteSongUiModel.Confirming.Event.Confirm)
                }
            },
            dismiss = {
                DialogButton(model.cancelLabel, style.cancel) {
                    model.events(DeleteSongUiModel.Confirming.Event.Cancel)
                }
            },
        )

        is DeleteSongUiModel.Refused -> DialogShell(
            style = style,
            title = model.title,
            lines = model.lines,
            onDismiss = { model.events(DeleteSongUiModel.Refused.Event.Dismiss) },
            confirm = {
                DialogButton(model.okLabel, style.cancel) { model.events(DeleteSongUiModel.Refused.Event.Dismiss) }
            },
            dismiss = null,
        )
    }
}

@Composable
private fun DialogShell(
    style: CatalogDefaults.DialogStyle,
    title: UiText,
    lines: List<UiText>,
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit,
    dismiss: (@Composable () -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = style.container,
        title = { Text(text = title.resolve(), style = BluesJamTheme.typography.body, color = style.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
                lines.forEach { line ->
                    Text(text = line.resolve(), style = BluesJamTheme.typography.caption, color = style.body)
                }
            }
        },
        confirmButton = confirm,
        dismissButton = dismiss,
    )
}

@Composable
private fun DialogButton(label: UiText, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text = label.resolve(), color = color) }
}
