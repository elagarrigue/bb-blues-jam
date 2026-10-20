package com.bbbjam.feature.catalog

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.catalog.CatalogSnapshot
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Song
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.PullRefreshUiModel
import com.bbbjam.core.ui.state.StalenessNoticeUiModel
import com.bbbjam.core.ui.state.stalenessNotice
import com.bbbjam.core.ui.text.UiText
import java.time.Duration
import java.time.Instant

/** The admin's Temas tab for the current [inputs] and [controls]. */
internal fun catalogModel(inputs: CatalogInputs, controls: CatalogControls): CatalogUiModel.Content {
    val songs = inputs.catalog?.songs.orEmpty()
    val editing = controls.form.editingId?.let { id -> songs.firstOrNull { it.id.value == id } }
    val deleting = controls.deletingId?.let { id -> songs.firstOrNull { it.id.value == id } }
    return CatalogUiModel.Content(
        title = UiText.res(R.string.catalog_title),
        count = UiText.plural(R.plurals.catalog_count, songs.size).takeIf { songs.isNotEmpty() },
        add = addAction(controls),
        notice = notice(controls),
        search = SearchFieldUiModel(
            query = controls.query,
            label = UiText.res(R.string.catalog_search_label),
            events = EventHandler { event ->
                when (event) {
                    is SearchFieldUiModel.Event.Changed -> controls.search(event.query)
                }
            },
        ),
        list = catalogContent(
            inputs.catalog,
            controls.query,
            controls.busy,
            inputs.onRetry,
            controls::openEdit,
            controls::requestDelete,
        ),
        staleness = inputs.catalog?.staleness(inputs.now, inputs.onRetry),
        pullRefresh = PullRefreshUiModel(isRefreshing = inputs.pulling, events = EventHandler { inputs.onPull() }),
        form = formModel(controls, songs, editing),
        delete = deleting?.let { deleteModel(controls, it, inputs.jams) },
    )
}

private fun addAction(controls: CatalogControls) = ActionUiModel(
    label = UiText.res(R.string.catalog_add),
    enabled = !controls.busy,
    events = EventHandler(key = controls.busy) { event ->
        when (event) {
            ActionUiModel.Event.Click -> controls.openAdd()
        }
    },
)

private fun formModel(controls: CatalogControls, songs: List<Song>, editing: Song?): SongFormUiModel? {
    val form = controls.form
    // A form for a song that no longer exists is not drawn (the controls drop it).
    return if (form.isOpen && (form.editingId == null || editing != null)) {
        songFormModel(
            state = form,
            catalog = songs,
            editing = editing,
            busy = controls.busy,
            onType = form::type,
            onSubmit = { fields -> controls.submit(editing, fields) },
            onCancel = form::close,
        )
    } else {
        null
    }
}

private fun deleteModel(controls: CatalogControls, song: Song, jams: JamsSnapshot?) = deleteDialog(
    song = song,
    uses = jams?.let { futureUses(song.id, it) }.orEmpty(),
    onConfirm = { controls.confirmDelete(song) },
    onClose = controls::closeDelete,
)

/** The sending line, the result line or the failure card; the sending line wins while a write runs. */
private fun notice(controls: CatalogControls): CatalogNoticeUiModel? {
    val sending = controls.sending
    if (sending != null) return CatalogNoticeUiModel.Progress(sending.progressLine())
    return when (val result = controls.result) {
        is CatalogResult.Done -> CatalogNoticeUiModel.Done(result.message)
        is CatalogResult.Failed -> failureNotice(result, controls)
        null -> null
    }
}

private fun failureNotice(result: CatalogResult.Failed, controls: CatalogControls) = CatalogNoticeUiModel.Failure(
    title = result.title,
    message = result.message,
    consequence = UiText.res(R.string.catalog_unchanged),
    retryLabel = UiText.res(CoreR.string.list_state_retry).takeIf { result.retryable },
    dismissLabel = UiText.res(R.string.catalog_close),
    events = EventHandler(key = result) { event ->
        when (event) {
            CatalogNoticeUiModel.Failure.Event.Retry -> controls.retry(result)
            CatalogNoticeUiModel.Failure.Event.Dismiss -> controls.dismissResult()
        }
    },
)

/** The notice above cached songs whose latest refresh failed; never on age alone. */
private fun CatalogSnapshot.staleness(now: Instant, onRetry: () -> Unit): StalenessNoticeUiModel? {
    val fetchedAt = freshness.fetchedAt
    val failure = freshness.lastFailure
    return if (fetchedAt != null && failure != null) {
        stalenessNotice(
            failure is DataFailure.Offline,
            Duration.between(fetchedAt, now),
            freshness.isRefreshing,
            onRetry,
        )
    } else {
        null
    }
}
