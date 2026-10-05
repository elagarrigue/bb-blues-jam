package com.bbbjam.feature.pastjams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.bbbjam.core.ui.nav.BackButton
import com.bbbjam.core.ui.state.EmptyStateBlock
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.time.LocalDate
import org.koin.compose.koinInject

/**
 * A past jam (`past-jam-detail`): a back button, the date with the year, the venue and the song
 * count, then the songs in position order, each with position, title, artist and key. Muted archive
 * treatment, the key the only amber. Nothing about who played, nothing tappable but the back
 * button. It renders [PastJamDetailUiModel] and forwards the back event; the presenter decides.
 * [onBack] is the caller's: `:app` binds it to navigation, which this module never sees.
 * [contentPadding] goes inside the list, so the background runs edge to edge.
 */
@Composable
fun PastJamDetailScreen(
    jamDate: LocalDate,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    presenter: PastJamDetailPresenter = koinInject(),
) {
    val model = presenter.present(PastJamDetailPresenter.Params(jamDate, onBack))
    PastJamDetailContent(model = model, modifier = modifier, contentPadding = contentPadding)
}

@Composable
internal fun PastJamDetailContent(model: PastJamDetailUiModel, modifier: Modifier, contentPadding: PaddingValues) {
    val spacing = BluesJamTheme.spacing
    val layoutDirection = LocalLayoutDirection.current
    // The back button brings its own 48dp target, so the top and start padding are the small step.
    val padding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + spacing.sm,
        top = contentPadding.calculateTopPadding() + spacing.sm,
        end = contentPadding.calculateEndPadding(layoutDirection) + spacing.md,
        bottom = contentPadding.calculateBottomPadding() + spacing.lg,
    )
    val inset = Modifier.padding(start = spacing.sm)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BluesJamTheme.colors.background)
            .semantics { if (model is PastJamDetailUiModel.Loading) contentDescription = model.description },
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = BACK_KEY) { BackButton(model.back) }
        when (model) {
            is PastJamDetailUiModel.Loading -> Unit

            is PastJamDetailUiModel.NotFound -> item(key = NOT_FOUND_KEY) { EmptyStateBlock(model.empty, inset) }

            is PastJamDetailUiModel.Jam -> {
                item(key = HEADER_KEY) { Header(model.header, inset) }
                when (val setlist = model.setlist) {
                    is PastSetlistUiModel.Songs -> {
                        items(setlist.rows, key = { it.position }) { row -> PastSongRow(row, inset) }
                        setlist.droppedRowsNote?.let { note ->
                            item(key = NOTE_KEY) {
                                Text(
                                    text = note,
                                    style = BluesJamTheme.typography.caption,
                                    color = PastJamDetailDefaults.rowStyle().droppedNote,
                                    modifier = inset,
                                )
                            }
                        }
                    }

                    is PastSetlistUiModel.NotShown -> item(key = MESSAGE_KEY) {
                        Text(
                            text = setlist.message,
                            style = BluesJamTheme.typography.body,
                            color = PastJamDetailDefaults.headerStyle().message,
                            modifier = inset,
                        )
                    }
                }
            }
        }
    }
}

private const val BACK_KEY = "back"
private const val NOT_FOUND_KEY = "notFound"
private const val HEADER_KEY = "header"
private const val NOTE_KEY = "note"
private const val MESSAGE_KEY = "message"

/** The date (a heading), then the venue and the count on one line. */
@Composable
private fun Header(header: PastJamHeaderUiModel, modifier: Modifier) {
    val spacing = BluesJamTheme.spacing
    val typography = BluesJamTheme.typography
    val style = PastJamDetailDefaults.headerStyle()
    Column(
        modifier = modifier.padding(bottom = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = header.dateLabel,
            style = typography.h1,
            color = style.date,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = header.venue,
                style = typography.body,
                color = style.venue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            header.countLabel?.let { count -> Text(text = count, style = typography.body, color = style.count) }
        }
    }
}

/**
 * One past song: one merged node read in visual order (position, title, key, artist). Not
 * clickable: no role, no ripple, no chevron. Colours only from [PastJamDetailDefaults.rowStyle].
 */
@Composable
private fun PastSongRow(row: PastSongRowUiModel, modifier: Modifier) {
    val spacing = BluesJamTheme.spacing
    val typography = BluesJamTheme.typography
    val style = PastJamDetailDefaults.rowStyle()
    Surface(
        color = style.fill,
        contentColor = style.title,
        shape = BluesJamTheme.shapes.md,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(text = row.positionLabel, style = typography.songTitle, color = style.position)
                Text(
                    text = row.title,
                    style = typography.songTitle,
                    color = style.title,
                    maxLines = TITLE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.key,
                    style = typography.key,
                    color = style.key,
                    modifier = Modifier.semantics { contentDescription = row.keyDescription },
                )
            }
            row.artist?.let { artist -> Text(text = artist, style = typography.body, color = style.artist) }
        }
    }
}

private const val TITLE_MAX_LINES = 2
