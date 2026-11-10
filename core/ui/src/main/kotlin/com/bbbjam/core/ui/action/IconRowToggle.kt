package com.bbbjam.core.ui.action

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * A 48dp icon-only switch in a row of compact admin actions (`song-played-state`): the same target as
 * [IconRowAction] with `Role.Switch` and `toggleable` semantics, so a screen reader announces [stateDescription]
 * ("Tocado" / "Sin tocar") with [contentDescription] ("Marcar como tocado"). [checkedTint] and [uncheckedTint]
 * are the caller's, never amber; a disabled one keeps its tint and ignores taps.
 */
@Composable
fun IconRowToggle(
    icon: IconRowActionIcon,
    checked: Boolean,
    contentDescription: String,
    stateDescription: String,
    checkedTint: Color,
    uncheckedTint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    val target = LocalMinimumInteractiveComponentSize.current
    Box(
        modifier = modifier
            .sizeIn(minWidth = target, minHeight = target)
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
            }
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = IconRowToggleDefaults.glyph(icon, checked),
            contentDescription = null,
            tint = if (checked) checkedTint else uncheckedTint,
        )
    }
}

internal object IconRowToggleDefaults {
    /** The glyph for the toggle state: [IconRowActionIcon.imageVector] when on, the unchecked one when off. */
    fun glyph(icon: IconRowActionIcon, checked: Boolean): ImageVector =
        if (checked) icon.imageVector else icon.uncheckedImageVector
}
