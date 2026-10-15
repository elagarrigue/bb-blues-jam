package com.bbbjam.feature.pastjams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.status.JamStatusChips
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The status change controls under the card's date and venue (`jam-status-chips`): the single-select chips,
 * the note's reason line when Publicada is disabled and the failure card. While a change sends there is no text:
 * the pending chip pulses its outline and announces itself (`jam-status-chip-loading`).
 */
@Composable
internal fun JamStatusActions(model: JamStatusUiModel) {
    val spacing = BluesJamTheme.spacing
    val failureStyle = JamsAdminDefaults.failure()
    val note = JamsAdminDefaults.status()
    val actionColor = JamStatusDefaults.action().action
    JamStatusChips(model.chips)
    model.publishUnavailable?.let { Text(text = it, style = BluesJamTheme.typography.caption, color = note) }
    model.failure?.let { failure ->
        Surface(
            color = failureStyle.container,
            shape = BluesJamTheme.shapes.md,
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Assertive
            },
        ) {
            Column(
                modifier = Modifier.padding(start = spacing.md, end = spacing.md, top = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(text = failure.title, style = BluesJamTheme.typography.body, color = failureStyle.title)
                Text(text = failure.message, style = BluesJamTheme.typography.body, color = failureStyle.message)
                Text(
                    text = failure.consequence,
                    style = BluesJamTheme.typography.caption,
                    color = failureStyle.message,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    failure.retryLabel?.let { label ->
                        PublishTextAction(label, color = actionColor) {
                            model.events(JamStatusUiModel.Event.Retry(failure.id))
                        }
                    }
                    PublishTextAction(failure.dismissLabel, color = actionColor) {
                        model.events(JamStatusUiModel.Event.Dismiss(failure.id))
                    }
                }
            }
        }
    }
}

/**
 * Leaving PUBLICADA: a modal [AlertDialog] (pattern of `RemovalConfirmationDialog`) with every colour
 * from [JamStatusDefaults.dialog]. Back or a tap outside equals "Cancelar"; nothing is
 * written until the confirm button calls `setJamStatus` through the presenter (D-13).
 */
@Composable
internal fun UnpublishDialog(model: StatusConfirmUiModel, events: EventHandler<JamStatusUiModel.Event>) {
    val style = JamStatusDefaults.dialog()
    AlertDialog(
        onDismissRequest = { events(JamStatusUiModel.Event.Cancel) },
        containerColor = style.container,
        title = { Text(text = model.prompt, style = BluesJamTheme.typography.body, color = style.title) },
        text = { Text(text = model.body, style = BluesJamTheme.typography.caption, color = style.body) },
        confirmButton = {
            TextButton(onClick = { events(JamStatusUiModel.Event.Confirm) }) {
                Text(text = model.confirmLabel, color = style.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = { events(JamStatusUiModel.Event.Cancel) }) {
                Text(text = model.cancelLabel, color = style.cancel)
            }
        },
    )
}
