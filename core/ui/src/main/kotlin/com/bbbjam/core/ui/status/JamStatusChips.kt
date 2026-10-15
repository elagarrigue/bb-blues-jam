package com.bbbjam.core.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.rememberDotsClock
import com.bbbjam.core.ui.state.rememberReducedMotion
import com.bbbjam.core.ui.theme.BluesJamTheme

private val GlyphSize = 18.dp

/**
 * The status chips of a jam card: one single-select row (radio semantics, at least 48dp each) that wraps at
 * large font scales and never scrolls. Each chip is one accessibility node, "Estado: Creada", whose
 * selected and disabled state comes from `selectable`. Tapping the selected chip emits nothing. It draws
 * [model] and forwards events; the presenter decides.
 */
@Composable
fun JamStatusChips(model: JamStatusChipsUiModel, modifier: Modifier = Modifier) {
    val spacing = BluesJamTheme.spacing
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        model.chips.forEach { chip ->
            StatusChip(chip) { model.events(JamStatusChipsUiModel.Event.Select(chip.status)) }
        }
    }
}

@Composable
private fun StatusChip(chip: JamStatusChipUiModel, onSelect: () -> Unit) {
    val spacing = BluesJamTheme.spacing
    val shape = BluesJamTheme.shapes.lg
    val style = JamStatusChipDefaults.style(chip.selected, chip.isPublished, chip.enabled, chip.pending)
    val reduced = rememberReducedMotion()
    // The transition exists only while the chip is pending and motion is on; it leaves with the chip.
    val clock = if (chip.pending && !reduced) rememberDotsClock() else null
    val target = LocalMinimumInteractiveComponentSize.current
    Row(
        modifier = Modifier
            .heightIn(min = target)
            .widthIn(min = target)
            .clip(shape)
            .background(style.fill, shape)
            .then(
                when {
                    chip.pending -> Modifier.pendingOutline(style, shape, reduced, clock)
                    style.outline != null -> Modifier.border(style.outlineWidth, style.outline, shape)
                    else -> Modifier
                },
            )
            .semantics {
                contentDescription = chip.description
                if (chip.pending) liveRegion = LiveRegionMode.Polite
            }
            .selectable(
                selected = chip.selected,
                enabled = chip.enabled,
                role = Role.RadioButton,
                onClick = { if (!chip.selected) onSelect() },
            )
            .padding(horizontal = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style.showGlyph) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = style.glyph,
                modifier = Modifier.size(GlyphSize).clearAndSetSemantics {},
            )
        }
        Text(
            text = chip.label,
            style = BluesJamTheme.typography.body,
            color = style.label,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** Draws the pending outline in the draw phase, so the pulse never recomposes; [clock] is null when static. */
private fun Modifier.pendingOutline(
    style: JamStatusChipDefaults.Style,
    shape: Shape,
    reduced: Boolean,
    clock: (() -> Float)?,
): Modifier = drawWithContent {
    drawContent()
    val color = style.outline ?: return@drawWithContent
    val width = style.outlineWidth.toPx()
    val alpha = pendingOutlineAlpha(clock?.invoke()?.toLong() ?: 0L, reduced)
    inset(width / 2f) {
        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            color = color.copy(alpha = alpha),
            style = Stroke(width),
        )
    }
}

@Composable
private fun ChipsPreview(current: JamStatus, enabled: Set<JamStatus>, pending: PendingChip? = null) {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.surface) {
            JamStatusChips(
                model = jamStatusChips(current, enabled, EventHandler {}, pending),
                modifier = Modifier.padding(BluesJamTheme.spacing.md),
            )
        }
    }
}

@Preview
@Composable
private fun DraftPreview() = ChipsPreview(JamStatus.DRAFT, setOf(JamStatus.CREATED, JamStatus.PUBLISHED))

@Preview
@Composable
private fun CreatedPreview() = ChipsPreview(JamStatus.CREATED, setOf(JamStatus.DRAFT, JamStatus.PUBLISHED))

@Preview
@Composable
private fun PublishedPreview() = ChipsPreview(JamStatus.PUBLISHED, setOf(JamStatus.DRAFT, JamStatus.CREATED))

@Preview
@Composable
private fun PublishedDisabledPreview() = ChipsPreview(JamStatus.DRAFT, setOf(JamStatus.CREATED))

@Preview
@Composable
private fun SendingPreview() =
    ChipsPreview(JamStatus.CREATED, emptySet(), PendingChip(JamStatus.DRAFT, "Cambiando el estado a Borrador…"))

@Preview
@Composable
private fun PublishingPreview() =
    ChipsPreview(JamStatus.CREATED, emptySet(), PendingChip(JamStatus.PUBLISHED, "Publicando la lista…"))
