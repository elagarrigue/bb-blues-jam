package com.bbbjam.feature.nextjam

import com.bbbjam.core.data.DataFailure
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.ListErrorUiModel
import com.bbbjam.core.ui.state.StalenessNoticeUiModel
import com.bbbjam.core.ui.text.UiText
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every row of Decision 1's state table in `docs/specs/list-states.md`, through the pure mapping.
 * Texts are `UiText` values written out with their resource ids (C1).
 */
class NextJamStatesTest {
    private val today = LocalDate.of(2026, 10, 2)

    /** 2 October 2026, 12:00 in Buenos Aires. */
    private val now = Instant.parse("2026-10-02T15:00:00Z")
    private val threeHoursAgo = now.minus(Duration.ofHours(3))

    private val loading = nextJamLoading

    private val offlineMessage = UiText.res(CoreR.string.list_state_error_offline)
    private val otherMessage = UiText.res(CoreR.string.list_state_error_other)
    private val offlineTitle = UiText.res(CoreR.string.list_state_notice_offline)

    private fun failed(message: UiText) = NextJamUiModel.Failed(
        ListErrorUiModel(
            UiText.res(R.string.next_jam_load_failed),
            message,
            UiText.res(CoreR.string.list_state_retry),
            EventHandler {},
        ),
    )

    private val noUpcoming =
        EmptyStateUiModel(
            UiText.res(R.string.next_jam_no_upcoming_title),
            UiText.res(R.string.next_jam_no_upcoming_message),
        )

    private val emptySetlist = SetlistUiModel.Empty(
        EmptyStateUiModel(
            UiText.res(R.string.next_jam_empty_title),
            UiText.res(R.string.next_jam_empty_message),
        ),
    )

    private fun notice(
        title: UiText,
        detail: UiText = UiText.res(
            CoreR.string.list_state_showing_saved,
            UiText.plural(CoreR.plurals.list_state_age_hours, 3),
        ),
        retry: UiText? = UiText.res(CoreR.string.list_state_retry),
    ) = StalenessNoticeUiModel(title, detail, retry, EventHandler {})

    private val header = octoberThirtyFirstHeader

    private fun jam(setlist: Setlist, status: JamStatus = JamStatus.PUBLISHED) = Jam(
        date = LocalDate.of(2026, 10, 31),
        startTime = LocalTime.of(21, 0),
        venue = "La Macanuda",
        status = status,
        setlist = setlist,
    )

    private val song =
        JamSong(1, SongId("sweet-little-angel"), "Sweet Little Angel", "B.B. King", Key("B"), Lineup.default())

    private fun model(upcoming: Jam?, freshness: Freshness) =
        JamsSnapshot(upcoming, emptyList(), freshness).toUiModel(today, now = now)

    private val otherFailures = listOf(
        DataFailure.NotConfigured,
        DataFailure.Service("internal"),
        DataFailure.InvalidResponse("not json"),
        DataFailure.Storage("SQLiteException"),
    )

    @Test
    fun `nothing fetched and no failure, or a failure while refreshing, is loading`() {
        assertEquals(loading, model(null, Freshness(null, null, isRefreshing = false)))
        assertEquals(loading, model(null, Freshness(null, null, isRefreshing = true)))
        assertEquals(loading, model(null, Freshness(null, DataFailure.Offline, isRefreshing = true)))
        otherFailures.forEach { failure ->
            assertEquals(loading, model(null, Freshness(null, failure, isRefreshing = true)))
        }
    }

    @Test
    fun `nothing fetched and a failed read is the error, offline or other`() {
        assertEquals(failed(offlineMessage), model(null, Freshness(null, DataFailure.Offline, isRefreshing = false)))
        otherFailures.forEach { failure ->
            assertEquals(failed(otherMessage), model(null, Freshness(null, failure, isRefreshing = false)))
        }
    }

    @Test
    fun `fetched with no upcoming jam is the empty block, with the notice only after a failure`() {
        assertEquals(
            NextJamUiModel.NoUpcomingJam(noUpcoming, staleness = null),
            model(null, Freshness(threeHoursAgo, null, isRefreshing = false)),
        )
        assertEquals(
            NextJamUiModel.NoUpcomingJam(noUpcoming, notice(offlineTitle)),
            model(null, Freshness(threeHoursAgo, DataFailure.Offline, isRefreshing = false)),
        )
        assertEquals(
            NextJamUiModel.NoUpcomingJam(noUpcoming, notice(UiText.res(CoreR.string.list_state_notice_other))),
            model(null, Freshness(threeHoursAgo, DataFailure.Service("internal"), isRefreshing = false)),
        )
    }

    @Test
    fun `a published setlist with no song is the empty-setlist block under the header`() {
        assertEquals(
            NextJamUiModel.Jam(header, emptySetlist, staleness = null),
            model(jam(Setlist.Available(emptyList())), Freshness(threeHoursAgo, null, isRefreshing = false)),
        )
        assertEquals(
            NextJamUiModel.Jam(header, emptySetlist, notice(offlineTitle)),
            model(jam(Setlist.Available(emptyList())), Freshness(threeHoursAgo, DataFailure.Offline, false)),
        )
    }

