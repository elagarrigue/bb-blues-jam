package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bbbjam.core.ui.action.IconRowAction
import com.bbbjam.core.ui.action.IconRowActionIcon

/**
 * "Subir"/"Bajar" as an up/down arrow icon button (`admin-row-icon-actions`): a disabled edge stays
 * drawn, muted, with no click. [model.clickLabel] ("subir un lugar"/"bajar un lugar") is the icon's
 * only accessible string; [model.label] ("Subir"/"Bajar") stays in the `UiModel` for any test or
 * copy that still names the action, unused by this composable. [icon] is chosen by the caller
 * ([IconRowActionIcon.MoveUp]/[IconRowActionIcon.MoveDown]), since the direction is not part of the
 * `UiModel`.
 */
@Composable
internal fun MoveAction(model: MoveActionUiModel, icon: IconRowActionIcon, modifier: Modifier, onClick: () -> Unit) {
    val style = AdminControlsDefaults.move()
    IconRowAction(
        icon = icon,
        contentDescription = model.clickLabel,
        tint = style.action,
        mutedTint = style.disabled,
        modifier = modifier,
        enabled = model.enabled,
        onClick = onClick,
    )
}
