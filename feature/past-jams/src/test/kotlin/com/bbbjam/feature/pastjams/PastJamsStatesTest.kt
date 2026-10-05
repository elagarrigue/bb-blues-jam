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
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.state.ListErrorUiModel
import com.bbbjam.core.ui.state.StalenessNoticeUiModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The state table and the row mapping of `docs/specs/past-jams-list.md` (Technical Approach 3 and
 * 4), through the pure mapping. Strings are written out from the approved copy table (C1, P1),
 * never read from [PastJamsCopy].
 */
class PastJamsStatesTest {
    private val title = "Jams anteriores"

    /** 5 October 2026, 12:00 in Buenos Aires. */
    private val now = Instant.parse("2026-10-05T15:00:00Z")
    private val threeHoursAgo = now.minus(Duration.ofHours(3))

    private val fetched = Freshness(threeHoursAgo, lastFailure = null, isRefreshing = false)

    private val loading = PastJamsUiModel.Loading(title, "Cargando las jams anteriores")

    private val offlineMessage =
        "No hay conexión y todavía no hay nada guardado. Revisá los datos o el wifi y probá de nuevo."
    private val otherMessage =
        "Algo falló al leer los datos. Probá de nuevo en un rato; si sigue fallando, avisale a la organización."

    private fun failed(message: String) = PastJamsUiModel.Failed(
        title,
        ListErrorUiModel("No pudimos cargar las jams anteriores", message, "Reintentar", EventHandler {}),
    )

    private val empty = EmptyStateUiModel(
        "Todavía no hay jams anteriores",
        "Después de cada jam, su lista queda guardada acá para que veas qué se tocó.",
    )

    private fun notice(
        title: String,
        detail: String = "Mostrando lo guardado hace 3 horas.",
        retry: String? = "Reintentar",
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
                dateLabel = "Sábado 25 de julio de 2026",
                venue = "X",
                summary = PastJamSummary.Songs(
                    "13 temas",
                    "Sweet Home Chicago, The Thrill Is Gone, Pride and Joy y 10 más",
                ),
                openLabel = "ver la lista de temas",
                events = EventHandler {},
            ),
            onlyRow(jam("2026-07-25", Setlist.Available(songs(13)))),
        )
    }

    @Test
    fun `short setlists say 1 tema and drop the tail when nothing is left`() {
        assertEquals(
            PastJamSummary.Songs("1 tema", "Sweet Home Chicago"),
            onlyRow(jam("2026-07-25", Setlist.Available(songs(1)))).summary,
        )
        assertEquals(
            PastJamSummary.Songs("3 temas", "Sweet Home Chicago, The Thrill Is Gone, Pride and Joy"),
            onlyRow(jam("2026-07-25", Setlist.Available(songs(3)))).summary,
        )
        assertEquals(
            PastJamSummary.Songs("4 temas", "Sweet Home Chicago, The Thrill Is Gone, Pride and Joy y 1 más"),
            onlyRow(jam("2026-07-25", Setlist.Available(songs(4)))).summary,
        )
    }

    @Test
    fun `dropped rows are not counted`() {
        assertEquals(
            PastJamSummary.Songs("2 temas", "Sweet Home Chicago, The Thrill Is Gone"),
            onlyRow(jam("2026-07-25", Setlist.Available(songs(2), droppedRows = 3))).summary,
        )
    }

    @Test
    fun `a past draft, an unreadable list and an empty list each get a row with one line, never 0 temas`() {
        val draft = onlyRow(jam("2026-08-29", Setlist.Withheld, JamStatus.DRAFT, venue = "La Macanuda"))
        assertEquals(
            PastJamRowUiModel(
                LocalDate.of(2026, 8, 29),
                "Sábado 29 de agosto de 2026",
                "La Macanuda",
                PastJamSummary.NotShown("La lista de esta jam no se publicó."),
                openLabel = null,
                events = EventHandler {},
            ),
            draft,
        )
        assertEquals(
            PastJamSummary.NotShown("No se pudo leer la lista de esta jam."),
            onlyRow(jam("2026-07-25", Setlist.Unavailable(SetlistProblem.MISSING_TAB))).summary,
        )
        assertEquals(
            PastJamSummary.NotShown("Esta jam no tiene temas cargados."),
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
        assertEquals(notice("Sin conexión"), withRows.staleness)
        assertEquals(
            PastJamsUiModel.Empty(title, empty, notice("No se pudo actualizar")),
            model(emptyList(), Freshness(threeHoursAgo, DataFailure.Service("internal"), isRefreshing = false)),
        )
        val refreshing = model(listOf(jam("2026-07-25")), offline.copy(isRefreshing = true)) as PastJamsUiModel.Jams
        assertEquals(notice("Sin conexión", "Actualizando…", retry = null), refreshing.staleness)
    }

    @Test
    fun `old data with no failure draws no notice`() {
        val old = Freshness(now.minus(Duration.ofDays(40)), lastFailure = null, isRefreshing = false)
        assertNull((model(listOf(jam("2026-07-25")), old) as PastJamsUiModel.Jams).staleness)
    }

    @Test
    fun `every state carries the title`() {
        assertEquals("Jams anteriores", model(emptyList()).title)
        assertEquals("Jams anteriores", model(listOf(jam("2026-07-25"))).title)
    }

    @Test
    fun `only a row with songs opens, and Open calls onOpenJam with its date`() {
        val opened = mutableListOf<LocalDate>()
        val past = listOf(
            jam("2026-07-25"),
            jam("2026-06-27", Setlist.Unavailable(SetlistProblem.MISSING_TAB)),
            jam("2026-05-30", Setlist.Available(emptyList())),
            jam("2026-04-25", Setlist.Withheld, JamStatus.DRAFT),
        )
        val rows = (
            JamsSnapshot(null, past, fetched).toUiModel(now, onOpenJam = {
                opened += it
            }) as PastJamsUiModel.Jams
            )
            .rows
        assertEquals(listOf("ver la lista de temas", null, null, null), rows.map { it.openLabel })
        rows.first().events(PastJamRowUiModel.Event.Open)
        assertEquals(listOf(LocalDate.of(2026, 7, 25)), opened)
    }

    @Test
    fun `a past draft whose songs reached the app is still the not-published line and does not open`() {
        val row = onlyRow(jam("2026-08-29", Setlist.Available(songs(3)), JamStatus.DRAFT))
        assertEquals(PastJamSummary.NotShown("La lista de esta jam no se publicó."), row.summary)
        assertNull(row.openLabel)
    }
}
