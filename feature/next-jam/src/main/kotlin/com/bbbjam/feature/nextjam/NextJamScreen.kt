package com.bbbjam.feature.nextjam

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Slot
import com.bbbjam.core.ui.filter.InstrumentFilterBar
import com.bbbjam.core.ui.filter.instrumentFilterBar
import com.bbbjam.core.ui.lineup.ExpandIndicator
import com.bbbjam.core.ui.lineup.LineupPanel
import com.bbbjam.core.ui.lineup.toLineupPanel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateBlock
import com.bbbjam.core.ui.state.ListErrorBlock
import com.bbbjam.core.ui.state.SkeletonList
import com.bbbjam.core.ui.state.StalenessNotice
import com.bbbjam.core.ui.strip.InstrumentStrip
import com.bbbjam.core.ui.strip.toInstrumentChips
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.time.LocalDate
import org.koin.compose.koinInject

/**
 * Próxima jam: the upcoming jam's date, venue and time remaining, and its songs as rows (position,
 * title, key in the amber `key` role) with the instrument strip under each; tapping a row's header
 * expands it in place to the artist and the lineup panel. Under the header, the instrument filter
 * bar narrows the rows to songs with an open slot for the selected instruments. Loading, error,
 * empty and offline are drawn with the `:core:ui` state components (`list-states`). An expanded
 * row offers "Ver detalle del tema", which calls [onOpenSong] with the jam's date and the song's
 * position (`song-detail-screen`); `:app` binds it to navigation. It renders [NextJamUiModel] and
 * forwards events; the presenter decides. [contentPadding] goes inside the list, so the background
 * runs edge to edge.
 */
@Composable
fun NextJamScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenSong: (jamDate: LocalDate, position: Int) -> Unit = { _, _ -> },
    presenter: NextJamPresenter = koinInject(),
) {
    val model = presenter.present(NextJamPresenter.Params(onOpenSong))
    NextJamContent(model = model, modifier = modifier, contentPadding = contentPadding)
}

@Composable
internal fun NextJamContent(model: NextJamUiModel, modifier: Modifier, contentPadding: PaddingValues) {
    val spacing = BluesJamTheme.spacing
    val layoutDirection = LocalLayoutDirection.current
    val padding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + spacing.md,
        top = contentPadding.calculateTopPadding() + spacing.lg,
        end = contentPadding.calculateEndPadding(layoutDirection) + spacing.md,
        bottom = contentPadding.calculateBottomPadding() + spacing.lg,
    )
    val background = modifier
        .fillMaxSize()
        .background(BluesJamTheme.colors.background)
    when (model) {
        is NextJamUiModel.Loading -> Box(modifier = background.padding(padding)) {
            SkeletonList(description = model.description)
        }

        // At the top of the padded area, like the other messages, not vertically centred.
        is NextJamUiModel.Failed -> Box(modifier = background.padding(padding)) {
            ListErrorBlock(model.error)
        }

        is NextJamUiModel.NoUpcomingJam -> LazyColumn(
            modifier = background,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            model.staleness?.let { notice -> item(key = STALENESS_KEY) { StalenessNotice(notice) } }
            item(key = EMPTY_KEY) { EmptyStateBlock(model.empty) }
        }

        is NextJamUiModel.Jam -> LazyColumn(
            modifier = background,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // The notice sits above the header: on the offline screen the cached data is the content.
            model.staleness?.let { notice -> item(key = STALENESS_KEY) { StalenessNotice(notice) } }
            item(key = HEADER_KEY) { Header(model.header) }
            when (val setlist = model.setlist) {
                is SetlistUiModel.Songs -> {
                    setlist.filterBar?.let { bar -> item(key = FILTER_KEY) { InstrumentFilterBar(bar) } }
                    items(setlist.rows, key = { it.position }) { row -> SongRow(row) }
                    setlist.droppedRowsNote?.let { note ->
                        item(key = NOTE_KEY) {
                            Text(
                                text = note,
                                style = BluesJamTheme.typography.caption,
                                color = BluesJamTheme.colors.textMuted,
                            )
                        }
                    }
                }

                is SetlistUiModel.Empty -> item(key = EMPTY_KEY) { EmptyStateBlock(setlist.empty) }

                is SetlistUiModel.NotShown -> item(key = NOTE_KEY) { Message(text = setlist.message) }
            }
        }
    }
}

private const val HEADER_KEY = "header"
private const val NOTE_KEY = "note"
private const val FILTER_KEY = "filter"
private const val STALENESS_KEY = "staleness"
private const val EMPTY_KEY = "empty"

