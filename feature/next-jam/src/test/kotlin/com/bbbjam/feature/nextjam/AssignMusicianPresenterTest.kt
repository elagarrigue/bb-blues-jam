package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.nav.BackUiModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignMusicianPresenterTest {
    private val jams = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val setlist = FakeSetlistRepository()
    private val date = LocalDate.of(2026, 10, 31)
    private val songId = SongId("crossroads")
    private val ordinal = SlotPosition(2)

    @Test
    fun `shows a fixed slot and invalid or blank input cannot submit`() = runTest {
        val presenter = presenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present(AssignMusicianPresenter.Params(date, songId, Instrument.GUITAR, ordinal, {}, {}))
        }.test {
            jams.snapshots.emit(snapshot())
            val content = expectMostRecentItem() as AssignMusicianUiModel.Content
            assertEquals("Anotar músico", content.title)
            assertEquals("Crossroads", content.songTitle)
            assertEquals("Guitarra 2", content.slotLabel)
            assertEquals(2, content.ordinal)
            assertFalse(content.submitEnabled)
            content.events(AssignMusicianUiModel.Content.Event.Submit)
            assertTrue(setlist.assignmentCalls.isEmpty())

            content.events(AssignMusicianUiModel.Content.Event.NameChanged("Tincho;"))
            val invalid = expectMostRecentItem() as AssignMusicianUiModel.Content
            assertEquals(AssignMusicianCopy.INVALID_NAME, invalid.validationMessage)
            invalid.events(AssignMusicianUiModel.Content.Event.Submit)
            assertTrue(setlist.assignmentCalls.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `valid normalized name submits the fixed slot once and closes immediately`() = runTest {
        var done = 0
        val presenter = presenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present(AssignMusicianPresenter.Params(date, songId, Instrument.GUITAR, ordinal, {}, { done++ }))
        }.test {
            jams.snapshots.emit(snapshot())
            val initial = expectMostRecentItem() as AssignMusicianUiModel.Content
            initial.events(AssignMusicianUiModel.Content.Event.NameChanged("  Tincho   Pérez  "))
            val edited = expectMostRecentItem() as AssignMusicianUiModel.Content
            assertTrue(edited.submitEnabled)
            edited.events(AssignMusicianUiModel.Content.Event.Submit)
            edited.events(AssignMusicianUiModel.Content.Event.Submit)
            assertEquals(1, done)
            assertEquals(
                listOf(
                    FakeSetlistRepository.AssignmentCall(
                        date,
                        songId,
                        Instrument.GUITAR,
                        ordinal,
                        requireNotNull(MusicianName.parseOrNull("Tincho Pérez")),
                    ),
                ),
                setlist.assignmentCalls,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping a suggestion submits that spelling once`() = runTest {
        var done = 0
        val presenter = presenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present(AssignMusicianPresenter.Params(date, songId, Instrument.GUITAR, ordinal, {}, { done++ }))
        }.test {
            jams.snapshots.emit(snapshot(currentName = "María Sol"))
            val content = expectMostRecentItem() as AssignMusicianUiModel.Content
            assertEquals(listOf("María Sol"), content.currentSection.rows.map { it.name })
            content.currentSection.rows.single().events(SuggestionRowUiModel.Event.Select)
            assertEquals(1, done)
            assertEquals("María Sol", setlist.assignmentCalls.single().name.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `slot is unavailable for musicians filled slots and assignments already sending`() {
        val params = AssignMusicianPresenter.Params(date, songId, Instrument.GUITAR, ordinal, {}, {})
        assertNull(assignableSong(false, snapshot(), emptyList(), params))
        assertNull(assignableSong(true, snapshot(filledOrdinal = true), emptyList(), params))
        assertNull(
            assignableSong(
                true,
                snapshot(),
                listOf(
                    com.bbbjam.core.data.setlist.Assignment(
                        1,
                        date,
                        songId,
                        "Crossroads",
                        Instrument.GUITAR,
                        ordinal,
                        requireNotNull(MusicianName.parseOrNull("Ana")),
                        com.bbbjam.core.data.setlist.Assignment.State.Sending,
                    ),
                ),
                params,
            ),
        )
        assertEquals("Crossroads", requireNotNull(assignableSong(true, snapshot(), emptyList(), params)).title)
    }

    @Test
    fun `back uses the current callback and sends no assignment`() = runTest {
        var backs = 0
        val presenter = presenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present(AssignMusicianPresenter.Params(date, songId, Instrument.GUITAR, ordinal, { backs++ }, {}))
        }.test {
            val loading = awaitItem()
            loading.back.events(BackUiModel.Event.Back)
            assertEquals(1, backs)
            assertTrue(setlist.assignmentCalls.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun presenter() = AssignMusicianPresenter(jams, session, setlist)

    private fun snapshot(currentName: String? = null, filledOrdinal: Boolean = false): JamsSnapshot {
        val lineup = Lineup(
            listOf(
                Slot(Instrument.GUITAR, currentName.takeUnless { filledOrdinal }),
                Slot(Instrument.GUITAR, if (filledOrdinal) "Ana" else null),
                Slot(Instrument.BASS),
            ),
        )
        val jam = Jam(
            date,
            LocalTime.of(21, 0),
            "La Macanuda",
            JamStatus.PUBLISHED,
            Setlist.Available(listOf(JamSong(1, songId, "Crossroads", "Cream", Key("A"), lineup))),
        )
        return JamsSnapshot(jam, emptyList(), Freshness(Instant.parse("2026-10-01T00:00:00Z"), null, false))
    }
}
