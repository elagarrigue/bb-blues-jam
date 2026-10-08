package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.KeyChange
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.SongId
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
 * The copy is written out from the approved table, never read from [NextJamCopy].
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

    private fun presenter() = NextJamPresenter(repository, calendar, session, setlist)

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

    private fun admin(changes: List<KeyChange>, onSetKey: (LocalDate, SongId) -> Unit = { _, _ -> }) =
        AdminState(emptyList(), {}, {}, keyChanges = changes, onSetKey = onSetKey)

    @Test
    fun `an admin row offers Cambiar tonalidad, which opens the picker for its jam and song`() {
        val opened = mutableListOf<Pair<LocalDate, SongId>>()
        val model = snapshot().toUiModel(today, admin = admin(emptyList()) { date, id -> opened += date to id })
        val setKey = checkNotNull(model.row("Hoochie Coochie Man").admin).setKey
        assertEquals("Cambiar tonalidad", setKey.label)
        setKey.events(SetKeyActionUiModel.Event.Open)
        assertEquals(listOf(jamDate to SongId("hoochie")), opened)
        assertNull(checkNotNull(model.row("Crossroads").admin).saveStatus)
    }

    @Test
    fun `a musician's rows never get the overlay or the action`() = runTest {
        session.isAdmin.value = false
        setlist.keyChanges.value = listOf(change(1, "Bb", KeyChange.State.Sending))
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot())
            val row = expectMostRecentItem().row("Crossroads")
            assertEquals("A", row.key)
            assertEquals("Tonalidad A", row.keyDescription)
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
            assertEquals("Tonalidad Bb", sending.keyDescription)
            assertEquals("Guardando…", checkNotNull(sending.admin).saveStatus)
            // Only that row is overlaid.
            assertEquals("E", model.row("Hoochie Coochie Man").key)
            assertEquals(emptyList<AddFailureUiModel>(), model.failures())

            setlist.keyChanges.value = listOf(change(4, "Bb", KeyChange.State.Failed(WriteOutcome.AccessRefused)))
            model = awaitItem()
            val reverted = model.row("Crossroads")
            assertEquals("A", reverted.key)
            assertEquals("Tonalidad A", reverted.keyDescription)
            assertNull(checkNotNull(reverted.admin).saveStatus)
            val card = model.failures().single()
            assertEquals("No se pudo cambiar la tonalidad de «Crossroads»", card.title)
            assertEquals(
                "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar.",
                card.message,
            )
            assertEquals("Cerrar", card.dismissLabel)
            card.events(AddFailureUiModel.Event.Dismiss)
            assertEquals(listOf(4L), setlist.dismissed)
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
        assertEquals(
            "No hay conexión. Probá de nuevo cuando tengas internet.",
            model.failures().single().message,
        )
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
            onDismiss = {},
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
                "No se pudo cambiar la tonalidad de «Crossroads»" to
                    "La jam cambió en la planilla. Actualizá y probá de nuevo.",
                "No se pudo agregar «Red House»" to "El servidor no respondió. Probá de nuevo en un rato.",
                // The removal keeps its own wording for the same code.
                "No se pudo quitar «Hoochie Coochie Man»" to "Ese tema ya no estaba en la lista.",
                "No se pudo cambiar la tonalidad de «Crossroads»" to
                    "Ese tema está repetido en la planilla. Corregilo ahí y probá de nuevo.",
            ),
            failures.map { it.title to it.message },
        )
    }

    @Test
    fun `a key change maps the outcomes like the other cards, except the list-changed codes`() {
        assertEquals(
            "La jam cambió en la planilla. Actualizá y probá de nuevo.",
            keyFailureMessage(WriteOutcome.Rejected("song_not_in_setlist")),
        )
        listOf("unknown_jam", "jam_not_editable", "duplicate_date").forEach { code ->
            assertEquals(
                "La jam cambió en la planilla. Actualizá y probá de nuevo.",
                keyFailureMessage(WriteOutcome.Rejected(code)),
            )
        }
        assertEquals(
            "La planilla rechazó el cambio. Revisala y probá de nuevo.",
            keyFailureMessage(WriteOutcome.Rejected("invalid_key")),
        )
        assertEquals("No hay conexión. Probá de nuevo cuando tengas internet.", keyFailureMessage(WriteOutcome.Offline))
        assertEquals(
            "El servidor no respondió. Probá de nuevo en un rato.",
            keyFailureMessage(WriteOutcome.Unavailable),
        )
    }
}
