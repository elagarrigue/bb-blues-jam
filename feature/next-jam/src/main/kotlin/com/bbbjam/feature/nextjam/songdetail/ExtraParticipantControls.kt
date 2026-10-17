package com.bbbjam.feature.nextjam.songdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bbbjam.core.ui.lineup.LineupPanelActionRow
import com.bbbjam.core.ui.theme.BluesJamTheme
import com.bbbjam.feature.nextjam.ExtraParticipantEditorUiModel
import com.bbbjam.feature.nextjam.NextJamCopy
import com.bbbjam.feature.nextjam.admin.AdminControlsDefaults
import com.bbbjam.feature.nextjam.admin.SaveStatusLine
import com.bbbjam.feature.nextjam.admin.textFieldColors

/**
 * The admin's "+ Otro / Agregar" row at the end of the open slots. The action opens [AddExtraDialog]
 * (drawn by the screen beside the list, not here); "Guardando…" appears below while a write is
 * pending. The extras and their "Quitar" are lines of the row's lineup panel.
 */
@Composable
internal fun ExtraParticipantEditor(model: ExtraParticipantEditorUiModel) {
    val spacing = BluesJamTheme.spacing
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        LineupPanelActionRow(
            instrument = NextJamCopy.OTHER,
            actionLabel = NextJamCopy.SUBMIT_EXTRA,
            actionDescription = NextJamCopy.ADD_EXTRA,
            onAction = { model.events(ExtraParticipantEditorUiModel.Event.Open) },
        )
        if (model.saving) SaveStatusLine(NextJamCopy.SAVING)
    }
}

/**
 * The "Agregar a Otros" dialog (`extras-dialog-and-lines`): two labelled fields, "Agregar" (enabled
 * only when both hold text) and "Cancelar". Back and a tap outside dismiss exactly as "Cancelar"
 * does ([AlertDialog]'s `onDismissRequest`); "Agregar" calls the same `addExtraParticipant` (D-13).
 * Colours come from [ExtraDialogDefaults.style], never amber.
 */
@Composable
internal fun AddExtraDialog(model: ExtraParticipantEditorUiModel) {
    val style = ExtraDialogDefaults.style()
    AlertDialog(
        onDismissRequest = { model.events(ExtraParticipantEditorUiModel.Event.Cancel) },
        containerColor = style.container,
        title = { Text(text = NextJamCopy.ADD_EXTRA, style = BluesJamTheme.typography.body, color = style.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
                ExtraTextField(NextJamCopy.EXTRA_NAME, model.name) {
                    model.events(ExtraParticipantEditorUiModel.Event.NameChanged(it))
                }
                ExtraTextField(NextJamCopy.EXTRA_INSTRUMENT, model.instrument) {
                    model.events(ExtraParticipantEditorUiModel.Event.InstrumentChanged(it))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = model.submitEnabled,
                onClick = { model.events(ExtraParticipantEditorUiModel.Event.Submit) },
            ) {
                Text(
                    text = NextJamCopy.SUBMIT_EXTRA,
                    color = if (model.submitEnabled) style.confirm else style.disabled,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { model.events(ExtraParticipantEditorUiModel.Event.Cancel) }) {
                Text(text = NextJamCopy.CANCEL, color = style.cancel)
            }
        },
    )
}

@Composable
private fun ExtraTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = BluesJamTheme.typography.body,
        label = { Text(text = label) },
        shape = BluesJamTheme.shapes.md,
        colors = AdminControlsDefaults.extraField().textFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}
