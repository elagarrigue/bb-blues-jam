package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.catalog.CatalogRepository
import com.bbbjam.core.data.catalog.CatalogSnapshot
import com.bbbjam.core.data.catalog.RefreshOutcome
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Song
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.ListErrorUiModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalog picker (`admin-add-song-to-setlist` Part B): states, search, sort, listed songs and the
 * pick. The copy is written out from the approved table (C1), never read from [AddSongCopy].
 */
class AddSongPresenterTest {
    private val catalog = FakeCatalogRepository()
    private val jams = FakeJamsRepository()
    private val setlist = FakeSetlistRepository()
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    private val thrill = Song(SongId("the-thrill-is-gone"), "The Thrill Is Gone", "B.B. King", Key("Bm"))
    private val agua = Song(SongId("agua-de-rio"), "Ágüa de río", "Pappo", Key("E"))
    private val crossroads = Song(SongId("crossroads"), "crossroads", "Eric Clapton", Key("A"))
    private val redHouse = Song(SongId("red-house"), "Red House", "Jimi Hendrix", Key("Bb"))
    private val songs = listOf(thrill, agua, crossroads, redHouse)

    private fun snapshot(songs: List<Song>, freshness: Freshness = fetched) = CatalogSnapshot(songs, freshness)

    private fun titles(content: AddSongContentUiModel) = (content as AddSongContentUiModel.Songs).rows.map { it.title }

    @Test
    fun `songs are sorted by title ignoring case and accents, each with its artist and default key`() {
        val content = addSongContent(snapshot(songs), emptySet(), "")

        assertEquals(listOf("Ágüa de río", "crossroads", "Red House", "The Thrill Is Gone"), titles(content))
        val row = (content as AddSongContentUiModel.Songs).rows.last()
        assertEquals("B.B. King", row.artist)
        assertEquals("Bm", row.key)
        assertEquals("Tonalidad Bm", row.keyDescription)
        assertEquals("agregar a la lista", row.pickLabel)
        assertFalse(row.isListed)
        assertNull(row.listedLabel)
    }

    @Test
    fun `search matches title or artist, ignoring case, accents and surrounding spaces`() {
        assertEquals(listOf("Ágüa de río"), titles(addSongContent(snapshot(songs), emptySet(), "  AGUA ")))
        assertEquals(listOf("Ágüa de río"), titles(addSongContent(snapshot(songs), emptySet(), "rio")))
        assertEquals(listOf("The Thrill Is Gone"), titles(addSongContent(snapshot(songs), emptySet(), "b.b.")))
        assertEquals(listOf("crossroads"), titles(addSongContent(snapshot(songs), emptySet(), "CLAP")))
        assertEquals(
            AddSongContentUiModel.NoResults("Ningún tema coincide con «zeppelin»."),
            addSongContent(snapshot(songs), emptySet(), " zeppelin "),
        )
    }

    @Test
    fun `a listed song is muted with Ya está en la lista and its pick does nothing`() {
        var picked: Song? = null
        val content = addSongContent(snapshot(songs), setOf(redHouse.id), "", onPick = { picked = it })
        val rows = (content as AddSongContentUiModel.Songs).rows

        val listed = rows.single { it.id == "red-house" }
        assertTrue(listed.isListed)
        assertEquals("Ya está en la lista", listed.listedLabel)
        listed.events(CatalogRowUiModel.Event.Pick)
        assertNull(picked)

        rows.single { it.id == "crossroads" }.events(CatalogRowUiModel.Event.Pick)
        assertEquals(crossroads, picked)
    }

    @Test
    fun `listed songs are the jam's setlist, drafts included, and the adds sending to it`() {
        val inSetlist = JamSong(1, redHouse.id, "Red House", "Jimi Hendrix", Key("Bb"), Lineup.default())
        val draft = Jam(jamDate, LocalTime.of(21, 0), "Lugar", JamStatus.DRAFT, Setlist.Available(listOf(inSetlist)))
        val jamsSnapshot = JamsSnapshot(draft, emptyList(), fetched)
        val adds = listOf(
            SetlistAdd(1, jamDate, crossroads.id, "crossroads", "Eric Clapton", Key("A"), SetlistAdd.State.Sending),
            SetlistAdd(2, jamDate, thrill.id, "t", "a", Key("Bm"), SetlistAdd.State.Failed(WriteOutcome.Offline)),
            SetlistAdd(3, jamDate.plusDays(1), agua.id, "a", "b", Key("E"), SetlistAdd.State.Sending),
        )

        assertEquals(setOf(redHouse.id, crossroads.id), listedSongs(jamsSnapshot, adds, jamDate))
        assertEquals(emptySet<SongId>(), listedSongs(null, emptyList(), jamDate))
    }