@Composable
private fun Header(header: JamHeaderUiModel) {
    Column {
        Text(text = header.date, style = BluesJamTheme.typography.h1, color = BluesJamTheme.colors.text)
        Text(text = header.venue, style = BluesJamTheme.typography.body, color = BluesJamTheme.colors.text)
        Text(
            text = header.timeRemaining,
            style = BluesJamTheme.typography.body,
            color = BluesJamTheme.colors.textMuted,
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(text = text, style = BluesJamTheme.typography.body, color = BluesJamTheme.colors.textMuted)
}

/**
 * One song row. Only the header toggles, so a stray tap on the panel never collapses it. The header
 * is one button node (title line, then the strip while collapsed or the artist while expanded) with
 * the row's state and action; the panel sits outside it, one node per line, so each slot is spoken
 * once in either state. `animateContentSize` grows the row downwards: the header and the rows above
 * it do not move, the rows below slide.
 */
@Composable
private fun SongRow(row: SongRowUiModel) {
    val spacing = BluesJamTheme.spacing
    Surface(
        color = BluesJamTheme.colors.surface,
        contentColor = BluesJamTheme.colors.text,
        shape = BluesJamTheme.shapes.md,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            RowHeader(row)
            if (row.isExpanded) {
                LineupPanel(
                    model = row.lineup,
                    modifier = Modifier.padding(start = spacing.md, end = spacing.md),
                )
                OpenDetailAction(row)
            }
        }
    }
}

/**
 * The expanded row's entry to the song detail: a full-width text action, underlined like the other
 * secondary text actions, `text` and never amber, at least 48dp tall. Drawn only while expanded.
 */
@Composable
private fun OpenDetailAction(row: SongRowUiModel) {
    val spacing = BluesJamTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .clickable(role = Role.Button) { row.events(SongRowUiModel.Event.OpenDetail) }
            .padding(start = spacing.md, end = spacing.md, bottom = spacing.xs),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = row.detailLabel,
            style = BluesJamTheme.typography.body,
            color = BluesJamTheme.colors.text,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun RowHeader(row: SongRowUiModel) {
    val spacing = BluesJamTheme.spacing
    // The key style's 44sp line plus two spacing.sm paddings makes the header at least 56dp
    // (DESIGN.md song-row), above the 48dp touch target, without a dp literal.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = row.toggleLabel, role = Role.Button) {
                row.events(SongRowUiModel.Event.ToggleExpanded)
            }
            .semantics { stateDescription = row.stateDescription }
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        TitleLine(row)
        if (!row.isExpanded) {
            InstrumentStrip(row.instruments)
        } else if (row.artist.isNotBlank()) {
            Text(text = row.artist, style = BluesJamTheme.typography.body, color = BluesJamTheme.colors.textMuted)
        }
    }
}

@Composable
private fun TitleLine(row: SongRowUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.md),
    ) {
        Text(
            text = row.positionLabel,
            style = BluesJamTheme.typography.songTitle,
            color = BluesJamTheme.colors.textMuted,
        )
        Text(
            text = row.title,
            style = BluesJamTheme.typography.songTitle,
            color = BluesJamTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.key,
            style = BluesJamTheme.typography.key,
            color = BluesJamTheme.colors.key,
            modifier = Modifier.semantics { contentDescription = row.keyDescription },
        )
        ExpandIndicator(expanded = row.isExpanded)
    }
}

private fun previewRow(
    position: Int,
    title: String,
    artist: String,
    key: String,
    lineup: Lineup,
    extras: List<ExtraParticipant> = emptyList(),
    isExpanded: Boolean = false,
) = SongRowUiModel(
    position = position,
    positionLabel = position.toString().padStart(2, '0'),
    title = title,
    key = key,
    keyDescription = NextJamCopy.keyDescription(key),
    instruments = lineup.toInstrumentChips(extras),
    artist = artist,
    isExpanded = isExpanded,
    stateDescription = if (isExpanded) NextJamCopy.ROW_EXPANDED else NextJamCopy.ROW_COLLAPSED,
    toggleLabel = if (isExpanded) NextJamCopy.HIDE_SLOTS else NextJamCopy.SHOW_SLOTS,
    lineup = lineup.toLineupPanel(extras),
    detailLabel = NextJamCopy.OPEN_DETAIL,
    events = EventHandler {},
)

@Preview
@Composable
private fun NextJamScreenPreview() {
    val mixed = Lineup(
        listOf(
            Slot(Instrument.GUITAR),
            Slot(Instrument.GUITAR, "Tincho"),
            Slot(Instrument.BASS, "Nico"),
            Slot(Instrument.DRUMS),
            Slot(Instrument.VOCALS),
            Slot(Instrument.HARMONICA, "Mono"),
        ),
    )
    val saxo = listOf(ExtraParticipant("Juan", "saxo"))
    val rows = listOf(
        previewRow(1, "Sweet Little Angel", "B.B. King", "B", Lineup.default()),
        previewRow(2, "Walking Thru the Park", "Muddy Waters", "A", mixed, saxo, isExpanded = true),
        previewRow(4, "Blues Del Politico", "Pappo", "C", Lineup(emptyList())),
        previewRow(5, "The Thrill Is Gone", "B.B. King", "Bm", Lineup.default(), isExpanded = true),
    )
    val lineups = listOf(Lineup.default(), mixed, Lineup(emptyList()), Lineup.default())
    val model = NextJamUiModel.Jam(
        header = JamHeaderUiModel("Sábado 31 de octubre · 21:00", "La Macanuda", "En 29 días"),
        setlist = SetlistUiModel.Songs(
            rows = rows,
            droppedRowsNote = "Falta 1 tema: no se pudo leer.",
            filterBar = instrumentFilterBar(lineups, emptySet()) {},
        ),
        staleness = null,
    )
    BluesJamTheme { NextJamContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) }
}
