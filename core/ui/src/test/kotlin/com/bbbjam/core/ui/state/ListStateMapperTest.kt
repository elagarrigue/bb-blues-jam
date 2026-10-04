package com.bbbjam.core.ui.state

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every string here is written out from the approved copy table in `docs/specs/list-states.md`
 * (C1), never read from [ListStateCopy], so any change to the shipped copy fails this class.
 */
class ListStateMapperTest {

    @Test
    fun `the offline error says there is no connection and nothing saved`() {
        val model = listError("No pudimos cargar la próxima jam", isOffline = true) {}
        assertEquals("No pudimos cargar la próxima jam", model.title)
        assertEquals(
            "No hay conexión y todavía no hay nada guardado. Revisá los datos o el wifi y probá de nuevo.",
            model.message,
        )
        assertEquals("Reintentar", model.retryLabel)
    }

    @Test
    fun `any other failure gets the generic message`() {
        val model = listError("No pudimos cargar la próxima jam", isOffline = false) {}
        assertEquals(
            "Algo falló al leer los datos. Probá de nuevo en un rato; si sigue fallando, avisale a la organización.",
            model.message,
        )
        assertEquals("Reintentar", model.retryLabel)
    }

    @Test
    fun `the notice title says offline or that the refresh failed`() {
        assertEquals("Sin conexión", notice(Duration.ofHours(3), isOffline = true).title)
        assertEquals("No se pudo actualizar", notice(Duration.ofHours(3), isOffline = false).title)
        assertEquals("Mostrando lo guardado hace 3 horas.", notice(Duration.ofHours(3)).detail)
        assertEquals("Reintentar", notice(Duration.ofHours(3)).retryLabel)
    }

    @Test
    fun `ages are whole units rounded down at every boundary`() {
        val expected = listOf(
            Duration.ZERO to "hace menos de un minuto",
            Duration.ofSeconds(59) to "hace menos de un minuto",
            Duration.ofSeconds(60) to "hace 1 minuto",
            Duration.ofSeconds(119) to "hace 1 minuto",
            Duration.ofMinutes(2) to "hace 2 minutos",
            Duration.ofMinutes(59) to "hace 59 minutos",
            Duration.ofMinutes(60) to "hace 1 hora",
            Duration.ofMinutes(119) to "hace 1 hora",
            Duration.ofHours(2) to "hace 2 horas",
            Duration.ofHours(23).plusMinutes(59) to "hace 23 horas",
            Duration.ofHours(24) to "hace 1 día",
            Duration.ofHours(47) to "hace 1 día",
            Duration.ofHours(48) to "hace 2 días",
            Duration.ofSeconds(-30) to "hace menos de un minuto",
            Duration.ofHours(-5) to "hace menos de un minuto",
        )
        expected.forEach { (age, text) ->
            assertEquals("age $age", "Mostrando lo guardado $text.", notice(age).detail)
        }
    }

    @Test
    fun `while refreshing the detail says so and there is no action`() {
        val model = notice(Duration.ofHours(3), isRefreshing = true)
        assertEquals("Sin conexión", model.title)
        assertEquals("Actualizando…", model.detail)
        assertNull(model.retryLabel)
    }

    @Test
    fun `both retry handlers call onRetry`() {
        var calls = 0
        listError("t", isOffline = true) { calls++ }.events(ListErrorUiModel.Event.Retry)
        stalenessNotice(isOffline = false, Duration.ofHours(1), isRefreshing = false) { calls++ }
            .events(StalenessNoticeUiModel.Event.Retry)
        assertEquals(2, calls)
    }

    private fun notice(age: Duration, isOffline: Boolean = true, isRefreshing: Boolean = false) =
        stalenessNotice(isOffline, age, isRefreshing) {}
}
