package com.bbbjam.feature.pastjams.admin

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.data.setlist.jamcreate.CreateJamOutcome
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.feature.pastjams.R
import com.bbbjam.feature.pastjams.create.createFailureMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every server code against the text its card showed before the codes became typed
 * (`typed-write-rejections`). The expected texts name their resources here, not the helpers under test.
 */
class JamsFailureMessagesTest {
    private val accessRefused = UiText.res(CoreR.string.write_failure_access_refused)
    private val offline = UiText.res(CoreR.string.write_failure_offline)
    private val unavailable = UiText.res(CoreR.string.write_failure_unavailable)
    private val jamChanged = UiText.res(CoreR.string.write_failure_jam_changed)
    private val sheetRefused = UiText.res(CoreR.string.write_failure_sheet_refused)
    private val createExists = UiText.res(R.string.past_jams_create_exists)
    private val createInvalid = UiText.res(R.string.past_jams_create_invalid)
    private val datePast = UiText.res(CoreR.string.jam_form_date_past_error)
    private val setlistChanged = UiText.res(R.string.past_jams_setlist_changed)
    private val emptyPublish = UiText.res(R.string.past_jams_empty_publish)
    private val publishOffline = UiText.res(R.string.past_jams_publish_offline)

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
