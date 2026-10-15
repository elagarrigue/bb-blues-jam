package com.bbbjam.feature.assistant

import com.bbbjam.core.data.assistant.AssistantFailure
import com.bbbjam.core.data.assistant.AssistantReply
import com.bbbjam.core.data.assistant.AssistantStatus
import com.bbbjam.core.ui.presenter.EventHandler
import com.bbbjam.core.ui.state.EmptyStateUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure mapping, with the copy written out from the approved table (not read from AssistantCopy). */
class AssistantMappingTest {
    private val handlers = testHandlers()

    private val available = ServiceState.Known(AssistantStatus.Available)

    private fun chat(turns: List<ChatTurn> = emptyList(), draft: String = "", service: ServiceState = available) =
        assistantModel(true, turns, service, draft, handlers) as AssistantUiModel.Chat

    private fun answered(id: Long, reply: AssistantReply) = ChatTurn(id, "pedido $id", ChatTurn.State.Answered(reply))

    private fun replyOf(reply: AssistantReply) = chat(listOf(answered(1, reply))).turns.single().reply

    @Test
    fun `not configured shows only the title and the block`() {
        val model = assistantModel(false, listOf(answered(1, proposal())), available, "hola", handlers)
        assertEquals(
            AssistantUiModel.NotConfigured(
                "Asistente",
                EmptyStateUiModel(
                    "El asistente no está configurado",
                    "Esta versión de la app no tiene la clave del servicio, así que no se pueden pedir listas.",
                ),
            ),
            model,
        )
    }

    @Test
    fun `the empty chat has the intro and a disabled Enviar`() {
        val model = chat()
        assertEquals("Asistente", model.title)
        assertEquals(
            EmptyStateUiModel(
                "Pedí una lista",
                "Contale al asistente qué jam querés armar: cuántos temas, qué estilo, cómo arrancar y cómo " +
                    "cerrar. Solo propone temas del catálogo.",
            ),
            model.intro,
        )
        assertTrue(model.turns.isEmpty())
        assertEquals("Tu pedido", model.composer.label)
        assertEquals("Ej.: 12 temas de blues nacional, con inicio y final arriba", model.composer.placeholder)
        assertEquals("Enviar", model.composer.sendLabel)
        assertFalse(model.composer.sendEnabled)
    }

    @Test
    fun `the intro is gone as soon as there is a turn`() {
        assertNull(chat(listOf(ChatTurn(1, "uno", ChatTurn.State.Pending))).intro)
    }

    @Test
    fun `Enviar is enabled only with non-blank text and no pending turn`() {
        assertTrue(chat(draft = "hola").composer.sendEnabled)
        assertFalse(chat(draft = "   ").composer.sendEnabled)
        val pending = chat(listOf(ChatTurn(1, "uno", ChatTurn.State.Pending)), draft = "hola").composer
        assertFalse(pending.sendEnabled)
        assertEquals("Esperando respuesta…", pending.sendLabel)
        assertEquals("hola", pending.text)
        val done = chat(listOf(answered(1, proposal())), draft = "hola").composer
        assertTrue(done.sendEnabled)
        assertEquals("Enviar", done.sendLabel)
    }

    @Test
    fun `a pending turn shows the wait copy and a Cancelar keyed by its id`() {
        val turn = chat(listOf(ChatTurn(7, "uno", ChatTurn.State.Pending))).turns.single()
        assertEquals(7, turn.id)
        assertEquals("Vos", turn.authorLabel)
        assertEquals("uno", turn.request)
        assertEquals(
            ReplyUiModel.Pending(
                "Armando la propuesta…",
                "Puede tardar unos segundos.",
                "Cancelar",
                EventHandler(7L) {
                },
            ),
            turn.reply,
        )
    }

    @Test
    fun `a proposal lists numbered songs, the message, questions and no ids`() {
        val reply = replyOf(proposal(songs = 2, message = "Cerramos arriba.", questions = listOf("¿Con armónica?")))
        assertEquals(
            ReplyUiModel.Proposal(
                authorLabel = "Asistente",
                heading = "PROPUESTA · 2 temas",
                songs = listOf(
                    ProposedSongUiModel("01", "Tema 1", "Artista 1", "Bm", "Tonalidad Bm", "Razón 1"),
                    ProposedSongUiModel("02", "Tema 2", "Artista 2", "Bm", "Tonalidad Bm", "Razón 2"),
                ),
                message = "Cerramos arriba.",
                questionsHeading = "PREGUNTAS",
                questions = listOf("¿Con armónica?"),
                discardedNote = null,
            ),
            reply,
        )
    }

