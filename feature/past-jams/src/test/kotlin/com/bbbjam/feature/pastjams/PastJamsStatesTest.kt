package com.bbbjam.feature.pastjams

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
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state table and the row mapping of `docs/specs/past-jams-list.md` (Technical Approach 3 and
 * 4), through the pure mapping. Texts are the `UiText` (resource id and arguments) that the approved
 * copy table (C1, P1) maps to.
 */
class PastJamsStatesTest {
    private val title = UiText.res(R.string.past_jams_title)

    /** 5 October 2026, 12:00 in Buenos Aires. */
    private val now = Instant.parse("2026-10-05T15:00:00Z")
    private val threeHoursAgo = now.minus(Duration.ofHours(3))

    private val fetched = Freshness(threeHoursAgo, lastFailure = null, isRefreshing = false)

    private val loading = PastJamsUiModel.Loading(title, UiText.res(R.string.past_jams_loading))

    private val offlineTitle = UiText.res(CoreR.string.list_state_notice_offline)
    private val offlineMessage = UiText.res(CoreR.string.list_state_error_offline)
    private val otherMessage = UiText.res(CoreR.string.list_state_error_other)

    private fun failed(message: UiText) = PastJamsUiModel.Failed(
        title,
        ListErrorUiModel(
            UiText.res(R.string.past_jams_load_failed),
            message,
            UiText.res(CoreR.string.list_state_retry),
            EventHandler {},
        ),
    )

    private val empty = EmptyStateUiModel(
        UiText.res(R.string.past_jams_empty_title),
        UiText.res(R.string.past_jams_empty_message),
    )

    private fun notice(
        title: UiText,
        detail: UiText = UiText.res(
            CoreR.string.list_state_showing_saved,
            UiText.plural(CoreR.plurals.list_state_age_hours, 3),
        ),
        retry: UiText? = UiText.res(CoreR.string.list_state_retry),
    ) = StalenessNoticeUiModel(title, detail, retry, EventHandler {})

    /** The first titles of the 2026-07-25 tab, then filler titles. */
    private val titles = listOf(
        "Sweet Home Chicago",
        "The Thrill Is Gone",
        "Pride and Joy",
        "Crossroads",
        "Got My Mojo Working",
        "Dust My Broom",
        "Messin' With the Kid",
        "The Score",
        "Blues de Rosario",
        "Tres Palabras",
        "Café Madrid",
        "Blues del Equipaje",
        "Walking Thru the Park",
    )

    /** "Sábado 25 de julio de 2026": the shared date label and the year. */
    private fun dateLabel(date: LocalDate) =
        UiText.res(R.string.past_jams_date_with_year, SpanishDateNames.label(date), date.year)

    private fun count(songs: Int) = UiText.plural(R.plurals.past_jams_song_count, songs)

    private val openLabel = UiText.res(R.string.past_jams_open_jam)

    private fun songs(count: Int) = titles.take(count).mapIndexed { index, title ->
        JamSong(index + 1, SongId("song-$index"), title, "Someone", Key("A"), Lineup.default())
    }

    private fun jam(
        date: String,
        setlist: Setlist = Setlist.Available(songs(1)),
        status: JamStatus = JamStatus.PUBLISHED,
        venue: String = "X",
    ) = Jam(LocalDate.parse(date), LocalTime.of(21, 0), venue, status, setlist)

    private fun model(past: List<Jam>, freshness: Freshness = fetched, upcoming: Jam? = null) =
        JamsSnapshot(upcoming, past, freshness).toUiModel(now = now)

    private fun onlyRow(past: Jam): PastJamRowUiModel = (model(listOf(past)) as PastJamsUiModel.Jams).rows.single()

    @Test
    fun `rows are newest first whatever the repository order`() {
        val rows = (model(listOf(jam("2026-05-30"), jam("2026-07-25"), jam("2026-06-27"))) as PastJamsUiModel.Jams).rows
        assertEquals(
            listOf(LocalDate.of(2026, 7, 25), LocalDate.of(2026, 6, 27), LocalDate.of(2026, 5, 30)),
            rows.map { it.date },
        )
    }

