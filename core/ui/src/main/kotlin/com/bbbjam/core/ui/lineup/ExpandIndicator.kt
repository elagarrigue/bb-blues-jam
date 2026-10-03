package com.bbbjam.core.ui.lineup

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.theme.BluesJamTheme

/** The chevron points down when collapsed and is turned this many degrees when expanded. */
private const val EXPANDED_ROTATION_DEGREES = 180f

/**
 * The expand/collapse chevron of a row header, in `textMuted`, never amber. Decorative: the header
 * carries the state and the action for screen readers, so the icon has no description. It lives in
 * `:core:ui` because features do not depend on the icons library.
 */
@Composable
fun ExpandIndicator(expanded: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = BluesJamTheme.colors.textMuted,
        modifier = modifier.rotate(if (expanded) EXPANDED_ROTATION_DEGREES else 0f),
    )
}

@Preview
@Composable
private fun ExpandIndicatorPreview() {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.surface) {
            Row {
                ExpandIndicator(expanded = false)
                ExpandIndicator(expanded = true)
            }
        }
    }
}
