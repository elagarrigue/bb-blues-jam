package com.bbbjam.feature.nextjam.admin

import com.bbbjam.core.data.admin.WriteOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every server code against the text its card showed before the codes became typed
 * (`typed-write-rejections`). The expected strings are written out here, never read from the copy.
 */
class FailureMessagesTest {
    private val accessRefused =
        "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar."
    private val offline = "No hay conexión. Probá de nuevo cuando tengas internet."
    private val unavailable = "El servidor no respondió. Probá de nuevo en un rato."
    private val inCatalog = "Ese tema ya no está en el catálogo."
    private val jamChanged = "La jam cambió en la planilla. Actualizá y probá de nuevo."
    private val sheetRefused = "La planilla rechazó el cambio. Revisala y probá de nuevo."
    private val alreadyListed = "Ese tema ya está en la lista."
    private val notInSetlist = "Ese tema ya no estaba en la lista."
    private val duplicateSong = "Ese tema está repetido en la planilla. Corregilo ahí y probá de nuevo."
    private val slotFilled = "Ese cupo tiene un músico anotado. Liberalo antes de sacarlo."
    private val notExpandable =
        "Esta hoja todavía no tiene columnas para ese cupo. Agregalas en el Sheet y volvé a probar."
    private val invalidName = "La planilla no aceptó ese nombre."
    private val slotTaken = "Ese cupo ya está ocupado. Actualizá la lista y probá de nuevo."
    private val clearStale = "Ese cupo ya no está ocupado. Actualizá la lista para ver cómo quedó."
    private val unordered = "Las posiciones de la planilla están desordenadas. Corregilas ahí y probá de nuevo."

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

    private val families: List<(WriteOutcome) -> String> = listOf(
        ::failureMessage,
        ::keyFailureMessage,
        ::lineupFailureMessage,
        ::assignmentFailureMessage,
        ::slotClearFailureMessage,
        ::moveFailureMessage,
    )

    private val base = mapOf(
        "song_already_in_setlist" to alreadyListed,
        "song_not_in_setlist" to notInSetlist,
        "duplicate_song" to duplicateSong,
        "unknown_song" to inCatalog,
        "unknown_jam" to jamChanged,
        "jam_not_editable" to jamChanged,
        "duplicate_date" to jamChanged,
    )

    private val keyOverrides = mapOf("song_not_in_setlist" to jamChanged, "duplicate_song" to duplicateSong)

    private fun failure(code: String) = base[code] ?: sheetRefused

    private fun key(code: String) = keyOverrides[code] ?: failure(code)

    private fun check(fn: (WriteOutcome) -> String, overrides: Map<String, String>, fallback: (String) -> String) {
        allCodes.forEach { code ->
            assertEquals(code, overrides[code] ?: fallback(code), fn(WriteOutcome.Rejected(code)))
        }
    }

    @Test
    fun `the generic outcomes are the same in every family`() {
        families.forEach { fn ->
            assertEquals(accessRefused, fn(WriteOutcome.AccessRefused))
            assertEquals(offline, fn(WriteOutcome.Offline))
            assertEquals(unavailable, fn(WriteOutcome.Unavailable))
            assertEquals(unavailable, fn(WriteOutcome.Done))
        }
    }

    @Test
    fun `failureMessage maps every code`() = check(::failureMessage, emptyMap(), ::failure)

    @Test
    fun `keyFailureMessage maps every code`() = check(::keyFailureMessage, keyOverrides, ::failure)

    @Test
    fun `lineupFailureMessage maps every code`() = check(
        ::lineupFailureMessage,
        mapOf("slot_filled" to slotFilled, "instrument_not_expandable" to notExpandable),
        ::key,
    )

    @Test
    fun `assignmentFailureMessage maps every code`() = check(
        ::assignmentFailureMessage,
        mapOf(
            "invalid_name" to invalidName,
            "slot_taken" to slotTaken,
            "slot_not_in_lineup" to jamChanged,
            "song_not_in_setlist" to jamChanged,
        ),
        ::failure,
    )

    @Test
    fun `slotClearFailureMessage maps every code`() = check(
        ::slotClearFailureMessage,
        mapOf(
            "slot_empty" to clearStale,
            "slot_changed" to clearStale,
            "slot_not_in_lineup" to jamChanged,
            "song_not_in_setlist" to jamChanged,
        ),
        ::failure,
    )

    @Test
    fun `moveFailureMessage maps every code`() = check(
        ::moveFailureMessage,
        mapOf(
            "song_not_in_setlist" to jamChanged,
            "duplicate_song" to duplicateSong,
            "unordered_setlist" to unordered,
        ),
        ::failure,
    )

    @Test
    fun `an unknown code is the Sheet-refused line in every family`() {
        families.forEach { assertEquals(sheetRefused, it(WriteOutcome.Rejected("zzz"))) }
    }
}
