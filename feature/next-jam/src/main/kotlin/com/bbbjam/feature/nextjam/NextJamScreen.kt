package com.bbbjam.feature.nextjam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.bbbjam.core.ui.theme.BluesJamTheme
import org.koin.compose.koinInject

/**
 * Próxima jam: the upcoming jam's date, venue and time remaining, and its songs as collapsed rows
 * (position, title, key in the amber `key` role). It renders [NextJamUiModel]; the presenter
 * decides. [contentPadding] goes inside the list, so the background runs edge to edge.
 */
@Composable
fun NextJamScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    presenter: NextJamPresenter = koinInject(),
) {
    NextJamContent(model = presenter.present(Unit), modifier = modifier, contentPadding = contentPadding)
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
        NextJamUiModel.Loading -> Box(modifier = background)

        is NextJamUiModel.NoUpcomingJam -> Box(modifier = background.padding(padding)) {
            Message(text = model.message)
        }

        is NextJamUiModel.Jam -> LazyColumn(
            modifier = background,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item(key = HEADER_KEY) { Header(model.header) }
            when (val setlist = model.setlist) {
                is SetlistUiModel.Songs -> {
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

                is SetlistUiModel.NotShown -> item(key = NOTE_KEY) { Message(text = setlist.message) }
            }
        }
    }
}

private const val HEADER_KEY = "header"
private const val NOTE_KEY = "note"

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

@Composable
private fun SongRow(row: SongRowUiModel) {
    val spacing = BluesJamTheme.spacing
    Surface(
        color = BluesJamTheme.colors.surface,
        contentColor = BluesJamTheme.colors.text,
        shape = BluesJamTheme.shapes.md,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // The key style's 44sp line plus two spacing.sm paddings makes the row at least 56dp
        // (DESIGN.md song-row) without a dp literal.
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
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
        }
    }
}

@Preview
@Composable
private fun NextJamScreenPreview() {
    val rows = listOf(
        SongRowUiModel(1, "01", "Sweet Little Angel", "B", "Tonalidad B"),
        SongRowUiModel(2, "02", "Walking Thru the Park", "A", "Tonalidad A"),
        SongRowUiModel(4, "04", "Blues Del Politico", "C", "Tonalidad C"),
        SongRowUiModel(5, "05", "The Thrill Is Gone", "Bm", "Tonalidad Bm"),
    )
    val model = NextJamUiModel.Jam(
        header = JamHeaderUiModel("Sábado 31 de octubre · 21:00", "La Macanuda", "En 29 días"),
        setlist = SetlistUiModel.Songs(rows, droppedRowsNote = "Falta 1 tema: no se pudo leer."),
    )
    BluesJamTheme { NextJamContent(model = model, modifier = Modifier, contentPadding = PaddingValues()) }
}
