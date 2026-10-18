package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.feature.assistant.jam.JamCopy
import com.bbbjam.feature.assistant.run.JamFailure
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every server code against the sentence the assistant showed before the codes became typed
 * (`typed-write-rejections`). The expected strings are written out here, never read from the copy.
 */
class WriteFailureMappingTest {
    private val accessRefused =
        "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar."
    private val unavailable = "El servidor no respondió. Probá de nuevo en un rato."
    private val inCatalog = "Ese tema ya no está en el catálogo."
    private val jamChanged = "La jam cambió en la planilla. Actualizá y probá de nuevo."
    private val sheetRefused = "La planilla rechazó el cambio. Revisala y probá de nuevo."
    private val unordered = "La planilla tiene posiciones repetidas o vacías; ordená la lista a mano."

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
        "zzz",
        "",
    )

    private fun write(code: String) = when (code) {
        "unknown_song" -> inCatalog
        "unknown_jam", "jam_not_editable" -> jamChanged
        else -> sheetRefused
    }

    @Test
    fun `writeFailure maps every code and outcome`() {
        allCodes.forEach { assertEquals(it, write(it), ApplyOutcomeCopy.writeFailure(WriteOutcome.Rejected(it))) }
        assertEquals(accessRefused, ApplyOutcomeCopy.writeFailure(WriteOutcome.AccessRefused))
        assertEquals(unavailable, ApplyOutcomeCopy.writeFailure(WriteOutcome.Unavailable))
        assertEquals(unavailable, ApplyOutcomeCopy.writeFailure(WriteOutcome.Done))
        assertEquals(
            "No pudimos confirmar si se agregó: no hay conexión o el servidor tardó.",
            ApplyOutcomeCopy.writeFailure(WriteOutcome.Offline),
        )
    }

    @Test
    fun `moveFailure maps every code`() {
        allCodes.forEach {
            val expected = if (it == "unordered_setlist") unordered else write(it)
            assertEquals(it, expected, ReplaceOutcomeCopy.moveFailure(WriteOutcome.Rejected(it)))
        }
    }

    @Test
    fun `jam creation reasons map every code`() {
        val own = mapOf(
            "jam_exists" to "ya hay una jam con esa fecha en la planilla.",
            "tab_exists" to "ya hay una pestaña con esa fecha en la planilla; revisala antes de crear la jam.",
            "past_date" to "esa fecha ya pasó.",
            "invalid_date" to "la planilla rechazó los datos de la jam.",
            "invalid_time" to "la planilla rechazó los datos de la jam.",
            "invalid_venue" to "la planilla rechazó los datos de la jam.",
        )
        allCodes.forEach {
            val reason = JamCopy.reason(JamFailure.Write(WriteOutcome.Rejected(it)))
            assertEquals(it, own[it] ?: sheetRefused, reason)
        }
        assertEquals(accessRefused, JamCopy.reason(JamFailure.Write(WriteOutcome.AccessRefused)))
        assertEquals(unavailable, JamCopy.reason(JamFailure.Write(WriteOutcome.Unavailable)))
    }
}
