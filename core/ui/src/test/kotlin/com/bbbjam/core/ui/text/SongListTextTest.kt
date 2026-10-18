package com.bbbjam.core.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

/** The shared song-row text, written out here, resolved against the shipped `strings.xml`. */
class SongListTextTest {
    @Test
    fun `positions are two digits`() {
        assertEquals("01", SongListText.positionLabel(1))
        assertEquals("13", SongListText.positionLabel(13))
    }

    @Test
    fun `the key is described as Tonalidad`() {
        assertEquals("Tonalidad Bm", SongListText.keyDescription("Bm").text())
    }

    @Test
    fun `the dropped rows note is singular for one and plural otherwise`() {
        assertEquals("Falta 1 tema: no se pudo leer.", SongListText.droppedRows(1).text())
        assertEquals("Faltan 3 temas: no se pudieron leer.", SongListText.droppedRows(3).text())
    }
}
