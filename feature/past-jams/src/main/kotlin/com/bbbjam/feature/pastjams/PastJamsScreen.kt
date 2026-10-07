package com.bbbjam.feature.pastjams

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.bbbjam.core.ui.state.EmptyStateBlock
import com.bbbjam.core.ui.state.ListErrorBlock
import com.bbbjam.core.ui.state.RefreshableContent
import com.bbbjam.core.ui.state.SkeletonList
import com.bbbjam.core.ui.state.StalenessNotice
import com.bbbjam.core.ui.state.rememberRevealingLazyListState
import com.bbbjam.core.ui.theme.BluesJamTheme
import java.time.LocalDate
import org.koin.compose.koinInject

/**
 * Anteriores: the past jams, newest first, each a muted archive row (date with the year, venue,
 * song count and the first titles). Loading, error, empty and offline are drawn with the `:core:ui`
 * state components (`list-states`); every state can be pulled to refresh
 * (`live-refresh-during-jam`). Read-only; a row with songs opens its jam (`past-jam-detail`)
 * through [onOpenJam], which `:app` binds to navigation. It renders [PastJamsUiModel] and forwards
 * the retries, the pull and the rows' Open; the presenter decides. [contentPadding] goes inside the list, so
 * the background runs edge to edge.
 */
@Composable
fun PastJamsScreen(
    onOpenJam: (jamDate: LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    presenter: PastJamsPresenter = koinInject(),
) {
    val model = presenter.present(PastJamsPresenter.Params(onOpenJam))
    PastJamsContent(model = model, modifier = modifier, contentPadding = contentPadding)
}

@Composable
internal fun PastJamsContent(model: PastJamsUiModel, modifier: Modifier, contentPadding: PaddingValues) {
    val spacing = BluesJamTheme.spacing
    val layoutDirection = LocalLayoutDirection.current
    val padding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + spacing.md,
        top = contentPadding.calculateTopPadding() + spacing.lg,
        end = contentPadding.calculateEndPadding(layoutDirection) + spacing.md,
        bottom = contentPadding.calculateBottomPadding() + spacing.lg,
    )
    // Every state is a list, so every state can be pulled (`live-refresh-during-jam`).
    RefreshableContent(
        model.pullRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(BluesJamTheme.colors.background),
    ) {
        PastJamsList(model, padding)
    }
}

@Composable
private fun PastJamsList(model: PastJamsUiModel, padding: PaddingValues) {
    // null for Loading/Failed, which never carry a notice, so the reveal effect never triggers there.
    val staleness = (model as? PastJamsUiModel.Empty)?.staleness ?: (model as? PastJamsUiModel.Jams)?.staleness
    val listState = rememberRevealingLazyListState(staleness)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(BluesJamTheme.spacing.sm),
    ) {
        item(key = TITLE_KEY) { Title(model.title) }
        when (model) {
            is PastJamsUiModel.Loading -> item(key = LOADING_KEY) { SkeletonList(description = model.description) }

            is PastJamsUiModel.Failed -> item(key = ERROR_KEY) { ListErrorBlock(model.error) }

            is PastJamsUiModel.Empty -> {
                model.staleness?.let { notice -> item(key = STALENESS_KEY) { StalenessNotice(notice) } }
                item(key = EMPTY_KEY) { EmptyStateBlock(model.empty) }
            }

            is PastJamsUiModel.Jams -> {
                // The notice sits above the rows: on the offline screen the cached data is the content.
                model.staleness?.let { notice -> item(key = STALENESS_KEY) { StalenessNotice(notice) } }
                items(model.rows, key = { it.date.toString() }) { row -> PastJamRow(row) }
            }
        }
    }
}

private const val TITLE_KEY = "title"
private const val LOADING_KEY = "loading"
private const val ERROR_KEY = "error"
private const val STALENESS_KEY = "staleness"
private const val EMPTY_KEY = "empty"

/** Screen chrome, not archive content: `text`, drawn in every state. */
@Composable
private fun Title(title: String) {
    Text(
        text = title,
        style = BluesJamTheme.typography.h1,
        color = BluesJamTheme.colors.text,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * One past jam: one merged node read in visual order (date, venue, count, hook or line). A row with
 * [PastJamRowUiModel.openLabel] is one clickable node (`Role.Button`, that click label, at least
 * 48dp, ripple only: no chevron, colours unchanged); a row without it is not clickable. Colours only
 * from [PastJamsDefaults.rowStyle].
 */
@Composable
private fun PastJamRow(row: PastJamRowUiModel) {
    val spacing = BluesJamTheme.spacing
    val typography = BluesJamTheme.typography
    val style = PastJamsDefaults.rowStyle()
    val openLabel = row.openLabel
    // Inside the Surface, so the ripple is clipped to the row's shape. Either way one merged node:
    // clickable merges its descendants, the plain row merges them explicitly.
    val node = if (openLabel != null) {
        Modifier
            .clickable(onClickLabel = openLabel, role = Role.Button) { row.events(PastJamRowUiModel.Event.Open) }
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Surface(
        color = style.fill,
        contentColor = style.date,
        shape = BluesJamTheme.shapes.md,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(node)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(text = row.dateLabel, style = typography.songTitle, color = style.date)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = row.venue,
                    style = typography.body,
                    color = style.venue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                (row.summary as? PastJamSummary.Songs)?.let { songs ->
                    Text(text = songs.countLabel, style = typography.body, color = style.count)
                }
            }
            when (val summary = row.summary) {
                is PastJamSummary.Songs -> Text(
                    text = summary.hook,
                    style = typography.body,
                    color = style.hook,
                    maxLines = HOOK_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )

                is PastJamSummary.NotShown -> Text(
                    text = summary.message,
                    style = typography.body,
                    color = style.message,
                )
            }
        }
    }
}

private const val HOOK_MAX_LINES = 2
