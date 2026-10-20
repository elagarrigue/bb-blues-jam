package com.bbbjam.feature.catalog

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.catalog.CatalogSnapshot
import com.bbbjam.core.model.Song
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.listError
import com.bbbjam.core.ui.text.SongListText
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.text.searchKey

/**
 * The list's content: the `list-states` rules over the cached catalog (skeleton, error only when nothing is
 * cached and the read failed with no retry running, empty once a read succeeded), then the songs matching
 * [query] sorted by title and artist, or the no-results line. [busy] disables the rows' actions while a
 * catalog write runs.
 */
internal fun catalogContent(
    catalog: CatalogSnapshot?,
    query: String,
    busy: Boolean,
    onRetry: () -> Unit = {},
    onEdit: (Song) -> Unit = {},
    onDelete: (Song) -> Unit = {},
): CatalogListUiModel {
    val freshness = catalog?.freshness
    val failure = freshness?.lastFailure
    val wanted = query.trim()
    return when {
        catalog == null -> CatalogListUiModel.Loading(UiText.res(R.string.catalog_loading))

        catalog.songs.isNotEmpty() -> {
            val needle = wanted.searchKey()
            val rows = catalog.songs
                .filter {
                    needle.isEmpty() || it.title.searchKey().contains(needle) ||
                        it.artist.searchKey().contains(needle)
                }
                .sortedWith(compareBy({ it.title.searchKey() }, { it.artist.searchKey() }, { it.id.value }))
                .map { it.toRow(busy, onEdit, onDelete) }
            if (rows.isEmpty()) {
                CatalogListUiModel.NoResults(UiText.res(R.string.catalog_no_results, wanted))
            } else {
                CatalogListUiModel.Songs(rows)
            }
        }

        freshness?.fetchedAt != null -> CatalogListUiModel.Empty(
            EmptyStateUiModel(
                UiText.res(R.string.catalog_empty_title),
                UiText.res(R.string.catalog_empty_message),
            ),
        )

        failure != null && freshness?.isRefreshing != true -> CatalogListUiModel.Failed(
            listError(UiText.res(R.string.catalog_load_failed), failure is DataFailure.Offline, onRetry),
        )

        else -> CatalogListUiModel.Loading(UiText.res(R.string.catalog_loading))
    }
}

private fun Song.toRow(busy: Boolean, onEdit: (Song) -> Unit, onDelete: (Song) -> Unit) = SongRowUiModel(
    id = id.value,
    title = title,
    artist = artist,
    key = defaultKey.value,
    keyDescription = SongListText.keyDescription(defaultKey.value),
    editDescription = UiText.res(R.string.catalog_edit_description, title),
    deleteDescription = UiText.res(R.string.catalog_delete_description, title),
    actionsEnabled = !busy,
    events = EventHandler(key = "${id.value}|$busy") { event ->
        when (event) {
            SongRowUiModel.Event.Edit -> onEdit(this)
            SongRowUiModel.Event.Delete -> onDelete(this)
        }
    },
)
