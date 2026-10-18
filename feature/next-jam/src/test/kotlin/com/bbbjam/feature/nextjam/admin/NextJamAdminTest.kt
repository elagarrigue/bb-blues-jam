package com.bbbjam.feature.nextjam.admin

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.add.SetlistAdd
import com.bbbjam.core.data.testing.FakeAdminSession
import com.bbbjam.core.data.testing.FakeJamsRepository
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
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.filter.FilterChipUiModel
import com.bbbjam.core.ui.lineup.LineupLineEvent
import com.bbbjam.core.ui.state.EmptyStateUiModel
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.nextjam.AddFailureUiModel
import com.bbbjam.feature.nextjam.AddSongActionUiModel
import com.bbbjam.feature.nextjam.FakeSetlistRepository
import com.bbbjam.feature.nextjam.NextJamPresenter
import com.bbbjam.feature.nextjam.NextJamUiModel
import com.bbbjam.feature.nextjam.PendingRowUiModel
import com.bbbjam.feature.nextjam.R
import com.bbbjam.feature.nextjam.RemovalUiModel
import com.bbbjam.feature.nextjam.SetlistUiModel
import com.bbbjam.feature.nextjam.SongRowAdminUiModel
import com.bbbjam.feature.nextjam.chipName
import com.bbbjam.feature.nextjam.sources
import com.bbbjam.feature.nextjam.toUiModel
import com.bbbjam.feature.nextjam.writes
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `admin-add-song-to-setlist` Part B: the admin's layer on Próxima jam. The texts are
 * `UiText` values written out with their resource ids (C1).
 */
class NextJamAdminTest {
    private val repository = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val setlist = FakeSetlistRepository()

    /** 2 October 2026, 12:00 in Buenos Aires. */
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
    private val today = LocalDate.of(2026, 10, 2)
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    private val bassOnly = Lineup(listOf(Slot(Instrument.BASS)))
    private val redHouse = JamSong(1, SongId("red-house"), "Red House", "Jimi Hendrix", Key("Bb"), Lineup.default())
    private val boogie = JamSong(2, SongId("boogie"), "Boogie", "Someone", Key("E"), bassOnly)

    private fun jam(setlist: Setlist, status: JamStatus = JamStatus.DRAFT) =
        Jam(date = jamDate, startTime = LocalTime.of(21, 0), venue = "La Macanuda", status = status, setlist = setlist)

    private fun snapshot(upcoming: Jam?) = JamsSnapshot(upcoming = upcoming, past = emptyList(), freshness = fetched)

    private fun presenter() = NextJamPresenter(repository, calendar, session, setlist.writes(), setlist.sources())

    private fun add(id: Long, state: SetlistAdd.State, date: LocalDate = jamDate, title: String = "Crossroads") =
        SetlistAdd(id, date, SongId("crossroads"), title, "Eric Clapton", Key("A"), state)

    private fun adminState(
        adds: List<SetlistAdd> = emptyList(),
        onAdd: (LocalDate) -> Unit = {},
        onDismiss: (Long) -> Unit = {},
    ) = AdminState(adds, onAdd, onDismiss)

    private fun NextJamUiModel.asJam() = this as NextJamUiModel.Jam

    @Test
    fun `the admin sees a draft's songs with the badge, the note and Agregar tema, a musician sees no jam`() {
        val draft = snapshot(jam(Setlist.Available(listOf(redHouse, boogie))))

        val model = draft.toUiModel(today, admin = adminState()).asJam()

        assertEquals(listOf("Red House", "Boogie"), (model.setlist as SetlistUiModel.Songs).rows.map { it.title })
        val admin = checkNotNull(model.admin)
        assertEquals(UiText.res(CoreR.string.jam_status_draft), admin.status.badge.label)
        assertEquals(UiText.res(R.string.next_jam_note_draft), admin.status.note)
        assertEquals(UiText.res(R.string.next_jam_status_hint), admin.status.hint)
        assertEquals(UiText.res(R.string.next_jam_add_song), admin.addSong?.label)
        assertEquals(emptyList<PendingRowUiModel>(), admin.pending)
        assertEquals(emptyList<AddFailureUiModel>(), admin.failures)

        // D-26: a BORRADOR jam does not exist for a musician.
        assertTrue(draft.toUiModel(today) is NextJamUiModel.NoUpcomingJam)
    }

    private fun NextJamUiModel.Jam.onlyRow() = (setlist as SetlistUiModel.Songs).rows.single()

