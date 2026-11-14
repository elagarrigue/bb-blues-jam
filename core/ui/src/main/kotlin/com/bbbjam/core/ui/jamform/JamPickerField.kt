package com.bbbjam.core.ui.jamform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bbbjam.core.ui.text.resolve
import com.bbbjam.core.ui.theme.BluesJamTheme

/** A labelled button rather than a text editor: tapping anywhere opens the picker, with no keyboard. */
@Composable
internal fun JamPickerField(model: JamFormFieldUiModel, style: JamFormDefaults.FormStyle, onClick: () -> Unit) {
    Column {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = BluesJamTheme.shapes.md,
            border = BorderStroke(1.dp, if (model.error != null) style.error else style.field.unfocusedBorder),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = style.container, contentColor = style.title),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(model.label.resolve(), style = BluesJamTheme.typography.caption, color = style.note)
                Text(
                    model.value.ifBlank { model.hint?.resolve().orEmpty() },
                    style = BluesJamTheme.typography.body,
                    color = if (model.value.isBlank()) style.note else style.title,
                )
            }
        }
        model.error?.let { Text(it.resolve(), style = BluesJamTheme.typography.caption, color = style.error) }
    }
}
