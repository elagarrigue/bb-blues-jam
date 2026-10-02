package com.bbbjam.core.ui.strip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The instrument strip (DESIGN.md): one labelled chip per [chips] entry, wrapping onto new lines,
 * never scrolling sideways. Not interactive: each chip is one screen-reader node with its
 * description, and the glyphs are decorative. An empty list draws nothing.
 */
@Composable
fun InstrumentStrip(chips: List<InstrumentChipUiModel>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return
    val spacing = BluesJamTheme.spacing
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        chips.forEach { chip -> InstrumentChip(chip) }
    }
}

@Composable
private fun InstrumentChip(chip: InstrumentChipUiModel) {
    val spacing = BluesJamTheme.spacing
    val textStyle = BluesJamTheme.typography.caption
    val style = InstrumentStripDefaults.style(chip.kind)
    // Glyphs follow the label's font size, so they scale with the system font like the text does.
    val em = with(LocalDensity.current) { textStyle.fontSize.toDp() }
    Row(
        modifier = Modifier
            .background(style.fill, BluesJamTheme.shapes.sm)
            .padding(horizontal = spacing.sm, vertical = spacing.xs)
            .clearAndSetSemantics { contentDescription = chip.contentDescription },
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

            InstrumentStripDefaults.Glyph.NONE -> Unit
        }
        Text(
            text = chip.label,
            style = textStyle,
            color = style.textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StripPreview(lineup: Lineup, extras: List<ExtraParticipant> = emptyList()) {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.surface) {
            Column(modifier = Modifier.padding(BluesJamTheme.spacing.md)) {
                InstrumentStrip(lineup.toInstrumentChips(extras))
            }
        }
    }
}

@Preview
@Composable
private fun AllOpenPreview() = StripPreview(Lineup.default())

@Preview
@Composable
private fun AllFilledPreview() = StripPreview(
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
private fun MixedPreview() = StripPreview(mixedLineup())

@Preview
@Composable
private fun MixedWithExtrasPreview() = StripPreview(
    mixedLineup(),
    extras = listOf(ExtraParticipant("Juan", "saxo"), ExtraParticipant("Ana", "Trompeta")),
)

private fun mixedLineup() = Lineup(
    listOf(
        Slot(Instrument.GUITAR),
        Slot(Instrument.GUITAR, "Tincho"),
        Slot(Instrument.BASS, "Nico"),
        Slot(Instrument.DRUMS),
        Slot(Instrument.VOCALS),
        Slot(Instrument.HARMONICA, "Mono"),
    ),
)
