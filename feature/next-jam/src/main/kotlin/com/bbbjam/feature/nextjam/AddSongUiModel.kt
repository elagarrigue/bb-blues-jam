package com.bbbjam.feature.nextjam

import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.ListErrorUiModel

/**
 * The catalog picker (`admin-add-song-to-setlist`, V1): a back control, the [title], the [search]
 * field and the [content]. Picking a song adds it in the key the catalog suggests (K1 (a)) and
 * closes the picker at once; the add goes on in the data layer.
 */
data class AddSongUiModel(
    val back: BackUiModel,
    val title: String,
    val search: SearchFieldUiModel,
    val content: AddSongContentUiModel,
) : UiModel

/** The search field: [query] as typed, [label] its label. Matching ignores case and accents. */
data class SearchFieldUiModel(val query: String, val label: String, val events: EventHandler<Event>) : UiModel {
    sealed interface Event : UiEvent {
        data class Changed(val query: String) : Event
    }
}

sealed interface AddSongContentUiModel : UiModel {
    /** No catalog emission yet, or nothing cached and a read is running. */
    data class Loading(val description: String) : AddSongContentUiModel

    /** Nothing cached and the latest read failed. */
    data class Failed(val error: ListErrorUiModel) : AddSongContentUiModel

    /** The catalog was read and holds no song. */
    data class Empty(val empty: EmptyStateUiModel) : AddSongContentUiModel

    /** The search matches no song: [message] names the query. */
    data class NoResults(val message: String) : AddSongContentUiModel

    /** The matching songs, sorted by title ignoring case and accents. */
    data class Songs(val rows: List<CatalogRowUiModel>) : AddSongContentUiModel
}

/**
 * One catalog song: [title], [artist] and the [key] it would be added in (the catalog default,
 * drawn in the `key` role, read as [keyDescription]). A song already in the setlist or being added
 * [isListed]: drawn muted with [listedLabel] and not clickable. [pickLabel] is the click label
 * ("agregar a la lista").
 */
data class CatalogRowUiModel(
    val id: String,
    val title: String,
    val artist: String,
    val key: String,
    val keyDescription: String,
    val isListed: Boolean,
    val listedLabel: String?,
    val pickLabel: String,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        /** Add this song to the setlist. A repository call, never a UI-only change (D-13). */
        data object Pick : Event
    }
}
