package com.bbbjam.feature.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.bbbjam.core.data.catalog.edit.SongFields
import com.bbbjam.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * The tab's state and decisions (`admin-catalog-tab`): the search, the add and edit form, the song being
 * deleted, the write in flight and the last result. One write runs at a time: while it does every opener and
 * submit is ignored, so a double tap sends once. A submit or a confirmation closes its dialog first and ignores a
 * handler of an earlier model that finds the dialog already closed. The write is started undispatched in
 * [scope], and runs in the data scope inside the repository, so leaving the tab loses only the result line.
 */
internal class CatalogControls(
    private val scope: CoroutineScope,
    private val writes: CatalogWrites,
    private val onStaleJams: () -> Unit,
    val form: SongFormState,
    private val queryState: MutableState<String>,
    private val deleting: MutableState<String?>,
    private val sendingState: MutableState<CatalogAction?>,
    private val resultState: MutableState<CatalogResult?>,
) {
    val query: String get() = queryState.value
    val deletingId: String? get() = deleting.value
    val sending: CatalogAction? get() = sendingState.value
    val result: CatalogResult? get() = resultState.value
    val busy: Boolean get() = sending != null

    fun search(text: String) {
        queryState.value = text
    }

    fun openAdd() {
        if (!busy) {
            deleting.value = null
            form.openAdd()
        }
    }

    fun openEdit(song: Song) {
        if (!busy) {
            deleting.value = null
            form.openEdit(song)
        }
    }

    fun requestDelete(song: Song) {
        if (!busy) {
            form.close()
            deleting.value = song.id.value
        }
    }

    fun closeDelete() {
        deleting.value = null
    }

    fun dismissResult() {
        resultState.value = null
    }

    fun retry(failed: CatalogResult.Failed) {
        resultState.value = null
        run(failed.action)
    }

    /** Creates, or updates [editing] when it is the song being edited. */
    fun submit(editing: Song?, fields: SongFields) {
        if (form.isOpen) {
            form.close()
            run(editing?.let { CatalogAction.Update(it.id, fields) } ?: CatalogAction.Create(fields))
        }
    }

    fun confirmDelete(song: Song) {
        if (deleting.value == song.id.value) {
            deleting.value = null
            run(CatalogAction.Delete(song.id))
        }
    }

    /** A song another refresh removed takes its open dialog with it. */
    fun dropMissing(editingMissing: Boolean, deletingMissing: Boolean) {
        if (editingMissing) form.close()
        if (deletingMissing) deleting.value = null
    }

    private fun run(action: CatalogAction) {
        if (!busy) {
            sendingState.value = action
            resultState.value = null
            // Undispatched: the write is handed to the data layer before this handler returns.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    val outcome = writes.perform(action)
                    resultState.value = outcome
                    if ((outcome as? CatalogResult.Failed)?.staleJams == true) onStaleJams()
                } finally {
                    sendingState.value = null
                }
            }
        }
    }
}

@Composable
internal fun rememberCatalogControls(writes: CatalogWrites, onStaleJams: () -> Unit): CatalogControls {
    val scope = rememberCoroutineScope()
    val form = rememberSongFormState()
    val query = rememberSaveable { mutableStateOf("") }
    val deleting = rememberSaveable { mutableStateOf<String?>(null) }
    val sending = remember { mutableStateOf<CatalogAction?>(null) }
    val result = remember { mutableStateOf<CatalogResult?>(null) }
    return CatalogControls(scope, writes, onStaleJams, form, query, deleting, sending, result)
}