    @Test
    fun `the states follow the cached catalog`() {
        assertEquals(AddSongContentUiModel.Loading("Cargando el catálogo"), addSongContent(null, emptySet(), ""))
        assertEquals(
            AddSongContentUiModel.Loading("Cargando el catálogo"),
            addSongContent(snapshot(emptyList(), Freshness(null, null, isRefreshing = true)), emptySet(), ""),
        )
        assertEquals(
            AddSongContentUiModel.Empty(
                EmptyStateUiModel("El catálogo está vacío", "Cargá temas en la pestaña Catalogo de la planilla."),
            ),
            addSongContent(snapshot(emptyList()), emptySet(), "blues"),
        )
        val failed = addSongContent(snapshot(emptyList(), Freshness(null, DataFailure.Offline, false)), emptySet(), "")
        assertEquals("No pudimos cargar el catálogo", ((failed as AddSongContentUiModel.Failed).error).title)
        // A retry running shows the skeleton again.
        assertEquals(
            AddSongContentUiModel.Loading("Cargando el catálogo"),
            addSongContent(snapshot(emptyList(), Freshness(null, DataFailure.Offline, true)), emptySet(), ""),
        )
    }

    @Test
    fun `picking adds the song in its catalog key, closes once, and a second tap adds nothing`() = runTest {
        var added = 0
        val presenter = AddSongPresenter(catalog, jams, setlist)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present(AddSongPresenter.Params(jamDate, onBack = {}, onAdded = { added++ }))
        }.test {
            val first = awaitItem()
            assertEquals("Agregar tema", first.title)
            assertEquals("Buscar por título o artista", first.search.label)
            assertEquals(AddSongContentUiModel.Loading("Cargando el catálogo"), first.content)
            catalog.snapshots.emit(snapshot(songs))
            jams.snapshots.emit(JamsSnapshot(null, emptyList(), fetched))
            val rows = (expectMostRecentItem().content as AddSongContentUiModel.Songs).rows

            val row = rows.single { it.id == "the-thrill-is-gone" }
            row.events(CatalogRowUiModel.Event.Pick)
            row.events(CatalogRowUiModel.Event.Pick)
            rows.single { it.id == "crossroads" }.events(CatalogRowUiModel.Event.Pick)

            assertEquals(listOf(FakeSetlistRepository.AddCall(jamDate, thrill.id, Key("Bm"))), setlist.addCalls)
            assertEquals(1, added)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing filters, Back leaves without adding, and Retry re-subscribes and refreshes`() = runTest {
        var backs = 0
        val presenter = AddSongPresenter(catalog, jams, setlist)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present(AddSongPresenter.Params(jamDate, onBack = { backs++ }, onAdded = {}))
        }.test {
            catalog.snapshots.emit(snapshot(emptyList(), Freshness(null, DataFailure.Offline, false)))
            val failed = expectMostRecentItem()
            ((failed.content as AddSongContentUiModel.Failed).error).events(ListErrorUiModel.Event.Retry)
            catalog.snapshots.emit(snapshot(songs))
            val loaded = expectMostRecentItem()
            assertEquals(2, catalog.subscriptions)
            assertEquals(1, catalog.refreshCalls)

            loaded.search.events(SearchFieldUiModel.Event.Changed("red"))
            val searched = awaitItem()
            assertEquals("red", searched.search.query)
            assertEquals(listOf("Red House"), titles(searched.content))

            searched.back.events(BackUiModel.Event.Back)
            assertEquals(1, backs)
            assertTrue(setlist.addCalls.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Emits what the test puts in [snapshots]; counts subscriptions and refreshes. */
    class FakeCatalogRepository : CatalogRepository {
        val snapshots = MutableSharedFlow<CatalogSnapshot>(replay = 1)
        var subscriptions = 0
            private set
        var refreshCalls = 0
            private set

        override fun observeCatalog(): Flow<CatalogSnapshot> {
            subscriptions++
            return snapshots
        }

        override suspend fun refresh(): RefreshOutcome {
            refreshCalls++
            return RefreshOutcome.Failed(DataFailure.Offline)
        }
    }
}