    @Test
    fun `one song says tema and no questions means no heading`() {
        val reply = replyOf(proposal(songs = 1)) as ReplyUiModel.Proposal
        assertEquals("PROPUESTA · 1 tema", reply.heading)
        assertNull(reply.questionsHeading)
        assertTrue(reply.questions.isEmpty())
    }

    @Test
    fun `zero songs has no heading and no rows, just the message and questions`() {
        val reply = replyOf(proposal(songs = 0, message = "¿Cuántos temas?", questions = listOf("¿Cuántos?"))) as
            ReplyUiModel.Proposal
        assertNull(reply.heading)
        assertTrue(reply.songs.isEmpty())
        assertEquals("¿Cuántos temas?", reply.message)
        assertEquals("PREGUNTAS", reply.questionsHeading)
    }

    @Test
    fun `the discarded note counts rejected ids in the singular and plural and never shows them`() {
        val none = replyOf(proposal(rejected = emptyList())) as ReplyUiModel.Proposal
        assertNull(none.discardedNote)
        val one = replyOf(proposal(rejected = listOf("zz-secreto"))) as ReplyUiModel.Proposal
        assertEquals("Se descartó 1 tema que no está en el catálogo.", one.discardedNote)
        val two = replyOf(proposal(rejected = listOf("a-1", "b-2"))) as ReplyUiModel.Proposal
        assertEquals("Se descartaron 2 temas que no están en el catálogo.", two.discardedNote)
        assertFalse(two.toString().contains("a-1"))
    }

    @Test
    fun `every failure has its Spanish copy and the status is never shown`() {
        val expected = mapOf(
            AssistantFailure.Offline to "No hay conexión. Para usar el asistente necesitás internet.",
            AssistantFailure.Unauthorized to "El servicio rechazó la clave del asistente.",
            AssistantFailure.Unavailable(503) to
                "El servicio del asistente no respondió. Probá de nuevo en un rato.",
            AssistantFailure.InvalidResponse to "No se pudo leer la respuesta del asistente. Probá de nuevo.",
            AssistantFailure.TooManySteps to
                "El asistente no llegó a una propuesta. Probá con un pedido más concreto.",
            AssistantFailure.CatalogEmpty to
                "El catálogo todavía no se cargó. Abrí la app con conexión y volvé a probar.",
        )
        expected.forEach { (failure, text) ->
            val reply = replyOf(failed(failure)) as ReplyUiModel.Failed
            assertEquals(text, reply.message)
            assertEquals("Reintentar", reply.retry?.label)
        }
        assertFalse((replyOf(failed(AssistantFailure.Unavailable(503))) as ReplyUiModel.Failed).message.contains("503"))
        // The sealed type has exactly these six members.
        assertEquals(6, expected.size)
    }

    @Test
    fun `cancelled and interrupted turns have their copy and a retry when last`() {
        val cancelled = chat(listOf(ChatTurn(1, "a", ChatTurn.State.Cancelled))).turns.single().reply
        assertEquals(
            ReplyUiModel.Cancelled(
                "Cancelaste este pedido.",
                RetryUiModel(
                    "Reintentar",
                    EventHandler(1L) {
                    },
                ),
            ),
            cancelled,
        )
        val interrupted = chat(listOf(ChatTurn(1, "a", ChatTurn.State.Interrupted))).turns.single().reply
        assertEquals(
            ReplyUiModel.Failed(
                "No se pudo terminar este pedido. Probá de nuevo.",
                RetryUiModel("Reintentar", EventHandler(1L) {}),
            ),
            interrupted,
        )
    }

    @Test
    fun `a NotConfigured reply maps to the not-configured message without retry`() {
        val reply = replyOf(AssistantReply.NotConfigured) as ReplyUiModel.Failed
        assertEquals(
            "Esta versión de la app no tiene la clave del servicio, así que no se pueden pedir listas.",
            reply.message,
        )
        assertNull(reply.retry)
    }