    @Test
    fun `a 13-song jam shows date with year, venue, count and three titles then the rest`() {
        assertEquals(
            PastJamRowUiModel(
                date = LocalDate.of(2026, 7, 25),
                dateLabel = dateLabel(LocalDate.of(2026, 7, 25)),
                venue = "X",
                summary = PastJamSummary.Songs(
                    count(13),
                    UiText.res(
                        R.string.past_jams_hook_more,
                        "Sweet Home Chicago, The Thrill Is Gone, Pride and Joy",
                        10,
                    ),
                ),
                openLabel = openLabel,
                events = EventHandler {},
            ),
            onlyRow(jam("2026-07-25", Setlist.Available(songs(13)))),
        )
    }

    @Test
    fun `short setlists say 1 tema and drop the tail when nothing is left`() {
        assertEquals(
            PastJamSummary.Songs(count(1), UiText.raw("Sweet Home Chicago")),
            onlyRow(jam("2026-07-25", Setlist.Available(songs(1)))).summary,
        )
        assertEquals(
            PastJamSummary.Songs(
                count(3),
                UiText.raw("Sweet Home Chicago, The Thrill Is Gone, Pride and Joy"),
            ),
            onlyRow(jam("2026-07-25", Setlist.Available(songs(3)))).summary,
        )
        assertEquals(
            PastJamSummary.Songs(
                count(4),
                UiText.res(R.string.past_jams_hook_more, "Sweet Home Chicago, The Thrill Is Gone, Pride and Joy", 1),
            ),
            onlyRow(jam("2026-07-25", Setlist.Available(songs(4)))).summary,
        )
    }

    @Test
    fun `dropped rows are not counted`() {
        assertEquals(
            PastJamSummary.Songs(count(2), UiText.raw("Sweet Home Chicago, The Thrill Is Gone")),
            onlyRow(jam("2026-07-25", Setlist.Available(songs(2), droppedRows = 3))).summary,
        )
    }

    @Test
    fun `a past created jam, an unreadable list and an empty list each get a row with one line, never 0 temas`() {
        val draft = onlyRow(jam("2026-08-29", Setlist.Withheld, JamStatus.CREATED, venue = "La Macanuda"))
        assertEquals(
            PastJamRowUiModel(
                LocalDate.of(2026, 8, 29),
                dateLabel(LocalDate.of(2026, 8, 29)),
                "La Macanuda",
                PastJamSummary.NotShown(UiText.res(R.string.past_jams_setlist_not_published)),
                openLabel = null,
                events = EventHandler {},
            ),
            draft,
        )
        assertEquals(
            PastJamSummary.NotShown(UiText.res(R.string.past_jams_setlist_unavailable)),
            onlyRow(jam("2026-07-25", Setlist.Unavailable(SetlistProblem.MISSING_TAB))).summary,
        )
        assertEquals(
            PastJamSummary.NotShown(UiText.res(R.string.past_jams_empty_setlist)),
            onlyRow(jam("2026-07-25", Setlist.Available(emptyList()))).summary,
        )
    }

    @Test
    fun `the upcoming jam is never listed`() {
        val upcoming = jam("2026-10-31", venue = "Próxima")
        assertEquals(
            PastJamsUiModel.Empty(title, empty, staleness = null),
            model(past = emptyList(), upcoming = upcoming),
        )
        val rows = (model(listOf(jam("2026-07-25")), upcoming = upcoming) as PastJamsUiModel.Jams).rows
        assertEquals(listOf(LocalDate.of(2026, 7, 25)), rows.map { it.date })
    }

    @Test
    fun `nothing read yet is the skeleton`() {
        assertEquals(loading, model(emptyList(), Freshness(null, lastFailure = null, isRefreshing = true)))
        assertEquals(loading, model(emptyList(), Freshness(null, lastFailure = null, isRefreshing = false)))
    }

