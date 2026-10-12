package com.bbbjam.feature.nextjam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import com.bbbjam.core.ui.theme.BluesJamTheme

/** "Subir" or "Bajar": a 48dp underlined text action; a disabled edge stays drawn, muted. */
@Composable
internal fun MoveAction(model: MoveActionUiModel, modifier: Modifier, onClick: () -> Unit) {
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
            textDecoration = if (model.enabled || style.underlineDisabled) TextDecoration.Underline else null,
        )
    }
}
