package com.bbbjam.feature.nextjam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The admin's "Otros" editor after "Cambiar formación": "Agregar a Otros", or the open form with two
 * labelled fields inset like the rest of the row, "Agregar" (muted and not underlined until both
 * fields hold something) and "Cancelar". The extras and their "Quitar" are lines of the row's
 * lineup panel, so they are not listed again here (`ui-polish-pass`, U4).
 */
@Composable
internal fun ExtraParticipantEditor(model: ExtraParticipantEditorUiModel) {
    val spacing = BluesJamTheme.spacing
    val action = AdminControlsDefaults.lineupEditor().action
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        if (model.saving) SaveStatusLine(NextJamCopy.SAVING)
        if (!model.formVisible) {
            RowTextAction(NextJamCopy.ADD_EXTRA, action) {
                model.events(ExtraParticipantEditorUiModel.Event.Open)
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                ExtraTextField(NextJamCopy.EXTRA_NAME, model.name) {
                    model.events(ExtraParticipantEditorUiModel.Event.NameChanged(it))
                }
                ExtraTextField(NextJamCopy.EXTRA_INSTRUMENT, model.instrument) {
                    model.events(ExtraParticipantEditorUiModel.Event.InstrumentChanged(it))
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                RowTextAction(
                    NextJamCopy.SUBMIT_EXTRA,
                    action,
                    Modifier.weight(1f),
                    enabled = model.submitEnabled,
                ) {
                    model.events(ExtraParticipantEditorUiModel.Event.Submit)
                }
                RowTextAction(NextJamCopy.CANCEL, action, Modifier.weight(1f)) {
                    model.events(ExtraParticipantEditorUiModel.Event.Cancel)
                }
            }
        }
    }
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
