package com.bbbjam.feature.nextjam

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.bbbjam.core.ui.theme.BluesJamTheme

private const val EXTRA_REMOVE_ACTION_WEIGHT = 0.55f

@Composable
internal fun ExtraParticipantEditor(model: ExtraParticipantEditorUiModel) {
    val spacing = BluesJamTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        if (model.saving) SaveStatusLine(NextJamCopy.SAVING)
        model.extras.forEachIndexed { index, extra ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${extra.name} · ${extra.instrument}",
                    style = BluesJamTheme.typography.body,
                    color = BluesJamTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                RowTextAction(
                    NextJamCopy.REMOVE_EXTRA,
                    AdminControlsDefaults.removal().action,
                    Modifier.weight(EXTRA_REMOVE_ACTION_WEIGHT),
                    accessibilityLabel = extraRemoveAccessibilityLabel(extra),
                ) {
                    model.events(ExtraParticipantEditorUiModel.Event.Remove(index + 1, extra.name, extra.instrument))
                }
            }
        }
        if (!model.formVisible) {
            RowTextAction(NextJamCopy.ADD_EXTRA, AdminControlsDefaults.lineupEditor().action) {
                model.events(ExtraParticipantEditorUiModel.Event.Open)
            }
        } else {
            ExtraTextField(NextJamCopy.EXTRA_NAME, model.name) {
                model.events(ExtraParticipantEditorUiModel.Event.NameChanged(it))
            }
            ExtraTextField(NextJamCopy.EXTRA_INSTRUMENT, model.instrument) {
                model.events(ExtraParticipantEditorUiModel.Event.InstrumentChanged(it))
            }
            RowTextAction(NextJamCopy.ADD_EXTRA, AdminControlsDefaults.lineupEditor().action) {
                model.events(ExtraParticipantEditorUiModel.Event.Submit)
            }
        }
    }
}

@Composable
private fun ExtraTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    val spacing = BluesJamTheme.spacing
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = BluesJamTheme.typography.body.copy(color = BluesJamTheme.colors.text),
        cursorBrush = SolidColor(BluesJamTheme.colors.text),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = LocalMinimumInteractiveComponentSize.current)
                    .border(Dp.Hairline, BluesJamTheme.colors.textMuted, BluesJamTheme.shapes.sm)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        label,
                        style = BluesJamTheme.typography.caption,
                        color = BluesJamTheme.colors.textMuted,
                    )
                }
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
    )
}
