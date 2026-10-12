package com.bbbjam.core.ui.action

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * A 48dp icon-only button in a row of compact admin actions (`admin-row-icon-actions`): a touch
 * target sized with [LocalMinimumInteractiveComponentSize] (no literal), drawing one [Icon] in
 * [tint], or [mutedTint] with no click when [enabled] is false. The disabled state stays drawn,
 * never hidden (`ui-polish-pass`, U3), and reports disabled to accessibility services through
 * `clickable`'s own semantics. [contentDescription] is set on the clickable `Box`, not the [Icon]
 * (which otherwise creates its own separate, unmerged semantics node — the same pattern
 * [com.bbbjam.feature.nextjam.RowTextAction] uses for its `accessibilityLabel`), so one
 * accessibility node carries both the description and the disabled state together; it lives in
 * `:core:ui` so a feature never depends on `material-icons-core` itself.
 */
@Composable
fun IconRowAction(
    icon: IconRowActionIcon,
    contentDescription: String,
    tint: Color,
    mutedTint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val target = LocalMinimumInteractiveComponentSize.current
    Box(
        modifier = modifier
            .sizeIn(minWidth = target, minHeight = target)
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon.imageVector,
            contentDescription = null,
            tint = if (enabled) tint else mutedTint,
        )
    }
}

@Preview
@Composable
private fun IconRowActionPreview() {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.background) {
            IconRowAction(
                icon = IconRowActionIcon.MoveUp,
                contentDescription = "subir un lugar",
                tint = BluesJamTheme.colors.text,
                mutedTint = BluesJamTheme.colors.textMuted,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun IconRowActionDisabledPreview() {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.background) {
            IconRowAction(
                icon = IconRowActionIcon.MoveDown,
                contentDescription = "bajar un lugar",
                tint = BluesJamTheme.colors.text,
                mutedTint = BluesJamTheme.colors.textMuted,
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun IconRowActionDeletePreview() {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.background) {
            IconRowAction(
                icon = IconRowActionIcon.Delete,
                contentDescription = "Quitar de la lista",
                tint = BluesJamTheme.colors.error,
                mutedTint = BluesJamTheme.colors.textMuted,
                onClick = {},
            )
        }
    }
}
