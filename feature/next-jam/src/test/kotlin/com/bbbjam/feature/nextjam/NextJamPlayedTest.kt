package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.played.PlayedChange
import com.bbbjam.core.data.testing.FakeAdminSession
import com.bbbjam.core.data.testing.FakeJamsRepository
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.nextjam.admin.AdminState
import com.bbbjam.feature.nextjam.overlay.pendingPlayed
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `song-played-state` on Próxima jam (D-31): the muted row and its "Ya tocado" label for everyone, the admin's
 * switch and its optimistic value, the removal that disappears while played, the move that stays, the
 * revert and its card, and the filter rules (P8).
 */
class NextJamPlayedTest {
    private val repository = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val setlist = FakeSetlistRepository()
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
    private val today = LocalDate.of(2026, 10, 2)
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    private val crossroads = JamSong(1, SongId("crossroads"), "Crossroads", "Cream", Key("A"), Lineup.default())
    private val hoochie =
        JamSong(2, SongId("hoochie"), "Hoochie Coochie Man", "Muddy Waters", Key("E"), Lineup.default())

    private fun snapshot(songs: List<JamSong>) = JamsSnapshot(
        upcoming = Jam(jamDate, LocalTime.of(21, 0), "La Macanuda", JamStatus.PUBLISHED, Setlist.Available(songs)),
        past = emptyList(),
        freshness = fetched,
    )

    private fun presenter() = NextJamPresenter(
        repository,
        calendar,
        session,
        setlist.writes(),
        setlist.sources(),
        FakeMusicianFilterPreference(),
    )

