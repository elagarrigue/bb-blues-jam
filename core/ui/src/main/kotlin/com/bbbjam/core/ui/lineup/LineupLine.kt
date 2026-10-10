package com.bbbjam.core.ui.lineup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bbbjam.core.ui.strip.InstrumentChipKind
import com.bbbjam.core.ui.strip.InstrumentStripDefaults
import com.bbbjam.core.ui.theme.BluesJamTheme

/** A line's detail wraps to at most this many lines before it is cut with "…". */
private const val DETAIL_MAX_LINES = 2

/**
 * One compact line: glyph, instrument and detail, one screen-reader node with the line's
 * description. [showInstrument] is false where a heading already names the instrument (the song
 * detail's groups). The instrument text is cut with "…" at [LineupPanelDefaults.INSTRUMENT_MAX_WIDTH],
 * so a long free-text extra instrument never squeezes the name.
 */
@Composable
internal fun Line(line: LineupLineUiModel, showInstrument: Boolean = true) {
    val spacing = BluesJamTheme.spacing
    val style = LineupPanelDefaults.style(line.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.fill, BluesJamTheme.shapes.sm)
            .padding(horizontal = spacing.sm, vertical = if (line.action == null) spacing.xs else 0.dp)
            .then(
                if (line.action == null) {
                    Modifier.clearAndSetSemantics { contentDescription = line.contentDescription }
                } else {
                    Modifier.semantics { contentDescription = line.contentDescription }
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineGlyph(style)
        if (line.action == null) {
            if (showInstrument) {
                InstrumentText(
                    line,
                    style,
                    Modifier.widthIn(max = LineupPanelDefaults.INSTRUMENT_MAX_WIDTH),
                )
            }
            DetailText(line, Modifier.weight(1f))
        } else {
            // With an action the instrument sits above the detail, so a large font never breaks
            // "LIBRE" or a name letter by letter between the instrument and the action.
            Column(modifier = Modifier.weight(1f).padding(vertical = spacing.xs)) {
                if (showInstrument) InstrumentText(line, style)
                DetailText(line)
            }
        }
        line.actionLabel?.let { label ->
            val action = requireNotNull(line.action)
            TextButton(
                onClick = { action(LineupLineEvent.Activate) },
                modifier = Modifier
                    .heightIn(min = LocalMinimumInteractiveComponentSize.current)
                    .then(
                        line.actionDescription?.let { description ->
                            Modifier.semantics { contentDescription = description }
                        } ?: Modifier,
                    ),
            ) {
                Text(
                    text = label,
                    color = if (line.kind == InstrumentChipKind.OPEN_SLOT) {
                        BluesJamTheme.colors.slotOpen
                    } else {
                        BluesJamTheme.colors.text
                    },
                )
            }
        }
    }
}

@Composable
private fun InstrumentText(
    line: LineupLineUiModel,
    style: InstrumentStripDefaults.ChipStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = line.instrument,
        style = BluesJamTheme.typography.body,
        color = style.textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun DetailText(line: LineupLineUiModel, modifier: Modifier = Modifier) {
    Text(
        text = line.detail,
        style = BluesJamTheme.typography.body,
        color = LineupPanelDefaults.detailColor(line.kind),
        maxLines = DETAIL_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The strip's glyphs, sized from the body font size so they scale with the system font. */
@Composable
private fun LineGlyph(style: InstrumentStripDefaults.ChipStyle) {
    val em = with(LocalDensity.current) { BluesJamTheme.typography.body.fontSize.toDp() }
    when (style.glyph) {
        InstrumentStripDefaults.Glyph.DOT -> Box(
            modifier = Modifier
                .size(em * InstrumentStripDefaults.DOT_TO_EM)
                .background(style.glyphColor, CircleShape),
        )

        InstrumentStripDefaults.Glyph.CHECK -> Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = style.glyphColor,
            modifier = Modifier.size(em),
        )

        // An extra's "+" is the first character of its instrument text.
        InstrumentStripDefaults.Glyph.NONE -> Unit
    }
}
