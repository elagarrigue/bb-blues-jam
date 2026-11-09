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
    fun `finger above all centres is before the first`() {
        assertEquals(DropSlot.Before("a"), ReorderMath.dropSlot(-500f, items))
        assertEquals(DropSlot.Before("a"), ReorderMath.dropSlot(40f, items))
    }

    @Test
    fun `finger below all centres is after the last`() {
        assertEquals(DropSlot.After("c"), ReorderMath.dropSlot(900f, items))
    }

    @Test
    fun `finger between rows is before the next one`() {
        assertEquals(DropSlot.Before("b"), ReorderMath.dropSlot(60f, items))
        assertEquals(DropSlot.Before("c"), ReorderMath.dropSlot(170f, items))
    }

    @Test
    fun `no rows means no slot`() {
        assertNull(ReorderMath.dropSlot(10f, emptyList()))
    }

    @Test
    fun `the slot is stable after a neighbour moves into the gap`() {
        val before = listOf(ItemBounds("a", 0f, 100f), ItemBounds("c", 220f, 320f))
        val after = listOf(ItemBounds("a", 0f, 100f), ItemBounds("c", 110f, 210f))
        assertEquals(ReorderMath.dropSlot(150f, before), DropSlot.Before("c"))
        assertEquals(ReorderMath.dropSlot(150f, after), DropSlot.Before("c"))
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

    @Test
    fun `finger position follows the grab offset inside the row`() {
        assertEquals(130f, ReorderMath.fingerY(pointerY = 300f, itemSize = 400f, grabY = 30f), 0f)
    }

    @Test
    fun `edge zone is capped by a fraction of the viewport, never above one row`() {
        assertEquals(206.4f, ReorderMath.autoScrollThreshold(570f, 1720f, 48f), 0.01f)
        assertEquals(100f, ReorderMath.autoScrollThreshold(100f, 1720f, 48f), 0f)
        assertEquals(48f, ReorderMath.autoScrollThreshold(570f, 200f, 48f), 0f)
    }

    @Test
    fun `a tall row whose centre is in the old zone but whose finger is outside the new one does not scroll`() {
        val itemSize = 570f
        val viewport = 1720f
        // Row centre 250 px from the top: inside the old zone (threshold = itemSize), so it used to scroll.
        assertTrue(ReorderMath.edgeScrollDelta(250f, 0f, viewport, itemSize, 142f) < 0f)
        val threshold = ReorderMath.autoScrollThreshold(itemSize, viewport, 48f)
        val finger = ReorderMath.fingerY(pointerY = 250f, itemSize = itemSize, grabY = 285f + 100f)
        assertEquals(0f, ReorderMath.edgeScrollDelta(finger, 0f, viewport, threshold, 142f), 0f)
    }
}