    private fun NextJamUiModel.rows() = ((this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows

    private fun NextJamUiModel.row(title: String) = rows().single { it.title == title }

    private fun change(id: Long, played: Boolean, state: PlayedChange.State, songId: String = "crossroads") =
        PlayedChange(id, jamDate, SongId(songId), "Crossroads", played, state)

    private fun admin(
        changes: List<PlayedChange> = emptyList(),
        onToggle: (LocalDate, SongId, Boolean) -> Unit = {
                _,
                _,
                _,
            ->
        },
    ) = AdminState(emptyList(), {}, {}, playedChanges = changes, onTogglePlayed = onToggle)

    private val playedLabel = UiText.res(R.string.next_jam_played)

    @Test
    fun `a musician sees a played row with its label and played flag, and nothing to toggle`() {
        val model = snapshot(listOf(crossroads.copy(played = true), hoochie)).toUiModel(today)

        val played = model.row("Crossroads")
        assertTrue(played.played)
        assertEquals(playedLabel, played.playedLabel)
        assertNull(played.admin)
        val other = model.row("Hoochie Coochie Man")
        assertFalse(other.played)
        assertNull(other.playedLabel)
    }

    @Test
    fun `the admin's played row has a checked switch, no removal and keeps its move, an unplayed row has all three`() {
        val model = snapshot(listOf(crossroads.copy(played = true), hoochie)).toUiModel(today, admin = admin())

        val played = checkNotNull(model.row("Crossroads").admin)
        assertNull(played.removal)
        assertNotNull(played.move)
        val toggle = checkNotNull(played.played)
        assertTrue(toggle.checked)
        assertEquals(UiText.res(R.string.next_jam_unmark_played), toggle.description)
        assertEquals(UiText.res(R.string.next_jam_state_played), toggle.stateDescription)
        val open = checkNotNull(model.row("Hoochie Coochie Man").admin)
        assertTrue(open.removal is RemovalUiModel.Idle)
        assertNotNull(open.move)
        val unchecked = checkNotNull(open.played)
        assertFalse(unchecked.checked)
        assertEquals(UiText.res(R.string.next_jam_mark_played), unchecked.description)
        assertEquals(UiText.res(R.string.next_jam_state_unplayed), unchecked.stateDescription)
    }

    @Test
    fun `a tap on the switch asks for the opposite of what is drawn`() {
        val calls = mutableListOf<Triple<LocalDate, SongId, Boolean>>()
        val model = snapshot(listOf(crossroads.copy(played = true), hoochie))
            .toUiModel(today, admin = admin(onToggle = { date, id, played -> calls += Triple(date, id, played) }))

        checkNotNull(model.row("Crossroads").admin?.played).events(PlayedToggleUiModel.Event.Toggle)
        checkNotNull(model.row("Hoochie Coochie Man").admin?.played).events(PlayedToggleUiModel.Event.Toggle)

        assertEquals(
            listOf(Triple(jamDate, SongId("crossroads"), false), Triple(jamDate, SongId("hoochie"), true)),
            calls,
        )
    }

    @Test
    fun `a sending mark draws the row played with Guardando, and a failure reverts it and adds a card`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(listOf(crossroads, hoochie)))
            assertFalse(expectMostRecentItem().row("Crossroads").played)

            setlist.playedChanges.value = listOf(change(4, true, PlayedChange.State.Sending))
            var model = awaitItem()
            val sending = model.row("Crossroads")
            assertTrue(sending.played)
            assertEquals(playedLabel, sending.playedLabel)
            val admin = checkNotNull(sending.admin)
            assertEquals(savingText, admin.saveStatus)
            assertNull(admin.removal)
            assertTrue(checkNotNull(admin.played).checked)
            assertFalse(model.row("Hoochie Coochie Man").played)

            setlist.playedChanges.value =
                listOf(change(4, true, PlayedChange.State.Failed(WriteOutcome.Offline)))
            model = awaitItem()
            assertFalse(model.row("Crossroads").played)
            assertTrue(checkNotNull(model.row("Crossroads").admin).removal is RemovalUiModel.Idle)
            val card = checkNotNull((model as NextJamUiModel.Jam).admin).failures.single()
            assertEquals(UiText.res(R.string.next_jam_played_failed, "Crossroads"), card.title)
            assertEquals(UiText.res(CoreR.string.write_failure_offline), card.message)

            card.events(AddFailureUiModel.Event.Dismiss)
            assertEquals(listOf("PlayedChange" to 4L), setlist.dismissCalls)
            assertTrue(checkNotNull((awaitItem() as NextJamUiModel.Jam).admin).failures.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an outdated script is its own card and the row is unchanged`() {
        val model = snapshot(listOf(crossroads, hoochie))
            .toUiModel(today, admin = admin(listOf(change(2, true, PlayedChange.State.Outdated))))

        assertFalse(model.row("Crossroads").played)
        val card = checkNotNull((model as NextJamUiModel.Jam).admin).failures.single()
        assertEquals(UiText.res(R.string.next_jam_played_failed, "Crossroads"), card.title)
        assertEquals(UiText.res(R.string.next_jam_played_script_outdated), card.message)
    }

    @Test
    fun `a failed unmark is titled as an unmark and a played song reverts to played`() {
        val model = snapshot(listOf(crossroads.copy(played = true), hoochie)).toUiModel(
            today,
            admin = admin(
                listOf(change(3, false, PlayedChange.State.Failed(WriteOutcome.Rejected("jam_not_editable")))),
            ),
        )

        assertTrue(model.row("Crossroads").played)
        val card = checkNotNull((model as NextJamUiModel.Jam).admin).failures.single()
        assertEquals(UiText.res(R.string.next_jam_unplayed_failed, "Crossroads"), card.title)
        assertEquals(UiText.res(CoreR.string.write_failure_jam_changed), card.message)
    }

    @Test
    fun `the presenter sends one setSongPlayed per tap pair and ignores a tap while that song is sending`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(listOf(crossroads, hoochie)))
            var model = expectMostRecentItem()

            checkNotNull(model.row("Crossroads").admin?.played).events(PlayedToggleUiModel.Event.Toggle)
            assertEquals(listOf(Triple(jamDate, SongId("crossroads"), true)), setlist.playedCalls)

            setlist.playedChanges.value = listOf(change(1, true, PlayedChange.State.Sending))
            model = awaitItem()
            checkNotNull(model.row("Crossroads").admin?.played).events(PlayedToggleUiModel.Event.Toggle)
            assertEquals(1, setlist.playedCalls.size)
            // Another song is not blocked by it.
            checkNotNull(model.row("Hoochie Coochie Man").admin?.played).events(PlayedToggleUiModel.Event.Toggle)
            assertEquals(2, setlist.playedCalls.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pendingPlayed is the latest sending value only`() {
        val changes = listOf(
            change(1, true, PlayedChange.State.Sending),
            change(2, false, PlayedChange.State.Sending),
            change(3, true, PlayedChange.State.Failed(WriteOutcome.Offline)),
            change(9, true, PlayedChange.State.Sending, songId = "other"),
        )

        assertEquals(false, changes.pendingPlayed(jamDate, SongId("crossroads")))
        assertEquals(true, changes.pendingPlayed(jamDate, SongId("other")))
        assertNull(changes.pendingPlayed(jamDate, SongId("hoochie")))
        assertNull(changes.pendingPlayed(LocalDate.of(2026, 11, 28), SongId("crossroads")))
    }

    @Test
    fun `a played song's open slots are not matches nor counted, but it still shows with no instrument selected`() {
        val open = JamSong(3, SongId("red-house"), "Red House", "Jimi Hendrix", Key("Bb"), Lineup.default())
        val songs = listOf(crossroads.copy(played = true), hoochie, open)
        val snapshot = snapshot(songs)
        val guitar = Instrument.entries.indexOf(Instrument.GUITAR) + 1

        val all = snapshot.toUiModel(today).let { (it as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs }
        assertEquals(listOf("Crossroads", "Hoochie Coochie Man", "Red House"), all.rows.map { it.title })
        assertEquals("3", checkNotNull(all.filterBar).chips[0].count)
        assertEquals("2", checkNotNull(all.filterBar).chips[guitar].count)

        val filtered = snapshot.toUiModel(today, filter = setOf(Instrument.GUITAR))
            .let { (it as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs }
        assertEquals(listOf("Hoochie Coochie Man", "Red House"), filtered.rows.map { it.title })
    }

    @Test
    fun `the musician filter still lists a played song of that musician`() {
        val played = crossroads.copy(
            lineup = Lineup(listOf(Slot(Instrument.GUITAR, "Ana"), Slot(Instrument.BASS))),
            played = true,
        )
        val snapshot = snapshot(listOf(played, hoochie))

        val model = snapshot.toUiModel(today, musician = "Ana")
            .let { (it as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs }

        assertEquals(listOf("Crossroads"), model.rows.map { it.title })
        assertTrue(model.rows.single().played)
        // With the bass chip, the played song's open bass slot is not a match.
        val bass = snapshot.toUiModel(today, filter = setOf(Instrument.BASS), musician = "Ana")
            .let { (it as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs }
        assertTrue(bass.rows.isEmpty())
    }
}
