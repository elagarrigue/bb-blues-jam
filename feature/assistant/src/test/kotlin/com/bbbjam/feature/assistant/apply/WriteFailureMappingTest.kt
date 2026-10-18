package com.bbbjam.feature.assistant.apply

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.jam.jamFailureReason
import com.bbbjam.feature.assistant.run.JamFailure
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every server code against the text the assistant showed before the codes became typed
 * (`typed-write-rejections`). The expected resources are named here, never read from the mapping.
 */
class WriteFailureMappingTest {
    private val accessRefused = UiText.res(CoreR.string.write_failure_access_refused)
    private val unavailable = UiText.res(CoreR.string.write_failure_unavailable)
    private val inCatalog = UiText.res(CoreR.string.write_failure_not_in_catalog)
    private val jamChanged = UiText.res(CoreR.string.write_failure_jam_changed)
    private val sheetRefused = UiText.res(CoreR.string.write_failure_sheet_refused)
    private val unordered = UiText.res(R.string.assistant_replace_unordered)

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
        allCodes.forEach { assertEquals(it, write(it), writeFailureText(WriteOutcome.Rejected(it))) }
        assertEquals(accessRefused, writeFailureText(WriteOutcome.AccessRefused))
        assertEquals(unavailable, writeFailureText(WriteOutcome.Unavailable))
        assertEquals(unavailable, writeFailureText(WriteOutcome.Done))
        assertEquals(UiText.res(R.string.assistant_apply_write_offline), writeFailureText(WriteOutcome.Offline))
    }

    @Test
    fun `moveFailure maps every code`() {
        allCodes.forEach {
            val expected = if (it == "unordered_setlist") unordered else write(it)
            assertEquals(it, expected, moveFailureText(WriteOutcome.Rejected(it)))
        }
    }

    @Test
    fun `jam creation reasons map every code`() {
        val own = mapOf(
            "jam_exists" to UiText.res(R.string.assistant_jam_reason_jam_exists),
            "tab_exists" to UiText.res(R.string.assistant_jam_reason_tab_exists),
            "past_date" to UiText.res(R.string.assistant_jam_reason_past_date),
            "invalid_date" to UiText.res(R.string.assistant_jam_reason_invalid_data),
            "invalid_time" to UiText.res(R.string.assistant_jam_reason_invalid_data),
            "invalid_venue" to UiText.res(R.string.assistant_jam_reason_invalid_data),
        )
        allCodes.forEach {
            val reason = jamFailureReason(JamFailure.Write(WriteOutcome.Rejected(it)))
            assertEquals(it, own[it] ?: sheetRefused, reason)
        }
        assertEquals(accessRefused, jamFailureReason(JamFailure.Write(WriteOutcome.AccessRefused)))
        assertEquals(unavailable, jamFailureReason(JamFailure.Write(WriteOutcome.Unavailable)))
    }
}