    @Test
    fun `an admin row carries only the list-level actions, a musician row none`() {
        val published = snapshot(jam(Setlist.Available(listOf(redHouse, boogie)), JamStatus.PUBLISHED))
        val admin = published.toUiModel(today, admin = adminState()).asJam()
        val rows = (admin.setlist as SetlistUiModel.Songs).rows
        val first = checkNotNull(rows.first().admin)
        assertTrue(first.removal is RemovalUiModel.Idle)
        assertEquals(UiText.res(R.string.next_jam_move_up), first.move?.up?.label)
        assertFalse(checkNotNull(first.move).up.enabled)
        assertTrue(checkNotNull(first.move).down.enabled)
        assertNull(first.saveStatus)
        // The song-level controls live in the song detail: the row's admin part has nothing else.
        val fields = SongRowAdminUiModel::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .map { it.name }.toSet()
        assertEquals(setOf("removal", "move", "saveStatus"), fields)
        val musician = published.toUiModel(today).asJam()
        assertTrue((musician.setlist as SetlistUiModel.Songs).rows.all { it.admin == null })
    }

    @Test
    fun `a published jam shows its badge, and bad setlists hide the add button`() {
        val published = snapshot(jam(Setlist.Available(listOf(redHouse)), JamStatus.PUBLISHED))
            .toUiModel(today, admin = adminState()).asJam()
        assertEquals(UiText.res(CoreR.string.jam_status_published), published.admin?.status?.badge?.label)
        assertEquals(true, published.admin?.status?.badge?.isPublished)
        assertEquals(UiText.res(R.string.next_jam_note_published), published.admin?.status?.note)
        assertEquals(UiText.res(R.string.next_jam_add_song), published.admin?.addSong?.label)

        val withheld = snapshot(jam(Setlist.Withheld)).toUiModel(today, admin = adminState()).asJam()
        assertEquals(SetlistUiModel.Withheld::class, withheld.setlist::class)
        assertEquals(UiText.res(CoreR.string.jam_status_draft), withheld.admin?.status?.badge?.label)
        assertNull(withheld.admin?.addSong)

        val unreadable = snapshot(jam(Setlist.Unavailable(SetlistProblem.INVALID_TAB)))
            .toUiModel(today, admin = adminState()).asJam()
        assertNull(unreadable.admin?.addSong)
    }

    @Test
    fun `the admin's empty draft has the admin empty copy and the button, a musician's empty list keeps its copy`() {
        val empty = snapshot(jam(Setlist.Available(emptyList()), JamStatus.PUBLISHED))

        val admin = empty.toUiModel(today, admin = adminState()).asJam()
        assertEquals(
            SetlistUiModel.Empty(
                EmptyStateUiModel(
                    UiText.res(R.string.next_jam_admin_empty_title),
                    UiText.res(R.string.next_jam_admin_empty_message),
                ),
            ),
            admin.setlist,
        )
        assertEquals(UiText.res(R.string.next_jam_add_song), admin.admin?.addSong?.label)

        val musician = empty.toUiModel(today).asJam()
        assertEquals(
            UiText.res(R.string.next_jam_empty_message),
            (musician.setlist as SetlistUiModel.Empty).empty.message,
        )
    }

    @Test
    fun `with no upcoming jam the admin is sent to the Jams tab and gets no button, a musician gets no hint`() {
        val none = snapshot(null)

        val admin = none.toUiModel(today, admin = adminState()) as NextJamUiModel.NoUpcomingJam
        assertEquals(UiText.res(R.string.next_jam_admin_no_upcoming), admin.adminHint)
        assertNull((none.toUiModel(today) as NextJamUiModel.NoUpcomingJam).adminHint)
    }

    @Test
    fun `sending adds are pending rows and failed ones are cards, for this jam only, and the filter hides neither`() {
        val adds = listOf(
            add(1, SetlistAdd.State.Sending),
            add(2, SetlistAdd.State.Failed(WriteOutcome.AccessRefused), title = "Red House"),
            add(3, SetlistAdd.State.Sending, date = jamDate.plusDays(28)),
        )
        val model = snapshot(jam(Setlist.Available(listOf(redHouse, boogie))))
            .toUiModel(today, filter = setOf(Instrument.HARMONICA), admin = adminState(adds)).asJam()

        val admin = checkNotNull(model.admin)
        assertEquals(listOf(PendingRowUiModel(1, "Crossroads", UiText.res(R.string.next_jam_adding))), admin.pending)
        val failure = admin.failures.single()
        assertEquals(2L, failure.id)
        assertEquals(UiText.res(R.string.next_jam_add_failed, "Red House"), failure.title)
        assertEquals(UiText.res(CoreR.string.write_failure_access_refused), failure.message)
        assertEquals(UiText.res(R.string.next_jam_close), failure.dismissLabel)
        // The harmonica filter hides Boogie (bass only), never the admin's entries.
        assertEquals(listOf("Red House"), (model.setlist as SetlistUiModel.Songs).rows.map { it.title })
    }

