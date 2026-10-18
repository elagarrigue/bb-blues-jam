package com.bbbjam.feature.nextjam.overlay

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.add.SetlistAdd
import com.bbbjam.core.data.setlist.assign.Assignment
import com.bbbjam.core.data.setlist.clear.SlotClear
import com.bbbjam.core.data.setlist.extras.ExtraParticipantChange
import com.bbbjam.core.data.setlist.key.KeyChange
import com.bbbjam.core.data.setlist.lineup.LineupChange
import com.bbbjam.core.data.setlist.move.SetlistMove
import com.bbbjam.core.data.setlist.remove.SetlistRemove
import com.bbbjam.core.data.testing.FakeAdminSession
import com.bbbjam.core.data.testing.FakeJamsRepository
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.MusicianName
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.nextjam.AddFailureUiModel
import com.bbbjam.feature.nextjam.FakeSetlistRepository
import com.bbbjam.feature.nextjam.NextJamPresenter
import com.bbbjam.feature.nextjam.NextJamUiModel
import com.bbbjam.feature.nextjam.R
import com.bbbjam.feature.nextjam.SetlistUiModel
import com.bbbjam.feature.nextjam.admin.AdminState
import com.bbbjam.feature.nextjam.admin.RemovalState
import com.bbbjam.feature.nextjam.admin.keyFailureMessage
import com.bbbjam.feature.nextjam.keyDescription
import com.bbbjam.feature.nextjam.savingText
import com.bbbjam.feature.nextjam.sources
import com.bbbjam.feature.nextjam.toUiModel
import com.bbbjam.feature.nextjam.writes
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `admin-set-key` Part B on Próxima jam: "Cambiar tonalidad", the optimistic key (O1) and its revert.
 * The texts are `UiText` values written out with their resource ids.
 */
class NextJamKeyChangeTest {
    private val repository = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val setlist = FakeSetlistRepository()

    /** 2 October 2026, 12:00 in Buenos Aires. */
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
    private val today = LocalDate.of(2026, 10, 2)
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    private val crossroads = JamSong(1, SongId("crossroads"), "Crossroads", "Cream", Key("A"), Lineup.default())
    private val hoochie =
        JamSong(2, SongId("hoochie"), "Hoochie Coochie Man", "Muddy Waters", Key("E"), Lineup.default())

    private fun snapshot(songs: List<JamSong> = listOf(crossroads, hoochie)) = JamsSnapshot(
        upcoming = Jam(jamDate, LocalTime.of(21, 0), "La Macanuda", JamStatus.PUBLISHED, Setlist.Available(songs)),
        past = emptyList(),
        freshness = fetched,
    )

    private fun presenter() = NextJamPresenter(repository, calendar, session, setlist.writes(), setlist.sources())

