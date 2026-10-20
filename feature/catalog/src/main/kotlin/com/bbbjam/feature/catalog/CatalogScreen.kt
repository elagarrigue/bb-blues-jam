package com.bbbjam.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.bbbjam.core.ui.state.EmptyStateBlock
import com.bbbjam.core.ui.state.ListErrorBlock
import com.bbbjam.core.ui.state.RefreshableContent
import com.bbbjam.core.ui.state.SkeletonList
import com.bbbjam.core.ui.state.StalenessNotice
import com.bbbjam.core.ui.state.rememberRevealingLazyListState
import com.bbbjam.core.ui.text.resolve
import com.bbbjam.core.ui.theme.BluesJamTheme
import org.koin.compose.koinInject

/**
 * The admin's Temas tab (`admin-catalog-tab`, D-30): the catalog with a search field, `Agregar tema`, and an
 * edit and a delete icon per song. `:app` draws it only in admin mode; the presenter also hides it from a
 * musician, and Apps Script authorizes every write. It renders [CatalogUiModel] and forwards events; the
 * presenter decides. [contentPadding] goes inside the list, so the background runs edge to edge.
 */
@Composable
fun CatalogScreen(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues()) {
    CatalogScreen(modifier = modifier, contentPadding = contentPadding, presenter = koinInject())
}

@Composable
internal fun CatalogScreen(modifier: Modifier, contentPadding: PaddingValues, presenter: CatalogPresenter) {
    CatalogContent(model = presenter.present(Unit), modifier = modifier, contentPadding = contentPadding)
}

@Composable
internal fun CatalogContent(model: CatalogUiModel, modifier: Modifier, contentPadding: PaddingValues) {
    when (model) {
        // Not read yet, or not an admin: the background only, never a flash of controls.
        CatalogUiModel.Loading, CatalogUiModel.Hidden ->
            Box(modifier = modifier.fillMaxSize().background(BluesJamTheme.colors.background))

        is CatalogUiModel.Content -> CatalogList(model, modifier, contentPadding)
    }
}

@Composable
private fun CatalogList(model: CatalogUiModel.Content, modifier: Modifier, contentPadding: PaddingValues) {
    val spacing = BluesJamTheme.spacing
    val layoutDirection = LocalLayoutDirection.current
    val padding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + spacing.md,
        top = contentPadding.calculateTopPadding() + spacing.lg,
        end = contentPadding.calculateEndPadding(layoutDirection) + spacing.md,
        bottom = contentPadding.calculateBottomPadding() + spacing.lg,
    )
    RefreshableContent(
        model.pullRefresh,
        modifier = modifier.fillMaxSize().background(BluesJamTheme.colors.background),
    ) {
        val listState = rememberRevealingLazyListState(model.staleness)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item(key = TITLE_KEY) {
                Text(
                    text = model.title.resolve(),
                    style = BluesJamTheme.typography.h1,
                    color = BluesJamTheme.colors.text,
                    modifier = Modifier.semantics { heading() },
                )
            }
            model.count?.let { count ->
                item(key = COUNT_KEY) {
                    Text(
                        text = count.resolve(),
                        style = BluesJamTheme.typography.caption,
                        color = CatalogDefaults.caption(),
                    )
                }
            }
            item(key = ADD_KEY) { AddSongButton(model.add) }
            model.notice?.let { notice -> item(key = NOTICE_KEY) { CatalogNotice(notice) } }
            item(key = SEARCH_KEY) { SearchField(model.search) }
            model.staleness?.let { notice -> item(key = STALENESS_KEY) { StalenessNotice(notice) } }
            listItems(model.list)
        }
    }
    model.form?.let { SongFormDialog(it) }
    model.delete?.let { DeleteSongDialog(it) }
}

private fun LazyListScope.listItems(list: CatalogListUiModel) {
    when (list) {
        is CatalogListUiModel.Loading -> item(key = STATE_KEY) { SkeletonList(list.description.resolve()) }

        is CatalogListUiModel.Failed -> item(key = STATE_KEY) { ListErrorBlock(list.error) }

        is CatalogListUiModel.Empty -> item(key = STATE_KEY) { EmptyStateBlock(list.empty) }

        is CatalogListUiModel.NoResults -> item(key = STATE_KEY) {
            Text(
                text = list.message.resolve(),
                style = BluesJamTheme.typography.body,
                color = BluesJamTheme.colors.textMuted,
            )
        }

        is CatalogListUiModel.Songs -> items(list.rows, key = { it.id }) { row -> SongRow(row) }
    }
}

private const val TITLE_KEY = "title"
private const val COUNT_KEY = "count"
private const val ADD_KEY = "add"
private const val NOTICE_KEY = "notice"
private const val SEARCH_KEY = "search"
private const val STALENESS_KEY = "staleness"
private const val STATE_KEY = "state"
