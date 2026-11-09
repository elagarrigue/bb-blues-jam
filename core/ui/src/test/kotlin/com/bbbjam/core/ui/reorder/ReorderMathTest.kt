package com.bbbjam.core.ui.reorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderMathTest {
    private val items = listOf(
        ItemBounds("a", 0f, 100f),
        ItemBounds("b", 110f, 210f),
        ItemBounds("c", 220f, 320f),
    )

    @Test
    fun `hovered key is the nearest item center`() {
        assertEquals("a", ReorderMath.hoveredKey(40f, items))
        assertEquals("b", ReorderMath.hoveredKey(160f, items))
        assertEquals("c", ReorderMath.hoveredKey(900f, items))
        assertEquals("a", ReorderMath.hoveredKey(-500f, items))
    }

    @Test
    fun `a center in the gap goes to the nearer neighbour`() {
        assertEquals("a", ReorderMath.hoveredKey(104f, items))
        assertEquals("b", ReorderMath.hoveredKey(106f, items))
    }

    @Test
    fun `no candidates means no hovered key`() {
        assertNull(ReorderMath.hoveredKey(10f, emptyList()))
    }

    @Test
    fun `edge scroll is zero in the middle and signed near the edges`() {
        assertEquals(0f, ReorderMath.edgeScrollDelta(500f, 0f, 1000f, 100f, 40f), 0f)
        assertTrue(ReorderMath.edgeScrollDelta(50f, 0f, 1000f, 100f, 40f) < 0f)
        assertTrue(ReorderMath.edgeScrollDelta(950f, 0f, 1000f, 100f, 40f) > 0f)
    }

    @Test
    fun `edge scroll grows to the max step and is clamped beyond the edge`() {
        assertEquals(-20f, ReorderMath.edgeScrollDelta(50f, 0f, 1000f, 100f, 40f), 0.001f)
        assertEquals(-40f, ReorderMath.edgeScrollDelta(-300f, 0f, 1000f, 100f, 40f), 0.001f)
        assertEquals(40f, ReorderMath.edgeScrollDelta(1300f, 0f, 1000f, 100f, 40f), 0.001f)
        assertEquals(0f, ReorderMath.edgeScrollDelta(10f, 0f, 1000f, 0f, 40f), 0f)
    }

    @Test
    fun `lift translation keeps the row centre under the finger wherever it was placed`() {
        // Placed at its true slot: translation is the finger's travel.
        assertEquals(100f, ReorderMath.liftTranslation(pointerY = 150f, itemSize = 100f, placedTop = 0f), 0f)
        // Placed far away by the lazy list (a pinned item): still ends at pointerY - itemSize / 2 after translation.
        val placedTop = 2000f
        val translation = ReorderMath.liftTranslation(pointerY = 600f, itemSize = 100f, placedTop = placedTop)
        assertEquals(550f, placedTop + translation, 0f)
    }
}
