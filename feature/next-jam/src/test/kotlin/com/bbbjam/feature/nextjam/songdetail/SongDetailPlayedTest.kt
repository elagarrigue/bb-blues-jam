package com.bbbjam.feature.nextjam.songdetail

import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.played.PlayedChange
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SlotPosition
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.nextjam.PlayedToggleUiModel
import com.bbbjam.feature.nextjam.R
import com.bbbjam.feature.nextjam.assign.AssignMusicianPresenter
import com.bbbjam.feature.nextjam.assign.assignableSong
import com.bbbjam.feature.nextjam.overlay.SongOverlays
import com.bbbjam.feature.nextjam.setkey.SetKeyUiModel
import com.bbbjam.feature.nextjam.setkey.setKeyModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `song-played-state` on the song detail (D-31): the read-only admin model of a played song, the switch, the
 * muted label for everyone, and the two pickers that are gone for it.
 */
class SongDetailPlayedTest {
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val id = SongId("crossroads")
    private val lineup = Lineup(listOf(Slot(Instrument.GUITAR, "Ana"), Slot(Instrument.GUITAR), Slot(Instrument.BASS)))
    private val song = JamSong(1, id, "Crossroads", "Cream", Key("A"), lineup)

    private fun snapshot(played: Boolean, status: JamStatus = JamStatus.PUBLISHED) = JamsSnapshot(
        upcoming = Jam(
            jamDate,
            LocalTime.of(21, 0),
            "La Macanuda",
            status,
            Setlist.Available(listOf(song.copy(played = played))),
        ),
        past = emptyList(),
        freshness = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false),
    )

    private fun change(played: Boolean, state: PlayedChange.State) =
        PlayedChange(1, jamDate, id, "Crossroads", played, state)

    private fun admin(
        changes: List<PlayedChange> = emptyList(),
        onToggle: (LocalDate, SongId, Boolean) -> Unit = { _, _, _ -> },
    ) = SongAdminState(
        overlays = SongOverlays(playedChanges = changes),
        reservations = LineupReservations(),
        onTogglePlayed = onToggle,
    )

    private fun JamsSnapshot.detail(admin: SongAdminState? = null) =
        toSongDetail(jamDate, id, 1, admin, onBack = {}) as SongDetailUiModel.Song

    @Test
    fun `a musician sees YA TOCADO and a muted panel with no hint, and no admin part`() {
        val played = snapshot(played = true).detail()

        assertEquals(UiText.res(R.string.song_detail_played_label), played.playedLabel)
        assertTrue(played.lineup.muted)
        assertNull(played.lineup.hint)
        assertNull(played.admin)
        val open = snapshot(played = false).detail()
        assertNull(open.playedLabel)
        assertFalse(open.lineup.muted)
        assertNotNull(open.lineup.hint)
    }

    @Test
    fun `the admin's played song has no key picker, formation editor or Otros, no line action, and a checked switch`() {
        val model = snapshot(played = true).detail(admin())

        val controls = checkNotNull(model.admin)
        assertNull(controls.setKey)
        assertNull(controls.lineup)
        assertNull(controls.extraParticipants)
        val lines = model.lineup.openSlots + model.lineup.filledSlots + model.lineup.extras
        assertTrue(lines.isNotEmpty() && lines.all { it.action == null && it.actionLabel == null })
        assertTrue(model.lineup.muted)
        val toggle = checkNotNull(controls.played)
        assertTrue(toggle.checked)
        assertEquals(UiText.res(R.string.next_jam_unmark_played), toggle.description)
    }

    @Test
    fun `the admin's unplayed song keeps every editor and an unchecked switch`() {
        val model = snapshot(played = false).detail(admin())

        val controls = checkNotNull(model.admin)
        assertNotNull(controls.setKey)
        assertNotNull(controls.lineup)
        assertNotNull(controls.extraParticipants)
        assertTrue(model.lineup.openSlots.any { it.action != null })
        assertFalse(checkNotNull(controls.played).checked)
        assertNull(model.playedLabel)
    }

    @Test
    fun `a sending mark turns the detail read-only at once with Guardando, a failure turns it back`() {
        val sending = snapshot(played = false)
            .detail(admin(listOf(change(played = true, state = PlayedChange.State.Sending))))
        assertEquals(UiText.res(R.string.song_detail_played_label), sending.playedLabel)
        assertNull(checkNotNull(sending.admin).setKey)
        assertEquals(UiText.res(R.string.next_jam_saving), sending.admin?.saveStatus)
        assertTrue(checkNotNull(sending.admin?.played).checked)

        val failed = snapshot(played = false)
            .detail(admin(listOf(change(played = true, state = PlayedChange.State.Outdated))))
        assertNull(failed.playedLabel)
        assertNotNull(checkNotNull(failed.admin).setKey)
        assertEquals(1, failed.admin?.failures?.size)
    }

    @Test
    fun `the switch asks for the opposite of what is drawn`() {
        val calls = mutableListOf<Triple<LocalDate, SongId, Boolean>>()
        val onToggle: (LocalDate, SongId, Boolean) -> Unit = { date, songId, played ->
            calls +=
                Triple(date, songId, played)
        }

        checkNotNull(snapshot(played = true).detail(admin(onToggle = onToggle)).admin?.played)
            .events(PlayedToggleUiModel.Event.Toggle)
        checkNotNull(snapshot(played = false).detail(admin(onToggle = onToggle)).admin?.played)
            .events(PlayedToggleUiModel.Event.Toggle)

        assertEquals(listOf(Triple(jamDate, id, false), Triple(jamDate, id, true)), calls)
    }

    @Test
    fun `the key picker and the assign screen are gone for a played song`() {
        assertTrue(setKeyModel(true, snapshot(played = false), emptyList(), jamDate, id) is SetKeyUiModel.Content)
        assertTrue(setKeyModel(true, snapshot(played = true), emptyList(), jamDate, id) is SetKeyUiModel.Gone)
        val params = AssignMusicianPresenter.Params(jamDate, id, Instrument.GUITAR, SlotPosition(2), {}, {})
        assertNotNull(assignableSong(true, snapshot(played = false), emptyList(), params))
        assertNull(assignableSong(true, snapshot(played = true), emptyList(), params))
    }
}
