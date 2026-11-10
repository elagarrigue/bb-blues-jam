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

    // Mixed heights. Gap rows are 10 apart. Margin fraction used below: 0.05 of the step.
    private val margin = 0.05f

    private fun slot(centre: Float, others: List<ItemBounds>, size: Float, gapTop: Float) =
        ReorderMath.dropSlot(centre, others, size, gapTop, margin)

    // a(0-100) gap(110-410, lifted 300 tall) b(420-520) c(530-630) e(640-740)
    private val tallAmongShort = listOf(
        ItemBounds("a", 0f, 100f),
        ItemBounds("b", 420f, 520f),
        ItemBounds("c", 530f, 630f),
        ItemBounds("e", 640f, 740f),
    )

    // a(0-300) gap(310-410, lifted 100 tall) b(420-720) c(730-1030)
    private val shortAmongTall = listOf(
        ItemBounds("a", 0f, 300f),
        ItemBounds("b", 420f, 720f),
        ItemBounds("c", 730f, 1030f),
    )

    @Test
    fun `tall lifted row among short ones swaps after half a neighbour step`() {
        // Gap top 110, centre = 110 + 150 + d. Step past b is 110, so the swap needs d > 55 + 5.5.
        assertEquals(DropSlot.Before("b"), slot(110f + 150f + 54f, tallAmongShort, 300f, 110f))
        assertEquals(DropSlot.Before("c"), slot(110f + 150f + 70f, tallAmongShort, 300f, 110f))
    }

    @Test
    fun `short lifted row among tall ones does not swap early going up`() {
        // Gap top 310, centre = 310 + 50 + d. Step past a is -310, so the swap needs d < -155 - 15.5.
        assertEquals(DropSlot.Before("b"), slot(360f - 150f, shortAmongTall, 100f, 310f))
        assertEquals(DropSlot.Before("a"), slot(360f - 200f, shortAmongTall, 100f, 310f))
    }

    @Test
    fun `short lifted row among tall ones swaps going down only after half the tall step`() {
        assertEquals(DropSlot.Before("b"), slot(360f + 150f, shortAmongTall, 100f, 310f))
        assertEquals(DropSlot.Before("c"), slot(360f + 200f, shortAmongTall, 100f, 310f))
    }

    @Test
    fun `jitter inside the margin never changes the slot and the back swap sits a dead band away`() {
        // Forward boundary d = 55 + 5.5 = 66 from gap top 110.
        for (d in listOf(50f, 55f, 60f)) {
            assertEquals(DropSlot.Before("b"), slot(260f + d, tallAmongShort, 300f, 110f))
        }
        // After the swap the gap is at 220 with b at 110-210; back boundary d < -55 - 5.5 from the new gap.
        val swapped = listOf(
            ItemBounds("a", 0f, 100f),
            ItemBounds("b", 110f, 210f),
            ItemBounds("c", 530f, 630f),
            ItemBounds("e", 640f, 740f),
        )
        for (d in listOf(-40f, -55f, -60f)) {
            assertEquals(DropSlot.Before("c"), slot(220f + 150f + d, swapped, 300f, 220f))
        }
        assertEquals(DropSlot.Before("b"), slot(220f + 150f - 70f, swapped, 300f, 220f))
    }

    @Test
    fun `a fast move crosses several mixed rows in one call`() {
        assertEquals(DropSlot.Before("e"), slot(260f + 200f, tallAmongShort, 300f, 110f))
        assertEquals(DropSlot.After("e"), slot(260f + 300f, tallAmongShort, 300f, 110f))
    }

    @Test
    fun `uniform heights keep the old results`() {
        val uniform = listOf(ItemBounds("a", 0f, 100f), ItemBounds("c", 220f, 320f))
        // Lifted 100 tall, gap at 110-210. Old rule: before c while centre < 270... new: swap past c at d > 55.
        assertEquals(DropSlot.Before("c"), ReorderMath.dropSlot(160f + 10f, uniform, 100f, 110f))
        assertEquals(DropSlot.After("c"), ReorderMath.dropSlot(160f + 120f, uniform, 100f, 110f))
        assertEquals(DropSlot.Before("a"), ReorderMath.dropSlot(160f - 120f, uniform, 100f, 110f))
        assertEquals(DropSlot.Before("c"), ReorderMath.dropSlot(160f - 10f, uniform, 100f, 110f))
    }

    @Test
    fun `a sweep over mixed heights keeps the gap under the drawn row and never reverses`() {
        val heights = listOf(100f, 280f, 140f, 300f, 90f, 200f)
        val keys = heights.indices.map { "k$it" }
        val liftedKey = "lifted"
        val liftedSize = 220f
        val spacing = 10f
        val maxStep = heights.max() + spacing

        fun layout(slotIndex: Int): Pair<List<ItemBounds>, Float> {
            var top = 0f
            var gap = 0f
            val result = mutableListOf<ItemBounds>()
            for (i in 0..heights.size) {
                if (i == slotIndex) {
                    gap = top
                    top += liftedSize + spacing
                }
                if (i < heights.size) {
                    result += ItemBounds(keys[i], top, top + heights[i])
                    top += heights[i] + spacing
                }
            }
            return result to gap
        }

        fun indexOf(slot: DropSlot?) = when (slot) {
            is DropSlot.Before -> keys.indexOf(slot.key)
            is DropSlot.After -> heights.size
            else -> error("no slot")
        }

        val maxGap = layout(heights.size).second
        val bound = maxStep / 2f + margin * maxStep
        var index = 0
        val down = generateSequence(0f) { it + 3f }.takeWhile { it <= maxGap }.toList()
        val seen = mutableListOf<Int>()
        for (drawnTop in down) {
            val (others, gap) = layout(index)
            index = indexOf(ReorderMath.dropSlot(drawnTop + liftedSize / 2f, others, liftedSize, gap, margin))
            seen += index
            assertTrue("gap too far at $drawnTop", kotlin.math.abs(drawnTop - layout(index).second) <= bound)
        }
        assertEquals(seen.sorted(), seen)
        assertEquals(heights.size, seen.last())
        val downSwapPoints = seen.indices.filter { it > 0 && seen[it] != seen[it - 1] }.map { down[it] }

        val upSeen = mutableListOf<Int>()
        for (drawnTop in down.reversed()) {
            val (others, gap) = layout(index)
            index = indexOf(ReorderMath.dropSlot(drawnTop + liftedSize / 2f, others, liftedSize, gap, margin))
            upSeen += index
            assertTrue("gap too far at $drawnTop", kotlin.math.abs(drawnTop - layout(index).second) <= bound)
        }
        assertEquals(upSeen.sortedDescending(), upSeen)
        assertEquals(0, upSeen.last())
        val reversed = down.reversed()
        val upSwapPoints = upSeen.indices.filter { it > 0 && upSeen[it] != upSeen[it - 1] }.map { reversed[it] }
        // Each row's back-swap happens at a different drawn position than its forward swap (the dead band).
        assertEquals(downSwapPoints.size, upSwapPoints.size)
        assertTrue(downSwapPoints.reversed().zip(upSwapPoints).all { (f, b) -> f != b })
        assertTrue(liftedKey.isNotEmpty())
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
