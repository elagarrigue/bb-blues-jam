package com.bbbjam.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val SwatchWidth = 48.dp
private val SwatchHeight = 20.dp

/**
 * Every color role and text style of the theme, for the preview and for checking the tokens on a
 * device. Public so `MainActivity` can show it by changing a single line; no screen uses it.
 */
@Composable
fun ThemeShowcase(modifier: Modifier = Modifier) {
    val colors = BluesJamTheme.colors
    val type = BluesJamTheme.typography
    Column(
        modifier = modifier
            .background(colors.background)
            .padding(BluesJamTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs),
    ) {
        colorRoles().forEach { (name, color) -> Swatch(name, color) }
        TypeLine("h1 Próxima jam", type.h1)
        TypeLine("songTitle Stormy Monday", type.songTitle)
        Text(text = "key E7", style = type.key, color = colors.key)
        TypeLine("body Traé tu viola y cable", type.body)
        TypeLine("caption 32 anotados", type.caption)
    }
}

private fun colorRoles(): List<Pair<String, Color>> = with(BluesJamColors) {
    listOf(
        "background" to background,
        "surface" to surface,
        "surfaceRaised" to surfaceRaised,
        "text" to text,
        "textMuted" to textMuted,
        "border" to border,
        "slotFilled" to slotFilled,
        "archive" to archive,
        "error" to error,
        "primaryAction" to primaryAction,
        "onPrimaryAction" to onPrimaryAction,
        "slotOpen" to slotOpen,
        "key" to key,
        "published" to published,
        "onPublished" to onPublished,
        "activeFilter" to activeFilter,
        "onActiveFilter" to onActiveFilter,
    )
}

@Composable
private fun Swatch(name: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm),
    ) {
        Box(Modifier.size(SwatchWidth, SwatchHeight).background(color, BluesJamTheme.shapes.sm))
        Text(text = name, style = BluesJamTheme.typography.caption)
    }
}

@Composable
private fun TypeLine(text: String, style: TextStyle) {
    Text(text = text, style = style)
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun ThemeShowcasePreview() {
    BluesJamTheme { ThemeShowcase() }
}
