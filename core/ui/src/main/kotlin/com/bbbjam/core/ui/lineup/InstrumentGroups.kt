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
 * The lineup grouped by instrument (`song-detail-screen`, G1): the note or the hint first, then each
 * instrument's uppercase heading and its lines (open first), then `OTROS` and the extras. A slot
 * line draws its glyph and detail only, because the heading names the instrument; an extra line
 * draws `+ saxo` and the name. Not interactive, compact lines, amber only inside `:core:ui` (the
 * open glyph and "LIBRE", DESIGN.md "Song row, expanded" rules).
 */
@Composable
fun InstrumentGroups(model: InstrumentGroupsUiModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.xs)) {
        model.noOpenSlotsNote?.let { GroupCaption(it) }
        model.hint?.let { GroupCaption(it) }
        model.groups.forEach { group ->
            GroupHeading(group.heading)
            group.lines.forEach { line -> Line(line, showInstrument = false) }
        }
        if (model.extras.isNotEmpty()) {
            GroupHeading(LineupPanelCopy.EXTRAS_HEADING)
            model.extras.forEach { line -> Line(line) }
        }
    }
}

@Composable
private fun GroupHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = BluesJamTheme.typography.caption,
        color = BluesJamTheme.colors.textMuted,
        modifier = Modifier
            .padding(top = BluesJamTheme.spacing.sm)
            .semantics { heading() },
    )
}

@Composable
private fun GroupCaption(text: String) {
    Text(text = text, style = BluesJamTheme.typography.caption, color = BluesJamTheme.colors.textMuted)
}

@Composable
private fun GroupsPreview(lineup: Lineup, extras: List<ExtraParticipant> = emptyList()) {
    BluesJamTheme {
        Surface(color = BluesJamTheme.colors.surface) {
            InstrumentGroups(lineup.toInstrumentGroups(extras), Modifier.padding(BluesJamTheme.spacing.md))
        }
    }
}

@Preview
@Composable
private fun MixedGroupsPreview() = GroupsPreview(
    Lineup(
        listOf(
            Slot(Instrument.GUITAR, "Tincho"),
            Slot(Instrument.GUITAR),
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
private fun EmptyGroupsPreview() = GroupsPreview(Lineup(emptyList()))
