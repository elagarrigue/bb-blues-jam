package com.bbbjam.feature.nextjam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.CatalogSnapshot
import com.bbbjam.core.data.jams.JamsRepository
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistRepository
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.nav.backUiModel
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.presenter.Presenter
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.listError
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Presents the catalog picker (`admin-add-song-to-setlist`, V1). It follows the cached catalog
 * (collecting it may start the repository's own refresh; Retry re-subscribes and refreshes, as on
 * Próxima jam), filters it by the search and marks the songs already in the jam's setlist or being
 * added. Picking a song calls [SetlistRepository.addSong] with the catalog's default key (K1 (a);
 * the admin changes it later with `admin-set-key`, D-08) and then [Params.onAdded] at once: the add
 * runs in the data layer and shows on Próxima jam as a pending row. Only the first pick counts, so
 * a double tap before the screen closes adds once.
 *
 * Reached only from the admin's "Agregar tema"; it authorizes nothing (Apps Script does).
 */
class AddSongPresenter(
    private val catalog: CatalogRepository,
    private val jams: JamsRepository,
    private val setlist: SetlistRepository,
) : Presenter<AddSongUiModel, AddSongPresenter.Params> {

    /** [jamDate] is the jam to add to; [onBack] leaves without adding; [onAdded] closes after a pick. */
    data class Params(val jamDate: LocalDate, val onBack: () -> Unit, val onAdded: () -> Unit)

    @Composable
    override fun present(params: Params): AddSongUiModel {
        val currentJamDate by rememberUpdatedState(params.jamDate)
        val currentOnBack by rememberUpdatedState(params.onBack)
        val currentOnAdded by rememberUpdatedState(params.onAdded)
        val scope = rememberCoroutineScope()
        var subscription by remember { mutableIntStateOf(0) }
        val catalogSnapshot by remember(subscription) { catalog.observeCatalog() }.collectAsState(initial = null)
        val jamsSnapshot by remember { jams.observeJams() }.collectAsState(initial = null)
        val adds by remember { setlist.observeAdds() }.collectAsState(initial = emptyList())
        var query by rememberSaveable { mutableStateOf("") }
        var picked by remember { mutableStateOf(false) }
        val onRetry: () -> Unit = {
            subscription++
            scope.launch { catalog.refresh() }
        }
        val onPick: (Song) -> Unit = { song ->
            if (!picked) {
                picked = true
                // Undispatched: the add is handed to the data layer before this handler returns.
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    setlist.addSong(currentJamDate, song.id, song.defaultKey)
                }
                currentOnAdded()
            }
        }
        return AddSongUiModel(
            back = backUiModel { currentOnBack() },
            title = AddSongCopy.TITLE,
            search = SearchFieldUiModel(
                query = query,
                label = AddSongCopy.SEARCH_LABEL,
                events = EventHandler { event ->
                    when (event) {
                        is SearchFieldUiModel.Event.Changed -> query = event.query
                    }
                },
            ),
            content = addSongContent(
                catalog = catalogSnapshot,
                listed = listedSongs(jamsSnapshot, adds, params.jamDate),
                query = query,
                onRetry = onRetry,
                onPick = onPick,
            ),
        )
    }
}

/** The songs of [jamDate]'s setlist (the admin's view, drafts included) and those being added to it. */
internal fun listedSongs(jams: JamsSnapshot?, adds: List<SetlistAdd>, jamDate: LocalDate): Set<SongId> {
    val jam = (listOfNotNull(jams?.upcoming) + jams?.past.orEmpty()).firstOrNull { it.date == jamDate }
    val inSetlist = (jam?.setlist as? Setlist.Available)?.songs.orEmpty().map { it.songId }
    val sending = adds.filter { it.jamDate == jamDate && it.state == SetlistAdd.State.Sending }.map { it.songId }
    return (inSetlist + sending).toSet()
}

/**
 * The picker's content: the `list-states` rules over the cached catalog (skeleton, error only when
 * nothing is cached and the read failed with no retry running, empty once a read succeeded), then
 * the songs matching [query] sorted by title, or the no-results line.
 */
internal fun addSongContent(
    catalog: CatalogSnapshot?,
    listed: Set<SongId>,
    query: String,
    onRetry: () -> Unit = {},
    onPick: (Song) -> Unit = {},
): AddSongContentUiModel {
    val freshness = catalog?.freshness
    val failure = freshness?.lastFailure
    val wanted = query.trim()
    return when {
        catalog == null -> AddSongContentUiModel.Loading(AddSongCopy.LOADING)

        catalog.songs.isNotEmpty() -> {
            val needle = wanted.searchKey()
            val rows = catalog.songs
                .filter {
                    needle.isEmpty() || it.title.searchKey().contains(needle) ||
                        it.artist.searchKey().contains(needle)
                }
                .sortedWith(compareBy({ it.title.searchKey() }, { it.artist.searchKey() }, { it.id.value }))
                .map { song -> song.toRow(isListed = song.id in listed, onPick = onPick) }
            if (rows.isEmpty()) {
                AddSongContentUiModel.NoResults(
                    AddSongCopy.noResults(wanted),
                )
            } else {
                AddSongContentUiModel.Songs(rows)
            }
        }

        freshness?.fetchedAt != null ->
            AddSongContentUiModel.Empty(EmptyStateUiModel(AddSongCopy.EMPTY_TITLE, AddSongCopy.EMPTY))

        failure != null && freshness?.isRefreshing != true ->
            AddSongContentUiModel.Failed(listError(AddSongCopy.LOAD_FAILED, failure is DataFailure.Offline, onRetry))

        else -> AddSongContentUiModel.Loading(AddSongCopy.LOADING)
    }
}

private fun Song.toRow(isListed: Boolean, onPick: (Song) -> Unit) = CatalogRowUiModel(
    id = id.value,
    title = title,
    artist = artist,
    key = defaultKey.value,
    keyDescription = NextJamCopy.keyDescription(defaultKey.value),
    isListed = isListed,
    listedLabel = if (isListed) AddSongCopy.ALREADY_LISTED else null,
    pickLabel = AddSongCopy.PICK_LABEL,
    events = EventHandler(key = id.value) { event ->
        when (event) {
            CatalogRowUiModel.Event.Pick -> if (!isListed) onPick(this)
        }
    },
)

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Lowercase with accents removed (`Batería` → `bateria`), for matching and sorting only. */
internal fun String.searchKey(): String =
    COMBINING_MARKS.replace(Normalizer.normalize(this, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
