package com.bbbjam.core.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

/** The shared write-failure lines, written out here as they read before they moved. */
class WriteFailureCopyTest {
    @Test
    fun `the lines read exactly as before`() {
        assertEquals(
            "La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar.",
            WriteFailureCopy.ACCESS_REFUSED,
        )
        assertEquals("No hay conexión. Probá de nuevo cuando tengas internet.", WriteFailureCopy.OFFLINE)
        assertEquals("El servidor no respondió. Probá de nuevo en un rato.", WriteFailureCopy.UNAVAILABLE)
        assertEquals("La jam cambió en la planilla. Actualizá y probá de nuevo.", WriteFailureCopy.JAM_CHANGED)
        assertEquals("La planilla rechazó el cambio. Revisala y probá de nuevo.", WriteFailureCopy.SHEET_REFUSED)
        assertEquals("Ese tema ya no está en el catálogo.", WriteFailureCopy.NOT_IN_CATALOG)
    }
}
