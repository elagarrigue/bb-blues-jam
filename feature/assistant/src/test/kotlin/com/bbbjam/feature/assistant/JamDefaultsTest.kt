package com.bbbjam.feature.assistant

import com.bbbjam.core.data.admin.WriteOutcome
import com.bbbjam.core.ui.theme.BluesJamColors
import java.time.LocalDate
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
    fun `the copy uses vos and the documented sentences`() {
        assertEquals("Sábado 28 de noviembre de 2026", JamCopy.dateInWords(LocalDate.of(2026, 11, 28)))
        assertEquals("21:05", JamCopy.time(java.time.LocalTime.of(21, 5)))
        assertEquals(
            "No se pudo crear: ${ApplyOutcomeCopy.ACCESS_REFUSED}",
            JamCopy.failure(JamFailure.Write(WriteOutcome.AccessRefused)),
        )
        assertEquals(
            "No se pudo crear: ya hay una pestaña con esa fecha en la planilla; revisala antes de crear la jam.",
            JamCopy.failure(JamFailure.Write(WriteOutcome.Rejected("tab_exists"))),
        )
        assertEquals(
            "No se pudo crear: esa fecha ya pasó.",
            JamCopy.failure(JamFailure.Write(WriteOutcome.Rejected("past_date"))),
        )
        assertEquals(
            "No se pudo crear: la planilla rechazó los datos de la jam.",
            JamCopy.failure(JamFailure.Write(WriteOutcome.Rejected("invalid_venue"))),
        )
    }
}