    private fun NextJamUiModel.rows() = ((this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows

    private fun NextJamUiModel.row(title: String) = rows().single { it.title == title }

    private fun NextJamUiModel.failures() = checkNotNull((this as NextJamUiModel.Jam).admin).failures

    private fun change(
        id: Long,
        key: String,
        state: KeyChange.State,
        songId: String = "crossroads",
        date: LocalDate = jamDate,
    ) = KeyChange(id, date, SongId(songId), "Crossroads", Key(key), state)

    private fun admin(changes: List<KeyChange>) = AdminState(emptyList(), {}, {}, keyChanges = changes)

    @Test
    fun `a musician's rows never get the overlay or the action`() = runTest {
        session.isAdmin.value = false
        setlist.keyChanges.value = listOf(change(1, "Bb", KeyChange.State.Sending))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val row = expectMostRecentItem().row("Crossroads")
            assertEquals("A", row.key)
            assertEquals(keyDescription("A"), row.keyDescription)
            assertNull(row.admin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a sending change draws the new key with Guardando, and a failure reverts it and adds a card`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            assertEquals("A", expectMostRecentItem().row("Crossroads").key)

            setlist.keyChanges.value = listOf(change(4, "Bb", KeyChange.State.Sending))
            var model = awaitItem()
            val sending = model.row("Crossroads")
            assertEquals("Bb", sending.key)
            assertEquals(keyDescription("Bb"), sending.keyDescription)
            assertEquals(savingText, checkNotNull(sending.admin).saveStatus)
            // Only that row is overlaid.
            assertEquals("E", model.row("Hoochie Coochie Man").key)
            assertEquals(emptyList<AddFailureUiModel>(), model.failures())

            setlist.keyChanges.value = listOf(change(4, "Bb", KeyChange.State.Failed(WriteOutcome.AccessRefused)))
            model = awaitItem()
            val reverted = model.row("Crossroads")
            assertEquals("A", reverted.key)
            assertEquals(keyDescription("A"), reverted.keyDescription)
            assertNull(checkNotNull(reverted.admin).saveStatus)
            val card = model.failures().single()
            assertEquals(UiText.res(R.string.next_jam_key_failed, "Crossroads"), card.title)
            assertEquals(UiText.res(CoreR.string.write_failure_access_refused), card.message)
            assertEquals(UiText.res(R.string.next_jam_close), card.dismissLabel)
            card.events(AddFailureUiModel.Event.Dismiss)
            assertEquals(listOf(4L), setlist.dismissed)
            assertEquals(listOf("KeyChange" to 4L), setlist.dismissCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `after the server confirms, the cached key shows with no status`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            setlist.keyChanges.value = listOf(change(4, "Bb", KeyChange.State.Sending))
            assertEquals("Bb", expectMostRecentItem().row("Crossroads").key)

            // The repository updates the cache first, then removes the entry.
            repository.snapshots.emit(snapshot(listOf(crossroads.copy(key = Key("Bb")), hoochie)))
            assertEquals("Bb", awaitItem().row("Crossroads").key)
            setlist.keyChanges.value = emptyList()
            val row = awaitItem().row("Crossroads")
            assertEquals("Bb", row.key)
            assertNull(checkNotNull(row.admin).saveStatus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the latest of two sending changes wins, whatever the list order`() {
        val changes = listOf(change(7, "C", KeyChange.State.Sending), change(3, "Bb", KeyChange.State.Sending))
        assertEquals("C", snapshot().toUiModel(today, admin = admin(changes)).row("Crossroads").key)
    }

    @Test
    fun `when the later change fails after the earlier one succeeded, the row shows the earlier, confirmed key`() {
        val cached = snapshot(listOf(crossroads.copy(key = Key("Bb")), hoochie))
        val changes = listOf(change(8, "C", KeyChange.State.Failed(WriteOutcome.Offline)))
        val model = cached.toUiModel(today, admin = admin(changes))
        assertEquals("Bb", model.row("Crossroads").key)
        assertEquals(UiText.res(CoreR.string.write_failure_offline), model.failures().single().message)
    }

    @Test
    fun `changes of another jam or song never overlay`() {
        val changes = listOf(
            change(1, "Bb", KeyChange.State.Sending, date = jamDate.plusDays(28)),
            change(2, "G", KeyChange.State.Sending, songId = "hoochie"),
        )
        val model = snapshot().toUiModel(today, admin = admin(changes))
        assertEquals("A", model.row("Crossroads").key)
        assertEquals("G", model.row("Hoochie Coochie Man").key)
    }

    @Test
    fun `failed adds, removals and key changes are merged in id order`() {
        val state = AdminState(
            adds = listOf(
                SetlistAdd(
                    2,
                    jamDate,
                    SongId("red-house"),
                    "Red House",
                    "Jimi Hendrix",
                    Key("Bb"),
                    SetlistAdd.State.Failed(WriteOutcome.Unavailable),
                ),
            ),
            onAddSong = {},
            onDismissAdd = {},
            removal = RemovalState(
                listOf(
                    SetlistRemove(
                        3,
                        jamDate,
                        SongId("hoochie"),
                        "Hoochie Coochie Man",
                        SetlistRemove.State.Failed(WriteOutcome.Rejected("song_not_in_setlist")),
                    ),
                ),
            ),
            keyChanges = listOf(
                change(1, "Bb", KeyChange.State.Failed(WriteOutcome.Rejected("song_not_in_setlist"))),
                change(4, "C", KeyChange.State.Failed(WriteOutcome.Rejected("duplicate_song"))),
            ),
        )
        val failures = snapshot().toUiModel(today, admin = state).failures()
        assertEquals(listOf(1L, 2L, 3L, 4L), failures.map { it.id })
        assertEquals(
            listOf(
                UiText.res(R.string.next_jam_key_failed, "Crossroads") to
                    UiText.res(CoreR.string.write_failure_jam_changed),
                UiText.res(R.string.next_jam_add_failed, "Red House") to
                    UiText.res(CoreR.string.write_failure_unavailable),
                // The removal keeps its own wording for the same code.
                UiText.res(R.string.next_jam_remove_failed, "Hoochie Coochie Man") to
                    UiText.res(R.string.next_jam_failure_not_in_setlist),
                UiText.res(R.string.next_jam_key_failed, "Crossroads") to
                    UiText.res(R.string.next_jam_failure_duplicate_song),
            ),
            failures.map { it.title to it.message },
        )
    }

    private fun everyKindFailed(): AdminState {
        val reason = WriteOutcome.Unavailable
        val song = SongId("crossroads")
        return AdminState(
            adds = listOf(
                SetlistAdd(1, jamDate, song, "Crossroads", "Cream", Key("A"), SetlistAdd.State.Failed(reason)),
            ),
            onAddSong = {},
            onDismissAdd = setlist::dismissAdd,
            removal = RemovalState(
                listOf(SetlistRemove(2, jamDate, song, "Crossroads", SetlistRemove.State.Failed(reason))),
            ),
            moves = listOf(SetlistMove(3, jamDate, song, "Crossroads", 2, SetlistMove.State.Failed(reason))),
            onDismissRemove = setlist::dismissRemove,
            onDismissMove = setlist::dismissMove,
            overlaySources = setlist.sources(),
            keyChanges = listOf(change(4, "C", KeyChange.State.Failed(reason))),
            lineupChanges = listOf(
                LineupChange(5, jamDate, song, "Crossroads", Instrument.BASS, 1, LineupChange.State.Failed(reason)),
            ),
            assignments = listOf(
                Assignment(
                    6,
                    jamDate,
                    song,
                    "Crossroads",
                    Instrument.BASS,
                    SlotPosition(1),
                    MusicianName.parseOrNull("Ana")!!,
                    Assignment.State.Failed(reason),
                ),
            ),
            slotClears = listOf(
                SlotClear(
                    7,
                    jamDate,
                    song,
                    "Crossroads",
                    Instrument.BASS,
                    SlotPosition(1),
                    "Ana",
                    SlotClear.State.Failed(reason),
                ),
            ),
            extraParticipantChanges = listOf(
                ExtraParticipantChange(
                    8,
                    jamDate,
                    song,
                    "Crossroads",
                    emptyList(),
                    ExtraParticipantChange.State.Failed(reason),
                ),
            ),
        )
    }

    @Test
    fun `each failure card's Cerrar goes through the dismiss of the contract that owns it, with its own id`() {
        val state = everyKindFailed()

        val failures = snapshot().toUiModel(today, admin = state).failures()
        assertEquals((1L..8L).toList(), failures.map { it.id })
        failures.forEach { it.events(AddFailureUiModel.Event.Dismiss) }

        assertEquals(
            listOf(
                "Add" to 1L,
                "Remove" to 2L,
                "Move" to 3L,
                "KeyChange" to 4L,
                "LineupChange" to 5L,
                "Assignment" to 6L,
                "SlotClear" to 7L,
                "ExtraParticipantChange" to 8L,
            ),
            setlist.dismissCalls,
        )
    }

    @Test
    fun `a key change maps the outcomes like the other cards, except the list-changed codes`() {
        val jamChanged = UiText.res(CoreR.string.write_failure_jam_changed)
        assertEquals(jamChanged, keyFailureMessage(WriteOutcome.Rejected("song_not_in_setlist")))
        listOf("unknown_jam", "jam_not_editable", "duplicate_date").forEach { code ->
            assertEquals(jamChanged, keyFailureMessage(WriteOutcome.Rejected(code)))
        }
        assertEquals(
            UiText.res(CoreR.string.write_failure_sheet_refused),
            keyFailureMessage(WriteOutcome.Rejected("invalid_key")),
        )
        assertEquals(UiText.res(CoreR.string.write_failure_offline), keyFailureMessage(WriteOutcome.Offline))
        assertEquals(UiText.res(CoreR.string.write_failure_unavailable), keyFailureMessage(WriteOutcome.Unavailable))
    }
}
