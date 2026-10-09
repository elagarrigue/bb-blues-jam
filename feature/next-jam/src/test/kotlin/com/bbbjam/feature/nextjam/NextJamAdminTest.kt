package com.bbbjam.feature.nextjam

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.bbbjam.core.data.Freshness
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.jams.JamCalendar
import com.bbbjam.core.data.jams.JamsSnapshot
import com.bbbjam.core.data.setlist.SetlistAdd
import com.bbbjam.core.data.setlist.SetlistPublish
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
import com.bbbjam.core.ui.state.EmptyStateUiModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `admin-add-song-to-setlist` Part B: the admin's layer on Próxima jam. The copy is written out from
 * the approved table (C1), never read from [NextJamCopy].
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

    private fun presenter() = NextJamPresenter(repository, calendar, session, setlist)

    private fun add(id: Long, state: SetlistAdd.State, date: LocalDate = jamDate, title: String = "Crossroads") =
        SetlistAdd(id, date, SongId("crossroads"), title, "Eric Clapton", Key("A"), state)

    private fun adminState(
        adds: List<SetlistAdd> = emptyList(),
        onAdd: (LocalDate) -> Unit = {},
        onDismiss: (Long) -> Unit = {},
        publishes: List<SetlistPublish> = emptyList(),
        publishConfirming: String? = null,
        onRequestPublish: (LocalDate) -> Unit = {},
        onCancelPublish: (LocalDate) -> Unit = {},
        onConfirmPublish: (LocalDate) -> Unit = {},
        onRetryPublish: (LocalDate, Long) -> Unit = { _, _ -> },
    ) = AdminState(
        adds,
        onAdd,
        onDismiss,
        publishes = publishes,
        publishConfirming = publishConfirming,
        onRequestPublish = onRequestPublish,
        onCancelPublish = onCancelPublish,
        onConfirmPublish = onConfirmPublish,
        onRetryPublish = onRetryPublish,
    )

    private fun NextJamUiModel.asJam() = this as NextJamUiModel.Jam

    @Test
    fun `the admin sees a draft's songs with the badge, the note and Agregar tema, a musician sees the card`() {
        val draft = snapshot(jam(Setlist.Available(listOf(redHouse, boogie))))

        val model = draft.toUiModel(today, admin = adminState()).asJam()

        assertEquals(listOf("Red House", "Boogie"), (model.setlist as SetlistUiModel.Songs).rows.map { it.title })
        val admin = checkNotNull(model.admin)
        assertEquals("Borrador", admin.status.badge)
        assertEquals("Los músicos todavía no ven esta lista.", admin.status.note)
        assertEquals(NextJamCopy.PUBLISH, (admin.status.publish as PublishUiModel.Idle).label)
        assertEquals("Agregar tema", admin.addSong?.label)
        assertEquals(emptyList<PendingRowUiModel>(), admin.pending)
        assertEquals(emptyList<AddFailureUiModel>(), admin.failures)

        val musician = draft.toUiModel(today).asJam()
        assertEquals(SetlistUiModel.Withheld::class, musician.setlist::class)
        assertNull(musician.admin)
    }

    @Test
    fun `publish confirmation is explicit and a sending publish keeps the confirmed draft badge`() = runTest {
        val hold = CompletableDeferred<Unit>().also { setlist.publishHold = it }
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(jam(Setlist.Available(listOf(redHouse, boogie)))))
            val idleJam = expectMostRecentItem() as NextJamUiModel.Jam
            val idle = idleJam.admin?.status?.publish as PublishUiModel.Idle
            idle.events(PublishUiModel.Event.Request)
            val confirmingJam = awaitItem() as NextJamUiModel.Jam
            val confirmation = confirmingJam.admin?.status?.publish as PublishUiModel.Confirming
            assertEquals(NextJamCopy.PUBLISH_PROMPT, confirmation.prompt)
            assertEquals(publishDetails(2), confirmation.details)
            assertEquals(NextJamCopy.PUBLISH_IRREVERSIBLE, confirmation.irreversibleNote)

            confirmation.events(PublishUiModel.Event.Cancel)
            val cancelled = awaitItem() as NextJamUiModel.Jam
            val idleAgain = cancelled.admin?.status?.publish as PublishUiModel.Idle
            idleAgain.events(PublishUiModel.Event.Request)
            val secondConfirmation = awaitItem() as NextJamUiModel.Jam
            val confirm = secondConfirmation.admin?.status?.publish as PublishUiModel.Confirming
            confirm.events(PublishUiModel.Event.Confirm)
            confirm.events(PublishUiModel.Event.Confirm)
            val sending = awaitItem() as NextJamUiModel.Jam
            assertEquals(1, setlist.publishCalls.size)
            assertEquals(NextJamCopy.DRAFT_BADGE, sending.admin?.status?.badge)
            assertEquals(NextJamCopy.PUBLISHING, (sending.admin?.status?.publish as PublishUiModel.Publishing).status)
            hold.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `admin extra form submits through repository and musician rows expose no editor`() = runTest {
        val extras = listOf(ExtraParticipant("Juan", "saxo"), ExtraParticipant("Mora", "trompeta"))
        val song = redHouse.copy(extraParticipants = extras)
        moleculeFlow(RecompositionMode.Immediate) { presenter().present(NextJamPresenter.Params()) }.test {
            repository.snapshots.emit(snapshot(jam(Setlist.Available(listOf(song)), JamStatus.PUBLISHED)))
            val initial = expectMostRecentItem() as NextJamUiModel.Jam
            val row = (initial.setlist as SetlistUiModel.Songs).rows.single()
            row.events(SongRowUiModel.Event.ToggleExpanded)
            val expanded = awaitItem() as NextJamUiModel.Jam
            var editor = checkNotNull((expanded.setlist as SetlistUiModel.Songs).rows.single().admin?.extraParticipants)
            assertEquals(extras, editor.extras)
            val removeLabels = editor.extras.map(::extraRemoveAccessibilityLabel)
            assertEquals(listOf("Quitar a Juan, saxo", "Quitar a Mora, trompeta"), removeLabels)
            assertEquals(2, removeLabels.distinct().size)
            editor.events(ExtraParticipantEditorUiModel.Event.Open)
            val form = awaitItem() as NextJamUiModel.Jam
            editor = checkNotNull((form.setlist as SetlistUiModel.Songs).rows.single().admin?.extraParticipants)
            assertTrue(editor.formVisible)
            editor.events(ExtraParticipantEditorUiModel.Event.NameChanged("Ana"))
            val named = awaitItem() as NextJamUiModel.Jam
            editor = checkNotNull((named.setlist as SetlistUiModel.Songs).rows.single().admin?.extraParticipants)
            editor.events(ExtraParticipantEditorUiModel.Event.InstrumentChanged("percusión"))
            val instrumented = awaitItem() as NextJamUiModel.Jam
            editor = checkNotNull((instrumented.setlist as SetlistUiModel.Songs).rows.single().admin?.extraParticipants)
            editor.events(ExtraParticipantEditorUiModel.Event.Submit)
            assertEquals(
                FakeSetlistRepository.ExtraAddCall(jamDate, song.songId, "Ana", "percusión"),
                setlist.extraAddCalls.single(),
            )
            val musician = snapshot(jam(Setlist.Available(listOf(song)), JamStatus.PUBLISHED)).toUiModel(today).asJam()
            assertNull((musician.setlist as SetlistUiModel.Songs).rows.single().admin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty drafts hide publish and a failed publish stays in the status block`() {
        val empty = snapshot(jam(Setlist.Available(emptyList()))).toUiModel(today, admin = adminState()).asJam()
        assertNull(empty.admin?.status?.publish)
        val unreadable = snapshot(jam(Setlist.Unavailable(SetlistProblem.INVALID_TAB)))
            .toUiModel(today, admin = adminState()).asJam()
        assertNull(unreadable.admin?.status?.publish)

        val dismissed = mutableListOf<Long>()
        val retries = mutableListOf<Pair<LocalDate, Long>>()
        val entry = SetlistPublish(51, jamDate, SetlistPublish.State.Failed(WriteOutcome.Offline))
        val model = snapshot(jam(Setlist.Available(listOf(redHouse))))
            .toUiModel(
                today,
                admin = adminState(
                    publishes = listOf(entry),
                    onDismiss = dismissed::add,
                    onRetryPublish = { date, id -> retries += date to id },
                ),
            ).asJam()
        val failed = model.admin?.status?.publish as PublishUiModel.Failed
        assertEquals(NextJamCopy.PUBLISH_FAILED, failed.title)
        assertEquals(NextJamCopy.PUBLISH_OFFLINE, failed.message)
        assertEquals(NextJamCopy.PUBLISH_CONSEQUENCE, failed.consequence)
        failed.events(PublishUiModel.Event.Retry)
        assertEquals(listOf(jamDate to 51L), retries)
        failed.events(PublishUiModel.Event.Dismiss)
        assertEquals(listOf(51L), dismissed)
        assertTrue(model.admin?.failures?.isEmpty() == true)

        val published = snapshot(jam(Setlist.Available(listOf(redHouse)), JamStatus.PUBLISHED))
            .toUiModel(today, admin = adminState(publishes = listOf(entry))).asJam()
        assertNull(published.admin?.status?.publish)
    }

    @Test
    fun `published jam has no publish action and bad setlists hide it`() {
        val published = snapshot(jam(Setlist.Available(listOf(redHouse)), JamStatus.PUBLISHED))
            .toUiModel(today, admin = adminState()).asJam()
        assertEquals("Publicada", published.admin?.status?.badge)
        assertEquals(NextJamCopy.PUBLISHED_NOTE, published.admin?.status?.note)
        assertNull(published.admin?.status?.publish)
        assertEquals("Agregar tema", published.admin?.addSong?.label)

        val withheld = snapshot(jam(Setlist.Withheld)).toUiModel(today, admin = adminState()).asJam()
        assertEquals(SetlistUiModel.Withheld::class, withheld.setlist::class)
        assertEquals("Borrador", withheld.admin?.status?.badge)
        assertNull(withheld.admin?.status?.publish)
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
            SetlistUiModel.Empty(EmptyStateUiModel("Todavía no hay temas", "Agregá el primero desde el catálogo.")),
            admin.setlist,
        )
        assertEquals("Agregar tema", admin.admin?.addSong?.label)

        val musician = empty.toUiModel(today).asJam()
        assertEquals(
            "La lista está publicada pero todavía no tiene temas. ¿Tenés uno en mente? Contáselo a la organización.",
            (musician.setlist as SetlistUiModel.Empty).empty.message,
        )
    }

    @Test
    fun `with no upcoming jam the admin gets the Sheet hint and no button, a musician gets no hint`() {
        val none = snapshot(null)

        val admin = none.toUiModel(today, admin = adminState()) as NextJamUiModel.NoUpcomingJam
        assertEquals("Para armar la lista, cargá la fecha en la pestaña Jams de la planilla.", admin.adminHint)
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
        assertEquals(listOf(PendingRowUiModel(1, "Crossroads", "Agregando…")), admin.pending)
        val failure = admin.failures.single()
        assertEquals(2L, failure.id)
        assertEquals("No se pudo agregar «Red House»", failure.title)
        assertEquals(
            "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar.",
            failure.message,
        )
        assertEquals("Cerrar", failure.dismissLabel)
        // The harmonica filter hides Boogie (bass only), never the admin's entries.
        assertEquals(listOf("Red House"), (model.setlist as SetlistUiModel.Songs).rows.map { it.title })
    }

    @Test
    fun `every outcome and code has its approved message`() {
        val expected = mapOf(
            WriteOutcome.AccessRefused to
                "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar.",
            WriteOutcome.Offline to "No hay conexión. Probá de nuevo cuando tengas internet.",
            WriteOutcome.Unavailable to "El servidor no respondió. Probá de nuevo en un rato.",
            WriteOutcome.Rejected("song_already_in_setlist") to "Ese tema ya está en la lista.",
            WriteOutcome.Rejected("unknown_song") to "Ese tema ya no está en el catálogo.",
            WriteOutcome.Rejected("unknown_jam") to "La jam cambió en la planilla. Actualizá y probá de nuevo.",
            WriteOutcome.Rejected("jam_not_editable") to "La jam cambió en la planilla. Actualizá y probá de nuevo.",
            WriteOutcome.Rejected("duplicate_date") to "La jam cambió en la planilla. Actualizá y probá de nuevo.",
            WriteOutcome.Rejected("missing_header") to "La planilla rechazó el cambio. Revisala y probá de nuevo.",
            WriteOutcome.Rejected("invalid_key") to "La planilla rechazó el cambio. Revisala y probá de nuevo.",
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
                assertEquals("Borrador", model.admin?.status?.badge)

                setlist.adds.value = listOf(add(4, SetlistAdd.State.Failed(WriteOutcome.Rejected("busy"))))
                model = awaitItem().asJam()
                checkNotNull(model.admin).failures.single().events(AddFailureUiModel.Event.Dismiss)
                checkNotNull(model.admin?.addSong).events(AddSongActionUiModel.Event.Open)
                assertEquals(listOf(4L), setlist.dismissed)
                assertEquals(listOf(jamDate), opened)

                // Logging out turns the same cache back into the musician's draft card.
                session.isAdmin.value = false
                model = awaitItem().asJam()
                assertNull(model.admin)
                assertEquals(SetlistUiModel.Withheld::class, model.setlist::class)
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
                .chips.single { it.label == "Armónica" }.events(FilterChipUiModel.Event.Toggle)
            val filtered = awaitItem().asJam()
            assertEquals(listOf("Red House"), (filtered.setlist as SetlistUiModel.Songs).rows.map { it.title })
            assertEquals("Agregar tema", filtered.admin?.addSong?.label)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
