package com.bbbjam.feature.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.edit.CatalogEditRepository
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.ui.presenter.Presenter
import kotlinx.coroutines.launch

/**
 * Presents the admin's Temas tab (`admin-catalog-tab`, D-30): the cached catalog, searchable, with the add,
 * edit and delete flows. The flag from [adminSession] only decides what is drawn: [CatalogUiModel.Loading]
 * until it is read, [CatalogUiModel.Hidden] for a musician. Every mutation is a [CatalogEditRepository] call
 * (D-13) that Apps Script validates; the list changes only from Room after the server's echo, never from here.
 *
 * The state and its decisions are in [CatalogControls] (one write at a time, so a double tap sends once; the
 * write itself runs in the data scope, so leaving the tab loses only the result line) and the model in
 * [catalogModel]. The delete of a song that the raw jams snapshot shows in the upcoming or a held-back jam is
 * refused with the dates and nothing is sent; the server refuses it too. A pull is one catalog read; a pull
 * while one runs is ignored. A Retry re-subscribes, which recovers from a failed local read, and reads again.
 */
internal class CatalogPresenter(
    private val catalog: CatalogRepository,
    edits: CatalogEditRepository,
    private val jams: JamsRepository,
    private val calendar: JamCalendar,
    private val adminSession: AdminSession,
) : Presenter<CatalogUiModel, Unit> {
    private val writes = CatalogWrites(edits)

    @Composable
    override fun present(params: Unit): CatalogUiModel {
        val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = null)
        return when (isAdmin) {
            null -> CatalogUiModel.Loading
            false -> CatalogUiModel.Hidden
            true -> presentContent()
        }
    }

    @Composable
    private fun presentContent(): CatalogUiModel.Content {
        val scope = rememberCoroutineScope()
        var subscription by remember { mutableIntStateOf(0) }
        var pulling by remember { mutableStateOf(false) }
        val snapshot by remember(subscription) { catalog.observeCatalog() }.collectAsState(initial = null)
        val jamsSnapshot by remember { jams.observeJams() }.collectAsState(initial = null)
        val now = remember(snapshot) { calendar.now() }
        val controls = rememberCatalogControls(writes) { scope.launch { jams.refresh() } }
        val songs = snapshot?.songs.orEmpty()
        val editingMissing = controls.form.editingId?.let { id -> songs.none { it.id.value == id } } == true
        val deletingMissing = controls.deletingId?.let { id -> songs.none { it.id.value == id } } == true
        LaunchedEffect(snapshot != null, editingMissing, deletingMissing) {
            if (snapshot != null) controls.dropMissing(editingMissing, deletingMissing)
        }
        val onRetry: () -> Unit = {
            subscription++
            scope.launch { catalog.refresh() }
        }
        val onPull: () -> Unit = {
            if (!pulling) {
                pulling = true
                subscription++
                scope.launch {
                    try {
                        catalog.refresh()
                    } finally {
                        pulling = false
                    }
                }
            }
        }
        return catalogModel(CatalogInputs(snapshot, jamsSnapshot, now, pulling, onRetry, onPull), controls)
    }
}
