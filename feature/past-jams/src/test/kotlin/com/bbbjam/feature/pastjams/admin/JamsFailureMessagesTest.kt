package com.bbbjam.feature.pastjams.admin

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.setlist.jamcreate.CreateJamOutcome
import com.bbbjam.feature.pastjams.create.createFailureMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every server code against the text its card showed before the codes became typed
 * (`typed-write-rejections`). The expected strings are written out here, never read from the copy.
 */
class JamsFailureMessagesTest {
    private val accessRefused =
        "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar."
    private val offline = "No hay conexión. Probá de nuevo cuando tengas internet."
    private val unavailable = "El servidor no respondió. Probá de nuevo en un rato."
    private val jamChanged = "La jam cambió en la planilla. Actualizá y probá de nuevo."
    private val sheetRefused = "La planilla rechazó el cambio. Revisala y probá de nuevo."
    private val createExists = "Ya hay una jam o una pestaña con esa fecha en la planilla."
    private val createInvalid = "La planilla rechazó los datos. Revisalos y probá de nuevo."
    private val datePast = "Esa fecha ya pasó."
    private val setlistChanged = "La lista cambió mientras tanto. Revisala y probá de nuevo."
    private val emptyPublish = "La lista no tiene temas. Agregá al menos uno antes de publicar."
    private val publishOffline =
        "No pudimos confirmar la publicación: no hay conexión o el servidor tardó. " +
            "Reintentá; si ya estaba publicada, no pasa nada."

    private val allCodes = listOf(
        "song_already_in_setlist",
        "song_not_in_setlist",
        "duplicate_song",
        "unknown_song",
        "unknown_jam",
        "jam_not_editable",
        "duplicate_date",
        "slot_filled",
        "instrument_not_expandable",
        "invalid_name",
        "slot_taken",
        "slot_not_in_lineup",
        "slot_empty",
        "slot_changed",
        "unordered_setlist",
        "jam_exists",
        "tab_exists",
        "past_date",
        "setlist_changed",
        "empty_setlist",
        "invalid_song",
        "invalid_key",
        "invalid_date",
        "invalid_time",
        "invalid_venue",
        "invalid_status",
        "zzz",
        "",
    )

    private val jamCodes = setOf("unknown_jam", "jam_not_editable", "duplicate_date")

    private fun failure(code: String) = if (code in jamCodes) jamChanged else sheetRefused

    private fun create(code: String) = when {
        code == "jam_exists" || code == "tab_exists" -> createExists
        code == "past_date" -> datePast
        code.startsWith("invalid_") -> createInvalid
        else -> failure(code)
    }

    @Test
    fun `failureMessage maps every code and outcome`() {
        allCodes.forEach { assertEquals(it, failure(it), failureMessage(WriteOutcome.Rejected(it))) }
        assertEquals(accessRefused, failureMessage(WriteOutcome.AccessRefused))
        assertEquals(offline, failureMessage(WriteOutcome.Offline))
        assertEquals(unavailable, failureMessage(WriteOutcome.Unavailable))
        assertEquals(unavailable, failureMessage(WriteOutcome.Done))
    }

    @Test
    fun `editFailureMessage maps every code`() {
        allCodes.forEach {
            val expected = if (it == "setlist_changed") setlistChanged else create(it)
            assertEquals(it, expected, editFailureMessage(WriteOutcome.Rejected(it)))
        }
        assertEquals(offline, editFailureMessage(WriteOutcome.Offline))
    }

    @Test
    fun `publishFailureMessage maps every code`() {
        allCodes.forEach {
            val expected = if (it == "empty_setlist") emptyPublish else failure(it)
            assertEquals(it, expected, publishFailureMessage(WriteOutcome.Rejected(it)))
        }
        assertEquals(publishOffline, publishFailureMessage(WriteOutcome.Offline))
        assertEquals(accessRefused, publishFailureMessage(WriteOutcome.AccessRefused))
        assertEquals(unavailable, publishFailureMessage(WriteOutcome.Unavailable))
    }

    @Test
    fun `createFailureMessage maps every code`() {
        allCodes.forEach {
            val outcome = CreateJamOutcome.NotCreated(WriteOutcome.Rejected(it))
            assertEquals(it, create(it), createFailureMessage(outcome))
        }
        assertEquals(offline, createFailureMessage(CreateJamOutcome.NotCreated(WriteOutcome.Offline)))
    }
}
