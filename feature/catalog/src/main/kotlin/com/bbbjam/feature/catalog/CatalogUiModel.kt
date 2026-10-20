package com.bbbjam.feature.catalog

import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.UiEvent
import com.bbbjam.core.ui.presenter.UiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.ListErrorUiModel
import com.bbbjam.core.ui.state.PullRefreshUiModel
import com.bbbjam.core.ui.state.StalenessNoticeUiModel
import com.bbbjam.core.ui.text.UiText

/**
 * Everything the admin's Temas tab draws (`admin-catalog-tab`, D-30), as plain values. [Loading] until the
 * admin flag is read, [Hidden] for a musician (defence in depth: `:app` does not draw the tab either), and
 * [Content] for the admin. The flag only decides what is drawn; Apps Script authorizes every write.
 */
internal sealed interface CatalogUiModel : UiModel {
    data object Loading : CatalogUiModel

    data object Hidden : CatalogUiModel

    /**
     * [count] is the whole catalog's size (null until a catalog is shown). [notice] is the sending line, the
     * result line or the failure card. [form] and [delete] are the modals open now; at most one is.
     */
    data class Content(
        val title: UiText,
        val count: UiText?,
        val add: ActionUiModel,
        val notice: CatalogNoticeUiModel?,
        val search: SearchFieldUiModel,
        val list: CatalogListUiModel,
        val staleness: StalenessNoticeUiModel?,
        val pullRefresh: PullRefreshUiModel,
        val form: SongFormUiModel?,
        val delete: DeleteSongUiModel?,
    ) : CatalogUiModel
}

/** A button: [label], and [enabled] false while a write runs. */
internal data class ActionUiModel(val label: UiText, val enabled: Boolean, val events: EventHandler<Event>) :
    UiModel {
    sealed interface Event : UiEvent {
        data object Click : Event
    }
}

/** The search field: [query] as typed, [label] its label. Matching ignores case and accents. */
internal data class SearchFieldUiModel(val query: String, val label: UiText, val events: EventHandler<Event>) :
    UiModel {
    sealed interface Event : UiEvent {
        data class Changed(val query: String) : Event
    }
}

/** The line or card under the add button. */
internal sealed interface CatalogNoticeUiModel : UiModel {
    /** A write is running: "Guardando el tema…". Polite live region. */
    data class Progress(val message: UiText) : CatalogNoticeUiModel

    /** The last write worked: "Tema agregado." Polite live region. */
    data class Done(val message: UiText) : CatalogNoticeUiModel

    /**
     * The last write failed and changed nothing. [retryLabel] is null when sending the same request again is
     * pointless (the server refused it, or the script is outdated). Assertive live region.
     */
    data class Failure(
        val title: UiText,
        val message: UiText,
        val consequence: UiText,
        val retryLabel: UiText?,
        val dismissLabel: UiText,
        val events: EventHandler<Event>,
    ) : CatalogNoticeUiModel {
        sealed interface Event : UiEvent {
            data object Retry : Event

            data object Dismiss : Event
        }
    }
}

internal sealed interface CatalogListUiModel : UiModel {
    /** No catalog emission yet, or nothing cached and a read is running. */
    data class Loading(val description: UiText) : CatalogListUiModel

    /** Nothing cached and the latest read failed. */
    data class Failed(val error: ListErrorUiModel) : CatalogListUiModel

    /** The catalog was read and holds no song. */
    data class Empty(val empty: EmptyStateUiModel) : CatalogListUiModel

    /** The search matches no song: [message] names the query. */
    data class NoResults(val message: UiText) : CatalogListUiModel

    /** The matching songs, sorted by title then artist ignoring case and accents. */
    data class Songs(val rows: List<SongRowUiModel>) : CatalogListUiModel
}

/**
 * One catalog song: [key] is its default key (the `key` role, read as [keyDescription]); the two icon actions
 * are described by the song title and drawn disabled ([actionsEnabled] false) while any catalog write runs.
 */
internal data class SongRowUiModel(
    val id: String,
    val title: String,
    val artist: String,
    val key: String,
    val keyDescription: UiText,
    val editDescription: UiText,
    val deleteDescription: UiText,
    val actionsEnabled: Boolean,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data object Edit : Event

        data object Delete : Event
    }
}

/** Which field a [SongFormUiModel.Event.Edit] is for. */
internal enum class SongFormField { TITLE, ARTIST, KEY }

/** One labelled text field of the form: the typed [value], an optional [hint] and an optional [error]. */
internal data class FormFieldUiModel(val label: UiText, val value: String, val hint: UiText?, val error: UiText?) :
    UiModel

/**
 * The add and edit dialog: three fields, the [notes], a [submitLabel] enabled only when [canSubmit] and a
 * [cancelLabel]. Back and a tap outside send [Event.Cancel]; nothing is written until [Event.Submit].
 */
internal data class SongFormUiModel(
    val title: UiText,
    val titleField: FormFieldUiModel,
    val artistField: FormFieldUiModel,
    val keyField: FormFieldUiModel,
    val notes: List<UiText>,
    val submitLabel: UiText,
    val cancelLabel: UiText,
    val canSubmit: Boolean,
    val events: EventHandler<Event>,
) : UiModel {
    sealed interface Event : UiEvent {
        data class Edit(val field: SongFormField, val text: String) : Event

        data object Submit : Event

        data object Cancel : Event
    }
}

/** The delete dialog of one song: a confirmation, or the reason it cannot be deleted. */
internal sealed interface DeleteSongUiModel : UiModel {
    val songId: String

    data class Confirming(
        override val songId: String,
        val title: UiText,
        val lines: List<UiText>,
        val confirmLabel: UiText,
        val cancelLabel: UiText,
        val events: EventHandler<Event>,
    ) : DeleteSongUiModel {
        sealed interface Event : UiEvent {
            data object Confirm : Event

            data object Cancel : Event
        }
    }

    /** The song is in the list of the upcoming or a held-back jam: nothing is sent. */
    data class Refused(
        override val songId: String,
        val title: UiText,
        val lines: List<UiText>,
        val okLabel: UiText,
        val events: EventHandler<Event>,
    ) : DeleteSongUiModel {
        sealed interface Event : UiEvent {
            data object Dismiss : Event
        }
    }
}
