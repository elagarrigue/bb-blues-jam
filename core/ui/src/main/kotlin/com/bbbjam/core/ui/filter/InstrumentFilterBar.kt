package com.bbbjam.core.ui.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The instrument filter bar (DESIGN.md "Filter chip"): a heading, the chips wrapping onto as many
 * lines as they need (never a horizontal scroll), then the count line or the no-results block. Each
 * chip is a checkbox (`Role.Checkbox`, at least 48dp tall) with one description; its label and
 * count are not read separately. It draws [model] and forwards events; the presenter decides.
 */
@Composable
fun InstrumentFilterBar(model: InstrumentFilterBarUiModel, modifier: Modifier = Modifier) {
    val spacing = BluesJamTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = model.heading,
            style = BluesJamTheme.typography.caption,
            color = BluesJamTheme.colors.textMuted,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            model.chips.forEach { chip -> FilterChip(chip) }
        }
        model.summary?.let { summary ->
            Text(
                text = summary,
                style = BluesJamTheme.typography.body,
                color = BluesJamTheme.colors.text,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        model.noResults?.let { noResults -> NoResults(noResults, model) }
    }
}

@Composable
private fun NoResults(message: String, model: InstrumentFilterBarUiModel) {
    Text(
        text = message,
        style = BluesJamTheme.typography.body,
        color = BluesJamTheme.colors.textMuted,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Box(
        modifier = Modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(role = Role.Button) { model.events(InstrumentFilterBarUiModel.Event.Clear) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = model.clearLabel,
            style = BluesJamTheme.typography.body,
            color = BluesJamTheme.colors.text,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun FilterChip(chip: FilterChipUiModel) {
    val spacing = BluesJamTheme.spacing
    val shape = BluesJamTheme.shapes.lg
    val style = InstrumentFilterDefaults.chipStyle(chip.isSelected)
    Row(
        modifier = Modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clip(shape)
            .background(style.fill, shape)
            .semantics { contentDescription = chip.contentDescription }
            .toggleable(value = chip.isSelected, role = Role.Checkbox) {
                chip.events(FilterChipUiModel.Event.Toggle)
            }
            .padding(horizontal = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chip.label,
            style = BluesJamTheme.typography.body,
            color = style.content,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = chip.count,
            style = BluesJamTheme.typography.caption,
            color = style.content,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun BarPreview(lineups: List<Lineup>, selected: Set<Instrument> = emptySet()) {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.background) {
            InstrumentFilterBar(
                model = instrumentFilterBar(lineups, selected) {},
                modifier = Modifier.padding(BluesJamTheme.spacing.md),
            )
        }
    }
}

/** Six songs: guitar open on 1 and 4, bass on 2, vocals on 1, 3 and 5, drums and keyboards on all. */
private val previewLineups: List<Lineup> = listOf(
    previewLineup(guitar = true, vocals = true),
    previewLineup(bass = true),
    previewLineup(vocals = true),
    previewLineup(guitar = true),
    previewLineup(vocals = true),
    previewLineup(),
)

private fun previewLineup(guitar: Boolean = false, bass: Boolean = false, vocals: Boolean = false) = Lineup(
    listOf(
        Slot(Instrument.GUITAR, if (guitar) null else "Tincho"),
        Slot(Instrument.BASS, if (bass) null else "Nico"),
        Slot(Instrument.DRUMS),
        Slot(Instrument.VOCALS, if (vocals) null else "Laura"),
        Slot(Instrument.HARMONICA, "Mono"),
        Slot(Instrument.KEYBOARDS),
    ),
)

/** A real setlist's size, so the counts take two digits. */
private const val TWO_DIGIT_SETLIST = 13

@Preview
@Composable
private fun NoneSelectedPreview() = BarPreview(previewLineups)

@Preview
@Composable
private fun BassSelectedPreview() = BarPreview(previewLineups, setOf(Instrument.BASS))

@Preview
@Composable
private fun BassOrVocalsPreview() = BarPreview(previewLineups, setOf(Instrument.BASS, Instrument.VOCALS))

@Preview
@Composable
private fun HarmonicaNoResultsPreview() = BarPreview(previewLineups, setOf(Instrument.HARMONICA))

@Preview
@Composable
private fun TwoDigitCountsPreview() = BarPreview(List(TWO_DIGIT_SETLIST) { Lineup.default() })
