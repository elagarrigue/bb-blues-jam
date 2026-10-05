package com.bbbjam.core.ui.lineup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.strip.InstrumentStripDefaults
import com.bbbjam.core.ui.theme.BluesJamTheme

/** A line's detail wraps to at most this many lines before it is cut with "…". */
private const val DETAIL_MAX_LINES = 2

/**
 * The expanded lineup (DESIGN.md, song row expanded): open slots first under "Cupos libres", then
 * "Cupos cubiertos", then "Otros"; a section is drawn only when it has lines. Not interactive for
 * musicians: no click, no ripple, no role. Lines are compact (no 48dp minimum, because nothing here
 * is a touch target) and each is one screen-reader node with its description.
 */
@Composable
fun LineupPanel(model: LineupPanelUiModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
        if (model.openSlots.isNotEmpty()) {
            Section(LineupPanelCopy.OPEN_HEADING, model.openSlots)
        }
        model.hint?.let { Caption(it) }
        model.noOpenSlotsNote?.let { Caption(it) }
        if (model.filledSlots.isNotEmpty()) {
            Section(LineupPanelCopy.FILLED_HEADING, model.filledSlots)
        }
        if (model.extras.isNotEmpty()) {
            Section(LineupPanelCopy.EXTRAS_HEADING, model.extras)
        }
    }
}

@Composable
private fun Section(heading: String, lines: List<LineupLineUiModel>) {
    Text(
        text = heading.uppercase(),
        style = BluesJamTheme.typography.caption,
        color = BluesJamTheme.colors.textMuted,
        modifier = Modifier.semantics { heading() },
    )
    lines.forEach { line -> Line(line) }
}

@Composable
private fun Caption(text: String) {
    Text(text = text, style = BluesJamTheme.typography.caption, color = BluesJamTheme.colors.textMuted)
}

/**
 * One compact line: glyph, instrument and detail, one screen-reader node with the line's
 * description. [showInstrument] is false where a heading already names the instrument (the song
 * detail's groups). The instrument text is cut with "…" at [LineupPanelDefaults.INSTRUMENT_MAX_WIDTH],
 * so a long free-text extra instrument never squeezes the name.
 */
@Composable
internal fun Line(line: LineupLineUiModel, showInstrument: Boolean = true) {
    val spacing = BluesJamTheme.spacing
    val textStyle = BluesJamTheme.typography.body
    val style = LineupPanelDefaults.style(line.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.fill, BluesJamTheme.shapes.sm)
            .padding(horizontal = spacing.sm, vertical = spacing.xs)
            .clearAndSetSemantics { contentDescription = line.contentDescription },
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineGlyph(style)
        if (showInstrument) {
            Text(
                text = line.instrument,
                style = textStyle,
                color = style.textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = LineupPanelDefaults.INSTRUMENT_MAX_WIDTH),
            )
        }
        Text(
            text = line.detail,
            style = textStyle,
            color = LineupPanelDefaults.detailColor(line.kind),
            maxLines = DETAIL_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
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

@Composable
private fun PanelPreview(lineup: Lineup, extras: List<ExtraParticipant> = emptyList()) {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.surface) {
            LineupPanel(lineup.toLineupPanel(extras), Modifier.padding(BluesJamTheme.spacing.md))
        }
    }
}

@Preview
@Composable
private fun MixedWithExtraPreview() = PanelPreview(
    Lineup(
        listOf(
            Slot(Instrument.GUITAR),
            Slot(Instrument.GUITAR, "Tincho"),
            Slot(Instrument.BASS, "Nico"),
            Slot(Instrument.DRUMS),
            Slot(Instrument.VOCALS),
            Slot(Instrument.HARMONICA, "Mono"),
        ),
    ),
    extras = listOf(ExtraParticipant("Juan", "saxo")),
)

@Preview
@Composable
private fun LongExtraInstrumentPreview() = PanelPreview(
    Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"))),
    extras = listOf(ExtraParticipant("Juan", "saxofón barítono con pedal de octavador y wah")),
)

@Preview
@Composable
private fun AllOpenPreview() = PanelPreview(Lineup.default())

@Preview
@Composable
private fun AllFilledLongNamePreview() = PanelPreview(
    Lineup(
        listOf(
            Slot(Instrument.GUITAR, "Maximiliano Fernández de la Torre y Sotomayor"),
            Slot(Instrument.GUITAR, "Seba"),
            Slot(Instrument.BASS, "Nico"),
            Slot(Instrument.DRUMS, "Pato"),
            Slot(Instrument.VOCALS, "Laura"),
            Slot(Instrument.HARMONICA, "Mono"),
            Slot(Instrument.KEYBOARDS, "Caro"),
        ),
    ),
)

@Preview
@Composable
private fun EmptyLineupPreview() = PanelPreview(Lineup(emptyList()))
