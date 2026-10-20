package com.bbbjam.feature.catalog

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.listError
import com.bbbjam.core.ui.text.UiText
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list-states rules of the Temas list, with the sort and the search (`admin-catalog-tab`). */
class CatalogContentTest {
    private fun titles(content: CatalogListUiModel) = (content as CatalogListUiModel.Songs).rows.map { it.title }

    @Test
    fun `no snapshot yet is the skeleton`() {
        assertEquals(
            CatalogListUiModel.Loading(UiText.res(R.string.catalog_loading)),
            catalogContent(null, "", busy = false),
        )
    }

    @Test
    fun `songs are sorted by title then artist ignoring case and accents`() {
        val sameTitle = CROSSROADS.copy(id = com.bbbjam.core.model.SongId("crossroads-cream"), artist = "Cream")

        val content = catalogContent(catalogSnapshot(SONGS + sameTitle), "", busy = false)

        assertEquals(
            listOf("Ágüa de río", "Crossroads", "Crossroads", "Red House", "The Thrill Is Gone"),
            titles(content),
        )
        assertEquals(
            listOf("Cream", "Eric Clapton"),
            (content as CatalogListUiModel.Songs).rows.filter { it.title == "Crossroads" }.map { it.artist },
        )
    }

    @Test
    fun `the search matches title or artist and trims the query`() {
        assertEquals(listOf("Ágüa de río"), titles(catalogContent(catalogSnapshot(), "  AGUA ", busy = false)))
        assertEquals(listOf("Red House"), titles(catalogContent(catalogSnapshot(), "jimi", busy = false)))
        assertEquals(
            CatalogListUiModel.NoResults(UiText.res(R.string.catalog_no_results, "zzz")),
            catalogContent(catalogSnapshot(), "zzz", busy = false),
        )
    }

    @Test
    fun `rows are disabled while a write runs`() {
        val rows = (catalogContent(catalogSnapshot(), "", busy = true) as CatalogListUiModel.Songs).rows
        assertTrue(rows.none { it.actionsEnabled })
    }

    @Test
    fun `an empty catalog read once is the empty state`() {
        assertEquals(
            CatalogListUiModel.Empty(
                EmptyStateUiModel(
                    UiText.res(R.string.catalog_empty_title),
                    UiText.res(R.string.catalog_empty_message),
                ),
            ),
            catalogContent(catalogSnapshot(emptyList()), "", busy = false),
        )
    }

    @Test
    fun `nothing cached and a failed read is the error block, unless a retry is running`() {
        val failed = Freshness(null, DataFailure.Offline, isRefreshing = false)
        assertEquals(
            CatalogListUiModel.Failed(listError(UiText.res(R.string.catalog_load_failed), isOffline = true) {}),
            catalogContent(catalogSnapshot(emptyList(), failed), "", busy = false),
        )
        val retrying = Freshness(null, DataFailure.Offline, isRefreshing = true)
        assertEquals(
            CatalogListUiModel.Loading(UiText.res(R.string.catalog_loading)),
            catalogContent(catalogSnapshot(emptyList(), retrying), "", busy = false),
        )
        val never = Freshness(Instant.EPOCH, null, isRefreshing = false)
        assertTrue(catalogContent(catalogSnapshot(emptyList(), never), "", busy = false) is CatalogListUiModel.Empty)
    }
}