    @Test
    fun `a failed read with nothing fetched is the error, offline or not, and the skeleton while retrying`() {
        assertEquals(
            failed(offlineMessage),
            model(emptyList(), Freshness(null, DataFailure.Offline, isRefreshing = false)),
        )
        assertEquals(
            failed(otherMessage),
            model(emptyList(), Freshness(null, DataFailure.Service("internal"), isRefreshing = false)),
        )
        assertEquals(loading, model(emptyList(), Freshness(null, DataFailure.Offline, isRefreshing = true)))
    }

    @Test
    fun `fetched with no past jam is the empty block`() {
        assertEquals(PastJamsUiModel.Empty(title, empty, staleness = null), model(emptyList()))
    }

    @Test
    fun `the notice sits over cached rows and the empty block exactly when the latest refresh failed`() {
        val offline = Freshness(threeHoursAgo, DataFailure.Offline, isRefreshing = false)
        val withRows = model(listOf(jam("2026-07-25")), offline) as PastJamsUiModel.Jams
        assertEquals(notice(offlineTitle), withRows.staleness)
        assertEquals(
            PastJamsUiModel.Empty(title, empty, notice(UiText.res(CoreR.string.list_state_notice_other))),
            model(emptyList(), Freshness(threeHoursAgo, DataFailure.Service("internal"), isRefreshing = false)),
        )
        val refreshing = model(listOf(jam("2026-07-25")), offline.copy(isRefreshing = true)) as PastJamsUiModel.Jams
        assertEquals(
            notice(offlineTitle, UiText.res(CoreR.string.list_state_refreshing), retry = null),
            refreshing.staleness,
        )
    }

    @Test
    fun `old data with no failure draws no notice`() {
        val old = Freshness(now.minus(Duration.ofDays(40)), lastFailure = null, isRefreshing = false)
        assertNull((model(listOf(jam("2026-07-25")), old) as PastJamsUiModel.Jams).staleness)
    }

    @Test
    fun `every state carries the title`() {
        assertEquals(title, model(emptyList()).title)
        assertEquals(title, model(listOf(jam("2026-07-25"))).title)
    }

    @Test
    fun `only a row with songs opens, and Open calls onOpenJam with its date`() {
        val opened = mutableListOf<LocalDate>()
        val past = listOf(
            jam("2026-07-25"),
            jam("2026-06-27", Setlist.Unavailable(SetlistProblem.MISSING_TAB)),
            jam("2026-05-30", Setlist.Available(emptyList())),
            jam("2026-04-25", Setlist.Withheld, JamStatus.CREATED),
        )
        val rows = (
            JamsSnapshot(null, past, fetched).toUiModel(now, onOpenJam = {
                opened += it
            }) as PastJamsUiModel.Jams
            )
            .rows
        assertEquals(listOf(openLabel, null, null, null), rows.map { it.openLabel })
        rows.first().events(PastJamRowUiModel.Event.Open)
        assertEquals(listOf(LocalDate.of(2026, 7, 25)), opened)
    }

    @Test
    fun `a past BORRADOR jam is in neither the rows nor the empty state, whatever its songs`() {
        val hidden = jam("2026-08-29", Setlist.Available(songs(3)), JamStatus.DRAFT)
        val shown = jam("2026-07-25")

        val rows = (model(listOf(hidden, shown)) as PastJamsUiModel.Jams).rows

        assertEquals(1, rows.size)
        assertTrue(model(listOf(hidden)) is PastJamsUiModel.Empty)
    }

    @Test
    fun `a past created jam whose songs reached the app is still the not-published line and does not open`() {
        val row = onlyRow(jam("2026-08-29", Setlist.Available(songs(3)), JamStatus.CREATED))
        assertEquals(PastJamSummary.NotShown(UiText.res(R.string.past_jams_setlist_not_published)), row.summary)
        assertNull(row.openLabel)
    }
}
