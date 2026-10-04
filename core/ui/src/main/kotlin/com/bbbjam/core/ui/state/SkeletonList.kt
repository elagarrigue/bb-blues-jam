package com.bbbjam.core.ui.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * Loading (DESIGN.md "Required States"): a header placeholder and five row placeholders shaped like
 * collapsed song rows, never a centred spinner. Static: no shimmer or pulse (battery, and WCAG
 * 2.2.2 for moving content). A screen reader reads the whole list as one node, [description].
 */
@Composable
fun SkeletonList(description: String, modifier: Modifier = Modifier) {
    val spacing = BluesJamTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            ListStateDefaults.HEADER_BAR_FRACTIONS.forEachIndexed { index, fraction ->
                SkeletonBar(height = if (index == 0) spacing.lg else spacing.md, widthFraction = fraction)
            }
        }
        repeat(ListStateDefaults.SKELETON_ROWS) { SkeletonRow() }
    }
}

/** A collapsed song row's shape: a title bar and a strip bar, at least 64dp tall, with no dp literal. */
@Composable
private fun SkeletonRow() {
    val spacing = BluesJamTheme.spacing
    Surface(
        color = ListStateDefaults.skeletonFill().row,
        shape = BluesJamTheme.shapes.md,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            SkeletonBar(height = spacing.lg, widthFraction = ListStateDefaults.TITLE_BAR_FRACTION)
            SkeletonBar(height = spacing.md, widthFraction = ListStateDefaults.STRIP_BAR_FRACTION)
        }
    }
}

@Composable
private fun SkeletonBar(height: Dp, widthFraction: Float) {
    val shape = BluesJamTheme.shapes.sm
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(shape)
            .background(ListStateDefaults.skeletonFill().bar, shape),
    )
}

@Preview
@Composable
private fun SkeletonListPreview() = ListStatePreviewFrame { SkeletonList(description = "Cargando la lista") }
