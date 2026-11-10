package com.bbbjam.core.ui.action

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The glyph a feature may ask [IconRowAction] to draw. `material-icons-core` stays an
 * `implementation` dependency of `:core:ui` only (D-03): a feature names one of these values, it
 * never imports `androidx.compose.material.icons.*` itself. [uncheckedImageVector] is the off glyph of a
 * toggle ([IconRowToggle]); it equals [imageVector] for the icons that are never toggles.
 */
enum class IconRowActionIcon(
    internal val imageVector: ImageVector,
    internal val uncheckedImageVector: ImageVector = imageVector,
) {
    MoveUp(Icons.Filled.KeyboardArrowUp),
    MoveDown(Icons.Filled.KeyboardArrowDown),
    Delete(Icons.Filled.Delete),
    Edit(Icons.Filled.Edit),
    Played(Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
}
