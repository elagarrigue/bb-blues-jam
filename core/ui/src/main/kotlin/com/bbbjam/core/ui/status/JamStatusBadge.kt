package com.bbbjam.core.ui.status

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bbbjam.core.ui.theme.BluesJamTheme

/** The status badge of a jam: a small pill with the uppercase label, coloured by [JamStatusBadgeDefaults]. */
@Composable
fun JamStatusBadge(model: JamStatusBadgeUiModel, modifier: Modifier = Modifier) {
    val style = JamStatusBadgeDefaults.style(model.isPublished)
    Surface(color = style.fill, contentColor = style.content, shape = BluesJamTheme.shapes.sm, modifier = modifier) {
        Text(
            text = model.label.uppercase(),
            style = BluesJamTheme.typography.caption,
            color = style.content,
            modifier = Modifier.padding(horizontal = BluesJamTheme.spacing.sm, vertical = BluesJamTheme.spacing.xs),
        )
    }
}
