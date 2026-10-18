package com.bbbjam.feature.assistant.apply

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.model.Instrument
import com.bbbjam.core.model.JamStatus
import com.bbbjam.core.model.Key
import com.bbbjam.core.model.Lineup
import com.bbbjam.core.model.Setlist
import com.bbbjam.core.model.Slot
import com.bbbjam.core.model.SongId
import com.bbbjam.feature.assistant.AssistantUiModel
import com.bbbjam.feature.assistant.CallLog
import com.bbbjam.feature.assistant.ChatScope
import com.bbbjam.feature.assistant.FakeSetlistAssistant
import com.bbbjam.feature.assistant.FakeSetlistSongAdder
import com.bbbjam.feature.assistant.FakeSetlistSongMover
import com.bbbjam.feature.assistant.FakeSetlistSongRemover
import com.bbbjam.feature.assistant.ReplyUiModel
import com.bbbjam.feature.assistant.SettableJamsRepository
import com.bbbjam.feature.assistant.chat.AssistantChat
import com.bbbjam.feature.assistant.jam
import com.bbbjam.feature.assistant.jamSong
import com.bbbjam.feature.assistant.listed
import com.bbbjam.feature.assistant.listedOrder
import com.bbbjam.feature.assistant.proposal
import com.bbbjam.feature.assistant.run.ApplyMode
import com.bbbjam.feature.assistant.snapshot
import com.bbbjam.feature.assistant.testApplier
import com.bbbjam.feature.assistant.testPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Agregar and Reemplazar in the chat (`assistant-proposal-targets`, Part B): dialog, confirm, run, summary. */
class ReplacePresenterTest {
    private val assistant = FakeSetlistAssistant()
    private val log: CallLog = mutableListOf()
    private val adder = FakeSetlistSongAdder(log)
    private val remover = FakeSetlistSongRemover(log)
    private val mover = FakeSetlistSongMover(log)
    private val jams = SettableJamsRepository()
    private val applier = testApplier(
        adder,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        remover,
        mover,
    )

    // The list holds s2 (with two musicians), s9 and s10; the proposal is s1 to s4.
    private fun listWithMusicians() = Setlist.Available(
        listOf(
            jamSong(2).copy(position = 1),
            jamSong(9).copy(
                position = 2,
                lineup = Lineup(listOf(Slot(Instrument.GUITAR, "Ana"), Slot(Instrument.BASS, "Beto"))),
            ),
            jamSong(10).copy(position = 3, key = Key("Em")),
        ),
    )

    private fun TestScope.chat() = AssistantChat(assistant, ChatScope(testScheduler).scope)

    private fun flow(chat: AssistantChat) =
        moleculeFlow(RecompositionMode.Immediate) { testPresenter(chat, applier, jams).present(Unit) }

    private fun AssistantChat.ask(reply: com.bbbjam.core.assistant.AssistantReply) {
        val deferred = assistant.expectReply()
        send("pedido")
        deferred.complete(reply)
    }

    private suspend fun ReceiveTurbine<AssistantUiModel>.awaitProposal(
        predicate: (ReplyUiModel.Proposal) -> Boolean = { true },
    ): ReplyUiModel.Proposal {
        while (true) {
            val chat = awaitItem() as? AssistantUiModel.Chat
            val proposal = chat?.turns?.lastOrNull()?.reply as? ReplyUiModel.Proposal
            if (proposal != null && predicate(proposal)) return proposal
        }
    }

    private fun ReplyUiModel.Proposal.targets() = apply as ApplyUiModel.Targets

    private fun ReplyUiModel.Proposal.replace() = targets().actions.first { it.target == ApplyMode.REPLACE }

    private fun ReplyUiModel.Proposal.add() = targets().actions.first { it.target == ApplyMode.ADD }

