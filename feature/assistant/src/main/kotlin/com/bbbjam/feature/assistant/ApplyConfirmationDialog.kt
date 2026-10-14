package com.bbbjam.feature.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The confirmation before anything is written (D-24): a Material 3 [AlertDialog] with every colour
 * from [ApplyDefaults.applyDialog] (no amber, no `error`). Back, a tap outside and "Cancelar" all
 * send Cancel; only the confirm button sends Confirm, which starts the load.
 */
@Composable
internal fun ApplyConfirmationDialog(model: ConfirmationUiModel) {
    val style = ApplyDefaults.applyDialog()
    AlertDialog(
        onDismissRequest = { model.events(ConfirmationUiModel.Event.Cancel) },
        containerColor = style.container,
        title = { Text(text = model.title, style = BluesJamTheme.typography.body, color = style.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm)) {
                model.lines.forEach { line ->
                    Text(text = line, style = BluesJamTheme.typography.body, color = style.lines)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { model.events(ConfirmationUiModel.Event.Confirm) }) {
                Text(text = model.confirmLabel, color = style.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = { model.events(ConfirmationUiModel.Event.Cancel) }) {
                Text(text = model.cancelLabel, color = style.cancel)
            }
        },
    )
}
