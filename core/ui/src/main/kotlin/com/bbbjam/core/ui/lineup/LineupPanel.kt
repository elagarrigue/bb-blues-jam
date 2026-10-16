package com.bbbjam.core.ui.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.theme.BluesJamTheme

/**
 * The lineup panel of the song detail (DESIGN.md "Song detail"; the expanded row it was drawn in is
 * gone since `song-detail-replaces-row-expansion`): open slots first under "Cupos libres", then
 * "Cupos cubiertos", then "Otros"; a section is drawn only when it has lines. Not interactive for
 * musicians: no click, no ripple, no role. Lines are compact (no 48dp minimum, because nothing here
 * is a touch target) and each is one screen-reader node with its description. The admin may provide
 * [openSlotsFooter], drawn after the free slots and before the filled section.
 */
@Composable
fun LineupPanel(
    model: LineupPanelUiModel,
    modifier: Modifier = Modifier,
    openSlotsFooter: (@Composable () -> Unit)? = null,
) {
    val hasOpenSection = model.openSlots.isNotEmpty() || openSlotsFooter != null
    val hasCaption = model.hint != null || model.noOpenSlotsNote != null
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
        if (hasOpenSection) {
            Section(LineupPanelCopy.OPEN_HEADING, model.openSlots, separated = false)
            openSlotsFooter?.invoke()
        }
        model.hint?.let { Caption(it) }
        model.noOpenSlotsNote?.let { Caption(it) }
        if (model.filledSlots.isNotEmpty()) {
            Section(LineupPanelCopy.FILLED_HEADING, model.filledSlots, separated = hasOpenSection || hasCaption)
        }
        if (model.extras.isNotEmpty()) {
            Section(
                LineupPanelCopy.EXTRAS_HEADING,
                model.extras,
                separated = hasOpenSection || hasCaption || model.filledSlots.isNotEmpty(),
            )
        }
    }
}

/**
 * A heading and its lines. A [separated] heading (any after something already drawn) gets `sm`
 * above it, so groups sit `xs + sm` apart while lines inside a group stay `xs` apart.
 */
@Composable
private fun Section(heading: String, lines: List<LineupLineUiModel>, separated: Boolean) {
    Text(
        text = heading.uppercase(),
        style = BluesJamTheme.typography.caption,
        color = BluesJamTheme.colors.textMuted,
        modifier = Modifier
            .then(if (separated) Modifier.padding(top = BluesJamTheme.spacing.sm) else Modifier)
            .semantics { heading() },
    )
    lines.forEach { line -> Line(line) }
}

@Composable
private fun Caption(text: String) {
    Text(text = text, style = BluesJamTheme.typography.caption, color = BluesJamTheme.colors.textMuted)
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
