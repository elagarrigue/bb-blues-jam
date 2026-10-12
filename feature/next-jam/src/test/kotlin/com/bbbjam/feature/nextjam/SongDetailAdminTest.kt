package com.bbbjam.feature.nextjam

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.AdminSession
import com.bbbjam.core.data.admin.LoginOutcome
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.Assignment
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.model.ExtraParticipant
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
import com.bbbjam.core.ui.lineup.LineupLineEvent
import com.bbbjam.core.ui.nav.BackUiModel
import com.bbbjam.core.ui.strip.toInstrumentChips
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `song-detail-replaces-row-expansion`: the song detail holds everything the expanded row had. The
 * copy is written out from the approved tables, never read from [NextJamCopy] or [SongDetailCopy].
 * Several tests here moved from the list's tests (extras form, panel "Quitar", lineup editor,
 * "Cambiar tonalidad"), now driven through [SongDetailPresenter].
 */
class SongDetailAdminTest {
    private val jams = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val setlist = FakeSetlistRepository()
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)
    private val hint = "Para tocar, anotate en la jam: la organización te suma a un tema."

    private val mixed =
        Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"), Slot(Instrument.GUITAR), Slot(Instrument.BASS)))
    private val extras = listOf(ExtraParticipant("Juan", "saxo"), ExtraParticipant("Mora", "trompeta"))
    private val crossroads = JamSong(1, SongId("crossroads"), "Crossroads", "Cream", Key("A"), mixed, extras)
    private val hoochie =
        JamSong(2, SongId("hoochie"), "Hoochie Coochie Man", "Muddy Waters", Key("E"), Lineup.default())

    private fun snapshot(songs: List<JamSong> = listOf(crossroads, hoochie), status: JamStatus = JamStatus.PUBLISHED) =
        JamsSnapshot(
            upcoming = Jam(jamDate, LocalTime.of(21, 0), "La Macanuda", status, Setlist.Available(songs)),
            past = emptyList(),
            freshness = fetched,
        )

    private fun presenter(adminSession: AdminSession = session) = SongDetailPresenter(jams, adminSession, setlist)

    private fun params(
        onBack: () -> Unit = {},
        onSetKey: (LocalDate, SongId) -> Unit = { _, _ -> },
        onAssignSlot: (LocalDate, SongId, Instrument, SlotPosition) -> Unit = { _, _, _, _ -> },
    ) = SongDetailPresenter.Params(jamDate, crossroads.songId, 1, onBack, onSetKey, onAssignSlot)

    private suspend fun ReceiveTurbine<SongDetailUiModel>.awaitSong(
        predicate: (SongDetailUiModel.Song) -> Boolean = { true },
    ): SongDetailUiModel.Song {
        while (true) {
            val item = awaitItem()
            if (item is SongDetailUiModel.Song && predicate(item)) return item
        }
    }

    private fun SongDetailUiModel.Song.controls() = checkNotNull(admin)

    private fun Pair<NextJamUiModel, SongDetailUiModel>.bothShowAna(): Boolean {
        val row = ((first as? NextJamUiModel.Jam)?.setlist as? SetlistUiModel.Songs)?.rows?.firstOrNull()
        val detail = second as? SongDetailUiModel.Song
        return row?.key == "C" && row.instruments.any { it.label == "Gtr: Ana" } &&
            detail?.key == "C" && detail.lineup.filledSlots.any { it.detail == "Ana" }
    }

    @Test
    fun `a musician's detail is the panel with the hint and no control`() = runTest {
        session.isAdmin.value = false
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params()) }.test {
            jams.snapshots.emit(snapshot())
            val song = awaitSong()
            assertNull(song.admin)
            assertEquals(hint, song.lineup.hint)
            assertEquals(listOf("LIBRE", "LIBRE"), song.lineup.openSlots.map { it.detail })
            assertEquals(listOf("Tincho"), song.lineup.filledSlots.map { it.detail })
            assertEquals(listOf("+ saxo", "+ trompeta"), song.lineup.extras.map { it.instrument })
            val lines = song.lineup.openSlots + song.lineup.filledSlots + song.lineup.extras
            assertTrue(lines.all { it.action == null && it.actionLabel == null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the admin's detail adds the song's controls, the line actions and no hint`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params()) }.test {
            jams.snapshots.emit(snapshot())
            val song = awaitSong()
            val controls = song.controls()
            assertEquals("Cambiar tonalidad", controls.setKey.label)
            assertEquals("Cambiar formación", (controls.lineup as LineupEditorUiModel.Idle).label)
            assertFalse(controls.extraParticipants.formVisible)
            assertNull(controls.saveStatus)
            assertTrue(controls.failures.isEmpty())
            assertNull(song.lineup.hint)
            assertEquals(listOf("Anotar", "Anotar"), song.lineup.openSlots.map { it.actionLabel })
            assertEquals(listOf("Liberar"), song.lineup.filledSlots.map { it.actionLabel })
            assertEquals(listOf("Quitar", "Quitar"), song.lineup.extras.map { it.actionLabel })
            assertEquals(
                listOf("Quitar a Juan, saxo", "Quitar a Mora, trompeta"),
                song.lineup.extras.map { it.actionDescription },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the detail waits for the flag, so the admin never sees a draft's song as not found`() = runTest {
        val flag = MutableStateFlow<Boolean?>(null)
        val pending = object : AdminSession {
            override fun observeIsAdmin(): Flow<Boolean> = flag.filterNotNull()
            override suspend fun logIn(passphrase: String): LoginOutcome = LoginOutcome.Unavailable
            override suspend fun logOut() = Unit
        }
        jams.snapshots.emit(snapshot(status = JamStatus.DRAFT))
        moleculeFlow(RecompositionMode.Immediate) { presenter(pending).present(params()) }.test {
            assertTrue(awaitItem() is SongDetailUiModel.Loading)
            flag.value = true
            val seen = mutableListOf<SongDetailUiModel>()
            do {
                seen += awaitItem()
            } while (seen.last() !is SongDetailUiModel.Song)
            assertTrue("no frame may be not found: $seen", seen.none { it is SongDetailUiModel.NotFound })
            assertEquals("Crossroads", (seen.last() as SongDetailUiModel.Song).title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a musician opening a draft's song gets not found once the flag is read`() = runTest {
        session.isAdmin.value = false
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params()) }.test {
            jams.snapshots.emit(snapshot(status = JamStatus.DRAFT))
            var item = awaitItem()
            while (item is SongDetailUiModel.Loading) item = awaitItem()
            assertTrue(item is SongDetailUiModel.NotFound)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Cambiar tonalidad and Anotar navigate for this song, and Liberar clears that slot once`() = runTest {
        val keys = mutableListOf<Pair<LocalDate, SongId>>()
        val assigns = mutableListOf<List<Any>>()
        val p = params(
            onSetKey = { date, id -> keys += date to id },
            onAssignSlot = { date, id, instrument, position -> assigns += listOf(date, id, instrument, position) },
        )
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(p) }.test {
            jams.snapshots.emit(snapshot())
            val song = awaitSong()
            song.controls().setKey.events(SetKeyActionUiModel.Event.Open)
            assertEquals(listOf(jamDate to crossroads.songId), keys)
            // The open guitar is the second guitar of the lineup, ordinal 2, whatever the panel order.
            checkNotNull(song.lineup.openSlots.first().action)(LineupLineEvent.Activate)
            assertEquals(listOf(listOf(jamDate, crossroads.songId, Instrument.GUITAR, SlotPosition(2))), assigns)
            checkNotNull(song.lineup.filledSlots.single().action)(LineupLineEvent.Activate)
            assertEquals(
                listOf(
                    FakeSetlistRepository.ClearCall(
                        jamDate,
                        crossroads.songId,
                        Instrument.GUITAR,
                        SlotPosition(1),
                        "Tincho",
                    ),
                ),
                setlist.clearCalls,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a pending key shows with Guardando, a failure reverts it into a card of this song only`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params()) }.test {
            jams.snapshots.emit(snapshot())
            awaitSong()
            setlist.keyChanges.value =
                listOf(KeyChange(5, jamDate, crossroads.songId, "Crossroads", Key("Bb"), KeyChange.State.Sending))
            val sending = awaitSong { it.key == "Bb" }
            assertEquals("Tonalidad Bb", sending.keyDescription)
            assertEquals("Guardando…", sending.controls().saveStatus)

            setlist.keyChanges.value = listOf(
                KeyChange(
                    5,
                    jamDate,
                    crossroads.songId,
                    "Crossroads",
                    Key("Bb"),
                    KeyChange.State.Failed(WriteOutcome.Offline),
                ),
                KeyChange(
                    6,
                    jamDate,
                    hoochie.songId,
                    "Hoochie Coochie Man",
                    Key("G"),
                    KeyChange.State.Failed(WriteOutcome.Offline),
                ),
            )
            val failed = awaitSong { it.key == "A" }
            assertNull(failed.controls().saveStatus)
            val card = failed.controls().failures.single()
            assertEquals("No se pudo cambiar la tonalidad de «Crossroads»", card.title)
            assertEquals("No hay conexión. Probá de nuevo cuando tengas internet.", card.message)
            assertEquals("Cerrar", card.dismissLabel)
            card.events(AddFailureUiModel.Event.Dismiss)
            assertEquals(listOf(5L), setlist.dismissed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the list and the detail draw the same key, lineup and status for the same sending entries`() = runTest {
        val calendar =
            JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
        val list = NextJamPresenter(jams, calendar, session, setlist)
        moleculeFlow(RecompositionMode.Immediate) {
            list.present(NextJamPresenter.Params()) to presenter().present(params())
        }.test {
            jams.snapshots.emit(snapshot())
            setlist.keyChanges.value =
                listOf(KeyChange(1, jamDate, crossroads.songId, "Crossroads", Key("C"), KeyChange.State.Sending))
            setlist.assignments.value = listOf(
                Assignment(
                    2,
                    jamDate,
                    crossroads.songId,
                    "Crossroads",
                    Instrument.GUITAR,
                    SlotPosition(2),
                    checkNotNull(MusicianName.parseOrNull("Ana")),
                    Assignment.State.Sending,
                ),
            )
            // The two presenters collect the same entries separately; wait for the frame both drew.
            var pair = awaitItem()
            while (!pair.bothShowAna()) pair = awaitItem()
            val row = ((pair.first as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows.first()
            val detail = pair.second as SongDetailUiModel.Song
            val shown =
                Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"), Slot(Instrument.GUITAR, "Ana"), Slot(Instrument.BASS)))
            assertEquals("C", row.key)
            assertEquals(row.key, detail.key)
            assertEquals(row.keyDescription, detail.keyDescription)
            assertEquals(shown.toInstrumentChips(extras), row.instruments)
            assertEquals(listOf("Tincho", "Ana"), detail.lineup.filledSlots.map { it.detail })
            assertEquals("Guardando…", row.admin?.saveStatus)
            assertEquals(row.admin?.saveStatus, detail.controls().saveStatus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the Otros form submits through the repository, a blank one cannot, and Cancelar clears it`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params()) }.test {
            jams.snapshots.emit(snapshot())
            var editor = awaitSong().controls().extraParticipants
            assertFalse(editor.submitEnabled)
            editor.events(ExtraParticipantEditorUiModel.Event.Open)
            editor = awaitSong { it.controls().extraParticipants.formVisible }.controls().extraParticipants
            assertFalse(editor.submitEnabled)
            editor.events(ExtraParticipantEditorUiModel.Event.NameChanged("  "))
            editor.events(ExtraParticipantEditorUiModel.Event.InstrumentChanged("bajo"))
            editor = awaitSong { it.controls().extraParticipants.instrument == "bajo" }.controls().extraParticipants
            assertFalse(editor.submitEnabled)
            editor.events(ExtraParticipantEditorUiModel.Event.Submit)
            assertTrue(setlist.extraAddCalls.isEmpty())
            editor.events(ExtraParticipantEditorUiModel.Event.Cancel)
            val closed = awaitSong {
                val form = it.controls().extraParticipants
                !form.formVisible && form.name.isEmpty() && form.instrument.isEmpty()
            }.controls().extraParticipants
            assertTrue(setlist.extraAddCalls.isEmpty())

            closed.events(ExtraParticipantEditorUiModel.Event.Open)
            editor = awaitSong { it.controls().extraParticipants.formVisible }.controls().extraParticipants
            editor.events(ExtraParticipantEditorUiModel.Event.NameChanged("Ana"))
            editor.events(ExtraParticipantEditorUiModel.Event.InstrumentChanged("percusión"))
            editor = awaitSong { it.controls().extraParticipants.submitEnabled }.controls().extraParticipants
            editor.events(ExtraParticipantEditorUiModel.Event.Submit)
            assertEquals(
                listOf(FakeSetlistRepository.ExtraAddCall(jamDate, crossroads.songId, "Ana", "percusión")),
                setlist.extraAddCalls,
            )
            assertTrue(setlist.extraRemoveCalls.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Quitar on an extra line removes that participant through the repository once`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params()) }.test {
            jams.snapshots.emit(snapshot())
            checkNotNull(awaitSong().lineup.extras[1].action)(LineupLineEvent.Activate)
            assertEquals(
                listOf(FakeSetlistRepository.ExtraRemoveCall(jamDate, crossroads.songId, 2, "Mora", "trompeta")),
                setlist.extraRemoveCalls,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the lineup editor opens and closes, and a retained handler queues distinct counts`() = runTest {
        setlist.lineupHold = CompletableDeferred()
        val defaults = crossroads.copy(lineup = Lineup.default(), extraParticipants = emptyList())
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params()) }.test {
            jams.snapshots.emit(snapshot(listOf(defaults)))
            (awaitSong().controls().lineup as LineupEditorUiModel.Idle).events(LineupEditorUiModel.Event.Open)
            val editing = awaitSong { it.controls().lineup is LineupEditorUiModel.Editing }
                .controls().lineup as LineupEditorUiModel.Editing
            assertEquals("FORMACIÓN", editing.heading)
            val retained = editing.lines.single { it.instrument == Instrument.GUITAR }.events
            retained(LineupEditorLineUiModel.Event.Remove)
            retained(LineupEditorLineUiModel.Event.Remove)
            retained(LineupEditorLineUiModel.Event.Remove)
            assertEquals(listOf(1, 0), setlist.lineupCalls.map { it.count })
            val empty = expectMostRecentItem() as SongDetailUiModel.Song
            assertEquals("Guardando…", empty.controls().saveStatus)
            val guitar = (empty.controls().lineup as LineupEditorUiModel.Editing).lines.first()
            assertEquals("No va en este tema", guitar.countLabel)
            assertFalse(guitar.canRemove)
            retained(LineupEditorLineUiModel.Event.Add)
            retained(LineupEditorLineUiModel.Event.Add)
            retained(LineupEditorLineUiModel.Event.Add)
            assertEquals(listOf(1, 0, 1, 2, 3), setlist.lineupCalls.map { it.count })
            setlist.lineupHold?.complete(Unit)
            editing.events(LineupEditorUiModel.Event.Done)
            awaitSong { it.controls().lineup is LineupEditorUiModel.Idle }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun SongDetailUiModel.editor() =
        (this as SongDetailUiModel.Song).controls().lineup as LineupEditorUiModel.Editing

    @Test
    fun `every instrument can grow to three and the plus disables at three`() = runTest {
        val twoVocals = crossroads.copy(
            lineup = Lineup(listOf(Slot(Instrument.VOCALS, "Lu"), Slot(Instrument.VOCALS))),
            extraParticipants = emptyList(),
        )
        val threeVocals = twoVocals.copy(lineup = twoVocals.lineup.withSlotCount(Instrument.VOCALS, 3)!!)
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params()) }.test {
            jams.snapshots.emit(
                snapshot(listOf(crossroads.copy(lineup = Lineup.default(), extraParticipants = emptyList()))),
            )
            (awaitSong().controls().lineup as LineupEditorUiModel.Idle).events(LineupEditorUiModel.Event.Open)
            val one = awaitSong { it.controls().lineup is LineupEditorUiModel.Editing }
                .controls().lineup as LineupEditorUiModel.Editing
            val vocals = one.lines.single { it.instrument == Instrument.VOCALS }
            assertEquals("1 cupo", vocals.countLabel)
            assertTrue(vocals.canAdd)
            vocals.events(LineupEditorLineUiModel.Event.Add)
            assertEquals(Instrument.VOCALS to 2, setlist.lineupCalls.last().let { it.instrument to it.count })
            jams.snapshots.emit(snapshot(listOf(twoVocals)))
            val two = expectMostRecentItem().editor()
            val twoLine = two.lines.single { it.instrument == Instrument.VOCALS }
            assertEquals("2 cupos", twoLine.countLabel)
            assertTrue(twoLine.canAdd)
            jams.snapshots.emit(snapshot(listOf(threeVocals)))
            val three = expectMostRecentItem().editor()
            val threeLine = three.lines.single { it.instrument == Instrument.VOCALS }
            assertEquals("3 cupos", threeLine.countLabel)
            assertFalse(threeLine.canAdd)
            assertTrue(threeLine.canRemove)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the editor stops adding at one for drums and keyboards but not for bass`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params()) }.test {
            jams.snapshots.emit(
                snapshot(listOf(crossroads.copy(lineup = Lineup.default(), extraParticipants = emptyList()))),
            )
            (awaitSong().controls().lineup as LineupEditorUiModel.Idle).events(LineupEditorUiModel.Event.Open)
            val editor = awaitSong { it.controls().lineup is LineupEditorUiModel.Editing }
                .controls().lineup as LineupEditorUiModel.Editing
            fun canAdd(instrument: Instrument) = editor.lines.single { it.instrument == instrument }.canAdd
            assertFalse(canAdd(Instrument.DRUMS))
            assertFalse(canAdd(Instrument.KEYBOARDS))
            assertTrue(canAdd(Instrument.BASS))
            assertTrue(canAdd(Instrument.GUITAR))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a filled slot cannot be removed in the editor and says why`() = runTest {
        val filled = crossroads.copy(lineup = Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"))))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params()) }.test {
            jams.snapshots.emit(snapshot(listOf(filled)))
            (awaitSong().controls().lineup as LineupEditorUiModel.Idle).events(LineupEditorUiModel.Event.Open)
            val editor = awaitSong { it.controls().lineup is LineupEditorUiModel.Editing }
                .controls().lineup as LineupEditorUiModel.Editing
            val guitar = editor.lines.first()
            assertFalse(guitar.canRemove)
            assertTrue(guitar.canAdd)
            assertEquals("Tiene músico anotado. Liberá el cupo antes de sacarlo.", guitar.blockedNote)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the detail follows its song through a renumbering and turns not found when it is removed`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(params()) }.test {
            jams.snapshots.emit(snapshot())
            awaitSong()
            // Another song moved above it: Crossroads is now second, and its controls still edit it.
            jams.snapshots.emit(snapshot(listOf(hoochie.copy(position = 1), crossroads.copy(position = 2))))
            val moved = awaitSong()
            assertEquals("Crossroads", moved.title)
            moved.controls().setKey.events(SetKeyActionUiModel.Event.Open)
            jams.snapshots.emit(snapshot(listOf(hoochie.copy(position = 1))))
            var item = awaitItem()
            while (item is SongDetailUiModel.Song) item = awaitItem()
            assertTrue(item is SongDetailUiModel.NotFound)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an earlier model's handlers call the current callbacks`() = runTest {
        val calls = mutableListOf<String>()
        fun named(name: String) = params(
            onBack = { calls += "$name back" },
            onSetKey = { _, _ -> calls += "$name key" },
            onAssignSlot = { _, _, _, _ -> calls += "$name assign" },
        )
        var current by mutableStateOf(named("first"))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(current) }.test {
            jams.snapshots.emit(snapshot())
            val first = awaitSong()
            current = named("second")
            awaitItem()
            first.back.events(BackUiModel.Event.Back)
            first.controls().setKey.events(SetKeyActionUiModel.Event.Open)
            checkNotNull(first.lineup.openSlots.first().action)(LineupLineEvent.Activate)
            assertEquals(listOf("second back", "second key", "second assign"), calls)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
