package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistRemove
import com.bbbjam.core.model.ExtraParticipant
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.Jam
import com.bbbjam.core.model.JamSong
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `admin-remove-song-from-setlist` Part B: the inline two-step removal on an expanded row. The copy
 * is written out from the approved table, never read from [NextJamCopy].
 */
class NextJamRemoveTest {
    private val repository = FakeJamsRepository()
    private val session = FakeAdminSession(isAdmin = true)
    private val setlist = FakeSetlistRepository()

    /** 2 October 2026, 12:00 in Buenos Aires. */
    private val calendar =
        JamCalendar(Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC), JamCalendar.BUENOS_AIRES)
    private val today = LocalDate.of(2026, 10, 2)
    private val jamDate = LocalDate.of(2026, 10, 31)
    private val fetched = Freshness(Instant.parse("2026-10-02T14:59:00Z"), lastFailure = null, isRefreshing = false)

    private val crossroads = song(1, "crossroads", "Crossroads")
    private val hoochie = song(2, "hoochie", "Hoochie Coochie Man").copy(
        lineup = Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"), Slot(Instrument.BASS))),
    )
    private val thrill = song(3, "thrill", "The Thrill Is Gone").copy(
        lineup = Lineup(listOf(Slot(Instrument.GUITAR, "Tincho"), Slot(Instrument.BASS, "Nico"))),
        extraParticipants = listOf(ExtraParticipant("Juan", "saxo")),
    )
    private val pride = song(4, "pride", "Pride and Joy")

    private fun song(position: Int, id: String, title: String) =
        JamSong(position, SongId(id), title, "Someone", Key("A"), Lineup.default())

    private fun jam(songs: List<JamSong>, status: JamStatus = JamStatus.PUBLISHED) = Jam(
        date = jamDate,
        startTime = LocalTime.of(21, 0),
        venue = "La Macanuda",
        status = status,
        setlist = Setlist.Available(songs),
    )

    private fun snapshot(jam: Jam) = JamsSnapshot(upcoming = jam, past = emptyList(), freshness = fetched)

    private fun presenter() = NextJamPresenter(repository, calendar, session, setlist)

    private fun NextJamUiModel.rows() = ((this as NextJamUiModel.Jam).setlist as SetlistUiModel.Songs).rows

    private fun NextJamUiModel.row(title: String) = rows().single { it.title == title }

    private fun NextJamUiModel.removal(title: String) = checkNotNull(row(title).admin).removal

    private fun remove(id: Long, songId: String, title: String, state: SetlistRemove.State) =
        SetlistRemove(id, jamDate, SongId(songId), title, state)

    /** An idle removal of [songId]; handlers compare by key, which names the jam, the song and the step. */
    private fun idleRemoval(songId: String) =
        RemovalUiModel.Idle("Quitar de la lista", EventHandler(key = "$jamDate|$songId|idle") {})

    @Test
    fun `the admin's rows offer Quitar de la lista, a musician's rows have no admin part`() {
        val published = snapshot(jam(listOf(crossroads, hoochie)))

        val admin = published.toUiModel(today, admin = AdminState(emptyList(), {}, {}))
        assertEquals(idleRemoval("crossroads"), admin.removal("Crossroads"))
        assertEquals(idleRemoval("hoochie"), admin.removal("Hoochie Coochie Man"))

        val musician = published.toUiModel(today)
        musician.rows().forEach { row -> assertNull(row.admin) }
    }

    @Test
    fun `the confirmation names the assigned musicians and the published list, only when they apply`() {
        val confirmingAll = listOf("crossroads", "hoochie", "thrill").map { id ->
            val state = AdminState(emptyList(), {}, {}, RemovalState(confirming = "$jamDate|$id"))
            id to state
        }
        val details = confirmingAll.associate { (id, state) ->
            val model = snapshot(jam(listOf(crossroads, hoochie, thrill))).toUiModel(today, admin = state)
            val title = model.rows().single { it.admin?.removal is RemovalUiModel.Confirming }.title
            id to (model.removal(title) as RemovalUiModel.Confirming).let { it.prompt to it.details }
        }
        val published = "La lista está publicada: los músicos van a dejar de verlo."
        assertEquals("¿Quitar «Crossroads» de la lista?" to listOf(published), details["crossroads"])
        assertEquals(
            "¿Quitar «Hoochie Coochie Man» de la lista?" to listOf("Se borra también el músico anotado.", published),
            details["hoochie"],
        )
        // Two filled slots plus one extra.
        assertEquals(
            "¿Quitar «The Thrill Is Gone» de la lista?" to
                listOf("Se borran también los 3 músicos anotados.", published),
            details["thrill"],
        )

        // A draft has no published line; a song with nobody assigned then has no detail at all.
        val draft = snapshot(jam(listOf(crossroads), JamStatus.DRAFT)).toUiModel(
            today,
            admin = AdminState(emptyList(), {}, {}, RemovalState(confirming = "$jamDate|crossroads")),
        )
        val confirming = draft.removal("Crossroads") as RemovalUiModel.Confirming
        assertEquals(emptyList<String>(), confirming.details)
        assertEquals("Quitar", confirming.confirmLabel)
        assertEquals("Cancelar", confirming.cancelLabel)
    }

    @Test
    fun `Quitar de la lista only asks, Cancelar goes back, and neither writes anything`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(jam(listOf(crossroads, hoochie))))
            var model = expectMostRecentItem()
            (model.removal("Hoochie Coochie Man") as RemovalUiModel.Idle).events(RemovalUiModel.Event.RequestRemove)

            model = awaitItem()
            val confirming = model.removal("Hoochie Coochie Man") as RemovalUiModel.Confirming
            assertTrue(model.removal("Crossroads") is RemovalUiModel.Idle)
            assertEquals(emptyList<Any>(), setlist.removeCalls)

            confirming.events(RemovalUiModel.Event.Cancel)
            model = awaitItem()
            assertTrue(model.removal("Hoochie Coochie Man") is RemovalUiModel.Idle)
            assertEquals(emptyList<Any>(), setlist.removeCalls)

            // A stale Confirm after Cancel does nothing either.
            confirming.events(RemovalUiModel.Event.Confirm)
            assertEquals(emptyList<Any>(), setlist.removeCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Quitar removes once even when tapped twice, and asking on another row moves the confirmation`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(jam(listOf(crossroads, hoochie))))
            var model = expectMostRecentItem()
            (model.removal("Crossroads") as RemovalUiModel.Idle).events(RemovalUiModel.Event.RequestRemove)
            model = awaitItem()
            (model.removal("Hoochie Coochie Man") as RemovalUiModel.Idle)
                .events(RemovalUiModel.Event.RequestRemove)
            model = awaitItem()
            assertTrue(model.removal("Crossroads") is RemovalUiModel.Idle)
            val confirming = model.removal("Hoochie Coochie Man") as RemovalUiModel.Confirming

            confirming.events(RemovalUiModel.Event.Confirm)
            confirming.events(RemovalUiModel.Event.Confirm)

            assertEquals(listOf(jamDate to SongId("hoochie")), setlist.removeCalls)
            assertTrue(awaitItem().removal("Hoochie Coochie Man") is RemovalUiModel.Idle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a sending removal draws Quitando, a failed one keeps the row and adds a card in call order`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(jam(listOf(crossroads, hoochie, pride))))
            expectMostRecentItem()

            setlist.removes.value = listOf(remove(5, "hoochie", "Hoochie Coochie Man", SetlistRemove.State.Sending))
            var model = awaitItem()
            assertEquals(RemovalUiModel.Removing("Quitando…"), model.removal("Hoochie Coochie Man"))
            assertTrue(model.removal("Crossroads") is RemovalUiModel.Idle)

            setlist.adds.value = listOf(
                SetlistAdd(
                    6,
                    jamDate,
                    SongId("red-house"),
                    "Red House",
                    "Jimi Hendrix",
                    Key("Bb"),
                    SetlistAdd.State.Failed(WriteOutcome.Offline),
                ),
            )
            setlist.removes.value = listOf(
                remove(5, "hoochie", "Hoochie Coochie Man", SetlistRemove.State.Failed(WriteOutcome.AccessRefused)),
                remove(
                    7,
                    "pride",
                    "Pride and Joy",
                    SetlistRemove.State.Failed(WriteOutcome.Rejected("duplicate_song")),
                ),
            )
            model = expectMostRecentItem()
            assertEquals(idleRemoval("hoochie"), model.removal("Hoochie Coochie Man"))
            assertEquals(listOf("Crossroads", "Hoochie Coochie Man", "Pride and Joy"), model.rows().map { it.title })
            val failures = checkNotNull((model as NextJamUiModel.Jam).admin).failures
            assertEquals(
                listOf(
                    "No se pudo quitar «Hoochie Coochie Man»" to
                        "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar.",
                    "No se pudo agregar «Red House»" to "No hay conexión. Probá de nuevo cuando tengas internet.",
                    "No se pudo quitar «Pride and Joy»" to
                        "Ese tema está repetido en la planilla. Corregilo ahí y probá de nuevo.",
                ),
                failures.map { it.title to it.message },
            )
            assertEquals(listOf("Cerrar", "Cerrar", "Cerrar"), failures.map { it.dismissLabel })

            failures.first().events(AddFailureUiModel.Event.Dismiss)
            assertEquals(listOf(5L), setlist.dismissed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removals of another jam are neither drawn on the rows nor as cards`() {
        val other = jamDate.plusDays(28)
        val removes = listOf(
            SetlistRemove(1, other, SongId("crossroads"), "Crossroads", SetlistRemove.State.Sending),
            SetlistRemove(
                2,
                other,
                SongId("crossroads"),
                "Crossroads",
                SetlistRemove.State.Failed(WriteOutcome.Offline),
            ),
        )
        val model = snapshot(jam(listOf(crossroads)))
            .toUiModel(today, admin = AdminState(emptyList(), {}, {}, RemovalState(removes)))
        assertTrue(model.removal("Crossroads") is RemovalUiModel.Idle)
        assertEquals(emptyList<AddFailureUiModel>(), checkNotNull((model as NextJamUiModel.Jam).admin).failures)
    }

    @Test
    fun `the two removal codes have their approved messages`() {
        assertEquals("Ese tema ya no estaba en la lista.", failureMessage(WriteOutcome.Rejected("song_not_in_setlist")))
        assertEquals(
            "Ese tema está repetido en la planilla. Corregilo ahí y probá de nuevo.",
            failureMessage(WriteOutcome.Rejected("duplicate_song")),
        )
    }

    @Test
    fun `scenario 9, the song that moves up into a removed song's place is not drawn expanded`() = runTest {
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(jam(listOf(crossroads, hoochie, thrill))))
            expectMostRecentItem().row("Hoochie Coochie Man").events(SongRowUiModel.Event.ToggleExpanded)
            assertEquals(listOf("Hoochie Coochie Man"), awaitItem().rows().filter { it.isExpanded }.map { it.title })

            // The server removed Hoochie and renumbered: The Thrill Is Gone now sits at position 2.
            repository.snapshots.emit(snapshot(jam(listOf(crossroads, thrill.copy(position = 2)))))
            val after = awaitItem()
            assertEquals(listOf(1, 2), after.rows().map { it.position })
            assertEquals(emptyList<String>(), after.rows().filter { it.isExpanded }.map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `scenario 10, removing the last song leaves the admin's empty state and the musician's published one`() {
        val empty = JamsSnapshot(
            upcoming = Jam(
                jamDate,
                LocalTime.of(21, 0),
                "La Macanuda",
                JamStatus.PUBLISHED,
                Setlist.Available(emptyList()),
            ),
            past = emptyList(),
            freshness = fetched,
        )
        val admin = empty.toUiModel(today, admin = AdminState(emptyList(), {}, {})) as NextJamUiModel.Jam
        assertEquals(
            SetlistUiModel.Empty(EmptyStateUiModel("Todavía no hay temas", "Agregá el primero desde el catálogo.")),
            admin.setlist,
        )
        val musician = empty.toUiModel(today) as NextJamUiModel.Jam
        assertEquals("Todavía no hay temas", (musician.setlist as SetlistUiModel.Empty).empty.title)
    }
}