    @Test
    fun `every outcome and code has its approved message`() {
        val expected = mapOf(
            WriteOutcome.AccessRefused to UiText.res(CoreR.string.write_failure_access_refused),
            WriteOutcome.Offline to UiText.res(CoreR.string.write_failure_offline),
            WriteOutcome.Unavailable to UiText.res(CoreR.string.write_failure_unavailable),
            WriteOutcome.Rejected("song_already_in_setlist") to UiText.res(R.string.next_jam_failure_already_listed),
            WriteOutcome.Rejected("unknown_song") to UiText.res(CoreR.string.write_failure_not_in_catalog),
            WriteOutcome.Rejected("unknown_jam") to UiText.res(CoreR.string.write_failure_jam_changed),
            WriteOutcome.Rejected("jam_not_editable") to UiText.res(CoreR.string.write_failure_jam_changed),
            WriteOutcome.Rejected("duplicate_date") to UiText.res(CoreR.string.write_failure_jam_changed),
            WriteOutcome.Rejected("missing_header") to UiText.res(CoreR.string.write_failure_sheet_refused),
            WriteOutcome.Rejected("invalid_key") to UiText.res(CoreR.string.write_failure_sheet_refused),
        )
        expected.forEach { (outcome, message) -> assertEquals(outcome.toString(), message, failureMessage(outcome)) }
    }

    @Test
    fun `Agregar tema opens the picker for the jam, and Cerrar dismisses that entry only`() {
        val opened = mutableListOf<LocalDate>()
        val dismissed = mutableListOf<Long>()
        val adds = listOf(add(7, SetlistAdd.State.Failed(WriteOutcome.Offline)))
        val admin = checkNotNull(
            snapshot(jam(Setlist.Available(listOf(redHouse))))
                .toUiModel(today, admin = adminState(adds, { opened += it }, { dismissed += it })).asJam().admin,
        )

        checkNotNull(admin.addSong).events(AddSongActionUiModel.Event.Open)
        admin.failures.single().events(AddFailureUiModel.Event.Dismiss)

        assertEquals(listOf(jamDate), opened)
        assertEquals(listOf(7L), dismissed)
    }

    @Test
    fun `through the presenter, the admin layer follows the flag and the adds, and Cerrar reaches the repository`() =
        runTest {
            val opened = mutableListOf<LocalDate>()
            moleculeFlow(RecompositionMode.Immediate) {
                presenter().present(NextJamPresenter.Params(onAddSong = { opened += it }))
            }.test {
                repository.snapshots.emit(snapshot(jam(Setlist.Available(listOf(redHouse, boogie)))))
                var model = expectMostRecentItem().asJam()
                assertEquals(
                    listOf("Red House", "Boogie"),
                    (model.setlist as SetlistUiModel.Songs).rows.map {
                        it.title
                    },
                )
                assertEquals(UiText.res(CoreR.string.jam_status_draft), model.admin?.status?.badge?.label)

                setlist.adds.value = listOf(add(4, SetlistAdd.State.Failed(WriteOutcome.Rejected("busy"))))
                model = awaitItem().asJam()
                checkNotNull(model.admin).failures.single().events(AddFailureUiModel.Event.Dismiss)
                checkNotNull(model.admin?.addSong).events(AddSongActionUiModel.Event.Open)
                assertEquals(listOf("Add" to 4L), setlist.dismissCalls)
                assertEquals(listOf(4L), setlist.dismissed)
                assertEquals(listOf(jamDate), opened)
                assertEquals(emptyList<Long>(), awaitItem().asJam().admin?.failures?.map { it.id })

                // Logging out hides the BORRADOR jam from the same cache altogether (D-26).
                session.isAdmin.value = false
                assertTrue(awaitItem() is NextJamUiModel.NoUpcomingJam)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `turning admin on asks for one refresh, a second login another, and a musician none`() = runTest {
        session.isAdmin.value = false
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(jam(Setlist.Withheld)))
            expectMostRecentItem()
            assertEquals(0, repository.refreshCalls)

            session.isAdmin.value = true
            expectMostRecentItem()
            assertEquals(1, repository.refreshCalls)

            // An unrelated emission does not refresh again.
            repository.snapshots.emit(snapshot(jam(Setlist.Available(listOf(redHouse)))))
            expectMostRecentItem()
            assertEquals(1, repository.refreshCalls)

            session.isAdmin.value = false
            expectMostRecentItem()
            session.isAdmin.value = true
            expectMostRecentItem()
            assertEquals(2, repository.refreshCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the filter bar of the admin's list toggles as before`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(jam(Setlist.Available(listOf(redHouse, boogie)))))
            val model = expectMostRecentItem().asJam()
            checkNotNull((model.setlist as SetlistUiModel.Songs).filterBar)
                .chips.single { it.label.chipName() == "Armónica" }.events(FilterChipUiModel.Event.Toggle)
            val filtered = awaitItem().asJam()
            assertEquals(listOf("Red House"), (filtered.setlist as SetlistUiModel.Songs).rows.map { it.title })
            assertEquals(UiText.res(R.string.next_jam_add_song), filtered.admin?.addSong?.label)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
