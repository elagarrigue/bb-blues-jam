package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SetlistProblem
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.filter.FilterChipUiModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * `unpublished-setlist-state`: a draft upcoming jam exposes no song to a musician, whether its
 * setlist arrived withheld (scenario 1) or available (scenario 2), through the pure mapping and
 * through the presenter. The copy is written out from the approved table (C1), never read from
 * [NextJamCopy].
 */
class NextJamDraftTest {
    private val repository = FakeJamsRepository()

    /** 2 October 2026, 12:00 in Buenos Aires. */
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
    private val today = LocalDate.of(2026, 10, 2)
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    private val loading = NextJamUiModel.Loading("Cargando la próxima jam")
    private val header = JamHeaderUiModel("Sábado 31 de octubre · 21:00", "La Macanuda", "En 29 días")

    private val withheldDraft = NextJamUiModel.Jam(
        header,
        SetlistUiModel.Withheld(
            DraftSetlistUiModel(
                "En preparación",
                "La lista se está armando",
                "Cuando la organización la publique, vas a ver acá los temas, las tonalidades y los cupos libres.",
            ),
        ),
        staleness = null,
    )

    /** A song whose title, artist, key and musician names must never reach a draft's model. */
    private val secretSong = JamSong(
        position = 1,
        songId = SongId("secret-song"),
        title = "Secret Draft Title",
        artist = "Secret Artist",
        key = Key("F#m"),
        lineup = Lineup(listOf(Slot(Instrument.GUITAR, "SecretMusician"), Slot(Instrument.BASS))),
        extraParticipants = listOf(ExtraParticipant("SecretExtra", "saxo")),
    )

    private val otherSong =
        JamSong(2, SongId("crossroads"), "Secret Second Title", "Someone", Key("A"), Lineup.default())

    private val secrets =
        listOf("Secret Draft Title", "Secret Second Title", "Secret Artist", "F#m", "SecretMusician", "SecretExtra")

    private fun NextJamUiModel.assertNoSecret() {
        secrets.forEach { secret -> assertFalse("$secret leaked into $this", toString().contains(secret)) }
    }

    private fun jam(setlist: Setlist, status: JamStatus) =
        Jam(date = jamDate, startTime = LocalTime.of(21, 0), venue = "La Macanuda", status = status, setlist = setlist)

    private fun snapshot(upcoming: Jam) = JamsSnapshot(upcoming = upcoming, past = emptyList(), freshness = fetched)

    private fun presenter() = NextJamPresenter(repository, calendar)

    @Test
    fun `scenario 1, a withheld draft is the header and the draft card, no rows and no bar`() = runTest {
        assertEquals(withheldDraft, snapshot(jam(Setlist.Withheld, JamStatus.DRAFT)).toUiModel(today))

        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Withheld, JamStatus.DRAFT)))
            assertEquals(withheldDraft, awaitItem())
        }
    }

    @Test
    fun `scenario 2, a draft with available songs exposes none of them`() = runTest {
        val draft = jam(Setlist.Available(listOf(secretSong, otherSong)), JamStatus.DRAFT)
        val model = snapshot(draft).toUiModel(today)
        assertEquals(withheldDraft, model)
        model.assertNoSecret()
        // Expansion and filter state have nothing to apply to.
        val expanded = ExpandedRows(jamDate, setOf(1, 2))
        val withState = snapshot(draft).toUiModel(today, expanded, filter = setOf(Instrument.BASS))
        assertEquals(withheldDraft, withState)

        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(draft))
            val emitted = awaitItem()
            assertEquals(withheldDraft, emitted)
            emitted.assertNoSecret()
        }
    }

    @Test
    fun `a filter selected before a draft emission still yields no rows and no bar`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Available(listOf(otherSong)), JamStatus.PUBLISHED)))
            val published = awaitItem() as NextJamUiModel.Jam
            checkNotNull((published.setlist as SetlistUiModel.Songs).filterBar)
                .chips.single { it.label == "Bajo" }.events(FilterChipUiModel.Event.Toggle)
            val filtered = awaitItem() as NextJamUiModel.Jam
            assertEquals(
                listOf("Bajo"),
                checkNotNull((filtered.setlist as SetlistUiModel.Songs).filterBar).chips.filter { it.isSelected }
                    .map { it.label },
            )

            // The same jam turns into a draft: the card, nothing else.
            val draftJam = jam(Setlist.Available(listOf(secretSong, otherSong)), JamStatus.DRAFT)
            repository.snapshots.emit(snapshot(draftJam))
            val draft = awaitItem()
            assertEquals(withheldDraft, draft)
            draft.assertNoSecret()
        }
    }

    private val unavailable = NextJamUiModel.Jam(
        header,
        SetlistUiModel.Unavailable("No se pudo leer la lista de temas de esta jam. Avisale a la organización."),
        staleness = null,
    )

    private fun NextJamUiModel.titles() =
        ((this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows.map { it.title }

    @Test
    fun `scenario 5, published setlists are unchanged and an unreadable one is one line`() = runTest {
        SetlistProblem.entries.forEach { problem ->
            assertEquals(
                unavailable,
                snapshot(jam(Setlist.Unavailable(problem), JamStatus.PUBLISHED)).toUiModel(today),
            )
        }
        val published = snapshot(jam(Setlist.Available(listOf(otherSong)), JamStatus.PUBLISHED)).toUiModel(today)
        assertEquals(listOf("Secret Second Title"), published.titles())
        assertEquals(header, (published as NextJamUiModel.Jam).header)

        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            assertEquals(loading, awaitItem())
            repository.snapshots.emit(snapshot(jam(Setlist.Withheld, JamStatus.DRAFT)))
            assertEquals(withheldDraft, awaitItem())
            repository.snapshots.emit(
                snapshot(jam(Setlist.Unavailable(SetlistProblem.MISSING_TAB), JamStatus.PUBLISHED)),
            )
            assertEquals(unavailable, awaitItem())
            // Published again: the rows come back.
            repository.snapshots.emit(snapshot(jam(Setlist.Available(listOf(otherSong)), JamStatus.PUBLISHED)))
            assertEquals(listOf("Secret Second Title"), awaitItem().titles())
        }
    }
}