    @Test
    fun `the targets block offers Agregar, Reemplazar and Crear, all enabled`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listWithMusicians()))
        val chat = chat()
        chat.ask(proposal(songs = 4))
        flow(chat).test {
            val targets = awaitProposal { it.apply is ApplyUiModel.Targets }.targets()
            assertEquals(
                listOf(
                    "Agregar a la próxima jam",
                    "Reemplazar la lista de la próxima jam",
                    "Crear una jam nueva con esta lista",
                ),
                targets.actions.map { it.label },
            )
            assertEquals(listOf(ApplyMode.ADD, ApplyMode.REPLACE, ApplyMode.CREATE), targets.actions.map { it.target })
            assertTrue(targets.actions.all { it.enabled })
            assertTrue(targets.notes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the replace dialog names the date, the removed titles, the lost musicians, the kept and the added`() =
        runTest {
            jams.snapshot = snapshot(jam(setlist = listWithMusicians()))
            val chat = chat()
            chat.ask(proposal(songs = 4))
            flow(chat).test {
                awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
                val dialog = awaitProposal { it.targets().confirmation != null }.targets().confirmation!!

                assertEquals("¿Reemplazar la lista?", dialog.title)
                assertEquals(
                    listOf(
                        "La lista de la jam del sábado 31 de octubre va a quedar igual a esta: 4 temas, en este orden.",
                        "Se quitan 2 temas: Tema 9, Tema 10.",
                        "Se pierden 2 músicos anotados en esos temas.",
                        "1 ya estaba: conserva su tonalidad y sus músicos.",
                        "Se agregan 3 temas con la tonalidad del catálogo.",
                    ),
                    dialog.lines,
                )
                assertEquals("Reemplazar", dialog.confirmLabel)
                assertEquals("Cancelar", dialog.cancelLabel)
                assertTrue(dialog.destructive)
                assertTrue(log.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an empty list says so and the dialog is not destructive`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listed()))
        val chat = chat()
        chat.ask(proposal(songs = 2))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
            val dialog = awaitProposal { it.targets().confirmation != null }.targets().confirmation!!

            assertEquals("La lista está vacía: se cargan 2 temas.", dialog.lines.last())
            assertFalse(dialog.destructive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling the replace dialog writes nothing`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listWithMusicians()))
        val chat = chat()
        chat.ask(proposal(songs = 4))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
            val dialog = awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
            dialog.events(ConfirmationUiModel.Event.Cancel)

            assertNull(awaitProposal { it.targets().confirmation == null }.targets().confirmation)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(log.isEmpty())
    }

    @Test
    fun `confirming runs adds, removes and moves in that order, and the success needs the matching list`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listWithMusicians()))
        val chat = chat()
        chat.ask(proposal(songs = 4))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
            val dialog = awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
            dialog.events(ConfirmationUiModel.Event.Confirm)
            dialog.events(ConfirmationUiModel.Event.Confirm)

            // The jams snapshot still shows the old list: the run finished but the check fails (R3).
            val mismatch = awaitProposal {
                it.summary?.text?.startsWith("La lista de la próxima jam no quedó") ==
                    true
            }
            assertEquals(
                "La lista de la próxima jam no quedó igual a la propuesta. Revisala en Próxima jam.",
                mismatch.summary!!.text,
            )
            assertEquals(ApplyTone.FAILURE, mismatch.summary!!.tone)
            assertEquals(listOf("Quitado", "Quitado"), mismatch.removals.map { it.text })
            assertEquals(listOf("Tema 9", "Tema 10"), mismatch.removals.map { it.title })

            jams.snapshot = snapshot(jam(setlist = listedOrder(1, 2, 3, 4)))
            val done = awaitProposal { it.summary?.text?.startsWith("Listo") == true }
            assertEquals(
                "Listo: la lista de la jam del sábado 31 de octubre quedó igual a la propuesta.",
                done.summary!!.text,
            )
            assertEquals(ApplyTone.SUCCESS, done.summary!!.tone)
            assertEquals(ApplyUiModel.None, done.apply)
            cancelAndIgnoreRemainingEvents()
        }
        // Confirmed twice, replaced once: three adds, two removes, then only the needed moves.
        assertEquals(3, adder.calls.size)
        assertEquals(2, remover.calls.size)
        assertEquals(listOf("add s1", "add s3", "add s4", "remove s9", "remove s10", "move s1@1"), log)
        assertEquals(1, mover.calls.size)
        assertFalse(adder.calls.any { it.songId == SongId("s2") })
    }

    @Test
    fun `a failed add cuts the replace before any removal and the summary says so`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listWithMusicians()))
        adder.reject(3, WriteOutcome.Offline)
        val chat = chat()
        chat.ask(proposal(songs = 4))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
            awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
                .events(ConfirmationUiModel.Event.Confirm)

            val cut = awaitProposal { it.summary?.text?.startsWith("Se cortó el reemplazo antes") == true }
            assertTrue(cut.summary!!.text.contains("se agregaron 1 de 3 y no se quitó ninguno"))
            assertEquals(listOf("Sin intentar", "Sin intentar"), cut.removals.map { it.text })
            // Not a success: the actions are back, and the replace one offers the retry.
            assertEquals("Reintentar el reemplazo", cut.replace().label)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(remover.calls.isEmpty())
    }

    @Test
    fun `a move refused as unordered_setlist gets its own line`() = runTest {
        jams.snapshot = snapshot(jam(setlist = Setlist.Available(listOf(jamSong(2).copy(position = 1)))))
        mover.reject(1, WriteOutcome.Rejected("unordered_setlist"))
        val chat = chat()
        chat.ask(proposal(songs = 2))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.replace().events(TargetActionUiModel.Event.Request)
            awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
                .events(ConfirmationUiModel.Event.Confirm)

            val ordered = awaitProposal { it.summary?.text?.startsWith("Los temas quedaron cargados") == true }
            assertEquals(
                "Los temas quedaron cargados, pero no en el orden propuesto. " +
                    "La planilla tiene posiciones repetidas o vacías; ordená la lista a mano.",
                ordered.summary!!.text,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a list equal to the proposal disables Reemplazar with its note`() = runTest {
        jams.snapshot = snapshot(jam(setlist = listedOrder(1, 2)))
        val chat = chat()
        chat.ask(proposal(songs = 2))
        flow(chat).test {
            val targets = awaitProposal { it.apply is ApplyUiModel.Targets }.targets()
            assertFalse(targets.actions.first { it.target == ApplyMode.REPLACE }.enabled)
            assertEquals(listOf("La lista de la próxima jam ya es igual a esta."), targets.notes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Agregar still adds without removing or moving`() = runTest {
        jams.snapshot = snapshot(jam(JamStatus.DRAFT, Setlist.Available(listOf(jamSong(9).copy(position = 1)))))
        val chat = chat()
        chat.ask(proposal(songs = 2))
        flow(chat).test {
            awaitProposal { it.apply is ApplyUiModel.Targets }.add().events(TargetActionUiModel.Event.Request)
            awaitProposal { it.targets().confirmation != null }.targets().confirmation!!
                .events(ConfirmationUiModel.Event.Confirm)
            awaitProposal { it.summary?.text?.startsWith("Listo") == true }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("add s1", "add s2"), log)
    }
}
