package com.bbbjam.core.ui.jamform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The jam form dialog: three labelled outlined fields (fecha and hora with the numeric keyboard), the
 * notes and the submit action, enabled only when [JamFormUiModel.canSubmit]. Back and a tap outside equal
 * `Cancelar`; nothing is written here, the host decides what [JamFormUiModel.Event.Submit] does (D-13).
 */
@Composable
fun JamFormDialog(model: JamFormUiModel) {
    val style = JamFormDefaults.form()
    AlertDialog(
        onDismissRequest = { model.events(JamFormUiModel.Event.Cancel) },
        containerColor = style.container,
        title = { Text(text = model.title, style = BluesJamTheme.typography.body, color = style.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
                FormField(model.date, KeyboardType.Number, style) {
                    model.events(JamFormUiModel.Event.Edit(JamFormField.DATE, it))
                }
                FormField(model.time, KeyboardType.Number, style) {
                    model.events(JamFormUiModel.Event.Edit(JamFormField.TIME, it))
                }
                FormField(model.venue, KeyboardType.Text, style) {
                    model.events(JamFormUiModel.Event.Edit(JamFormField.VENUE, it))
                }
                model.notes.forEach { Text(text = it, style = BluesJamTheme.typography.caption, color = style.note) }
            }
        },
        confirmButton = {
            TextButton(enabled = model.canSubmit, onClick = { model.events(JamFormUiModel.Event.Submit) }) {
                Text(text = model.submitLabel, color = if (model.canSubmit) style.confirm else style.disabled)
            }
        },
        dismissButton = {
            TextButton(onClick = { model.events(JamFormUiModel.Event.Cancel) }) {
                Text(text = model.cancelLabel, color = style.cancel)
            }
        },
    )
}

@Composable
private fun FormField(
    model: JamFormFieldUiModel,
    keyboard: KeyboardType,
    style: JamFormDefaults.FormStyle,
    onValueChange: (String) -> Unit,
) {
    val field = style.field
    OutlinedTextField(
        value = model.value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = BluesJamTheme.typography.body,
        label = { Text(text = model.label) },
        supportingText = (model.error ?: model.hint)?.let { text -> { Text(text = text) } },
        isError = model.error != null,
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
