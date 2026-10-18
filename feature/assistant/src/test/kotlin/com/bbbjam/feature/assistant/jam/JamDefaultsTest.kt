package com.bbbjam.feature.assistant.jam

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.ui.R as CoreR
import com.bbbjam.core.ui.text.SpanishDateNames
import com.bbbjam.core.ui.text.UiText
import com.bbbjam.core.ui.theme.BluesJamColors
import com.bbbjam.feature.assistant.R
import com.bbbjam.feature.assistant.apply.ApplyDefaults
import com.bbbjam.feature.assistant.apply.ApplyTone
import com.bbbjam.feature.assistant.apply.summary
import com.bbbjam.feature.assistant.run.JamFailure
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Test

class JamDefaultsTest {
    @Test
    fun `the card is a raised surface with muted heading and note and never amber`() {
        val card = JamDefaults.card()
        assertEquals(BluesJamColors.surfaceRaised, card.fill)
        assertEquals(BluesJamColors.textMuted, card.heading)
        assertEquals(BluesJamColors.text, card.date)
        assertEquals(BluesJamColors.text, card.detail)
        assertEquals(BluesJamColors.textMuted, card.note)
    }

    @Test
    fun `the button shows on the raised card and is muted when disabled`() {
        assertEquals(BluesJamColors.surface, JamDefaults.button(true).fill)
        assertEquals(BluesJamColors.text, JamDefaults.button(true).content)
        assertEquals(BluesJamColors.surface, JamDefaults.button(false).fill)
        assertEquals(BluesJamColors.textMuted, JamDefaults.button(false).content)
        assertEquals(BluesJamColors.textMuted, JamDefaults.rejectionNote())
    }

    @Test
    fun `the result tones are text, error or muted`() {
        assertEquals(BluesJamColors.text, ApplyDefaults.summary(ApplyTone.SUCCESS))
        assertEquals(BluesJamColors.error, ApplyDefaults.summary(ApplyTone.FAILURE))
        assertEquals(BluesJamColors.text, ApplyDefaults.summary(ApplyTone.NEUTRAL))
    }

    @Test
    fun `the dialog colours are the apply dialog colours`() {
        val dialog = ApplyDefaults.applyDialog()
        assertEquals(BluesJamColors.surfaceRaised, dialog.container)
        assertEquals(BluesJamColors.text, dialog.confirm)
    }

    @Test
    fun `the texts name the documented resources`() {
        assertEquals(
            UiText.res(
                R.string.assistant_jam_date_in_words,
                SpanishDateNames.day(DayOfWeek.SATURDAY),
                28,
                SpanishDateNames.month(Month.NOVEMBER),
                2026,
            ),
            jamDateInWordsText(LocalDate.of(2026, 11, 28)),
        )
        assertEquals("21:05", jamTime(java.time.LocalTime.of(21, 5)))
        assertEquals(
            UiText.res(R.string.assistant_jam_failure, UiText.res(CoreR.string.write_failure_access_refused)),
            jamFailureText(JamFailure.Write(WriteOutcome.AccessRefused)),
        )
        assertEquals(
            UiText.res(R.string.assistant_jam_failure, UiText.res(R.string.assistant_jam_reason_tab_exists)),
            jamFailureText(JamFailure.Write(WriteOutcome.Rejected("tab_exists"))),
        )
        assertEquals(
            UiText.res(R.string.assistant_jam_failure, UiText.res(R.string.assistant_jam_reason_past_date)),
            jamFailureText(JamFailure.Write(WriteOutcome.Rejected("past_date"))),
        )
        assertEquals(
            UiText.res(R.string.assistant_jam_failure, UiText.res(R.string.assistant_jam_reason_invalid_data)),
            jamFailureText(JamFailure.Write(WriteOutcome.Rejected("invalid_venue"))),
        )
        assertEquals(UiText.res(R.string.assistant_jam_script_outdated), jamFailureText(JamFailure.ScriptOutdated))
    }
}
