package com.bbbjam.core.ui.jamform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.bbbjam.core.ui.text.resolve
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The jam form dialog: date and time pickers, a labelled venue field, the
 * notes and the submit action, enabled only when [JamFormUiModel.canSubmit]. Back and a tap outside equal
 * `Cancelar`; nothing is written here, the host decides what [JamFormUiModel.Event.Submit] does (D-13).
 */
@Composable
fun JamFormDialog(model: JamFormUiModel) {
    val style = JamFormDefaults.form()
    var picker by rememberSaveable { mutableStateOf<JamFormField?>(null) }
    AlertDialog(
        onDismissRequest = { model.events(JamFormUiModel.Event.Cancel) },
        containerColor = style.container,
        title = { Text(text = model.title.resolve(), style = BluesJamTheme.typography.body, color = style.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs),
            ) {
                JamPickerField(model.date, style) {
                    picker = JamFormField.DATE
                }
                JamPickerField(model.time, style) {
                    picker = JamFormField.TIME
                }
                FormField(model.venue, KeyboardType.Text, style) {
                    model.events(JamFormUiModel.Event.Edit(JamFormField.VENUE, it))
                }
                model.notes.forEach {
                    Text(text = it.resolve(), style = BluesJamTheme.typography.caption, color = style.note)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = model.canSubmit, onClick = { model.events(JamFormUiModel.Event.Submit) }) {
                Text(text = model.submitLabel.resolve(), color = if (model.canSubmit) style.confirm else style.disabled)
            }
        },
        dismissButton = {
            TextButton(onClick = { model.events(JamFormUiModel.Event.Cancel) }) {
                Text(text = model.cancelLabel.resolve(), color = style.cancel)
            }
        },
    )
    JamFormPicker(model, picker, onDismiss = { picker = null })
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
        label = { Text(text = model.label.resolve()) },
        supportingText = (model.error ?: model.hint)?.let { text -> { Text(text = text.resolve()) } },
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
