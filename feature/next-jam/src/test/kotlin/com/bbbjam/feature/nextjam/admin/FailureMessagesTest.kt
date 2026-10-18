package com.bbbjam.feature.nextjam.admin

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.nextjam.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every server code against the text its card showed before the codes became typed
 * (`typed-write-rejections`). The expected texts are `UiText` values written out here with their resource ids.
 */
class FailureMessagesTest {
    private val accessRefused = UiText.res(CoreR.string.write_failure_access_refused)
    private val offline = UiText.res(CoreR.string.write_failure_offline)
    private val unavailable = UiText.res(CoreR.string.write_failure_unavailable)
    private val inCatalog = UiText.res(CoreR.string.write_failure_not_in_catalog)
    private val jamChanged = UiText.res(CoreR.string.write_failure_jam_changed)
    private val sheetRefused = UiText.res(CoreR.string.write_failure_sheet_refused)
    private val alreadyListed = UiText.res(R.string.next_jam_failure_already_listed)
    private val notInSetlist = UiText.res(R.string.next_jam_failure_not_in_setlist)
    private val duplicateSong = UiText.res(R.string.next_jam_failure_duplicate_song)
    private val slotFilled = UiText.res(R.string.next_jam_failure_slot_filled)
    private val notExpandable = UiText.res(R.string.next_jam_failure_not_expandable)
    private val invalidName = UiText.res(R.string.next_jam_failure_invalid_name)
    private val slotTaken = UiText.res(R.string.next_jam_failure_slot_taken)
    private val clearStale = UiText.res(R.string.next_jam_failure_clear_stale)
    private val unordered = UiText.res(R.string.next_jam_failure_unordered_setlist)

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

    private val families: List<(WriteOutcome) -> UiText> = listOf(
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

    private fun check(fn: (WriteOutcome) -> UiText, overrides: Map<String, UiText>, fallback: (String) -> UiText) {
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
