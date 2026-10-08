package com.bbbjam.feature.nextjam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import com.bbbjam.core.ui.theme.BluesJamTheme

@Composable
internal fun MoveControl(model: MoveUiModel, rowKey: String, onAction: (String, MoveActionUiModel) -> Unit) {
    val spacing = BluesJamTheme.spacing
    val style = AdminControlsDefaults.move()
    Column(modifier = Modifier.padding(top = spacing.xs)) {
        Text(
            text = model.positionLine,
            style = BluesJamTheme.typography.caption,
            color = style.position,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            MoveAction(model.up, Modifier.weight(1f)) { onAction(rowKey, model.up) }
            MoveAction(model.down, Modifier.weight(1f)) { onAction(rowKey, model.down) }
        }
    }
}

@Composable
private fun MoveAction(model: MoveActionUiModel, modifier: Modifier, onClick: () -> Unit) {
    val spacing = BluesJamTheme.spacing
    val style = AdminControlsDefaults.move()
    Box(
        modifier = modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(
                enabled = model.enabled,
                onClickLabel = model.clickLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(start = spacing.md, end = spacing.md, bottom = spacing.xs),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = model.label,
            style = BluesJamTheme.typography.body,
            color = if (model.enabled) style.action else style.disabled,
            textDecoration = TextDecoration.Underline,
        )
    }
}