    @Test
    fun `retry is offered on the last turn only`() {
        val turns = listOf(
            ChatTurn(1, "a", ChatTurn.State.Cancelled),
            answered(2, failed(AssistantFailure.Offline)),
            ChatTurn(3, "c", ChatTurn.State.Interrupted),
        )
        val replies = chat(turns).turns.map { it.reply }
        assertNull((replies[0] as ReplyUiModel.Cancelled).retry)
        assertNull((replies[1] as ReplyUiModel.Failed).retry)
        assertEquals("Reintentar", (replies[2] as ReplyUiModel.Failed).retry?.label)
    }

    @Test
    fun `no banner when the service is Available or the build is not configured for it`() {
        assertEquals(StatusUiModel.None, chat().status)
        assertEquals(StatusUiModel.None, chat(service = ServiceState.Known(AssistantStatus.NotConfigured)).status)
    }

    @Test
    fun `checking shows a static line and disables Enviar`() {
        val model = chat(draft = "hola", service = ServiceState.Checking)
        assertEquals(StatusUiModel.Checking("Comprobando el servicio…"), model.status)
        assertFalse(model.composer.sendEnabled)
        assertEquals("Enviar", model.composer.sendLabel)
    }

    @Test
    fun `each problem has its banner copy, Reintentar, and a disabled Enviar`() {
        val expected = mapOf(
            AssistantStatus.ServiceDown to
                "El asistente no está disponible en este momento. El servicio de IA no responde; probá más tarde.",
            AssistantStatus.Offline to "No hay conexión. Para usar el asistente necesitás internet.",
            AssistantStatus.Unauthorized to "El servicio rechazó la clave del asistente.",
        )
        expected.forEach { (status, text) ->
            val model = chat(draft = "hola", service = ServiceState.Known(status))
            assertEquals(
                StatusUiModel.Banner(text, "Reintentar", EventHandler {}),
                model.status,
            )
            assertFalse("$status", model.composer.sendEnabled)
        }
    }

    @Test
    fun `a turn's Reintentar is withheld while the service is not available`() {
        val turns = listOf(answered(1, failed(AssistantFailure.Offline)))
        assertEquals("Reintentar", ((chat(turns).turns.single().reply) as ReplyUiModel.Failed).retry?.label)
        val down = chat(turns, service = ServiceState.Known(AssistantStatus.ServiceDown))
        assertNull((down.turns.single().reply as ReplyUiModel.Failed).retry)
        val checking = chat(turns, service = ServiceState.Checking)
        assertNull((checking.turns.single().reply as ReplyUiModel.Failed).retry)
    }

    @Test
    fun `the apply copy is written out for singular and plural`() {
        val date = java.time.LocalDate.of(2026, 10, 31)
        assertEquals(
            "Listo: se agregaron 11 temas a la jam del sábado 31 de octubre. 1 ya estaba en la lista.",
            ApplyOutcomeCopy.success(11, 1, date),
        )
        assertEquals("Listo: se agregó 1 tema a la jam del sábado 31 de octubre.", ApplyOutcomeCopy.success(1, 0, date))
        assertEquals(
            "No se agregó ningún tema a la jam del sábado 31 de octubre. 2 ya estaban en la lista.",
            ApplyOutcomeCopy.success(0, 2, date),
        )
        assertEquals("Se agregaron 10 de 11 temas. 1 no se agregó.", ApplyOutcomeCopy.partial(10, 11, 1))
        assertEquals("Se agregaron 8 de 11 temas. 3 no se agregaron.", ApplyOutcomeCopy.partial(8, 11, 3))
        assertEquals("Detuviste la carga: se agregaron 4 de 11 temas.", ApplyOutcomeCopy.stopped(4, 11))
        assertEquals("Agregar 1 tema", ApplyCopy.applyConfirm(1))
        assertEquals(
            "3 ya están en la lista y no se agregan: A, B, C.",
            ApplyCopy.applySkipped(listOf("A", "B", "C")),
        )
    }

    @Test
    fun `a success shows no action and no pending run draws the summary only`() {
        val turns = listOf(answered(1, proposal(songs = 2)))
        val ready = ApplyInputs(snapshot = snapshot())
        val action = (chat(turns).turns.single().reply as ReplyUiModel.Proposal).apply
        assertEquals(ApplyUiModel.None, action)
        val withSnapshot = assistantModel(true, turns, available, "", handlers, ready) as AssistantUiModel.Chat
        assertTrue((withSnapshot.turns.single().reply as ReplyUiModel.Proposal).apply is ApplyUiModel.Targets)
    }
}