    @Test
    fun `cached data older than 30 minutes without a failure draws no notice`() {
        listOf(Duration.ofMinutes(31), Duration.ofHours(3), Duration.ofDays(2)).forEach { age ->
            val stale = model(jam(Setlist.Available(listOf(song))), Freshness(now.minus(age), null, false))
            assertNull("age $age", (stale as NextJamUiModel.Jam).staleness)
        }
    }

    @Test
    fun `songs keep their rows and gain the notice when the refresh failed, refreshing has no action`() {
        val offline = model(jam(Setlist.Available(listOf(song))), Freshness(threeHoursAgo, DataFailure.Offline, false))
        assertEquals(notice(offlineTitle), (offline as NextJamUiModel.Jam).staleness)
        assertEquals(listOf("Sweet Little Angel"), (offline.setlist as SetlistUiModel.Songs).rows.map { it.title })

        val refreshing =
            model(jam(Setlist.Available(listOf(song))), Freshness(threeHoursAgo, DataFailure.Offline, true))
        assertEquals(
            notice(offlineTitle, UiText.res(CoreR.string.list_state_refreshing), retry = null),
            (refreshing as NextJamUiModel.Jam).staleness,
        )
    }

    private val withheldDraft = SetlistUiModel.Withheld(draftCard)

    @Test
    fun `a draft jam exposes no song, withheld or available, plus the notice when failing`() {
        val fresh = Freshness(threeHoursAgo, null, isRefreshing = false)
        val failing = Freshness(threeHoursAgo, DataFailure.Offline, isRefreshing = false)
        listOf(Setlist.Withheld, Setlist.Available(listOf(song))).forEach { setlist ->
            val draft = model(jam(setlist, JamStatus.CREATED), fresh)
            assertEquals(NextJamUiModel.Jam(header, withheldDraft, staleness = null), draft)
            assertFalse("a song leaked: $draft", draft.toString().contains("Sweet Little Angel"))
            // Scenario 6: offline with a cached draft, the notice above the header, then the card.
            assertEquals(
                NextJamUiModel.Jam(header, withheldDraft, notice(offlineTitle)),
                model(jam(setlist, JamStatus.CREATED), failing),
            )
        }
    }

    @Test
    fun `scenario 3, the draft card is not the empty block and shares no sentence with it`() {
        val fresh = Freshness(threeHoursAgo, null, isRefreshing = false)
        val empty = model(jam(Setlist.Available(emptyList())), fresh) as NextJamUiModel.Jam
        val draft = model(jam(Setlist.Withheld, JamStatus.CREATED), fresh) as NextJamUiModel.Jam
        assertEquals(emptySetlist, empty.setlist)
        assertEquals(withheldDraft, draft.setlist)
        // Different resources, so no sentence is shared with the empty block.
        val emptyTexts = listOf(emptySetlist.empty.title, emptySetlist.empty.message)
        listOf(draftCard.label, draftCard.title, draftCard.message).forEach { line ->
            assertFalse("$line is also in the empty block", line in emptyTexts)
        }
    }

    @Test
    fun `an unavailable setlist keeps its one line`() {
        assertEquals(
            NextJamUiModel.Jam(
                header,
                SetlistUiModel.Unavailable(UiText.res(R.string.next_jam_setlist_unavailable)),
                staleness = null,
            ),
            model(jam(Setlist.Unavailable(SetlistProblem.MISSING_TAB)), Freshness(threeHoursAgo, null, false)),
        )
    }

    @Test
    fun `a clock behind the fetch time reads as less than a minute`() {
        val ahead = model(null, Freshness(now.plus(Duration.ofMinutes(10)), DataFailure.Offline, false))
        assertEquals(
            NextJamUiModel.NoUpcomingJam(
                noUpcoming,
                notice(
                    offlineTitle,
                    UiText.res(
                        CoreR.string.list_state_showing_saved,
                        UiText.res(CoreR.string.list_state_age_under_a_minute),
                    ),
                ),
            ),
            ahead,
        )
    }

    @Test
    fun `both retry handlers call onRetry`() {
        var calls = 0
        val failedModel = JamsSnapshot(null, emptyList(), Freshness(null, DataFailure.Offline, false))
            .toUiModel(today, now = now, onRetry = { calls++ }) as NextJamUiModel.Failed
        failedModel.error.events(ListErrorUiModel.Event.Retry)
        val stale = JamsSnapshot(null, emptyList(), Freshness(threeHoursAgo, DataFailure.Offline, false))
            .toUiModel(today, now = now, onRetry = { calls++ }) as NextJamUiModel.NoUpcomingJam
        checkNotNull(stale.staleness).events(StalenessNoticeUiModel.Event.Retry)
        assertEquals(2, calls)
    }
}
