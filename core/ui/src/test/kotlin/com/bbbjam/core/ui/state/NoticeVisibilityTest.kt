package com.bbbjam.core.ui.state

import com.bbbjam.core.ui.presenter.EventHandler
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [shouldRevealNotice] covers every row of `offline-notice-visible`'s Acceptance Scenarios list.
 * Real [StalenessNoticeUiModel] instances, not mocks, as `NextJamStatesTest` builds them.
 */
class NoticeVisibilityTest {

    private fun notice(detail: String = "Mostrando lo guardado hace menos de un minuto.") = StalenessNoticeUiModel(
        title = "Sin conexión",
        detail = detail,
        retryLabel = "Reintentar",
        events = EventHandler {},
    )

    @Test
    fun `a notice appearing while resting at the top reveals it`() {
        assertEquals(
            true,
            shouldRevealNotice(notice(), null, firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0),
        )
    }

    @Test
    fun `a notice appearing while scrolled past the first item does not scroll`() {
        assertEquals(
            false,
            shouldRevealNotice(notice(), null, firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0),
        )
    }

    @Test
    fun `a notice appearing with a nonzero offset on the first item does not scroll`() {
        assertEquals(
            false,
            shouldRevealNotice(notice(), null, firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 1),
        )
    }

    @Test
    fun `a notice appearing while the keyed top anchor is retained reveals it`() {
        // In NextJam the new notice is inserted before the keyed header, so Compose retains the
        // header as first visible and its index becomes 1 before the effect runs.
        assertEquals(
            true,
            shouldRevealNotice(
                notice(),
                null,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
                firstVisibleItemIsTopAnchor = true,
            ),
        )
    }

    @Test
    fun `a retained top anchor with a nonzero offset does not scroll`() {
        assertEquals(
            false,
            shouldRevealNotice(
                notice(),
                null,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 1,
                firstVisibleItemIsTopAnchor = true,
            ),
        )
    }

    @Test
    fun `an item at index one that is not the top anchor does not scroll`() {
        assertEquals(
            false,
            shouldRevealNotice(
                notice(),
                null,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun `an existing notice does not scroll again when its anchor remains visible`() {
        assertEquals(
            false,
            shouldRevealNotice(
                notice(detail = "Actualizandoâ€¦"),
                notice(),
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
                firstVisibleItemIsTopAnchor = true,
            ),
        )
    }

    @Test
    fun `a notice whose content changes while already showing is not a new appearance`() {
        val previous = notice(detail = "Mostrando lo guardado hace menos de un minuto.")
        val current = notice(detail = "Actualizando…")
        assertEquals(
            false,
            shouldRevealNotice(current, previous, firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0),
        )
    }

    @Test
    fun `a notice disappearing does not scroll`() {
        assertEquals(
            false,
            shouldRevealNotice(null, notice(), firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0),
        )
    }

    @Test
    fun `no notice before or after does not scroll`() {
        assertEquals(false, shouldRevealNotice(null, null, firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0))
    }

    @Test
    fun `the first emission with a notice already present and the list at rest reveals it`() {
        // previousStaleness starts null (the remember default) on the very first composition.
        assertEquals(
            true,
            shouldRevealNotice(notice(), null, firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0),
        )
    }
}
