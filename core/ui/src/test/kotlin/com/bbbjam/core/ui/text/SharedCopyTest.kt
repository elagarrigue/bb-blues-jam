package com.bbbjam.core.ui.text

import com.bbbjam.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared copy that no mapper test already pins, written out from the approved copy and compared
 * with `strings.xml`: the tab labels, the back control, the jam form fields and the generic failure
 * lines of an admin write. Rioplatense Spanish with vos (D-12).
 */
class SharedCopyTest {
    private fun text(id: Int) = UiText.res(id).text()

    @Test
    fun `the tab labels and the back control`() {
        assertEquals("Próxima jam", text(R.string.tab_next_jam))
        assertEquals("Jams", text(R.string.tab_jams))
        assertEquals("Info", text(R.string.tab_info))
        assertEquals("Asistente", text(R.string.tab_assistant))
        assertEquals("Volver", text(R.string.nav_back))
        assertEquals("Logo de Bahía Blanca Blues", text(R.string.brand_logo_description))
    }

    @Test
    fun `the jam form labels, hints and field errors`() {
        assertEquals("Fecha", text(R.string.jam_form_date_label))
        assertEquals("DD/MM/AAAA", text(R.string.jam_form_date_hint))
        assertEquals("Hora", text(R.string.jam_form_time_label))
        assertEquals("HH:MM", text(R.string.jam_form_time_hint))
        assertEquals("Lugar", text(R.string.jam_form_venue_label))
        assertEquals("Cancelar", text(R.string.jam_form_cancel))
        assertEquals("Escribí la fecha como DD/MM/AAAA.", text(R.string.jam_form_date_format_error))
        assertEquals("Esa fecha ya pasó.", text(R.string.jam_form_date_past_error))
        assertEquals("Ya hay una jam ese día.", text(R.string.jam_form_date_exists_error))
        assertEquals("Escribí la hora como HH:MM, por ejemplo 21:00.", text(R.string.jam_form_time_error))
        assertEquals("Escribí el lugar.", text(R.string.jam_form_venue_empty_error))
        assertEquals("El lugar puede tener hasta 80 letras.", text(R.string.jam_form_venue_long_error))
        assertEquals("El lugar no puede empezar con =, +, - o @.", text(R.string.jam_form_venue_formula_error))
    }

    @Test
    fun `the generic failure lines of an admin write`() {
        assertEquals(
            "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar.",
            text(R.string.write_failure_access_refused),
        )
        assertEquals("No hay conexión. Probá de nuevo cuando tengas internet.", text(R.string.write_failure_offline))
        assertEquals("El servidor no respondió. Probá de nuevo en un rato.", text(R.string.write_failure_unavailable))
        assertEquals(
            "La jam cambió en la planilla. Actualizá y probá de nuevo.",
            text(R.string.write_failure_jam_changed),
        )
        assertEquals(
            "La planilla rechazó el cambio. Revisala y probá de nuevo.",
            text(R.string.write_failure_sheet_refused),
        )
        assertEquals("Ese tema ya no está en el catálogo.", text(R.string.write_failure_not_in_catalog))
    }

    @Test
    fun `the lineup panel headings and actions`() {
        assertEquals("Cupos libres", text(R.string.lineup_open_heading))
        assertEquals("Cupos cubiertos", text(R.string.lineup_filled_heading))
        assertEquals("Otros", text(R.string.lineup_extras_heading))
        assertEquals("Quitando…", text(R.string.lineup_clearing))
        assertEquals("Quitar", text(R.string.lineup_remove_extra))
    }
}
